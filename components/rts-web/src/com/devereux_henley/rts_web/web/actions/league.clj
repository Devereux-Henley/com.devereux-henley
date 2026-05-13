(ns com.devereux-henley.rts-web.web.actions.league
  "/actions handler for league creation. PUT redirects via 303 to the
   new league page; failures return an inline error fragment."
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.web.actions.common :as common]
   [integrant.core]))

(defmethod integrant.core/init-key ::create-league
  [_init-key dependencies]
  (fn [{{{:keys [name description game-eid]} :body
         {:keys [version]}                   :query
         {:keys [eid]}                       :path} :parameters
        session                                     :ory-session
        :as                                         _request}]
    (let [result (domain/create-league
                  dependencies
                  {:eid            eid
                   :game-eid       game-eid
                   :name           name
                   :description    description
                   :created-by-sub (get-in session [:identity :id])
                   :version        (or version 1)})]
      (if (= :game/league (:type result))
        (common/redirect-response
         (str "/view/game/" game-eid "/league/" eid "/index.html"))
        (common/error-fragment 422 (:message result))))))
