(ns com.devereux-henley.rpfm-scraper.rpfm
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]))

(defn- cell-val
  "Extract the scalar value from an RPFM typed cell — a one-key dict."
  [cell]
  (when (map? cell)
    (-> cell first val)))

(defn- unwrap-mcp
  "RPFM MCP output wraps tables as [{\"type\" ..., \"text\" <json-string>}].
  If the parsed JSON matches that shape, re-parse the inner text; otherwise
  return the parsed object as-is."
  [parsed]
  (if (and (sequential? parsed)
           (seq parsed)
           (map? (first parsed))
           (contains? (first parsed) "text"))
    (json/read-str (get (first parsed) "text"))
    parsed))

(defn- read-json [filepath]
  (with-open [r (io/reader filepath)]
    (unwrap-mcp (json/read r))))

(defn parse-rpfm-table
  "Parse an RPFM-decoded DB table file. Returns {:fields [...], :rows [...]}
  where each row is a map of field-name → scalar value."
  [filepath]
  (let [obj     (read-json filepath)
        info    (or (get obj "DBRFileInfo") (get obj "LocRFileInfo"))
        entry   (first info)
        table   (get entry "table")
        fields  (mapv #(get % "name") (get-in table ["definition" "fields"]))
        rows    (mapv (fn [row]
                        (into {} (map-indexed
                                  (fn [i cell] [(nth fields i) (cell-val cell)])
                                  row)))
                      (get table "table_data"))]
    {:fields fields :rows rows}))

(defn parse-loc-file
  "Parse an RPFM-decoded .loc file into {loc-key → text}."
  [filepath]
  (let [obj   (read-json filepath)
        info  (get obj "LocRFileInfo")
        entry (first info)
        table (get entry "table")]
    (into {}
          (map (fn [row]
                 [(cell-val (nth row 0)) (cell-val (nth row 1))]))
          (get table "table_data"))))
