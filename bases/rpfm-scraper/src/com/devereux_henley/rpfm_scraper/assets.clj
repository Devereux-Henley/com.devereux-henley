(ns com.devereux-henley.rpfm-scraper.assets
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.string :as str]
   [com.devereux-henley.rpfm-scraper.name-match :as nm]
   [com.devereux-henley.rpfm-scraper.overrides :as overrides])
  (:import
   (java.nio.file Files StandardCopyOption)))

(def ^:private unit-seed-row-re
  #"\(\s*\d+,\s*'([0-9a-f\-]+)',\s*'((?:[^']|'')*)',")

(defn- log [& args]
  (binding [*out* *err*] (apply println args)))

(defn- list-pngs [dir]
  (->> (.list (io/file dir))
       (filter #(str/ends-with? % ".png"))
       sort))

(defn- png-stems [dir]
  (->> (list-pngs dir)
       (map #(subs % 0 (- (count %) 4)))
       set))

(defn- copy-file! [src dest]
  (Files/copy (.toPath (io/file src))
              (.toPath (io/file dest))
              (into-array java.nio.file.CopyOption
                          [StandardCopyOption/REPLACE_EXISTING
                           StandardCopyOption/COPY_ATTRIBUTES])))

(defn- mogrify-trim! [path]
  (let [{:keys [exit err]}
        (shell/sh "mogrify" "-fuzz" "20%" "-trim" "+repage" path)]
    (when-not (zero? exit)
      (throw (ex-info (str "mogrify failed: " err) {:path path})))))

(defn- copy-and-trim! [src dest dry-run?]
  (when-not dry-run?
    (copy-file! src dest)
    (mogrify-trim! dest)))

(defn build-unit-name-eid-map
  "Walk every seed-<faction>-units.sql file and return a vector of
  [display-name eid faction-slug] triples. A vector (not a map) so that units
  sharing a display name across factions are all processed."
  [seed-dir]
  (let [files (->> (.list (io/file seed-dir))
                   (filter #(and (str/starts-with? % "seed-")
                                 (str/ends-with? % "-units.sql")))
                   sort)]
    (into []
          (mapcat
           (fn [filename]
             (let [faction (subs filename (count "seed-")
                                 (- (count filename) (count "-units.sql")))
                   content (slurp (io/file seed-dir filename))]
               (for [m (re-seq unit-seed-row-re content)]
                 [(str/replace (nth m 2) "''" "'") (nth m 1) faction]))))
          files)))

(defn- filter-by-faction [candidates faction]
  (if-not faction
    candidates
    (let [prefixes (get overrides/faction-key-map faction [])
          filtered (filter (fn [[uk _]]
                             (some #(str/includes? uk (str "_" % "_")) prefixes))
                           candidates)]
      (if (seq filtered) filtered candidates))))

(defn- apply-override! [name eid cards-dir portraits-dir asset-dir dry-run?]
  (when-let [key (get overrides/unit-card-overrides name)]
    (let [candidates (remove nil? [cards-dir portraits-dir])]
      (loop [dirs candidates]
        (if-let [d (first dirs)]
          (let [src (io/file d (str key ".png"))]
            (if (.exists src)
              (do (copy-and-trim! src (io/file asset-dir (str eid ".png")) dry-run?)
                  :copied)
              (recur (next dirs))))
          (do (log (format "  [override] WARNING: override key '%s' not found for '%s'"
                           key name))
              :override-missing))))))

(defn copy-unit-cards
  "For each (name eid faction) in unit-name-eid-pairs, find the best-matching
  icon in cards-dir and copy to asset-dir/<eid>.png, then trim. Overrides
  apply first; faction filter disambiguates same-named units."
  [cards-dir asset-dir name-index unit-name-eid-pairs portraits-dir dry-run?]
  (let [available      (png-stems cards-dir)
        available-list (sort available)
        copied      (atom 0)
        missing-key (atom [])
        missing-src (atom [])]
    (doseq [[name eid faction] unit-name-eid-pairs]
      (if (apply-override! name eid cards-dir portraits-dir asset-dir dry-run?)
        (swap! copied inc)
        (let [all-candidates (get name-index (nm/normalize-name name) [])]
          (if (empty? all-candidates)
            (swap! missing-key conj name)
            (let [candidates (filter-by-faction all-candidates faction)
                  unit-key   (some (fn [[uk _]]
                                     (nm/find-icon uk available available-list name))
                                   candidates)]
              (if unit-key
                (do (copy-and-trim! (io/file cards-dir (str unit-key ".png"))
                                    (io/file asset-dir (str eid ".png"))
                                    dry-run?)
                    (swap! copied inc))
                (swap! missing-src conj name)))))))
    (log (format "  [unit cards] copied+trimmed %d cards" @copied))
    (when (seq @missing-key)
      (log (format "  [unit cards] %d units not in name index (e.g. %s)"
                   (count @missing-key) (pr-str (take 3 @missing-key)))))
    (when (seq @missing-src)
      (log (format "  [unit cards] %d units with no matching icon (e.g. %s)"
                   (count @missing-src) (pr-str (take 3 @missing-src)))))))

(defn- build-portrait-role-map
  "Scan portraits-dir for base PNGs, returning {role-key best-filename}
  preferring campaign_01 over higher-numbered variants."
  [portraits-dir]
  (reduce
   (fn [m fname]
     (let [stem (subs fname 0 (- (count fname) 4))]
       (if-let [[_ base quality] (re-matches #"^(.+)_(\d+)$" stem)]
         (if (zero? (Long/parseLong quality))
           (let [role (str/replace base #"_campaign_\d+$" "")]
             (cond
               (not (contains? m role))  (assoc m role fname)
               (and (str/includes? base "_campaign_01")
                    (not (str/includes? (get m role) "_campaign_01")))
               (assoc m role fname)
               :else m))
           m)
         m)))
   {}
   (->> (list-pngs portraits-dir)
        (remove #(str/includes? % "_mask")))))

(defn copy-unit-portraits
  "For each unit without an existing card, find a matching portrait and
  copy it. Overrides apply first; faction filter disambiguates."
  [portraits-dir asset-dir name-index unit-name-eid-pairs cards-dir dry-run?]
  (let [role-map  (build-portrait-role-map portraits-dir)
        role-list (sort (keys role-map))
        existing  (png-stems asset-dir)
        copied      (atom 0)
        missing-key (atom [])
        no-portrait (atom [])]
    (doseq [[name eid faction] unit-name-eid-pairs
            :when (not (contains? existing eid))]
      (if (apply-override! name eid cards-dir portraits-dir asset-dir dry-run?)
        (swap! copied inc)
        (let [all-candidates (get name-index (nm/normalize-name name) [])]
          (if (empty? all-candidates)
            (swap! missing-key conj name)
            (let [candidates (filter-by-faction all-candidates faction)
                  portrait-file (some (fn [[uk _]]
                                        (nm/find-portrait
                                         (nm/unit-key->portrait-base uk)
                                         role-map role-list))
                                      candidates)]
              (if portrait-file
                (do (copy-and-trim! (io/file portraits-dir portrait-file)
                                    (io/file asset-dir (str eid ".png"))
                                    dry-run?)
                    (swap! copied inc))
                (swap! no-portrait conj name)))))))
    (log (format "  [portraits] copied+trimmed %d portraits" @copied))
    (when (seq @missing-key)
      (log (format "  [portraits] %d units not in name index (e.g. %s)"
                   (count @missing-key) (pr-str (take 3 @missing-key)))))
    (when (seq @no-portrait)
      (log (format "  [portraits] %d units with no matching portrait (e.g. %s)"
                   (count @no-portrait) (pr-str (take 5 @no-portrait)))))))

(defn copy-ability-icons
  "Copy icons-dir/<icon>.png → asset-dir/<eid>.png for every ability with an
  icon_name, then trim transparent borders."
  [icons-dir asset-dir unit-ability-map key-eid-map dry-run?]
  (let [copied      (atom 0)
        missing-src (atom [])
        missing-eid (atom [])]
    (doseq [[key info] unit-ability-map
            :let [icon-name (:icon_name info)]
            :when (seq icon-name)]
      (if-let [eid (get key-eid-map key)]
        (let [src (io/file icons-dir (str icon-name ".png"))]
          (if (.isFile src)
            (do (copy-and-trim! src (io/file asset-dir (str eid ".png")) dry-run?)
                (swap! copied inc))
            (swap! missing-src conj icon-name)))
        (swap! missing-eid conj key)))
    (log (format "  [icons] copied+trimmed %d icons" @copied))
    (when (seq @missing-src)
      (log (format "  [icons] %d source PNGs not found (e.g. %s)"
                   (count @missing-src) (pr-str (take 3 @missing-src)))))
    (when (seq @missing-eid)
      (log (format "  [icons] %d keys have no eid in seed (e.g. %s)"
                   (count @missing-eid) (pr-str (take 3 @missing-eid)))))))

(defn copy-spell-icons
  "For each spell key in spell-key-eid-map, look up its icon_name in
  unit-ability-map (spells ARE abilities in WH3) and copy + trim
  icons-dir/<icon>.png → asset-dir/<eid>.png. asset-dir is the same
  ability icon directory since templates resolve spell icons via
  /icon/ability/."
  [icons-dir asset-dir unit-ability-map spell-key-eid-map dry-run?]
  (let [copied          (atom 0)
        missing-src     (atom [])
        missing-ability (atom [])]
    (doseq [[key eid] spell-key-eid-map]
      (let [info      (get unit-ability-map key)
            icon-name (or (:icon_name info) "")]
        (cond
          (or (nil? info) (empty? icon-name))
          (swap! missing-ability conj key)

          :else
          (let [src (io/file icons-dir (str icon-name ".png"))]
            (if (.isFile src)
              (do (copy-and-trim! src (io/file asset-dir (str eid ".png")) dry-run?)
                  (swap! copied inc))
              (swap! missing-src conj icon-name))))))
    (log (format "  [spell icons] copied+trimmed %d icons" @copied))
    (when (seq @missing-src)
      (log (format "  [spell icons] %d source PNGs not found (e.g. %s)"
                   (count @missing-src) (pr-str (take 3 @missing-src)))))
    (when (seq @missing-ability)
      (log (format "  [spell icons] %d spell keys not in unit_abilities_tables (e.g. %s)"
                   (count @missing-ability) (pr-str (take 3 @missing-ability)))))))

(defn- ui-icon-stem [rel]
  (let [base (last (str/split rel #"/"))
        dot  (.lastIndexOf ^String base ".")]
    (if (pos? dot) (subs base 0 dot) base)))

(defn copy-item-icons
  "Copy one icon per distinct ui_icon stem referenced by MP items. Dedupes
  ~1190 items to ~80 source files. Writes {asset-dir}/{stem}.png. `root`
  is the extraction directory containing `ui/`."
  [ancillary-icons-root asset-dir item-key-type-map type-icon-map dry-run?]
  (let [stem->src    (atom {})
        missing-type (atom 0)]
    (doseq [[_ item-type] item-key-type-map
            :let [rel (get type-icon-map item-type)]]
      (if-not rel
        (swap! missing-type inc)
        (let [stem (ui-icon-stem rel)]
          (when-not (contains? @stem->src stem)
            (swap! stem->src assoc stem (io/file ancillary-icons-root rel))))))
    (let [copied      (atom 0)
          missing-src (atom [])]
      (doseq [[stem src] @stem->src]
        (if (.isFile ^java.io.File src)
          (do (copy-and-trim! src (io/file asset-dir (str stem ".png")) dry-run?)
              (swap! copied inc))
          (swap! missing-src conj stem)))
      (log (format "  [item icons] copied+trimmed %d distinct icons (dedup from %d items)"
                   @copied (count item-key-type-map)))
      (when (seq @missing-src)
        (log (format "  [item icons] %d source PNGs not found (e.g. %s)"
                     (count @missing-src) (pr-str (take 3 @missing-src)))))
      (when (pos? @missing-type)
        (log (format "  [item icons] %d items with no type/icon mapping" @missing-type))))))

(defn copy-mount-icons
  "Copy one icon per distinct ui_icon stem referenced by mount-category
  ancillaries. `root` is the extraction directory containing `ui/`."
  [ancillary-icons-root asset-dir ancillary-rows type-icon-map dry-run?]
  (let [stem->src (atom {})]
    (doseq [a ancillary-rows
            :when (= "mount" (get a "category"))
            :let [rel (get type-icon-map (or (get a "type") ""))]
            :when rel]
      (let [stem (ui-icon-stem rel)]
        (when-not (contains? @stem->src stem)
          (swap! stem->src assoc stem (io/file ancillary-icons-root rel)))))
    (let [copied      (atom 0)
          missing-src (atom [])]
      (doseq [[stem src] @stem->src]
        (if (.isFile ^java.io.File src)
          (do (copy-and-trim! src (io/file asset-dir (str stem ".png")) dry-run?)
              (swap! copied inc))
          (swap! missing-src conj stem)))
      (log (format "  [mount icons] copied+trimmed %d distinct icons" @copied))
      (when (seq @missing-src)
        (log (format "  [mount icons] %d source PNGs not found (e.g. %s)"
                     (count @missing-src) (pr-str (take 3 @missing-src))))))))
