(ns com.devereux-henley.rpfm-scraper.units-edn
  "Emit `units.edn` + `unit-statistics.edn` for the Datalog seed from RPFM data
  and the curated authoring EDN.

  Each unit row is the authoring identity (eid, name, faction, type, category,
  description, is-unique) merged with the scraper-derived `key`, `mark`, `lore`,
  and `family-name`. Each unit-statistics row is the RPFM statline decomposed
  into `:unit-statistics/*` attributes, merged with the curated `abilities` /
  `draftable-spells` from authoring. See docs/rpfm-scraper/edn-seed-pipeline.md."
  (:require
   [clojure.java.io :as io]
   [com.devereux-henley.rpfm-scraper.marks-seed :as marks]
   [com.devereux-henley.rpfm-scraper.name-match :as nm]
   [com.devereux-henley.rpfm-scraper.overrides :as overrides]
   [com.devereux-henley.rpfm-scraper.rpfm :as rpfm]
   [com.devereux-henley.rpfm-scraper.seed-edn :as seed-edn]
   [com.devereux-henley.rpfm-scraper.stats :as stats]
   [com.devereux-henley.rpfm-scraper.unit-lores-seed :as lores]))

(def stat-fields
  "`[engine-doc-key attribute kind]` for every stored stat — a replica of
  `rts-data-access` `schema.datalog.unit-statistics/fields`, kept here so the
  lean scraper need not depend on that component (and Datalevin). Drift is
  caught by the verify phase's full-seed diff: a mismatched spec changes the
  regenerated `unit-statistics.edn`."
  [["ammunition"           :unit-statistics/ammunition           :long]
   ["armor"                :unit-statistics/armor                :long]
   ["barrier"              :unit-statistics/barrier              :long]
   ["bonus_vs_infantry"    :unit-statistics/bonus-vs-infantry    :long]
   ["bonus_vs_large"       :unit-statistics/bonus-vs-large       :long]
   ["charge_bonus"         :unit-statistics/charge-bonus         :long]
   ["cost"                 :unit-statistics/cost                 :long]
   ["health"              :unit-statistics/health               :long]
   ["leadership"           :unit-statistics/leadership           :long]
   ["melee_attack"         :unit-statistics/melee-attack         :long]
   ["melee_defence"        :unit-statistics/melee-defence        :long]
   ["missile_ap_damage"    :unit-statistics/missile-ap-damage    :long]
   ["missile_base_damage"  :unit-statistics/missile-base-damage  :long]
   ["missile_damage"       :unit-statistics/missile-damage       :long]
   ["range"                :unit-statistics/range                :long]
   ["speed"                :unit-statistics/speed                :long]
   ["unit_size"            :unit-statistics/unit-size            :long]
   ["weapon_ap_damage"     :unit-statistics/weapon-ap-damage     :long]
   ["weapon_damage"        :unit-statistics/weapon-damage        :long]
   ["weapon_strength"      :unit-statistics/weapon-strength      :long]
   ["is_large"             :unit-statistics/is-large             :boolean]
   ["abilities"            :unit-statistics/abilities            :strings]
   ["attributes"           :unit-statistics/attributes           :strings]
   ["melee_attack_types"   :unit-statistics/melee-attack-types   :strings]
   ["melee_modifiers"      :unit-statistics/melee-modifiers      :strings]
   ["missile_modifiers"    :unit-statistics/missile-modifiers    :strings]
   ["missile_damage_types" :unit-statistics/missile-damage-types :strings]
   ["draftable-spells"     :unit-statistics/draftable-spells     :spell-keys]])

(defn decode->stat-attrs
  "Decompose an engine-shaped, string-keyed statline into `:unit-statistics/*`
  attributes per `stat-fields`. Empty lists are omitted; `:spell-keys` pull the
  `\"key\"` out of each `{\"key\" k}` map."
  [decoded]
  (reduce (fn [attrs [doc-key attr kind]]
            (let [v (get decoded doc-key)]
              (case kind
                :long       (if (some? v) (assoc attrs attr (long v)) attrs)
                :boolean    (if (some? v) (assoc attrs attr (boolean v)) attrs)
                :strings    (if (seq v) (assoc attrs attr (vec v)) attrs)
                :spell-keys (let [ks (mapv #(get % "key") v)]
                              (if (seq ks) (assoc attrs attr ks) attrs)))))
          {}
          stat-fields))

;;; ─── Per-unit derivation ───────────────────────────────────────────────────

(defn- unit-faction-eid
  [unit]
  (second (:unit/faction unit)))

(defn- engine-key
  "Resolve a unit's engine `land_units` key from its name + faction slug."
  [unit faction-slug name-index]
  (first (nm/find-unit-key (:unit/name unit)
                           (get overrides/faction-key-map faction-slug)
                           name-index)))

(defn- derive-mark-lore-family
  "Final `{:mark :lore :family-name}` for a unit, composing the mark seed (engine
  `unit_set` membership + first mark-suffix strip) then the lore seed (name-suffix
  lore + mark inference + lore-suffix strip, plus the curated `extra-lore-pins`)."
  [unit engine-key key->mark lore-suffix->key eid->pin]
  (let [nm-str     (:unit/name unit)
        [base sfx] (lores/name+suffix nm-str)
        lore-key   (when sfx (get lore-suffix->key sfx))
        pin        (eid->pin (str (:unit/eid unit)))]
    (cond-> {:family-name (cond lore-key (lores/strip-mark-suffix base)
                                pin      (:family-name pin)
                                :else    (marks/name->family-name nm-str))
             :mark        (or (when lore-key (lores/infer-mark base))
                              (get key->mark engine-key))}
      (or lore-key pin) (assoc :lore (or lore-key (:lore-key pin))))))

;;; ─── Builders ──────────────────────────────────────────────────────────────

(defn build-units
  "Authoring unit identities ⊕ derived `key`/`mark`/`lore`/`family-name`."
  [version data junction-rows]
  (let [eid->slug        (seed-edn/faction-eid->key version)
        key->mark        (marks/build-unit-key-mark-map junction-rows)
        lore-suffix->key (seed-edn/lore-suffix->key version)
        eid->pin         (into {} (map (juxt :eid identity)) lores/extra-lore-pins)]
    (mapv (fn [unit]
            (let [slug (eid->slug (unit-faction-eid unit))
                  k    (engine-key unit slug (:name-index data))
                  {:keys [mark lore family-name]}
                  (derive-mark-lore-family unit k key->mark lore-suffix->key eid->pin)]
              (cond-> (assoc unit
                             :unit/key k
                             :unit/family-name family-name)
                mark (assoc :unit/mark (keyword mark))
                lore (assoc :unit/lore lore))))
          (seed-edn/read-authoring version "units.edn"))))

(defn build-unit-statistics
  "RPFM statlines decomposed ⊕ curated abilities/draftable-spells from authoring,
  one row per unit, with deterministic eids tied to the patch."
  [version data]
  (let [eid->slug (seed-edn/faction-eid->key version)
        patch-eid (seed-edn/derived-uuid "patch" version)
        curated   (into {}
                        (map (fn [r] [(unit-faction-eid {:unit/faction (:unit-statistics/unit r)})
                                      (select-keys r [:unit-statistics/abilities
                                                      :unit-statistics/draftable-spells])]))
                        (seed-edn/read-authoring version "unit-statistics.edn"))]
    (into []
          (keep (fn [unit]
                  (let [eid  (:unit/eid unit)
                        slug (eid->slug (unit-faction-eid unit))
                        k    (engine-key unit slug (:name-index data))
                        doc  (stats/extract-stats k (:main-unit-map data) (:land-unit-stats data)
                                                  (:agent-subtype-map data) (:equipment-map data)
                                                  (:ancillary-cost-map data))]
                    (when doc
                      (merge {:unit-statistics/eid   (seed-edn/derived-uuid "unit-statistics" patch-eid eid)
                              :unit-statistics/unit  [:unit/eid eid]
                              :unit-statistics/patch [:patch/eid patch-eid]}
                             (decode->stat-attrs (into {} doc))
                             (get curated eid))))))
          (seed-edn/read-authoring version "units.edn"))))

(defn emit!
  "Write the merged `units.edn` + `unit-statistics.edn` to the Datalog seed.
  `data` is the loaded RPFM table map; `data-dir` supplies the unit-set
  junctions that drive mark assignment."
  [version data data-dir]
  (let [junctions (:rows (rpfm/parse-rpfm-table
                          (str (io/file data-dir "unit_set_to_unit_junctions_tables.json"))))]
    (seed-edn/write-seed-file! version "units.edn" (build-units version data junctions))
    (seed-edn/write-seed-file! version "unit-statistics.edn" (build-unit-statistics version data))))
