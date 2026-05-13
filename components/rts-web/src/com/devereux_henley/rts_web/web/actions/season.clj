(ns com.devereux-henley.rts-web.web.actions.season
  "/actions handler for season creation. Looks up the parent league to
   derive the game-eid for the redirect URL (the create-season form
   doesn't carry game-eid)."
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.web.actions.common :as common]
   [integrant.core]))

(defmethod integrant.core/init-key ::create-season
  [_init-key dependencies]
  (fn [{{{:keys [name timezone start-at end-at]} :body
         {:keys [eid league-eid]}                :path} :parameters
        session                                         :ory-session
        :as                                             _request}]
    (let [user-sub (get-in session [:identity :id])
          result   (domain/create-season
                    dependencies
                    {:eid        eid
                     :league-eid league-eid
                     :name       name
                     :timezone   timezone
                     :start-at   start-at
                     :end-at     end-at}
                    user-sub)]
      (if (= :season/error (:type result))
        (common/error-fragment 422 (:message result))
        (let [league   (domain/get-league-by-eid dependencies league-eid)
              game-eid (:game-eid league)]
          (common/redirect-response
           (str "/view/game/" game-eid
                "/league/" league-eid "/season/" eid "/index.html")))))))
