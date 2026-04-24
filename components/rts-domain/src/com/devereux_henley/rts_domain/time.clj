(ns com.devereux-henley.rts-domain.time
  (:import
   [java.time Instant LocalDateTime ZoneId]))

(defn to-utc-instant
  "Converts a LocalDateTime in the given ZoneId to a UTC Instant."
  ^Instant [^LocalDateTime local-dt ^ZoneId zone]
  (-> local-dt (.atZone zone) .toInstant))
