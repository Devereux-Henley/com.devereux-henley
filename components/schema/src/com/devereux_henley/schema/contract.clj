(ns com.devereux-henley.schema.contract
  (:require
   [clojure.core.protocols]
   [clojure.string]
   [malli.core]
   [malli.transform]
   [malli.util]
   [reitit.core])
  (:import
   [java.time Instant LocalDate LocalDateTime ZoneId]
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
      :pred            (fn [_] (partial instance? child))
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

(def local-datetime
  (malli.core/-simple-schema
   {:type            :local-datetime
    :type-properties {:encode/json         str
                      :decode/json         (fn [json-value]
                                             (try (when (not (empty? json-value)) (LocalDateTime/parse json-value))
                                                  (catch Exception _ json-value)))
                      :decode/string       (fn [string-value]
                                             (try (when (not (empty? string-value)) (LocalDateTime/parse string-value))
                                                  (catch Exception _ string-value)))
                      :json-schema/type    "string"
                      :json-schema/format  "date-time"
                      :json-schema/example "2023-01-01T12:00:00"
                      :error/message       {:en "Should be a valid local date-time (e.g. 2023-01-01T12:00)."}}
    :pred            (partial instance? LocalDateTime)}))

(def timezone-id
  (malli.core/-simple-schema
   {:type            :timezone-id
    :type-properties {:encode/json         str
                      :decode/json         (fn [json-value]
                                             (try (when (not (empty? json-value)) (ZoneId/of json-value))
                                                  (catch Exception _ json-value)))
                      :decode/string       (fn [string-value]
                                             (try (when (not (empty? string-value)) (ZoneId/of string-value))
                                                  (catch Exception _ string-value)))
                      :json-schema/type    "string"
                      :json-schema/example "US/Eastern"
                      :error/message       {:en "Should be a valid IANA timezone (e.g. US/Eastern, Europe/London)."}}
    :pred            (partial instance? ZoneId)}))

(def registry
  (merge
   (malli.core/default-schemas)
   {:url            url
    :local-date     local-date
    :local-datetime local-datetime
    :timezone-id    timezone-id
    :instant        instant
    :instance       instance
    :neg-int        (malli.core/-simple-schema
                     {:type            :neg-int
                      :type-properties {:decode/string
                                        (fn [string-value] (Integer/parseInt string-value))}
                      :pred            neg-int?})
    :pos-int        (malli.core/-simple-schema
                     {:type            :pos-int
                      :type-properties {:json-schema/type   "integer"
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
    [:_links [:map [:self url]]]]))

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

(def game-id-path-parameter
  (to-schema
   [:map
    [:game-eid :uuid]]))

(def game-and-id-path-parameter
  (to-schema
   [:map
    [:game-eid :uuid]
    [:eid :uuid]]))

(def version-query-parameter
  (to-schema
   [:map
    [:version {:optional true} :pos-int]]))

(defn key-to-link-mapping
  [schema]
  (reduce (fn [acc [map-key properties _map-value-schema]]
            (if-let [link (:model/link properties)]
              (assoc acc map-key link)
              acc))
          {}
          (malli.core/children schema)))

(defn to-resource-link
  "Builds a URL for the named reitit route. `path-parameters` fills the
   route's `:`-prefixed slots; `query-parameters` is appended as a
   query string. Reitit silently ignores extra keys in
   `path-parameters` that aren't part of the route's path template, so
   callers may pass a superset."
  ([route-data route-name path-parameters]
   (to-resource-link route-data route-name path-parameters {}))
  ([{:keys [hostname router] :as _route-data} route-name path-parameters query-parameters]
   (str hostname (-> router
                     (reitit.core/match-by-name! route-name path-parameters)
                     (reitit.core/match->path query-parameters)))))

(def sqlite-transformer
  (malli.transform/transformer
   {:name     :sqlite
    :decoders {:uuid           (fn [uuid-string] (when-not (empty? uuid-string)
                                                   (UUID/fromString uuid-string)))
               :bool           (fn [bit] (if bit true false))
               :local-date     (fn [date-string] (when-not (empty? date-string)
                                                   (LocalDate/parse date-string)))
               :instant        (fn [instant-string] (when-not (empty? instant-string)
                                                      (Instant/parse instant-string)))
               :local-datetime (fn [dt-string] (when-not (empty? dt-string)
                                                 (LocalDateTime/parse dt-string)))
               :timezone-id    (fn [tz-string] (when-not (empty? tz-string)
                                                 (ZoneId/of tz-string)))}}))

(defn- self-link-prefix
  "Returns the singular resource name from the schema's `:eid` field's
   `:model/link` annotation, as a string. E.g. `:tournament/by-eid`
   yields `\"tournament\"`. Used to derive the `<prefix>-eid`
   query-parameter key that sub-resource collection routes accept."
  [schema]
  (some (fn [[k props _]]
          (when (= k :eid)
            (some-> (:model/link props) namespace)))
        (malli.core/children schema)))

(defn handle-model-transform
  "Returns a malli encoder fn for a `:map` schema annotated
   `{:model/type :model/model}`. Walks the input value once: each key
   whose schema field carries `:model/link <route-name>` becomes a
   `_links.<key>` entry (or `_links.self` for `:eid`), resolved against
   the matched reitit route with the field's value as the `:eid` path
   parameter. All other keys pass through unchanged.

   If the schema's properties include `:model/sub-resources
   {<rel-key> <route-name>, ...}`, every entry also produces a
   `_links.<rel-key>` resolved with the resource's own eid passed to
   the sub-resource collection route as a `?<prefix>-eid=...` query
   parameter, where `<prefix>` is the namespace of the `:eid` field's
   `:model/link` annotation (e.g. `:tournament/by-eid` → `tournament-eid`).
   The /api surface puts every collection at a top-level URL filtered
   by query params, so a tournament's `:games` sub-resource resolves to
   `/api/match-game?match-eid=…` style links."
  [route-data schema]
  (let [mapping       (key-to-link-mapping schema)
        props         (malli.core/properties schema)
        sub-resources (:model/sub-resources props)
        prefix        (self-link-prefix schema)
        parent-eid-q  (some-> prefix (str "-eid") keyword)]
    (fn [value]
      (cond-> (reduce-kv
               (fn [acc k v]
                 (cond
                   (and (contains? mapping k) (nil? v))
                   acc

                   (contains? mapping k)
                   (-> acc
                       (assoc k v)
                       (assoc-in [:_links
                                  (if (= k :eid)
                                    :self
                                    (keyword
                                     (clojure.string/replace (name k) #"-eid" "")))]
                                 (to-resource-link
                                  route-data
                                  (get mapping k)
                                  {:eid v})))

                   :else
                   (assoc acc k v)))
               {}
               value)
        (and sub-resources parent-eid-q (:eid value))
        ((fn [result]
           (let [parent-eid (:eid value)]
             (reduce-kv (fn [acc rel route-name]
                          (assoc-in acc [:_links rel]
                                    (to-resource-link route-data route-name
                                                      {}
                                                      {parent-eid-q parent-eid})))
                        result
                        sub-resources))))))))

(defn model-transformer
  "Malli encoder that walks a value against its schema, applying
   `handle-model-transform` to every `:map` whose properties include
   `{:model/type :model/model}`. Maps without that annotation pass
   through unchanged."
  [route-data]
  (malli.transform/transformer
   {:name     :model
    :encoders {:map {:compile
                     (fn [schema _]
                       (if (= :model/model (:model/type (malli.core/properties schema)))
                         (handle-model-transform route-data schema)
                         identity))}}}))
