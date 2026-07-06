(require '[datalevin.core :as d])
(require '[com.devereux-henley.rts-api.configuration :as config])
(require '[com.devereux-henley.rts-data.contract :as rts-data])
(require '[integrant.core :as ig])

(def datalog-patch-version
  "Patch the e2e dev server seeds Datalevin against. Bump alongside the seed
  EDN under `components/rts-data/resources/rts-data/seed/datalog/<v>/`."
  "8.0")

(let [system (ig/init (ig/expand config/development-configuration))]
  (let [conn (get system :com.devereux-henley.rts-api.datalog/connection)]
    (rts-data/ensure-datalog-patch datalog-patch-version)
    (doseq [[_ tx-data] (rts-data/load-datalog-seed datalog-patch-version)]
      (d/transact! conn tx-data))
    (println "Datalevin seeded from patch" datalog-patch-version))
  (println "Dev server started on port 3001")
  (.addShutdownHook (Runtime/getRuntime) (Thread. #(ig/halt! system)))
  @(promise))
