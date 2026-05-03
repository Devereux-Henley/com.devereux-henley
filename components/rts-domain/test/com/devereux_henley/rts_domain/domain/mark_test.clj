(ns com.devereux-henley.rts-domain.domain.mark-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.devereux-henley.rts-domain.domain.mark :as mark]))

(deftest mark-from-key-resolves-one-variant-per-mark
  (testing "WoC sorcerer / daemon prince `_m<god>` suffix"
    (is (= "khorne"   (mark/mark-from-key "wh3_dlc20_chs_cha_daemon_prince_mkho")))
    (is (= "nurgle"   (mark/mark-from-key "wh3_dlc25_chs_cha_chaos_sorcerer_nurgle_mnur")))
    (is (= "slaanesh" (mark/mark-from-key "wh3_dlc27_chs_cha_sorcerer_lord_slaanesh_msla")))
    (is (= "tzeentch" (mark/mark-from-key "wh3_dlc20_chs_cha_chaos_sorcerer_lord_tzeentch_mtze"))))
  (testing "DoC mono-god subfaction key infix `_<god>_`"
    (is (= "khorne"   (mark/mark-from-key "wh3_main_kho_inf_bloodletters_0")))
    (is (= "nurgle"   (mark/mark-from-key "wh3_main_nur_inf_plaguebearers_2")))
    (is (= "slaanesh" (mark/mark-from-key "wh3_main_sla_inf_daemonette_0")))
    (is (= "tzeentch" (mark/mark-from-key "wh3_main_pro_tze_inf_blue_horrors_0"))))
  (testing "Proper-god suffix"
    (is (= "khorne"   (mark/mark-from-key "wh3_main_dae_cha_daemon_prince_khorne")))
    (is (= "slaanesh" (mark/mark-from-key "wh3_main_sla_cha_alluress_slaanesh_0"))))
  (testing "Unmarked engine keys"
    (is (nil? (mark/mark-from-key nil)))
    (is (nil? (mark/mark-from-key "wh_main_emp_inf_halberdiers")))
    (is (nil? (mark/mark-from-key "wh3_main_dae_cha_daemon_prince_0")))))

(deftest mark-from-stat-attributes-resolves-one-variant-per-mark
  (testing "Each mark_<god> attribute resolves to its mark"
    (is (= "khorne"
           (mark/mark-from-stat-attributes
            ["causes_fear" "causes_terror" "daemonic" "encourages" "flying" "mark_khorne"])))
    (is (= "nurgle"
           (mark/mark-from-stat-attributes
            ["causes_fear" "daemonic" "mark_nurgle"])))
    (is (= "slaanesh"
           (mark/mark-from-stat-attributes
            ["devastating_flanker" "mark_slaanesh" "strider"])))
    (is (= "tzeentch"
           (mark/mark-from-stat-attributes
            ["flying" "mark_tzeentch"])))
    (is (= "undivided"
           (mark/mark-from-stat-attributes
            ["encourages" "mark_undivided"]))))
  (testing "Attribute lists with no mark return nil"
    (is (nil? (mark/mark-from-stat-attributes [])))
    (is (nil? (mark/mark-from-stat-attributes ["causes_fear" "encourages"])))))
