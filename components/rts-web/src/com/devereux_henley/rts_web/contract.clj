(ns com.devereux-henley.rts-web.contract
  (:require
   [com.devereux-henley.rts-web.web.game]
   [com.devereux-henley.rts-web.web.routes :as web.routes]
   [com.devereux-henley.rts-web.web.social-media]
   [com.devereux-henley.rts-web.web.tournament]
   [com.devereux-henley.rts-web.web.view :as web.view]))

(def root-route web.routes/root-route)
(def icon-routes web.routes/icon-routes)
(def view-routes web.routes/view-routes)
(def api-routes web.routes/api-routes)

(def css-version web.view/css-version)
