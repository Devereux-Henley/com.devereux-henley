(ns com.devereux-henley.rts-web.web.draft.view
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.render :as render]
   [com.devereux-henley.rts-web.web.game.api :as web.game.api]
   [com.devereux-henley.rts-web.web.view :as web.view]
   [integrant.core]))

(defn- build-draft-context
  [dependencies draft request]
  (let [game-mode    (domain/get-game-mode-by-eid dependencies (:game-mode-eid draft))
        faction      (domain/get-faction-by-eid dependencies (:faction-eid draft))
        units        (domain/hydrate-units-with-stats
                      (domain/get-units-for-faction dependencies (:faction-eid draft)))
        unit-by-eid  (into {} (map (juxt :eid identity) units))
        ;; Roster shows one card per family — five "Daemon Prince"
        ;; rows (one per mark) collapse into a single tile whose mark
        ;; switcher lives in the panel.  Each card carries its
        ;; canonical's portrait + cost; the per-mark variant eids ride
        ;; along on `:family-variants` for the panel + slot panel to
        ;; consume.
        families     (domain/group-units-by-family units)
        units-by-cat (->> families
                          (partition-by :unit-category-name)
                          (mapv (fn [g] {:category (:unit-category-name (first g))
                                         :units    (vec (sort-by :cost g))})))
        state        (domain/get-draft-state dependencies (:eid draft))
        ;; Each (mark, lore) variant is its own unit row with its own
        ;; portrait, so the entry's `:unit-eid` already points at the
        ;; correct image — no per-lore portrait override needed.
        hydrate      (fn [entries]
                       (vec (keep (fn [entry]
                                    (when-let [u (get unit-by-eid (:unit-eid entry))]
                                      (let [total     (or (:total-cost entry) (:cost u))
                                            engine    (:engine-cost entry)
                                            ;; The engine-cost audit flag fires only when we
                                            ;; have a parser-reported value AND it diverges
                                            ;; from what compute-unit-total-cost produced.
                                            ;; Manual-builder entries (no :engine-cost) skip
                                            ;; the check entirely.
                                            mismatch? (and engine total (not= engine total))]
                                        (assoc u
                                               :total-cost      total
                                               :entry-eid       (:entry-eid entry)
                                               :engine-cost     engine
                                               :cost-mismatch?  mismatch?))))
                                  entries)))
        main-units   (hydrate (:main state))
        reinf-units  (hydrate (:reinforcements state))
        main-ctx     (domain/build-section-context "main" main-units (:eid draft) game-mode)
        reinf-ctx    (domain/build-section-context "reinforcements" reinf-units (:eid draft) game-mode)
        ;; A draft becomes read-only when a tournament match references
        ;; it (one-way). The lock info carries the linking match + parent
        ;; tournament so the template can offer a back-link to the
        ;; context that locked the draft.
        lock         (domain/draft-lock-info dependencies (:eid draft))]
    {:faction                 faction
     :game-mode               game-mode
     :reinforcements-enabled  (= 1 (:reinforcements-enabled game-mode))
     :units-by-category       units-by-cat
     :main-section            main-ctx
     :reinf-section           reinf-ctx
     :game                    (:game (:game-context request))
     :draft-eid               (:eid draft)
     :locked?                 (some? lock)
     :locking-match-eid       (:match-eid lock)
     :locking-tournament-eid  (:tournament-eid lock)
     :locking-tournament-name (:tournament-name lock)}))

(defmethod integrant.core/init-key ::draft-view
  [_init-key dependencies]
  (partial web.view/standard-entity-view-handler
           (fn [eid] (web.game.api/get-draft-by-eid dependencies eid))
           "draft-index.html"
           (partial build-draft-context dependencies)))

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
       :body   (render/render "my-drafts.html"
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
       :body   (render/render "create-draft.html"
                              (assoc (web.view/base-context request)
                                     :game-modes game-modes
                                     :draft-eid  (random-uuid)))})))
