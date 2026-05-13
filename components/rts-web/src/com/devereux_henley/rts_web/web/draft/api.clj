(ns com.devereux-henley.rts-web.web.draft.api
  (:require
   [com.devereux-henley.http.contract :as web.core]
   [com.devereux-henley.rts-domain.contract :as domain]
   [integrant.core]))

(def draft-entry-embed-registry
  "Registry of :embed keys → functions that enrich a draft-entry-resource.
   Clients opt into additional data via `?embed=<key>` on the GET."
  {:unit domain/embed-unit-for-entry})

(defn- parse-embed-set
  "Normalises the `embed` query param (string, sequence of strings, or nil)
   into a set of keywords that apply-embeds can consume."
  [embed]
  (when-let [v (web.core/query-param->vec embed)]
    (into #{} (map keyword) v)))

(defn- selection-overrides
  "Builds a selection-override map from the GET query params, or nil when
  the caller provided none of the selection keys. When at least one is
  present the result is a complete snapshot — missing keys default to nil
  or empty so the render reflects exactly what the URL specifies."
  [{:keys [mount level items spells abilities] :as query}]
  (when (some #(contains? query %) [:mount :level :items :spells :abilities])
    {:mount     (not-empty mount)
     :level     (or level 0)
     :items     (or (web.core/query-param->vec items) [])
     :spells    (or (web.core/query-param->vec spells) [])
     :abilities (or (web.core/query-param->vec abilities) [])}))

(defmethod integrant.core/init-key ::create-draft
  [_init-key dependencies]
  (fn [{{{:keys [faction-eid game-mode-eid game-eid name]} :body
         {:keys [version]}                                 :query
         {:keys [eid]}                                     :path} :parameters
        router                                                    :reitit.core/router
        session                                                   :ory-session
        :as                                                       _request}]
    (let [response (web.core/handle-create-response
                    domain/draft-resource
                    {:hostname (:hostname dependencies) :router router}
                    #(domain/create-draft
                      dependencies
                      {:faction-eid    faction-eid
                       :game-mode-eid  game-mode-eid
                       :name           name
                       :player-sub     (get-in session [:identity :id])
                       :created-by-sub (get-in session [:identity :id])
                       :eid            eid
                       :version        version}))]
      (assoc-in response [:headers "HX-Redirect"] (str "/view/game/" game-eid "/draft/" eid "/index.html")))))

(defmethod integrant.core/init-key ::update-draft
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path
         body          :body} :parameters
        router                :reitit.core/router
        :as                   _request}]
    (let [result (domain/update-draft dependencies eid (select-keys (or body {}) [:name]))]
      (if (= :draft/locked (:type result))
        {:status 409 :body result}
        (web.core/handle-fetch-response
         domain/draft-resource
         {:hostname (:hostname dependencies) :router router}
         (constantly result))))))

(defn- with-lock-flag
  "Attaches `:locked? true/false` to a panel resource so the template
  can gate mutation triggers (hx-patch/hx-post on mark/lore/mount/
  abilities/spells/items selectors) without each fragment re-querying
  the lock state. The flag is computed once per request and propagated
  to any embedded unit panel so the form controls inside it know about
  the lock too."
  [dependencies draft-eid resource]
  (let [locked? (some? (domain/draft-lock-info dependencies draft-eid))
        attach  #(assoc % :locked? locked?)]
    (cond-> (attach resource)
      (get-in resource [:_embedded :unit])
      (update-in [:_embedded :unit] attach))))

(defmethod integrant.core/init-key ::get-draft-unit
  [_init-key dependencies]
  (fn [{{{:keys [draft-eid eid]}      :path
         {:keys [unit-eid] :as query} :query} :parameters
        :as                                   _request}]
    (let [overrides (selection-overrides query)
          unit      (or eid unit-eid)
          result    (some->> (domain/get-draft-unit-details dependencies draft-eid unit overrides)
                             (with-lock-flag dependencies draft-eid))]
      (if result
        {:status 200 :body result}
        {:status 404 :body {:type :missing/resource :name "draft-unit" :id unit}}))))

(defmethod integrant.core/init-key ::get-draft-entry
  [_init-key dependencies]
  (fn [{{{:keys [draft-eid eid]}           :path
         {:keys [section embed] :as query} :query} :parameters
        :as                                        _request}]
    (let [embed-set (parse-embed-set embed)
          overrides (selection-overrides query)]
      (if-let [details (domain/get-draft-entry-details dependencies draft-eid eid section overrides)]
        {:status 200
         :body   (->> details
                      (web.core/apply-embeds draft-entry-embed-registry dependencies embed-set)
                      (with-lock-flag dependencies draft-eid))}
        {:status 404 :body {:type :missing/resource :name "draft-entry" :id eid}}))))

(defmethod integrant.core/init-key ::draft-add-unit
  [_init-key dependencies]
  (fn [request]
    (let [{{{:keys [draft-eid eid]} :path
            {:keys [section]}       :query
            body                    :body} :parameters} request
          selections                                    (select-keys (or body {}) [:mount :level :abilities :spells :items])
          result                                        (domain/add-unit-to-draft dependencies draft-eid eid section selections)]
      {:status (case (:type result)
                 :draft/add-success 200
                 :draft/locked      409
                 422)
       :body   result})))

(defmethod integrant.core/init-key ::draft-update-unit
  [_init-key dependencies]
  (fn [{{{:keys [draft-eid eid]} :path
         {:keys [section]}       :query
         body                    :body} :parameters
        router                          :reitit.core/router
        :as                             _request}]
    (let [selections (select-keys (or body {}) [:unit-eid :mount :level :abilities :spells :items])
          result     (domain/update-unit-in-draft dependencies draft-eid eid section selections)]
      (case (:type result)
        :draft/update-success
        ;; Enrich the response with the freshly-persisted entry (+ embedded
        ;; unit) so the same round-trip re-renders the panel via HTMX. The
        ;; OOB fragments in draft-update-success.html handle the sidebar
        ;; slot/budget updates.
        (let [entry (some->> (domain/get-draft-entry-details dependencies draft-eid eid section)
                             (domain/embed-unit-for-entry dependencies))]
          (web.core/handle-fetch-response
           domain/draft-update-response
           {:hostname (:hostname dependencies) :router router}
           (constantly (assoc result :entry entry))))

        :draft/locked
        {:status 409 :body result}

        {:status 422 :body result}))))

(defmethod integrant.core/init-key ::draft-remove-unit
  [_init-key dependencies]
  (fn [request]
    (let [{{{:keys [draft-eid eid]} :path
            {:keys [section]}       :query} :parameters} request
          result                                         (domain/remove-unit-from-draft dependencies draft-eid eid section)]
      {:status (if (= :draft/locked (:type result)) 409 200)
       :body   result})))
