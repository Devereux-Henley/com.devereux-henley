(ns com.devereux-henley.rts-domain.rules.draft
  (:require
   [odoyle.rules :as o]))

;; ─── Configuration ────────────────────────────────────────────────────────────
;;
;; Verified from RPFM wh3 game files (unit_set_to_mp_unit_caps_tables,
;; battle_unit_caps_for_team_sizes_tables) — 2026-04-12
;;
;; lord-max:          engine-enforced; mp_cap_group_lords exists but has no table entry
;; hero-max:          mp_cap_group_heroes cap=2
;; semc-wm-max:       mp_cap_group_single_entities cap=5 (lords+heroes+monsters+select
;;                    war machines combined); since lords are tracked separately via
;;                    lord-max the non-lord pool is 4, matching the game's intent of
;;                    1 lord + 4 heroes/monsters/machines = 5 total
;; semc-wm-categories: confirmed from unit_set_to_unit_junctions — SE pool uses
;;                    unit_caste=hero, unit_caste=lord, plus explicit monster and
;;                    war_machine records. Artillery and Monstrous Cavalry are NOT
;;                    in the game's single-entities pool.
;; chariot-wm-max:    mp_cap_group_chariots_warmachines cap=4; all units with
;;                    land_unit.category=war_machine (which covers both chariots and
;;                    war machines in WH3) are in this group. Independent from
;;                    semc-wm — e.g. regular Chaos Chariots are capped here but are
;;                    not in the SE pool.
;; per-unit-max:      216 regular unit entries at cap=4 in unit_set_to_mp_unit_caps;
;;                    cap=4 is the de-facto default for non-unique, non-rare units
;; section-slot-max:  battle_unit_caps_for_team_sizes team_size=1 starting_unit_cap=20
;;
;; Untracked cap groups (require per-unit flags not yet in schema):
;;   mp_cap_group_flying_unit cap=6      — needs is_flying on unit_statistics
;;   mp_cap_group_rare_single_entities cap=3 — needs is_rare_se on unit_statistics
;;   mp_cap_group_360_degree_fire cap=6  — needs is_360_fire on unit_statistics
;;   mp_cap_group_ranged_units cap=12    — missile_infantry + missile_cavalry; cap of
;;                                        12 out of 20 slots is non-binding in practice

(def ^:private caps
  {:lord-max             1
   :hero-max             2
   :semc-wm-max          4
   :semc-wm-categories   #{"Hero" "Monster" "War Machine"}
   :chariot-wm-max       4
   :chariot-wm-categories #{"Chariot" "War Machine"}
   :per-unit-max         4
   :unique-unit-max      1
   :section-slot-max     20})

;; ─── Rule keys ────────────────────────────────────────────────────────────────
;;
;; Facts inserted before fire-rules:
;;
;;   [::action :action/section    "main" | "reinforcements"]
;;   [::action :action/category   "Lord" | "Hero" | ...]
;;   [::action :action/is-unique  true | false]
;;   [::action :action/section-cost  N]    ;; current cost in target section before adding
;;   [::action :action/section-max   N]    ;; budget cap for target section (omit when nil)
;;   [::action :action/total-cost    N]    ;; cost of unit being added (base + selections)
;;
;;   [::army :counts/lords         N]   ;; lords across entire army (should always be 0 or 1)
;;   [::army :counts/heroes        N]   ;; heroes across entire army
;;   [::army :counts/semc-wm       N]   ;; semc-wm units across entire army
;;   [::army :counts/chariot-wm    N]   ;; chariot+war machine units across entire army
;;   [::army :counts/unit-copies   N]   ;; copies of the target unit already in army
;;   [::army :counts/section-slots N]   ;; units already in target section
;;
;; After fire-rules, query each key below; a non-empty result means that rule fired.

(def ^:private violation-rule-keys
  [::lord-section-violation
   ::lord-cap-violation
   ::hero-cap-violation
   ::semc-wm-cap-violation
   ::chariot-wm-cap-violation
   ::unique-unit-violation
   ::per-unit-cap-violation
   ::section-slot-violation
   ::budget-violation])

;; ─── Ruleset ──────────────────────────────────────────────────────────────────

(def ^:private draft-rules
  (o/ruleset
   {::lord-section-violation
    [:what
     [::action :action/section section]
     [::action :action/category category]
     :when (and (= section "reinforcements") (= category "Lord"))]

    ::lord-cap-violation
    [:what
     [::action :action/category category]
     [::army :counts/lords lord-count]
     :when (and (= category "Lord")
                (>= lord-count (:lord-max caps)))]

    ::hero-cap-violation
    [:what
     [::action :action/category category]
     [::army :counts/heroes hero-count]
     :when (and (= category "Hero")
                (>= hero-count (:hero-max caps)))]

    ::semc-wm-cap-violation
    [:what
     [::action :action/category category]
     [::army :counts/semc-wm semc-wm-count]
     :when (and (contains? (:semc-wm-categories caps) category)
                (>= semc-wm-count (:semc-wm-max caps)))]

    ::chariot-wm-cap-violation
    [:what
     [::action :action/category category]
     [::army :counts/chariot-wm chariot-wm-count]
     :when (and (contains? (:chariot-wm-categories caps) category)
                (>= chariot-wm-count (:chariot-wm-max caps)))]

    ::unique-unit-violation
    [:what
     [::action :action/is-unique is-unique]
     [::army :counts/unit-copies unit-copies]
     :when (and is-unique
                (>= unit-copies (:unique-unit-max caps)))]

    ::per-unit-cap-violation
    [:what
     [::action :action/is-unique is-unique]
     [::army :counts/unit-copies unit-copies]
     :when (and (not is-unique)
                (>= unit-copies (:per-unit-max caps)))]

    ::section-slot-violation
    [:what
     [::army :counts/section-slots slot-count]
     :when (>= slot-count (:section-slot-max caps))]

    ::budget-violation
    [:what
     [::action :action/section-cost section-cost]
     [::action :action/section-max section-max]
     [::action :action/total-cost total-cost]
     :when (> (+ section-cost total-cost) section-max)]}))

(def ^:private base-session
  (reduce o/add-rule (o/->session) draft-rules))

;; ─── Violation messages ───────────────────────────────────────────────────────

(defn- violation-message
  [rule-key unit-name section section-max _total-cost]
  (case rule-key
    ::lord-section-violation
    "Lords may only be drafted into the main army section."

    ::lord-cap-violation
    (str "You may not have more than " (:lord-max caps) " lord in your army.")

    ::hero-cap-violation
    (str "You may not have more than " (:hero-max caps) " heroes in your army.")

    ::semc-wm-cap-violation
    (str "You may not have more than " (:semc-wm-max caps)
         " single entity characters, monsters, and war machines combined in your army.")

    ::chariot-wm-cap-violation
    (str "You may not have more than " (:chariot-wm-max caps)
         " chariots and war machines combined in your army.")

    ::unique-unit-violation
    (str unit-name " is a unique unit and may only appear once in your army.")

    ::per-unit-cap-violation
    (str "You may not have more than " (:per-unit-max caps)
         " copies of " unit-name " in your army.")

    ::section-slot-violation
    (str "You may not have more than " (:section-slot-max caps)
         " units in the " section " section.")

    ::budget-violation
    (str "Adding " unit-name " would exceed the " section
         " army budget of " section-max ".")

    nil))

;; ─── Public API ───────────────────────────────────────────────────────────────

(defn validate-add
  "Returns nil when the add is valid; returns {:type :draft/add-error :message <str>}
   on the first rule violation found.

   army-entries  — seq of {:eid uuid :unit-category-name str :is-unique bool :section str}
                   for every unit currently in both sections of the army.
   unit-to-add   — {:eid uuid :unit-category-name str :is-unique bool :name str}
   section       — \"main\" | \"reinforcements\"
   section-cost  — current total point spend in the target section (before this add)
   section-max   — budget cap for the target section; when nil the budget rule is skipped
   total-cost    — base cost + selected mount + spells + items for the unit being added"
  [army-entries unit-to-add section section-cost section-max total-cost]
  (let [category       (:unit-category-name unit-to-add)
        is-unique      (boolean (:is-unique unit-to-add))
        unit-eid       (:eid unit-to-add)
        lord-count      (count (filter #(= "Lord" (:unit-category-name %)) army-entries))
        hero-count      (count (filter #(= "Hero" (:unit-category-name %)) army-entries))
        semc-wm-count   (count (filter #(contains? (:semc-wm-categories caps)
                                                   (:unit-category-name %)) army-entries))
        chariot-wm-count (count (filter #(contains? (:chariot-wm-categories caps)
                                                    (:unit-category-name %)) army-entries))
        unit-copies     (count (filter #(= unit-eid (:eid %)) army-entries))
        slot-count      (count (filter #(= section (:section %)) army-entries))
        session        (cond-> (-> base-session
                                   (o/insert ::army
                                             {:counts/lords         lord-count
                                              :counts/heroes        hero-count
                                              :counts/semc-wm       semc-wm-count
                                              :counts/chariot-wm    chariot-wm-count
                                              :counts/unit-copies   unit-copies
                                              :counts/section-slots slot-count})
                                   (o/insert ::action
                                             (cond-> {:action/section    section
                                                      :action/category   category
                                                      :action/is-unique  is-unique
                                                      :action/section-cost (or section-cost 0)
                                                      :action/total-cost (or total-cost 0)}
                                               (some? section-max)
                                               (assoc :action/section-max section-max))))
                         :always o/fire-rules)
        violation      (some (fn [rule-key]
                               (when (seq (o/query-all session rule-key))
                                 rule-key))
                             violation-rule-keys)]
    (when violation
      {:type    :draft/add-error
       :message (violation-message violation
                                   (:name unit-to-add)
                                   section
                                   section-max
                                   total-cost)})))
