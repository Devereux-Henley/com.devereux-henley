(ns com.devereux-henley.rts-api.domain.game)

(def game
  [:map
   [:name :string]])

(def create-game-specification
  [:map
   [:id :uuid]
   [:name :string]])

(def update-game-specification
  [:map
   [:name :string]])
