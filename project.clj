(defproject bytegeist "0.1.0-SNAPSHOT"
  :description "Schemas for binary data"
  :url "https://github.com/yonatane/bytegeist"
  :license {:name "MIT License"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [io.netty/netty-buffer "4.1.49.Final"]]
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "1.0.0"]
                                  [criterium "0.4.5"]
                                  [com.clojure-goes-fast/clj-async-profiler "0.4.1"]]}}
  :repl-options {:init-ns bytegeist.dev}
  :global-vars {*warn-on-reflection* true}
  :pedantic? :abort)
