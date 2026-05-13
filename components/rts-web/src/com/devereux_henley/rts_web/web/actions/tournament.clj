(ns com.devereux-henley.rts-web.web.actions.tournament
  "/actions handlers for tournament mutations. Body shape mirrors the
  /api variants so existing view-by-type templates and OOB swaps keep
  working; HX-Trigger header is added for future listener-bound
  refresh."
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.web.actions.common :as common]
   [integrant.core]))

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

(defmethod integrant.core/init-key ::update-status
  [_init-key dependencies]
  (fn [{{{:keys [eid]}    :path
         {:keys [status]} :body} :parameters
        session                  :ory-session
        :as                      _request}]
    (let [user-sub (get-in session [:identity :id])
          result   (domain/advance-tournament dependencies eid status user-sub)]
      (if (= :tournament/advance-success (:type result))
        (common/trigger-response "tournament-status-updated" result)
        {:status 422 :body result}))))

(defmethod integrant.core/init-key ::update-registration
  [_init-key dependencies]
  (fn [{{{:keys [eid]}          :path
         {:keys [closed-early]} :body} :parameters
        session                        :ory-session
        :as                            _request}]
    (let [user-sub (get-in session [:identity :id])
          result   (if closed-early
                     (domain/close-registration-early dependencies eid user-sub)
                     {:type :tournament/registration-error :message "No updates specified."})]
      (if (= :tournament/close-registration-success (:type result))
        (common/trigger-response "tournament-registration-updated" result)
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
