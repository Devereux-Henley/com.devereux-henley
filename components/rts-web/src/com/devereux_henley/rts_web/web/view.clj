(ns com.devereux-henley.rts-web.web.view
  (:require
   [clojure.java.io :as io]
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.web.game :as web.game]
   [com.devereux-henley.rts-web.web.tournament :as web.tournament]
   [integrant.core]
   [selmer.filters]
   [selmer.parser]
   [taoensso.timbre :as log]))

(selmer.filters/add-filter! :not-empty? (comp boolean seq))

(defn standard-view-handler
  [view-name request]
  {:status 200
   :body   (selmer.parser/render-file
            (str "rts-web/view/" view-name)
            (merge {:session (:ory-session request)}
                   (:game-context request)))})

(defn standard-entity-view-handler
  [pipeline-fn template-name extra-data-fn request]
  (let [{{{:keys [eid]} :path} :parameters} request
        data (pipeline-fn eid)]
    (if (= :missing/resource (:type data))
      {:status 404 :body data}
      {:status 200
       :body   (selmer.parser/render-file
                (str "rts-web/view/" template-name)
                (merge {:data    data
                        :session (:ory-session request)}
                       (:game-context request)
                       (extra-data-fn data request)))})))

(defmethod integrant.core/init-key ::game-context-middleware
  [_init-key dependencies]
  (fn [handler]
    (fn [request]
      (let [game-eid (get-in request [:parameters :path :game-eid])
            game     (domain/get-game-by-eid dependencies game-eid)]
        (if game
          (handler (assoc request :game-context {:game-eid game-eid
                                                 :game     game
                                                 :factions (domain/get-factions-for-game dependencies game-eid)
                                                 :socials  (domain/get-socials-for-game dependencies game-eid)}))
          {:status 404 :body {:type :missing/resource :name "game" :id game-eid}})))))

(defmethod integrant.core/init-key ::dashboard-view
  [_init-key _dependencies]
  (partial standard-view-handler "dashboard.html"))

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
           (fn [data _request]
             (let [{:keys [stats abilities draftable-spells]} (domain/parse-unit-statistics (:unit-statistics data))
                   key->spell         (domain/get-spells-by-keys dependencies draftable-spells)
                   key->ability       (domain/get-abilities-by-keys dependencies abilities)
                   resolved-spells    (mapv (fn [k]
                                              (let [spell (get key->spell k)]
                                                {:name      (or (:name spell) k)
                                                 :eid       (:eid spell)
                                                 :mana-cost (:mana-cost spell)
                                                 :cost      (:cost spell)}))
                                            draftable-spells)
                   resolved-abilities (mapv (fn [k]
                                              (let [a (get key->ability k)]
                                                {:name        (:name a)
                                                 :eid         (:eid a)
                                                 :description (:description a)}))
                                            abilities)
                   mounts             (domain/get-mounts-for-unit dependencies (:eid data))
                   items              (domain/get-items-for-unit dependencies (:eid data))]
               {:unit-statistics  stats
                :abilities        (not-empty resolved-abilities)
                :draftable-spells (not-empty resolved-spells)
                :mounts           (not-empty mounts)
                :items            (not-empty items)
                :unit-card        (when (io/resource
                                         (str "rts-web/asset/card/unit/" (:eid data) ".png"))
                                    (str "/card/unit/" (:eid data) ".png"))}))))

(defn ^:private build-draft-context
  [dependencies draft request]
  (let [game-mode    (domain/get-game-mode-by-eid dependencies (:game-mode-eid draft))
        faction      (domain/get-faction-by-eid dependencies (:faction-eid draft))
        units        (domain/hydrate-units-with-stats
                      (domain/get-units-for-faction dependencies (:faction-eid draft)))
        unit-by-eid  (into {} (map (juxt :eid identity) units))
        units-by-cat (->> units
                          (partition-by :unit-category-name)
                          (mapv (fn [g] {:category (:unit-category-name (first g))
                                         :units    (vec (sort-by :cost g))})))
        state        (domain/get-draft-state dependencies (:eid draft))
        hydrate      (fn [entries]
                       (vec (keep (fn [entry]
                                    (when-let [u (get unit-by-eid (:unit-eid entry))]
                                      (assoc u
                                             :total-cost (or (:total-cost entry) (:cost u))
                                             :entry-eid  (:entry-eid entry))))
                                  entries)))
        main-units   (hydrate (:main state))
        reinf-units  (hydrate (:reinforcements state))
        main-ctx     (domain/build-section-context "main" main-units (:eid draft) game-mode)
        reinf-ctx    (domain/build-section-context "reinforcements" reinf-units (:eid draft) game-mode)]
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
  (fn [{session      :ory-session
        game-context :game-context
        :as          _request}]
    (let [player-sub (get-in session [:identity :id])]
      {:status 200
       :body   (selmer.parser/render-file
                "rts-web/view/my-drafts.html"
                (merge {:drafts  (domain/get-drafts-for-player-by-game dependencies player-sub (:game-eid game-context))
                        :session session}
                       game-context))})))

(defmethod integrant.core/init-key ::game-index-view
  [_init-key _dependencies]
  (fn [{game-context :game-context
        session      :ory-session
        :as          _request}]
    {:status 200
     :body   (selmer.parser/render-file
              "rts-web/view/game-index.html"
              (merge {:data    (:game game-context)
                      :session session}
                     game-context))}))

(defmethod integrant.core/init-key ::create-draft-view
  [_init-key dependencies]
  (fn [{game-context :game-context
        session      :ory-session
        :as          _request}]
    (let [game-modes (domain/get-game-modes-for-game dependencies (:game-eid game-context))]
      {:status 200
       :body   (selmer.parser/render-file
                "rts-web/view/create-draft.html"
                (merge {:game-modes game-modes
                        :session    session
                        :draft-eid  (random-uuid)}
                       game-context))})))

(defmethod integrant.core/init-key ::logout-view
  [_init-key {:keys [auth-hostname]}]
  (fn [request]
    (log/info (:ory-session request))
    {:status 301 :headers {"Location" (str auth-hostname "/self-service/logout?token=")}}))

(defmethod integrant.core/init-key ::tournament-list-view
  [_init-key dependencies]
  (fn [{game-context :game-context
        session      :ory-session
        :as          _request}]
    (let [tournaments (domain/get-tournaments-for-game dependencies (:game-eid game-context))
          enriched    (mapv (fn [t]
                              (let [state   (domain/get-tournament-state dependencies (:eid t))
                                    entries (domain/get-entries dependencies (:eid t))]
                                (assoc t
                                       :status      (:status state)
                                       :entry-count (count entries))))
                            tournaments)]
      {:status 200
       :body   (selmer.parser/render-file
                "rts-web/view/tournament-list.html"
                (merge {:tournaments enriched
                        :session     session}
                       game-context))})))

(def ^:private common-timezones
  "Curated list of IANA timezone IDs for the tournament create form."
  ["US/Eastern" "US/Central" "US/Mountain" "US/Pacific"
   "Europe/London" "Europe/Paris" "Europe/Berlin"
   "Asia/Tokyo" "Asia/Shanghai" "Australia/Sydney"
   "UTC"])

(defmethod integrant.core/init-key ::create-tournament-view
  [_init-key _dependencies]
  (fn [{game-context :game-context
        session      :ory-session
        :as          _request}]
    {:status 200
     :body   (selmer.parser/render-file
              "rts-web/view/create-tournament.html"
              (merge {:session          session
                      :tournament-eid   (random-uuid)
                      :timezones        common-timezones
                      :default-timezone "US/Eastern"}
                     game-context))}))

(defmethod integrant.core/init-key ::tournament-view
  [_init-key dependencies]
  (partial standard-entity-view-handler
           (fn [eid] (web.tournament/get-tournament-by-eid dependencies eid))
           "tournament-index.html"
           (fn [data request]
             (let [state        (domain/get-tournament-state dependencies (:eid data))
                   entries      (domain/get-entries dependencies (:eid data))
                   raw-matches  (domain/get-matches-for-tournament dependencies (:eid data))
                   phases           (:phases state)
                   matches-by-round (->> raw-matches
                                         (group-by (fn [m] {:phase (:phase-index m) :round (:round-index m)}))
                                         (sort-by (fn [[k _]] [(:phase k) (:round k)]))
                                         (mapv (fn [[k ms]]
                                                 (let [phase-config (get phases (:phase k))
                                                       total-rounds (count (:rounds phase-config))]
                                                   {:phase       (:phase k)
                                                    :round       (:round k)
                                                    :phase-type  (:phase-type phase-config)
                                                    :total-rounds total-rounds
                                                    :matches     ms}))))
                   player-sub   (get-in request [:ory-session :identity :id])
                   has-entry    (some #(= player-sub (:player-sub %)) entries)
                   now          (java.time.Instant/now)
                   reg-open     (domain/is-registration-open? state now)
                   is-organizer (= player-sub (:created-by-sub data))
                   organizer-has-actions (and is-organizer
                                              (contains? #{"registration" "active"} (:status state)))
                   pow2-ceil
                   (fn [n] (loop [s 1] (if (>= s n) s (recur (* s 2)))))
                   ceil-log2
                   (fn [n] (loop [s 1 k 0] (if (>= s n) k (recur (* s 2) (inc k)))))
                   expected-wb-match-count
                   (fn [qualifier-count round-idx]
                     (max 1 (quot (quot (pow2-ceil qualifier-count) 2)
                                  (int (Math/pow 2 round-idx)))))
                   expected-lb-match-count
                   (fn [qualifier-count round-idx]
                     (let [p     (pow2-ceil qualifier-count)
                           denom (int (Math/pow 2 (+ (quot round-idx 2) 2)))]
                       (max 1 (quot p denom))))
                   build-bracket
                   (fn [phase-idx phase-type total-rounds bracket-type bracket-matches round-count expected-count-fn]
                     (let [matches-grouped (group-by :round-index bracket-matches)]
                       (mapv (fn [round-idx]
                               (let [expected     (expected-count-fn round-idx)
                                     actual       (get matches-grouped round-idx [])
                                     empty-slots  (max 0 (- expected (count actual)))
                                     placeholders (repeat empty-slots {:placeholder    true
                                                                       :player-one-sub nil
                                                                       :player-two-sub nil
                                                                       :winner-sub     nil
                                                                       :status         "tbd"})]
                                 {:phase        phase-idx
                                  :round        round-idx
                                  :phase-type   phase-type
                                  :bracket-type bracket-type
                                  :total-rounds total-rounds
                                  :matches      (into (vec actual) placeholders)}))
                             (range round-count))))
                   ;; Group by phase for bracket rendering
                   ;; For elimination phases, build full bracket skeleton(s) from phase config.
                   ;; Double-elim yields winners, losers, and grand-final brackets.
                   matches-by-phase
                   (->> (map-indexed vector phases)
                        (filter (fn [[idx phase-config]]
                                  (or (some #(= idx (:phase-index %)) raw-matches)
                                      (#{"single-elimination" "double-elimination"} (:phase-type phase-config)))))
                        (mapv (fn [[phase-idx phase-config]]
                                (let [phase-type      (:phase-type phase-config)
                                      total-rounds    (count (:rounds phase-config))
                                      qualifier-count (or (:qualifier-count state) (count (:standings state)))
                                      phase-matches   (filter #(= phase-idx (:phase-index %)) raw-matches)]
                                  (cond
                                    (= "single-elimination" phase-type)
                                    {:phase        phase-idx
                                     :phase-type   phase-type
                                     :winners-bracket
                                     (build-bracket phase-idx phase-type total-rounds "winners"
                                                    phase-matches total-rounds
                                                    #(max 1 (quot (quot (pow2-ceil qualifier-count) 2) (int (Math/pow 2 %)))))}

                                    (= "double-elimination" phase-type)
                                    (let [wb-rounds (ceil-log2 qualifier-count)
                                          lb-rounds (max 0 (* 2 (dec wb-rounds)))
                                          wb-matches (filter #(= "winners" (or (:bracket-type %) "winners")) phase-matches)
                                          lb-matches (filter #(= "losers" (:bracket-type %)) phase-matches)
                                          gf-matches (filter #(= "grand-final" (:bracket-type %)) phase-matches)]
                                      {:phase        phase-idx
                                       :phase-type   phase-type
                                       :winners-bracket
                                       (build-bracket phase-idx phase-type wb-rounds "winners"
                                                      wb-matches wb-rounds
                                                      #(expected-wb-match-count qualifier-count %))
                                       :losers-bracket
                                       (build-bracket phase-idx phase-type lb-rounds "losers"
                                                      lb-matches lb-rounds
                                                      #(expected-lb-match-count qualifier-count %))
                                       :grand-final
                                       (build-bracket phase-idx phase-type 1 "grand-final"
                                                      gf-matches 1 (constantly 1))})

                                    :else
                                    {:phase      phase-idx
                                     :phase-type phase-type
                                     :rounds
                                     (mapv (fn [[_ rounds]] (first rounds))
                                           (sort-by first
                                                    (group-by :round
                                                              (filter #(= phase-idx (:phase %)) matches-by-round))))})))))]
               {:tournament-state  state
                :entries           entries
                :matches-by-round  matches-by-round
                :matches-by-phase  matches-by-phase
                :has-entry         has-entry
                :registration-open reg-open
                :is-organizer      is-organizer
                :organizer-has-actions organizer-has-actions}))))
