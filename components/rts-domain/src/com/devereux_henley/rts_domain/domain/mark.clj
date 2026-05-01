(ns com.devereux-henley.rts-domain.domain.mark
  "Mark of Chaos dimension on units.  WoC + DoC unit rows carry the
  family display name; the mark variant is surfaced via a single
  dimension column.  The engine `land_units` key suffix is the
  canonical, mechanical signal — `seed-unit-marks.sql` mirrors this
  inference in SQL so the rpfm scraper can regenerate the seed file
  without hand-curation per unit.")

(def marks
  "Closed set of valid Mark of Chaos values."
  #{"khorne" "nurgle" "slaanesh" "tzeentch" "undivided"})

(defn mark-from-key
  "Returns the Mark of Chaos that an engine `land_units` key encodes,
  or nil if the key is not marked.  Two suffix conventions:

    `_mkho` / `_mnur` / `_msla` / `_mtze` — WoC sorcerer & daemon
    prince mark variants (e.g. `wh3_dlc20_chs_cha_daemon_prince_mkho`).

    `_kho_` / `_nur_` / `_sla_` / `_tze_` — DoC + mono-god daemon
    rank-and-file living under a god subfaction (e.g.
    `wh3_main_kho_inf_bloodletters_0`).

    `_khorne_` / `_nurgle_` / `_slaanesh_` / `_tzeentch_` — proper-god
    suffix variants (e.g. `wh3_main_sla_cha_alluress_slaanesh_0`)."
  [k]
  (cond
    (nil? k) nil
    (or (.contains k "_mkho") (.contains k "_kho_") (.contains k "_khorne"))   "khorne"
    (or (.contains k "_mnur") (.contains k "_nur_") (.contains k "_nurgle"))   "nurgle"
    (or (.contains k "_msla") (.contains k "_sla_") (.contains k "_slaanesh")) "slaanesh"
    (or (.contains k "_mtze") (.contains k "_tze_") (.contains k "_tzeentch")) "tzeentch"
    :else nil))

(defn mark-from-stat-attributes
  "Returns the Mark of Chaos encoded as a `mark_<god>` entry in the
  raw `unit_statistics.attributes` array, or nil if no mark attribute
  is present."
  [attributes]
  (some {"mark_khorne"    "khorne"
         "mark_nurgle"    "nurgle"
         "mark_slaanesh"  "slaanesh"
         "mark_tzeentch"  "tzeentch"
         "mark_undivided" "undivided"}
        attributes))
