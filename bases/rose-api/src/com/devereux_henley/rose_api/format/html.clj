(ns com.devereux-henley.rose-api.format.html
  (:require
   [muuntaja.format.core]
   [selmer.parser]))

(defn html-htmx-encoder
  [{:keys [view-fn]}]
  (reify
    muuntaja.format.core/EncodeToBytes
    (encode-to-bytes [_ data charset]
      (.getBytes ^String (selmer.parser/render-file (view-fn (:type data)) data) ^String charset))
    muuntaja.format.core/EncodeToOutputStream
    (encode-to-output-stream [_ data charset]
      (fn [^java.io.OutputStream output-stream]
        (let [encoded (selmer.parser/render-file (view-fn (:type data)) data)
              bytes   (.getBytes ^String encoded ^String charset)]
          (.write output-stream bytes))))))

(defn html-decoder
  [_options]
  (reify
    muuntaja.format.core/Decode
    (decode [_ data charset]
      (slurp (java.io.InputStreamReader. ^java.io.InputStream data ^String charset)))))

(defn html-encoder
  [_options]
  (reify
    muuntaja.format.core/EncodeToBytes
    (encode-to-bytes [_ data charset]
      (println data)
      (.getBytes ^String data ^String charset))
    muuntaja.format.core/EncodeToOutputStream
    (encode-to-output-stream [_ data charset]
      (fn [^java.io.OutputStream output-stream]
        (.write output-stream (.getBytes ^String data ^String charset))))))

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
