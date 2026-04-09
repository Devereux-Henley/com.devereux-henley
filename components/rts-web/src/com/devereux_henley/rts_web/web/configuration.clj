(ns com.devereux-henley.rts-web.web.configuration
  (:require
   [integrant.core]))

(defmethod integrant.core/expand-key ::configuration
  [_init-key configuration]
  configuration)

(defmethod integrant.core/init-key ::auth-hostname
  [_init-key hostname]
  hostname)

(defmethod integrant.core/init-key ::openid-url
  [_init-key url]
  url)
