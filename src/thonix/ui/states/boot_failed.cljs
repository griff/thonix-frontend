(ns thonix.ui.states.boot-failed
  (:require [thonix.ui.helpers :as ui]
            [om.dom :as dom]
            [thonix.ui.routing :as routing]
            [om.next :as om :refer-macros [defui]]
            [untangled.client.core :as uc]
            [cljs.core.async :refer [put! <! >!]]
            [thonix.ui.components.drives :as drives]
            [thonix.ui.components.shell :as shell]
            [thonix.ui.components.log :as log])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [thonix.macros :as m]))

(defn open-shell! [app]
  (routing/nav-to! app :boot-failed/shell)
  (let [[in out] (shell/open-shell! app [:boot-failed/shell :boot-failed :shell])]
    (go-loop []
             (when-let [msg (<! out)]
               (js/console.log msg)
               (when (>! in msg)
                 (recur))))
    (put! in "Welcome to xterm.js\r\n")
    (put! in "This is a local terminal emulation, without a real terminal in the back-end.\r\n")
    (put! in "Type some keys and commands to play around.\r\n\r\n$ ")))


(defui ^:once FailMenu
  static uc/InitialAppState
  (initial-state [clz params] {:page :boot-failed/menu})
  static om/IQuery
  (query [this] [:page])
  Object
  (viewLog [this ev]
    (routing/nav-to! this :boot-failed/log))
  (startShell [this ev]
    (open-shell! this))
  (driveManager [this ev]
    (routing/nav-to! this :boot-failed/file-systems))
  (render [this]
    (dom/div #js {:id "boot" :style #js {:margin "0 1.5em"}}
      (dom/h1 #js {:key "title"} "ThoNix")
      (dom/h4 #js {:key "subtitle"} "boot failed")
      (dom/div #js {:style #js {:textAlign "center"}}
        (dom/div #js {:className "btn-group-vertical" :role "group" :aria-label "Menu"}
          (ui/button "View Bootlog" #(.viewLog this %))
          (ui/button "Drivemanager" #(.driveManager this %))
          (ui/button "Start Shell" #(.startShell this %)))))))

(defui ^:once ViewLog
  static uc/InitialAppState
  (initial-state [clz params]
    {:page :boot-failed/log
     :dmesg (uc/get-initial-state log/DmesgLog {})})
  static om/IQuery
  (query [this]
    (let [dmesg (om/get-query log/DmesgLog)]
      [:page {:dmesg dmesg}]))
  Object
  (back [this ev]
    (routing/nav-to! this :boot-failed/menu))
  (render [this]
    (let [{:keys [dmesg]} (om/props this)]
      (dom/div #js {:className "with-menu"}
        (ui/header
          (ui/back-button "Menu" #(.back this %))
          (dom/h2 #js {:key "title"} "Bootlog"))
        (log/ui-dmesg-log dmesg)))))

(defui ^:once BootShell
  static uc/InitialAppState
  (initial-state [clz params] {:page :boot-failed/shell})
  static om/IQuery
  (query [this]
    [:page {:shell (om/get-query shell/Shell)}])
  Object
  (back [this ev]
    (routing/nav-to! this :boot-failed/menu))
  (render [this]
    (dom/div #js {:className "with-menu"}
      (ui/header
        (ui/back-button "Menu" #(.back this %))
        (dom/h2 #js {:key "title"} "Init shell"))
      (shell/shell (:shell (om/props this))))))

(defui ^:once DriveStatus
  static uc/InitialAppState
  (initial-state [clz params] {:page :boot-failed/drives :drives []})
  static om/IQuery
  (query [this]
    [:page {[:drives '_] (om/get-query drives/Drive)}])
  Object
  (back [this ev]
    (routing/nav-to! this :boot-failed/menu))
  (fileSystems [this ev]
    (routing/nav-to! this :boot-failed/file-systems))
  (render [this]
    (let [{:keys [drives]} (om/props this)]
      (dom/div #js {:className "with-menu"}
        (ui/header
          (ui/back-button "Menu" #(.back this %))
          (dom/h2 #js {:key "title"} "Drives"))
        (drives/drives drives)
        (ui/footer
          (dom/div #js {:key "btn-group" :className "btn-group btn-group-justified" :role "group" :aria-label "Menu"}
            (dom/div #js {:className "btn-group" :role "group" :key "Physical Drives"}
              (ui/button "Physical Drives" identity {:className "btn-default active"}))
            (dom/div #js {:className "btn-group" :key "File Systems" :role "group"}
              (ui/button "File Systems" #(.fileSystems this %)))))))))

(defui ^:once FileSystemStatus
  static uc/InitialAppState
  (initial-state [clz params] {:page :boot-failed/file-systems})
  static om/IQuery
  (query [this] [:page])
  Object
  (back [this ev]
    (routing/nav-to! this :boot-failed/menu))
  (drives [this ev]
    (routing/nav-to! this :boot-failed/drives))
  (render [this]
    (dom/div #js {:className "with-menu"}
      (ui/header
        (ui/back-button "Menu" #(.back this %))
        (dom/h2 #js {:key "title"} "FS"))
      (dom/div #js {:key "content"}
        "File system status")
      (ui/footer
        (dom/div #js {:key "btn-group" :className "btn-group btn-group-justified" :role "group" :aria-label "Menu"}
          (dom/div #js {:className "btn-group" :role "group" :key "Physical Drives"}
            (ui/button "Physical Drives" #(.drives this %)))
          (dom/div #js {:className "btn-group" :key "File Systems" :role "group"}
            (ui/button "File Systems" identity {:className "btn-default active"})))))))

(m/defrouter BootFailedRouter :boot-failed
  (ident [this props] [(:page props) :boot-failed])
  :boot-failed/menu FailMenu
  :boot-failed/log ViewLog
  :boot-failed/drives DriveStatus
  :boot-failed/file-systems FileSystemStatus
  :boot-failed/shell BootShell)

(m/defpassthrough BootFailedState :boot-failed BootFailedRouter)
