(ns com.devereux-henley.rts-web.web.draft
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]
   [integrant.core]
   [taoensso.timbre :as log]))

(defmethod integrant.core/init-key ::get-draft-unit
  [_init-key dependencies]
  (fn [request]
    (try
      (let [{{{:keys [eid unit-eid]} :path} :parameters} request]
        {:status 200
         :body   (domain/get-draft-unit-details dependencies eid unit-eid)})
      (catch Exception exc
        (log/error exc)
        {:status 500
         :body   {:type :draft/add-error :message "Failed to load unit details."}}))))

(defmethod integrant.core/init-key ::draft-add-unit
  [_init-key dependencies]
  (fn [request]
    (try
      (let [{{{:keys [eid unit-eid]} :path
              {:keys [section]}      :query} :parameters} request
            result (domain/add-unit-to-draft dependencies eid unit-eid section)]
        {:status (get result :http-status 200)
         :body   (dissoc result :http-status)})
      (catch Exception exc
        (log/error exc)
        {:status 500
         :body   {:type :draft/add-error :message "An unexpected error occurred."}}))))

(defmethod integrant.core/init-key ::draft-remove-unit
  [_init-key dependencies]
  (fn [request]
    (try
      (let [{{{:keys [eid unit-eid]} :path
              {:keys [section]}      :query} :parameters} request]
        {:status 200
         :body   (domain/remove-unit-from-draft dependencies eid unit-eid section)})
      (catch Exception exc
        (log/error exc)
        {:status 200
         :body   {:type :draft/add-error :message "An unexpected error occurred."}}))))
