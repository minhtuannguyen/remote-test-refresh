(ns leiningen.remote-test-refresh-test
  (:require [clojure.test :refer :all]
            [leiningen.remote-test-refresh :as rt]))

(deftest ^:unit test-determin-asset-paths
  (testing "extract option correctly"
    (is (= #{"src" "resources" "test"}
           (-> {}
               (rt/determin-asset-paths)
               (set))))

    (is (= #{"folder1" "folder2" "folder3"}
           (-> {:source-paths   ["folder1"]
                :resource-paths ["folder2"]
                :test-paths     ["folder3"]}
               (rt/determin-asset-paths)
               (set))))

    (is (= #{"folder1" "folder2" "test"}
           (-> {:source-paths   ["folder1"]
                :resource-paths ["folder2"]}
               (rt/determin-asset-paths)
               (set))))))

(deftest ^:unit test-determine-connection-parameter
  (testing "correct path"
    (is (= {:host        "host"
            :remote-path "path/"
            :repo        "project"
            :user        "user"}

           (-> {:name        "project"
                :remote-test {:user        "user"
                              :host        "host"
                              :remote-path "path/"}}
               (rt/determine-connection-parameter))))

    (is (= {:host        "host"
            :remote-path "path/"
            :repo        "project"
            :user        "user"}
           (-> {:name        "project"
                :remote-test {:user        "user"
                              :host        "host"
                              :remote-path "path"}}
               (rt/determine-connection-parameter)))))

  (testing "connection parameter must be found"
    (is (= {:host        "host"
            :remote-path "path/"
            :repo        "project"
            :user        "user"}
           (-> {:name        "project"
                :remote-test {:user        "user"
                              :host        "host"
                              :remote-path "path/"}}
               (rt/determine-connection-parameter))))

    (is (thrown? AssertionError
                 (-> {:name        "project"
                      :remote-test {:user        ""
                                    :host        "host"
                                    :remote-path "path"}}
                     (rt/determine-connection-parameter))))

    (is (thrown? AssertionError
                 (-> {:name        "project"
                      :remote-test {:user        "user"
                                    :remote-path "path"}}
                     (rt/determine-connection-parameter))))

    (is (thrown? AssertionError
                 (-> {:name        "project"
                      :remote-test {:user        "user"
                                    :host        "host"
                                    :remote-path ""}}
                     (rt/determine-connection-parameter))))

    (is (thrown? AssertionError
                 (-> {:name        ""
                      :remote-test {:user        "user"
                                    :host        "host"
                                    :remote-path "path"}}
                     (rt/determine-connection-parameter))))))

(deftest ^:unit test-transfer-per-ssh
  (testing "check for correct status"
    (let [steps [(fn [_] {:status :success}) (fn [_] {:status :success})]
          option {}]
      (is (= :success (:status (rt/transfer-per-ssh steps option)))))

    (let [steps [(fn [_] {:status :failed}) (fn [_] {:status :success})]
          option {}]
      (is (= :failed (:status (rt/transfer-per-ssh steps option)))))))
