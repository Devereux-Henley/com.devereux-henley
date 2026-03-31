(ns workspace
  (:require
   [com.devereux-henley.rts-api.system :as system]
   [com.devereux-henley.rts-data.contract :as rts-data]
   [com.devereux-henley.rts-api.db :as rts-db]))

(comment
  (system/go!)
  (system/halt!)
  (system/reload-views!)
  (system/restart!)
  (rts-data/seed-db rts-db/db-spec))
