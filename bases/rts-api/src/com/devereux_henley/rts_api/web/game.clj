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

(defn encode-game
  [router game]
  (malli.core/encode schema/game-resource game (schema.contract/model-transformer router)))

(comment
  (encode-game {:description "The single most poggers warhammer game.", :created_by_id "f0ce7395-a57f-41e9-ade0-fd13bafc058f", :deleted_at nil, :name "Total War: Warhammer III", :type :game/game, :updated_at "2022-12-18 17:38:15", :id 1, :eid #uuid "eea787d7-1065-45eb-a3f6-e26f32c294a1", :version 1, :created_at "2022-12-18 17:38:15"})
  )

(defn to-fetch-response
  [resource-schema router value]
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
     :body   (encode-game router value)}))

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
  [dependencies]
  (try
    (either/right (handlers.game/get-games dependencies))
    (catch Exception exc
      (either/left (ex-info
                    "Failed to fetch games."
                    {:error/kind :error/unknown
                     :model/type :game/game}
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
     (cats/extract (get-games dependencies)))))
