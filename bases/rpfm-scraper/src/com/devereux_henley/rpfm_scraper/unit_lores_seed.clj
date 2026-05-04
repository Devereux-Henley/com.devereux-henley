(ns com.devereux-henley.rpfm-scraper.unit-lores-seed
  "Pins `unit.lore` and refines `unit.family_name` for every variant
  unit row whose engine name carries a trailing `(<Suffix>)` resolving
  to a lore in the catalogue.  The emitted `seed-unit-lores.sql` is a
  single UPDATE that sets both columns via CASE eid expressions (same
  shape as `seed-unit-marks.sql`), so non-spellcaster rows pass
  through untouched.

  Why family_name lives partly here: `seed-unit-marks.sql` already
  strips mark suffixes from name into family_name; lore-suffixed
  variants need an additional strip.  Folding both strips into the
  marks seed via chained REPLACE blows SQLite's parser depth limit
  (the catalogue has ~80 lore suffixes), so we precompute the final
  family_name in Clojure here and emit per-eid overrides.

  No consolidation: each (mark, lore) pair lives as its own unit row
  sharing `family_name` with its siblings, exactly the way marks
  already do.  The draft panel's family selector toggles between
  variants by swapping `unit-eid`."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def ^:private suffix-re #"^(.+?) \(([A-Za-z ]+)\)$")

(defn- name+suffix
  "Splits 'Archmage (High)' → ['Archmage' 'High'].  Returns nil when
  the trailing token isn't a `(<Word>)` suffix."
  [name]
  (when-let [[_ base suffix] (re-matches suffix-re name)]
    [base suffix]))

(def ^:private tuple-start-re #"(?m)^  \((\d+),")

(def ^:private tuple-eid-name-re
  "Captures eid + raw-name from a single tuple slice.  The unrolled-loop
  form on `name` avoids catastrophic backtracking on stat blobs that
  contain many doubled-quotes."
  #"(?s)\(\s*\d+\s*,\s*'([0-9a-f\-]+)',\s*'([^']*(?:''[^']*)*)'")

(defn- tuple-spans
  [content]
  (let [matcher (re-matcher tuple-start-re content)
        starts  (loop [acc []]
                  (if (.find matcher)
                    (recur (conj acc (.start matcher)))
                    acc))
        ends    (concat (rest starts) [(count content)])]
    (mapv vector starts ends)))

(defn- parse-tuples
  "Returns a vector of {:eid :name} for every unit tuple in `content`.
  Names are unescaped (`''` → `'`)."
  [content]
  (into []
        (keep
         (fn [[start end]]
           (let [slice (subs content start end)]
             (when-let [m (re-find tuple-eid-name-re slice)]
               {:eid  (nth m 1)
                :name (str/replace (nth m 2) "''" "'")}))))
        (tuple-spans content)))

(def ^:private mark-name-patterns
  "Substrings whose presence in a unit's engine name implies a mark
  assignment.  Mirrors `marks-seed/mark-strips`'s shape but maps each
  pattern to its mark so the lore step can also infer mark for
  newly-cloned variants whose engine `key` isn't in seed-unit-keys.sql
  yet (e.g. lore-paren clones produced by the one-shot
  un-consolidation).  Order matters only insofar as we strip them all
  unconditionally — detection just checks containment."
  {" of Khorne"   "khorne"
   " of Nurgle"   "nurgle"
   " of Slaanesh" "slaanesh"
   " of Tzeentch" "tzeentch"
   " (Khorne)"    "khorne"
   " (Nurgle)"    "nurgle"
   " (Slaanesh)"  "slaanesh"
   " (Tzeentch)"  "tzeentch"})

(defn- strip-mark-suffix
  [name]
  (reduce (fn [n s] (str/replace n s "")) name (keys mark-name-patterns)))

(defn- infer-mark
  "Returns the first mark whose suffix appears in `name`, or nil when
  the name carries no mark token.  Used as a fallback for variant rows
  that haven't been linked to an engine `key` yet."
  [name]
  (some (fn [[s m]] (when (str/includes? name s) m)) mark-name-patterns))

(def ^:private extra-lore-pins
  "Hand-curated lore assignments for unit rows whose engine name
  doesn't carry a `(<Suffix>)` token — currently the unmarked Daemon
  Prince variants in Daemons of Chaos and Warriors of Chaos, both
  pinned to the Lore of Fire pool.  Modeled as overrides because
  their family identity ('Daemon Prince' shared with the four marked
  variants) precludes a renaming-style fix."
  [{:eid         "00050004-0000-4000-8000-000000000000"
    :lore-key    "wh_main_lore_fire"
    :family-name "Daemon Prince"}
   {:eid         "00170013-0000-4000-8000-000000000000"
    :lore-key    "wh_main_lore_fire"
    :family-name "Daemon Prince"}])

(defn collect-lore-variants
  "Walks every seed-<faction>-units.sql under `seed-dir` and returns
  `[{:eid :lore-key :family-name} …]` for each tuple whose name suffix
  resolves to a lore, plus a small set of hand-curated extras for
  unmarked-but-lored rows that don't follow the suffix pattern (see
  `extra-lore-pins`).  `:family-name` is the unit's name with both
  mark and lore suffixes stripped (e.g.
  'Chaos Sorcerer Lord of Nurgle (Death)' → 'Chaos Sorcerer Lord').
  Tuples whose suffix isn't a known lore (e.g. mark suffixes like
  ` (Khorne)`) are silently skipped — the marks seed handles them."
  [seed-dir lore-suffix->key]
  (let [files         (->> (.list (io/file seed-dir))
                           (filter #(and (str/starts-with? % "seed-")
                                         (str/ends-with? % "-units.sql")))
                           sort)
        suffixed      (into []
                            (mapcat
                             (fn [filename]
                               (let [content (slurp (str (io/file seed-dir filename)))]
                                 (keep
                                  (fn [{:keys [eid name]}]
                                    (when-let [[base suffix] (name+suffix name)]
                                      (when-let [lore-key (get lore-suffix->key suffix)]
                                        {:eid         eid
                                         :lore-key    lore-key
                                         :mark        (infer-mark base)
                                         :family-name (strip-mark-suffix base)})))
                                  (parse-tuples content)))))
                            files)
        suffixed-eids (into #{} (map :eid) suffixed)
        extras        (remove #(contains? suffixed-eids (:eid %)) extra-lore-pins)]
    (into suffixed extras)))

(def ^:private no-rows-sentinel
  "-- no lore assignments in this scrape (preserved on subsequent empty runs)\n")

(defn- format-seed
  [variants]
  (if (empty? variants)
    no-rows-sentinel
    (let [sorted (->> variants
                      (filter :lore-key)
                      (sort-by :eid))]
      (str
       "-- Generated by rpfm-scraper.  Do not hand-edit — re-run the\n"
       "-- scraper.  Pins `unit.lore` and overrides `unit.family_name`\n"
       "-- for every variant unit row whose engine name carries a\n"
       "-- trailing `(<Suffix>)` resolving to a lore in the catalogue.\n"
       "-- The mark suffix has already been stripped from family_name\n"
       "-- by seed-unit-marks.sql; this step strips the lore paren on\n"
       "-- top so all (mark, lore) variants of a wizard collapse to a\n"
       "-- shared family identifier.  Single combined UPDATE because\n"
       "-- next.jdbc.execute! only consumes the first statement of a\n"
       "-- multi-statement string.  Runs after the per-faction unit\n"
       "-- seeds + seed-unit-marks.sql populate the rows.\n"
       "UPDATE unit\n"
       "SET lore = CASE eid\n"
       (apply str
              (for [{:keys [eid lore-key]} sorted]
                (format "  WHEN '%s' THEN '%s'\n"
                        eid
                        (str/replace lore-key "'" "''"))))
       "  ELSE lore\n"
       "END,\n"
       "mark = CASE eid\n"
       (apply str
              (for [{:keys [eid mark]} sorted
                    :when              mark]
                (format "  WHEN '%s' THEN '%s'\n" eid mark)))
       "  ELSE mark\n"
       "END,\n"
       "family_name = CASE eid\n"
       (apply str
              (for [{:keys [eid family-name]} sorted]
                (format "  WHEN '%s' THEN '%s'\n"
                        eid
                        (str/replace family-name "'" "''"))))
       "  ELSE family_name\n"
       "END;\n"))))

(defn generate-unit-lore-seed
  "Returns `{:content :rows :empty?}` for `seed-unit-lores.sql` so the
  standard write helper can persist it."
  [variants]
  (let [content (format-seed variants)]
    {:content content
     :rows    variants
     :empty?  (empty? variants)}))
