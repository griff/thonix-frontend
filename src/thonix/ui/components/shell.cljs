(ns thonix.ui.components.shell
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [thonix.core :as thonix]
            [om.next :as om :refer-macros [defui]]
            [compassus.core :as compassus]
            [om.dom :as dom]
            [clojure.string :as string]
            [xterm :as xt]
            [xterm.addons.fit :as fit]
            [cljs.core.async :refer [close! put! <! >! chan]]
            [untangled.client.mutations :as m]))


(defui ^:once Shell
  static om/IQuery
  (query [this]
    [:in :out])
  Object
  (sendData [this data]
    (when-let [out (:out (om/props this))]
      (js/console.log "sendData" out data)
      (put! out data)))
  (changeReader [this in]
    (let [term (om/get-state this :term)]
      (js/console.log "changeReader" term in)
      (when in
        (go-loop []
          (when-let [data (<! in)]
            (.write term data)
            (recur))))))
  (initLocalState [this]
    (let [term (new xt/Xterm #js {:cursorBlink true})]
      (.on term "data" (fn [data]
                         (js/console.log "Data" this data)
                         (.sendData this data)))
      {:term term}))
  (componentWillReceiveProps [this next-props]
    (let [{old-in :in old-out :out} (om/props this)
          {:keys [in out]} next-props]
      (js/console.log "componentWillReceiveProps sd" in out old-in old-out)
      (if-not (= in old-in)
        (.changeReader this in))
      (if-not (= out old-out)
        (close! old-out))))
  (componentDidMount [this]
    (let [term (om/get-state this :term)
          {:keys [in]} (om/props this)]
      (js/console.log "componentDidMount" term in (dom/node this))
      (.open term (dom/node this))
      (fit/fit term)
      (.changeReader this in)))
  (componentWillUnmount [this]
    (let [term (om/get-state this :term)
          {:keys [out in]} (om/props this)]
      (js/console.log "componentWillUnmount" in out)

      (.destroy term)
      (when out
        (close! out))
      (when in
        (close! in))))
  (render [this]
    (dom/div #js {:className "terminal-container terminal"})))

(def shell (om/factory Shell))

(defn- open-shell
  [{:keys [:location :in :out]}] (comment "placeholder for IDE assistance"))

(defmethod m/mutate 'thonix.ui.components.shell/open-shell
  [{:keys [state]} key {:keys [location] :as params}]
  (let [params (dissoc params :location)]
    {:value  {:keys [location]}
     :action #(swap! state assoc-in location params)}))

(defn open-shell! [x location]
  {:pre [(or (om/reconciler? x) (compassus/application? x) (om/component? x))]}
  (let [in (chan)
        out (chan)
        reconciler (cond-> x
                     (compassus/application? x) compassus/get-reconciler
                     (om/component? x) om/get-reconciler)]
    (om/transact! reconciler
                  (->
                    `[(open-shell {:location ~location :in ~in :out ~out})]
                    (into (om/transform-reads reconciler [location]))))
    [in out]))

