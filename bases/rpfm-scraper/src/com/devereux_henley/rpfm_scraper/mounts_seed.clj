(ns com.devereux-henley.rpfm-scraper.mounts-seed
  (:require
   [clojure.string :as str]))

(defn stem-of [icon-path]
  (when (seq icon-path)
    (let [base (last (str/split icon-path #"/"))
          dot  (.lastIndexOf ^String base ".")]
      (if (pos? dot) (subs base 0 dot) base))))

(defn mount-name-from-stem [stem]
  (let [s (if (str/starts-with? stem "mount_") (subs stem (count "mount_")) stem)]
    (if (seq s)
      (->> (str/split s #"_")
           (map str/capitalize)
           (str/join " "))
      stem)))

(defn build-icon-stem->name
  "{icon_stem → display_name} by resolving every mount-category ancillary
  to its type's ui_icon basename; first-seen wins for dup stems."
  [ancillary-rows ancillary-name-map type-icon-map]
  (reduce
   (fn [m a]
     (if (= "mount" (get a "category"))
       (let [rel (get type-icon-map (or (get a "type") ""))]
         (if rel
           (let [stem (stem-of rel)]
             (if (and (seq stem) (not (contains? m stem)))
               (if-let [nm (get ancillary-name-map (get a "key"))]
                 (assoc m stem nm)
                 m)
               m))
           m))
       m))
   {}
   ancillary-rows))
