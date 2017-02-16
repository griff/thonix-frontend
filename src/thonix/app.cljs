(ns thonix.app
  (:require [goog.dom :as gdom]
            [thonix.ui.states.booting :as booting]
            [thonix.ui.states.boot-failed :as boot-failed]
            [om.dom :as dom]
            [om.next :as om :refer-macros [defui]]
            [thonix.ui.components.log :as thonix-log]
            [compassus.core :as compassus]
            [thonix.core :as thonix]
            [thonix.ui.routing :as routing]
            [thonix.ui.states.running :as running]
            [thonix.ui.states.no-connection :as nc]
            [untangled.client.core :as uc]
            [untangled.client.logging :as log]
            [untangled.client.core :as core]
            [cognitect.transit :as t])
  (:require-macros [thonix.macros :as m]))

(m/defrouter StateRouter :state
  (ident [this props] [(:page props) :state])
  :no-connection nc/Connecting
  :booting booting/BootingState
  :running running/RunningState
  :boot-failed boot-failed/BootFailedState)

(def ui-router (om/factory StateRouter))

(defui ^:once Root
  static uc/InitialAppState
  (initial-state [clz params]
    {:ui/react-key "initial"
     :state        (uc/get-initial-state StateRouter nil)})
  static om/IQuery
  (query [this]
    [:ui/react-key {:state (om/get-query StateRouter)}])
  Object
  (render [this]
    (let [{:keys [ui/react-key state]} (om/props this)]
      (js/console.log "Render" (om/props this) (om/full-query this))
      (dom/div #js {:key react-key}
        (ui-router state)))))

(comment
  (enable-console-print!)

  ((om/parser {:read (fn [{:keys [query]} dk params]
                       (log/info "Query" query)
                       {:remote (om/query->ast [:hello])})}) {} [:test {:te [:ll]}] :remote)


  #_(def route->component
      {:boot/connecting        Connecting
       :boot/default           boot/Default
       :boot/details           boot/Details
       :boot-fail/menu         boot/FailMenu
       :boot-fail/log          boot/ViewLog
       :boot-fail/shell        boot/BootShell
       :boot-fail/drives       boot/DriveStatus
       :boot-fail/file-systems boot/FileSystemStatus
       :app/install            boot/Default
       :app/menu               running/Menu})

  #_(def state->default-route
      {:no-connection :boot/connecting
       :booting       :boot/default
       :boot-failed   :boot-fail/drives
       :installation  :app/install
       :running       :app/menu})


  #_(def init-data
      {:logblock/by-id  {}
       :boot            {:now 0 :total 10 :current nil}
       :boot/default    {}
       :boot/details    {}
       :server/state    :running
       :app/state-route state->default-route
       :dmesg           []
       :app/menu        {:apps [{:icon-url "apps/status.png" :title "Status" :route :app/drives}
                                {:icon-url "apps/status.png" :title "Storage" :route :app/drives}
                                {:icon-url "apps/status.png" :title "Photos" :route :app/drives}
                                {:icon-url "apps/status.png" :title "App Store" :route :app/drives}
                                {:icon-url "apps/status.png" :title "Backup" :route :app/drives}
                                {:icon-url "apps/status.png" :title "Files" :route :app/drives}
                                {:icon-url "apps/status.png" :title "Sharing" :route :app/drives}
                                {:icon-url "apps/status.png" :title "Devices" :route :app/drives}
                                {:icon-url "apps/status.png" :title "Docker" :route :app/drives}
                                {:icon-url "apps/status.png" :title "Sandstorm" :route :app/drives}
                                {:icon-url "apps/status.png" :title "Plex" :route :app/drives}]}
       :drives/by-path  {"/dev/sda" {:path            "/dev/sda" :vendor "WD" :name "NAS Red" :size 1345550000000 :ok false
                                     :show-attributes true
                                     :attributes      [{:label "Bus" :content "SATA"}
                                                       {:label "Serial" :content "123423kjkj234jh234"}]}
                         "/dev/sdb" {:path            "/dev/sdb"
                                     :vendor          "WD" :name "NAS Red" :size 1345550000000 :ok true
                                     :show-attributes false
                                     :attributes      [{:label "Bus" :content "SATA"}
                                                       {:label "Serial" :content "14jh234"}]}
                         "/dev/sdd" {:path            "/dev/sdd"
                                     :vendor          "Seagate" :name "Barracuda" :size 4045550000000 :ok true
                                     :show-attributes false
                                     :attributes      [{:label "Bus" :content "USB"}
                                                       {:label "Serial" :content "2423kjkj234jh234"}]}}
       :drives          [[:drives/by-path "/dev/sda"]
                         [:drives/by-path "/dev/sdb"]
                         [:drives/by-path "/dev/sdd"]]})

  #_(defonce reconciler
      (om/reconciler
        {:state  (atom init-data)
         :parser thonix/parser}))

  #_(defonce app
      (compassus/application
        {:routes      route->component
         :index-route (get-in init-data [:app/state-route (:server/state init-data)])
         :reconciler  reconciler}))


  #_(defn main []
      (let [el (gdom/getElement "app")]
        (js/console.log el)
        (compassus/mount! app el)
        #_(thonix-log/resend-all! app thonix-log/dmesg 500)))

  ;; Enable browser console
  (enable-console-print!)

  ;; Set overall browser loggin level
  (log/set-level :debug)

  ;; Mount the Root UI component in the DOM div named "app"
  (swap! thonix/app uc/mount Root "app"))

(comment
  (routing/set-state! (:reconciler @thonix/app) :booting)
  (routing/set-state! app :booting)
  (thonix-log/clear-dmesg! app)
  (thonix-log/resend-all! app thonix-log/dmesg 500)
  (om/transform-reads (:reconciler @thonix/app) [:boot])
  (t/write (om/writer {:handlers {}}) [:id :test])
  (t/write (om/writer {:handlers {}}) [(with-meta [:test 21] {:dav "muh"})])
  (t/write (om/writer {:handlers {}}) [`({:test [:id :muh]} {:limit 12})])
  (t/write (om/writer {:handlers {}}) [{:boot [{:state (om/get-query booting/BootHeader)}]}
                                       `({:muh [:sss]} {:limit 10 :after "Test"})
                                       :server/state])

  (thonix-log/add-dmesg! app (first thonix-log/dmesg))
  @(:reconciler @thonix/app)
  (:query (om/process-roots (om/get-query Root)))
  (om/app-state reconciler)
  (om/tree->db Root (uc/get-initial-state Root nil) true)
  (om/db->tree
    (om/get-query Root)
    (om/tree->db Root (uc/get-initial-state Root nil) true)
    (om/tree->db Root (uc/get-initial-state Root nil) true))
  )