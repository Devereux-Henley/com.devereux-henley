(ns com.devereux-henley.rose-api.configuration
  (:require
   [com.devereux-henley.rose-api.db :as db]
   [com.devereux-henley.rose-api.schema :as schema]
   [com.devereux-henley.rose-api.web :as web]
   [com.devereux-henley.rose-api.web.collection :as web.collection]
   [com.devereux-henley.rose-api.web.flower :as web.flower]
   [integrant.core]
   [clojure.java.io :as io]))

(def hostname (or (System/getenv "ROSE_API_HOSTNAME") "http://localhost:3000"))
(def core-configuration
  {::db/connection {}
   ::web/swagger-handler {}
   ::web.flower/get-flower {:connection (integrant.core/ref ::db/connection)}
   ::web.flower/create-flower {:connection (integrant.core/ref ::db/connection)}
   ::web.flower/get-my-flower-collection {:connection (integrant.core/ref ::db/connection)}
   ::web.flower/get-recent-flower-collection {:connection (integrant.core/ref ::db/connection)}
   ::web.collection/get-flower-collection {:connection (integrant.core/ref ::db/connection)}
   ::web.collection/create-flower-collection {:connection (integrant.core/ref ::db/connection)}
   ::web/routes
   [["/"
     {:get {:no-doc   true
            :produces ["text/html"]
            :handler  (fn [_request] {:status 301 :headers {"Location" "/index.html"}})}}]
    ["/index.html"
     {:get {:no-doc   true
            :produces ["text/html"]
            :handler  (fn [_request] {:status 200 :body (slurp (io/resource "rose-api/asset/index.html"))})}}]
    ["/index.css"
     {:get {:no-doc   true
            :produces ["application/css"]
            :handler  (fn [_request] {:status 200 :body (slurp (io/resource "rose-api/asset/index.css"))})}}]
    ["/flower.html"
     {:get {:no-doc   true
            :produces ["text/html"]
            :handler  (fn [_request] {:status 200 :body (slurp (io/resource "rose-api/asset/flower.html"))})}}]
    ["/flower.css"
     {:get {:no-doc   true
            :produces ["application/css"]
            :handler  (fn [_request] {:status 200 :body (slurp (io/resource "rose-api/asset/flower.css"))})}}]

    ["/api"
     ["/swagger.json"
      {:get {:no-doc  true
             :swagger {:info {:title       "rose-api"
                              :description "Rose API"}
                       :tags [{:name "flowers" :description "flower api"}
                              {:name "collections" :description "collection api"}]}
             :handler (integrant.core/ref ::web/swagger-handler)}}]

     ["/flower"
      {:swagger {:tags ["flowers"]}}

      ["/:id"
       {:get {:summary    "Fetches a flower by id."
              :swagger    {:produces     ["application/htmx+html" "application/json"]
                           :operation-id "get-flower"}
              :parameters {:path schema/get-by-id-request}
              :responses  {200 {:body schema/flower-resource}}
              :handler    (integrant.core/ref ::web.flower/get-flower)}}]
      ["/collection"
       ["/mine"
        {:get {:summary   "Fetches a flower by id."
               :swagger   {:produces     ["application/htmx+html" "application/json"]
                           :operation-id "get-flower-collection-mine"}
               :responses {200 {:body schema/flower-collection-resource}}
               :handler   (integrant.core/ref ::web.flower/get-my-flower-collection)}}]
       ["/recent"
        {:get {:summary   "Fetches a flower by id."
               :swagger   {:produces     ["application/htmx+html" "application/json"]
                           :operation-id "get-flower-collection-recent"}
               :responses {200 {:body schema/flower-collection-resource}}
               :handler   (integrant.core/ref ::web.flower/get-recent-flower-collection)}}]]]

     ["/collection"
      ["/flower/:id"
       {:swagger {:tags ["flowers" "collections"]}
        :get     {:summary    "Fetches a collection of flowers."
                  :swagger    {:produces     ["application/htmx+html" "application/json"]
                               :operation-id "get-flower-collection"}
                  :parameters {:path schema/get-by-id-request}
                  :responses  {200 {:body schema/flower-collection-resource}}
                  :handler    (integrant.core/ref ::web.collection/get-flower-collection)}
        :post    {:summary    "Create a collection of flowers with input specification."
                  :swagger    {:produces     ["application/htmx+html" "application/json"]
                               :operation-id "create-flower-collection"}
                  :parameters {:body schema/create-flower-collection-request}
                  :responses  {200 {:body schema/flower-collection-resource}}
                  :handler    (integrant.core/ref ::web.collection/create-flower-collection)}}]]]]
   ::web/app     {:routes (integrant.core/ref ::web/routes)}
   ::web/service {:handler       (integrant.core/ref ::web/app)
                  :configuration {:port 3000, :join? false}}
   })
