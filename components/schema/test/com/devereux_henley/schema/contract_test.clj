(ns com.devereux-henley.schema.contract-test
  (:require
   [clojure.test :refer [deftest is]]
   [com.devereux-henley.schema.contract :as schema.contract]
   [malli.core :as m]
   [malli.util]
   [reitit.core :as reitit]))

(deftest dummy-test
  (is (= 1 1)))

(def ^:private tournament-resource
  (malli.util/merge
   schema.contract/base-resource
   (schema.contract/to-schema
    [:map
     [:eid {:model/link :tournament/by-eid} :uuid]
     [:type [:= :tournament/tournament]]
     [:name :string]
     [:league-eid {:optional true :model/link :league/by-eid} :uuid]
     [:season-eid {:optional true :model/link :season/by-eid} :uuid]
     [:_links
      [:map
       [:self :url]
       [:league {:optional true} :url]
       [:season {:optional true} :url]]]])))

(def ^:private router
  (reitit/router
   [["/api/tournament/:eid" {:name :tournament/by-eid}]
    ["/api/league/:eid"     {:name :league/by-eid}]
    ["/api/season/:eid"     {:name :season/by-eid}]]))

(def ^:private route-data
  {:hostname "http://localhost:3001" :router router})

(defn- encode
  [value]
  (m/encode tournament-resource value (schema.contract/model-transformer route-data)))

(deftest model-transformer-strips-nil-linked-eids
  (let [eid    #uuid "11111111-1111-1111-1111-111111111111"
        result (encode {:type       :tournament/tournament
                        :eid        eid
                        :name       "Standalone"
                        :league-eid nil
                        :season-eid nil})]
    (is (not (contains? result :league-eid))
        "nil :league-eid should be dropped from the encoded resource")
    (is (not (contains? result :season-eid)))
    (is (not (contains? (:_links result) :league))
        "no :league link should be emitted when :league-eid is nil")
    (is (not (contains? (:_links result) :season)))
    (is (= (str "http://localhost:3001/api/tournament/" eid)
           (get-in result [:_links :self])))))

(deftest model-transformer-builds-links-for-non-nil-eids
  (let [eid        #uuid "11111111-1111-1111-1111-111111111111"
        league-eid #uuid "22222222-2222-2222-2222-222222222222"
        season-eid #uuid "33333333-3333-3333-3333-333333333333"
        result     (encode {:type       :tournament/tournament
                            :eid        eid
                            :name       "Attached"
                            :league-eid league-eid
                            :season-eid season-eid})]
    (is (= league-eid (:league-eid result)))
    (is (= season-eid (:season-eid result)))
    (is (= (str "http://localhost:3001/api/league/" league-eid)
           (get-in result [:_links :league])))
    (is (= (str "http://localhost:3001/api/season/" season-eid)
           (get-in result [:_links :season])))))
