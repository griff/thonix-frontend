(ns thonix.macros
  (:require [clojure.spec :as s]
            [untangled.client.core :as uc]
            [om.next :as om]
            [om.dom :as dom]
            [untangled.client.logging :as log]))

(defmacro defpassthrough
  ""
  [sym page router]
  (let [router-kw (keyword (namespace page) (str (name page) "-router"))]
    `(om/defui ~(vary-meta sym assoc :once true)
       ~'static untangled.client.core/InitialAppState
       (~'initial-state [~'clz ~'params]
         {:page ~page ~router-kw (untangled.client.core/get-initial-state ~router {})})
       ~'static om.next/IQuery
       (~'query [~'this]
         [:page {~router-kw (om.next/get-query ~router)}])
       ~'Object
       (~'render [~'this]
         (let [router# (~router-kw (om.next/props ~'this))]
           ((om.next/factory ~router) router#))))))

(s/def ::mutation-args (s/cat
                         :sym symbol?
                         :doc (s/? string?)
                         :arglist vector?
                         :body (s/+ (constantly true))))

(defn conform! [spec x]
  (let [rt (s/conform spec x)]
    (when (s/invalid? rt)
      (throw (ex-info (s/explain-str spec x)
                      (s/explain-data spec x))))
    rt))

(defmacro defplaceholder
  "Generate a screen placeholder (with no ident) that is meant to be the target of a router. The initial-state should
  include whatever is necessary for the router's ident function to perform properly. The component's query will include
  all of the keys of the supplied initial state, which will then be available in your render body.
  render-body should look like  `(render [this] ...)`."
  [sym initial-state render-body]
  (let [query (vec (keys initial-state))]
    `(om/defui ~(vary-meta sym assoc :once true)
       ~'static untangled.client.core/InitialAppState
       (~'initial-state [~'clz ~'params] ~initial-state)
       ~'static om.next/IQuery
       (~'query [~'this] ~query)
       ~'Object
       ~render-body)))

(defmacro ^{:doc      "Define an Untangled UI-only mutation.

                       The given symbol will be prefixed with the namespace of the current namespace.

                       The arglist should be the *parameter* arglist of the mutation, NOT the complete argument list
                       for the equivalent defmethod. For example:

                          (defmutation boo [id] ...) => (defmethod m/mutate *ns*/boo [{:keys [state ref]} _ {:keys [id]}] ...)

                       The following will be available in the body:
                       - state : The application state atom
                       - ref : The ident of the invoking component, if available
                       - ast : The AST of the mutation
                       - env : The complete Om Parser env"
            :arglists '([sym docstring? arglist body])}
defmutation
  [& args]
  (let [{:keys [sym doc arglist body]} (conform! ::mutation-args args)
        fqsym (symbol (name (ns-name *ns*)) (name sym))
        env '{:keys [state ref ast] :as env}]
    `(defmethod untangled.client.mutations/mutate '~fqsym [~env ~'_ {:keys ~arglist}]
       {:action (fn [] ~@body)})))

(defn- emit-union-element [sym ident-fn kws-and-screens]
  (try
    (let [query (reduce (fn [q {:keys [kw sym]}] (assoc q kw `(om.next/get-query ~sym))) {} kws-and-screens)
          first-screen (-> kws-and-screens first :sym)
          screen-render (fn [cls] `((om.next/factory ~cls {:keyfn (fn [props#] ~(name cls))}) (om.next/props ~'this)))
          render-stmt (reduce (fn [cases {:keys [kw sym]}]
                                (-> cases
                                    (conj kw (screen-render sym)))) [] kws-and-screens)]
      `(om.next/defui ~(vary-meta sym assoc :once true)
         ~'static untangled.client.core/InitialAppState
         (~'initial-state [~'clz ~'params] (untangled.client.core/get-initial-state ~first-screen ~'params))
         ~'static om.next/Ident
         ~ident-fn
         ~'static om.next/IQuery
         (~'query [~'this] ~query)
         ~'Object
         (~'render [~'this]
           (let [page# (first (om.next/get-ident ~'this))]
             (case page#
               ~@render-stmt
               (om.dom/div nil (str "Cannot route: Unknown Screen " page#)))))))
    #?(:cljs (catch js/Error e (js/console.error "Problem with macro " e)
                               `(def ~sym (js/console.log "BROKEN ROUTER!")))
       :clj (catch Exception e (.println System/err (str "Problem with macro " (.printStackTrace e System/out)))
                               `(def ~sym (js/console.log "BROKEN ROUTER!"))))))

(defn- emit-router [router-id sym union-sym]
  `(om.next/defui ~(vary-meta sym assoc :once true)
     ~'static untangled.client.core/InitialAppState
     (~'initial-state [~'clz ~'params] {:router/id ~router-id :router/current-route (uc/get-initial-state ~union-sym {})})
     ~'static om.next/Ident
     (~'ident [~'this ~'props] [:routers/by-id ~router-id])
     ~'static om.next/IQuery
     (~'query [~'this] [:router/id {:router/current-route (om/get-query ~union-sym)}])
     ~'Object
     (~'render [~'this]
       ((om.next/factory ~union-sym) (:router/current-route (om/props ~'this))))))

(s/def ::router-args (s/cat
                       :sym symbol?
                       :router-id keyword?
                       :ident-fn (constantly true)
                       :kws-and-screens (s/+ (s/cat :kw keyword? :sym symbol?))))

(defmacro ^{:doc      "Generates a component with a union query that can route among the given screen, which MUST be
in cljc files. The first screen listed will be the 'default' screen that the router will be initialized to show.

- All screens *must* implement InitialAppState
- All screens *must* have a UI query
"
            :arglists '([sym router-id ident-fn & kws-and-screens])}
defrouter
  [& args]
  (let [{:keys [sym router-id ident-fn kws-and-screens]} (conform! ::router-args args)
        union-sym (symbol (str (name sym) "-Union"))]
    `(do
       ~(emit-union-element union-sym ident-fn kws-and-screens)
       ~(emit-router router-id sym union-sym))))


(defn- emit-root [sym child]
  `(om.next/defui ~(vary-meta sym assoc :once true)
     ~'static untangled.client.core/InitialAppState
     (~'initial-state [~'clz ~'params] {:screen (uc/get-initial-state ~child {})})
     ~'static om.next/IQuery
     (~'query [~'this] [:ui/react-key {:screen (om/get-query ~child)}])
     ~'Object
     (~'render [~'this]
       (let [{:keys [~'ui/react-key]} (om/props ~'this)]
         ((om.next/factory ~child) (:screen (om/props ~'this)))))))

(s/def ::root-args (s/cat
                     :sym symbol?
                     :child symbol?))

(defmacro ^{:doc      "Generate a defui for a root component that renders just the given child element. Useful
for developing screens in devcards. Sample usage:

(defroot CardRoot Screen1)
(defcard my-card Screen1 {} {:inspect-data true})
"
            :arglists '([sym child])}
defroot
  [& args]
  (let [{:keys [sym child]} (conform! ::root-args args)]
    (emit-root sym child)))

(comment
  (macroexpand-1 '(defroot Root Child))
  (macroexpand-1 '(defrouter TopRouter :top (ident [cls props] [(:page props) :report]) :screen1 Screen1 :screen2 Screen2))
  (macroexpand-1 '(defmutation open "This is a test" [id param] (println "hello") (println "there")))
  (macroexpand-1 '(defmutation open "This is a test" [id param] (println hello) true)))
