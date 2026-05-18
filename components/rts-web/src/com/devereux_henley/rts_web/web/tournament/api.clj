(ns com.devereux-henley.rts-web.web.tournament.api
  (:require
   [com.devereux-henley.http.contract :as web.core]
   [com.devereux-henley.rts-domain.contract :as domain]
   [integrant.core]
   [reitit.core]))

(defn get-tournament-by-eid
  [dependencies eid]
  (or (domain/get-tournament-by-eid dependencies eid)
      {:type :missing/resource :name "tournament" :id eid}))

(defmethod integrant.core/init-key ::get-tournament
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        :as                   _request}]
    (let [result (get-tournament-by-eid dependencies eid)]
      (if (= :missing/resource (:type result))
        {:status 404 :body result}
        {:status 200 :body result}))))

(defmethod integrant.core/init-key ::get-tournaments
  [_init-key dependencies]
  (fn [{{{:keys [game-eid]} :query} :parameters
        router                      :reitit.core/router
        :as                         _request}]
    {:status 200
     :body   {:type      :collection/tournament
              :_embedded {:results (if game-eid
                                     (domain/get-tournaments-for-game dependencies game-eid)
                                     (domain/get-tournaments dependencies))}
              :_links    {:self (str (:hostname dependencies)
                                     (-> router
                                         (reitit.core/match-by-name! :collection/tournament)
                                         (reitit.core/match->path
                                          (when game-eid {:game-eid game-eid}))))}}}))

(defmethod integrant.core/init-key ::create-tournament
  [_init-key dependencies]
  (fn [{{{:keys [name description game-eid league-eid season-eid timezone
                 registration-opens-at registration-closes-at]}           :body
         {:keys [version]}                                                :query
         {:keys [eid]}                                                    :path} :parameters
        router                                                                   :reitit.core/router
        session                                                                  :ory-session
        :as                                                                      _request}]
    (let [result (domain/create-tournament
                  dependencies
                  (cond-> {:eid                    eid
                           :game-eid               game-eid
                           :name                   name
                           :description            description
                           :timezone               timezone
                           :registration-opens-at  registration-opens-at
                           :registration-closes-at registration-closes-at
                           :created-by-sub         (get-in session [:identity :id])
                           :version                version}
                    league-eid (assoc :league-eid league-eid)
                    season-eid (assoc :season-eid season-eid)))]
      (if (= :tournament/create-error (:type result))
        {:status 422 :body result}
        (let [response (web.core/handle-create-response
                        domain/tournament-resource
                        {:hostname (:hostname dependencies) :router router}
                        (constantly result))]
          (assoc-in response [:headers "HX-Redirect"]
                    (str "/view/game/" game-eid "/tournament/" eid "/index.html")))))))

(defmethod integrant.core/init-key ::create-entry
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        session               :ory-session
        :as                   _request}]
    (let [player-sub (get-in session [:identity :id])
          result     (domain/create-entry dependencies eid player-sub)]
      (if (= :tournament/entry-error (:type result))
        {:status 422 :body result}
        {:status  201
         :headers {"HX-Refresh" "true"}
         :body    result}))))

(defmethod integrant.core/init-key ::delete-entry
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        session               :ory-session
        :as                   _request}]
    (let [player-sub (get-in session [:identity :id])
          result     (domain/delete-entry dependencies eid player-sub)]
      (if (= :tournament/entry-error (:type result))
        {:status 422 :body result}
        {:status  200
         :headers {"HX-Refresh" "true"}
         :body    result}))))

(defmethod integrant.core/init-key ::get-entries
  [_init-key dependencies]
  (fn [{{{:keys [tournament-eid]} :query} :parameters
        :as                               _request}]
    {:status 200
     :body   {:type           :tournament/entries
              :tournament-eid tournament-eid
              :entries        (domain/get-entries dependencies tournament-eid)}}))

(defmethod integrant.core/init-key ::get-status
  [_init-key dependencies]
  (fn [{{{:keys [tournament-eid]} :query} :parameters
        :as                               _request}]
    (let [state (domain/get-tournament-state dependencies tournament-eid)]
      {:status 200
       :body   {:type                  :tournament/status
                :tournament-eid        tournament-eid
                :status                (:status state)
                :available-transitions (vec (domain/available-transitions dependencies tournament-eid))}})))

(defmethod integrant.core/init-key ::start-tournament
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        session               :ory-session
        :as                   _request}]
    (let [user-sub (get-in session [:identity :id])
          result   (domain/start-tournament dependencies eid user-sub)]
      (case (:type result)
        :tournament/started {:status 200 :body result}
        {:status 422 :body result}))))

(defmethod integrant.core/init-key ::complete-tournament
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        session               :ory-session
        :as                   _request}]
    (let [user-sub (get-in session [:identity :id])
          result   (domain/complete-tournament dependencies eid user-sub)]
      (case (:type result)
        :tournament/completed {:status 200 :body result}
        {:status 422 :body result}))))

(defmethod integrant.core/init-key ::cancel-tournament
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        session               :ory-session
        :as                   _request}]
    (let [user-sub (get-in session [:identity :id])
          result   (domain/cancel-tournament dependencies eid user-sub)]
      (case (:type result)
        :tournament/cancelled {:status 200 :body result}
        {:status 422 :body result}))))

(defmethod integrant.core/init-key ::get-registration
  [_init-key dependencies]
  (fn [{{{:keys [tournament-eid]} :query} :parameters
        :as                               _request}]
    (let [state (domain/get-tournament-state dependencies tournament-eid)]
      {:status 200
       :body   {:type           :tournament/registration
                :tournament-eid tournament-eid
                :opens-at       (get-in state [:registration :opens-at])
                :closes-at      (get-in state [:registration :closes-at])
                :timezone       (get-in state [:registration :timezone])
                :closed-early   (get-in state [:registration :closed-early])}})))

(defmethod integrant.core/init-key ::close-registration
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        session               :ory-session
        :as                   _request}]
    (let [user-sub (get-in session [:identity :id])
          result   (domain/close-registration-early dependencies eid user-sub)]
      (case (:type result)
        :tournament/registration-closed {:status 200 :body result}
        {:status 422 :body result}))))

;; ─── Match handlers ─────────────────────────────────────────────────────────

(defmethod integrant.core/init-key ::get-matches
  [_init-key dependencies]
  (fn [{{{:keys [tournament-eid]} :query} :parameters
        :as                               _request}]
    {:status 200
     :body   {:type           :collection/match
              :tournament-eid tournament-eid
              :matches        (if tournament-eid
                                (domain/get-matches-for-tournament dependencies tournament-eid)
                                (domain/get-matches dependencies))}}))

(defmethod integrant.core/init-key ::get-match
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        :as                   _request}]
    (if-let [match (domain/get-match-by-eid dependencies eid)]
      {:status 200 :body match}
      {:status 404 :body {:type :missing/resource :name "match" :id eid}})))

(defmethod integrant.core/init-key ::create-match
  [_init-key dependencies]
  (fn [{{{:keys [eid]}                                  :path
         {:keys [phase-index round-index
                 player-one-sub player-two-sub format]} :body} :parameters
        :as                                                    _request}]
    (let [result (domain/create-match
                  dependencies
                  eid
                  (cond-> {:phase-index    phase-index
                           :round-index    round-index
                           :player-one-sub player-one-sub
                           :player-two-sub player-two-sub}
                    format (assoc :format format)))]
      (if (= :tournament/match-error (:type result))
        {:status 422 :body result}
        {:status 201 :body result}))))

(defmethod integrant.core/init-key ::update-match-result
  [_init-key dependencies]
  (fn [{{{:keys [match-eid]}  :path
         {:keys [winner-sub]} :body} :parameters
        :as                          _request}]
    (let [result (domain/update-match-result dependencies match-eid winner-sub)]
      (if (= :tournament/match-error (:type result))
        {:status 422 :body result}
        {:status 200 :body result}))))

;; ─── Game handlers ──────────────────────────────────────────────────────────

(defmethod integrant.core/init-key ::record-game
  [_init-key dependencies]
  (fn [{{{:keys [match-eid]}  :path
         {:keys [winner-sub]} :body} :parameters
        :as                          _request}]
    (let [result (domain/record-game-result dependencies match-eid winner-sub)]
      (if (= :tournament/match-error (:type result))
        {:status 422 :body result}
        {:status 200 :body result}))))

(defmethod integrant.core/init-key ::get-games
  [_init-key dependencies]
  (fn [{{{:keys [match-eid]} :query} :parameters
        :as                          _request}]
    (let [match (domain/get-match-by-eid dependencies match-eid)]
      {:status 200
       :body   (cond-> {:type      :collection/match-game
                        :match-eid match-eid
                        :games     (domain/get-games-for-match dependencies match-eid)}
                 (:tournament-eid match)
                 (assoc :tournament-eid (:tournament-eid match)))})))

;; ─── Phase handlers ─────────────────────────────────────────────────────────

(defmethod integrant.core/init-key ::update-phase-configuration
  [_init-key dependencies]
  (fn [{{{:keys [tournament-eid]}         :query
         {:keys [phases qualifier-count]} :body} :parameters
        session                                  :ory-session
        :as                                      _request}]
    (let [user-sub (get-in session [:identity :id])
          result   (domain/configure-phases dependencies tournament-eid
                                            {:phases phases :qualifier-count qualifier-count}
                                            user-sub)]
      (if (= :tournament/phase-error (:type result))
        {:status 422 :body result}
        {:status 200 :body result}))))

(defmethod integrant.core/init-key ::create-round
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        session               :ory-session
        :as                   _request}]
    (let [user-sub (get-in session [:identity :id])
          result   (domain/generate-next-round dependencies eid user-sub)]
      (if (= :tournament/phase-error (:type result))
        {:status 422 :body result}
        {:status 200 :body result}))))

(defn attach-lineups-to-matches
  "Walks each round-bucket inside a phase-group and replaces every match
  slot with one carrying `:lineups` — the per-game pair of draft eids
  recorded against `match_game`. Drafts are auto-created on match-record
  submit, so completed matches end up with one lineup row per game."
  [phase-group lineups-by-match-eid]
  (let [decorate-match  (fn [m]
                          (if-let [lineups (get lineups-by-match-eid (:eid m))]
                            (assoc m :lineups lineups)
                            m))
        decorate-round  (fn [r] (update r :matches #(mapv decorate-match %)))
        decorate-bucket #(when (seq %) (mapv decorate-round %))]
    (cond-> phase-group
      (:rounds phase-group)          (update :rounds          decorate-bucket)
      (:winners-bracket phase-group) (update :winners-bracket  decorate-bucket)
      (:losers-bracket phase-group)  (update :losers-bracket   decorate-bucket)
      (:grand-final phase-group)     (update :grand-final      decorate-bucket))))

(defn build-phase-context
  "Gathers the phase-group, tournament state, and parent game-eid for one
  phase. Returns nil when no phase with `phase-index` exists. Shared by
  the /api handler (text/html resource view) and the /components
  fragment view (htmx panel)."
  [dependencies tournament-eid phase-index]
  (let [state           (domain/get-tournament-state dependencies tournament-eid)
        phases          (:phases state)
        raw-matches     (domain/get-matches-for-tournament dependencies tournament-eid)
        qualifier-count (or (:qualifier-count state) (count (:standings state)))
        grouped         (domain/group-matches-by-phase raw-matches phases qualifier-count)
        phase-group-raw (first (filter #(= phase-index (:phase %)) grouped))
        real-matches    (filter :eid raw-matches)
        lineups         (into {}
                              (map (fn [m]
                                     [(:eid m)
                                      (domain/get-games-for-match dependencies (:eid m))]))
                              real-matches)]
    (when phase-group-raw
      {:tournament-state state
       :phase-group      (attach-lineups-to-matches phase-group-raw lineups)
       :game-eid         (:game-eid (get-tournament-by-eid dependencies tournament-eid))})))

(defmethod integrant.core/init-key ::get-phase
  [_init-key dependencies]
  (fn [{{{:keys [tournament-eid phase-index]} :query} :parameters
        :as                                           _request}]
    (if-let [ctx (build-phase-context dependencies tournament-eid phase-index)]
      {:status 200
       :body   (assoc ctx
                      :type :tournament/phase
                      :tournament-eid tournament-eid
                      :data {:eid tournament-eid})}
      {:status 404
       :body   {:type :missing/resource :name "tournament-phase" :id phase-index}})))

(defmethod integrant.core/init-key ::get-round
  [_init-key _dependencies]
  (fn [{{{:keys [tournament-eid]} :query} :parameters
        :as                               _request}]
    {:status 200
     :body   {:type           :tournament/round
              :tournament-eid tournament-eid}}))
