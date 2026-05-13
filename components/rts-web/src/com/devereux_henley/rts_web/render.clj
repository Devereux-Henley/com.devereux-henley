(ns com.devereux-henley.rts-web.render
  "Selmer template rendering. The rts-web/ resource root has five
  parallel subdirectories — view/, components/, template/, resource/,
  asset/ — mirroring the URL surface (full pages, htmx fragments,
  Selmer partials, /api resources, static files). Callers pass the
  full sub-path so the target surface is explicit at every render site."
  (:require
   [selmer.parser]))

(def ^:private resource-prefix "rts-web/")

(defn view-path
  "Returns the classpath template path for a template name like
   \"view/foo.html\" or \"components/bar.html\". Use this when only the
   path is needed (e.g. for a dispatch map). For one-shot rendering,
   prefer `render`."
  [view-name]
  (str resource-prefix view-name))

(defn render
  "Renders a Selmer template by name (relative to the rts-web resource
   root) and returns the rendered string. Caller specifies the surface
   in the name: \"view/foo.html\" / \"components/bar.html\" /
   \"template/baz.html\"."
  [view-name context]
  (selmer.parser/render-file (view-path view-name) context))
