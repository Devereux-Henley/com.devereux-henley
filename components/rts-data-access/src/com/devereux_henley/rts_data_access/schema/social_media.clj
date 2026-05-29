(ns com.devereux-henley.rts-data-access.schema.social-media
  "Malli return-shape schemas for the social-media-domain Datalevin queries."
  (:require
   [com.devereux-henley.schema.contract :as schema.contract]))

(def platform-result-schema
  (schema.contract/to-schema
   [:map
    [:eid          :uuid]
    [:name         [:maybe :string]]
    [:description  [:maybe :string]]
    [:platform-url [:maybe :string]]]))
