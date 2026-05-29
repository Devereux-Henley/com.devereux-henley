(ns com.devereux-henley.rts-data-access.query.datalog.replay
  "Datalevin reads + mutations for the replay domain."
  (:require
   [com.devereux-henley.datalog.contract :as dl])
  (:import
   [java.time Instant]
   [java.util Date]))

(def ^:private replay-pattern
  [:replay/eid :replay/match-id-external :replay/played-at
   :replay/victory-condition :replay/parser-format :replay/parsed-data
   :replay/uploader-local-alliance-index :replay/uploaded-by-sub
   :replay/created-at :replay/updated-at])

(defn- ->instant
  ^Instant [x]
  (cond
    (nil? x)              nil
    (instance? Instant x) x
    (instance? Date x)    (.toInstant ^Date x)))

(defn- ->replay
  [m]
  (when m
    {:eid                           (:replay/eid m)
     :match-id-external             (:replay/match-id-external m)
     :played-at                     (:replay/played-at m)
     :victory-condition             (:replay/victory-condition m)
     :parser-format                 (:replay/parser-format m)
     :parsed-data                   (:replay/parsed-data m)
     :uploader-local-alliance-index (:replay/uploader-local-alliance-index m)
     :uploaded-by-sub               (:replay/uploaded-by-sub m)
     :created-at                    (->instant (:replay/created-at m))
     :updated-at                    (->instant (:replay/updated-at m))}))

(defn replay-by-eid
  "Fetch a replay by eid. Returns nil when not found."
  [conn eid]
  (->replay (dl/pull (dl/db conn) replay-pattern (dl/lookup-ref :replay/eid eid))))

(defn- ->date
  ^Date [x]
  (cond
    (instance? Date x)    x
    (instance? Instant x) (Date/from ^Instant x)))

(defn create-replay!
  "Transact a new replay row. Audit columns (`:created-at`,
  `:updated-at`) are stamped here so the handler stays thin. The
  caller has already converted `:parsed-data` from the parser-emitted
  JSON to a Clojure map; Datalevin stores it as `:db.type/idoc`."
  [conn {:keys [eid match-id-external played-at victory-condition parser-format
                parsed-data uploader-local-alliance-index uploaded-by-sub]}]
  (let [now (Date.)
        tx  (cond-> {:replay/eid               eid
                     :replay/match-id-external match-id-external
                     :replay/played-at         played-at
                     :replay/parser-format     parser-format
                     :replay/parsed-data       parsed-data
                     :replay/uploaded-by-sub   uploaded-by-sub
                     :replay/created-at        now
                     :replay/updated-at        now}
              victory-condition             (assoc :replay/victory-condition victory-condition)
              (some? uploader-local-alliance-index)
              (assoc :replay/uploader-local-alliance-index uploader-local-alliance-index))]
    (dl/transact! conn [tx])
    (replay-by-eid conn eid)))
