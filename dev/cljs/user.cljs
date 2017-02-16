(ns cljs.user
  (:require
    [untangled.client.core :as uc]
    [om.next :as om]
    [thonix.ui.routing :as r]
    [thonix.core :as core]
    [thonix.app :as app]
    [cljs.pprint :refer [pprint]]
    [untangled.client.mutations :as m]
    [om.next :as om]
    [om.next :as om]
    [thonix.ui.routing :as r]))

(enable-console-print!)

(defn main []
  (reset! core/app (uc/mount @core/app app/Root "app")))

(defn reload [])

(defmethod m/mutate 'nav/route-to [{:keys [state]} _ {:keys [router page]}]
  (swap! state assoc-in [:routers/by-id router :current-route] page))

(comment
  (let [{:keys [reconciler]} @core/app]
    (om/transact! reconciler '[(nav/route-to {:router :top-screen :page [:main :top]})])))

(defn set-state! [state]
  (let [{:keys [reconciler]} @core/app]
    (r/set-state reconciler state)))

(defn app-state [] @(:reconciler @core/app))

(defn force-root-render! []
  (om/force-root-render! (:reconciler @core/app)))

(defn log-app-state [& keywords]
  #_(pprint) (let [app-state (app-state)]
             (if (= 0 (count keywords))
               app-state
               (select-keys app-state keywords))))

(defn dump-query [comp]
  (let [component (om/class->any (:reconciler @core/app) comp)]
    (om/full-query component)))

(defn dump-query-kw [kw]
  (let [component (om/ref->any (:reconciler @core/app) kw)]
    (om/full-query component)))

(defn q
  "Run the query of the given UI class and return the result as a map of the query that was run and the data that was returned.
  NOTE: If the component is mounted in several places on the UI, you may not get the expected result. Be sure to check
  the QUERY part of the result to see the query used."
  [ui-class]
  (let [query (dump-query ui-class)
        state (app-state)]
    {:QUERY  query
     :RESULT (om/db->tree query state state)}))

(defn qkw
  "Find a component that uses the given keyword in its query, then run that query against the app database and show
  the result. NOTE: If more than one component matches, your results may vary. Be sure to examine the query that
  was used."
  [query-kw]
  (let [query (dump-query-kw query-kw)
        state (app-state)]
    {:QUERY  query
     :RESULT (om/db->tree query state state)}))