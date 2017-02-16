(defproject
  thonix-frontend
  "0.1.0-SNAPSHOT"
  :dependencies
  [[adzerk/boot-cljs "1.7.228-2"]
   [adzerk/boot-cljs-repl "0.3.3"]
   [boot-deps "0.1.6"]
   [bendyorke/boot-postcss "0.0.1" :scope "test"]
   [adzerk/boot-reload "0.4.13" :scope "test"]
   [pandeiro/boot-http "0.7.6" :scope "test"]
   [com.cemerick/piggieback "0.2.1" :scope "test"]
   [weasel "0.7.0" :scope "test"]
   [org.clojure/tools.nrepl "0.2.12" :scope "test"]
   [cljsjs/boot-cljsjs "0.5.2" :scope "test"]
   [org.clojure/clojure "1.9.0-alpha14"]
   [org.clojure/clojurescript "1.9.293"]
   [org.omcljs/om "1.0.0-alpha47"]
   [navis/untangled-client "0.6.1"]
   [sablono "0.7.6"]
   [cljsjs/react-datepicker "0.29.0-0"]
   [cljs-ajax "0.5.8"]
   [cljsjs/react-bootstrap "0.30.6-0"]
   [compassus "1.0.0-alpha2"]
   [bidi "2.0.16"]
   [kibu/pushy "0.3.6"]
   [org.clojure/core.async "0.2.395"]
   [camel-snake-kebab "0.4.0"]
   [devcards "0.2.2"]
   [binaryage/devtools "0.8.3"]]
  :source-paths
  ["src" "libs" "html" "resources"])