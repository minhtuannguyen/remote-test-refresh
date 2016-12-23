(defproject minhtuannguyen/remote-test-refresh "0.2.2"
  :description "Automatically synchronize with remote project over ssh  when files change"
  :url "https://github.com/minhtuannguyen/remote-test-refresh"
  :license {:name "Eclipse Public License" :url "http://www.eclipse.org/legal/epl-v10.html"}
  :eval-in-leiningen true

  :dependencies [[org.clojure/tools.namespace "0.2.11" :exclusions [org.clojure/clojure]]
                 [clj-ssh "0.5.14"]
                 [org.clojure/core.async "0.2.395"]]

  :profiles {:uberjar {:aot :all}
             :test    {:resource-paths ["test-resources"]}
             :dev     {:dependencies [[pjstadig/humane-test-output "0.8.1"]]
                       :plugins      [[lein-cljfmt "0.5.6"]
                                      [lein-cloverage "1.0.9"]
                                      [jonase/eastwood "0.2.3"]
                                      [lein-kibit "0.1.3"]]}})
