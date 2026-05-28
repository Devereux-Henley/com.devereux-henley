(ns com.devereux-henley.rts-web.web.game.view
  (:require
   [clojure.java.io :as io]
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.render :as render]
   [com.devereux-henley.rts-web.web.game.queries :as queries]
   [com.devereux-henley.rts-web.web.view :as web.view]
   [integrant.core]))

(defmethod integrant.core/init-key ::faction-list-view
  [_init-key _dependencies]
  (partial web.view/standard-view-handler "faction-list.html"))

(defmethod integrant.core/init-key ::game-index-view
  [_init-key _dependencies]
  (fn [request]
    {:status 200
     :body   (render/render-view "game-index.html"
                                 (assoc (web.view/base-context request)
                                        :data (:game (:game-context request))))}))

(defn- units-by-category
  "Group a faction's units by category. The query sorts by
  `(unit-category-name, name)` so a sequential partition produces stable
  groups without resorting."
  [units]
  (mapv (fn [group]
          {:category (:unit-category-name (first group))
           :units    (vec group)})
        (partition-by :unit-category-name units)))

(defmethod integrant.core/init-key ::faction-view
  [_init-key {:keys [datalog-connection]}]
  (partial web.view/standard-entity-view-handler
           (fn [eid]
             (if-let [faction (queries/faction-by-eid datalog-connection eid)]
               (assoc-in faction [:_embedded :units-by-category]
                         (units-by-category
                          (queries/units-for-faction datalog-connection eid)))
               {:type :missing/resource :name "faction" :id eid}))
           "faction.html"
           (fn [_data _request] {})))

(defn- resolve-spells
  "Spell resolution for unit detail: lore-based wizards pull from the
  `spell-lore` table; fixed-list casters resolve `draftable-spells` keys
  against the spell table."
  [conn lore-key draftable-spells]
  (if lore-key
    (mapv (fn [{:keys [eid key name mana-cost cost]}]
            {:eid eid :key key :name name :mana-cost mana-cost :cost cost})
          (queries/spells-for-lore conn lore-key))
    (let [key->spell (queries/spells-by-keys conn draftable-spells)]
      (mapv (fn [k]
              (let [spell (get key->spell k)]
                {:name      (or (:name spell) k)
                 :eid       (:eid spell)
                 :mana-cost (:mana-cost spell)
                 :cost      (:cost spell)}))
            draftable-spells))))

(defn- resolve-abilities
  "Resolve ability keys against the ability table, preserving the input
  order so the rendered list matches the order the unit lists them."
  [conn ability-keys]
  (let [key->ability (queries/abilities-by-keys conn ability-keys)]
    (mapv (fn [k]
            (let [a (get key->ability k)]
              {:name        (:name a)
               :eid         (:eid a)
               :description (:description a)}))
          ability-keys)))

(defmethod integrant.core/init-key ::unit-view
  [_init-key {:keys [datalog-connection]}]
  (partial web.view/standard-entity-view-handler
           (fn [eid]
             (or (queries/unit-by-eid datalog-connection eid)
                 {:type :missing/resource :name "unit" :id eid}))
           "unit.html"
           (fn [data _request]
             (let [{:keys [stats abilities draftable-spells]} (domain/parse-unit-statistics (:unit-statistics data))
                   lore-key                                   (:lore data)
                   resolved-spells                            (resolve-spells datalog-connection lore-key draftable-spells)
                   resolved-abilities                         (resolve-abilities datalog-connection abilities)
                   mounts                                     (queries/mounts-for-unit datalog-connection (:eid data))
                   items                                      (queries/items-for-unit datalog-connection (:eid data))
                   portrait-stem                              (:eid data)]
               {:unit-statistics  stats
                :abilities        (not-empty resolved-abilities)
                :draftable-spells (not-empty resolved-spells)
                :mounts           (not-empty mounts)
                :items            (not-empty items)
                :lore             lore-key
                :unit-card        (when (io/resource
                                         (str "rts-web/asset/card/unit/" portrait-stem ".png"))
                                    (str "/card/unit/" portrait-stem ".png"))}))))
