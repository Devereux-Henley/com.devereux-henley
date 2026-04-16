(require '[com.devereux-henley.rts-api.configuration :as config])
(require '[com.devereux-henley.rts-api.db :as db])
(require '[com.devereux-henley.rts-data.contract :as rts-data])
(require '[integrant.core :as ig])

(let [system (ig/init (ig/expand config/development-configuration))]
  (rts-data/seed-db db/db-spec)
  (println "Dev server started on port 3001")
  (.addShutdownHook (Runtime/getRuntime) (Thread. #(ig/halt! system)))
  @(promise))
