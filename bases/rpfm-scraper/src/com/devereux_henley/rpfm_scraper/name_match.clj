(ns com.devereux-henley.rpfm-scraper.name-match
  (:require
   [clojure.string :as str]))

(defn normalize-name [s]
  (when s
    (-> s
        (str/replace "\u2013" "-")
        (str/replace "\u2014" "-")
        (str/replace "\u2018" "'")
        (str/replace "\u2019" "'")
        str/trim)))

(defn build-name-index
  "display-name → [[unit-key land-unit-key] ...]. Walks the main-unit table
  rows in their original order so that the 'first candidate' tiebreaker
  matches the RPFM table row order (same as Python's dict-insertion order)."
  [land-units-loc main-unit-rows]
  (let [prefix  "land_units_onscreen_name_"
        pn      (count prefix)
        lu-name (reduce-kv
                 (fn [m k v]
                   (if (and k (str/starts-with? k prefix))
                     (assoc m (subs k pn) (normalize-name v))
                     m))
                 {}
                 land-units-loc)]
    (reduce
     (fn [m row]
       (let [unit-key (get row "unit")
             lu-key   (get row "land_unit")
             nm       (get lu-name lu-key)]
         (if nm
           (update m nm (fnil conj []) [unit-key lu-key])
           m)))
     {}
     main-unit-rows)))

(defn find-unit-key
  "Best matching [unit-key land-unit-key] for a display name + faction. Returns
  [nil nil] when no match is possible."
  [unit-name faction-prefixes name-index]
  (let [norm       (normalize-name unit-name)
        candidates (get name-index norm [])]
    (cond
      (empty? candidates)
      [nil nil]

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
          (or (some (fn [gp]
                      (some (fn [item] (when (str/starts-with? (first item) gp) item))
                            filtered))
                    ["wh3_" "wh2_" "wh_"])
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
