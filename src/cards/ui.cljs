(ns cards.ui
  (:require [devcards.core :as dc :include-macros true]))

(defn start []
  (js/console.log "Start")
  (dc/start-devcard-ui!))

(js/console.log "UI")

(defn reload []
  (js/console.log "Reload"))