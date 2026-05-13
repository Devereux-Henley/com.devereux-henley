(ns com.devereux-henley.rts-web.web.actions.draft
  "/actions handlers for draft mutations. Each delegates the domain
  work to rts-domain, returns the same typed-map response body the /api
  variants returned (so view-by-type rendering and the existing OOB
  swap templates keep working in-place), and adds an HX-Trigger header
  so future listener-bound /components fragments can react granularly."
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.web.actions.common :as common]
   [integrant.core]))

(defmethod integrant.core/init-key ::add-unit
  [_init-key dependencies]
  (fn [{{{:keys [draft-eid eid]} :path
         {:keys [section]}       :query
         body                    :body} :parameters
        :as                             _request}]
    (let [selections (select-keys (or body {}) [:mount :level :abilities :spells :items])
          result     (domain/add-unit-to-draft dependencies draft-eid eid section selections)]
      (case (:type result)
        :draft/add-success (common/trigger-response "draft-entry-created" result)
        :draft/locked      {:status 409 :body result}
        {:status 422 :body result}))))

(defmethod integrant.core/init-key ::update-entry
  [_init-key dependencies]
  (fn [{{{:keys [draft-eid eid]} :path
         {:keys [section]}       :query
         body                    :body} :parameters
        :as                             _request}]
    (let [selections (select-keys (or body {}) [:unit-eid :mount :level :abilities :spells :items])
          result     (domain/update-unit-in-draft dependencies draft-eid eid section selections)]
      (case (:type result)
        :draft/update-success
        ;; Reuse the same shape the /api endpoint returned: include the
        ;; freshly-persisted entry + embedded unit so the OOB swaps in
        ;; draft-update-success.html refresh sidebar slot + budget.
        (let [entry (some->> (domain/get-draft-entry-details dependencies draft-eid eid section)
                             (domain/embed-unit-for-entry dependencies))]
          (common/trigger-response "draft-entry-updated" (assoc result :entry entry)))

        :draft/locked {:status 409 :body result}
        {:status 422 :body result}))))

(defmethod integrant.core/init-key ::remove-entry
  [_init-key dependencies]
  (fn [{{{:keys [draft-eid eid]} :path
         {:keys [section]}       :query} :parameters
        :as                              _request}]
    (let [result (domain/remove-unit-from-draft dependencies draft-eid eid section)]
      (case (:type result)
        :draft/remove-success (common/trigger-response "draft-entry-deleted" result)
        :draft/locked         {:status 409 :body result}
        {:status 422 :body result}))))

(defmethod integrant.core/init-key ::create-draft
  [_init-key dependencies]
  (fn [{{{:keys [faction-eid game-mode-eid game-eid name]} :body
         {:keys [version]}                                 :query
         {:keys [eid]}                                     :path} :parameters
        session                                                   :ory-session
        :as                                                       _request}]
    (let [result (domain/create-draft
                  dependencies
                  {:faction-eid    faction-eid
                   :game-mode-eid  game-mode-eid
                   :name           name
                   :player-sub     (get-in session [:identity :id])
                   :created-by-sub (get-in session [:identity :id])
                   :eid            eid
                   :version        version})]
      (if (= :game/draft (:type result))
        (common/redirect-response
         (str "/view/game/" game-eid "/draft/" eid "/index.html"))
        (common/error-fragment 422 (:message result))))))

(defmethod integrant.core/init-key ::update-draft
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path
         body          :body} :parameters
        :as                   _request}]
    (let [result (domain/update-draft dependencies eid (select-keys (or body {}) [:name]))]
      (case (:type result)
        :game/draft   (common/trigger-response "draft-updated" result)
        :draft/locked {:status 409 :body result}
        {:status 500 :body result}))))
