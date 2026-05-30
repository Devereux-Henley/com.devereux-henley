(ns com.devereux-henley.rts-web.web.draft.view
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.render :as render]
   [com.devereux-henley.rts-web.web.view :as web.view]
   [integrant.core]))

(defmethod integrant.core/init-key ::draft-view
  [_init-key dependencies]
  (fn [request]
    (web.view/render-entity-view request "draft-index.html"
                                 (domain/draft-view-model dependencies (-> request :parameters :path :eid)))))

(defmethod integrant.core/init-key ::my-drafts-view
  [_init-key dependencies]
  (fn [{session :ory-session :as request}]
    (let [player-sub     (get-in session [:identity :id])
          game-eid       (:game-eid (:game-context request))
          active-faction (not-empty (get-in request [:parameters :query :faction]))
          all-drafts     (domain/get-drafts-for-player-by-game dependencies player-sub game-eid)
          faction-counts (->> all-drafts
                              (group-by :faction-name)
                              (mapv (fn [[name drafts]] {:name name :count (count drafts)}))
                              (sort-by (juxt (comp - :count) :name))
                              vec)
          drafts         (if active-faction
                           (filterv #(= active-faction (:faction-name %)) all-drafts)
                           all-drafts)]
      {:status 200
       :body   (render/render-view "my-drafts.html"
                                   (assoc (web.view/base-context request)
                                          :drafts drafts
                                          :faction-counts faction-counts
                                          :active-faction active-faction
                                          :total-count (count all-drafts)))})))

(defmethod integrant.core/init-key ::create-draft-view
  [_init-key dependencies]
  (fn [request]
    (let [game-eid   (:game-eid (:game-context request))
          game-modes (domain/get-game-modes-for-game dependencies game-eid)]
      {:status 200
       :body   (render/render-view "create-draft.html"
                                   (assoc (web.view/base-context request)
                                          :game-modes game-modes
                                          :draft-eid  (random-uuid)))})))
