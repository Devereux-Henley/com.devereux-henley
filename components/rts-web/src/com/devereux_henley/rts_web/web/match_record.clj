(ns com.devereux-henley.rts-web.web.match-record
  "Web handlers for the post-match modal. Two-phase flow:

    1. POST /api/match/:match-eid/parse — multipart with N `.replay` files,
       parses each via the Rust binary, returns parsed JSON. No DB writes.
    2. POST /api/match/:match-eid/record — JSON body with the parsed data
       (echoed back from phase 1) plus per-game declared winner subs.
       Persists replays + match_game rows + match completion.

  The modal also serves an HTML fragment view for the modal itself."
  (:require
   [camel-snake-kebab.core :as csk]
   [camel-snake-kebab.extras :as cske]
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.render :as render]
   [integrant.core]))

(defn- parsed->snake
  "Internal kebab-case keys from the parser get round-tripped through the
  client (echoed back to the commit endpoint).  Emit snake_case to keep the
  JSON natural for the JS modal and to match the Rust binary's wire format."
  [parsed]
  (cske/transform-keys csk/->snake_case_string parsed))

(defn- collect-game-files
  "Pulls multipart parts named `game-0`, `game-1`, … in order, up to but
  not including the first index that has no part. Returns a vector of
  {:source-name <original filename> :file-path <tempfile abs path>}."
  [multipart-params]
  (loop [idx 0 acc []]
    (if-let [part (get multipart-params (str "game-" idx))]
      (recur (inc idx)
             (conj acc {:source-name (:filename part)
                        :file-path   (.getAbsolutePath (:tempfile part))}))
      acc)))

(defmethod integrant.core/init-key ::parse-replays
  [_init-key dependencies]
  (fn [{{{:keys [match-eid]} :path} :parameters
        multipart-params            :multipart-params
        :as                         _request}]
    (let [files (collect-game-files multipart-params)]
      (cond
        (empty? files)
        {:status 400
         :body   {:type    :match-record/error
                  :message "No game files supplied. Use multipart fields game-0, game-1, …"}}

        :else
        (try
          (let [parsed (domain/parse-replay-files dependencies (mapv :file-path files))]
            {:status 200
             :body   {:type      :match-record/parsed
                      :match-eid match-eid
                      :games     (mapv (fn [{:keys [source-name]} parsed-map]
                                         {:source-name source-name
                                          :parsed      (parsed->snake parsed-map)})
                                       files
                                       parsed)}})
          (catch Exception e
            {:status 422
             :body   {:type    :match-record/error
                      :message (str "Replay parse failed: " (.getMessage e))}}))))))

(defn- snake->kebab [m] (cske/transform-keys csk/->kebab-case-keyword m))

(defmethod integrant.core/init-key ::record-match
  [_init-key dependencies]
  (fn [{{{:keys [match-eid]} :path
         {:keys [games]}     :body} :parameters
        session                     :ory-session
        :as                         _request}]
    (let [submission {:games           (mapv (fn [{:keys [winner-sub parsed source-name]}]
                                               {:winner-sub  winner-sub
                                                :source-name source-name
                                                :parsed      (snake->kebab parsed)})
                                             games)
                      :uploaded-by-sub (get-in session [:identity :id])}
          result     (domain/record-match-from-parsed dependencies match-eid submission)]
      (case (:type result)
        :match-record/recorded {:status 201 :body result}
        :match-record/error    {:status 422 :body result}))))

(defmethod integrant.core/init-key ::modal-view
  [_init-key dependencies]
  (fn [{{{:keys [match-eid]} :path} :parameters
        session                     :ory-session
        :as                         _request}]
    (if-let [{:keys [match games]}
             (domain/get-record-context dependencies match-eid)]
      {:status 200
       :body   (render/render "match-record-modal.html"
                              {:match        match
                               :games        games
                               :viewer-sub   (get-in session [:identity :id])
                               :game-count   (:format match)
                               :game-indexes (vec (range (:format match)))})}
      {:status 404
       :body   {:type :missing/resource :name "match" :id match-eid}})))
