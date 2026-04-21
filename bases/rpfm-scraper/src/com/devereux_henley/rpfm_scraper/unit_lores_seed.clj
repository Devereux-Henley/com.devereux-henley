(ns com.devereux-henley.rpfm-scraper.unit-lores-seed
  "Consolidation of lore-dispatch unit variants.

  Many generic lords/heroes (High Elves Archmage, Dark Elves Sorceress,
  Tomb Kings Liche Priest, etc.) ship as one row per lore of magic — same
  combat stats, different `draftable-spells`, different portrait. This
  namespace:

  1. Walks every seed-<faction>-units.sql looking for `Name (<suffix>)`
     groups that qualify as lore-dispatch (size ≥ 2; each variant has a
     non-empty, distinct `draftable-spells`; every suffix resolves to a
     row in the `lore` table).
  2. Rewrites the file in-place, keeping the first variant as the
     survivor (stripping the suffix from its name, clearing its stored
     `draftable-spells`) and deleting the sibling tuples.
  3. Emits seed-unit-lores.sql with one row per original variant so the
     per-(unit, lore) spell pool + portrait alias lives on the new
     unit_lore table."
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def ^:private seed-author "f0ce7395-a57f-41e9-ade0-fd13bafc058f")

(defn- log [& args]
  (binding [*out* *err*] (apply println args)))

(defn- sql-escape [s]
  (str/replace (or s "") "'" "''"))

;; ─── Tuple parsing ────────────────────────────────────────────────────────────

(def ^:private tuple-start-re
  "Matches the very start of each unit INSERT tuple — `  (<id>,` at a line
  start. Used only to locate tuple boundaries; the per-tuple fields are
  extracted with a simpler regex on the resulting substring."
  #"(?m)^  \((\d+),")

(def ^:private tuple-field-re
  "Extracts just the fields we care about from a single tuple substring.
  1=eid-stem, 2=eid-tail, 3=name (SQL-escaped, may include `''`),
  4=stats-json (SQL-escaped).

  The `[^']*(?:''[^']*)*` unrolled-loop form avoids catastrophic
  backtracking that trips the simpler `(?:[^']|'')*` pattern on blobs
  with many doubled-quotes (e.g. the elaborate ability names in the
  High Elves seed)."
  #"(?s)'([0-9a-f]+)(-[0-9a-f\-]+)',\s*'([^']*(?:''[^']*)*)',\s*'[^']*(?:''[^']*)*',\s*\d+,\s*\d+,\s*\d+,\s*\d+,\s*'(\{[^']*(?:''[^']*)*\})'")

(defn- tuple-spans
  "Returns a vector of [start end) pairs — one per tuple boundary in
  `content`. `end` is the start of the next tuple (or file length for the
  last tuple)."
  [content]
  (let [matcher (re-matcher tuple-start-re content)
        starts  (loop [acc []]
                  (if (.find matcher)
                    (recur (conj acc (.start matcher)))
                    acc))
        ends    (concat (rest starts) [(count content)])]
    (mapv vector starts ends)))

(defn- parse-tuples
  "Returns a vector of maps, one per unit tuple in `content`:
    {:start :end :id :eid :name :raw-name :stats :raw-json}
  `:eid` is the full UUID (matching the PNG filename). `:end` is the
  offset just after this tuple (start of the next, or EOF). `:raw-name`
  and `:raw-json` preserve the SQL-escaped strings exactly as they appear
  on disk so in-place replacement is byte-exact."
  [content]
  (into []
        (keep
         (fn [[start end]]
           (let [slice (subs content start end)
                 id-m  (re-find tuple-start-re slice)]
             (when-let [field-m (re-find tuple-field-re slice)]
               (let [[_ stem tail raw-name raw-json] field-m
                     id                              (Long/parseLong (nth id-m 1))
                     name                            (str/replace raw-name "''" "'")
                     json                            (str/replace raw-json "''" "'")
                     stats                           (try (json/read-str json) (catch Exception _ nil))]
                 {:start    start
                  :end      end
                  :id       id
                  :eid      (str stem tail)
                  :name     name
                  :raw-name raw-name
                  :stats    stats
                  :raw-json raw-json})))))
        (tuple-spans content)))

;; ─── Classification ───────────────────────────────────────────────────────────

(def ^:private suffix-re #"^(.+?) \(([A-Za-z ]+)\)$")

(defn- base+suffix
  "Splits 'Archmage (High)' → ['Archmage' 'High']. Returns nil for names
  without a trailing ` (<Token>)` suffix."
  [name]
  (when-let [[_ base suffix] (re-matches suffix-re name)]
    [base suffix]))

(defn- spell-keys
  [stats]
  (mapv #(get % "key") (get stats "draftable-spells" [])))

(defn- dispatch-group?
  "A group of tuples sharing a base name qualifies as lore-dispatch when:
    - size ≥ 2
    - every variant has a non-empty draftable-spells list
    - the spell lists differ between variants
    - every suffix resolves to an id in lore-suffix->id"
  [variants lore-suffix->id]
  (and (>= (count variants) 2)
       (every? (fn [v] (seq (:spell-keys v))) variants)
       (> (count (set (map :spell-keys variants))) 1)
       (every? (fn [v] (contains? lore-suffix->id (:suffix v))) variants)))

(defn- group-tuples
  "Returns [dispatch-groups non-dispatch-tuples] where dispatch-groups is
  a seq of {:base :variants [...]} for groups that qualify, and
  non-dispatch-tuples contains every tuple not belonging to such a group
  (singletons, non-lore suffixed, and mixed groups)."
  [tuples lore-suffix->id]
  (let [decorated          (keep (fn [t]
                                   (when-let [[base suffix] (base+suffix (:name t))]
                                     (assoc t :base base :suffix suffix :spell-keys (spell-keys (:stats t)))))
                                 tuples)
        by-base            (group-by :base decorated)
        dispatch           (keep (fn [[base variants]]
                                   (when (dispatch-group? variants lore-suffix->id)
                                     {:base base :variants (vec variants)}))
                                 by-base)
        dispatch-tuple-ids (into #{} (mapcat (fn [g] (map :id (:variants g)))) dispatch)
        non-dispatch       (remove (fn [t] (contains? dispatch-tuple-ids (:id t))) tuples)]
    [dispatch non-dispatch]))

;; ─── Rewrite helpers ──────────────────────────────────────────────────────────

(defn- rewrite-survivor-tuple
  "Returns the original tuple text with the unit name replaced and the
  draftable-spells array in the stats JSON emptied. Uses the tuple's
  captured raw strings for byte-exact anchors."
  [orig-tuple {:keys [raw-name raw-json stats]} new-name]
  (let [stage-1     (str/replace-first orig-tuple
                                       (str "'" raw-name "'")
                                       (str "'" (sql-escape new-name) "'"))
        cleared     (assoc (or stats {}) "draftable-spells" [])
        cleared-raw (-> (json/write-str cleared
                                        :escape-slash false
                                        :escape-js-separators false)
                        (str/replace "'" "''"))]
    (str/replace-first stage-1 (str "'" raw-json "'") (str "'" cleared-raw "'"))))

(defn- splice-file
  "Produces the rewritten file content: survivor tuples are renamed +
  stats-cleared in place; sibling tuples are dropped wholesale."
  [content dispatch-groups]
  (let [survivor-info (into {} (map (fn [g]
                                      [(:id (first (:variants g)))
                                       {:new-name (:base g)
                                        :tuple    (first (:variants g))}]))
                            dispatch-groups)
        drop-ids      (into #{} (mapcat (fn [g] (map :id (rest (:variants g)))))
                            dispatch-groups)
        ;; Apply edits in reverse offset order so earlier offsets stay valid.
        content'      (reduce
                       (fn [acc t]
                         (cond
                           (contains? drop-ids (:id t))
                           (str (subs acc 0 (:start t)) (subs acc (:end t)))

                           (contains? survivor-info (:id t))
                           (let [{:keys [new-name tuple]} (get survivor-info (:id t))
                                 orig-tuple               (subs acc (:start t) (:end t))
                                 new-tuple                (rewrite-survivor-tuple orig-tuple tuple new-name)]
                             (str (subs acc 0 (:start t)) new-tuple (subs acc (:end t))))

                           :else acc))
                       content
                       (sort-by :start > (parse-tuples content)))]
    content'))

;; ─── Public API ───────────────────────────────────────────────────────────────

(defn consolidate-file
  "Reads one seed-<faction>-units.sql at `path`, consolidates lore-dispatch
  groups, and returns a map:
    {:content  rewritten-content
     :groups   [{:base str
                 :survivor-id int
                 :variants [{:id :eid-stem :suffix :spell-keys}]}]}
  The caller is responsible for writing :content back and aggregating
  :groups across files for seed-unit-lores.sql emission."
  [path lore-suffix->id]
  (let [content      (slurp path)
        tuples       (parse-tuples content)
        [dispatch _] (group-tuples tuples lore-suffix->id)
        rewritten    (splice-file content dispatch)
        records      (mapv (fn [g]
                             {:base        (:base g)
                              :survivor-id (:id (first (:variants g)))
                              :variants    (mapv (fn [v]
                                                   {:id         (:id v)
                                                    :eid        (:eid v)
                                                    :suffix     (:suffix v)
                                                    :spell-keys (:spell-keys v)})
                                                 (:variants g))})
                           dispatch)]
    {:content rewritten :groups records}))

(defn consolidate-lore-variants!
  "Walks every seed-<faction>-units.sql under `seed-dir`, rewrites it in
  place with lore dispatch groups collapsed, and returns the aggregated
  records for emission into seed-unit-lores.sql. When `dry-run?` is true
  the file system is not touched but the records are still returned."
  [seed-dir lore-suffix->id dry-run?]
  (let [files        (->> (.list (io/file seed-dir))
                          (filter #(and (str/starts-with? % "seed-")
                                        (str/ends-with? % "-units.sql")))
                          sort)
        total-groups (atom 0)
        total-rows   (atom 0)
        records
        (reduce
         (fn [acc filename]
           (let [path                     (str (io/file seed-dir filename))
                 {:keys [content groups]} (consolidate-file path lore-suffix->id)]
             (when (seq groups)
               (swap! total-groups + (count groups))
               (swap! total-rows + (reduce + (map #(count (:variants %)) groups)))
               (when-not dry-run?
                 (spit path content)))
             (into acc (map #(assoc % :faction-file filename) groups))))
         []
         files)]
    (log (format "  [unit-lores] consolidated %d dispatch groups (%d variants) across %d faction files"
                 @total-groups @total-rows (count files)))
    records))

(defn generate-unit-lore-seed
  "Builds seed-unit-lores.sql content from the aggregated consolidation
  records + lore-suffix→lore-id map. Each group contributes one row per
  (unit, lore-variant) pair — carrying the variant's eid-stem as
  portrait_key. The spell list for a lore is invariant across units and
  lives on the spell_lore junction; we don't duplicate it here."
  [records lore-suffix->id]
  (let [rows   (for [r     records
                     v     (:variants r)
                     :let  [lore-id (get lore-suffix->id (:suffix v))]
                     :when lore-id]
                 {:unit-id      (:survivor-id r)
                  :lore-id      lore-id
                  :portrait-key (:eid v)})
        sorted (sort-by (juxt :unit-id :lore-id) rows)
        n      (count sorted)]
    (if (zero? n)
      "-- no unit_lore rows\n"
      (let [header  ["INSERT OR IGNORE INTO unit_lore(id, unit_id, lore_id, cost, portrait_key, version, created_by_sub, created_at, updated_at, deleted_at)"
                     "VALUES"]
            indexed (map-indexed (fn [i r] [(inc i) r]) sorted)
            row-sql (fn [[idx {:keys [unit-id lore-id portrait-key]}]]
                      (let [comma    (if (< idx n) "," ";")
                            portrait (if portrait-key
                                       (format "'%s'" (sql-escape portrait-key))
                                       "null")]
                        (format "  (%d, %d, %d, 0, %s, 1, '%s', STRFTIME('%%Y-%%m-%%dT%%H:%%M:%%fZ','now'), STRFTIME('%%Y-%%m-%%dT%%H:%%M:%%fZ','now'), null)%s"
                                idx unit-id lore-id portrait seed-author comma)))
            body    (map row-sql indexed)]
        (log (format "  [unit-lores] emitted %d unit_lore rows" n))
        (str (str/join "\n" (concat header body)) "\n")))))
