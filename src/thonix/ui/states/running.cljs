(ns thonix.ui.states.running
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [thonix.ui.routing :as routing]
            [untangled.client.core :as uc])
  (:require-macros [thonix.macros :as m]))

(defui ^:once MenuItem
  static om/IQuery
  (query [this]
    [:icon-url :title :route])
  Object
  (clicked [this ev]
    (let [route (:route (om/props this))]
      (routing/nav-to! this route)))
  (render [this]
    (let [{:keys [icon-url title]} (om/props this)]
      (dom/button #js {:className "menu-item" :key title :type "button"
                       :onClick #(.clicked this %)}
        (dom/img #js {:src icon-url :className "icon" :key "icon"})
        (dom/div #js {:className "title"} title)))))

(def menu-item (om/factory MenuItem))

(defui ^:once Menu
  static uc/InitialAppState
  (initial-state [clz params]
    {:page :running/menu :apps [{:icon-url "apps/status.png" :title "Status" :route :running/status}
                                {:icon-url "apps/status.png" :title "Storage" :route :app/drives}
                                {:icon-url "apps/status.png" :title "Photos" :route :app/drives}
                                {:icon-url "apps/status.png" :title "App Store" :route :app/drives}
                                {:icon-url "apps/status.png" :title "Backup" :route :app/drives}
                                {:icon-url "apps/status.png" :title "Files" :route :app/drives}
                                {:icon-url "apps/status.png" :title "Sharing" :route :app/drives}
                                {:icon-url "apps/status.png" :title "Devices" :route :app/drives}
                                {:icon-url "apps/status.png" :title "Docker" :route :app/drives}
                                {:icon-url "apps/status.png" :title "Sandstorm" :route :app/drives}
                                {:icon-url "apps/status.png" :title "Plex" :route :app/drives}]})
  static om/IQuery
  (query [this]
    [:page {:apps (om/get-query MenuItem)}])
  Object
  (render [this]
    (let [{:keys [apps]} (om/props this)]
      (dom/div #js {:className "main-menu" :key "main-menu"}
        (dom/div #js {:className "menu-item-container"}
          (map menu-item apps))))))

(m/defrouter RunningRouter :running
  (ident [this props] [(:page props) :running])
  :running/menu Menu)

(m/defpassthrough RunningState :running RunningRouter)

#_(comment
  (defmethod thonix/read :app/menu
    [{:keys [state query] :as env} key params]
    (let [st @state]
      {:value (om/db->tree query (get st key) st)})))
