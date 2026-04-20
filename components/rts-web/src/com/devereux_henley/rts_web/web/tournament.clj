(ns com.devereux-henley.rts-web.web.tournament
  (:require
   [com.devereux-henley.http.contract :as web.core]
   [com.devereux-henley.rts-domain.contract :as domain]
   [integrant.core]
   [reitit.core]))

(defn get-tournament-by-eid
  [dependencies eid]
  (or (domain/get-tournament-by-eid dependencies eid)
      {:type :missing/resource :name "tournament" :id eid}))

(defn get-tournaments-for-game
  [dependencies game-eid {:keys [hostname router]}]
  {:type      :collection/tournament
   :_embedded {:results (domain/get-tournaments-for-game dependencies game-eid)}
   :_links    {:self (str hostname
                          (-> router
                              (reitit.core/match-by-name! :tournament/for-game)
                              (reitit.core/match->path {:game-eid game-eid})))}})

(defmethod integrant.core/init-key ::get-tournament
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        router                :reitit.core/router
        :as                   _request}]
    (web.core/handle-fetch-response
     domain/tournament-resource
     {:hostname (:hostname dependencies) :router router}
     #(get-tournament-by-eid dependencies eid))))

(defmethod integrant.core/init-key ::get-tournaments
  [_init-key dependencies]
  (fn [{{{:keys [game-eid]} :query} :parameters
        router                      :reitit.core/router
        :as                         _request}]
    (web.core/handle-fetch-response
     domain/tournament-collection-resource
     {:hostname (:hostname dependencies) :router router}
     #(get-tournaments-for-game dependencies game-eid
                                {:hostname (:hostname dependencies) :router router}))))

(defmethod integrant.core/init-key ::create-tournament
  [_init-key dependencies]
  (fn [{{{:keys [name description game-eid timezone
                 registration-opens-at registration-closes-at]} :body
         {:keys [version]}                                      :query
         {:keys [eid]}                                          :path} :parameters
        router                                                         :reitit.core/router
        session                                                        :ory-session
        :as                                                            _request}]
    (let [response (web.core/handle-create-response
                    domain/tournament-resource
                    {:hostname (:hostname dependencies) :router router}
                    #(domain/create-tournament
                      dependencies
                      {:eid                    eid
                       :game-eid               game-eid
                       :name                   name
                       :description            description
                       :timezone               timezone
                       :registration-opens-at  registration-opens-at
                       :registration-closes-at registration-closes-at
                       :created-by-sub         (get-in session [:identity :id])
                       :version                version}))]
      (assoc-in response [:headers "HX-Redirect"]
                (str "/view/game/" game-eid "/tournament/" eid "/index.html")))))

(defmethod integrant.core/init-key ::create-entry
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        session               :ory-session
        :as                   _request}]
    (let [player-sub (get-in session [:identity :id])
          result     (domain/create-entry dependencies eid player-sub)]
      (if (= :tournament/entry-error (:type result))
        {:status 422 :body result}
        {:status 201 :body result}))))

(defmethod integrant.core/init-key ::delete-entry
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        session               :ory-session
        :as                   _request}]
    (let [player-sub (get-in session [:identity :id])
          result     (domain/delete-entry dependencies eid player-sub)]
      (if (= :tournament/entry-error (:type result))
        {:status 422 :body result}
        {:status 200 :body result}))))

(defmethod integrant.core/init-key ::get-entries
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        :as                   _request}]
    {:status 200
     :body   {:type    :tournament/entries
              :entries (domain/get-entries dependencies eid)}}))

(defmethod integrant.core/init-key ::get-status
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        :as                   _request}]
    (let [state (domain/get-tournament-state dependencies eid)]
      {:status 200
       :body   {:type                  :tournament/status
                :status                (:status state)
                :available-transitions (vec (domain/available-transitions dependencies eid))}})))

(defmethod integrant.core/init-key ::update-status
  [_init-key dependencies]
  (fn [{{{:keys [eid]}    :path
         {:keys [status]} :body} :parameters
        session                  :ory-session
        :as                      _request}]
    (let [player-sub (get-in session [:identity :id])
          result     (domain/advance-tournament dependencies eid status player-sub)]
      (case (:type result)
        :tournament/advance-success {:status 200 :body result}
        {:status 422 :body result}))))

(defmethod integrant.core/init-key ::get-registration
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        :as                   _request}]
    (let [state (domain/get-tournament-state dependencies eid)]
      {:status 200
       :body   {:type         :tournament/registration
                :opens-at     (get-in state [:registration :opens-at])
                :closes-at    (get-in state [:registration :closes-at])
                :timezone     (get-in state [:registration :timezone])
                :closed-early (get-in state [:registration :closed-early])}})))

(defmethod integrant.core/init-key ::update-registration
  [_init-key dependencies]
  (fn [{{{:keys [eid]}          :path
         {:keys [closed-early]} :body} :parameters
        session                        :ory-session
        :as                            _request}]
    (let [player-sub (get-in session [:identity :id])
          result     (if closed-early
                       (domain/close-registration-early dependencies eid player-sub)
                       {:type :tournament/registration-error :message "No updates specified."})]
      (case (:type result)
        :tournament/close-registration-success {:status 200 :body result}
        {:status 422 :body result}))))

;; ─── Match handlers ─────────────────────────────────────────────────────────

(defmethod integrant.core/init-key ::get-matches
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        :as                   _request}]
    {:status 200
     :body   {:type    :tournament/matches
              :matches (domain/get-matches-for-tournament dependencies eid)}}))

(defmethod integrant.core/init-key ::get-match
  [_init-key dependencies]
  (fn [{{{:keys [match-eid]} :path} :parameters
        router                      :reitit.core/router
        :as                         _request}]
    (web.core/handle-fetch-response
     domain/match-resource
     {:hostname (:hostname dependencies) :router router}
     #(or (domain/get-match-by-eid dependencies match-eid)
          {:type :missing/resource :name "match" :id match-eid}))))

(defmethod integrant.core/init-key ::create-match
  [_init-key dependencies]
  (fn [{{{:keys [eid]}                           :path
         {:keys [phase-index round-index
                 player-one-sub player-two-sub]} :body} :parameters
        :as                                             _request}]
    (let [result (domain/create-match
                  dependencies
                  eid
                  {:phase-index    phase-index
                   :round-index    round-index
                   :player-one-sub player-one-sub
                   :player-two-sub player-two-sub})]
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
  (fn [{{{:keys [match-eid]} :path} :parameters
        :as                         _request}]
    {:status 200
     :body   {:type  :tournament/games
              :games (domain/get-games-for-match dependencies match-eid)}}))

;; ─── Phase handlers ─────────────────────────────────────────────────────────

(defmethod integrant.core/init-key ::update-phase-configuration
  [_init-key dependencies]
  (fn [{{{:keys [eid]}                    :path
         {:keys [phases qualifier-count]} :body} :parameters
        session                                  :ory-session
        :as                                      _request}]
    (let [player-sub (get-in session [:identity :id])
          result     (domain/configure-phases dependencies eid
                                              {:phases phases :qualifier-count qualifier-count}
                                              player-sub)]
      (if (= :tournament/phase-error (:type result))
        {:status 422 :body result}
        {:status 200 :body result}))))

(defmethod integrant.core/init-key ::create-round
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        session               :ory-session
        :as                   _request}]
    (let [player-sub (get-in session [:identity :id])
          result     (domain/generate-next-round dependencies eid player-sub)]
      (if (= :tournament/phase-error (:type result))
        {:status 422 :body result}
        {:status 200 :body result}))))

(defmethod integrant.core/init-key ::get-phase
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        router                :reitit.core/router
        :as                   _request}]
    (web.core/handle-fetch-response
     domain/phase-response
     {:hostname (:hostname dependencies) :router router}
     (fn [] {:type           :tournament/phase
             :tournament-eid eid}))))

(defmethod integrant.core/init-key ::get-phase-panel
  [_init-key dependencies]
  (fn [{{{:keys [eid phase-index]} :path} :parameters}]
    (let [state           (domain/get-tournament-state dependencies eid)
          phases          (:phases state)
          raw-matches     (domain/get-matches-for-tournament dependencies eid)
          qualifier-count (or (:qualifier-count state) (count (:standings state)))
          grouped         (domain/group-matches-by-phase raw-matches phases qualifier-count)
          phase-group     (first (filter #(= phase-index (:phase %)) grouped))]
      (if phase-group
        {:status 200
         :body   {:type             :tournament/phase-panel
                  :tournament-state state
                  :phase-group      phase-group
                  :data             {:eid eid}}}
        {:status 404
         :body   {:type :missing/resource :name "tournament-phase" :id phase-index}}))))

(defmethod integrant.core/init-key ::get-round
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        router                :reitit.core/router
        :as                   _request}]
    (web.core/handle-fetch-response
     domain/round-response
     {:hostname (:hostname dependencies) :router router}
     (fn [] {:type           :tournament/round
             :tournament-eid eid}))))
