(ns com.devereux-henley.rts-data-access.schema.datalog.unit-level-cost
  "Datalevin attributes for the `:unit-level-cost` lookup table. Keyed by
  `:level`, which is both the identity and the only \"name\" it has — no
  public uuid because nothing routes to this resource by id.")

(def schema
  {:unit-level-cost/level           {:db/valueType :db.type/long
                                     :db/unique    :db.unique/identity}
   :unit-level-cost/fixed-cost      {:db/valueType :db.type/long}
   :unit-level-cost/cost-multiplier {:db/valueType :db.type/double}
   :unit-level-cost/fatigue         {:db/valueType :db.type/long}
   :unit-level-cost/melee-cp        {:db/valueType :db.type/double}
   :unit-level-cost/missile-cp      {:db/valueType :db.type/double}})
