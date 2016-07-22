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

(deftest ^:unit test-determine-connection-parameter
  (testing "correct path"
    (is (= {:host        "host"
            :remote-path "path/"
            :repo        "project"
            :user        "user"
            :password    "secret"}

           (-> {:name        "project"
                :remote-test {:user        "user"
                              :password    "secret"
                              :host        "host"
                              :remote-path "path/"}}
               (rt/ssh-parameters))))

    (is (= {:host        "host"
            :password    "secret"
            :remote-path "path/"
            :repo        "project"
            :user        "user"}
           (-> {:name        "project"
                :remote-test {:user        "user"
                              :password    "secret"
                              :host        "host"
                              :remote-path "path"}}
               (rt/ssh-parameters)))))

  (testing "connection parameter must be found"
    (is (= {:host        "host"
            :remote-path "path/"
            :password    "secret"
            :repo        "project"
            :user        "user"}
           (-> {:name        "project"
                :remote-test {:user        "user"
                              :password    "secret"
                              :host        "host"
                              :remote-path "path/"}}
               (rt/ssh-parameters))))

    (is (thrown? AssertionError
                 (-> {:name        "project"
                      :remote-test {:user        ""
                                    :host        "host"
                                    :password    "secret"
                                    :remote-path "path"}}
                     (rt/ssh-parameters))))

    (is (thrown? AssertionError
                 (-> {:name        "project"
                      :remote-test {:user        "user"
                                    :password    "secret"
                                    :remote-path "path"}}
                     (rt/ssh-parameters))))

    (is (thrown? AssertionError
                 (-> {:name        "project"
                      :remote-test {:user        "user"
                                    :password    "secret"
                                    :host        "host"
                                    :remote-path ""}}
                     (rt/ssh-parameters))))

    (is (thrown? AssertionError
                 (-> {:name        ""
                      :remote-test {:user        "user"
                                    :password    "secret"
                                    :host        "host"
                                    :remote-path "path"}}
                     (rt/ssh-parameters))))

    (is (thrown? AssertionError
                 (-> {:name        ""
                      :remote-test {:user        "user"
                                    :host        "host"
                                    :remote-path "path"}}
                     (rt/ssh-parameters))))))

(deftest ^:unit test-transfer-per-ssh
  (testing "check for correct status"
    (let [steps [(fn [_ _] {:status :success}) (fn [_ _] {:status :success})]
          option {}
          session {}]
      (is (= :success (:status (rt/transfer-per-ssh steps option session)))))

    (let [steps [(fn [_ _] {:status :failed}) (fn [_ _] {:status :success})]
          option {}
          session {}]
      (is (= :failed (:status (rt/transfer-per-ssh steps option session)))))))
