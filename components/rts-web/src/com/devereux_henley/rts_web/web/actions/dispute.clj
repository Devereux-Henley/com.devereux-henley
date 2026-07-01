(ns com.devereux-henley.rts-web.web.actions.dispute
  "/actions handlers for the organizer dispute queue: resolve and dismiss.
  Both mutations fire an HX-Trigger in the `:dispute-queue` group so the
  organizer console reloads its disputes tab and the tab-badge count."
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.orchestration :as orchestration]
   [com.devereux-henley.rts-web.web.actions.common :as common]
   [integrant.core]))

(derive ::web-triggers ::orchestration/web-trigger-source)

(defmethod integrant.core/init-key ::web-triggers
  [_init-key _config]
  {:dispute-queue ["dispute-resolved"
                   "dispute-dismissed"]})

(defmethod integrant.core/init-key ::resolve-dispute
  [_init-key dependencies]
  (fn [{{{:keys [eid]}                                    :path
         {:keys [player-one-score player-two-score note]} :body} :parameters
        :as                                                      _request}]
    (let [result (domain/resolve-dispute
                  dependencies eid
                  {:player-one-score (parse-long player-one-score)
                   :player-two-score (parse-long player-two-score)
                   :note             (not-empty note)})]
      (if (= :dispute/error (:type result))
        (common/error-oob 422 {:type    :dispute/resolve-error
                               :eid     eid
                               :message (:message result)})
        (common/trigger-response "dispute-resolved" result)))))

(defmethod integrant.core/init-key ::dismiss-dispute
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        :as                   _request}]
    (let [result (domain/dismiss-dispute dependencies eid)]
      (if (= :dispute/error (:type result))
        (common/error-fragment 422 (:message result))
        (common/trigger-response "dispute-dismissed" result)))))
