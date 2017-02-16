(ns thonix.term
  (:require [xterm :as xt]
            [xterm.addons.fit :as fit]
            [xterm.addons.attach :as attach]
            [xterm.addons.fullscreen :as fullscreen]
            [goog.dom :as dom]
            [ajax.core :refer [GET POST]]))


(def active (atom {}))

(defn resize [size]
  (when-let [pid (:pid @active)]
    (let [cols (.-cols size)
          rows (.-rows size)
          url (str "//" (.-hostname js/location) ":3001/terminals/" pid "/size?cols=" cols "&rows=" rows)]
      (POST url))))

(defn setTerminalSize []
  (let [c (dom/getElement "terminal-container")
        cols (-> (dom/getElement "cols") .-value js/parseInt)
        rows (-> (dom/getElement "rows") .-value js/parseInt)
        state @active
        width (str (* cols (:char-width state)) "px")
        height (str (* rows (:char-height state)) "px")]
    (-> c .-style .-width (set! width))
    (-> c .-style .-height (set! height))
    (.resize (:term state) cols rows)))

(defn runRealTerminal [term socket]
  (attach/attach term socket)
  (set! (.-_initialized term) true)
  (set! (.-onclose socket)
        (fn []
          (.writeln term "Exited terminal")))
  term)

(defn runFakeTerminal [term]
  (when-not (.-_initialized term)
    (set! (.-_initialized term) true)
    (.writeln term "Welcome to xterm.js")
    (.writeln term "This is a local terminal emulation, without a real terminal in the back-end.")
    (.writeln term "Type some keys and commands to play around.")
    (.writeln term "")
    (let [shellprompt "$ "]
      (set! (.-prompt term)
            (fn []
              (.write term (str "\r\n" shellprompt))))
      (.prompt term)
      (.on term "key"
           (fn [key ev]
             (cond
               (= (.-keyCode ev) 13) (.prompt term)
               (= (.-keyCode ev) 8) (if (> (.-x term) (count shellprompt))
                                      (.write term "\b \b"))
               (not (or (.-altKey ev)
                        (.-altGraphKey ev)
                        (.-ctrlKey ev)
                        (.-metaKey ev)))
               (.write term key)))))
    (.on term "paste"
         (fn [data _]
           (.write term data)))))

(defn createTerminal []
  (let [c (dom/getElement "terminal-container")
        cursorBlink (dom/getElement "option-cursor-blink")
        colsElement (dom/getElement "cols")
        rowsElement (dom/getElement "rows")]
    (dom/removeChildren c)

    (let [term (new xt/Xterm #js {:cursorBlink (.-checked cursorBlink)})
          protocol (if (= (.-protocol js/location) "https:")
                     "wss://"
                     "ws://")
          socketURL (str protocol (.-hostname js/location)
                         ":" 3001
                         #_(when-let [port (.-port js/location)]
                             (str ":" port))
                         "/terminals/")]
      (swap! active assoc :term term)
      (.on term "resize" resize)
      (.open term c)
      (fit/fit term)
      (let [initialGeometry (fit/proposeGeometry term)
            cols (.-cols initialGeometry)
            rows (.-rows initialGeometry)]
        (aset colsElement "value" cols)
        (aset rowsElement "value" rows)
        (swap! active assoc :char-width (.ceil js/Math (/ (-> term .-element .-offsetWidth) cols))
               :char-height (.ceil js/Math (/ (-> term .-element .-offsetHeight) rows)))
        (POST (str "//" (.-hostname js/location) ":3001/terminals?cols=" cols "&rows=" rows)
              {:handler (fn [res]
                          (let [pid (str res)]
                            (swap! active assoc :pid pid)
                            (let [socket (new js/WebSocket (str socketURL pid))]
                              (set! (.-onopen socket) #(runRealTerminal term socket))
                              (set! (.-onclose socket) #(runFakeTerminal term))
                              (set! (.-onerror socket) #(runFakeTerminal term)))))}))
      )))



(defn main []
  (let [c (.. js/document (createElement "DIV"))
        cursorBlink (dom/getElement "option-cursor-blink")
        colsElement (dom/getElement "cols")
        rowsElement (dom/getElement "rows")]
    (aset c "innerHTML" (str "<p>i'm dynamically wee created</p>"))
    (.. js/document (getElementById "container") (appendChild c))
    (.addEventListener cursorBlink "change" createTerminal)
    (.addEventListener colsElement "change" setTerminalSize)
    (.addEventListener rowsElement "change" setTerminalSize))
  (createTerminal))
