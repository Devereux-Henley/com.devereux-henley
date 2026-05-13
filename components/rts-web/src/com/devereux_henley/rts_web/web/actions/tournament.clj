(ns com.devereux-henley.rts-web.web.actions.tournament
  "/actions handlers for tournament mutations. Body shape mirrors the
  /api variants so existing view-by-type templates and OOB swaps keep
  working; HX-Trigger header is added for future listener-bound
  refresh."
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.orchestration :as orchestration]
   [com.devereux-henley.rts-web.web.actions.common :as common]
   [integrant.core]))

(derive ::web-triggers ::orchestration/web-trigger-source)

(defmethod integrant.core/init-key ::web-triggers
  [_init-key _config]
  {:tournament-stage ["tournament-started"
                      "tournament-completed"
                      "tournament-cancelled"
                      "tournament-entry-created"
                      "tournament-entry-deleted"
                      "tournament-registration-closed"
                      "tournament-round-created"
                      "tournament-match-created"
                      "tournament-match-result-recorded"
                      "tournament-game-recorded"
                      "tournament-phase-configured"]})

(defmethod integrant.core/init-key ::create-tournament
  [_init-key dependencies]
  (fn [{{{:keys [name description game-eid league-eid season-eid timezone
                 registration-opens-at registration-closes-at]}           :body
         {:keys [version]}                                                :query
         {:keys [eid]}                                                    :path} :parameters
        session                                                                  :ory-session
        :as                                                                      _request}]
    (let [result (domain/create-tournament
                  dependencies
                  (cond-> {:eid                    eid
                           :game-eid               game-eid
                           :name                   name
                           :description            description
                           :timezone               timezone
                           :registration-opens-at  registration-opens-at
                           :registration-closes-at registration-closes-at
                           :created-by-sub         (get-in session [:identity :id])
                           :version                version}
                    league-eid (assoc :league-eid league-eid)
                    season-eid (assoc :season-eid season-eid)))]
      (if (= :tournament/create-error (:type result))
        (common/error-fragment 422 (:message result))
        (common/redirect-response
         (str "/view/game/" game-eid "/tournament/" eid "/index.html"))))))

(defmethod integrant.core/init-key ::create-entry
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        session               :ory-session
        :as                   _request}]
    (let [player-sub (get-in session [:identity :id])
          result     (domain/create-entry dependencies eid player-sub)]
      (if (= :tournament/entry-error (:type result))
        {:status 422 :body result}
        (common/trigger-status-response 201 "tournament-entry-created" result)))))

(defmethod integrant.core/init-key ::delete-entry
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        session               :ory-session
        :as                   _request}]
    (let [player-sub (get-in session [:identity :id])
          result     (domain/delete-entry dependencies eid player-sub)]
      (if (= :tournament/entry-error (:type result))
        {:status 422 :body result}
        (common/trigger-response "tournament-entry-deleted" result)))))

(defmethod integrant.core/init-key ::start-tournament
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        session               :ory-session
        :as                   _request}]
    (let [user-sub (get-in session [:identity :id])
          result   (domain/start-tournament dependencies eid user-sub)]
      (if (= :tournament/started (:type result))
        (common/trigger-response "tournament-started" result)
        {:status 422 :body result}))))

(defmethod integrant.core/init-key ::complete-tournament
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        session               :ory-session
        :as                   _request}]
    (let [user-sub (get-in session [:identity :id])
          result   (domain/complete-tournament dependencies eid user-sub)]
      (if (= :tournament/completed (:type result))
        (common/trigger-response "tournament-completed" result)
        {:status 422 :body result}))))

(defmethod integrant.core/init-key ::cancel-tournament
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        session               :ory-session
        :as                   _request}]
    (let [user-sub (get-in session [:identity :id])
          result   (domain/cancel-tournament dependencies eid user-sub)]
      (if (= :tournament/cancelled (:type result))
        (common/trigger-response "tournament-cancelled" result)
        {:status 422 :body result}))))

(defmethod integrant.core/init-key ::close-registration
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        session               :ory-session
        :as                   _request}]
    (let [user-sub (get-in session [:identity :id])
          result   (domain/close-registration-early dependencies eid user-sub)]
      (if (= :tournament/registration-closed (:type result))
        (common/trigger-response "tournament-registration-closed" result)
        {:status 422 :body result}))))

(defmethod integrant.core/init-key ::create-round
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        session               :ory-session
        :as                   _request}]
    (let [user-sub (get-in session [:identity :id])
          result   (domain/generate-next-round dependencies eid user-sub)]
      (if (= :tournament/phase-error (:type result))
        {:status 422 :body result}
        (common/trigger-response "tournament-round-created" result)))))

(defmethod integrant.core/init-key ::create-match
  [_init-key dependencies]
  (fn [{{{:keys [eid]}                                  :path
         {:keys [phase-index round-index
                 player-one-sub player-two-sub format]} :body} :parameters
        :as                                                    _request}]
    (let [result (domain/create-match
                  dependencies
                  eid
                  (cond-> {:phase-index    phase-index
                           :round-index    round-index
                           :player-one-sub player-one-sub
                           :player-two-sub player-two-sub}
                    format (assoc :format format)))]
      (if (= :tournament/match-error (:type result))
        {:status 422 :body result}
        (common/trigger-status-response 201 "tournament-match-created" result)))))

(defmethod integrant.core/init-key ::update-match-result
  [_init-key dependencies]
  (fn [{{{:keys [match-eid]}  :path
         {:keys [winner-sub]} :body} :parameters
        :as                          _request}]
    (let [result (domain/update-match-result dependencies match-eid winner-sub)]
      (if (= :tournament/match-error (:type result))
        {:status 422 :body result}
        (common/trigger-response "tournament-match-result-recorded" result)))))

(defmethod integrant.core/init-key ::record-game
  [_init-key dependencies]
  (fn [{{{:keys [match-eid]}  :path
         {:keys [winner-sub]} :body} :parameters
        :as                          _request}]
    (let [result (domain/record-game-result dependencies match-eid winner-sub)]
      (if (= :tournament/match-error (:type result))
        {:status 422 :body result}
        (common/trigger-response "tournament-game-recorded" result)))))

(defmethod integrant.core/init-key ::update-phase-configuration
  [_init-key dependencies]
  (fn [{{{:keys [eid]}                    :path
         {:keys [phases qualifier-count]} :body} :parameters
        session                                  :ory-session
        :as                                      _request}]
    (let [user-sub (get-in session [:identity :id])
          result   (domain/configure-phases dependencies eid
                                            {:phases phases :qualifier-count qualifier-count}
                                            user-sub)]
      (if (= :tournament/phase-error (:type result))
        {:status 422 :body result}
        (common/trigger-response "tournament-phase-configured" result)))))
