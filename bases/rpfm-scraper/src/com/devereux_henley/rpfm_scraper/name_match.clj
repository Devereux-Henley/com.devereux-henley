(ns com.devereux-henley.rpfm-scraper.name-match
  (:require
   [clojure.string :as str]
   [com.devereux-henley.rpfm-scraper.overrides :as overrides]))

(defn normalize-name [s]
  (when s
    (-> s
        (str/replace "\u2013" "-")
        (str/replace "\u2014" "-")
        (str/replace "\u2018" "'")
        (str/replace "\u2019" "'")
        str/trim)))

;; ---------------------------------------------------------------------------
;; Mark of Chaos
;; ---------------------------------------------------------------------------
;;
;; Marked Warriors-of-Chaos / Daemons-of-Chaos units encode the mark in
;; both directions: the display name carries " of <God>" / " (<God>)" /
;; the bare god noun, and the engine `unit` key carries one of three
;; suffix conventions: `_m<short>` (e.g. `_mtze`), `_<short>_` (e.g.
;; `_kho_`), or `_<full-god>` (e.g. `_tzeentch`).  Using the two together
;; lets `find-unit-key` disambiguate the variants of "Chaos Sorcerer" /
;; "Chaos Sorcerer Lord" / "Herald of <God>" / "Daemon Prince of <God>"
;; without having to hand-pin every case in `overrides/display-name-unit-key-overrides`.

(def ^:private display-name-mark-re
  "Captures the trailing god noun in display names like 'Chaos Sorcerer
  of Tzeentch', 'Chaos Furies (Khorne)', or 'Daemon Prince of Slaanesh'."
  #"(?i)(?:\s+of\s+|\s*\(\s*)(khorne|nurgle|slaanesh|tzeentch)\s*\)?$")

(def ^:private name-implied-mark
  "Display names that imply a Mark of Chaos by virtue of being a
  uniquely-marked daemon family in the engine.  Keeps the per-name
  list explicit (and tiny) instead of growing a per-(name \u00d7 lore)
  override entry \u2014 Alluress lives only as a Slaanesh daemon, the
  Plagueridden only as Nurgle, etc."
  {"Alluress"                       "slaanesh"
   "Bloodspeaker"                   "khorne"
   "Bloodreaper"                    "khorne"
   "Bringer of Pain"                "slaanesh"
   "Cultist of Khorne"              "khorne"
   "Cultist of Nurgle"              "nurgle"
   "Cultist of Slaanesh"            "slaanesh"
   "Cultist of Tzeentch"            "tzeentch"
   "Exalted Bloodthirster"          "khorne"
   "Exalted Great Unclean One"      "nurgle"
   "Exalted Keeper of Secrets"      "slaanesh"
   "Exalted Lord of Change"         "tzeentch"
   "Iridescent Horror"              "tzeentch"
   "Plagueridden"                   "nurgle"})

(defn- mark-from-faction-prefixes
  "Maps the mono-god subfaction prefix passed by `core/update-unit-seeds!`
  to the implied mark.  Returns nil for non-mono-god factions (DoC,
  WoC, Beastmen, \u2026) so non-implied names keep their literal lookup."
  [faction-prefixes]
  (some {"kho" "khorne"
         "nur" "nurgle"
         "sla" "slaanesh"
         "tze" "tzeentch"}
        faction-prefixes))

(defn mark-from-display-name
  "Returns 'khorne' / 'nurgle' / 'slaanesh' / 'tzeentch' if the display
  name carries a Mark of Chaos signal: trailing 'X of <God>' / 'X (<God>)'
  suffix, or membership in the `name-implied-mark` dictionary of
  uniquely-marked daemons (Alluress, Plagueridden, \u2026).  Lowercased for
  stable downstream matching against engine key suffixes."
  [name]
  (when name
    (or (when-let [m (re-find display-name-mark-re name)]
          (str/lower-case (second m)))
        (get name-implied-mark name))))

(defn strip-display-name-mark
  "Drops the trailing mark from a display name ('Daemon Prince of Khorne'
  \u2192 'Daemon Prince') so the base name can be looked up in the loc-derived
  name index when the composed name has no candidates."
  [name]
  (when name
    (str/trim (str/replace name display-name-mark-re ""))))

(defn mark-from-key
  "Returns the mark a `unit`/`land_unit` engine key encodes, or nil.
  Mirrors the inference in `seed-unit-marks.sql` and the runtime
  `rts-domain.domain.mark/mark-from-key` helper \u2014 kept inline here so
  the scraper has no dep on the runtime base."
  [k]
  (cond
    (nil? k) nil
    (or (str/includes? k "_mkho") (str/includes? k "_kho_") (str/includes? k "_khorne"))   "khorne"
    (or (str/includes? k "_mnur") (str/includes? k "_nur_") (str/includes? k "_nurgle"))   "nurgle"
    (or (str/includes? k "_msla") (str/includes? k "_sla_") (str/includes? k "_slaanesh")) "slaanesh"
    (or (str/includes? k "_mtze") (str/includes? k "_tze_") (str/includes? k "_tzeentch")) "tzeentch"
    :else nil))

(defn- prefer-mark
  "Among `candidates` (each `[unit-key land-unit-key]`), keep only those
  whose `unit-key` encodes `mark`.  Returns the original collection
  when nothing matches so the caller can fall through to the next
  filter."
  [candidates mark]
  (if-not mark
    candidates
    (let [filtered (filterv (fn [[uk _]] (= mark (mark-from-key uk))) candidates)]
      (if (seq filtered) filtered candidates))))

(defn- prefer-matching-lore
  "Among mark-matched candidates, prefer the one whose engine key
  carries the god's full noun as the *lore* segment — that is, the
  god noun appearing immediately before the trailing variant number
  or mark suffix (e.g. `…_herald_of_nurgle_nurgle_0`,
  `…_chaos_sorcerer_lord_nurgle_mnur`).  This mirrors the canonical
  lore the engine pairs with each mark, the same choice the old
  hand-curated `display-name-unit-key-overrides` map encoded.  Plain
  `str/includes?` of `_<god>_` would match family names too (e.g.
  `_of_nurgle_death_0` contains `_nurgle_` between `of` and `death`),
  so we anchor against the trailing `_<variant-number>` or mark
  suffix to pick the lore segment specifically."
  [candidates mark]
  (if-not mark
    candidates
    (let [pattern   (re-pattern (str "_" mark "_(\\d+|m(?:kho|nur|sla|tze))"))
          preferred (filterv (fn [[uk _]] (re-find pattern uk)) candidates)]
      (if (seq preferred) preferred candidates))))

(def ^:private trailing-paren-re
  "Drops a single trailing parenthetical (e.g. ' (Fire)', ' (Tzeentch)')
  from a loc display name."
  #"\s*\([^)]*\)\s*$")

(defn- strip-trailing-paren [s]
  (when s (str/trim (str/replace s trailing-paren-re ""))))

(defn build-name-index
  "display-name → [[unit-key land-unit-key] ...]. Walks the main-unit table
  rows in their original order so that the 'first candidate' tiebreaker
  matches the RPFM table row order (same as Python's dict-insertion order).

  The returned map has its `:rpfm-scraper.name-match/stripped` meta carrying
  a parallel index keyed by the parenthetical-stripped form of each
  display name (\"Chaos Sorcerer Lord (Fire)\" → \"Chaos Sorcerer Lord\").
  `find-unit-key` consults this fallback only when the literal lookup
  is empty AND a mark hint is available, so non-mark resolutions
  retain their original (paren-distinguished) canonical pinning."
  [land-units-loc main-unit-rows]
  (let [prefix    "land_units_onscreen_name_"
        pn        (count prefix)
        lu-name   (reduce-kv
                   (fn [m k v]
                     (if (and k (str/starts-with? k prefix))
                       (assoc m (subs k pn) (normalize-name v))
                       m))
                   {}
                   land-units-loc)
        index+strip (reduce
                     (fn [{:keys [full stripped]} row]
                       (let [unit-key (get row "unit")
                             lu-key   (get row "land_unit")
                             nm       (get lu-name lu-key)
                             sn       (when nm (strip-trailing-paren nm))]
                         (cond-> {:full     full
                                  :stripped stripped}
                           nm (update :full update nm (fnil conj []) [unit-key lu-key])
                           (and sn (not= sn nm))
                           (update :stripped update sn (fnil conj []) [unit-key lu-key]))))
                     {:full {} :stripped {}}
                     main-unit-rows)]
    (with-meta (:full index+strip)
      {::stripped (:stripped index+strip)})))

(defn find-unit-key
  "Best matching [unit-key land-unit-key] for a display name + faction. Returns
  [nil nil] when no match is possible.

  When the display name encodes a Mark of Chaos suffix (\"Chaos Sorcerer
  of Tzeentch\", \"Chaos Furies (Khorne)\", \"Daemon Prince of Slaanesh\"),
  `mark-from-display-name` lifts the mark and the lookup falls through
  to the bare base name's candidates filtered by `mark-from-key` — that
  pair handles the variants mechanically and keeps the override map
  free of mark-only entries.

  When the name index still has no candidates (typically for generic
  spellcasters whose display name shares a row with multiple lore
  variants in `land_units_loc`), `overrides/display-name-unit-key-overrides`
  provides an explicit display-name → engine `unit` key mapping; the
  second tuple element is `nil` because `extract-stats` derives the
  `land_unit` key from `main_units_tables` once it has the unit key."
  [unit-name faction-prefixes name-index]
  (let [norm        (normalize-name unit-name)
        mark        (or (mark-from-display-name norm)
                        (mark-from-faction-prefixes faction-prefixes))
        stripped-ix (::stripped (meta name-index))
        ;; Composed names like "Chaos Sorcerer of Tzeentch" don't appear
        ;; in the loc-derived name index, and uniquely-marked daemons
        ;; like "Alluress" live in loc only as "Alluress (Slaanesh)" /
        ;; "Alluress (Shadows)".  Fall back through (1) the bare base
        ;; with mark suffix dropped and (2) the parenthetical-stripped
        ;; index — both paths only fire when a mark hint is available
        ;; so non-mark lookups keep their literal canonical pinning.
        candidates  (or (seq (get name-index norm))
                        (when mark
                          (or (seq (get name-index (strip-display-name-mark norm)))
                              (seq (get stripped-ix norm))
                              (seq (get stripped-ix (strip-display-name-mark norm))))))
        candidates  (vec (or candidates []))
        candidates  (-> candidates
                        (prefer-mark mark)
                        (prefer-matching-lore mark))]
    (cond
      (empty? candidates)
      (if-let [override-key (get overrides/display-name-unit-key-overrides norm)]
        [override-key nil]
        [nil nil])

      (= 1 (count candidates))
      (first candidates)

      :else
      (let [filtered (filterv
                      (fn [[uk lu]]
                        (some (fn [p]
                                (or (str/includes? uk (str "_" p "_"))
                                    (str/includes? lu (str "_" p "_"))))
                              faction-prefixes))
                      candidates)]
        (cond
          (= 1 (count filtered)) (first filtered)

          (seq filtered)
          ;; Among same-display-name candidates, prefer the canonical
          ;; original variant (oldest game-version, no DLC-pack suffix)
          ;; over later DLC-set repackagings — `wh2_dlc13_*_imperial_supply`
          ;; et al re-export an existing unit's display name with a key
          ;; that won't match a replay drafted against the base unit.
          (or (some (fn [gp]
                      (some (fn [item] (when (str/starts-with? (first item) gp) item))
                            filtered))
                    ["wh_main_" "wh3_main_" "wh2_main_" "wh_" "wh3_" "wh2_"])
              (first filtered))

          :else (first candidates))))))

;; ---------------------------------------------------------------------------
;; Icon matching (unit cards)
;; ---------------------------------------------------------------------------

(def ^:private category-re #"_(inf|cav|mon|veh|art|cha|hrd|rng|mis)_")
(def ^:private game-prefix-re #"^wh[23]?_(main|dlc\d+|pro\d+|twa\d+)_")
(def ^:private numeric-suffix-re #"_\d+$")
(def ^:private stopwords #{"of" "the" "at" "a" "an" "and" "in" "on" "for"})

(defn- strip-stopwords [k]
  (->> (str/split k #"_")
       (remove stopwords)
       (str/join "_")))

(defn- normalize-unit-key [uk]
  (-> uk
      (str/replace category-re "_")
      (str/replace numeric-suffix-re "")))

(defn- paren-variant [display-name]
  (when display-name
    (when-let [m (re-find #"\(([^)]+)\)" display-name)]
      (str/replace (str/lower-case (str/trim (second m))) #"\s+" "_"))))

(defn- match-prefix [prefix available-list display-name]
  (let [matches (filter #(str/starts-with? % prefix) available-list)]
    (cond
      (empty? matches) nil
      (and (next matches) display-name)
      (if-let [variant (paren-variant display-name)]
        (or (some (fn [m] (when (str/ends-with? m (str "_" variant)) m)) matches)
            (first matches))
        (first matches))
      :else (first matches))))

(defn find-icon
  "Best-matching icon stem for a unit-key, or nil. Tries exact/normalised/
  prefix match and stopword-stripped variants in the same order as the Python."
  [uk available available-list display-name]
  (or
   (when (contains? available uk) uk)
   (let [s1 (str/replace uk numeric-suffix-re "")]
     (when (contains? available s1) s1))
   (let [s2 (str/replace uk category-re "_")]
     (when (contains? available s2) s2))
   (let [s3 (normalize-unit-key uk)]
     (when (contains? available s3) s3))
   (let [s3 (normalize-unit-key uk)]
     (match-prefix (str s3 "_") available-list display-name))
   (let [s3 (normalize-unit-key uk)
         s4 (strip-stopwords s3)]
     (when (not= s4 s3)
       (or
        (when (contains? available s4) s4)
        (match-prefix (str s4 "_") available-list display-name)
        (some (fn [ik] (when (= (strip-stopwords ik) s4) ik)) available-list))))))

;; ---------------------------------------------------------------------------
;; Portrait matching
;; ---------------------------------------------------------------------------

(defn unit-key->portrait-base
  "Reduce a unit key to {faction}_{role} form for portrait matching."
  [uk]
  (-> uk
      (str/replace game-prefix-re "")
      (str/replace category-re "_")
      (str/replace numeric-suffix-re "")
      (str/replace #"_+" "_")
      (str/replace #"^_" "")
      (str/replace #"_$" "")))

(defn find-portrait
  "Resolve a portrait filename for a normalised unit key `base` against the
  portrait role map."
  [base role-map role-list]
  (letfn [(try-key [key]
            (or (get role-map key)
                (some (fn [r] (when (str/starts-with? r key) (get role-map r))) role-list)
                (some (fn [r] (when (str/starts-with? key r) (get role-map r))) role-list)))
          (try-with-ch [key]
            (let [parts (str/split key #"_" 2)]
              (or (when (= 2 (count parts))
                    (try-key (str (first parts) "_ch_" (second parts))))
                  (try-key key))))]
    (or
     (try-with-ch base)
     (let [stripped (strip-stopwords base)]
       (when (not= stripped base) (try-with-ch stripped)))
     (let [parts (str/split base #"_")
           n     (count parts)]
       (first
        (for [drop  (range 1 (max 1 (dec n)))
              :let  [shorter (str/join "_" (take (- n drop) parts))
                     hit     (or (try-with-ch shorter)
                                 (let [sw (strip-stopwords shorter)]
                                   (when (not= sw shorter) (try-with-ch sw))))]
              :when hit]
          hit))))))
