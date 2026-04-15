(ns com.devereux-henley.rts-web.web.view
  (:require
   [clojure.java.io :as io]
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.web.game :as web.game]
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
                        :session    session}
                       game-context))})))

(defmethod integrant.core/init-key ::logout-view
  [_init-key {:keys [auth-hostname]}]
  (fn [request]
    (log/info (:ory-session request))
    {:status 301 :headers {"Location" (str auth-hostname "/self-service/logout?token=")}}))
