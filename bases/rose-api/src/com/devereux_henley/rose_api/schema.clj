(ns com.devereux-henley.rose-api.schema
  (:require
   [malli.util]))

(def url [:string {:min 1}])

(def base-resource
  [:map
   [:id
    {:json-schema/title       "Resource Id"
     :json-schema/description (str
                               "The internal identifier for a given resource."
                               " This API conforms to uuids. External APIs"
                               " should link to this via _links.self.")}
    :uuid]
   [:type :string]
   [:_links
    [:map
     [:self url]]]])

(def base-collection-resource
  [:map
   [:_links
    [:map
     [:self url]
     [:next url]
     [:previous url]
     [:first url]
     [:last url]]]])

(defn make-embedded-schema
  [schema]
  [:map
   [:results [:vector schema]]])

(defn make-collection-resource
  [schema]
  (malli.util/merge
   base-collection-resource
   [:map
    [:_embedded
     (make-embedded-schema schema)]]))

(def base-create-collection-request
  [:map
   [:metadata
    {:optional                true
     :json-schema/title       "Search Metadata"
     :json-schema/description (str
                               "Constraining metadata for the search itself."
                               "  Does not contain any resource specification.")}
    [:map
     [:size
      {:json-schema/title       "Search Size"
       :json-schema/description "The (requested) size of the result set."
       :json-schema/type        "integer"
       :json-schema/format      "int64"
       :json-schema/example     100
       :json-schema/minimum     1}
      pos-int?]]]])

(defn make-create-collection-request
  [specification-schema]
  (malli.util/merge
   base-create-collection-request
   [:map
    [:specification
     {:json-schema/title "Resource Specification"
      :json-schema/description
      "The specification for which resources to return in your search."}
     specification-schema]]))

(def create-flower-collection-request
  (make-create-collection-request
   [:map
    [:name
     {:optional            true
      :json-schema/title   "Flower Name"
      :json-schema/description
      "The name of the flower to search on. Supports (*) wild card."
      :json-schema/example "*Rose"}
     :string]]))

(def get-by-id-request
  [:map
   [:id :uuid]])

(def flower-resource
  (malli.util/merge
   base-resource
   [:map
    [:type [:= :flower/flower]]
    [:name :string]]))

(def flower-collection-resource
  (malli.util/merge
   (make-collection-resource flower-resource)
   [:map
    [:type [:= :collection/flower]]]))
