(ns com.devereux-henley.rts-domain.handlers.core)

(defn assign-model-type
  [model-type model]
  (when model
    (assoc model :type model-type)))
