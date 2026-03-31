(ns workspace
  (:require
   [com.devereux-henley.rts-api.system :as system]))

(comment
  (system/go!)
  (system/halt!)
  (system/reload-views!)
  (system/restart!))
