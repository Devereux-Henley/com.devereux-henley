(ns com.devereux-henley.rts-web.skin
  "Per-game visual skin dispatch. Maps a game eid onto the name of an optional
   theme stylesheet loaded after app.css in entrypoint.html. Games without an
   entry render with the default War Room tokens.")

(def game-eid->skin
  "Game eid → skin identifier. The identifier matches the theme filename stem
   (resources/rts-web/asset/style/themes/<skin>.css)."
  {"eea787d7-1065-45eb-a3f6-e26f32c294a1" "warhammer3"})

(def game-eid->logo
  "Game eid → filename under /image/ for the game's logo."
  {"eea787d7-1065-45eb-a3f6-e26f32c294a1" "wh3_logo.webp"})

(defn skin-for-game
  "Return the skin identifier for the given game eid, or nil for the default
   War Room look. `game-eid` may be a `java.util.UUID` or a string."
  [game-eid]
  (some-> game-eid str game-eid->skin))

(defn logo-for-game
  "Return the logo asset filename for the given game eid, or nil if unknown."
  [game-eid]
  (some-> game-eid str game-eid->logo))
