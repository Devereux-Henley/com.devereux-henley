(ns com.devereux-henley.rts-data-access.schema.replay
  "Malli return-shape schemas for the replay-domain Datalevin queries."
  (:require
   [com.devereux-henley.schema.contract :as schema.contract]))

(def replay-result-schema
  (schema.contract/to-schema
   [:map
    [:eid                           :uuid]
    [:match-id-external             [:maybe :string]]
    [:played-at                     [:maybe :string]]
    [:victory-condition             {:optional true} [:maybe :string]]
    [:parser-format                 [:maybe :string]]
    [:parsed-data                   [:maybe :any]]
    [:uploader-local-alliance-index {:optional true} [:maybe :int]]
    [:uploaded-by-sub               [:maybe :string]]
    [:created-at                    [:maybe inst?]]
    [:updated-at                    [:maybe inst?]]]))

(def create-spec-schema
  (schema.contract/to-schema
   [:map
    [:eid                           :uuid]
    [:match-id-external             :string]
    [:played-at                     :string]
    [:victory-condition             {:optional true} [:maybe :string]]
    [:parser-format                 :string]
    [:parsed-data                   :any]
    [:uploader-local-alliance-index {:optional true} [:maybe :int]]
    [:uploaded-by-sub               :string]]))
