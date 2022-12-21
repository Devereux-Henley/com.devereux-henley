(ns com.devereux-henley.schema.contract
  (:require
   [clojure.string]
   [malli.core]
   [malli.transform]
   [malli.util]
   [reitit.coercion.malli])
  (:import
   [java.net URL]
   [java.time Instant LocalDate]))

(def milliseconds-in-a-second 1000)
(def seconds-in-a-minute 60)
(def minutes-in-an-hour 60)
(def hours-in-a-day 24)

(def max-local-date-in-days
  (/ Long/MAX_VALUE (* milliseconds-in-a-second
                       seconds-in-a-minute
                       minutes-in-an-hour
                       hours-in-a-day)))

(def instance
  (malli.core/-simple-schema
   (fn [_ [child]]
     {:type            :instance
      :type-properties {:encode/json clojure.core.protocols/datafy}
      :pred            (fn [value] (partial instance? child))
      :from-ast        malli.core/-from-value-ast
      :to-ast          malli.core/-to-value-ast
      :min             1
      :max             1})))

(def local-date
  (malli.core/-simple-schema
   {:type            :local-date
    :type-properties {:encode/json   str
                      :decode/json   (fn [json-value] (when (not (empty? json-value)) (LocalDate/parse json-value)))
                      :gen/fmap      (fn [value] (LocalDate/ofEpochDay value))
                      :gen/schema    [:double {:min 0 :max max-local-date-in-days}]
                      :error/message {:en "Should be a valid instant."}}
    :pred            (partial instance? LocalDate)}))

(def instant
  (malli.core/-simple-schema
   {:type            :instant
    :type-properties {:encode/json   str
                      :decode/json   (fn [json-value] (when (not (empty? json-value)) (Instant/parse json-value)))
                      :gen/fmap      (fn [value] (Instant/ofEpochMilli value))
                      :gen/schema    [:double {:min 0 :max Long/MAX_VALUE}]
                      :error/message {:en "Should be a valid instant."}}
    :pred            (partial instance? Instant)}))

(def url
  (malli.core/-simple-schema
   {:type            :url
    :type-properties {:gen/fmap   (fn [[name host tld]]
                                    (str name "@" host "." tld))
                      :gen/schema [:tuple [:string {:min 1}] [:string {:min 1}] [:string {:min 1}]]}
    :pred            (fn [value] (and (string? value) (not (clojure.string/blank? value))))}))

(def registry
  (merge
   (malli.core/default-schemas)
   {:url        url
    :local-date local-date
    :instant    instant
    :instance   instance
    :neg-int    (malli.core/-simple-schema {:type :neg-int, :pred neg-int?})
    :pos-int    (malli.core/-simple-schema {:type :pos-int, :pred pos-int?})}))

(defn to-schema
  [input-schema]
  (malli.core/schema
   input-schema
   {:registry registry}))

(def base-resource
  [:map
   [:eid
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
     [:next {:optional true} url]
     [:previous {:optional true} url]
     [:first {:optional true} url]
     [:last {:optional true} url]]]])

(defn make-embedded-schema
  [schema]
  [:map
   [:results [:vector schema]]])

(defn make-collection-resource
  [schema]
  (to-schema
   (malli.util/merge
    base-collection-resource
    [:map
     [:_embedded
      (make-embedded-schema schema)]])))

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
  (to-schema
   (malli.util/merge
    base-create-collection-request
    [:map
     [:specification
      {:json-schema/title "Resource Specification"
       :json-schema/description
       "The specification for which resources to return in your search."}
      specification-schema]])))

(def id-path-parameter
  (to-schema
   [:map
    [:eid :uuid]]))

(def version-query-parameter
  (to-schema
   [:map
    [:version {:optional true} :pos-int]]))

(defn key-to-link-mapping
  [schema]
  (reduce (fn [acc [map-key properties map-value-schema]]
            (if-let [link (:model/link properties)]
              (assoc acc map-key link)
              acc))
          {}
          (malli.core/children schema)))

(def nav-transformer
  (malli.transform/transformer
   {:name     :nav
    :decoders {:map (fn [value] (vary-meta value dissoc `clojure.core.protocols/nav))}
    :encoders {:map {:compile
                     (fn [schema _]
                       (let [mapping (key-to-link-mapping schema)]
                         (fn [value]
                           (vary-meta value assoc `clojure.core.protocols/nav
                                      (fn [coll k v]
                                        (if-let [link (k mapping)]
                                          (URL. (str link "/" v))
                                          v))))))}}}))

;; Walk the input value.
;; For each key in the model, add that key to a links array.
;; Do this for every map in the model.
(def model-transformer
  (malli.transform/transformer
   nav-transformer
   {:name     :model
    :decoders {}
    :encoders {:map {:compile (fn [schema _]
                                (let [mapping (key-to-link-mapping schema)]
                                  (fn [value]
                                    (reduce-kv
                                     (fn [acc k v]
                                       (if-let [link (k mapping)]
                                         (-> acc
                                             (assoc k v)
                                             (assoc-in [:_links
                                                        (if (= k :eid)
                                                          :self
                                                          k)]
                                                       (str link "/" v)))
                                         (assoc acc k v)))
                                     {}
                                     value))))}}}))
