(ns com.devereux-henley.rts-web.web.api
  "Top-level /api handlers that aren't bound to a specific domain — the
   hypermedia entry point being the only resident so far."
  (:require
   [integrant.core]
   [reitit.core]))

(defn- route-url
  [hostname router route-name]
  (str hostname (-> router (reitit.core/match-by-name! route-name) :path)))

(defmethod integrant.core/init-key ::get-root
  [_init-key {:keys [hostname]}]
  (fn [{router :reitit.core/router :as _request}]
    {:status 200
     :body   {:type   :api/root
              :_links {:self  (route-url hostname router :api/root)
                       :games (route-url hostname router :collection/game)}}}))
