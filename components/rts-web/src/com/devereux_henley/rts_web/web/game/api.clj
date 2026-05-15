(ns com.devereux-henley.rts-web.web.game.api
  (:require
   [com.devereux-henley.http.contract :as web.core]
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.schema.contract :as schema.contract]
   [integrant.core]
   [reitit.core]))

(defn get-game-by-eid
  [dependencies eid]
  (or (domain/get-game-by-eid dependencies eid)
      {:type :missing/resource :name "game" :id eid}))

(defn embed-factions
  "Enrichment fn: returns the game model with the game's factions
   embedded under :_embedded.factions. Bound into game-embed-registry
   so /api/game/:eid?embed=factions opts into it."
  [dependencies model]
  (assoc-in model [:_embedded :factions]
            (domain/get-factions-for-game dependencies (:eid model))))

(defn embed-game-modes
  "Enrichment fn: returns the game model with its game modes embedded
   under :_embedded.game-modes."
  [dependencies model]
  (assoc-in model [:_embedded :game-modes]
            (domain/get-game-modes-for-game dependencies (:eid model))))

(defn embed-socials
  "Enrichment fn: returns the game model with the game's social-link
   resources embedded under :_embedded.socials."
  [dependencies model]
  (assoc-in model [:_embedded :socials]
            (domain/get-socials-for-game dependencies (:eid model))))

(def game-embed-registry
  {:factions   embed-factions
   :game-modes embed-game-modes
   :socials    embed-socials})

(def game-query-parameters
  (schema.contract/to-schema
   [:map
    [:version {:optional true} :pos-int]
    [:embed {:optional true} [:or :string [:sequential :string]]]]))

(defn embed-units-by-category
  "Enrichment fn: returns the faction model with its units embedded
   under :_embedded.units-by-category, grouped by unit category."
  [dependencies model]
  (let [units             (domain/get-units-for-faction dependencies (:eid model))
        units-by-category (->> units
                               (partition-by :unit-category-name)
                               (mapv (fn [group]
                                       {:category (:unit-category-name (first group))
                                        :units    (vec group)})))]
    (assoc-in model [:_embedded :units-by-category] units-by-category)))

(def faction-embed-registry
  {:units-by-category embed-units-by-category})

(def faction-query-parameters
  (schema.contract/to-schema
   [:map
    [:version {:optional true} :pos-int]
    [:embed {:optional true} [:or :string [:sequential :string]]]]))

(defn get-draft-by-eid
  [dependencies eid]
  (or (domain/get-draft-by-eid dependencies eid)
      {:type :missing/resource :name "draft" :id eid}))

(defn get-unit-by-eid
  [dependencies eid]
  (or (domain/get-unit-by-eid dependencies eid)
      {:type :missing/resource :name "unit" :id eid}))

(defn- enrich-unit
  "Add the parsed unit-statistics plus embedded abilities, spells, items,
   and mounts to a unit row. Spells come from `draftable-spells` in
   unit-statistics for fixed-list casters; lore-based casters expose
   the full lore via `:lore`, in which case we fall back to
   `get-spells-for-lore`."
  [dependencies {:keys [unit-statistics eid lore] :as unit}]
  (let [parsed     (when unit-statistics
                     (domain/parse-unit-statistics unit-statistics))
        ability-ks (:abilities parsed)
        spell-ks   (:draftable-spells parsed)
        abilities  (when (seq ability-ks)
                     (vals (domain/get-abilities-by-keys dependencies ability-ks)))
        spells     (cond
                     (seq spell-ks) (vals (domain/get-spells-by-keys dependencies spell-ks))
                     (seq lore)     (domain/get-spells-for-lore dependencies lore))
        items      (domain/get-items-for-unit dependencies eid)
        mounts     (domain/get-mounts-for-unit dependencies eid)]
    (-> unit
        (assoc :stats      (vec (:stats parsed))
               :attributes (vec (:attributes parsed))
               :health     (:health parsed)
               :barrier    (:barrier parsed))
        (assoc :_embedded {:abilities (vec abilities)
                           :spells    (vec spells)
                           :items     (vec items)
                           :mounts    (vec mounts)}))))

(defn get-faction-by-eid
  [dependencies eid]
  (or (domain/get-faction-by-eid dependencies eid)
      {:type :missing/resource :name "faction" :id eid}))

(defn get-games
  [dependencies {:keys [hostname router]}]
  {:type      :collection/game
   :_embedded {:results (domain/get-games dependencies)}
   :_links    {:self (str hostname
                          (-> router
                              (reitit.core/match-by-name! :collection/game)
                              :path))}})

(defn- ok-or-404
  [result]
  (if (= :missing/resource (:type result))
    {:status 404 :body result}
    {:status 200 :body result}))

(defmethod integrant.core/init-key ::get-faction
  [_init-key dependencies]
  (fn [{{{:keys [eid]}   :path
         {:keys [embed]} :query} :parameters
        :as                      _request}]
    (let [embed-set (some->> (web.core/query-param->vec embed) (into #{} (map keyword)))]
      (ok-or-404
       (web.core/apply-embeds faction-embed-registry dependencies embed-set
                              (get-faction-by-eid dependencies eid))))))

(defmethod integrant.core/init-key ::get-game
  [_init-key dependencies]
  (fn [{{{:keys [eid]}   :path
         {:keys [embed]} :query} :parameters
        :as                      _request}]
    (let [embed-set (some->> (web.core/query-param->vec embed) (into #{} (map keyword)))]
      (ok-or-404
       (web.core/apply-embeds game-embed-registry dependencies embed-set
                              (get-game-by-eid dependencies eid))))))

(defn get-factions
  [dependencies game-eid {:keys [hostname router]}]
  {:type      :collection/faction
   :_embedded {:results (if game-eid
                          (domain/get-factions-for-game dependencies game-eid)
                          (domain/get-factions dependencies))}
   :_links    {:self (str hostname
                          (-> router
                              (reitit.core/match-by-name! :collection/faction)
                              (reitit.core/match->path
                               (when game-eid {:game-eid game-eid}))))}})

(defmethod integrant.core/init-key ::get-factions-collection
  [_init-key dependencies]
  (fn [{{{:keys [game-eid]} :query} :parameters
        router                      :reitit.core/router
        :as                         _request}]
    {:status 200
     :body   (get-factions dependencies game-eid
                           {:hostname (:hostname dependencies) :router router})}))

(defn get-units
  "Collection builder for /api/unit. When `faction-eid` is set the
   collection is filtered to that faction; nil returns every unit."
  [dependencies faction-eid {:keys [hostname router]}]
  {:type      :collection/unit
   :_embedded {:results (vec (if faction-eid
                               (domain/get-units-for-faction dependencies faction-eid)
                               (domain/get-units dependencies)))}
   :_links    {:self (str hostname
                          (-> router
                              (reitit.core/match-by-name! :collection/unit)
                              (reitit.core/match->path
                               (when faction-eid {:faction-eid faction-eid}))))}})

(defmethod integrant.core/init-key ::get-unit
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        :as                   _request}]
    (let [result (get-unit-by-eid dependencies eid)]
      (if (= :missing/resource (:type result))
        {:status 404 :body result}
        {:status 200 :body (enrich-unit dependencies result)}))))

(defmethod integrant.core/init-key ::get-units-collection
  [_init-key dependencies]
  (fn [{{{:keys [faction-eid]} :query} :parameters
        router                         :reitit.core/router
        :as                            _request}]
    {:status 200
     :body   (get-units dependencies faction-eid
                        {:hostname (:hostname dependencies) :router router})}))

(defmethod integrant.core/init-key ::get-games
  [_init-key dependencies]
  (fn [{router :reitit.core/router :as _request}]
    {:status 200
     :body   (get-games dependencies {:hostname (:hostname dependencies) :router router})}))
