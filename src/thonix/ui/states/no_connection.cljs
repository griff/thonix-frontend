(ns thonix.ui.states.no-connection
  (:require [om.next :as om :refer-macros [defui]]
            [untangled.client.core :as uc]
            [om.dom :as dom]))

(defui ^:once Connecting
  static uc/InitialAppState
  (initial-state [clz params]
    {:page :no-connection})
  static om/IQuery
  (query [this]
    [:page])
  Object
  (render [this]
    (dom/div #js {:id "boot" :style #js {:margin "0 1.5em"}}
      (dom/h1 #js {:key "title"} "ThoNix")
      (dom/h4 #js {:key "subtitle"} "connecting to server"))))