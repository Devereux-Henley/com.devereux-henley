(ns build
  (:require [clojure.tools.build.api :as b]))

(def ^:private class-dir "target/classes")
(def ^:private uber-file "target/rts-api.jar")
(def ^:private basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (let [basis    @basis
        src-dirs (->> (:classpath-roots basis)
                      (filter #(.isDirectory (java.io.File. %))))]
    (b/copy-dir {:src-dirs   src-dirs
                 :target-dir class-dir})
    (b/compile-clj {:basis     basis
                    :src-dirs  src-dirs
                    :class-dir class-dir})
    (b/uber {:class-dir class-dir
             :uber-file uber-file
             :basis     basis
             :main      'com.devereux-henley.rts-api.core})))
