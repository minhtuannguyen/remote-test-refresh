(defproject minhtuannguyen/remote-test-refresh "0.1.2-SNAPSHOT"
  :description "Automatically synchronize with remote project over ssh  when files change"
  :url "https://github.com/minhtuannguyen/remote-test-refresh"
  :license {:name "Eclipse Public License" :url "http://www.eclipse.org/legal/epl-v10.html"}
  :eval-in-leiningen true

  :dependencies [[org.clojure/tools.namespace "0.2.11" :exclusions [org.clojure/clojure]]
                 [clj-ssh "0.5.14"]]

  :profiles {:uberjar {:aot :all}
             :test    {:resource-paths ["test-resources"]}
             :dev     {:dependencies [[pjstadig/humane-test-output "0.8.0"]]
                       :plugins      [[lein-cljfmt "0.5.3"]
                                      [lein-cloverage "1.0.6"]
                                      [jonase/eastwood "0.2.3"]
                                      [lein-kibit "0.1.2"]]}})
