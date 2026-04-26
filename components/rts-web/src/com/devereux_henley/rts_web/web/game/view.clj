(ns com.devereux-henley.rts-web.web.game.view
  (:require
   [clojure.java.io :as io]
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.render :as render]
   [com.devereux-henley.rts-web.web.game.api :as web.game.api]
   [com.devereux-henley.rts-web.web.view :as web.view]
   [integrant.core]))

(defmethod integrant.core/init-key ::faction-list-view
  [_init-key _dependencies]
  (partial web.view/standard-view-handler "faction-list.html"))

(defmethod integrant.core/init-key ::game-index-view
  [_init-key _dependencies]
  (fn [request]
    {:status 200
     :body   (render/render "game-index.html"
                            (assoc (web.view/base-context request)
                                   :data (:game (:game-context request))))}))

(defmethod integrant.core/init-key ::faction-view
  [_init-key dependencies]
  (partial web.view/standard-entity-view-handler
           (fn [eid]
             (web.game.api/load-units-by-category-for-faction
              dependencies
              (web.game.api/get-faction-by-eid dependencies eid)))
           "faction.html"
           (fn [_data _request] {})))

(defmethod integrant.core/init-key ::unit-view
  [_init-key dependencies]
  (partial web.view/standard-entity-view-handler
           (fn [eid] (web.game.api/get-unit-by-eid dependencies eid))
           "unit.html"
           (fn [data request]
             (let [{:keys [stats abilities draftable-spells]} (domain/parse-unit-statistics (:unit-statistics data))
                   lores                                      (vec (domain/get-lores-for-unit dependencies (:eid data)))
                   ;; ?lore=<key> toggles which lore's spell pool + portrait
                   ;; render; nil → base unit's draftable-spells + default portrait.
                   selected-lore-key                          (not-empty (get-in request [:parameters :query :lore]))
                   selected-lore                              (when selected-lore-key
                                                                (first (filter #(= selected-lore-key (:key %)) lores)))
                   ;; When a lore is selected, derive spells from the canonical
                   ;; spell_lore junction (one source of truth for the whole
                   ;; lore). Otherwise fall back to the unit's own
                   ;; draftable-spells from unit_statistics.
                   resolved-spells                            (if selected-lore
                                                                (mapv (fn [{:keys [key eid name mana-cost cost]}]
                                                                        {:name      name
                                                                         :key       key
                                                                         :eid       eid
                                                                         :mana-cost mana-cost
                                                                         :cost      cost})
                                                                      (domain/get-spells-for-lore dependencies selected-lore-key))
                                                                (let [key->spell (domain/get-spells-by-keys dependencies draftable-spells)]
                                                                  (mapv (fn [k]
                                                                          (let [spell (get key->spell k)]
                                                                            {:name      (or (:name spell) k)
                                                                             :eid       (:eid spell)
                                                                             :mana-cost (:mana-cost spell)
                                                                             :cost      (:cost spell)}))
                                                                        draftable-spells)))
                   key->ability                               (domain/get-abilities-by-keys dependencies abilities)
                   resolved-abilities                         (mapv (fn [k]
                                                                      (let [a (get key->ability k)]
                                                                        {:name        (:name a)
                                                                         :eid         (:eid a)
                                                                         :description (:description a)}))
                                                                    abilities)
                   mounts                                     (domain/get-mounts-for-unit dependencies (:eid data))
                   items                                      (domain/get-items-for-unit dependencies (:eid data))
                   portrait-stem                              (or (:portrait-key selected-lore) (:eid data))
                   lores-marked                               (mapv #(assoc % :selected (= selected-lore-key (:key %))) lores)]
               {:unit-statistics   stats
                :abilities         (not-empty resolved-abilities)
                :draftable-spells  (not-empty resolved-spells)
                :mounts            (not-empty mounts)
                :items             (not-empty items)
                :lores             (not-empty lores-marked)
                :lore              selected-lore-key
                :lore-portrait-key (:portrait-key selected-lore)
                :unit-card         (when (io/resource
                                          (str "rts-web/asset/card/unit/" portrait-stem ".png"))
                                     (str "/card/unit/" portrait-stem ".png"))}))))
