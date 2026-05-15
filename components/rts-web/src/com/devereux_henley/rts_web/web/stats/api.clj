(ns com.devereux-henley.rts-web.web.stats.api
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]
   [integrant.core]
   [reitit.core]))

(defn- self-link
  [hostname router query]
  (str hostname
       (-> router
           (reitit.core/match-by-name! :collection/faction-standings)
           (reitit.core/match->path query))))

(defmethod integrant.core/init-key ::get-faction-standings
  [_init-key dependencies]
  (fn [{{{:keys [game-eid league-eid season-eid]} :query} :parameters
        router                                            :reitit.core/router
        :as                                               _request}]
    (let [hostname (:hostname dependencies)
          base     {:type   :collection/faction-standings
                    :_links {:self (self-link hostname router
                                              (cond-> {}
                                                game-eid   (assoc :game-eid game-eid)
                                                league-eid (assoc :league-eid league-eid)
                                                season-eid (assoc :season-eid season-eid)))}}
          payload  (cond
                     season-eid (domain/get-season-faction-standings dependencies season-eid)
                     league-eid (domain/get-league-faction-standings dependencies league-eid)
                     game-eid   (domain/get-game-faction-standings dependencies game-eid)
                     :else      {:rows []})]
      {:status 200
       :body   (merge base
                      (select-keys payload [:rows :game-eid :league-eid :season-eid]))})))
