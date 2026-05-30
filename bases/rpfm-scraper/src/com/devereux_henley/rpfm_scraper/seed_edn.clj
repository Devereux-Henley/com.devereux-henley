(ns com.devereux-henley.rpfm-scraper.seed-edn
  "Read curated authoring EDN and write the merged Datalog seed — the
  scraper's replacement for reading back / writing SQL seed files.

  Authoring EDN (`seed/authoring/<version>/`) is the hand-maintained source
  of truth; the scraper reads identities + curated fields from it, attaches
  its RPFM-derived fields, and writes the merged result to the runtime seed
  (`seed/datalog/<version>/`). Paths are resolved relative to the repo root,
  matching the existing scraper convention. See
  docs/rpfm-scraper/edn-seed-pipeline.md."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pp]
   [clojure.string :as str])
  (:import
   [java.nio.charset StandardCharsets]
   [java.util UUID]))

(defn derived-uuid
  "Deterministic UUID (v3) over `\"/\"`-joined `parts`. The scraper's stable-eid
  scheme for generated link/statistics rows; matches the retired dump tool."
  [& parts]
  (UUID/nameUUIDFromBytes
   (.getBytes (str/join "/" (map str parts)) StandardCharsets/UTF_8)))

(def ^:private seed-root
  "components/rts-data/resources/rts-data/seed")

(defn authoring-dir
  [version]
  (io/file seed-root "authoring" version))

(defn datalog-dir
  [version]
  (io/file seed-root "datalog" version))

(def curated-files
  "Fully-curated seed files: copied verbatim from authoring into the merged
  Datalog seed (no scraper-derived fields)."
  ["games.edn" "social-media-platforms.edn" "game-social-links.edn"
   "unit-types.edn" "unit-categories.edn" "factions.edn" "game-modes.edn"
   "lores.edn" "attributes.edn" "spell-lores.edn"])

;;; ─── Read ──────────────────────────────────────────────────────────────────

(defn read-edn-file
  "Read a vector of EDN maps from `file`, or nil when it doesn't exist."
  [file]
  (when (.exists file)
    (edn/read-string (slurp file))))

(defn read-authoring
  "Read `file-name` from the authoring tree for `version`."
  [version file-name]
  (read-edn-file (io/file (authoring-dir version) file-name)))

;;; ─── Lookups (replace the regex SQL parsers) ───────────────────────────────

(defn faction-key->eid
  "`{faction-key → faction-eid}` from authoring factions. The faction key is
  the engine race slug (e.g. `empire`) subfactions resolve their parent
  against. Replaces `subfactions-seed/build-slug->faction-id`."
  [version]
  (into {}
        (keep (fn [f] (when-let [k (:faction/key f)] [k (:faction/eid f)])))
        (read-authoring version "factions.edn")))

(defn faction-eid->key
  "`{faction-eid → faction-key}` — the inverse, for joining unit rows (which
  carry a faction eid ref) back to a faction slug."
  [version]
  (into {}
        (keep (fn [f] (when-let [k (:faction/key f)] [(:faction/eid f) k])))
        (read-authoring version "factions.edn")))

(defn ability-key->eid
  "`{ability-key → ability-eid}` from authoring abilities. Replaces
  `abilities-seed/build-ability-key-eid-map`."
  [version]
  (into {} (map (juxt :ability/key :ability/eid))
        (read-authoring version "abilities.edn")))

(defn spell-key->eid
  "`{spell-key → spell-eid}` from authoring spells. Replaces
  `abilities-seed/build-spell-key-eid-map`."
  [version]
  (into {} (map (juxt :spell/key :spell/eid))
        (read-authoring version "spells.edn")))

(defn- unit-faction-eid
  "The faction eid a unit row points at (`:unit/faction [:faction/eid …]`)."
  [unit]
  (second (:unit/faction unit)))

(defn unit-name+faction->eid
  "`{[unit-name faction-key] → unit-eid}` from authoring units, joining each
  unit's faction eid back to its slug. Replaces the per-faction-file lookups
  `unit-items-seed/build-unit-seed-id-map` (name→id) and
  `assets/build-unit-name-eid-map`, now keyed to eids."
  [version]
  (let [eid->key (faction-eid->key version)]
    (into {}
          (map (fn [u] [[(:unit/name u) (eid->key (unit-faction-eid u))] (:unit/eid u)]))
          (read-authoring version "units.edn"))))

(defn- lore-suffix
  "Strip the `Lore of `/`Lore of the ` prefix and trailing ` Magic` so the
  remaining token matches the suffix carried inside unit names (e.g.
  `Lore of High Magic` → `High`). Mirrors `tables/canonical-lore-suffix`."
  [display-name]
  (-> display-name
      (str/replace #"^Lore of the " "")
      (str/replace #"^Lore of " "")
      (str/replace #" Magic$" "")))

(defn lore-suffix->key
  "`{canonical-suffix → lore-key}` from authoring lores. First occurrence wins
  on a suffix collision (authoring-file order). Replaces
  `tables/build-lore-name->key-map`."
  [version]
  (reduce (fn [m l]
            (let [k (lore-suffix (:lore/name l))]
              (if (or (str/blank? k) (contains? m k))
                m
                (assoc m k (:lore/key l)))))
          {}
          (read-authoring version "lores.edn")))

;;; ─── Merge + write ─────────────────────────────────────────────────────────

(defn merge-by-eid
  "Left-join `generated-by-eid` (a `{eid → field-map}`) onto each authoring
  row by `eid-key`. The authoring identity wins on key collisions only where
  the generated map omits the key; generated fields otherwise overwrite. Rows
  with no generated entry pass through unchanged."
  [authoring-rows generated-by-eid eid-key]
  (mapv (fn [row]
          (if-let [gen (get generated-by-eid (get row eid-key))]
            (merge row gen)
            row))
        authoring-rows))

(defn write-edn!
  "Pretty-print `rows` to `file`, creating parent dirs. Skips writing when
  `rows` is empty so the loader's absent-file handling stays intact. Returns
  the row count written (0 when skipped)."
  [file rows]
  (if (empty? rows)
    0
    (do
      (.mkdirs (.getParentFile file))
      (with-open [w (io/writer file)]
        (binding [*out* w]
          (pp/pprint rows)))
      (count rows))))

(defn write-seed-file!
  "Write `rows` to `<datalog>/<version>/<file-name>`."
  [version file-name rows]
  (write-edn! (io/file (datalog-dir version) file-name) rows))

(defn copy-curated!
  "Copy every fully-curated authoring file straight through to the merged
  Datalog seed for `version`."
  [version]
  (doseq [file-name curated-files]
    (when-let [rows (read-authoring version file-name)]
      (write-seed-file! version file-name rows))))
