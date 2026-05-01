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

(def ^:private name-default-lore
  "Display names that imply a canonical lore.  Mark-eligible daemons
  carry their mark as the lore (\"Alluress\" \u2192 'slaanesh', etc.);
  generic spellcasters whose name is shared across multiple lore
  variants in `land_units_loc` carry the lore the engine pairs with
  the archetype by convention (\"Mage\" \u2192 'light', \"Sorceress\" \u2192
  'dark').  Keeps the per-name list explicit (and tiny) instead of
  growing a per-(name \u00d7 variant) override entry."
  {;; Mark-eligible daemons (mark = lore)
   "Alluress"                      "slaanesh"
   "Bloodspeaker"                  "khorne"
   "Bloodreaper"                   "khorne"
   "Bringer of Pain"               "slaanesh"
   "Cultist of Khorne"             "khorne"
   "Cultist of Nurgle"             "nurgle"
   "Cultist of Slaanesh"           "slaanesh"
   "Cultist of Tzeentch"           "tzeentch"
   "Exalted Bloodthirster"         "khorne"
   "Exalted Great Unclean One"     "nurgle"
   "Exalted Keeper of Secrets"     "slaanesh"
   "Exalted Lord of Change"        "tzeentch"
   "Iridescent Horror"             "tzeentch"
   "Plagueridden"                  "nurgle"
   ;; Generic spellcaster archetypes \u2014 canonical lore shared with
   ;; the seed's existing pinning
   "Archmage"                      "light"
   "Bray-Shaman"                   "beasts"
   "Damsel"                        "damsel"
   "Daemonsmith Sorcerer"          "fire"
   "Frost Maiden"                  "ice"
   "Great Bray-Shaman"             "beasts"
   "Great Shaman-Sorcerer"         "death"
   "Grey Seer"                     "plague"
   "Ice Witch"                     "ice"
   "Liche Priest"                  "death"
   "Mage"                          "light"
   "Prophetess"                    "prophetess"
   "Shaman-Sorcerer"               "death"
   "Skink Priest"                  "beasts"
   "Sorcerer-Prophet"              "fire"
   "Sorceress"                     "dark"
   "Spellsinger"                   "beasts"
   "Spellweaver"                   "life"
   "Supreme Sorceress"             "dark"
   ;; Faction-unique non-spellcaster names that still need a
   ;; multi-variant tiebreaker
   "Butcher"                       "great_maw"
   "Slaughtermaster"               "great_maw"
   "Dragon-blooded Shugengan Lord" "yang"
   "Fimir Balefiend"               "fire"
   "Malevolent Ancient Treeman"    "life"
   "Malevolent Branchwraith"       "life"
   "Vampire"                       "vampire"
   "Vampire Fleet Captain"         "vampire_fleet_captain"})

;; Backwards-compatible alias: `mark-from-key`'s callers still expect a
;; mark-only result, but the lore preference machinery accepts any
;; lore string.  Keeping the names distinct in tests + comments.
(def ^:private name-implied-mark
  "Subset of `name-default-lore` whose value is one of the four Marks
  of Chaos.  Used by `mark-from-display-name` so the mark column on
  a unit row stays Khorne/Nurgle/Slaanesh/Tzeentch even though the
  full lore preference dictionary covers more archetypes."
  (into {} (filter (comp #{"khorne" "nurgle" "slaanesh" "tzeentch"} val))
        name-default-lore))

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

(def ^:private display-name-lore-paren-re
  "Captures any trailing parenthetical (e.g. '(Beasts)', '(Death)',
  '(Hellscourges)') in seed display names.  Matches more loosely than
  `display-name-mark-re` since we want the lore-preference path to
  cover Light/Dark/Fire/etc. too, not just Marks of Chaos."
  #"\s*\(\s*([^)]+?)\s*\)\s*$")

(defn lore-from-display-name
  "Returns the canonical lore implied by a display name, or nil.
  Priority: the four Marks of Chaos via `mark-from-display-name` \u2192 a
  trailing `(<Lore>)` parenthetical \u2192 entry in `name-default-lore`.
  Lowercased and snake-cased so the result matches engine key segments
  (`'beasts'`, `'great_maw'`, `'fire'` \u2026)."
  [name]
  (when name
    (or (mark-from-display-name name)
        (when-let [m (re-find display-name-lore-paren-re name)]
          (-> (second m) str/lower-case (str/replace #"\s+" "_")))
        (get name-default-lore name))))

(defn strip-display-name-lore
  "Drops the trailing mark suffix or parenthetical lore from a display
  name ('Slann Mage-Priest (Beasts)' \u2192 'Slann Mage-Priest', 'Daemon
  Prince of Khorne' \u2192 'Daemon Prince').  Used by `find-unit-key`
  when looking up the base name in the parenthetical-stripped
  secondary index."
  [name]
  (when name
    (-> name
        (str/replace display-name-mark-re "")
        (str/replace display-name-lore-paren-re "")
        str/trim)))

(def ^:private faction-default-lore
  "Faction prefix \u2192 default lore for archetypes whose loc display
  name is shared across multiple lore variants and whose seed name
  doesn't itself carry a lore disambiguator.  Captures the
  per-faction convention the engine pairs with each archetype.  Only
  triggers via `lore-from-faction-prefixes` after the per-name
  dictionary fails, so faction defaults never override a more
  specific name-level preference."
  {"hef" "light"
   "def" "dark"
   "tmb" "death"
   "skv" "plague"
   "ogr" "great_maw"
   "chd" "fire"
   "nor" "death"
   "cth" "yang"
   "ksl" "ice"
   "bst" "beasts"
   "lzd" "beasts"})

(defn- lore-from-faction-prefixes
  "Maps a seed's faction prefix(es) to the faction's default lore via
  `faction-default-lore`.  Returns nil when no prefix has a registered
  default (so unmarked, non-spellcaster units keep their literal
  lookup behaviour)."
  [faction-prefixes]
  (some faction-default-lore faction-prefixes))

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

(defn- prefer-lore
  "Among `candidates`, prefer the one whose engine key carries the
  given lore as the trailing-or-near-trailing key segment.  Pattern:
  `_<lore>` optionally followed by exactly one more non-underscore
  segment, then end-of-string.  Anchors against:

    `_<lore>_<digit>`   (e.g. `…_lord_death_0`)
    `_<lore>_m<short>`  (e.g. `…_lord_nurgle_mnur`)
    `_<lore>_<word>`    (e.g. `…_lord_death_warshrine`)
    `_<lore>$`          (e.g. `…_supreme_sorceress_dark`)

  Non-tail occurrences (e.g. `_of_nurgle_death_0` for lore='nurgle')
  don't satisfy the trailing anchor so the family name doesn't cause
  a false-positive lore match.  Generalises the original
  `prefer-matching-lore` from marks to any lore name (Light, Dark,
  Fire, Beasts, Great_Maw, …) so non-mark spellcaster archetypes can
  flow through the same machinery."
  [candidates lore]
  (if-not lore
    candidates
    (let [pattern   (re-pattern (str "_" lore "(?:_[a-z0-9]+)?$"))
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
  (let [prefix      "land_units_onscreen_name_"
        pn          (count prefix)
        lu-name     (reduce-kv
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
  (let [norm          (normalize-name unit-name)
        mark          (or (mark-from-display-name norm)
                          (mark-from-faction-prefixes faction-prefixes))
        ;; Lore preference broader than mark: a Mark of Chaos counts
        ;; as a lore (so Khorne-marked daemons still flow through
        ;; here) but `lore-from-display-name` also returns Light/Dark/
        ;; Fire/Beasts/etc. for non-mark spellcaster archetypes
        ;; ("Mage" → 'light', "Slann Mage-Priest (Beasts)" → 'beasts').
        ;; Faction defaults are the last resort — they fire only for
        ;; names with no per-name preference and no parenthetical, so
        ;; they don't overrule explicit lore signals.
        lore          (or (lore-from-display-name norm)
                          (lore-from-faction-prefixes faction-prefixes))
        stripped-ix   (::stripped (meta name-index))
        base-name     (strip-display-name-lore norm)
        direct        (seq (get name-index norm))
        override-key  (when-not direct (get overrides/display-name-unit-key-overrides norm))
        ;; Composed names like "Chaos Sorcerer of Tzeentch" don't
        ;; appear in the loc-derived name index, and uniquely-marked
        ;; daemons like "Alluress" live in loc only as "Alluress
        ;; (Slaanesh)" / "Alluress (Shadows)".  Fall back through
        ;; (1) the bare base with lore suffix dropped against the
        ;; full index, then (2) literal name in the
        ;; parenthetical-stripped secondary index, then (3) bare
        ;; base in the stripped index.  All fallbacks fire only
        ;; when a lore hint is available so non-spellcaster lookups
        ;; keep their literal canonical pinning.  An explicit
        ;; override beats the mechanical fallback so seed entries
        ;; whose loc display name is a `{{tr:…}}` placeholder
        ;; (Slann Mage-Priest (Beasts/Death/Metal/Shadows), …) keep
        ;; their hand-pinned canonical instead of collapsing to a
        ;; loose `_campaign_0` resolution.
        candidates    (or direct
                          (when (and (not override-key) lore)
                            (or (seq (get name-index base-name))
                                (seq (get stripped-ix norm))
                                (seq (get stripped-ix base-name)))))
        candidates    (vec (or candidates []))
        ;; Mark filter runs first because it's the strongest discriminator
        ;; (a row's god alignment is rarely ambiguous given its key).  The
        ;; faction filter follows so per-faction variants (chs vs kho for
        ;; "Daemon Prince of Khorne") are picked correctly.  Lore is the
        ;; softest tiebreaker — applied last so it doesn't fire over a
        ;; faction-matched candidate.
        mark-filtered (prefer-mark candidates mark)
        canonical     (fn [pool]
                        (some (fn [gp]
                                (some (fn [item] (when (str/starts-with? (first item) gp) item))
                                      pool))
                              ["wh_main_" "wh3_main_" "wh2_main_" "wh_" "wh3_" "wh2_"]))
        faction-of    (fn [pool]
                        (filterv
                         (fn [[uk lu]]
                           (some (fn [p]
                                   (or (str/includes? uk (str "_" p "_"))
                                       (str/includes? lu (str "_" p "_"))))
                                 faction-prefixes))
                         pool))]
    (cond
      override-key
      [override-key nil]

      (empty? candidates)
      [nil nil]

      (= 1 (count mark-filtered))
      (first mark-filtered)

      :else
      (let [faction-filtered (faction-of mark-filtered)
            ;; Faction filter eliminating everything is common for shared
            ;; daemons where the seed processes them under DoC/WoC but
            ;; the engine keys carry mono-god prefixes — fall back to
            ;; the mark-filtered set so the canonical-prefix tiebreaker
            ;; picks the mono-god `wh3_main_*` over a DLC-pack ROR.
            pool             (if (seq faction-filtered) faction-filtered mark-filtered)
            ;; Lore is a soft preference: narrow the pool only when at
            ;; least one candidate matches, otherwise leave the pool
            ;; intact for the canonical-prefix fallback.
            lore-filtered    (prefer-lore pool lore)]
        (cond
          (= 1 (count lore-filtered)) (first lore-filtered)
          :else (or (canonical lore-filtered) (first lore-filtered)))))))

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
