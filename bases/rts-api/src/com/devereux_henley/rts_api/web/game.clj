(ns com.devereux-henley.rts-api.web.game
  (:require
   [cats.core :as cats]
   [cats.monad.either :as either]
   [com.devereux-henley.schema.contract :as schema.contract]
   [com.devereux-henley.rts-api.schema :as schema]
   [com.devereux-henley.rts-api.handlers.game :as handlers.game]
   [integrant.core]
   [malli.core]
   [malli.generator])
  (:import
   [java.time LocalDate]))

(defn encode-value
  [router resource-schema value]
  (malli.core/encode resource-schema value (schema.contract/model-transformer router)))

(defn to-fetch-response
  [resource-schema router value]
  (println value)
  (condp instance? value
    clojure.lang.ExceptionInfo
    (case (:error/kind (ex-data value))
      :error/missing {:status 404
                      :body   (select-keys [:error/kind :model/id :error/message] (ex-data value))}
      :error/unknown {:status 500
                      :body   (select-keys [:error/kind :model/id :error/message] (ex-data value))}
      {:status 500
       :body   (select-keys [:error/kind :model/id :error/message] (ex-data value))})
    Throwable
    {:status 500
     :body   {}}
    {:status 200
     :body   (encode-value router resource-schema value)}))

;; TODO :reitit.core/router from request
(defn get-game-by-id
  [dependencies id]
  (try
    (if-let [game (handlers.game/get-game-by-id dependencies id)]
      (either/right game)
      (either/left (ex-info
                    "No game with given id."
                    {:error/kind :error/missing
                     :model/id   id
                     :model/type :game/game})))
    (catch Exception exc
      (println exc)
      (either/left (ex-info
                    "Failed to fetch game."
                    {:error/kind :error/unknown
                     :model/id   id
                     :model/type :game/game}
                    exc)))))

(defn get-games
  [dependencies router]
  (try
    (either/right {:type      :collection/game
                   :_embedded {:results (handlers.game/get-games dependencies)}
                   :_links    {:self (-> router (reitit.core/match-by-name! :collection/game) :path)}})
    (catch Exception exc
      (println exc)
      (either/left (ex-info
                    "Failed to fetch games."
                    {:error/kind :error/unknown
                     :model/type :collection/game}
                    exc)))))

(defmethod integrant.core/init-key ::get-game
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
       router :reitit.core/router
       :as _request}]
    (to-fetch-response
     schema/game-resource
     router
     (cats/extract
      (cats/>>=
       (either/right eid)
       (partial get-game-by-id dependencies))))))

(defmethod integrant.core/init-key ::get-games
  [_init-key dependencies]
  (fn [{router :reitit.core/router :as _request}]
    (to-fetch-response
     schema/game-collection-resource
     router
     (cats/extract (get-games dependencies router)))))
