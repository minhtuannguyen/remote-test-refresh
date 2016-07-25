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
            :notify-command  nil
            :forwarding-port 90
            :auth            {:with-system-agent true}}

           (-> {:name        "project"
                :remote-test {:user              "user"
                              :host              "host"
                              :command           "ls"
                              :forwarding-port   90
                              :with-system-agent true
                              :remote-path       "path/"}}
               (rt/ask-for-parameters)))))

  (is (= {:host            "host"
          :remote-path     "path/"
          :command         "ls"
          :forwarding-port 90
          :auth            {:with-system-agent true}
          :notify-command  ["hi"]
          :repo            "project"
          :user            "user"}
         (-> {:name        "project"
              :remote-test {:user              "user"
                            :password          "secret"
                            :host              "host"
                            :forwarding-port   90
                            :notify-command    ["hi"]
                            :with-system-agent true
                            :command           "ls"
                            :remote-path       "path"}}
             (rt/ask-for-parameters)))))

(deftest ^:unit test-transfer-per-ssh
  (testing "check for correct status"
    (let [transfer-cmd {:scp   (fn [])
                        :ssh   (fn [])
                        :shell (fn [])}
          steps [(fn [_ _ _] {:status :success})
                 (fn [_ _ _] {:status :success})]
          option {}
          session {}]
      (is (= :success (:status (rt/transfer-per-ssh option session transfer-cmd steps)))))

    (let [transfer-cmd {:scp   (fn [])
                        :ssh   (fn [])
                        :shell (fn [])}
          steps [(fn [_ _ _] {:status :failed})
                 (fn [_ _ _] {:status :success})]
          option {}
          session {}]
      (is (= :failed (:status (rt/transfer-per-ssh option session transfer-cmd steps)))))))

(deftest ^:unit test-session-option
  (testing "correct session option"
    (is (= {:username                 "user"
            :strict-host-key-checking :no}

           (rt/session-option {:user     "user"
                               :password "secret"}
                              true)))))

(deftest ^:unit test-valid-port
  (is (false? (rt/valid-port? -1)))
  (is (false? (rt/valid-port? 0)))
  (is (false? (rt/valid-port? :empty)))
  (is (false? (rt/valid-port? :invalid)))
  (is (false? (rt/valid-port? 1)))
  (is (true? (rt/valid-port? 1024))))

(deftest ^:unit test-create-notify-command
  (is (= ["do notify" "msg"]
         (rt/create-notify-command "do notify" "msg")))
  (is (= ["do" "notify" "msg"]
         (rt/create-notify-command ["do" "notify"] "msg"))))

(deftest ^:unit test-has-port?
  (is (false? (rt/has-port? {:foo :bar})))
  (is (false? (rt/has-port? {})))
  (is (false? (rt/has-port? nil)))
  (is (true? (rt/has-port? {:forwarding-port 20}))))

(deftest ^:unit test-notify
  (let [state-shell (atom [])
        state-log (atom [])
        log (fn [& args] (reset! state-log args))
        shell (fn [& args] (reset! state-shell args))
        notify-cmd ["do" "something"]
        msg "msg"
        _ (rt/notify shell log notify-cmd msg)]
    (is (= ["do" "something" "msg"] @state-shell))
    (is (= ["*" "msg"] @state-log)))

  (let [state-shell (atom [])
        shell (fn [& args] (reset! state-shell args))
        state-log (atom [])
        log (fn [& args] (reset! state-log args))
        notify-cmd []
        msg "msg"
        _ (rt/notify shell log notify-cmd msg)]
    (is (= [] @state-shell))
    (is (= ["*" "msg"] @state-log))))

(deftest ^:unit test-should-use-system-agent?
  (is (true? (rt/should-use-system-agent? true)))
  (is (true? (rt/should-use-system-agent? "y")))
  (is (false? (rt/should-use-system-agent? "n"))))

(deftest ^:unit test-run-cmd-remotely
  (let [log-state (atom [])
        options-state (atom {})
        ssh (fn [_ options] (reset! options-state options) {:out-stream "test-resources/console.txt"})
        log (fn [& args] (swap! log-state conj args))
        parameter {:command "do somthing" :repo "repo-name" :remote-path "repo-path"}
        session {}
        _ (rt/run-cmd-remotely ssh log parameter session)]

    (is (= {:agent-forwarding true
            :cmd              "cd repo-pathrepo-name;do somthing"
            :out              :stream
            :pty              true}
           @options-state))

    (is (= [["some line 1"] ["some line 2"]] @log-state))))