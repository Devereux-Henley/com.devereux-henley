(ns com.devereux-henley.rts-api.web.asset
  (:require
   [clojure.java.io :as io]
   [taoensso.timbre :as log]))

(defn standard-asset-handler
  [asset-name _request]
  {:status 200
   :body (io/resource (str "rts-api/asset/" asset-name))})

(defn match-icon
  [eid]
  (case eid
    #uuid "946d4828-2fa1-409b-8c7b-1f84108fcea9" (io/file (io/resource "rts-api/asset/icon/twitch_icon.svg"))
    #uuid "41780b82-9fcf-44a4-aa1c-6a57cc835585" (io/file (io/resource "rts-api/asset/icon/youtube_icon.svg"))
    nil))

(defn icon-handler
  [{{{:keys [eid]} :path} :parameters
    :as                   _request}]
  (log/debug eid)
  (if-let [matching-icon (match-icon eid)]
    {:status 200
     :headers {"Content-Type" "image/svg+xml"}
     :body   matching-icon}
    {:status 404
     :headers {"Content-Type" "image/svg+xml"}
     :body   nil}))
