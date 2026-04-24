(ns com.devereux-henley.rts-web.render
  (:require
   [selmer.parser]))

(def ^:private view-prefix "rts-web/view/")

(defn view-path
  "Returns the classpath template path for a view name like \"foo.html\".
   Use this when only the path is needed (e.g. for a dispatch map). For
   one-shot rendering, prefer `render`."
  [view-name]
  (str view-prefix view-name))

(defn render
  "Renders a Selmer view template by name (relative to the rts-web view
   root) and returns the rendered string."
  [view-name context]
  (selmer.parser/render-file (view-path view-name) context))
