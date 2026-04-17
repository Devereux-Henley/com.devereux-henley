(ns com.devereux-henley.rpfm-scraper.abilities-seed
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def ability-row-re
  #"(?s)(\(\d+,\s*'([^']+)',\s*'([^']+)',\s*'((?:[^']|'')*)',\s*'((?:[^']|'')*)',\s*'([^']+)'(,\s*[^)]+))")

(def ability-row-with-icon-re
  #"(?s)(\(\d+,\s*'([^']+)',\s*'([^']+)',\s*'((?:[^']|'')*)',\s*'((?:[^']|'')*)',\s*'([^']+)',\s*'([^']*)'(,\s*[^)]+))")

(def ability-row-with-cost-re
  #"(?s)\((\d+),\s*'([^']+)',\s*'([^']+)',\s*'((?:[^']|'')*)',\s*'((?:[^']|'')*)',\s*'([^']+)',\s*(\d+)(,\s*[^)]+)")

(def ability-seed-eid-re
  #"\(\d+,\s*'([0-9a-f\-]+)',\s*'([^']+)',")

(defn build-ability-key-eid-map
  "Parse seed-abilities.sql → {ability-key eid}."
  [filepath]
  (with-open [r (io/reader filepath)]
    (reduce (fn [m line]
              (if-let [[_ eid k] (re-find ability-seed-eid-re line)]
                (assoc m k eid)
                m))
            {}
            (line-seq r))))

(defn- sql-escape [s]
  (str/replace (or s "") "'" "''"))

(defn- unescape [s]
  (str/replace (or s "") "''" "'"))

(defn- id-part [^String full]
  (-> full
      (subs 1)
      (str/split #",")
      first
      str/trim))

(defn update-ability-seed-file
  "Rewrite seed-abilities.sql refreshing name, description, and cost from game
  loc + special-ability-map. Idempotent across three row shapes: no-cost,
  stale icon column, and the canonical 7-string-column + cost form."
  [filepath ability-name-map ability-tooltip-map special-ability-map]
  (let [raw-content (slurp filepath)
        content     (-> raw-content
                        (str/replace
                         "INSERT OR IGNORE INTO ability(id, eid, key, name, description, ability_type, icon,"
                         "INSERT OR IGNORE INTO ability(id, eid, key, name, description, ability_type,")
                        (str/replace
                         "INSERT OR REPLACE INTO ability(id, eid, key, name, description, ability_type, icon,"
                         "INSERT OR REPLACE INTO ability(id, eid, key, name, description, ability_type,"))
        has-cost    (str/includes? (subs content 0 (min 500 (count content))) "cost")
        updated     (atom 0)
        not-found   (atom 0)]
    (if has-cost
      (let [update-row-with-cost
            (fn [g]
              (let [id-part      (nth g 1)
                    eid          (nth g 2)
                    key          (nth g 3)
                    old-name     (nth g 4)
                    old-desc     (nth g 5)
                    ability-type (nth g 6)
                    rest-str     (nth g 8)
                    name         (get ability-name-map key)
                    desc         (get ability-tooltip-map key)
                    [nm dc]      (if (and (nil? name) (nil? desc))
                                   (do (swap! not-found inc)
                                       [(unescape old-name) (unescape old-desc)])
                                   (do (swap! updated inc)
                                       [(or name (unescape old-name))
                                        (or desc (unescape old-desc))]))
                    cost         (get special-ability-map key 0)]
                (format "(%s, '%s', '%s', '%s', '%s', '%s', %s%s"
                        id-part eid key (sql-escape nm) (sql-escape dc) ability-type cost rest-str)))
            new-content          (str/replace content ability-row-with-cost-re update-row-with-cost)]
        (binding [*out* *err*]
          (println (format "  [abilities] updated %d rows, %d without loc entry"
                           @updated @not-found)))
        new-content)
      (let [content     (-> content
                            (str/replace
                             "INSERT OR IGNORE INTO ability(id, eid, key, name, description, ability_type,"
                             "INSERT OR IGNORE INTO ability(id, eid, key, name, description, ability_type, cost,")
                            (str/replace
                             "INSERT OR REPLACE INTO ability(id, eid, key, name, description, ability_type,"
                             "INSERT OR REPLACE INTO ability(id, eid, key, name, description, ability_type, cost,"))
            refresh-row-with-cost
            (fn [g]
              (let [full         (nth g 0)
                    eid          (nth g 2)
                    key          (nth g 3)
                    old-name     (nth g 4)
                    old-desc     (nth g 5)
                    ability-type (nth g 6)
                    rest-str     (nth g 7)
                    id           (id-part full)
                    name         (get ability-name-map key)
                    desc         (get ability-tooltip-map key)
                    cost         (get special-ability-map key 0)]
                (if (and (nil? name) (nil? desc))
                  (do (swap! not-found inc)
                      (format "(%s, '%s', '%s', '%s', '%s', '%s', %s%s"
                              id eid key old-name old-desc ability-type cost rest-str))
                  (let [nm (or name (unescape old-name))
                        dc (or desc (unescape old-desc))]
                    (swap! updated inc)
                    (format "(%s, '%s', '%s', '%s', '%s', '%s', %s%s"
                            id eid key (sql-escape nm) (sql-escape dc) ability-type cost rest-str)))))
            strip-icon-add-cost
            (fn [g]
              (let [full         (nth g 0)
                    eid          (nth g 2)
                    key          (nth g 3)
                    old-name     (nth g 4)
                    old-desc     (nth g 5)
                    ability-type (nth g 6)
                    rest-str     (nth g 8)
                    id           (id-part full)
                    nm           (or (get ability-name-map key) (unescape old-name))
                    dc           (or (get ability-tooltip-map key) (unescape old-desc))
                    cost         (get special-ability-map key 0)]
                (swap! updated inc)
                (format "(%s, '%s', '%s', '%s', '%s', '%s', %s%s"
                        id eid key (sql-escape nm) (sql-escape dc) ability-type cost rest-str)))
            new-content (if (re-find ability-row-with-icon-re content)
                          (str/replace content ability-row-with-icon-re strip-icon-add-cost)
                          (str/replace content ability-row-re refresh-row-with-cost))]
        (binding [*out* *err*]
          (println (format "  [abilities] updated %d rows, %d without loc entry"
                           @updated @not-found)))
        new-content))))

(def spell-seed-eid-re
  #"\(\d+,\s*'([0-9a-f\-]+)',\s*'([^']+)',")

(defn build-spell-key-eid-map
  "Parse seed-spells.sql → {spell-key eid}."
  [filepath]
  (with-open [r (io/reader filepath)]
    (reduce (fn [m line]
              (if-let [[_ eid k] (re-find spell-seed-eid-re line)]
                (assoc m k eid)
                m))
            {}
            (line-seq r))))
