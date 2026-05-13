(ns com.devereux-henley.rts-web.render
  (:require
   [selmer.parser]))

(def ^:private view-prefix "rts-web/view/")
(def ^:private component-prefix "rts-web/components/")

(defn view-path
  "Returns the classpath template path for a view name like \"foo.html\"
   or \"tournament/phase-swiss.html\"."
  [view-name]
  (str view-prefix view-name))

(defn component-path
  "Returns the classpath template path for a component name like
   \"draft-unit-panel.html\" or \"icon/sword.html\"."
  [component-name]
  (str component-prefix component-name))

(defn render-view
  "Renders a full-page Selmer view template from `rts-web/view/` and
   returns the rendered string."
  [view-name context]
  (selmer.parser/render-file (view-path view-name) context))

(defn render-component
  "Renders a Selmer component template from `rts-web/components/` and
   returns the rendered string."
  [component-name context]
  (selmer.parser/render-file (component-path component-name) context))
