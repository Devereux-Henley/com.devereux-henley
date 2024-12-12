(ns com.devereux-henley.schema.contract
  (:require
   [clojure.string]
   [malli.core]
   [malli.transform]
   [malli.util]
   [reitit.coercion.malli]
   [reitit.core])
  (:import
   [java.net URL]
   [java.time Instant LocalDate]
   [java.util UUID]))

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
    :type-properties {:encode/json         str
                      :decode/json         (fn [json-value] (when (not (empty? json-value)) (LocalDate/parse json-value)))
                      :decode/string       (fn [string-value] (when (not (empty? string-value)) (LocalDate/parse string-value)))
                      :json-schema/type    "string"
                      :json-schema/format  "date"
                      :json-schema/example "2023-01-01"
                      :gen/fmap            (fn [value] (LocalDate/ofEpochDay value))
                      :gen/schema          [:double {:min 0 :max max-local-date-in-days}]
                      :error/message       {:en "Should be a valid instant."}}
    :pred            (partial instance? LocalDate)}))

(def instant
  (malli.core/-simple-schema
   {:type            :instant
    :type-properties {:encode/json         str
                      :decode/json         (fn [json-value] (when (not (empty? json-value)) (Instant/parse json-value)))
                      :decode/string       (fn [string-value] (when (not (empty? string-value)) (Instant/parse string-value)))
                      :json-schema/type    "string"
                      :json-schema/format  "date-time"
                      :json-schema/example "2007-03-01T13:00:00Z"
                      :gen/fmap            (fn [value] (Instant/ofEpochMilli value))
                      :gen/schema          [:double {:min 0 :max Long/MAX_VALUE}]
                      :error/message       {:en "Should be a valid instant."}}
    :pred            (partial instance? Instant)}))

(def url
  (malli.core/-simple-schema
   {:type            :url
    :type-properties {:gen/fmap            (fn [[name host tld]]
                                             (str name "@" host "." tld))
                      :json-schema/example "https://example.com"
                      :gen/schema          [:tuple [:string {:min 1}] [:string {:min 1}] [:string {:min 1}]]}
    :pred            (fn [value] (and (string? value) (not (clojure.string/blank? value))))}))

(def registry
  (merge
   (malli.core/default-schemas)
   {:url        url
    :local-date local-date
    :instant    instant
    :instance   instance
    :neg-int    (malli.core/-simple-schema
                 {:type            :neg-int
                  :type-properties {:decode/string
                                    (fn [string-value] (Integer/parseInt string-value))}
                  :pred            neg-int?})
    :pos-int    (malli.core/-simple-schema
                 {:type            :pos-int
                  :type-properties {:json-schema/type "integer"
                                    :json-schema/format "int64"
                                    :decode/string
                                    (fn [string-value] (Integer/parseInt string-value))}
                  :pred            pos-int?})}))

(defn to-schema
  [input-schema]
  (malli.core/schema
   input-schema
   {:registry registry}))

(def base-resource
  [:map
   {:model/type :model/model}
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
  (to-schema
   [:map
    {:model/type :model/collection}
    [:specification
     [:map
      [:since :instant]
      [:size :pos-int]
      [:offset :int]]]
    [:_links
     [:map
      [:self url]
      [:next {:optional true} url]
      [:previous {:optional true} url]
      [:first {:optional true} url]
      [:last {:optional true} url]]]]))

(defn create-link-schema
  [input-schema]
  (->> input-schema
       malli.core/entries
       (filter #(not= (first %) :eid))
       (map second)
       (map malli.core/properties)
       (map :model/link)
       (filter some?)
       (map namespace)
       (map keyword)
       (map (fn [x] [x url]))
       (into [:map])))

(defn to-resource-schema
  [input-schema]
  (let [link-schema (create-link-schema input-schema)]
    (to-schema
     (-> input-schema
       (malli.util/merge base-resource)
       (malli.util/merge [:map [:_links link-schema]])))))

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

(def collection-parameters
  (to-schema
   [:map
    [:since :instant]
    [:size {:optional true :json-schema/default 10} :pos-int]
    [:offset {:optional true :json-schema/default 0} :int]]))

(defn key-to-link-mapping
  [schema]
  (reduce (fn [acc [map-key properties map-value-schema]]
            (if-let [link (:model/link properties)]
              (assoc acc map-key link)
              acc))
          {}
          (malli.core/children schema)))

(defn to-resource-link
  ([route-data route-name path-parameters]
   (to-resource-link route-data route-name path-parameters {}))
  ([{:keys [hostname router] :as _route-data} route-name path-parameters query-parameters]
   (str hostname (-> router
                     (reitit.core/match-by-name! route-name path-parameters)
                     (reitit.core/match->path query-parameters)))))

(defn nav-transformer
  [route-data]
  (malli.transform/transformer
   {:name     :nav
    :decoders {:map (fn [value] (vary-meta value dissoc `clojure.core.protocols/nav))}
    :encoders {:map {:compile
                     (fn [schema _]
                       (let [mapping (key-to-link-mapping schema)]
                         (fn [value]
                           (vary-meta value assoc `clojure.core.protocols/nav
                                      (fn [coll k v]
                                        (if-let [link (get mapping k)]
                                          (URL. (to-resource-link route-data link {:eid v}))
                                          v))))))}}}))

(def sqlite-transformer
  (malli.transform/transformer
   {:name     :sqlite
    :decoders {:uuid       (fn [uuid-string] (UUID/fromString uuid-string))
               :bool       (fn [bit] (if bit true false))
               :local-date (fn [date-string] (when-not (empty? date-string)
                                              (LocalDate/parse date-string)))
               :instant    (fn [instant-string] (when-not (empty? instant-string)
                                                 (Instant/parse instant-string)))}}))

(defn handle-model-transform
  [route-data schema]
  (let [mapping (key-to-link-mapping schema)]
    (fn [value]
      (reduce-kv
       (fn [acc k v]
         (if-let [link (get mapping k)]
           (-> acc
               (assoc k v)
               (assoc-in [:_links
                          (if (= k :eid)
                            :self
                            (keyword
                             (clojure.string/replace (name k) #"-eid" "")))]
                         (to-resource-link
                          route-data
                          link
                          {:eid v})))
           (assoc acc k v)))
       (vary-meta {}
                  assoc
                  `clojure.core.protocols/nav
                  (get (meta value)
                       `clojure.core.protocols/nav))
       value))))

(defn next-specification
  [specification]
  (let [{:keys [offset size]} specification]
    (when (and (some? offset) (some? size))
        (assoc specification
               :offset
               (+ offset size)))))

(defn previous-specification
  [specification]
  (let [{:keys [offset size]} specification]
    (when (and (some? offset) (some? size) (> (- offset size) 0))
      (assoc specification
             :offset
             (- offset size)))))

(defn first-specification
  [specification]
  (let [{:keys [offset size]} specification]
    (when (and (some? offset) (some? size))
      (assoc specification
             :offset
             0))))

(defn last-specification
  [specification]
  ;; TODO Implement last.
  nil)

;; TODO Implement specification query params, additional links.
(defn handle-collection-transform
  [route-data schema]
  (let [link (some (fn [[map-key properties map-value-schema]]
                     (and
                      (= map-key :specification)
                      (:collection/link properties)))
                   (malli.core/children schema))]
    (fn [value]
      (let [{:keys [specification total]} value
            {:keys [offset size]} specification
            next (next-specification specification)
            previous (previous-specification specification)
            first (first-specification specification)
            last (last-specification specification)]
        (-> value
            (assoc-in [:_links :self] (to-resource-link route-data link {} specification))
            (assoc-in [:_links :next] (to-resource-link route-data link {} next))
            (as-> % (if first
                      (assoc-in %
                                [:_links :first]
                                (to-resource-link route-data link {} first))
                      %))
            (as-> % (if previous
                      (assoc-in %
                                [:_links :previous]
                                (to-resource-link route-data link {} previous))
                      %))
            (as-> % (if last
                      (assoc-in %
                                [:_links :first]
                                (to-resource-link route-data link {} last))
                      %)))))))

;; Walk the input value.
;; For each key in the model, add that key to a links array.
;; Do this for every map in the model.
(defn model-transformer
  [route-data]
  (malli.transform/transformer
   (nav-transformer route-data)
   {:name     :model
    :decoders {}
    :encoders {:map {:compile
                     (fn [schema _]
                       (let [properties (malli.core/properties schema)]
                         (case (:model/type properties)
                           :model/collection (handle-collection-transform route-data schema)
                           :model/model (handle-model-transform route-data schema)
                           identity)))}}}))
