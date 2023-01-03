(ns com.devereux-henley.rts-api.web.game
  (:require
   [cats.core :as cats]
   [cats.monad.either :as either]
   [com.devereux-henley.schema.contract :as schema.contract]
   [com.devereux-henley.rts-api.schema :as schema]
   [com.devereux-henley.rts-api.handlers.game :as handlers.game]
   [com.devereux-henley.rts-api.web.core :as web.core]
   [integrant.core]
   [taoensso.timbre :as log]))

(def get-game-by-eid
  (web.core/standard-fetch handlers.game/get-game-by-eid :game/game))

(def get-factions-for-game
  (web.core/standard-load-embedded handlers.game/get-factions-for-game :factions :game/game))

(def get-socials-for-game
  (web.core/standard-load-embedded handlers.game/get-socials-for-game :socials :game/game))

(def get-faction-by-eid
  (web.core/standard-fetch handlers.game/get-faction-by-eid :game/faction))

(def get-game-social-link-by-eid
  (web.core/standard-fetch handlers.game/get-game-social-link-by-eid :game/social))

(defn get-games
  [dependencies {:keys [hostname router]}]
  (try
    (either/right {:type      :collection/game
                   :_embedded {:results (handlers.game/get-games dependencies)}
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
  (fn [{{{:keys [eid]} :path} :parameters
       router                :reitit.core/router
       :as                   _request}]
    (web.core/to-fetch-response
     schema/faction-resource
     {:hostname (:hostname dependencies) :router router}
     (cats/extract
      (cats/>>=
       (either/right eid)
       (partial get-faction-by-eid dependencies))))))

(defmethod integrant.core/init-key ::get-game-social-link
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
       router                :reitit.core/router
       :as                   _request}]
    (web.core/to-fetch-response
     schema/game-social-link-resource
     {:hostname (:hostname dependencies) :router router}
     (cats/extract
      (cats/>>=
       (either/right eid)
       (partial get-game-social-link-by-eid dependencies))))))

(defmethod integrant.core/init-key ::get-game
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
       router                :reitit.core/router
       :as                   _request}]
    (web.core/to-fetch-response
     schema/game-resource
     {:hostname (:hostname dependencies) :router router}
     (cats/extract
      (cats/>>=
       (either/right eid)
       (partial get-game-by-eid dependencies)
       (partial get-socials-for-game dependencies)
       (partial get-factions-for-game dependencies))))))

(defmethod integrant.core/init-key ::get-games
  [_init-key dependencies]
  (fn [{router :reitit.core/router :as _request}]
    (web.core/to-fetch-response
     schema/game-collection-resource
     {:hostname (:hostname dependencies) :router router}
     (cats/extract (get-games dependencies {:hostname (:hostname dependencies) :router router})))))

(comment
  (require '[com.devereux-henley.rts-api.system :as rts-api.system])
  (require '[reitit.core])
  (def connection (:com.devereux-henley.rts-api.db/connection (rts-api.system/get-system)))
  (def router (reitit.core/router (:com.devereux-henley.rts-api.web/routes (rts-api.system/get-system)))))
