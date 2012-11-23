(defproject bestcase "0.1.0"
  :description "An A/B testing library for clojure"
  :url "https://github.com/jeandenis/bestcase"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/core.incubator "0.1.2"]
                 [org.clojure/math.numeric-tower "0.0.1"]
                 [com.taoensso/carmine "0.11.3"]
                 [ring "1.1.6"]
                 [compojure "1.1.3"]
                 [hiccup "1.0.1"]]
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.3"]
                        [midje "1.4.0" :exclusions [org.clojure/clojure]]
                        [com.stuartsierra/lazytest "1.2.3"]]
         :plugins [[lein-midje "2.0.1"]
                   [codox "0.6.3"]]
         :codox {:exclude [bestcase.for-testing]
                 :src-dir-uri
                 "http://github.com/jeandenis/bestcase/tree/master/"
                 :src-linenum-anchor-prefix "L"}
         :repositories {"stuart" "http://stuartsierra.com/maven2"}}})
                
