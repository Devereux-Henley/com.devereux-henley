(ns com.devereux-henley.rose-api.core
  (:require
   [clojure.java.io :as io]
   [com.devereux-henley.rose-api.format.html :as format.html]
   [com.devereux-henley.rose-api.schema :as schema]
   [malli.generator]
   [malli.util]
   [muuntaja.core :as m]
   [muuntaja.format.form]
   [reitit.coercion.malli]
   [reitit.dev.pretty :as pretty]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as coercion]
   [reitit.ring.middleware.exception :as exception]
   [reitit.ring.middleware.multipart :as multipart]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]
   [ring.adapter.jetty :as jetty]))

(defn view-by-type
  [type]
  (get
   {:view/index "rose-api/index.html"
    :flower/flower "rose-api/resource/flower.html"
    :collection/flower "rose-api/resource/flower-collection.html"}
   type
   "rose-api/resource/unknown.html"))

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
               :parameters {:path schema/get-by-id-request}
               :responses  {200 {:body schema/flower-resource}}
               :handler    (fn [{{{:keys [_id]} :path} :parameters}]
                             {:status 200
                              :body   (malli.generator/generate
                                       schema/flower-resource)})}}]
       ["/collection"
        ["/mine"
         {:get {:summary   "Fetches a flower by id."
                :swagger   {:produces     ["text/html" "application/json"]
                            :operation-id "get-flower-collection-mine"}
                :responses {200 {:body schema/flower-collection-resource}}
                :handler   (fn [{{{:keys [_id]} :path} :parameters}]
                             {:status 200
                              :body   (malli.generator/generate
                                       schema/flower-collection-resource)})}}]
        ["/recent"
         {:get {:summary   "Fetches a flower by id."
                :swagger   {:produces     ["text/html" "application/json"]
                            :operation-id "get-flower-collection-recent"}
                :responses {200 {:body schema/flower-collection-resource}}
                :handler   (fn [{{{:keys [_id]} :path} :parameters}]
                             {:status 200
                              :body   (malli.generator/generate
                                       schema/flower-collection-resource)})}}]]]

      ["/collection"
       ["/flower/:id"
        {:swagger {:tags ["flowers" "collections"]}
         :get     {:summary    "Fetches a collection of flowers."
                   :swagger    {:produces     ["text/html" "application/json"]
                                :operation-id "get-flower-collection"}
                   :parameters {:path schema/get-by-id-request}
                   :responses  {200 {:body nil?}}
                   :handler    (fn [{{{:keys [_id]} :path} :parameters}]
                                 {:status 200
                                  :body   nil})}
         :post    {:summary    "Create a collection of flowers with input specification."
                   :swagger    {:produces     ["text/html" "application/json"]
                                :operation-id "create-flower-collection"}
                   :parameters {:body schema/create-flower-collection-request}
                   :responses  {200 {:body schema/flower-collection-resource}}
                   :handler
                   (fn [{{{:keys [_name]} :body} :parameters}]
                     {:status 200
                      :body   (malli.generator/generate
                               schema/flower-collection-resource)})}}]]]]

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
                 :muuntaja   (m/create
                              (-> m/default-options
                                  (assoc :return :bytes)
                                  (assoc-in
                                   [:formats "application/hal+json"]
                                   (get-in
                                    m/default-options
                                    [:formats "application/json"]))
                                  (assoc-in
                                   [:formats "application/x-www-form-urlencoded"]
                                   muuntaja.format.form/format)
                                  (assoc-in [:formats "text/html"]
                                            format.html/html-format)
                                  (assoc-in [:formats "text/html" :encoder-opts] {:view-fn view-by-type})))
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
