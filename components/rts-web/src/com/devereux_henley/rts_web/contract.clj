(ns com.devereux-henley.rts-web.contract
  (:require
   [com.devereux-henley.rts-web.render :as render]
   [com.devereux-henley.rts-web.web.draft.api]
   [com.devereux-henley.rts-web.web.draft.view]
   [com.devereux-henley.rts-web.web.game.api]
   [com.devereux-henley.rts-web.web.game.view]
   [com.devereux-henley.rts-web.web.league.api]
   [com.devereux-henley.rts-web.web.league.view]
   [com.devereux-henley.rts-web.web.routes :as web.routes]
   [com.devereux-henley.rts-web.web.season.api]
   [com.devereux-henley.rts-web.web.season.view]
   [com.devereux-henley.rts-web.web.social-media.api]
   [com.devereux-henley.rts-web.web.stats.api]
   [com.devereux-henley.rts-web.web.tournament.api]
   [com.devereux-henley.rts-web.web.tournament.view]
   [com.devereux-henley.rts-web.web.view]))

(def root-route web.routes/root-route)
(def status-route web.routes/status-route)
(def shutdown-route web.routes/shutdown-route)
(def icon-routes web.routes/icon-routes)
(def view-routes web.routes/view-routes)
(def components-routes web.routes/components-routes)
(def api-routes web.routes/api-routes)

(def render-view render/render)
(def view-path render/view-path)
