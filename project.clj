(defproject minhtuannguyen/remote-test-refresh "0.2.4-SNAPSHOT"
  :description "Automatically synchronize with remote project over ssh  when files change"
  :url "https://github.com/minhtuannguyen/remote-test-refresh"
  :license {:name "Eclipse Public License" :url "http://www.eclipse.org/legal/epl-v10.html"}
  :eval-in-leiningen true

  :dependencies [[org.clojure/tools.namespace "0.2.11" :exclusions [org.clojure/clojure]]
                 [clj-ssh "0.5.14"]
                 [org.clojure/core.async "0.4.474"]]

  :profiles {:uberjar {:aot :all}
             :test    {:resource-paths ["test-resources"]}
             :dev     {:dependencies [[pjstadig/humane-test-output "0.8.3"]]
                       :plugins      [[lein-cljfmt "0.5.7"]
                                      [lein-cloverage "1.0.10"]
                                      [jonase/eastwood "0.2.5"]
                                      [lein-kibit "0.1.6"]]}})
