(ns com.devereux-henley.rts-web.web.draft
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]
   [integrant.core]))

(defmethod integrant.core/init-key ::get-draft-unit
  [_init-key dependencies]
  (fn [request]
    (let [{{{:keys [eid unit-eid]} :path} :parameters} request]
      {:status 200 :body (domain/get-draft-unit-details dependencies eid unit-eid)})))

(defmethod integrant.core/init-key ::draft-add-unit
  [_init-key dependencies]
  (fn [request]
    (let [{{{:keys [eid unit-eid]} :path
            {:keys [section]}      :query} :parameters} request
          result (domain/add-unit-to-draft dependencies eid unit-eid section)]
      {:status (if (= :draft/add-success (:type result)) 200 422)
       :body   result})))

(defmethod integrant.core/init-key ::draft-remove-unit
  [_init-key dependencies]
  (fn [request]
    (let [{{{:keys [eid unit-eid]} :path
            {:keys [section]}      :query} :parameters} request]
      {:status 200 :body (domain/remove-unit-from-draft dependencies eid unit-eid section)})))
