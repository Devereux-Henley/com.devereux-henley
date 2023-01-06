(ns com.devereux-henley.rts-api.handlers.core)

(defn assign-model-type
  [model-type model]
  (when model
    (assoc model :type model-type)))
