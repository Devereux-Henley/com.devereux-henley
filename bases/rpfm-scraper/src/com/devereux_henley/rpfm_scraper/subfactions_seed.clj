(ns com.devereux-henley.rpfm-scraper.subfactions-seed
  "Joins each playable subfaction back to its parent race via the
  `overrides/faction-key-map` slug prefixes, resolves display names from the
  factions loc, and generates stable subfaction eids. Used by catalog-edn to
  build the subfaction seed."
  (:require
   [clojure.string :as str]
   [com.devereux-henley.rpfm-scraper.overrides :as overrides])
  (:import
   [java.security MessageDigest]))

(def subfaction-uuid-namespace
  "Stable RFC 4122 namespace UUID for subfaction eid generation. Picked once
  and frozen so re-running the scraper produces identical eids for the same
  engine key."
  "1f7c0c3a-fa17-4d6f-8a41-2d3c2c3f5b9c")

(defn faction-slug-for-key
  "Maps an engine factions_tables key (e.g. `wh3_dlc23_chd_legion_of_azgorh`)
  to a race slug (e.g. `chaos-dwarfs`) by scanning for the
  `overrides/faction-key-map` infix prefixes inside the key. Returns nil
  when no prefix matches — those subfactions are skipped."
  [faction-key]
  (some (fn [[slug prefixes]]
          (when (some #(str/includes? faction-key (str "_" % "_")) prefixes)
            slug))
        overrides/faction-key-map))

(defn build-faction-display-name-map
  "{factions_tables key → display name} drawn from `factions_screen_name_<key>`
  loc entries. Falls back to the table row's `screen_name` literal when the
  loc lookup misses."
  [faction-rows faction-loc]
  (let [prefix "factions_screen_name_"]
    (reduce
     (fn [m row]
       (let [k        (get row "key")
             screen   (get row "screen_name")
             from-loc (get faction-loc (str prefix k))]
         (cond
           (and (seq k) (seq from-loc)) (assoc m k from-loc)
           (and (seq k) (seq screen))   (assoc m k screen)
           :else                        m)))
     {}
     faction-rows)))

(defn uuid-v5
  "Deterministic name-based UUID (RFC 4122 v5, SHA-1) for the given namespace
  + name string. Used so subfaction eids are stable across scraper re-runs."
  [namespace-uuid-str ^String name]
  (let [ns-uuid  (java.util.UUID/fromString namespace-uuid-str)
        ns-bytes (-> (java.nio.ByteBuffer/allocate 16)
                     (.putLong (.getMostSignificantBits ns-uuid))
                     (.putLong (.getLeastSignificantBits ns-uuid))
                     .array)
        digest   (doto (MessageDigest/getInstance "SHA-1")
                   (.update ns-bytes)
                   (.update (.getBytes name "UTF-8")))
        hash     (.digest digest)
        bs       (java.util.Arrays/copyOf hash 16)]
    (aset-byte bs 6 (unchecked-byte (bit-or 0x50 (bit-and (aget bs 6) 0x0f))))
    (aset-byte bs 8 (unchecked-byte (bit-or 0x80 (bit-and (aget bs 8) 0x3f))))
    (let [bb (java.nio.ByteBuffer/wrap bs)]
      (java.util.UUID. (.getLong bb) (.getLong bb)))))
