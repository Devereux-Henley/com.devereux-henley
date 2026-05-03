(ns com.devereux-henley.rpfm-scraper.marks-seed
  "Generates `seed-unit-marks.sql` from authoritative engine data — namely
  membership in the Mark-of-Chaos-bearing entries of `unit_sets_tables` /
  `unit_set_to_unit_junctions_tables`.  This replaces the earlier hand-
  maintained pattern-matching seed that combined `mark_<god>` attributes,
  engine-key infix sniffing, and faction-id fallbacks."
  (:require
   [clojure.string :as str]))

(def ^:private mark-bearing-sets
  "Engine `unit_sets_tables.key` → mark.  Membership in any of these sets via
  `unit_set_to_unit_junctions_tables` (with `exclude = false`) is treated as
  the engine's authoritative mark assignment.

  Coverage:
    wh3_main_<god>_all                     — mono-god subfaction membership
                                             (Heralds, mono-god rank-and-file
                                             daemons including pro_<god>_*
                                             campaign variants, cross-faction
                                             characters like Taurox-Khorne)
    <god>_characters                       — lord/hero subtypes (foot + mounts)
    wh3_dlc20_chs_<god>_marked_units       — WoC rank-and-file with the mark
    wh3_dlc20_chs_<god>_daemon_units       — DoC daemon units bound to the god
    undivided_characters                   — unmarked WoC characters (Daemon
                                             Prince base, Chaos Lord/Sorcerer
                                             base variants, etc.)
    chaos_spawn_undivided                  — the lone unmarked Chaos Spawn

  The mono-god `_all` sets are the broadest-precise source — they cover the
  Heralds and `pro_<god>_*` proxy-faction variants that the per-mark sets
  above miss.  The narrower sets remain in the map so that a unit listed in
  multiple sets always resolves to the same mark."
  {"wh3_main_kho_all"                    "khorne"
   "wh3_main_nur_all"                    "nurgle"
   "wh3_main_sla_all"                    "slaanesh"
   "wh3_main_tze_all"                    "tzeentch"
   "khorne_characters"                   "khorne"
   "wh3_dlc20_chs_khorne_marked_units"   "khorne"
   "wh3_dlc20_chs_khorne_daemon_units"   "khorne"
   "nurgle_characters"                   "nurgle"
   "wh3_dlc20_chs_nurgle_marked_units"   "nurgle"
   "wh3_dlc20_chs_nurgle_daemon_units"   "nurgle"
   "slaanesh_characters"                 "slaanesh"
   "wh3_dlc20_chs_slaanesh_marked_units" "slaanesh"
   "wh3_dlc20_chs_slaanesh_daemon_units" "slaanesh"
   "tzeentch_characters"                 "tzeentch"
   "wh3_dlc20_chs_tzeentch_marked_units" "tzeentch"
   "wh3_dlc20_chs_tzeentch_daemon_units" "tzeentch"
   "undivided_characters"                "undivided"
   "chaos_spawn_undivided"               "undivided"})

(def ^:private specific-mark-sets
  "Tier-1 mark-bearing sets — narrow and unambiguous.  A unit's
  membership in any one of these is treated as authoritative."
  #{"khorne_characters" "wh3_dlc20_chs_khorne_marked_units" "wh3_dlc20_chs_khorne_daemon_units"
    "nurgle_characters" "wh3_dlc20_chs_nurgle_marked_units" "wh3_dlc20_chs_nurgle_daemon_units"
    "slaanesh_characters" "wh3_dlc20_chs_slaanesh_marked_units" "wh3_dlc20_chs_slaanesh_daemon_units"
    "tzeentch_characters" "wh3_dlc20_chs_tzeentch_marked_units" "wh3_dlc20_chs_tzeentch_daemon_units"
    "undivided_characters" "chaos_spawn_undivided"})

(defn build-unit-key-mark-map
  "Returns {unit_record (engine `key`) → mark string} from the parsed
  `unit_set_to_unit_junctions_tables` rows.  Two-tier resolution:

    1. Specific sets (lord/hero/marked/daemon/undivided) — if a unit
       appears in any one of these, that's the authoritative mark.

    2. Broad mono-god sets (`wh3_main_<god>_all`) — fallback when the
       unit isn't in any specific set.  If the unit appears in
       multiple `<god>_all` sets simultaneously (e.g. the cross-faction
       base `wh3_main_dae_inf_chaos_furies_0` is in all four), it's a
       faction-shared base — mark Undivided rather than picking one
       god arbitrarily."
  [junction-rows]
  (let [;; tier-1: explicit per-set mark assignments
        specific      (reduce
                       (fn [m row]
                         (let [unit-rec (get row "unit_record")
                               excluded (true? (get row "exclude"))
                               set-key  (get row "unit_set")]
                           (if (and (seq unit-rec) (not excluded)
                                    (contains? specific-mark-sets set-key))
                             (assoc m unit-rec (get mark-bearing-sets set-key))
                             m)))
                       {}
                       junction-rows)
        ;; tier-2: collect every god `_all` set a unit belongs to
        all-set-marks (reduce
                       (fn [m row]
                         (let [unit-rec (get row "unit_record")
                               excluded (true? (get row "exclude"))
                               set-key  (get row "unit_set")
                               mark     (when (str/starts-with? (or set-key "") "wh3_main_")
                                          (get mark-bearing-sets set-key))]
                           (if (and (seq unit-rec) mark (not excluded))
                             (update m unit-rec (fnil conj #{}) mark)
                             m)))
                       {}
                       junction-rows)]
    (reduce-kv
     (fn [m unit-rec marks]
       (if (contains? specific unit-rec)
         m
         (assoc m unit-rec
                (if (= 1 (count marks))
                  (first marks)
                  ;; Multi-god membership ⇒ faction-shared base.
                  "undivided"))))
     specific
     all-set-marks)))

(def ^:private no-rows-sentinel
  "-- no mark assignments in this scrape (preserved on subsequent empty runs)\n")

(def ^:private family-name-strips
  "Substrings stripped from a unit's engine `name` to derive its
  `family_name` (the roster-grouping key).  The seed emits a CASE that
  applies these strips to `family_name` — `name` itself stays as the
  engine-original string so the slot, panel header, and replay enrichment
  can show 'Daemon Prince of Khorne'.

  Order matters: the longer ' of <God>' patterns are tried first so a
  name like 'Chaos Knights of Khorne (Lances)' strips just the mark
  segment and family-groups with 'Chaos Knights (Lances)' — not 'Chaos
  Knights' on its own.  Family grouping is also faction-scoped, so
  cross-faction same-stem units (e.g. mono-god 'Spawn of Khorne' in the
  Khorne faction vs 'Spawn of Nurgle' in Nurgle) never collide."
  [" of Khorne"
   " of Nurgle"
   " of Slaanesh"
   " of Tzeentch"
   " (Khorne)"
   " (Nurgle)"
   " (Slaanesh)"
   " (Tzeentch)"])

(defn- format-mark-seed
  "Renders the engine-key→mark map as a single combined UPDATE.  Single
  statement because next.jdbc.execute! only consumes the first one of a
  multi-statement string (same constraint as seed-unit-keys.sql)."
  [unit-key->mark]
  (if (empty? unit-key->mark)
    no-rows-sentinel
    (let [sorted (sort-by first unit-key->mark)]
      (str
       "-- Generated by rpfm-scraper from unit_sets_tables +\n"
       "-- unit_set_to_unit_junctions_tables.  Do not hand-edit — re-run\n"
       "-- the scraper.\n"
       "--\n"
       "-- Authoritative Mark-of-Chaos assignment via engine unit_set\n"
       "-- membership in:\n"
       "--   wh3_main_<god>_all                     (mono-god subfaction)\n"
       "--   <god>_characters                       (lord/hero subtypes)\n"
       "--   wh3_dlc20_chs_<god>_marked_units       (WoC rank-and-file)\n"
       "--   wh3_dlc20_chs_<god>_daemon_units       (DoC daemon units)\n"
       "--   undivided_characters                   (unmarked WoC characters)\n"
       "--   chaos_spawn_undivided                  (unmarked Chaos Spawn)\n"
       "--\n"
       "-- Single combined UPDATE because next.jdbc.execute! only consumes\n"
       "-- the first statement of a multi-statement string.  Runs after\n"
       "-- seed-unit-keys.sql so the engine `key` column is populated.\n"
       "UPDATE unit\n"
       "SET mark = CASE key\n"
       (apply str
              (for [[k mark] sorted]
                (format "  WHEN '%s' THEN '%s'\n" (str/replace k "'" "''") mark)))
       "  ELSE mark\n"
       "END,\n"
       "family_name = CASE\n"
       (apply str
              (for [s family-name-strips]
                (format "  WHEN INSTR(name, '%s') > 0 THEN REPLACE(name, '%s', '')\n" s s)))
       "  ELSE name\n"
       "END;\n"))))

(defn generate-mark-seed
  "Read `unit_set_to_unit_junctions_tables` rows, derive the engine-key→mark
  map, and return `{:content :rows :empty?}` so the standard write helper
  in core.clj can persist it."
  [junction-rows]
  (let [unit-key->mark (build-unit-key-mark-map junction-rows)]
    {:content (format-mark-seed unit-key->mark)
     :rows    unit-key->mark
     :empty?  (empty? unit-key->mark)}))
