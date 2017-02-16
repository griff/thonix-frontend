(ns thonix.graphql
  (:require [om.next :as om]
            [om.util :as util]
            [clojure.string :as string]
            [camel-snake-kebab.core :as csk]))

(defn param->args [{:keys [param-map]} params]
  (when-not (empty? params)
    (str "("
         (string/join
           (map (fn [[k v]]
                  (str (param-map k) ": " v))
                params)
           ", ")
         ")")))

(defn join-name [{:keys [prop-map]} expr]
  (prop-map (util/join-key expr)))

(defn alias-name [{:keys [prop-map]} params]
  (if-let [prop (:aliased/prop params)]
    (str ": " (prop-map prop))))

(declare expr)

(defn children [env c pre]
  (let [exprs (map #(expr env % pre) c)]
    {:query  (string/join
               (str "\n" pre)
               (map :query exprs))
     :mapper (->> exprs (map :mapper) (reduce merge))}))

(defn join [env j pre]
  (let [key (join-name env j)
        {:keys [query mapper]} (children env (util/join-value j) (str "    " pre))]
    {:query     (str key
                     (when (seq? j) (alias-name env (second j)))
                     (when (seq? j) (param->args env (dissoc (second j) :aliased/prop)))
                     "{\n    " pre
                     query
                     "\n" pre "}")
     :mapper {key [(util/join-key j) mapper]}}))

(defn union [{:keys [union-map] :as env} u pre]
  (str (join-name env u)
       (when (seq? u) (alias-name env (second u)))
       (when (seq? u) (param->args env (dissoc (second u) :aliased/prop)))
       "{\n    " pre
       (string/join
         (map (fn [[k v]]
                (str "... on " (union-map k) "{\n        " pre
                     (children env v (str "        " pre))
                     "\n    " pre "}"))
              (util/join-value u))
         (str "\n    " pre))
       "\n" pre "}"))

(defn prop [{:keys [prop-map] :as env} p]
  (if (seq? p)
    (let [name (first p)
          params (second p)]
      {:query  (str (prop-map name)
                    (alias-name env params)
                    (param->args env (dissoc params :aliased/prop)))
       :mapper {(prop-map name) [name]}})
    {:query  (prop-map p)
     :mapper {(prop-map p) [p]}}))



(defn call [{:keys [call-map] :as env} c]
  {:query (str (call-map (first c))
               (param->args env (second c)))})

(defn expr [env e pre]
  (cond
    (util/union? e) (union env e pre)
    (util/join? e) (join env e pre)
    (or (keyword? e)
        (and (seq? e)
             (keyword? (first e)))) (prop env e)
    (util/mutation? e) (call env e)))

(defn ns->ident [id]
  (if (namespace id)
    (str (csk/->camelCaseString (namespace id))
         (csk/->PascalCaseString (name id)))
    (csk/->camelCaseString (name id))))

(defn ns->interface [id]
  (str (when (namespace id) (csk/->PascalCaseString (namespace id)))
       (csk/->PascalCaseString (name id))))

(defn default-env []
  {:call-map ns->ident
   :prop-map ns->ident
   :union-map ns->interface})

(defn query
  ([edn] (query (default-env) edn))
  ([env edn]
   (let [{:keys [query mapper]} (children env edn "    ")]
     {:query  (str "query " (:name (meta edn) "OmQuery") "{\n"
                   query "}")
      :mapper mapper})))


(defn apply-mapper [mapper form]
  (if (and mapper (not-empty mapper))
    (cond
      (map? form)
      (into (empty form)
            (map (fn [[k v :as entry]]
                   (if-let [[key child] (get mapper k)]
                     [key (apply-mapper child v)]
                     entry))
                 form))

      (vector? form)
      (mapv #(apply-mapper mapper %) form))
    form))