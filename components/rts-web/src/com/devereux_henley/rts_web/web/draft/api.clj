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

(defn- parse-level
  "Coerces a level query-string param to a 0-9 int, or nil when absent/blank."
  [v]
  (when (and v (not= v ""))
    (try (max 0 (min 9 (Integer/parseInt (str v))))
         (catch Exception _ nil))))

(defn- selection-overrides
  "Builds a selection-override map from the GET query params, or nil when
  the caller provided none of the selection keys. When at least one is
  present the result is a complete snapshot — missing keys default to nil
  or empty so the render reflects exactly what the URL specifies."
  [{:keys [mount lore level items spells abilities] :as query}]
  (when (some #(contains? query %) [:mount :lore :level :items :spells :abilities])
    {:mount     (not-empty mount)
     :lore      (not-empty lore)
     :level     (or (parse-level level) 0)
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
    (web.core/handle-fetch-response
     domain/draft-resource
     {:hostname (:hostname dependencies) :router router}
     #(domain/update-draft dependencies eid (select-keys (or body {}) [:name])))))

(defmethod integrant.core/init-key ::get-draft-unit
  [_init-key dependencies]
  (fn [{{{:keys [draft-eid eid]} :path
         query                   :query} :parameters
        router                           :reitit.core/router
        :as                              _request}]
    (let [overrides (selection-overrides query)]
      (web.core/handle-fetch-response
       domain/draft-unit-resource
       {:hostname (:hostname dependencies) :router router}
       #(domain/get-draft-unit-details dependencies draft-eid eid overrides)))))

(defmethod integrant.core/init-key ::get-draft-entry
  [_init-key dependencies]
  (fn [{{{:keys [draft-eid eid]}           :path
         {:keys [section embed] :as query} :query} :parameters
        router                                     :reitit.core/router
        :as                                        _request}]
    (let [embed-set (parse-embed-set embed)
          overrides (selection-overrides query)]
      (web.core/handle-fetch-response
       domain/draft-entry-resource
       {:hostname (:hostname dependencies) :router router}
       #(if-let [details (domain/get-draft-entry-details dependencies draft-eid eid section overrides)]
          (web.core/apply-embeds draft-entry-embed-registry dependencies embed-set details)
          {:type :missing/resource :name "draft-entry" :id eid})))))

(defmethod integrant.core/init-key ::draft-add-unit
  [_init-key dependencies]
  (fn [request]
    (let [{{{:keys [draft-eid eid]} :path
            {:keys [section]}       :query
            body                    :body} :parameters} request
          selections                                    (select-keys (or body {}) [:mount :lore :level :abilities :spells :items])
          result                                        (domain/add-unit-to-draft dependencies draft-eid eid section selections)]
      {:status (if (= :draft/add-success (:type result)) 200 422)
       :body   result})))

(defmethod integrant.core/init-key ::draft-update-unit
  [_init-key dependencies]
  (fn [{{{:keys [draft-eid eid]} :path
         {:keys [section]}       :query
         body                    :body} :parameters
        router                          :reitit.core/router
        :as                             _request}]
    (let [selections (select-keys (or body {}) [:mount :lore :level :abilities :spells :items])
          result     (domain/update-unit-in-draft dependencies draft-eid eid section selections)]
      (if (= :draft/update-success (:type result))
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
        {:status 422 :body result}))))

(defmethod integrant.core/init-key ::draft-remove-unit
  [_init-key dependencies]
  (fn [request]
    (let [{{{:keys [draft-eid eid]} :path
            {:keys [section]}       :query} :parameters} request]
      {:status 200 :body (domain/remove-unit-from-draft dependencies draft-eid eid section)})))
