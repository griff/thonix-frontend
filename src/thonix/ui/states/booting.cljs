(ns thonix.ui.states.booting
  (:require [thonix.ui.helpers :as ui]
            [thonix.ui.components.log :as log]
            [thonix.ui.routing :as routing]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [om.util :as util]
            [om.next.impl.parser :as parser]
            [untangled.client.core :as uc])
  (:require-macros [thonix.macros :as m]))

(defui ^:once BootHeader
  static om/IQuery
  (query [this]
    [:total-steps {:steps [:name]}])
  Object
  (render [this]
    (let [{:keys [total-steps steps]} (om/props this)
          {:keys [name]} (last steps)
          now (count steps)
          total (or total-steps 1)
          label (om/get-computed this :label)]
      (dom/div #js {:key "boot-header"}
        (dom/h1 #js {:key "title"} "ThoNix")
        (dom/h4 #js {:key "subtitle"} "is booting")
        (dom/div #js {:className (str "progress-with-label "
                                      (condp = label
                                        :right "label-right"
                                        ""))}
          (ui/progress now (str now " of " total) 0 total)
          (if (= label :right)
            (dom/span nil (str now "/" total))
            (dom/div nil
              (dom/span #js {:key name :style #js {:minHeight "1em" :display "inline-block"}}
                (log/translations name))
              (dom/span #js {:style #js {:float "right"}}
                (str now "/" total)))))))))

(def boot-header (ui/factory BootHeader {:computed {:label :right}}))
(def boot-big-header (om/factory BootHeader))

(defui ^:once Details
  static uc/InitialAppState
  (initial-state [clz params]
    {:page   :booting/details
     :dmesg {}
     :header {}})
  static om/IQuery
  (query [this]
    (let [dmesg (om/get-query log/DmesgLog)
          header (om/get-query BootHeader)]
      [:page {:dmesg [{[:boot '_] dmesg}]} {:header [{[:boot '_] header}]}]))
  Object
  (hideDetails [this ev]
    (routing/nav-to! this :booting/default))
  (render [this]
    (let [{:keys [dmesg header]} (om/props this)]
      (dom/div nil
        (ui/header
          (boot-header (:boot header)))
        (log/ui-dmesg-log (:boot dmesg))
        (ui/footer
          (dom/div #js {:key "middle" :style #js {:padding "0.5em 0"}}
            (ui/button "Hide Details" #(.hideDetails this %)
                           {:className "btn-primary"})))))))

(defui ^:once Default
  static uc/InitialAppState
  (initial-state [clz params]
    {:page   :booting/default})
  static om/IQuery
  (query [this]
    [:page {[:boot '_] (om/get-query BootHeader)}])
  Object
  (showDetails [this ev]
    (routing/nav-to! this :booting/details))
  (render [this]
    (let [header (:boot (om/props this))]
      (dom/div #js {:id "boot" :style #js {:margin "0 1.5em"}}
        (boot-big-header header)
        (dom/div #js {:style #js {:textAlign "center"}}
          (ui/button "View Details" #(.showDetails this %)))))))

(m/defrouter BootingRouter :booting
  (ident [this props] [(:page props) :booting])
  :booting/default Default
  :booting/details Details)

(m/defpassthrough BootingState :booting BootingRouter)



(defn ident-remotes [{:keys [parser target] :as env} query]
  (let [{idents :ident joins :join jidents :join-ident}
        (group-by #(cond (util/ident? %) :ident
                         (and (util/join? %)
                              (util/ident? (util/join-key %))) :join-ident
                         (util/join? %) :join)
                  query)]
    (prn (parser env (vec (concat idents jidents)) target))
    (parser env (vec (concat idents jidents)) target)))

(defn test-read [{:keys [query ast] :as env} key _]
  (cond
    (= key :boot)
    {:value  {:test 12}
     :remote (parser/expr->ast {key (ident-remotes env query)})}

    (= key :dmesg)
    {:value 455
     :remote (assoc ast :query-root true)}

    (= key :tast)
    {:value 4333
     :remote (assoc ast :query-root true)}))
(comment
  (parser/expr->ast {[:dmesg '_] [:total :remote]})
  (:query (om/process-roots
            ((om/parser {:read test-read}) {} [[:tast 12] {:boot [:buut {[:tast '_] [:ll]} {[:dmesg '_] [:total]}]}] :remote))))


(defn root-idents [ast]
  (map
    #(cond-> %
       (and (= (:type %) :join)
            (util/unique-ident? (:key %)))
       (assoc :query-root true))
    ast))

#_(comment
  (defmethod thonix/read :boot/default
    [{:keys [state query ast] :as env} key params]
    (let [st @state]
      {:value  (om/db->tree query (get st key) st)
       :remote (root-idents ast)}))

  (defmethod thonix/read :boot/details
    [{:keys [state query ast] :as env} key params]
    (let [st @state]
      {:value  (om/db->tree query (get st key) st)
       :remote (root-idents ast)}))

  (defmethod thonix/read :boot-fail/log
    [{:keys [state query] :as env} key params]
    (let [st @state]
      {:value (om/db->tree query (get st key) st)}))

  (defmethod thonix/read :boot-fail/shell
    [{:keys [state query] :as env} key params]
    (let [st @state]
      {:value (om/db->tree query (get st key) st)}))

  (defmethod thonix/read :boot-fail/drives
    [{:keys [state query] :as env} key params]
    (let [st @state]
      {:value (om/db->tree query (get st key) st)}))

  (defmethod thonix/read :boot-fail/file-systems
    [{:keys [state query] :as env} key params]
    (let [st @state]
      {:value (om/db->tree query (get st key) st)})))
