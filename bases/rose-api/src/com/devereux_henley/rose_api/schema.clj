(ns com.devereux-henley.rose-api.schema
  (:require
   [com.devereux-henley.schema.contract :as schema.contract]
   [malli.util]))

(def create-flower-collection-request
  (schema.contract/make-create-collection-request
   [:map
    [:name
     {:optional            true
      :json-schema/title   "Flower Name"
      :json-schema/description
      "The name of the flower to search on. Supports (*) wild card."
      :json-schema/example "*Rose"}
     :string]]))

(def create-flower-request
  (schema.contract/to-schema
   [:map
    [:name {:min 0} :string]]))

(def flower-resource
  (schema.contract/to-schema
   (malli.util/merge
    schema.contract/base-resource
    [:map
     {:encode/nav (fn [value]
                    (vary-meta
                     value
                     assoc
                     `clojure.core.protocols/nav
                     (fn [_xs k v]
                       (case k
                         :eid (str "/api/flower/" v)
                         v))))}
     [:eid :uuid]
     [:type [:= :flower/flower]]
     [:name :string]])))

(def flower-collection-resource
  (schema.contract/to-schema
   (malli.util/merge
    (schema.contract/make-collection-resource flower-resource)
    [:map
     [:type [:= :collection/flower]]])))
