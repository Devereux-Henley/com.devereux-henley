(ns com.devereux-henley.rose-api.web.flower
  (:require
   [com.devereux-henley.rose-api.schema :as schema]
   [malli.generator]))

(defmethod integrant.core/init-key ::get-flower
  [_init-key _dependencies]
  (fn [{{{:keys [_id]} :path} :parameters}]
    {:status 200
     :body   (malli.generator/generate
              schema/flower-resource)}))

(defmethod integrant.core/init-key ::create-flower
  [_init-key _dependencies]
  (fn [_request-map]
    {:status 201
     :body   nil}))

(defmethod integrant.core/init-key ::get-my-flower-collection
  [_init-key _dependencies]
  (fn [{{{:keys [_id]} :path} :parameters}]
    {:status 200
     :body   (malli.generator/generate
              schema/flower-collection-resource)}))

(defmethod integrant.core/init-key ::get-recent-flower-collection
  [_init-key _dependencies]
  (fn [{{{:keys [_id]} :path} :parameters}]
    {:status 200
     :body   (malli.generator/generate
              schema/flower-collection-resource)}))
