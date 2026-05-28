(ns com.devereux-henley.rts-data-access.schema.datalog.game-social-link
  "Datalevin attributes for the `:game-social-link` entity — a per-game social
  media link pointing at one `:social-media-platform`.")

(def schema
  {:game-social-link/eid      {:db/valueType :db.type/uuid
                               :db/unique    :db.unique/identity}
   :game-social-link/url      {:db/valueType :db.type/string}
   :game-social-link/game     {:db/valueType :db.type/ref}
   :game-social-link/platform {:db/valueType :db.type/ref}})
