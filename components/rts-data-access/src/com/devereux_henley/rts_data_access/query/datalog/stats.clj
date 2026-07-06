(ns com.devereux-henley.rts-data-access.query.datalog.stats
  "Datalevin reads for the stats domain — faction win/loss standings derived
  from completed tournament match-games. Each scored match-game contributes one
  side per player whose draft has a faction; a win is when the match-game's
  `winner-sub` matches that side's match player-sub."
  (:require
   [datalevin.core :as d]))

(def ^:private standings-pattern
  [:match-game/winner-sub
   {:match/_games [:match/player-one-sub :match/player-two-sub]}
   {:match-game/player-one-draft [{:draft/faction [:faction/eid :faction/name]}]}
   {:match-game/player-two-draft [{:draft/faction [:faction/eid :faction/name]}]}])

(defn- side
  "A `{:faction … :won bool}` record for one player side of a match-game, or nil
  when that side has no drafted faction."
  [match-game winner-sub draft-key match-sub-key match]
  (when-let [faction (get-in match-game [draft-key :draft/faction])]
    {:faction faction
     :won     (= winner-sub (get match match-sub-key))}))

(defn- ->standings
  "Aggregate scored match-games into faction standings rows, sorted by wins,
  then matches-played, then name."
  [match-games]
  (->> match-games
       (mapcat (fn [mg]
                 (let [match  (first (:match/_games mg))
                       winner (:match-game/winner-sub mg)]
                   (keep #(apply side mg winner %)
                         [[:match-game/player-one-draft :match/player-one-sub match]
                          [:match-game/player-two-draft :match/player-two-sub match]]))))
       (group-by (comp :faction/eid :faction))
       (mapv (fn [[faction-eid sides]]
               (let [faction (:faction (first sides))
                     played  (count sides)
                     wins    (count (filter :won sides))]
                 {:faction-eid    faction-eid
                  :faction-name   (:faction/name faction)
                  :matches-played played
                  :wins           wins
                  :losses         (- played wins)})))
       (sort-by (juxt (comp - :wins) (comp - :matches-played) :faction-name))
       vec))

(defn- scoped-standings
  "Run the standings query scoped by `scope-clauses` (which must bind `?mg` to
  the in-scope scored match-games against the `?scope-eid` input)."
  [conn scope-eid scope-clauses]
  (->standings
   (d/q (into '[:find [(pull ?mg pattern) ...]
                :in $ pattern ?scope-eid
                :where [?mg :match-game/winner-sub _]
                [?m :match/games ?mg]
                [?m :match/tournament ?t]]
              scope-clauses)
        (d/db conn) standings-pattern scope-eid)))

(defn faction-standings-for-game
  "Faction standings across every scored match-game in the game's tournaments."
  [conn game-eid]
  (scoped-standings conn game-eid '[[?t :tournament/game ?g] [?g :game/eid ?scope-eid]]))

(defn faction-standings-for-league
  "Faction standings across every scored match-game in the league's tournaments."
  [conn league-eid]
  (scoped-standings conn league-eid '[[?t :tournament/league ?l] [?l :league/eid ?scope-eid]]))

(defn faction-standings-for-season
  "Faction standings across every scored match-game in the season's tournaments."
  [conn season-eid]
  (scoped-standings conn season-eid '[[?t :tournament/season ?s] [?s :season/eid ?scope-eid]]))
