(ns com.devereux-henley.rose-api.flower)

(def flower
  [:map
   [:name :string]])

(def create-flower-specification
  [:map
   [:id :uuid]
   [:name :string]])

(def update-flower-specification
  [:map
   [:name :string]])
