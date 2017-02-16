(ns thonix.graphql.network
  (:require [untangled.client.logging :as log]
            [cognitect.transit :as ct]
            [goog.events :as events]
            [om.transit :as t]
            [clojure.string :as str]
            [untangled.client.impl.network :refer [UntangledNetwork IXhrIOCallbacks response-ok response-error]]
            [thonix.graphql :as graphql])
  (:import [goog.net XhrIo EventType]))

(declare make-untangled-network)

(defn make-xhrio "This is here (not inlined) to make mocking easier." [] (XhrIo.))

(defn response-mapper [mapper valid-data-callback error-callback]
  (fn [{data "data" errors "errors" :as response}]
    (when (and data valid-data-callback)
      (let [mapped (graphql/apply-mapper mapper data)]
        (log/info "Mapped" mapped)
        (valid-data-callback mapped)))
    (when (and errors error-callback)
      (error-callback (str/join ", " (map #(get % "message") errors)) errors))))

(defn parse-response
  "An XhrIo-specific implementation method for interpreting the server response."
  ([xhr-io] (parse-response xhr-io nil))
  ([xhr-io read-handlers]
   (try (let [text (.getResponseText xhr-io)
              base-handlers {"f" (fn [v] (js/parseFloat v)) "u" cljs.core/uuid}
              handlers (if (map? read-handlers) (merge base-handlers read-handlers) base-handlers)]
          (if (str/blank? text)
            (.getStatus xhr-io)
            (ct/read (t/reader {:handlers handlers})
                     (.getResponseText xhr-io))))
        (catch js/Object e {:error 404 :message "Server down"}))))

(defrecord Network [url request-transform global-error-callback complete-app transit-handlers]
  IXhrIOCallbacks
  (response-ok [this xhr-io valid-data-callback]
    ;; Implies:  everything went well and we have a good response
    ;; (i.e., got a 200).
    (try
      (let [read-handlers (:read transit-handlers)
            query-response (parse-response xhr-io read-handlers)]
        (when (and query-response valid-data-callback) (valid-data-callback query-response)))
      (finally (.dispose xhr-io))))
  (response-error [this xhr-io error-callback]
    ;; Implies:  request was sent.
    ;; *Always* called if completed (even in the face of network errors).
    ;; Used to detect errors.
    (try
      (let [status (.getStatus xhr-io)
            log-and-dispatch-error (fn [str error]
                                     ;; note that impl.application/initialize will partially apply the
                                     ;; app-state as the first arg to global-error-callback
                                     (log/error str)
                                     (when @global-error-callback
                                       (@global-error-callback status error))
                                     (error-callback error))]
        (if (zero? status)
          (log-and-dispatch-error
            (str "UNTANGLED NETWORK ERROR: No connection established.")
            {:type :network})
          (log-and-dispatch-error
            (str "SERVER ERROR CODE: " status)
            (parse-response xhr-io transit-handlers))))
      (finally (.dispose xhr-io))))

  UntangledNetwork
  (send [this edn ok err]
    (let [xhrio (make-xhrio)
          {:keys [query mapper]} (graphql/query edn)
          handlers (or (:write transit-handlers) {})
          headers {"Content-Type" "application/json"}
          {:keys [body headers]} (cond-> {:body {:query         query
                                                 :operationName "OmQuery"} :headers headers}
                                   request-transform request-transform)
          post-data (js/JSON.stringify (clj->js body))
          headers (clj->js headers)
          log-and-dispatch-error (fn [str error]
                                   ;; note that impl.application/initialize will partially apply the
                                   ;; app-state as the first arg to global-error-callback
                                   (log/error str)
                                   (when @global-error-callback
                                     (@global-error-callback 200 error))
                                   (err error))
          mapped-ok (response-mapper mapper ok log-and-dispatch-error)]
      (.send xhrio url "POST" post-data headers)
      (events/listen xhrio (.-SUCCESS EventType) #(response-ok this xhrio mapped-ok))
      (events/listen xhrio (.-ERROR EventType) #(response-error this xhrio err))))
  (start [this app]
    (assoc this :complete-app app)))


(defn make-untangled-network
  "TODO: This is PUBLIC API! Should not be in impl ns.

  Build an Untangled Network object using the default implementation.

  Features:

  - Can configure the target URL on the server for Om network requests
  - Can supply a (fn [{:keys [body headers] :as req}] req') to transform arbitrary requests (e.g. to add things like auth headers)
  - Supports a global error callback (fn [status-code error] ) that is notified when a 400+ status code or hard network error occurs
  - `transit-handlers`: A map of transit handlers to install on the reader, such as

   `{ :read { \"thing\" (fn [wire-value] (convert wire-value))) }
      :write { Thing (ThingHandler.) } }`

   where:

   (defrecord Thing [foo])

   (deftype ThingHandler []
     Object
     (tag [_ _] \"thing\")
     (rep [_ thing] (make-raw thing))
     (stringRep [_ _] nil)))
  "
  [url & {:keys [request-transform global-error-callback transit-handlers]}]
  (map->Network {:url                   url
                 :transit-handlers      transit-handlers
                 :request-transform     request-transform
                 :global-error-callback (atom global-error-callback)}))