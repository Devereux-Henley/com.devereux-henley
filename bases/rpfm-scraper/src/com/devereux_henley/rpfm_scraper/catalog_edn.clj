(ns com.devereux-henley.rpfm-scraper.catalog-edn
  "Emit abilities.edn, spells.edn, and subfactions.edn for the Datalog seed.

  Abilities and spells are hybrid: the scraper patches their RPFM-derived
  fields (ability `name` + `cost`, spell `cost`) onto the curated authoring
  identity (which keeps eid/key/type, ability `description`, full spell
  display). Subfactions are fully generated — engine `factions_tables` keys
  paired with display names and the parent faction eid, with stable UUID-v5
  eids. See docs/rpfm-scraper/edn-seed-pipeline.md."
  (:require
   [clojure.java.io :as io]
   [com.devereux-henley.rpfm-scraper.rpfm :as rpfm]
   [com.devereux-henley.rpfm-scraper.seed-edn :as seed-edn]
   [com.devereux-henley.rpfm-scraper.subfactions-seed :as sf]))

(defn build-abilities
  "Authoring ability identities (incl. curated `description`) ⊕ RPFM `name` +
  `cost`. Keys are emitted in canonical order for a stable seed."
  [version data]
  (mapv (fn [a]
          (let [k (:ability/key a)]
            (array-map
             :ability/eid          (:ability/eid a)
             :ability/key          k
             :ability/name         (get (:ability-name-map data) k)
             :ability/description  (:ability/description a)
             :ability/ability-type (:ability/ability-type a)
             :ability/cost         (get (:special-ability-map data) k 0)
             :ability/game         (:ability/game a))))
        (seed-edn/read-authoring version "abilities.edn")))

(defn build-spells
  "Authoring spell display ⊕ RPFM gold `cost`. Keys in canonical order."
  [version data]
  (mapv (fn [s]
          (array-map
           :spell/eid         (:spell/eid s)
           :spell/key         (:spell/key s)
           :spell/name        (:spell/name s)
           :spell/description (:spell/description s)
           :spell/spell-type  (:spell/spell-type s)
           :spell/mana-cost   (:spell/mana-cost s)
           :spell/cost        (get (:special-ability-map data) (:spell/key s) 0)
           :spell/game        (:spell/game s)))
        (seed-edn/read-authoring version "spells.edn")))

(defn build-subfactions
  "subfactions.edn rows — engine `factions_tables` keys mapped to display name +
  parent faction eid (via the race slug), with stable UUID-v5 eids. Keys whose
  slug doesn't resolve to a seeded faction are skipped."
  [version data-dir]
  (let [faction-rows (:rows (rpfm/parse-rpfm-table (str (io/file data-dir "factions_tables.json"))))
        faction-loc  (rpfm/parse-loc-file (str (io/file data-dir "factions_loc.json")))
        name-map     (sf/build-faction-display-name-map faction-rows faction-loc)
        fkey->eid    (seed-edn/faction-key->eid version)]
    (->> faction-rows
         (sort-by #(get % "key"))
         (keep (fn [row]
                 (let [k    (get row "key")
                       nm   (get name-map k)
                       slug (some-> k sf/faction-slug-for-key)
                       feid (get fkey->eid slug)]
                   (when (and (seq k) (seq nm) feid)
                     {:subfaction/eid     (sf/uuid-v5 sf/subfaction-uuid-namespace k)
                      :subfaction/key     k
                      :subfaction/name    nm
                      :subfaction/faction [:faction/eid feid]}))))
         vec)))

(defn emit!
  "Write abilities.edn, spells.edn, and subfactions.edn to the Datalog seed for
  `version`. `data` is the loaded RPFM table map; `data-dir` supplies the
  factions tables/loc for subfactions."
  [version data data-dir]
  (seed-edn/write-seed-file! version "abilities.edn" (build-abilities version data))
  (seed-edn/write-seed-file! version "spells.edn" (build-spells version data))
  (seed-edn/write-seed-file! version "subfactions.edn" (build-subfactions version data-dir)))
