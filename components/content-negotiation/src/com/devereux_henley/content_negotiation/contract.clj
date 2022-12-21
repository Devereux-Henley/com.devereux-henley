(ns com.devereux-henley.content-negotiation.contract
  (:require
   [com.devereux-henley.content-negotiation.html :as html]))

(def html-format
  html/html-format)

(def html-htmx-format
  html/html-htmx-format)
