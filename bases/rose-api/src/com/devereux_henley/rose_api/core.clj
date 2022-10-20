(ns com.devereux-henley.rose-api.core
  (:require [reitit.ring :as ring]
            [reitit.coercion.malli]
            [reitit.ring.malli]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.ring.coercion :as coercion]
            [reitit.dev.pretty :as pretty]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.adapter.jetty :as jetty]
            [selmer.parser]
            [muuntaja.core :as m]
            [muuntaja.format.core]
            [muuntaja.format.form]
            [clojure.java.io :as io]
            [malli.generator]
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

(defn view-by-type
  [type]
  (get
   {:view/index "rose-api/index.html"
    :flower/flower "rose-api/resource/flower.html"
    :collection/flower "rose-api/resource/flower-collection.html"}
   type
   "rose-api/resource/unknown.html"))

(defn html-decoder
  [options]
  (reify
    muuntaja.format.core/Decode
    (decode [_ data charset]
      (slurp (java.io.InputStreamReader. ^java.io.InputStream data ^String charset)))))

(defn html-encoder
  [options]
  (reify
    muuntaja.format.core/EncodeToBytes
    (encode-to-bytes [_ data charset]
      (.getBytes ^String (selmer.parser/render-file (view-by-type (:type data)) data) ^String charset))
    muuntaja.format.core/EncodeToOutputStream
    (encode-to-output-stream [_ data charset]
      (fn [^java.io.OutputStream output-stream]
        (let [encoded (selmer.parser/render-file (view-by-type (:type data)) data)
              bytes (.getBytes ^String encoded ^String charset)]
          (.write output-stream bytes))))))

(def html-format
  (muuntaja.format.core/map->Format
   {:name    "text/html"
    :decoder [html-decoder]
    :encoder [html-encoder]}))

(def app
  (ring/ring-handler
   (ring/router
    [["/"
      {:get {:no-doc true
             :produces ["text/html"]
             :handler (fn [_request] {:status 301 :headers {"Location" "/index.html"}})}}]
     ["/index.html"
      {:get {:no-doc true
             :produces ["text/html"]
             :handler (fn [_request] {:status 200 :body {:type :view/index}})}}]
     ["/index.css"
      {:get {:no-doc true
             :produces ["application/css"]
             :handler (fn [_request] {:status 200 :body (slurp (io/resource "rose-api/index.css"))})}}]

     ["/api"
      ["/swagger.json"
       {:get {:no-doc  true
              :swagger {:info {:title       "rose-api"
                               :description "Rose API"}
                        :tags [{:name "flowers" :description "flower api"}
                               {:name "collections" :description "collection api"}]}
              :handler (swagger/create-swagger-handler)}}]

      ["/flower"
       {:swagger {:tags ["flowers"]}}

       ["/:id"
        {:get {:summary    "Fetches a flower by id."
               :swagger    {:produces     ["text/html" "application/json"]
                            :operation-id "get-flower"}
               :parameters {:path get-by-id-request}
               :responses  {200 {:body flower-resource}}
               :handler    (fn [{{{:keys [_id]} :path} :parameters}]
                             {:status 200
                              :body   (malli.generator/generate
                                       flower-resource)})}}]
       ["/collection"
        ["/mine"
         {:get {:summary   "Fetches a flower by id."
                :swagger   {:produces     ["text/html" "application/json"]
                            :operation-id "get-flower-collection-mine"}
                :responses {200 {:body flower-collection-resource}}
                :handler   (fn [{{{:keys [_id]} :path} :parameters}]
                             {:status 200
                              :body   (malli.generator/generate
                                       flower-collection-resource)})}}]
        ["/recent"
         {:get {:summary   "Fetches a flower by id."
                :swagger   {:produces     ["text/html" "application/json"]
                            :operation-id "get-flower-collection-recent"}
                :responses {200 {:body flower-collection-resource}}
                :handler   (fn [{{{:keys [_id]} :path} :parameters}]
                             {:status 200
                              :body   (malli.generator/generate
                                       flower-collection-resource)})}}]]]

      ["/collection"
       ["/flower/:id"
        {:swagger {:tags ["flowers" "collections"]}
         :get     {:summary    "Fetches a collection of flowers."
                   :swagger    {:produces     ["text/html" "application/json"]
                                :operation-id "get-flower-collection"}
                   :parameters {:path get-by-id-request}
                   :responses  {200 {:body nil?}}
                   :handler    (fn [{{{:keys [_id]} :path} :parameters}]
                                 {:status 200
                                  :body   nil})}
         :post    {:summary    "Create a collection of flowers with input specification."
                   :swagger    {:produces     ["text/html" "application/json"]
                                :operation-id "create-flower-collection"}
                   :parameters {:body create-flower-collection-request}
                   :responses  {200 {:body flower-collection-resource}}
                   :handler
                   (fn [{{{:keys [_name]} :body} :parameters}]
                     {:status 200
                      :body   (malli.generator/generate
                               flower-collection-resource)})}}]]]]

    {;;:reitit.middleware/transform dev/print-request-diffs ;; pretty diffs
     :exception pretty/exception
     :data      {:coercion   (reitit.coercion.malli/create
                              {;; set of keys to include in error messages
                               :error-keys       #{#_:type
                                                   :coercion
                                                   :in
                                                   :schema
                                                   :value
                                                   :errors
                                                   :humanized
                                                   #_:transformed}
                               :compile          malli.util/closed-schema
                               :strip-extra-keys true
                               :default-values   true
                               :options          nil})
                 :muuntaja   (m/create (-> m/default-options
                                           (assoc :return :bytes)
                                           (assoc-in [:formats "application/hal+json"] (get-in m/default-options [:formats "application/json"]))
                                           (assoc-in [:formats "application/x-www-form-urlencoded"] muuntaja.format.form/format)
                                           (assoc-in [:formats "text/html"] html-format)))
                 :middleware [;; swagger feature
                              swagger/swagger-feature
                              ;; query-params & form-params
                              parameters/parameters-middleware
                              ;; content-negotiation
                              muuntaja/format-negotiate-middleware
                              ;; encoding response body
                              muuntaja/format-response-middleware
                              ;; exception handling
                              exception/exception-middleware
                              ;; decoding request body
                              muuntaja/format-request-middleware
                              ;; coercing response bodys
                              coercion/coerce-response-middleware
                              ;; coercing request parameters
                              coercion/coerce-request-middleware
                              ;; multipart
                              multipart/multipart-middleware]}})
   (ring/routes
    (swagger-ui/create-swagger-ui-handler
     {:path   "/api/"
      :url    "/api/swagger.json"
      :config {:validatorUrl     nil
               :operationsSorter "alpha"}})
    (ring/create-default-handler))))

(defn start []
  (jetty/run-jetty #'app {:port 3000, :join? false})
  (println "server running in port 3000"))

(defn -main [& args]
  (start))

(comment
  (start))
