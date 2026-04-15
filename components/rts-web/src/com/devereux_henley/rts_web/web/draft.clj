(ns com.devereux-henley.rts-web.web.draft
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]
   [integrant.core]))

(defn- attach-has-passives
  [details]
  (assoc details
         :has-passives
         (boolean (or (seq (get-in details [:unit :passive-abilities]))
                      (seq (:passive-spells details))))))

(defmethod integrant.core/init-key ::get-draft-unit
  [_init-key dependencies]
  (fn [request]
    (let [{{{:keys [eid unit-eid]} :path} :parameters} request
          details (domain/get-draft-unit-details dependencies eid unit-eid)]
      {:status 200 :body (attach-has-passives details)})))

(defmethod integrant.core/init-key ::get-draft-entry
  [_init-key dependencies]
  (fn [request]
    (let [{{{:keys [eid entry-eid]} :path
            {:keys [section]}        :query} :parameters} request]
      (if-let [details (domain/get-draft-entry-details dependencies eid entry-eid section)]
        {:status 200 :body (attach-has-passives details)}
        {:status 404 :body {:type :missing/resource :name "draft-entry" :id entry-eid}}))))

(defmethod integrant.core/init-key ::draft-add-unit
  [_init-key dependencies]
  (fn [request]
    (let [{{{:keys [eid unit-eid]} :path
            {:keys [section]}      :query
            body                   :body}  :parameters} request
          selections (select-keys (or body {}) [:mount :abilities :spells :items])
          result     (domain/add-unit-to-draft dependencies eid unit-eid section selections)]
      {:status (if (= :draft/add-success (:type result)) 200 422)
       :body   result})))

(defmethod integrant.core/init-key ::draft-update-unit
  [_init-key dependencies]
  (fn [request]
    (let [{{{:keys [eid entry-eid]} :path
            {:keys [section]}       :query
            body                    :body}  :parameters} request
          selections (select-keys (or body {}) [:mount :abilities :spells :items])
          result     (domain/update-unit-in-draft dependencies eid entry-eid section selections)]
      {:status (if (= :draft/update-success (:type result)) 200 422)
       :body   result})))

(defmethod integrant.core/init-key ::draft-remove-unit
  [_init-key dependencies]
  (fn [request]
    (let [{{{:keys [eid entry-eid]} :path
            {:keys [section]}       :query} :parameters} request]
      {:status 200 :body (domain/remove-unit-from-draft dependencies eid entry-eid section)})))
