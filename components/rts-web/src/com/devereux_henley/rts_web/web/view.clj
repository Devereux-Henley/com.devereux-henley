(ns com.devereux-henley.rts-web.web.view
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.skin :as skin]
   [com.devereux-henley.rts-web.web.game :as web.game]
   [com.devereux-henley.rts-web.web.tournament :as web.tournament]
   [integrant.core]
   [selmer.filters]
   [selmer.parser]
   [taoensso.timbre :as log]))

(defn- active-nav
  "Derive the navbar section label for the given request URI."
  [uri]
  (cond
    (nil? uri)                            nil
    (string/includes? uri "/draft")       "drafts"
    (string/includes? uri "/competitive") "competitive"
    (string/includes? uri "/league")      "competitive"
    (string/includes? uri "/season")      "competitive"
    (string/includes? uri "/tournament")  "competitive"
    (string/includes? uri "/faction")     "atlas"
    :else                                 nil))

(defn base-context
  "Template context shared across every view — session, active navbar section,
   and any game-context assembled by the middleware."
  [request]
  (merge {:session    (:ory-session request)
          :active-nav (active-nav (:uri request))}
         (:game-context request)))

(selmer.filters/add-filter! :not-empty? (comp boolean seq))

(defn standard-view-handler
  [view-name request]
  {:status 200
   :body   (selmer.parser/render-file
            (str "rts-web/view/" view-name)
            (base-context request))})

(defn standard-entity-view-handler
  [pipeline-fn template-name extra-data-fn request]
  (let [{{{:keys [eid]} :path} :parameters} request
        data                                (pipeline-fn eid)]
    (if (= :missing/resource (:type data))
      {:status 404 :body data}
      {:status 200
       :body   (selmer.parser/render-file
                (str "rts-web/view/" template-name)
                (merge (base-context request)
                       {:data data}
                       (extra-data-fn data request)))})))

(defmethod integrant.core/init-key ::game-context-middleware
  [_init-key dependencies]
  (fn [handler]
    (fn [request]
      (let [game-eid (get-in request [:parameters :path :game-eid])
            game     (domain/get-game-by-eid dependencies game-eid)]
        (if game
          (handler (assoc request :game-context {:game-eid game-eid
                                                 :game     (assoc game :logo (skin/logo-for-game game-eid))
                                                 :factions (domain/get-factions-for-game dependencies game-eid)
                                                 :socials  (domain/get-socials-for-game dependencies game-eid)
                                                 :skin     (skin/skin-for-game game-eid)}))
          {:status 404 :body {:type :missing/resource :name "game" :id game-eid}})))))

(defmethod integrant.core/init-key ::game-selector-view
  [_init-key dependencies]
  (fn [request]
    (let [games (mapv (fn [game]
                        (assoc game :logo (skin/logo-for-game (:eid game))))
                      (domain/get-games dependencies))]
      {:status 200
       :body   (selmer.parser/render-file
                "rts-web/view/game-selector.html"
                (assoc (base-context request) :games games))})))

(defmethod integrant.core/init-key ::game-view
  [_init-key _dependencies]
  (partial standard-view-handler "game.html"))

(defmethod integrant.core/init-key ::about-view
  [_init-key _dependencies]
  (partial standard-view-handler "about.html"))

(defmethod integrant.core/init-key ::contact-view
  [_init-key _dependencies]
  (partial standard-view-handler "contact.html"))

(defmethod integrant.core/init-key ::login-view
  [_init-key {:keys [auth-hostname]}]
  (fn [_request] {:status 301 :headers {"Location" (str auth-hostname "/ui/login")}}))

(defmethod integrant.core/init-key ::faction-view
  [_init-key dependencies]
  (partial standard-entity-view-handler
           (fn [eid]
             (web.game/load-units-by-category-for-faction
              dependencies
              (web.game/get-faction-by-eid dependencies eid)))
           "faction.html"
           (fn [_data _request] {})))

(defmethod integrant.core/init-key ::unit-view
  [_init-key dependencies]
  (partial standard-entity-view-handler
           (fn [eid] (web.game/get-unit-by-eid dependencies eid))
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

(defn ^:private build-draft-context
  [dependencies draft request]
  (let [game-mode         (domain/get-game-mode-by-eid dependencies (:game-mode-eid draft))
        faction           (domain/get-faction-by-eid dependencies (:faction-eid draft))
        units             (domain/hydrate-units-with-stats
                           (domain/get-units-for-faction dependencies (:faction-eid draft)))
        unit-by-eid       (into {} (map (juxt :eid identity) units))
        units-by-cat      (->> units
                               (partition-by :unit-category-name)
                               (mapv (fn [g] {:category (:unit-category-name (first g))
                                              :units    (vec (sort-by :cost g))})))
        state             (domain/get-draft-state dependencies (:eid draft))
        lore-portrait-key (fn [unit-eid lore-key]
                            (when lore-key
                              (->> (domain/get-lores-for-unit dependencies unit-eid)
                                   (some #(when (= lore-key (:key %)) (:portrait-key %))))))
        hydrate           (fn [entries]
                            (vec (keep (fn [entry]
                                         (when-let [u (get unit-by-eid (:unit-eid entry))]
                                           (assoc u
                                                  :total-cost        (or (:total-cost entry) (:cost u))
                                                  :entry-eid         (:entry-eid entry)
                                                  :lore-portrait-key (lore-portrait-key (:unit-eid entry) (:lore entry)))))
                                       entries)))
        main-units        (hydrate (:main state))
        reinf-units       (hydrate (:reinforcements state))
        main-ctx          (domain/build-section-context "main" main-units (:eid draft) game-mode)
        reinf-ctx         (domain/build-section-context "reinforcements" reinf-units (:eid draft) game-mode)]
    {:faction                faction
     :game-mode              game-mode
     :reinforcements-enabled (= 1 (:reinforcements-enabled game-mode))
     :units-by-category      units-by-cat
     :main-section           main-ctx
     :reinf-section          reinf-ctx
     :game                   (:game (:game-context request))
     :draft-eid              (:eid draft)}))

(defmethod integrant.core/init-key ::draft-view
  [_init-key dependencies]
  (partial standard-entity-view-handler
           (fn [eid] (web.game/get-draft-by-eid dependencies eid))
           "draft-index.html"
           (partial build-draft-context dependencies)))

(defmethod integrant.core/init-key ::my-drafts-view
  [_init-key dependencies]
  (fn [{session :ory-session :as request}]
    (let [player-sub (get-in session [:identity :id])
          game-eid   (:game-eid (:game-context request))]
      {:status 200
       :body   (selmer.parser/render-file
                "rts-web/view/my-drafts.html"
                (assoc (base-context request)
                       :drafts (domain/get-drafts-for-player-by-game dependencies player-sub game-eid)))})))

(defmethod integrant.core/init-key ::faction-list-view
  [_init-key _dependencies]
  (partial standard-view-handler "faction-list.html"))

(defmethod integrant.core/init-key ::game-index-view
  [_init-key _dependencies]
  (fn [request]
    {:status 200
     :body   (selmer.parser/render-file
              "rts-web/view/game-index.html"
              (assoc (base-context request)
                     :data (:game (:game-context request))))}))

(defmethod integrant.core/init-key ::create-draft-view
  [_init-key dependencies]
  (fn [request]
    (let [game-eid   (:game-eid (:game-context request))
          game-modes (domain/get-game-modes-for-game dependencies game-eid)]
      {:status 200
       :body   (selmer.parser/render-file
                "rts-web/view/create-draft.html"
                (assoc (base-context request)
                       :game-modes game-modes
                       :draft-eid  (random-uuid)))})))

(defmethod integrant.core/init-key ::logout-view
  [_init-key {:keys [auth-hostname]}]
  (fn [request]
    (log/info (:ory-session request))
    {:status 301 :headers {"Location" (str auth-hostname "/self-service/logout?token=")}}))

(defn- enrich-tournament-with-league
  "Adds :league-name and :season-display-name to a tournament when it carries
   a :league-eid / :season-eid, by reading from the supplied lookup maps."
  [eid->league eid->season tournament]
  (cond-> tournament
    (:league-eid tournament) (assoc :league-name (get-in eid->league [(:league-eid tournament) :name]))
    (:season-eid tournament) (assoc :season-display-name (get-in eid->season [(:season-eid tournament) :display-name]))))

(defmethod integrant.core/init-key ::competitive-view
  [_init-key dependencies]
  (fn [request]
    (let [game-eid             (:game-eid (:game-context request))
          tournaments          (domain/get-tournaments-for-game dependencies game-eid)
          leagues              (domain/get-leagues-for-game dependencies game-eid)
          eid->league          (into {} (map (juxt :eid identity) leagues))
          ;; Pre-fetch the seasons for every league referenced by tournaments so we
          ;; can label tournament cards without N+1 calls.
          referenced-leagues   (distinct (keep :league-eid tournaments))
          eid->season          (into {}
                                     (mapcat (fn [leid]
                                               (map (juxt :eid identity)
                                                    (domain/get-seasons-for-league dependencies leid)))
                                             referenced-leagues))
          enriched-tournaments (mapv (fn [t]
                                       (let [state   (domain/get-tournament-state dependencies (:eid t))
                                             entries (domain/get-entries dependencies (:eid t))]
                                         (->> (assoc t
                                                     :status      (:status state)
                                                     :entry-count (count entries))
                                              (enrich-tournament-with-league eid->league eid->season))))
                                     tournaments)
          enriched-leagues     (mapv (fn [l]
                                       (let [current-season (domain/get-current-season-for-league dependencies (:eid l))
                                             tcount         (count (filter #(= (:eid l) (:league-eid %)) tournaments))]
                                         (assoc l
                                                :current-season current-season
                                                :tournament-count tcount)))
                                     leagues)]
      {:status 200
       :body   (selmer.parser/render-file
                "rts-web/view/competitive.html"
                (assoc (base-context request)
                       :tournaments enriched-tournaments
                       :leagues enriched-leagues))})))

(defmethod integrant.core/init-key ::create-tournament-view
  [_init-key dependencies]
  (fn [request]
    (let [game-eid (:game-eid (:game-context request))
          leagues  (domain/get-leagues-for-game dependencies game-eid)]
      {:status 200
       :body   (selmer.parser/render-file
                "rts-web/view/create-tournament.html"
                (assoc (base-context request)
                       :tournament-eid   (random-uuid)
                       :leagues          leagues
                       :timezones        domain/common-timezones
                       :default-timezone domain/default-timezone))})))

(defmethod integrant.core/init-key ::season-options-fragment-view
  [_init-key dependencies]
  (fn [request]
    (let [league-eid (get-in request [:parameters :query :league-eid])
          seasons    (when league-eid
                       (domain/get-seasons-for-league dependencies league-eid))]
      {:status  200
       :headers {"Content-Type" "text/html; charset=utf-8"}
       :body    (selmer.parser/render-file
                 "rts-web/view/_partial/season-options.html"
                 (assoc (base-context request) :seasons seasons))})))

(defmethod integrant.core/init-key ::create-league-view
  [_init-key _dependencies]
  (fn [request]
    {:status 200
     :body   (selmer.parser/render-file
              "rts-web/view/create-league.html"
              (assoc (base-context request)
                     :league-eid (random-uuid)))}))

(defmethod integrant.core/init-key ::league-view
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters :as request}]
    (let [league (domain/get-league-by-eid dependencies eid)]
      (if (nil? league)
        {:status 404 :body {:type :missing/resource :name "league" :id eid}}
        (let [seasons           (domain/get-seasons-for-league dependencies eid)
              all-tournaments   (domain/get-tournaments-for-game dependencies (:game-eid league))
              league-tourneys   (filter #(= eid (:league-eid %)) all-tournaments)
              eid->season       (into {} (map (juxt :eid identity) seasons))
              enriched-tourneys (mapv (fn [t]
                                        (let [state (domain/get-tournament-state dependencies (:eid t))]
                                          (cond-> (assoc t :status (:status state))
                                            (:season-eid t) (assoc :season-display-name (get-in eid->season [(:season-eid t) :display-name])))))
                                      league-tourneys)
              standings         (domain/get-league-faction-standings dependencies eid)
              player-sub        (get-in request [:ory-session :identity :id])]
          {:status 200
           :body   (selmer.parser/render-file
                    "rts-web/view/league-index.html"
                    (assoc (base-context request)
                           :data league
                           :seasons seasons
                           :tournaments enriched-tourneys
                           :standings standings
                           :is-organizer (= player-sub (:created-by-sub league))))})))))

(defmethod integrant.core/init-key ::create-season-view
  [_init-key dependencies]
  (fn [request]
    (let [league-eid (get-in request [:parameters :path :league-eid])
          league     (domain/get-league-by-eid dependencies league-eid)]
      (if (nil? league)
        {:status 404 :body {:type :missing/resource :name "league" :id league-eid}}
        {:status 200
         :body   (selmer.parser/render-file
                  "rts-web/view/create-season.html"
                  (assoc (base-context request)
                         :league league
                         :league-eid league-eid
                         :season-eid (random-uuid)
                         :timezones domain/common-timezones
                         :default-timezone domain/default-timezone))}))))

(defmethod integrant.core/init-key ::season-view
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters :as request}]
    (let [season (domain/get-season-by-eid dependencies eid)]
      (if (nil? season)
        {:status 404 :body {:type :missing/resource :name "season" :id eid}}
        (let [league          (domain/get-league-by-eid dependencies (:league-eid season))
              all-tournaments (domain/get-tournaments-for-game dependencies (:game-eid league))
              season-tourneys (filter #(= eid (:season-eid %)) all-tournaments)
              enriched        (mapv (fn [t]
                                      (let [state (domain/get-tournament-state dependencies (:eid t))]
                                        (assoc t :status (:status state))))
                                    season-tourneys)
              standings       (domain/get-season-faction-standings dependencies eid)]
          {:status 200
           :body   (selmer.parser/render-file
                    "rts-web/view/season-index.html"
                    (assoc (base-context request)
                           :data season
                           :league league
                           :tournaments enriched
                           :standings standings))})))))

(defmethod integrant.core/init-key ::tournament-phase-form-view
  [_init-key _dependencies]
  (fn [request]
    (let [eid (get-in request [:parameters :path :eid])]
      {:status  200
       :headers {"Content-Type" "text/html; charset=utf-8"}
       :body    (selmer.parser/render-file
                 "rts-web/view/tournament-phase-row.html"
                 (assoc (base-context request) :tournament-eid eid))})))

(defmethod integrant.core/init-key ::tournament-view
  [_init-key dependencies]
  (partial standard-entity-view-handler
           (fn [eid] (web.tournament/get-tournament-by-eid dependencies eid))
           "tournament-index.html"
           (fn [data request]
             (let [tournament-eid        (:eid data)
                   state                 (domain/get-tournament-state dependencies tournament-eid)
                   entries               (domain/get-entries dependencies tournament-eid)
                   raw-matches           (domain/get-matches-for-tournament dependencies tournament-eid)
                   phases                (:phases state)
                   qualifier-count       (or (:qualifier-count state) (count (:standings state)))
                   player-sub            (get-in request [:ory-session :identity :id])
                   has-entry             (some #(= player-sub (:player-sub %)) entries)
                   now                   (java.time.Instant/now)
                   reg-open              (domain/is-registration-open? state now)
                   is-organizer          (= player-sub (:created-by-sub data))
                   organizer-has-actions (and is-organizer
                                              (contains? #{"registration" "active"} (:status state)))
                   league                (when (:league-eid data)
                                           (domain/get-league-by-eid dependencies (:league-eid data)))
                   season                (when (:season-eid data)
                                           (domain/get-season-by-eid dependencies (:season-eid data)))
                   ;; For each match where the current player is a participant, surface
                   ;; their drafts for this game so the template can render a picker.
                   player-drafts         (when player-sub
                                           (domain/get-drafts-for-player-by-game dependencies player-sub (:game-eid data)))
                   matches-with-picker   (mapv (fn [m]
                                                 (let [is-p1        (= player-sub (:player-one-sub m))
                                                       is-p2        (= player-sub (:player-two-sub m))
                                                       my-draft-eid (cond is-p1 (:player-one-draft-eid m)
                                                                          is-p2 (:player-two-draft-eid m))]
                                                   (assoc m
                                                          :is-my-match (or is-p1 is-p2)
                                                          :my-draft-eid my-draft-eid)))
                                               raw-matches)]
               {:tournament-state      state
                :entries               entries
                :matches-by-phase      (domain/group-matches-by-phase raw-matches phases qualifier-count)
                :my-matches            (filterv :is-my-match matches-with-picker)
                :player-drafts         player-drafts
                :league                league
                :season                season
                :has-entry             has-entry
                :registration-open     reg-open
                :is-organizer          is-organizer
                :organizer-has-actions organizer-has-actions}))))
