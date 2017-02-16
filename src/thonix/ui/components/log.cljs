(ns thonix.ui.components.log
  (:require [om.next :as om :refer-macros [defui]]
            [compassus.core :as compassus]
            [om.dom :as dom]
            [clojure.string :as string]
            [untangled.client.mutations :as m]
            [untangled.client.core :as uc]))

(defn- clear-dmesg
  "Clear all dmesg log entries from state"
  [] (comment "placeholder for IDE assistance"))

(defmethod m/mutate 'thonix.ui.components.log/clear-dmesg
  [{:keys [state] :as env} key _]
  {:value  {:keys [:boot :dmesg :logblock/by-id]}
   :action #(swap! state (fn [cur]
                           (-> cur
                               (assoc-in [:boot :current] nil)
                               (assoc-in [:boot :now] 0)
                               (assoc :dmesg [])
                               (assoc :logblock/by-id {}))))})

(defn clear-dmesg! [x]
  {:pre [(or (om/reconciler? x) (compassus/application? x) (om/component? x))]}
  (let [reconciler (cond-> x
                     (compassus/application? x) compassus/get-reconciler
                     (om/component? x) om/get-reconciler)]
    (om/transact! reconciler
                  (->
                    `[(clear-dmesg)]
                    (into (om/transform-reads reconciler [:boot :dmesg]))))))

(defn- close-block
  "Close a logblock"
  [{:keys [id]}] (comment "placeholder for IDE assistance"))
(defn- open-block
  "Open a logblock"
  [{:keys [messages]}] (comment "placeholder for IDE assistance"))

(defn translations [key]
  (get {"booting"     "Starting boot"
        "preDevice"   "Pre-device"
        "preLVM"      "Pre-LVM"
        "networkPost" "Network started"
        "postDevice"  "Devices started"
        "postMount"   "Filesystems mounted"}
       key
       key))

(defn make-line [msg]
  (str (string/upper-case (name (:level msg)))
       " " (string/upper-case (name (:facility msg)))
       " [" (get-in msg [:timestamp "Timestamp"]) "] " (:message msg)))

(defui ^:once LogBlock
  static uc/InitialAppState
  (initial-state [clz params]
    {:name "booting" :ui/open true :entries []})
  static om/Ident
  (ident [this {:keys [name]}]
    [:logblock/by-id name])
  static om/IQuery
  (query [this]
    [:name :ui/open {:entries [:id :level :facility :timestamp :message]}])
  Object
  (clicked [this ev]
    (let [{:keys [name ui/open]} (om/props this)]
      (if open
        (om/transact! this `[(close-block {:id ~name})])
        (om/transact! this `[(open-block {:id ~name})]))))
  (render [this]
    (let [{:keys [name ui/open entries]} (om/props this)
          header (translations name)]
      (dom/div #js {:className (if open "block open" "block closed")
                    :key       name}
        (concat
          [(dom/h3 #js {:key     "header"
                        :onClick #(.clicked this %)} header)]
          (when open
            (map #(dom/div #js {:className "line" :key (:id %)} (make-line %))
                 entries)))))))

(def log-block (om/factory LogBlock))

(defui ^:once ViewLog
  Object
  (render [this]
    (let [blocks (om/props this)]
      (dom/div #js {:className "vert-scroll"}
        (dom/div #js {:id "blocks"}
          (map log-block blocks))
        (dom/div #js {:id "messages"}
          (map log-block blocks))))))

(def view-log (om/factory ViewLog))

(defui ^:once DmesgLog
  static uc/InitialAppState
  (initial-state [clz params]
    {:steps [(uc/get-initial-state LogBlock {})]})
  static om/Ident
  (ident [this props]
    [:dmesg :log])
  static om/IQuery
  (query [this]
    [{:steps (om/get-query LogBlock)}])
  Object
  (render [this]
    (view-log (:steps (om/props this)))))

(def ui-dmesg-log (om/factory DmesgLog))

#_(comment
    (defmethod thonix/read :dmesg
      [{:keys [state query] :as env} key params]
      (let [st @state]
        (.log js/console "dmesg"
              (clj->js query) env key
              (clj->js st)
              (clj->js (om/db->tree query (get st key) st)))
        {:value (om/db->tree query (get st key) st)}))

    (defmethod thonix/read :boot
      [{:keys [state query] :as env} key params]
      (let [st @state]
        (.log js/console "Boot" key)
        {:value (om/db->tree query (get st key) st)})))

(defn- add-block
  [cur logblock]
  (let [new-id [:logblock/by-id logblock]]
    (-> cur
        (update-in [:boot :now] + 1)
        (assoc-in new-id {:id     logblock
                          :header (translations logblock)
                          :open   true
                          :lines  []})
        (assoc-in [:boot :current] new-id)
        (update :dmesg conj new-id))))

(defn- add-dmesg [cur {:keys [level msg ts] :as params}]
  (let [[_ logblock] (re-matches #"^thonix:\s+(.+)$" msg)
        msg (make-line params)
        current-id (get-in cur [:boot :current])]
    (cond
      (and logblock current-id)
      (-> cur
          (assoc-in (conj current-id :open) false)
          (add-block logblock))

      logblock
      (add-block cur logblock)

      current-id
      (update-in cur (conj current-id :lines) conj msg)

      :else
      (-> cur
          (add-block "booting")
          (update-in [:logblock/by-id "booting" :lines] conj msg)))))

(defn- add-dmesgs
  "Add dmesg log entries"
  [{:keys [messages]}] (comment "placeholder for IDE assistance"))

(defmethod m/mutate 'thonix.ui.components.log/add-dmesgs
  [{:keys [state] :as env} key {msgs :messages}]
  {:value  {:keys [:boot :dmesg :logblock/by-id]}
   :action #(swap! state (fn [cur] (reduce add-dmesg cur msgs)))})

(defn add-dmesgs! [x msgs]
  {:pre [(or (om/reconciler? x) (compassus/application? x) (om/component? x))]}
  (let [reconciler (cond-> x
                     (compassus/application? x) compassus/get-reconciler
                     (om/component? x) om/get-reconciler)]
    (om/transact! reconciler
                  (->
                    `[(add-dmesgs {:messages ~msgs})]
                    (into (om/transform-reads reconciler [:boot :dmesg]))))))


(defmethod m/mutate 'thonix.ui.components.log/close-block
  [{:keys [state] :as inp} k {:keys [id]}]
  {:value  {:keys [[:logblock/by-id id]]}
   :action #(swap! state assoc-in
                   [:logblock/by-id id :open]
                   false)})
(defmethod m/mutate 'thonix.ui.components.log/open-block
  [{:keys [state] :as inp} k {:keys [id]}]
  {:value  {:keys [[:logblock/by-id id]]}
   :action #(swap! state assoc-in
                   [:logblock/by-id id :open]
                   true)})

(defn resend-all! [app dmesg ts]
  (clear-dmesg! app)
  (letfn [(self [dmesg]
            (add-dmesgs! app [(first dmesg)])
            (js/setTimeout (fn []
                             (if-let [r (seq (rest dmesg))]
                               (self r))) ts))]
    (js/setTimeout (fn []
                     (if-let [r (seq (rest dmesg))]
                       (self r))) ts)))

(comment
  (defn dmesg-chan! [app]
    (let [ch (chan)]
      (go-loop []
               (when-let [msg (<! ch)]
                 (add-dmesg! app msg)
                 (recur)))
      (clear-dmesg! app)
      ch))

  (defn delay
    ([out]
     (delay out true))
    ([out close?]
     (let [ch (chan)]
       (go-loop []
                (let [msg (<! ch)]
                  (if (nil? msg)
                    (when close? (close! ch))
                    (do
                      (<! (timeout ts))
                      (when (>! out msg)
                        (recur)))))))))

  (defn resend-all! [app dmesg ts]
    (pipe
      (to-chan dmesg)
      (delay (dmesg-chan app) ts))))

(defn dmesg-normalize [msg]
  (if (map? msg)
    msg
    (let [[_ level ts msg] (re-matches #"^(\w+) \[\s*(\d+\.\d*)\] (.+)$" msg)]
      {:level (keyword (string/lower-case level))
       :timestamp    (js/parseFloat ts)
       :msg   msg})))

(comment
  (re-matches #"^(\w+) \[s*(\d+\.\d*)\] (.+)$" "INFO [0.450426] stage-1: booting the big stuff from here"))

(def dmesg
  (map dmesg-normalize
       [{:level :info :timestamp 0.450426 :msg "thonix: booting"}
        "INFO [0.450426] stage-1: booting the big stuff from here"
        "INFO [0.450426] stage-1: [fsck.ext4 (1) -- /mnt-root/] fsck.ext4 -a /dev/disk/by-uuid/a8978ba9-c0bf-4445-b19c-5ee46a2e26bd"
        "INFO [0.450426] pci 0000:00:00.0: [8086:1237] type 00 class 0x060000"
        "INFO [0.450426] pci 0000:00:01.0: [8086:7000] type 00 class 0x060100"
        "INFO [0.450426] pci 0000:00:01.1: [8086:7111] type 00 class 0x01018a"
        {:level :info :timestamp 0.450426 :msg "thonix: preNetwork"}
        {:level :info :timestamp 0.450426 :msg "thonix: doStuff"}
        "INFO [0.450426] stage-1: booting the big stuff from here"
        "INFO [0.450426] stage-1: [fsck.ext4 (1) -- /mnt-root/] fsck.ext4 -a /dev/disk/by-uuid/a8978ba9-c0bf-4445-b19c-5ee46a2e26bd"
        "INFO [0.450426] pci 0000:00:00.0: [8086:1237] type 00 class 0x060000"
        "INFO [0.450426] pci 0000:00:01.0: [8086:7000] type 00 class 0x060100"
        "INFO [0.450426] pci 0000:00:01.1: [8086:7111] type 00 class 0x01018a"
        "INFO [0.450426] pci 0000:00:01.1: reg 0x20: [io 0xd000-0xd00f]"
        "INFO [0.450426] pci 0000:00:01.1: legacy IDE quirk: reg 0x10: [io 0x01f0-0x01f7]"
        "INFO [0.450426] pci 0000:00:01.1: legacy IDE quirk: reg 0x14: [io 0x03f6]"
        "INFO [0.450426] pci 0000:00:01.1: legacy IDE quirk: reg 0x18: [io 0x0170-0x0177]"
        "INFO [0.450426] pci 0000:00:01.1: legacy IDE quirk: reg 0x1c: [io 0x0376]"
        "INFO [0.450426] pci 0000:00:02.0: [80ee:beef] type 00 class 0x030000"
        "INFO [0.450426] pci 0000:00:02.0: reg 0x10: [mem 0xe0000000-0xe07fffff pref]"
        "INFO [0.450426] pci 0000:00:03.0: [8086:100e] type 00 class 0x020000"
        "INFO [0.450426] pci 0000:00:03.0: reg 0x10: [mem 0xf0000000-0xf001ffff]"
        "INFO [0.462236] pci 0000:00:03.0: reg 0x18: [io 0xd010-0xd017]"
        "INFO [0.480591] pci 0000:00:04.0: [80ee:cafe] type 00 class 0x088000"
        "INFO [0.486386] pci 0000:00:04.0: reg 0x10: [io 0xd020-0xd03f]"
        "INFO [0.491223] pci 0000:00:04.0: reg 0x14: [mem 0xf0400000-0xf07fffff]"
        "INFO [0.496874] pci 0000:00:04.0: reg 0x18: [mem 0xf0800000-0xf0803fff pref]"
        "INFO [0.515397] pci 0000:00:07.0: [8086:7113] type 00 class 0x068000"
        "INFO [0.516216] pci 0000:00:08.0: [8086:100e] type 00 class 0x020000"
        "INFO [0.520706] pci 0000:00:08.0: reg 0x10: [mem 0xf0820000-0xf083ffff]"
        "INFO [0.531462] pci 0000:00:08.0: reg 0x18: [io 0xd040-0xd047]"
        "INFO [0.549872] pci 0000:00:09.0: [8086:100e] type 00 class 0x020000"
        "INFO [0.555808] pci 0000:00:09.0: reg 0x10: [mem 0xf0840000-0xf085ffff]"
        "INFO [0.566404] pci 0000:00:09.0: reg 0x18: [io 0xd048-0xd04f]"
        {:level :info :timestamp 0.566404 :msg "thonix: postMount"}
        "INFO [    1.001236] stage-1-init: bringing up network interface lo..."
        "INFO [    1.002274] stage-1-init: acquiring IP address via DHCP..."
        "INFO [    1.004553] stage-1-init: udhcpc: SIOCGIFINDEX: No such device"
        "INFO [    1.005027] stage-1-init: starting device mapper and LVM..."
        "INFO [    1.012895] stage-1-init: checking /dev/disk/by-uuid/a8978ba9-c0bf-4445-b19c-5ee46a2e26bd..."
        "INFO [    1.013406] stage-1-init: fsck (busybox 1.24.2, )"
        "INFO [    1.013461] stage-1-init: [fsck.ext4 (1) -- /mnt-root/] fsck.ext4 -a /dev/disk/by-uuid/a8978ba9-c0bf-4445-b19c-5ee46a2e26bd"
        "INFO [    1.017065] stage-1-init: nixos: clean, 210095/2564096 files, 872228/10239744 blocks"
        "INFO [    1.018358] stage-1-init: mounting /dev/disk/by-uuid/a8978ba9-c0bf-4445-b19c-5ee46a2e26bd on /..."
        "INFO [    1.057588] EXT4-fs (sda1): mounted filesystem with ordered data mode. Opts: (null)"
        "INFO [    1.897773] tsc: Refined TSC clocksource calibration: 2689.456 MHz"
        "INFO [    1.897778] clocksource: tsc: mask: 0xffffffffffffffff max_cycles: 0x26c45604132, max_idle_ns: 440795338027 ns"
        "INFO [    1.917378] EXT4-fs (sda1): re-mounted. Opts: (null)"
        "INFO [    1.917667] booting system configuration /nix/store/sacl3hpya73jsz4clsh3jv7pww1c7cr0-nixos-system-nixos-16.09.1272.81428dd"
        "INFO [    2.793692] stage-2-init: running activation script..."
        "INFO [    2.807134] stage-2-init: setting up /etc..."
        "INFO [    2.989093] random: nonblocking pool is initialized"]))

