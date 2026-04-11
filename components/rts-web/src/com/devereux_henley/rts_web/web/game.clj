(ns com.devereux-henley.rts-web.web.game
  (:require
   [com.devereux-henley.http.contract :as web.core]
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.schema.contract :as schema.contract]
   [integrant.core]
   [reitit.core]
   [taoensso.timbre :as log]))

(defn get-game-by-eid
  [dependencies eid]
  (or (domain/get-game-by-eid dependencies eid)
      {:type :missing/resource :name "game" :id eid}))

(defn get-factions-for-game
  [dependencies model]
  (assoc-in model [:_embedded :factions]
            (domain/get-factions-for-game dependencies (:eid model))))

(defn get-game-modes-for-game
  [dependencies model]
  (assoc-in model [:_embedded :game-modes]
            (domain/get-game-modes-for-game dependencies (:eid model))))

(defn get-socials-for-game
  [dependencies model]
  (assoc-in model [:_embedded :socials]
            (domain/get-socials-for-game dependencies (:eid model))))

(def game-embed-registry
  {:factions   get-factions-for-game
   :game-modes get-game-modes-for-game
   :socials    get-socials-for-game})

(def game-query-parameters
  (schema.contract/to-schema
   [:map
    [:version {:optional true} :pos-int]
    [:embed {:optional true} [:or :string [:sequential :string]]]]))

(defn load-units-by-category-for-faction
  [dependencies model]
  (let [units             (domain/get-units-for-faction dependencies (:eid model))
        units-by-category (->> units
                               (partition-by :unit-category-name)
                               (mapv (fn [group]
                                       {:category (:unit-category-name (first group))
                                        :units    (vec group)})))]
    (assoc-in model [:_embedded :units-by-category] units-by-category)))

(def faction-embed-registry
  {:units-by-category load-units-by-category-for-faction})

(def faction-query-parameters
  (schema.contract/to-schema
   [:map
    [:version {:optional true} :pos-int]
    [:embed {:optional true} [:or :string [:sequential :string]]]]))

(defn get-draft-by-eid
  [dependencies eid]
  (or (domain/get-draft-by-eid dependencies eid)
      {:type :missing/resource :name "draft" :id eid}))

(defn create-draft
  [dependencies create-specification]
  (domain/create-draft dependencies create-specification))

(defn get-unit-by-eid
  [dependencies eid]
  (or (domain/get-unit-by-eid dependencies eid)
      {:type :missing/resource :name "unit" :id eid}))

(defn get-faction-by-eid
  [dependencies eid]
  (or (domain/get-faction-by-eid dependencies eid)
      {:type :missing/resource :name "faction" :id eid}))

(defn load-factions-for-faction-game
  [dependencies model]
  (assoc-in model [:_embedded :factions]
            (domain/get-factions-for-game dependencies (:game-eid model))))

(defn get-game-social-link-by-eid
  [dependencies eid]
  (or (domain/get-game-social-link-by-eid dependencies eid)
      {:type :missing/resource :name "social link" :id eid}))

(defn get-games
  [dependencies {:keys [hostname router]}]
  {:type      :collection/game
   :_embedded {:results (domain/get-games dependencies)}
   :_links    {:self (str hostname "/"
                          (-> router
                              (reitit.core/match-by-name! :collection/game)
                              :path))}})

(defmethod integrant.core/init-key ::get-faction
  [_init-key dependencies]
  (fn [{{{:keys [eid]}   :path
         {:keys [embed]} :query} :parameters
        router                    :reitit.core/router
        :as                       _request}]
    (let [embed-set (some-> embed (as-> e (set (map keyword (if (string? e) [e] e)))))]
      (web.core/handle-fetch-response
       domain/faction-resource
       {:hostname (:hostname dependencies) :router router}
       #(web.core/apply-embeds faction-embed-registry dependencies embed-set
                               (get-faction-by-eid dependencies eid))))))

(defmethod integrant.core/init-key ::get-game-social-link
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        router                :reitit.core/router
        :as                   _request}]
    (web.core/handle-fetch-response
     domain/game-social-link-resource
     {:hostname (:hostname dependencies) :router router}
     #(get-game-social-link-by-eid dependencies eid))))

(defmethod integrant.core/init-key ::get-game
  [_init-key dependencies]
  (fn [{{{:keys [eid]}   :path
         {:keys [embed]} :query} :parameters
        router                    :reitit.core/router
        :as                       _request}]
    (let [embed-set (some-> embed (as-> e (set (map keyword (if (string? e) [e] e)))))]
      (web.core/handle-fetch-response
       domain/game-resource
       {:hostname (:hostname dependencies) :router router}
       #(web.core/apply-embeds game-embed-registry dependencies embed-set
                               (get-game-by-eid dependencies eid))))))

(defmethod integrant.core/init-key ::create-draft
  [_init-key dependencies]
  (fn [{{{:keys [faction-eid game-mode-eid game-eid]} :body
         {:keys [version]}                            :query
         {:keys [eid]}                                :path} :parameters
        router                                                :reitit.core/router
        session                                               :ory-session
        :as                                                   _request}]
    (let [response (web.core/handle-create-response
                    domain/draft-resource
                    {:hostname (:hostname dependencies) :router router}
                    #(create-draft dependencies
                                   {:faction-eid    faction-eid
                                    :game-mode-eid  game-mode-eid
                                    :player-sub     (get-in session [:identity :id])
                                    :created-by-sub (get-in session [:identity :id])
                                    :eid            eid
                                    :version        version}))]
      (assoc-in response [:headers "HX-Redirect"] (str "/view/game/" game-eid "/draft/" eid "/index.html")))))

(defmethod integrant.core/init-key ::get-games
  [_init-key dependencies]
  (fn [{router :reitit.core/router :as _request}]
    (web.core/handle-fetch-response
     domain/game-collection-resource
     {:hostname (:hostname dependencies) :router router}
     #(get-games dependencies {:hostname (:hostname dependencies) :router router}))))
