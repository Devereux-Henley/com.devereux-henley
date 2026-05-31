(ns workspace
  (:require
   [com.devereux-henley.rts-api.configuration :as configuration]
   [integrant.core]
   [integrant.repl :refer [go halt reset]]))

(integrant.repl/set-prep! (fn [] (integrant.core/expand configuration/development-configuration)))

(comment
  (go)
  (halt)
  (reset))
