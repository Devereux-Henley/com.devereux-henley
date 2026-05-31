(ns com.devereux-henley.rts-data.contract
  "Datalog seed loading for the API. Each patch lives under
  `rts-data/seed/datalog/<patch-version>/` as one EDN file per entity, produced
  by the `rpfm-scraper` base. See docs/rpfm-scraper/edn-seed-pipeline.md."
  (:require
   [com.devereux-henley.rts-data.datalog-seed :as datalog-seed]))

(def datalog-seed-files          datalog-seed/seed-files)
(def load-datalog-seed-file      datalog-seed/load-file-tx)
(def load-datalog-seed           datalog-seed/load-all)
(def ensure-datalog-patch        datalog-seed/ensure-patch-version)
(def available-datalog-patches   datalog-seed/available-patches)
