(ns thonix.main
  (:require [untangled.client.core :as core]
            [thonix.app :as app]
            [thonix.core :as thonix]
            [untangled.client.core :as core]))

(reset! thonix/app (core/mount @thonix/app app/Root "app"))

(defn reload [])
