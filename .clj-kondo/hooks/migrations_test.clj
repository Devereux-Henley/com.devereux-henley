(ns hooks.migrations-test
  (:require [clj-kondo.hooks-api :as api]))

(defn with-temp-db [{:keys [node]}]
  (let [[_ bindings & body] (:children node)
        [cfg-node conn-node] (:children bindings)]
    {:node (api/list-node
            (list* (api/token-node 'let)
                   (api/vector-node [cfg-node (api/token-node nil)
                                     conn-node (api/token-node nil)])
                   body))}))
