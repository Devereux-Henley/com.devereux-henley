(ns com.devereux-henley.rpfm-scraper.name-match-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.devereux-henley.rpfm-scraper.name-match :as nm]))

(deftest mark-from-display-name-recognises-each-mark
  (testing "'X of <God>' suffix"
    (is (= "khorne"   (nm/mark-from-display-name "Daemon Prince of Khorne")))
    (is (= "nurgle"   (nm/mark-from-display-name "Chaos Sorcerer of Nurgle")))
    (is (= "slaanesh" (nm/mark-from-display-name "Chaos Sorcerer Lord of Slaanesh")))
    (is (= "tzeentch" (nm/mark-from-display-name "Herald of Tzeentch"))))
  (testing "'X (<God>)' suffix"
    (is (= "khorne"   (nm/mark-from-display-name "Chaos Furies (Khorne)")))
    (is (= "tzeentch" (nm/mark-from-display-name "Chaos Furies (Tzeentch)"))))
  (testing "Unmarked / non-mark display names"
    (is (nil? (nm/mark-from-display-name "Daemon Prince")))
    (is (nil? (nm/mark-from-display-name "Bloodletters of Khorne (something else)")))
    (is (nil? (nm/mark-from-display-name "Karl Franz")))
    (is (nil? (nm/mark-from-display-name nil)))))

(deftest mark-from-key-recognises-each-suffix-convention
  (testing "WoC `_m<short>` suffix"
    (is (= "khorne"   (nm/mark-from-key "wh3_dlc20_chs_cha_daemon_prince_mkho")))
    (is (= "nurgle"   (nm/mark-from-key "wh3_dlc25_chs_cha_chaos_sorcerer_nurgle_mnur")))
    (is (= "slaanesh" (nm/mark-from-key "wh3_dlc27_chs_cha_sorcerer_lord_slaanesh_msla")))
    (is (= "tzeentch" (nm/mark-from-key "wh3_dlc20_chs_cha_chaos_sorcerer_lord_tzeentch_mtze"))))
  (testing "Mono-god subfaction `_<short>_` infix"
    (is (= "khorne"   (nm/mark-from-key "wh3_main_kho_inf_bloodletters_0")))
    (is (= "nurgle"   (nm/mark-from-key "wh3_main_nur_inf_plaguebearers_0")))
    (is (= "slaanesh" (nm/mark-from-key "wh3_main_sla_inf_daemonette_0")))
    (is (= "tzeentch" (nm/mark-from-key "wh3_main_pro_tze_inf_blue_horrors_0"))))
  (testing "Proper-god `_<full-god>` suffix"
    (is (= "tzeentch" (nm/mark-from-key "wh3_main_tze_cha_iridescent_horror_tzeentch_0")))
    (is (= "slaanesh" (nm/mark-from-key "wh3_main_sla_cha_alluress_slaanesh_0"))))
  (testing "Unmarked engine keys"
    (is (nil? (nm/mark-from-key nil)))
    (is (nil? (nm/mark-from-key "wh_main_emp_inf_halberdiers")))
    (is (nil? (nm/mark-from-key "wh3_main_dae_cha_daemon_prince_0")))))

(deftest find-unit-key-resolves-mark-variants-without-overrides
  (testing "Mark-only variant (composed display name not in loc table)"
    ;; "Chaos Sorcerer of Tzeentch" is a runtime-composed string CA's loc
    ;; doesn't contain.  The base "Chaos Sorcerer" has 4 candidates (one
    ;; per mark) and the resolver picks the Tzeentch one mechanically.
    (let [name-index {"Chaos Sorcerer"
                      [["wh3_dlc20_chs_cha_chaos_sorcerer_nurgle_mnur"   "lu_nurgle"]
                       ["wh3_dlc20_chs_cha_chaos_sorcerer_slaanesh_msla" "lu_slaanesh"]
                       ["wh3_dlc20_chs_cha_chaos_sorcerer_tzeentch_mtze" "lu_tzeentch"]]}]
      (is (= ["wh3_dlc20_chs_cha_chaos_sorcerer_tzeentch_mtze" "lu_tzeentch"]
             (nm/find-unit-key "Chaos Sorcerer of Tzeentch" ["chs"] name-index)))
      (is (= ["wh3_dlc20_chs_cha_chaos_sorcerer_nurgle_mnur" "lu_nurgle"]
             (nm/find-unit-key "Chaos Sorcerer of Nurgle" ["chs"] name-index)))
      (is (= ["wh3_dlc20_chs_cha_chaos_sorcerer_slaanesh_msla" "lu_slaanesh"]
             (nm/find-unit-key "Chaos Sorcerer of Slaanesh" ["chs"] name-index)))))
  (testing "Mark variant in loc with multiple candidates — mark filter narrows before faction filter"
    (let [name-index {"Daemon Prince of Khorne"
                      [["wh3_main_dae_cha_daemon_prince_0"            "lu_unmarked"]
                       ["wh3_dlc20_chs_cha_daemon_prince_mkho"        "lu_khorne_chs"]
                       ["wh3_main_kho_cha_daemon_prince_khorne"       "lu_khorne_kho"]]}]
      ;; Mark filter drops the unmarked candidate; faction filter then
      ;; picks the WoC-scoped variant (`_chs_`) over the Khorne mono-god
      ;; one because the seed file is being processed under "chs".
      (is (= ["wh3_dlc20_chs_cha_daemon_prince_mkho" "lu_khorne_chs"]
             (nm/find-unit-key "Daemon Prince of Khorne" ["chs"] name-index)))
      ;; Same data, processed under the Khorne mono-god seed: faction
      ;; filter picks the `_kho_` candidate instead.
      (is (= ["wh3_main_kho_cha_daemon_prince_khorne" "lu_khorne_kho"]
             (nm/find-unit-key "Daemon Prince of Khorne" ["kho"] name-index)))))
  (testing "Parenthesised mark falls through to base + filter"
    (let [name-index {"Chaos Furies"
                      [["wh3_main_dae_inf_chaos_furies_0"            "lu_base"]
                       ["wh3_main_kho_inf_chaos_furies_khorne_0"     "lu_khorne"]
                       ["wh3_main_tze_inf_chaos_furies_tzeentch_0"   "lu_tzeentch"]]}]
      (is (= ["wh3_main_kho_inf_chaos_furies_khorne_0" "lu_khorne"]
             (nm/find-unit-key "Chaos Furies (Khorne)" ["dae"] name-index)))))
  (testing "Unmarked display name still resolves via existing single-candidate path"
    (let [name-index {"Halberdiers" [["wh_main_emp_inf_halberdiers" "lu_emp"]]}]
      (is (= ["wh_main_emp_inf_halberdiers" "lu_emp"]
             (nm/find-unit-key "Halberdiers" ["emp"] name-index)))))
  (testing "Empty index without mark suffix falls through to override map"
    ;; Sanity: the override path is preserved for non-mark generic names.
    (is (= ["wh_main_brt_cha_damsel_0" nil]
           (nm/find-unit-key "Damsel" ["brt"] {})))))
