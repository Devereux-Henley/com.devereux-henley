(ns com.devereux-henley.rts-web.web.actions.match-game
  "/actions handlers for the per-game confirmation gate on the Player Console:
  the non-uploading player confirms a submitted game (advancing the series if it
  clinches) or disputes it (opening a ticket and pausing the series). Confirm and
  dispute fire HX-Triggers in the `:player-series` group; a dispute additionally
  fires the `:dispute-queue` group so the organizer console refreshes."
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.orchestration :as orchestration]
   [com.devereux-henley.rts-web.render :as render]
   [com.devereux-henley.rts-web.web.actions.common :as common]
   [integrant.core]))

(derive ::web-triggers ::orchestration/web-trigger-source)

(defmethod integrant.core/init-key ::web-triggers
  [_init-key _config]
  {:player-series ["game-submitted" "game-confirmed" "game-disputed"]
   :dispute-queue ["dispute-opened"]})

(defn- series-context
  "Template context shared by the confirm/dispute response fragments and the
  inline series-view panels, so both render with the same variable names:
  `game-eid` (the catalog game, for the series back-link), `data.eid` (the
  tournament), `match-eid`, and `pending-game.eid` (the match-game). Resolves the
  tournament once to recover its catalog `:game-eid`."
  [dependencies tournament-eid match-eid game-eid]
  (let [tournament (domain/get-tournament-by-eid dependencies tournament-eid)]
    {:game-eid     (:game-eid tournament)
     :data         {:eid tournament-eid}
     :match-eid    match-eid
     :pending-game {:eid game-eid}}))

(defmethod integrant.core/init-key ::confirm-game
  [_init-key dependencies]
  (fn [{{{:keys [tournament-eid match-eid game-eid]} :path} :parameters
        session                                             :ory-session
        :as                                                 _request}]
    (let [confirming-sub (get-in session [:identity :id])
          result         (domain/confirm-game dependencies match-eid game-eid confirming-sub)]
      (if (= :match-record/error (:type result))
        (common/error-fragment 422 (:message result))
        {:status  200
         :headers {"Content-Type" "text/html; charset=utf-8"
                   "HX-Trigger"   "game-confirmed"}
         :body    (render/render-component
                   "tournament/player-game-confirmed.html"
                   (merge (series-context dependencies tournament-eid match-eid game-eid)
                          {:match-complete? (:match-complete? result)
                           :match-winner    (:match-winner result)}))}))))

(defmethod integrant.core/init-key ::dispute-game
  [_init-key dependencies]
  (fn [{{{:keys [tournament-eid match-eid game-eid]} :path} :parameters
        session                                             :ory-session
        :as                                                 _request}]
    (let [reporter-sub (get-in session [:identity :id])
          result       (domain/dispute-game
                        dependencies
                        {:match-eid    match-eid
                         :game-eid     game-eid
                         :reporter-sub reporter-sub})]
      (if (= :match-record/error (:type result))
        (common/error-fragment 422 (:message result))
        {:status  200
         :headers {"Content-Type" "text/html; charset=utf-8"
                   ;; Both the player series panel and the organizer queue react.
                   "HX-Trigger"   "game-disputed, dispute-opened"}
         :body    (render/render-component
                   "tournament/player-step-disputed.html"
                   (series-context dependencies tournament-eid match-eid game-eid))}))))
