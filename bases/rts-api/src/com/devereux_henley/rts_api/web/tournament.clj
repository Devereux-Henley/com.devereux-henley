(ns com.devereux-henley.rts-api.web.tournament
  (:require
   [cats.core :as cats]
   [cats.monad.either :as either]
   [com.devereux-henley.schema.contract :as schema.contract]
   [com.devereux-henley.rts-api.schema :as schema]
   [com.devereux-henley.rts-api.handlers.tournament :as handlers.tournament]
   [com.devereux-henley.rts-api.web.core :as web.core]
   [integrant.core]
   [taoensso.timbre :as log]))

(def get-tournament-by-eid
  (web.core/standard-fetch handlers.tournament/get-tournament-by-eid :tournament/tournament))

(def get-tournament-snapshot-by-eid
  (web.core/standard-fetch handlers.tournament/get-tournament-snapshot-by-eid :tournament/snapshot))

(def get-snapshot-for-tournament
  (web.core/standard-load-embedded
   handlers.tournament/get-tournament-snapshot-by-tournament-eid
   :snapshot
   :tournament/snapshot))

(def get-tournaments
  (web.core/standard-fetch-collection handlers.tournament/get-tournaments :collection/tournament))

(def create-tournament
  (web.core/standard-create handlers.tournament/create-tournament :tournament/tournament))

(defmethod integrant.core/init-key ::get-tournament-snapshot
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
       router                :reitit.core/router
       :as                   _request}]
    (web.core/handle-fetch-response
     schema/tournament-snapshot-resource
     {:hostname (:hostname dependencies) :router router}
     (cats/>>=
      (either/right eid)
      (partial get-tournament-snapshot-by-eid dependencies)))))

(defmethod integrant.core/init-key ::get-tournament
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
       router                :reitit.core/router
       :as                   _request}]
    (web.core/handle-fetch-response
     schema/tournament-resource
     {:hostname (:hostname dependencies) :router router}
     (cats/>>=
      (either/right eid)
      (partial get-tournament-by-eid dependencies)
      (partial get-snapshot-for-tournament dependencies)))))

(defmethod integrant.core/init-key ::get-tournaments
  [_init-key dependencies]
  (fn [{{{:keys [since size offset]} :query} :parameters
       router                               :reitit.core/router
       :as                                  _request}]
    (web.core/handle-fetch-response
     schema/tournament-collection-resource
     {:hostname (:hostname dependencies) :router router}
     (cats/>>=
      (either/right {:since since :size size :offset offset})
      (partial get-tournaments dependencies)))))

;; TODO Handle 301
;; TODO Embeds.
(defmethod integrant.core/init-key ::create-tournament
  [_init-key dependencies]
  (fn [{{{:keys [specification]} :body
        {:keys [version]}       :query
        {:keys [eid]}           :path} :parameters
       router                                 :reitit.core/router
       :as                                    _request}]
    (web.core/handle-create-response
     schema/tournament-resource
     {:hostname (:hostname dependencies) :router router}
     (cats/>>=
      (either/right (-> specification
                        (assoc :created-by-sub "TODO Replace Me.") ;; TODO Use sub from session.
                        (assoc :eid eid)
                        (assoc :version version)))
      (partial create-tournament dependencies)))))
