(ns com.devereux-henley.content-negotiation.html
  (:require
   [muuntaja.format.core]
   [selmer.parser]
   [taoensso.timbre :as log]))

(def ^:private fallback-error-fragment
  "<section class=\"resource\" role=\"alert\" aria-labelledby=\"render-error-heading\">
  <p id=\"render-error-heading\">Something went wrong rendering this view.</p>
</section>")

(defn- safe-render
  "Renders a selmer template and returns the result as a string. On any
  exception logs and returns the fallback error fragment, so a broken template
  or data mismatch degrades to an inline HTMX-friendly error instead of
  crashing through the response encoder (which runs outside Reitit's exception
  middleware)."
  [view-fn data]
  (try
    (selmer.parser/render-file (view-fn (:type data)) {:data data})
    (catch Throwable t
      (log/error t "Template render failure"
                 {:type     (:type data)
                  :template (try (view-fn (:type data)) (catch Throwable _ nil))})
      fallback-error-fragment)))

(defn html-htmx-encoder
  [{:keys [view-fn]}]
  (reify
    muuntaja.format.core/EncodeToBytes
    (encode-to-bytes [_ data charset]
      (.getBytes ^String (safe-render view-fn data) ^String charset))
    muuntaja.format.core/EncodeToOutputStream
    (encode-to-output-stream [_ data charset]
      (fn [^java.io.OutputStream output-stream]
        (let [encoded (safe-render view-fn data)
              bytes   (.getBytes ^String encoded ^String charset)]
          (.write output-stream bytes))))))

(defn html-decoder
  [_options]
  (reify
    muuntaja.format.core/Decode
    (decode [_ data charset]
      (slurp (java.io.InputStreamReader. ^java.io.InputStream data ^String charset)))))

(defn- encode-html
  "/view handlers pre-render their templates and hand back a string body.
   /api handlers return a typed map and let the encoder dispatch through
   `view-fn` (per-content-type view registry) to pick the chrome-less
   resource template — text/html dispatches via the /api registry,
   application/htmx+html via the /components+/actions registry.
   Both kinds of body get the same text/html content type."
  [view-fn data]
  (if (map? data)
    (safe-render view-fn data)
    (do (log/trace data) data)))

(defn html-encoder
  [{:keys [view-fn]}]
  (reify
    muuntaja.format.core/EncodeToBytes
    (encode-to-bytes [_ data charset]
      (.getBytes ^String (encode-html view-fn data) ^String charset))
    muuntaja.format.core/EncodeToOutputStream
    (encode-to-output-stream [_ data charset]
      (fn [^java.io.OutputStream output-stream]
        (.write output-stream
                (.getBytes ^String (encode-html view-fn data) ^String charset))))))

(def html-htmx-format
  (muuntaja.format.core/map->Format
   {:name    "application/htmx+html"
    :decoder [html-decoder]
    :encoder [html-htmx-encoder]}))

(def html-format
  (muuntaja.format.core/map->Format
   {:name    "text/html"
    :decoder [html-decoder]
    :encoder [html-encoder]}))
