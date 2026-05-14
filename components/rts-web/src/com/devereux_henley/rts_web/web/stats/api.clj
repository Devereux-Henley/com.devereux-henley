(ns com.devereux-henley.rts-web.web.stats.api
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]
   [integrant.core]))

(defmethod integrant.core/init-key ::get-game-faction-standings
  [_init-key dependencies]
  (fn [{{{:keys [game-eid]} :path} :parameters
        :as                        _request}]
    {:status 200 :body (domain/get-game-faction-standings dependencies game-eid)}))

(defmethod integrant.core/init-key ::get-league-faction-standings
  [_init-key dependencies]
  (fn [{{{:keys [league-eid]} :path} :parameters
        :as                          _request}]
    {:status 200 :body (domain/get-league-faction-standings dependencies league-eid)}))

(defmethod integrant.core/init-key ::get-season-faction-standings
  [_init-key dependencies]
  (fn [{{{:keys [season-eid]} :path} :parameters
        :as                          _request}]
    {:status 200 :body (domain/get-season-faction-standings dependencies season-eid)}))
