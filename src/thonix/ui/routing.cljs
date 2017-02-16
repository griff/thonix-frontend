(ns thonix.ui.routing
  (:require [om.next :as om :refer-macros [defui]]
            [compassus.core :as compassus]
            [om.util :as util]
            [untangled.client.mutations :as m]
            [untangled.client.data-fetch :as df]
            [bidi.bidi :as bidi :refer [branch leaf]]
            [bidi.verbose :refer [branch leaf param]]
            [pushy.core :as pushy]
            [untangled.client.core :as uc]
            [om.dom :as dom]
            [camel-snake-kebab.core :as csk])
  (:require-macros [thonix.macros :as m]))


(def routing-tree
  "A map of routes. The top key is the name of the route (returned from bidi). The value
  is a map. In this map:
  - The keys are the IDs of the routers that must be updated to show the route, and whose
  - The values are the target screen ident. A value in this ident using the `param` namespace will be
  replaced with the incoming route parameter."
  {:running/status           {:running [:running/status :running]}
   :running/menu             {:running [:running/menu :running]}
   :booting/default          {:booting [:booting/default :booting]}
   :booting/details          {:booting [:booting/details :booting]}
   :boot-failed/menu         {:boot-failed [:boot-failed/menu :boot-failed]}
   :boot-failed/log          {:boot-failed [:boot-failed/log :boot-failed]}
   :boot-failed/shell        {:boot-failed [:boot-failed/shell :boot-failed]}
   :boot-failed/drives       {:boot-failed [:boot-failed/drives :boot-failed]}
   :boot-failed/file-systems {:boot-failed [:boot-failed/file-systems :boot-failed]}
   :boot-failed/check        {:boot-failed [:boot-failed/check :boot-failed]
                              :fs-check    [:fs-check :param/fs-id]}
   :status                   {:top-screen    [:report :top]
                              :report-router [:status-report :param/report-id]}})

(def route-tree
  {:no-connection [:no-connection]
   :booting [:booting/default :booting/details]
   :boot-failed [:boot-failed/menu :boot-failed/log :boot-failed/shell :boot-failed/drives
                 :boot-failed/file-systems]
   :running [:running/menu :running/status]})


(def app-routes
  "The bidi routing map for the application. The leaf keywords are the route names. Parameters
  in the route are available for use in the routing algorithm as :param/param-name."
  (branch "/"
    (branch "booting/"
      (leaf "" :booting/default)
      (leaf "details" :booting/details)
      (branch "failed/"
        (leaf "" :boot-failed/menu)
        (leaf "shell" :boot-failed/shell)
        (leaf "log" :boot-failed/log)
        (leaf "drives" :boot-failed/drives)
        (branch "fs/"
          (leaf "" :boot-failed/file-systems)
          (branch (param :fs-id) (leaf "" :boot-failed/check)))))
    (leaf "no-connection" :no-connection)
    (leaf "index-dev.html" :running/menu)
    (leaf "index.html" :running/menu)
    (leaf "" :running/menu)
    (leaf "status" :running/status)))


(defn update-routing-links
  "Given the app state map, returns a new map that has the routing graph links updated for the given route/params
  as a bidi match."
  [state-map {:keys [route]}]
  (let [{:keys [route-params handler]} route
        path-map (get routing-tree handler {})]
    (reduce (fn [m [router-id ident-to-set]]
              (let [value (mapv (fn [element]
                                  (if (and (keyword? element) (= "param" (namespace element)))
                                    (keyword (get route-params (keyword (name element)) element))
                                    element))
                                ident-to-set)]
                (assoc-in m [:routers/by-id router-id :router/current-route] value))) state-map path-map)))

(defn app-state [x]
  (cond
    (om/component? x) (-> x om/get-reconciler om/app-state)
    (om/reconciler? x) (om/app-state x)
    :else x))

(defn get-state [x]
  (let [state (app-state x)]
    (first (get-in @state [:routers/by-id :state :router/current-route]))))

(defn get-route [x]
  (let [state (app-state x)]
    (get @state :current/route)))

(defn update-state
  "Change the application's view of the state of the server"
  [{:keys [state]}] (comment "placeholder for IDE assistance"))

(defn update-state-action [st state]
  (swap! st assoc-in [:routers/by-id :state :router/current-route]
         [state :state]))

(defmethod m/mutate 'thonix.ui.routing/update-state [{st :state} k {:keys [state]}]
  {:action (fn []
             (update-state-action st state))})

;; To keep track of the global HTML5 pushy routing object
(def history (atom nil))

;; To indicate when we should turn on URI mapping. This is so you can use with devcards (by turning it off)
(defonce use-html5-routing (atom true))

(defn update-route-action [state {:keys [handler route-params] :as route}]
  (swap! state update-routing-links {:route route})
  (when (= (get-state state) :running)
    (swap! state assoc :running/route route))
  (swap! state assoc :current/route route))

(defn update-pushy-route-action [state {:keys [handler route-params] :as route}]
  (update-route-action state route)
  (if (and @history @use-html5-routing)
    (pushy/set-token! @history (str (bidi/path-for app-routes handler route-params) js/location.search))))

(defn update-route
  "Change the application's UI route to the given route."
  [{:keys [route]}] (comment "placeholder for IDE assistance"))

(defmethod m/mutate 'thonix.ui.routing/update-route [{:keys [state]} k p]
  {:action (fn [] (update-route-action state (:route p)))})

(defn update-server-state
  "Change the application's view of the state of the server"
  [{:keys [state]}] (comment "placeholder for IDE assistance"))

(defn- get-server-state [state]
  (let [ss (get-in state [:server/state])]
    (cond
      (keyword? ss) ss
      (string? ss) (csk/->kebab-case-keyword ss))))

(defmethod m/mutate 'thonix.ui.routing/update-server-state [{st :state} _ {state :state}]
  {:action (fn []
             (let [cur (get-state st)
                   next (if state state (get-server-state @st))
                   default (first (route-tree next))
                   running (get @st :running/route {:handler default})]
               (when-not (= cur next)
                 (swap! st assoc :server/state next)
                 (update-state-action st next)
                 (case next
                   :no-connection (update-pushy-route-action st {:handler :no-connection})
                   :booting (update-pushy-route-action st {:handler default})
                   :boot-failed (update-pushy-route-action st {:handler default})
                   :running (update-pushy-route-action st running)))))})

(defn set-state
  [comp-or-reconciler state]
  (om/transact! comp-or-reconciler `[(update-server-state {:state ~state}) :state]))

(defn nav-to!
  [component page & kvs]
  (let [state (get-state component)
        pages (route-tree state)]
    (if (some #{page} pages)
      (if (and @history @use-html5-routing)
        (pushy/set-token! @history (str (bidi/path-for app-routes page) js/location.search))
        (om/transact! component `[(update-route ~{:route {:handler page :route-params (into {} kvs)}})]))
      (throw (ex-info "Page not allowed in state" {:state state :page page :pages pages})))))

(defn set-route!
  "Change the route using a bidi match. This method can be directly hooked to pushy via bidi at startup:
  ```
  (pushy/pushy (partial r/set-route! reconciler) (partial bidi/match-route r/app-routes))
  ```
  You probably will want to save the pushy return value for programatic routing via `pushy/set-token!`
  "
  [comp-or-reconciler bidi-match]
  (let [state (get-state comp-or-reconciler)
        pages (route-tree state)
        page (:handler bidi-match)
        route (get-route comp-or-reconciler)]
    #_(js/console.log "set-route!" route bidi-match (= bidi-match route))
    (if (some #{page} pages)
      (when-not (= bidi-match route)
        #_(js/console.log "set-route!" page pages)
        (om/transact! comp-or-reconciler `[(update-route ~{:route bidi-match}) :state]))
      (nav-to! comp-or-reconciler (first pages)))))


(comment
  (bidi/path-for app-routes :main)
  (defn set-route!
    "Given a reconciler, Compassus application or component, update the application's
     route. `next-route` may be a keyword or an ident. Takes an optional third
     options argument, a map with the following supported options:

       :queue? - boolean indicating if the application root should be queued for
                 re-render. Defaults to true.

       :params - map of parameters that will be merged into the application state.

       :tx     - transaction(s) (e.g.: `'(do/it!)` or `'[(do/this!) (do/that!)]`)
                 that will be run after the mutation that changes the route. Can be
                 used to perform additional setup for a given route (such as setting
                 the route's parameters).
       "
    ([x next-route]
     (set-route! x next-route nil))
    ([x next-route {:keys [queue? params tx] :or {queue? true}}]
     {:pre [(or (om/reconciler? x) (om/component? x))
            (or (util/ident? next-route) (keyword? next-route))]}
     (let [reconciler (cond-> x
                        (om/component? x) om/get-reconciler)
           tx (when-not (nil? tx)
                (cond->> tx
                         (not (vector? tx)) vector))]
       (om/transact! reconciler
                     (cond-> (into `[(set-route! ~(merge {:route next-route} params))] tx)
                       queue?
                       (into (om/transform-reads reconciler [:current-state])))))))

  (defn set-state!
    "Given a reconciler, Compassus application or component, update the
     applications version of server state. `next-state` may be a keyword or an
     ident. Takes an optional third options argument, a map with the following
     supported options:

       :queue? - boolean indicating if the application root should be queued for
                 re-render. Defaults to true.

       :params - map of parameters that will be merged into the application state.

       :tx     - transaction(s) (e.g.: `'(do/it!)` or `'[(do/this!) (do/that!)]`)
                 that will be run after the mutation that changes the route. Can be
                 used to perform additional setup for a given route (such as setting
                 the route's parameters).
       "
    ([x next-state]
     (set-state! x next-state nil))
    ([x next-state {:keys [queue? params tx] :or {queue? true}}]
     {:pre [(or (om/reconciler? x) (om/component? x))
            (keyword? next-state)]}
     (let [reconciler (cond-> x
                        (om/component? x) om/get-reconciler)
           tx (when-not (nil? tx)
                (cond->> tx
                         (not (vector? tx)) vector))]
       (om/transact! reconciler
                     (cond-> (into `[(set-state! ~(merge {:state next-state} params))] tx)
                       queue?
                       (into (om/transform-reads reconciler [:current-state]))))))))

(comment
  (defmethod thonix/read :server/state
    [{:keys [state query]} k _]
    (let [st @state]
      {:value (get st k)}))

  (defmethod m/mutate 'thonix.ui.routing/set-route!
    [{:keys [state] :as env} key {:keys [route] :as params}]
    (let [params (dissoc params :route)]
      {:value  {:keys (into [:current-state] (keys params))}
       :action #(swap! state (fn [cur]
                               (let [state-id (get-in cur [:current-state])]
                                 (-> cur
                                     (assoc-in (conj state-id :current-route) [route :route])
                                     (merge params)))))}))

  (defmethod m/mutate 'thonix.ui.routing/set-state!
    [{:keys [state] :as env} key {server-state :state :as params}]
    (let [params (dissoc params :state)]
      {:value  {:keys (into [:current-state] (keys params))}
       :action #(swap! state (fn [cur]
                               (-> cur
                                   (assoc-in [:current-state] [server-state :state])
                                   (merge params))))})))
