(ns com.devereux-henley.rts-web.web.game.api
  (:require
   [com.devereux-henley.http.contract :as web.core]
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.schema.contract :as schema.contract]
   [integrant.core]
   [reitit.core]))

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

(defn- ok-or-404
  [result]
  (if (= :missing/resource (:type result))
    {:status 404 :body result}
    {:status 200 :body result}))

(defmethod integrant.core/init-key ::get-faction
  [_init-key dependencies]
  (fn [{{{:keys [eid]}   :path
         {:keys [embed]} :query} :parameters
        :as                      _request}]
    (let [embed-set (some->> (web.core/query-param->vec embed) (into #{} (map keyword)))]
      (ok-or-404
       (web.core/apply-embeds faction-embed-registry dependencies embed-set
                              (get-faction-by-eid dependencies eid))))))

(defmethod integrant.core/init-key ::get-game-social-link
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        :as                   _request}]
    (ok-or-404 (get-game-social-link-by-eid dependencies eid))))

(defmethod integrant.core/init-key ::get-game
  [_init-key dependencies]
  (fn [{{{:keys [eid]}   :path
         {:keys [embed]} :query} :parameters
        :as                      _request}]
    (let [embed-set (some->> (web.core/query-param->vec embed) (into #{} (map keyword)))]
      (ok-or-404
       (web.core/apply-embeds game-embed-registry dependencies embed-set
                              (get-game-by-eid dependencies eid))))))

(defn get-factions
  [dependencies game-eid {:keys [hostname router]}]
  {:type      :collection/faction
   :_embedded {:results (domain/get-factions-for-game dependencies game-eid)}
   :_links    {:self (str hostname
                          (-> router
                              (reitit.core/match-by-name! :faction/for-game)
                              (reitit.core/match->path {:game-eid game-eid})))}})

(defn get-socials
  [dependencies game-eid {:keys [hostname router]}]
  {:type      :collection/game-social-link
   :_embedded {:results (domain/get-socials-for-game dependencies game-eid)}
   :_links    {:self (str hostname
                          (-> router
                              (reitit.core/match-by-name! :game-social-link/for-game)
                              (reitit.core/match->path {:game-eid game-eid})))}})

(defmethod integrant.core/init-key ::get-factions-collection
  [_init-key dependencies]
  (fn [{{{:keys [game-eid]} :query} :parameters
        router                      :reitit.core/router
        :as                         _request}]
    {:status 200
     :body   (get-factions dependencies game-eid
                           {:hostname (:hostname dependencies) :router router})}))

(defmethod integrant.core/init-key ::get-socials-collection
  [_init-key dependencies]
  (fn [{{{:keys [game-eid]} :query} :parameters
        router                      :reitit.core/router
        :as                         _request}]
    {:status 200
     :body   (get-socials dependencies game-eid
                          {:hostname (:hostname dependencies) :router router})}))

(defmethod integrant.core/init-key ::get-games
  [_init-key dependencies]
  (fn [{router :reitit.core/router :as _request}]
    {:status 200
     :body   (get-games dependencies {:hostname (:hostname dependencies) :router router})}))
