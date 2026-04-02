(ns com.devereux-henley.rts-data-deploy.migrations-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [migratus.core :as migratus]
   [next.jdbc :as jdbc])
  (:import
   [java.io File]))

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn- temp-db-file ^File []
  (doto (File/createTempFile "rts-data-test" ".db")
    (.deleteOnExit)))

(defn- migratus-config [^File db-file]
  {:store         :database
   :migration-dir "rts-data/migrations"
   :db            {:connection-uri (str "jdbc:sqlite:" (.getAbsolutePath db-file))}})

(defn- table-exists? [conn table-name]
  (some? (jdbc/execute-one! conn ["SELECT name FROM sqlite_master WHERE type='table' AND name=?" table-name])))

(defmacro with-temp-db
  "Binds `config-sym` to a fresh migratus config backed by a temp SQLite file,
  and `conn-sym` to a jdbc connection for assertions. Deletes the database file
  after the body completes, whether or not an exception is thrown."
  [[config-sym conn-sym] & body]
  `(let [f#          (temp-db-file)
         ~config-sym (migratus-config f#)]
     (try
       (with-open [~conn-sym (jdbc/get-connection
                              {:connection-uri (str "jdbc:sqlite:" (.getAbsolutePath f#))})]
         ~@body)
       (finally
         (.delete f#)))))

;; ---------------------------------------------------------------------------
;; Individual migration tests
;; SQLite does not enforce foreign keys by default, so each migration can be
;; exercised in isolation without applying its prerequisites first.
;; ---------------------------------------------------------------------------

(deftest migration-000001-game-table
  (with-temp-db [cfg conn]
    (testing "up creates game table"
      (migratus/up cfg 1)
      (is (table-exists? conn "game")))
    (testing "down drops game table"
      (migratus/down cfg 1)
      (is (not (table-exists? conn "game"))))))

(deftest migration-000002-social-media-platform-table
  (with-temp-db [cfg conn]
    (testing "up creates social_media_platform table"
      (migratus/up cfg 2)
      (is (table-exists? conn "social_media_platform")))
    (testing "down drops social_media_platform table"
      (migratus/down cfg 2)
      (is (not (table-exists? conn "social_media_platform"))))))

(deftest migration-000003-unit-type-table
  (with-temp-db [cfg conn]
    (testing "up creates unit_type table"
      (migratus/up cfg 3)
      (is (table-exists? conn "unit_type")))
    (testing "down drops unit_type table"
      (migratus/down cfg 3)
      (is (not (table-exists? conn "unit_type"))))))

(deftest migration-000004-unit-category-table
  (with-temp-db [cfg conn]
    (testing "up creates unit_category table"
      (migratus/up cfg 4)
      (is (table-exists? conn "unit_category")))
    (testing "down drops unit_category table"
      (migratus/down cfg 4)
      (is (not (table-exists? conn "unit_category"))))))

(deftest migration-000005-faction-table
  (with-temp-db [cfg conn]
    (testing "up creates faction table"
      (migratus/up cfg 5)
      (is (table-exists? conn "faction")))
    (testing "down drops faction table"
      (migratus/down cfg 5)
      (is (not (table-exists? conn "faction"))))))

(deftest migration-000006-unit-table
  (with-temp-db [cfg conn]
    (testing "up creates unit table"
      (migratus/up cfg 6)
      (is (table-exists? conn "unit")))
    (testing "down drops unit table"
      (migratus/down cfg 6)
      (is (not (table-exists? conn "unit"))))))

(deftest migration-000007-game-social-link-table
  (with-temp-db [cfg conn]
    (testing "up creates game_social_link table"
      (migratus/up cfg 7)
      (is (table-exists? conn "game_social_link")))
    (testing "down drops game_social_link table"
      (migratus/down cfg 7)
      (is (not (table-exists? conn "game_social_link"))))))

(deftest migration-000008-game-mode-table
  (with-temp-db [cfg conn]
    (testing "up creates game_mode table"
      (migratus/up cfg 8)
      (is (table-exists? conn "game_mode")))
    (testing "down drops game_mode table"
      (migratus/down cfg 8)
      (is (not (table-exists? conn "game_mode"))))))

(deftest migration-000009-draft-table
  (with-temp-db [cfg conn]
    (testing "up creates draft table"
      (migratus/up cfg 9)
      (is (table-exists? conn "draft")))
    (testing "down drops draft table"
      (migratus/down cfg 9)
      (is (not (table-exists? conn "draft"))))))

;; ---------------------------------------------------------------------------
;; Full migration cycle
;; ---------------------------------------------------------------------------

(def ^:private all-tables
  ["game"
   "social_media_platform"
   "unit_type"
   "unit_category"
   "faction"
   "unit"
   "game_social_link"
   "game_mode"
   "draft"])

(deftest full-migration-cycle
  (with-temp-db [cfg conn]
    (testing "migrate applies all migrations"
      (migratus/migrate cfg)
      (doseq [table all-tables]
        (is (table-exists? conn table) (str table " should exist after migrate"))))

    (testing "rollback to baseline removes all tables"
      (migratus/down cfg 9 8 7 6 5 4 3 2 1)
      (doseq [table all-tables]
        (is (not (table-exists? conn table)) (str table " should not exist after full rollback"))))))
