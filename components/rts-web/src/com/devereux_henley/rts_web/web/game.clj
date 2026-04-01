(ns com.devereux-henley.rts-web.web.game
  (:require
   [cats.core :as cats]
   [cats.monad.either :as either]
   [com.devereux-henley.http.contract :as web.core]
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.schema.contract :as schema.contract]
   [integrant.core]
   [reitit.core]
   [taoensso.timbre :as log]))

(def get-game-by-eid
  (web.core/standard-fetch domain/get-game-by-eid :game/game))

(def get-factions-for-game
  (web.core/standard-load-embedded domain/get-factions-for-game :factions :game/game))

(def get-game-modes-for-game
  (web.core/standard-load-embedded domain/get-game-modes-for-game :game-modes :game/game))

(def get-socials-for-game
  (web.core/standard-load-embedded domain/get-socials-for-game :socials :game/game))

(def game-embed-registry
  {:factions   get-factions-for-game
   :game-modes get-game-modes-for-game
   :socials    get-socials-for-game})

(def game-query-parameters
  (schema.contract/to-schema
   [:map
    [:version {:optional true} :pos-int]
    [:embed {:optional true} [:set (into [:enum] (keys game-embed-registry))]]]))

(defn load-units-by-category-for-faction
  [dependencies model]
  (try
    (let [units             (domain/get-units-for-faction dependencies (:eid model))
          units-by-category (->> units
                                 (partition-by :unit-category-name)
                                 (mapv (fn [group]
                                         {:category (:unit-category-name (first group))
                                          :units    (vec group)})))]
      (either/right (assoc-in model [:_embedded :units-by-category] units-by-category)))
    (catch Exception exc
      (log/error exc)
      (either/left (ex-info
                    "Failed to fetch units for faction."
                    {:error/kind :error/unknown
                     :model/eid  (:eid model)
                     :model/type :game/faction}
                    exc)))))

(def faction-embed-registry
  {:units-by-category load-units-by-category-for-faction})

(def faction-query-parameters
  (schema.contract/to-schema
   [:map
    [:version {:optional true} :pos-int]
    [:embed {:optional true} [:set (into [:enum] (keys faction-embed-registry))]]]))

(def get-draft-by-eid
  (web.core/standard-fetch domain/get-draft-by-eid :game/draft))

(def create-draft
  (web.core/standard-create domain/create-draft :game/draft))

(def get-unit-by-eid
  (web.core/standard-fetch domain/get-unit-by-eid :game/unit))

(def get-faction-by-eid
  (web.core/standard-fetch domain/get-faction-by-eid :game/faction))

(defn load-factions-for-faction-game
  [dependencies model]
  (try
    (either/right (assoc-in model [:_embedded :factions]
                             (domain/get-factions-for-game dependencies (:game-eid model))))
    (catch Exception exc
      (log/error exc)
      (either/left (ex-info
                    "Failed to fetch factions for game."
                    {:error/kind :error/unknown
                     :model/eid  (:eid model)
                     :model/type :game/faction}
                    exc)))))

(def get-game-social-link-by-eid
  (web.core/standard-fetch domain/get-game-social-link-by-eid :game/social))

(defn get-games
  [dependencies {:keys [hostname router]}]
  (try
    (either/right {:type      :collection/game
                   :_embedded {:results (domain/get-games dependencies)}
                   :_links    {:self (str hostname "/"
                                          (-> router
                                              (reitit.core/match-by-name! :collection/game)
                                              :path))}})
    (catch Exception exc
      (log/error exc)
      (either/left (ex-info
                    "Failed to fetch games."
                    {:error/kind :error/unknown
                     :model/type :collection/game}
                    exc)))))

(defmethod integrant.core/init-key ::get-faction
  [_init-key dependencies]
  (fn [{{{:keys [eid]}   :path
         {:keys [embed]} :query} :parameters
       router                    :reitit.core/router
       :as                       _request}]
    (web.core/handle-fetch-response
     domain/faction-resource
     {:hostname (:hostname dependencies) :router router}
     (cats/>>=
      (either/right eid)
      (partial get-faction-by-eid dependencies)
      (partial web.core/apply-embeds faction-embed-registry dependencies embed)))))

(defmethod integrant.core/init-key ::get-game-social-link
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
       router                :reitit.core/router
       :as                   _request}]
    (web.core/handle-fetch-response
     domain/game-social-link-resource
     {:hostname (:hostname dependencies) :router router}
     (cats/>>=
      (either/right eid)
      (partial get-game-social-link-by-eid dependencies)))))

(defmethod integrant.core/init-key ::get-game
  [_init-key dependencies]
  (fn [{{{:keys [eid]}   :path
         {:keys [embed]} :query} :parameters
       router                    :reitit.core/router
       :as                       _request}]
    (web.core/handle-fetch-response
     domain/game-resource
     {:hostname (:hostname dependencies) :router router}
     (cats/>>=
      (either/right eid)
      (partial get-game-by-eid dependencies)
      (partial web.core/apply-embeds game-embed-registry dependencies embed)))))

(defmethod integrant.core/init-key ::create-draft
  [_init-key dependencies]
  (fn [{{{:keys [specification]} :body
        {:keys [version]}       :query
        {:keys [eid]}           :path} :parameters
       router                          :reitit.core/router
       session                         :ory-session
       :as                             _request}]
    (web.core/handle-create-response
     domain/draft-resource
     {:hostname (:hostname dependencies) :router router}
     (cats/>>=
      (either/right (-> specification
                        (assoc :player-sub (get-in session [:identity :id]))
                        (assoc :created-by-sub (get-in session [:identity :id]))
                        (assoc :eid eid)
                        (assoc :version version)))
      (partial create-draft dependencies)))))

(defmethod integrant.core/init-key ::get-games
  [_init-key dependencies]
  (fn [{router :reitit.core/router :as _request}]
    (web.core/handle-fetch-response
     domain/game-collection-resource
     {:hostname (:hostname dependencies) :router router}
     (get-games dependencies {:hostname (:hostname dependencies) :router router}))))
