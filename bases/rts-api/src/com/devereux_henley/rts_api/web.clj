(ns com.devereux-henley.rts-api.web
  (:require
   [com.devereux-henley.content-negotiation.contract :as content-negotiation]
   [integrant.core]
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
   {:game/game "rts-api/resource/game.html"
    "exception" "rts-api/resource/error.html"}
   type
   "rts-api/resource/unknown.html"))

(defmethod integrant.core/init-key ::swagger-handler
  [_init-key _dependencies]
  (swagger/create-swagger-handler))

(defmethod integrant.core/init-key ::routes
  [_init-key routes]
  routes)

(defmethod integrant.core/init-key ::app
  [_init-key {:keys [routes]}]
  (ring/ring-handler
   (ring/router
    routes
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
                                            content-negotiation/html-format)
                                  (assoc-in [:formats "application/htmx+html"]
                                            content-negotiation/html-htmx-format)
                                  (assoc-in [:formats "application/htmx+html" :encoder-opts] {:view-fn view-by-type})))
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
    (ring/create-resource-handler {:root "rts-api/asset"
                                   :path "/"
                                   :not-found-handler (fn [_] {:status 404 :body "<div>Nothing here boss.</div>"})})
    (ring/create-default-handler))))

(defmethod integrant.core/init-key ::service
  [_init-key {:keys [handler configuration] :as dependencies}]
  (jetty/run-jetty handler configuration))

(defmethod integrant.core/halt-key! ::service
  [_init-key service]
  (.stop service))
