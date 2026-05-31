(ns com.devereux-henley.rpfm-scraper.unit-mounts-seed
  (:require
   [clojure.set :as set]))

(defn granted-keys
  "Returns the sorted vec of ability keys present on the mounted variant's
  land_unit but not on the base variant's land_unit, or nil when the
  mounted variant grants nothing new."
  [base-lu mounted-lu land-unit-ability-map]
  (let [mounted-abilities (get land-unit-ability-map mounted-lu #{})
        base-abilities    (get land-unit-ability-map base-lu #{})
        diff              (sort (set/difference mounted-abilities base-abilities))]
    (when (seq diff) (vec diff))))
