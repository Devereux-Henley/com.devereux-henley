(ns com.devereux-henley.rts-api.db.result-set
  (:require
   [next.jdbc.result-set :as jdbc.result-set]))

(def default-options
  {:builder-fn jdbc.result-set/as-unqualified-kebab-maps})
