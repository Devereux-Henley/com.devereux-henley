(ns com.devereux-henley.rose-api.web.collection
  (:require
   [com.devereux-henley.rose-api.schema :as schema]
   [integrant.core]
   [malli.generator]))

(defmethod integrant.core/init-key ::get-flower-collection
  [_init-key _dependencies]
  (fn [{{{:keys [_eid]} :path} :parameters}]
    {:status 200
     :body   (malli.generator/generate
              schema/flower-collection-resource)}))

(defmethod integrant.core/init-key ::create-flower-collection
  [_init-key _dependencies]
  (fn [{{{:keys [_name]} :body} :parameters}]
    {:status 201
     :body   (malli.generator/generate
              schema/flower-collection-resource)}))
