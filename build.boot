(set-env!
  :source-paths    #{"src"}
  :resource-paths    #{"html" "libs" "resources"}
  :dependencies '[; Boot setup
                  [adzerk/boot-cljs "1.7.228-2"]
                  [adzerk/boot-cljs-repl   "0.3.3"]
                  [boot-deps "0.1.6"]
                  [bendyorke/boot-postcss "0.0.1" :scope "test"]
                  [adzerk/boot-reload "0.4.13" :scope "test"]
                  [pandeiro/boot-http          "0.7.6" :scope "test"]
                  [com.cemerick/piggieback "0.2.1"  :scope "test"]
                  [weasel                  "0.7.0"  :scope "test"]
                  [org.clojure/tools.nrepl "0.2.12" :scope "test"]
                  [cljsjs/boot-cljsjs "0.5.2" :scope "test"]
                  [griff/boot2nix "1.1.0" :scope "test"]
                  [org.clojure/clojure "1.9.0-alpha14"]

                  ; App dependencies
                  [org.clojure/clojurescript   "1.9.293"]
                  [org.omcljs/om "1.0.0-alpha47"]
                  [navis/untangled-client "0.6.1"]
                  [sablono "0.7.6"]
                  #_[cljs-react-reload "0.1.1"]
                  [cljsjs/react-datepicker "0.29.0-0"]
                  [cljs-ajax "0.5.8"]
                  [cljsjs/react-bootstrap "0.30.6-0"]
                  [compassus "1.0.0-alpha2"]
                  [bidi "2.0.16"]
                  [kibu/pushy "0.3.6"]
                  [org.clojure/core.async "0.2.395"]
                  [camel-snake-kebab "0.4.0"]

                  ; Other dependencies
                  [devcards "0.2.2"]
                  #_[devcards-om-next "0.3.0"]
                  [binaryage/devtools "0.8.3"]])

(task-options!
  pom {:project  'griff/thonix-frontend
       :version "0.1.0-SNAPSHOT"})

(require '[adzerk.boot-reload :refer [reload]]
         '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
         '[adzerk.boot-cljs :refer [cljs]]
         '[griff.boot2nix :refer [boot2nix]]
         '[cljsjs.boot-cljsjs.packaging :refer [deps-cljs]]
         '[bendyorke.boot-postcss :refer [postcss]]
         '[pandeiro.boot-http    :refer [serve]])

(deftask dev []
  (comp (watch)
        (speak)
        (reload :ids #{"app"}
                :only-by-re [#"^app\.out/.*|css/.*|xterm/.*|.*\.html"]
                :on-jsload 'thonix.main/reload)
        (reload :ids #{"app-repl"}
                :only-by-re [#"^app-repl\.out/.*|css/.*|xterm/.*|.*\.html"]
                :on-jsload 'cljs.user/reload)
        (reload :ids #{"cards"}
                :only-by-re [#"^cards\.out/.*|css/.*|xterm/.*|.*\.html"]
                :on-jsload 'cards.ui/reload)
        (reload :ids #{"cards-repl"}
                :only-by-re [#"^cards-repl\.out/.*|css/.*|xterm/.*|.*\.html"]
                :on-jsload 'cards.ui/reload)
        (cljs-repl :ids #{"app-repl" "cards-repl"})
        (cljs :optimizations :none
              :compiler-options {:devcards true
                                 :preloads '[devtools.preload]})
        (target)
        (serve :dir "target")))

(replace-task!
  [r dev] (fn [& xs]
             ;; we only want to have "dev" included for the REPL task
             (merge-env! :source-paths #{"dev"})
             (apply r xs)))

(deftask prod []
         (comp
           (serve :dir "target/")
           (watch)
           (speak)
           (cljs :ids #{"app" "cards"} :source-map true :optimizations :advanced)
           (target)))

(deftask build []
  (comp
    (cljs :ids #{"app" "cards"} :source-map true :optimizations :advanced)
    (target)))

(deftask release []
  (comp
    (cljs :ids #{"app"} :source-map true :optimizations :advanced)
    (target :dir #{"release/"})))
