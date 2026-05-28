(ns com.devereux-henley.rts-data-access.schema.datalog.patch
  "Datalevin attributes for the `:patch` entity — the engine patch (e.g.
  `\"4.0.1\"`) a given `:unit-statistics` snapshot was scraped against. The
  seed loader inserts one `:patch` per RPFM run and links every freshly
  scraped `:unit-statistics` to it.

  `:patch/version` is the display string. Use `:patch/released-at`
  (not the version string) to order patches — lexicographic comparison on
  semver-style strings is wrong as soon as any component reaches double
  digits (`\"4.10.0\"` sorts before `\"4.2.0\"`). The canonical
  \"latest stats for unit X\" query is:

    (datalog/q '[:find (pull ?s […])
                 :in $ ?unit-eid
                 :where
                 [?u :unit/eid ?unit-eid]
                 [?u :unit/unit-statistics ?s]
                 [?s :unit-statistics/patch ?p]
                 [?p :patch/released-at ?ts]
                 [(q '[:find (max ?ts2) .
                       :in $ ?u
                       :where
                       [?u :unit/unit-statistics ?s2]
                       [?s2 :unit-statistics/patch ?p2]
                       [?p2 :patch/released-at ?ts2]]
                     $ ?u) ?max-ts]
                 [(= ?ts ?max-ts)]]
               db unit-eid)

  For the common \"everyone's on the current patch\" view, take the global
  max once and pass it in as a filter:

    (let [latest (datalog/q '[:find (max ?ts) .
                              :where [_ :patch/released-at ?ts]] db)]
      (datalog/q '[…  :in $ ?unit-eid ?latest
                   …  [?p :patch/released-at ?latest]] db unit-eid latest))")

(def schema
  {:patch/eid         {:db/valueType :db.type/uuid
                       :db/unique    :db.unique/identity}
   :patch/version     {:db/valueType :db.type/string
                       :db/unique    :db.unique/identity}
   :patch/released-at {:db/valueType :db.type/instant}})
