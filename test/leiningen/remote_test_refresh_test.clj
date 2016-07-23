(ns leiningen.remote-test-refresh-test
  (:require [clojure.test :refer :all]
            [leiningen.remote-test-refresh :as rt]))

(deftest ^:unit test-determin-asset-paths
  (testing "extract option correctly"
    (is (= #{"src" "resources" "test"}
           (-> {}
               (rt/find-asset-paths)
               (set))))

    (is (= #{"folder1" "folder2" "folder3"}
           (-> {:source-paths   ["folder1"]
                :resource-paths ["folder2"]
                :test-paths     ["folder3"]}
               (rt/find-asset-paths)
               (set))))

    (is (= #{"folder1" "folder2" "test"}
           (-> {:source-paths   ["folder1"]
                :resource-paths ["folder2"]}
               (rt/find-asset-paths)
               (set))))))

(deftest ^:unit test-start-parameters
  (testing "correct path"
    (is (= {:host            "host"
            :remote-path     "path/"
            :repo            "project"
            :user            "user"
            :command         "ls"
            :forwarding-port "90"
            :auth            {:with-system-agent true}}

           (-> {:name        "project"
                :remote-test {:user              "user"
                              :host              "host"
                              :command           "ls"
                              :forwarding-port   "90"
                              :with-system-agent true
                              :remote-path       "path/"}}
               (rt/start-parameters)))))

  (is (= {:host            "host"
          :remote-path     "path/"
          :command         "ls"
          :forwarding-port "90"
          :auth            {:with-system-agent true}
          :repo            "project"
          :user            "user"}
         (-> {:name        "project"
              :remote-test {:user              "user"
                            :password          "secret"
                            :host              "host"
                            :forwarding-port   "90"
                            :with-system-agent true
                            :command           "ls"
                            :remote-path       "path"}}
             (rt/start-parameters)))))

(deftest ^:unit test-transfer-per-ssh
  (testing "check for correct status"
    (let [steps [(fn [_ _] {:status :success}) (fn [_ _] {:status :success})]
          option {}
          session {}]
      (is (= :success (:status (rt/transfer-per-ssh option session steps)))))

    (let [steps [(fn [_ _] {:status :failed}) (fn [_ _] {:status :success})]
          option {}
          session {}]
      (is (= :failed (:status (rt/transfer-per-ssh option session steps)))))))

(deftest ^:unit test-session-option
  (testing "correct session option"
    (is (= {:username                 "user"
            :strict-host-key-checking :no}

           (rt/session-option {:user     "user"
                               :password "secret"}
                              true)))

    (is (= {:username                 "user"
            :password                 "secret"
            :strict-host-key-checking :no}

           (rt/session-option {:user     "user"
                               :password "secret"}
                              false)))))