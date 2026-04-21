(ns com.devereux-henley.rts-web.web.draft
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
  (some-> embed (as-> e (set (map keyword (if (string? e) [e] e))))))

(defn- ->vec
  "Coerces a query param that may arrive as nil, a single string, or a
  collection of strings into a (possibly empty) vector of strings."
  [v]
  (cond
    (nil? v)        nil
    (string? v)     [v]
    (sequential? v) (vec v)
    :else           nil))

(defn- selection-overrides
  "Builds a selection-override map from the GET query params, or nil when
  the caller provided none of the selection keys. When at least one is
  present the result is a complete snapshot — missing keys default to nil
  or empty so the render reflects exactly what the URL specifies."
  [{:keys [mount items spells abilities] :as query}]
  (when (some #(contains? query %) [:mount :items :spells :abilities])
    {:mount     (not-empty mount)
     :items     (or (->vec items) [])
     :spells    (or (->vec spells) [])
     :abilities (or (->vec abilities) [])}))

(defmethod integrant.core/init-key ::create-draft
  [_init-key dependencies]
  (fn [{{{:keys [faction-eid game-mode-eid game-eid]} :body
         {:keys [version]}                            :query
         {:keys [eid]}                                :path} :parameters
        router                                               :reitit.core/router
        session                                              :ory-session
        :as                                                  _request}]
    (let [response (web.core/handle-create-response
                    domain/draft-resource
                    {:hostname (:hostname dependencies) :router router}
                    #(domain/create-draft
                      dependencies
                      {:faction-eid    faction-eid
                       :game-mode-eid  game-mode-eid
                       :player-sub     (get-in session [:identity :id])
                       :created-by-sub (get-in session [:identity :id])
                       :eid            eid
                       :version        version}))]
      (assoc-in response [:headers "HX-Redirect"] (str "/view/game/" game-eid "/draft/" eid "/index.html")))))

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
  (fn [{{{:keys [draft-eid eid]} :path
         {:keys [section embed] :as query} :query} :parameters
        router                            :reitit.core/router
        :as                               _request}]
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
          selections                                    (select-keys (or body {}) [:mount :abilities :spells :items])
          result                                        (domain/add-unit-to-draft dependencies draft-eid eid section selections)]
      {:status (if (= :draft/add-success (:type result)) 200 422)
       :body   result})))

(defmethod integrant.core/init-key ::draft-update-unit
  [_init-key dependencies]
  (fn [request]
    (let [{{{:keys [draft-eid eid]} :path
            {:keys [section]}       :query
            body                    :body} :parameters} request
          selections                                    (select-keys (or body {}) [:mount :abilities :spells :items])
          result                                        (domain/update-unit-in-draft dependencies draft-eid eid section selections)]
      {:status (if (= :draft/update-success (:type result)) 200 422)
       :body   result})))

(defmethod integrant.core/init-key ::draft-remove-unit
  [_init-key dependencies]
  (fn [request]
    (let [{{{:keys [draft-eid eid]} :path
            {:keys [section]}       :query} :parameters} request]
      {:status 200 :body (domain/remove-unit-from-draft dependencies draft-eid eid section)})))
