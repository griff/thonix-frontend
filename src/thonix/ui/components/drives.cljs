(ns thonix.ui.components.drives
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [thonix.core :as thonix]
            [cljs.pprint :as pp]
            [untangled.client.mutations :as m]
            [thonix.ui.helpers :as ui]))

(def kilobyte 1000)
(def megabyte (* kilobyte 1000))
(def gigabyte (* megabyte 1000))
(def terrabyte (* gigabyte 1000))

(defn human-size [size]
  (letfn [(format [size type prefix]
            (str (pp/cl-format nil "~,1f" (/ size type)) " " prefix))]
    (condp <= size
      terrabyte
      (format size terrabyte "TB")

      gigabyte
      (format size gigabyte "GB")

      megabyte
      (format size megabyte "MB")

      kilobyte
      (format size kilobyte "KB")

      (str size " B"))))

(defn- hide-attributes
  "Clear all dmesg log entries from state"
  [{:keys [path]}] (comment "placeholder for IDE assistance"))

(defmethod m/mutate 'thonix.drives/hide-attributes
  [{:keys [state] :as inp} k {:keys [path]}]
  {:value  {:keys [[:drives/by-path path]]}
   :action #(swap! state assoc-in
                   [:drives/by-path path :show-attributes]
                   false)})

(defn- show-attributes
  "Clear all dmesg log entries from state"
  [{:keys [path]}] (comment "placeholder for IDE assistance"))

(defmethod m/mutate 'thonix.drives/show-attributes
  [{:keys [state] :as inp} k {:keys [path]}]
  {:value  {:keys [[:drives/by-path path]]}
   :action #(swap! state assoc-in
                   [:drives/by-path path :show-attributes]
                   true)})


(defui ^:once Drive
  static om/Ident
  (ident [this {:keys [path]}]
    [:drives/by-path path])
  static om/IQuery
  (query [this]
    [:vendor :name :path :size :ok :show-attributes {:attributes [:label :content]}])
  Object
  (clicked [this ev]
    (let [{:keys [path show-attributes]} (om/props this)]
      (if show-attributes
        (om/transact! this `[(hide-attributes {:path ~path})])
        (om/transact! this `[(show-attributes {:path ~path})]))))
  (render [this]
    (let [{:keys [vendor name path size ok show-attributes attributes]} (om/props this)]
      (dom/div #js {:className "drive" :key path}
        (dom/div #js {:className "drive-info" :key "drive-info"
                      :onClick #(.clicked this %)}
          (ui/icon "hdd-o" {:style #js {:fontSize  "30px" :float "left" :width "34px"
                                            :color (if ok "black" "red")}})
          (dom/div #js {:className "name" :key "name"}
            (str vendor " " name))
          (dom/div #js {:className "size" :key "size"}
            (human-size size)))
        (when show-attributes
          (dom/div #js {:className "drive-attributes" :key "drive-attributes"}
            (map #(dom/div #js {:className "attribute" :key (str "attribute-" (:label %))}
                    (dom/span #js {:className "label" :key "label"} (:label %))
                    (dom/span #js {:className "content" :key "content"} (:content %)))
                 attributes)))))))

(def drive (om/factory Drive))

(defui ^:once Drives
  Object
  (render [this]
    (let [drives (om/props this)]
      (dom/div #js {:className "drives"}
        (map drive drives)))))

(def drives (om/factory Drives))
