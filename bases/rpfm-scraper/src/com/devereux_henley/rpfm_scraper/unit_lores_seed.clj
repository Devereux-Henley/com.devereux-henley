(ns com.devereux-henley.rpfm-scraper.unit-lores-seed
  "Infers `unit.lore` and refines `unit.family-name` for variant unit
  rows whose engine name carries a trailing `(<Suffix>)` resolving to a
  lore in the catalogue.  Splits a unit name into base + lore suffix,
  strips mark and lore parentheticals to derive the family name, and
  carries hand-curated lore pins for rows that don't follow the suffix
  pattern.  Each (mark, lore) pair is its own unit row sharing a
  `family-name` with its siblings; the draft panel's family selector
  toggles between variants by swapping `unit-eid`."
  (:require
   [clojure.string :as str]))

(def ^:private suffix-re #"^(.+?) \(([A-Za-z ]+)\)$")

(def ^:private weapon-lore-suffix-re
  ;; Two-dimension parenthetical: '<name> (<weapon> - <lore>)'.  Used
  ;; for Vampire Fleet Admirals where the engine ships six rows
  ;; (Pistol|Polearms × Death|Deep|Vampires) but the user-facing UI
  ;; should collapse to two families ('… (Pistol)', '… (Polearms)')
  ;; with lore as the inner dimension — same end shape as a marked
  ;; spellcaster, just with the weapon kept in the family name.
  #"^(.+?) \(([A-Za-z]+) - ([A-Za-z ]+)\)$")

(defn name+suffix
  "Splits 'Archmage (High)' → ['Archmage' 'High'].  Also splits
  'Vampire Fleet Admiral (Pistol - Death)' →
  ['Vampire Fleet Admiral (Pistol)' 'Death'] so the weapon stays in
  the family name and only the lore is extracted as the suffix.
  Returns nil when the trailing token isn't a recognised parenthetical."
  [name]
  (or (when-let [[_ base weapon lore] (re-matches weapon-lore-suffix-re name)]
        [(str base " (" weapon ")") lore])
      (when-let [[_ base suffix] (re-matches suffix-re name)]
        [base suffix])))

(def ^:private mark-name-patterns
  "Substrings whose presence in a unit's engine name implies a mark,
  mapped to that mark.  Detection checks containment; `strip-mark-suffix`
  removes them all."
  {" of Khorne"   "khorne"
   " of Nurgle"   "nurgle"
   " of Slaanesh" "slaanesh"
   " of Tzeentch" "tzeentch"
   " (Khorne)"    "khorne"
   " (Nurgle)"    "nurgle"
   " (Slaanesh)"  "slaanesh"
   " (Tzeentch)"  "tzeentch"})

(defn strip-mark-suffix
  [name]
  (reduce (fn [n s] (str/replace n s "")) name (keys mark-name-patterns)))

(defn infer-mark
  "Returns the first mark whose suffix appears in `name`, or nil when
  the name carries no mark token.  Used as a fallback for variant rows
  that haven't been linked to an engine `key` yet."
  [name]
  (some (fn [[s m]] (when (str/includes? name s) m)) mark-name-patterns))

(def extra-lore-pins
  "Hand-curated lore assignments for unit rows whose engine name
  doesn't carry a `(<Suffix>)` token.  Two flavors:
  * unmarked Daemon Prince variants in Daemons of Chaos and Warriors
    of Chaos, both pinned to the Lore of Fire pool;
  * the seven Empire wizard rows whose color-themed names (Amber,
    Bright, Celestial, Gold, Grey, Jade, Light) imply a lore but
    don't carry a `(<Lore>)` suffix the scraper can match on.
  Modeled as overrides because their family identity (a shared
  'Wizard' / 'Daemon Prince' label) precludes a renaming-style fix."
  [{:eid         "00050004-0000-4000-8000-000000000000"
    :lore-key    "wh_main_lore_fire"
    :family-name "Daemon Prince"}
   {:eid         "00170013-0000-4000-8000-000000000000"
    :lore-key    "wh_main_lore_fire"
    :family-name "Daemon Prince"}
   {:eid         "0001000b-0000-4000-8000-000000000000"
    :lore-key    "wh_dlc03_lore_beasts"
    :family-name "Wizard"}
   {:eid         "0001000d-0000-4000-8000-000000000000"
    :lore-key    "wh_main_lore_fire"
    :family-name "Wizard"}
   {:eid         "0001000e-0000-4000-8000-000000000000"
    :lore-key    "wh_main_lore_heavens"
    :family-name "Wizard"}
   {:eid         "00010012-0000-4000-8000-000000000000"
    :lore-key    "wh_main_lore_metal"
    :family-name "Wizard"}
   {:eid         "00010014-0000-4000-8000-000000000000"
    :lore-key    "wh_dlc05_lore_shadows"
    :family-name "Wizard"}
   {:eid         "00010016-0000-4000-8000-000000000000"
    :lore-key    "wh_dlc05_lore_life"
    :family-name "Wizard"}
   {:eid         "00010019-0000-4000-8000-000000000000"
    :lore-key    "wh_main_lore_light"
    :family-name "Wizard"}])
