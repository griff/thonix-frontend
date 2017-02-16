(ns thonix.core
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [compassus.core :as compassus]
            [om.util :as util]
            [clojure.string :as string]
            [untangled.client.core :as uc]
            [untangled.client.logging :as log]
            [thonix.ui.routing :as r]
            [bidi.bidi :as bidi]
            [pushy.core :as pushy]
            [untangled.client.data-fetch :as df]
            [thonix.ui.states.booting :as booting]
            [thonix.graphql.network :as network]
            [thonix.ui.components.log :as clog]))

(defn merge-mutations [state k p]
  (log/info "Got return value for " k " -> " p)
  state)

(defonce app
  (atom
    (uc/new-untangled-client
      :mutation-merge merge-mutations
      :networking (network/make-untangled-network
                    "/graphql"
                    :global-error-callback (constantly nil))
      :started-callback (fn [{:keys [reconciler]}]
                          ; NOTE: To get a root re-render on routing, you should use the UI root as the component on the transact
                          (let [r (om/app-root reconciler)]
                            (reset! r/history (pushy/pushy (partial r/set-route! r) (partial bidi/match-route r/app-routes))))
                          (pushy/start! @r/history)
                          (df/load-data reconciler [:server/state {:boot [:total-steps {:steps (om/get-query clog/LogBlock)}]}]
                                        :post-mutation 'thonix.ui.routing/update-server-state
                                        :refresh [:state])
                          ))))
