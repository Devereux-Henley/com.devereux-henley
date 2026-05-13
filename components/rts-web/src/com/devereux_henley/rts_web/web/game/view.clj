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
     :body   (render/render-view "game-index.html"
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
           (fn [data _request]
             (let [{:keys [stats abilities draftable-spells]} (domain/parse-unit-statistics (:unit-statistics data))
                   lore-key                                   (:lore data)
                   ;; Wizard rows carry a `lore` column.  When set, the
                   ;; spell pool comes from the canonical spell_lore
                   ;; junction (one source of truth per lore); otherwise
                   ;; the unit's own draftable-spells from
                   ;; unit_statistics applies (unique characters / non-
                   ;; spellcasters).
                   resolved-spells                            (if lore-key
                                                                (mapv (fn [{:keys [key eid name mana-cost cost]}]
                                                                        {:name      name
                                                                         :key       key
                                                                         :eid       eid
                                                                         :mana-cost mana-cost
                                                                         :cost      cost})
                                                                      (domain/get-spells-for-lore dependencies lore-key))
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
