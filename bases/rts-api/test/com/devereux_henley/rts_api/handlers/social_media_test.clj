(ns com.devereux-henley.rts-api.handlers.social-media-test
  (:require
   [clojure.test :refer :all]
   [com.devereux-henley.rts-api.db.social-media :as db.social-media]
   [com.devereux-henley.rts-api.handlers.social-media :as handlers.social-media])
  (:import
   [java.util UUID]))

(def ^:private test-deps {:connection nil})

;; --- get-platform-by-eid ---

(deftest get-platform-by-eid-assigns-type
  (let [eid (UUID/randomUUID)]
    (with-redefs [db.social-media/get-platform-by-eid (fn [_ _] {:eid eid :name "Discord"})]
      (let [result (handlers.social-media/get-platform-by-eid test-deps eid)]
        (is (= :social-media/platform (:type result)))))))

(deftest get-platform-by-eid-preserves-fields
  (let [eid (UUID/randomUUID)]
    (with-redefs [db.social-media/get-platform-by-eid (fn [_ _] {:eid eid :name "Discord" :platform-url "https://discord.com"})]
      (let [result (handlers.social-media/get-platform-by-eid test-deps eid)]
        (is (= eid (:eid result)))
        (is (= "Discord" (:name result)))
        (is (= "https://discord.com" (:platform-url result)))))))
