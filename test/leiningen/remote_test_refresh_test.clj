(ns leiningen.remote-test-refresh-test
  (:require [clojure.test :refer :all]
            [leiningen.remote-test-refresh :as rt]
            [leiningen.remote.transfer :as transfer]
            [leiningen.remote.parameter :as p]))

(deftest ^:unit test-determin-asset-paths
  (testing "extract option correctly"
    (is (= #{"src" "resources" "test"}
           (-> {}
               (p/asset-paths)
               (set))))

    (is (= #{"folder1" "folder2" "folder3"}
           (-> {:source-paths   ["folder1"]
                :resource-paths ["folder2"]
                :test-paths     ["folder3"]}
               (p/asset-paths)
               (set))))

    (is (= #{"folder1" "folder2" "test"}
           (-> {:source-paths   ["folder1"]
                :resource-paths ["folder2"]}
               (p/asset-paths)
               (set))))))

(deftest ^:unit test-start-parameters
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
             (p/ask-for-parameters))))

  (is (= {:with-system-agent true}
         (-> {:name        "project"
              :remote-test {:with-system-agent true}}
             (p/ask-for-auth)))))

(deftest ^:unit test-normalize-remote-path
  (is (= "/path/to/"
         (p/normalize-remote-path "/path/to")
         (p/normalize-remote-path "/path/to/"))))

(deftest ^:unit test-transfer-per-ssh
  (testing "check for correct status"
    (let [transfer-cmd {:scp   (fn [])
                        :ssh   (fn [])
                        :shell (fn [])}
          steps [(fn [_ _ _] {:status :success})
                 (fn [_ _ _] {:status :success})]
          option {}
          session {}]
      (is (= :success (:status (transfer/run-per-ssh option session transfer-cmd steps)))))

    (let [transfer-cmd {:scp   (fn [])
                        :ssh   (fn [])
                        :shell (fn [])}
          steps [(fn [_ _ _] {:status :failed})
                 (fn [_ _ _] {:status :success})]
          option {}
          session {}]
      (is (= :failed (:status (transfer/run-per-ssh option session transfer-cmd steps)))))))

(deftest ^:unit test-session-option
  (testing "correct session option"
    (is (= {:username                 "user"
            :strict-host-key-checking :no}
           (p/session-option {:user     "user"
                              :password "secret"}
                             true)))))

(deftest ^:unit test-valid-port
  (is (false? (p/valid-port? -1)))
  (is (false? (p/valid-port? 0)))
  (is (false? (p/valid-port? :empty)))
  (is (false? (p/valid-port? :invalid)))
  (is (false? (p/valid-port? 1)))
  (is (true? (p/valid-port? 1024))))

(deftest ^:unit test-create-notify-command
  (is (= ["do notify" "msg"]
         (rt/make-notify-command "do notify" "msg")))
  (is (= ["do" "notify" "msg"]
         (rt/make-notify-command ["do" "notify"] "msg"))))

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
  (is (true? (p/should-use-system-agent? true)))
  (is (true? (p/should-use-system-agent? "y")))
  (is (false? (p/should-use-system-agent? "n"))))

(deftest ^:unit test-run-cmd-remotely
  (let [log-state (atom [])
        options-state (atom {})
        ssh (fn [_ options]
              (reset! options-state options)
              {:out-stream "test-resources/console.txt"})
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

(deftest ^:unit test-run-command-and-forward-port
  (testing "port-forward and run-command should be started here"
    (let [ssh-state (atom {})
          log-state (atom {})
          forward-port-state (atom {})

          transfer-cmd {:ssh          (fn [_ options]
                                        (reset! ssh-state options)
                                        {:out-stream "test-resources/console.txt"})
                        :log          (fn [& args] (reset! log-state args))
                        :forward-port (fn [session port func]
                                        (func)
                                        (reset! forward-port-state {:port    port
                                                                    :session session}))}
          parameter {:forwarding-port 9000 :command "run cmd"}
          session {:my :session}]
      (rt/run-command-and-forward-port session parameter transfer-cmd)
      (is (= {:agent-forwarding true, :cmd "cd ;run cmd", :out :stream, :pty true} @ssh-state))
      (is (= ["some line 2"] @log-state))
      (is (= {:port 9000 :session session} @forward-port-state))))

  (testing "only run-cmd-remotely should be invoked here"
    (let [ssh-state (atom {})
          log-state (atom {})
          forward-port-state (atom {})
          transfer-cmd {:ssh          (fn [_ options]
                                        (reset! ssh-state options)
                                        {:out-stream "test-resources/console.txt"})
                        :log          (fn [& args] (reset! log-state args))
                        :forward-port (fn [_ _ _] (reset! forward-port-state {:foo :bar}))}
          parameter {:command "run cmd"}
          session {:my :session}]

      (rt/run-command-and-forward-port session parameter transfer-cmd)
      (is (= {} @forward-port-state))
      (is (= {:agent-forwarding true, :cmd "cd ;run cmd", :out :stream, :pty true} @ssh-state))
      (is (= ["some line 2"] @log-state))))

  (testing "only port-forward should be invoked here"
    (let [ssh-state (atom {})
          log-state (atom {})
          forward-port-state (atom {})
          transfer-cmd {:ssh          (fn [_ options]
                                        (reset! ssh-state options)
                                        {:out-stream "test-resources/console.txt"})
                        :log          (fn [& args] (reset! log-state args))
                        :forward-port (fn [session port _]
                                        (reset! forward-port-state {:port    port
                                                                    :session session}))}
          parameter {:forwarding-port 9000}
          session {:my :session}]

      (rt/run-command-and-forward-port session parameter transfer-cmd)
      (is (= {:port 9000 :session session} @forward-port-state))
      (is (= {} @ssh-state))
      (is (= {} @log-state))))

  (testing "only endless loop should be started"
    (let [ssh-state (atom {})
          log-state (atom {})
          forward-port-state (atom {})
          transfer-cmd {:ssh          (fn [_ options]
                                        (reset! ssh-state options)
                                        {:out-stream "test-resources/console.txt"})
                        :log          (fn [& args] (reset! log-state args))
                        :loop         (fn [] "endless loop")
                        :forward-port (fn [session port _]
                                        (reset! forward-port-state {:port    port
                                                                    :session session}))}
          parameter {}
          session {:my :session}
          status (rt/run-command-and-forward-port session parameter transfer-cmd)]

      (is (= "endless loop" status))
      (is (= {} @forward-port-state))
      (is (= {} @ssh-state))
      (is (= {} @log-state)))))

(deftest ^:unit test-create-patch!
  (testing "port-forward and run-command should be started here"
    (let [shell-state (atom {})
          transfer-cmd {:shell (fn [& args]
                                 (reset! shell-state args)
                                 (throw (new RuntimeException "ex")))}
          status (transfer/create-patch! transfer-cmd {} {})]
      (is (= ["git" "diff" "HEAD"] @shell-state))
      (is (= {:step :create-patch :status :failed :error "ex"} status)))))

(deftest ^:unit test-apply-patch!
  (testing "apply path successful"
    (let [ssh-state (atom {})
          transfer-cmd {:ssh (fn [_ options]
                               (reset! ssh-state options)
                               {:exit 0})}
          parameters {:repo "repo" :remote-path "path/to/"}
          session {:my :session}
          status (transfer/apply-patch! transfer-cmd parameters session)]
      (is (= {:agent-forwarding true
              :cmd              (str "cd path/to/repo;git reset --hard;"
                                     "git apply --whitespace=warn test-refresh.remote.patch;"
                                     "rm -f test-refresh.remote.patch;")}
             @ssh-state))
      (is (= {:status :success :step :apply-patch} status))))

  (testing "apply path failed"
    (let [transfer-cmd {:ssh (fn [_ _] {:exit 1 :err "so wrong"})}
          parameters {:repo "repo" :remote-path "path/to/"}
          session {:my :session}
          status (transfer/apply-patch! transfer-cmd parameters session)]
      (is (= {:error "so wrong" :status :failed :step :apply-patch} status)))))

(deftest ^:unit test-upload-patch!
  (testing "update successful"
    (let [shell-state (atom {})
          scp-state (atom {})
          transfer-cmd {:scp   (fn [session from to]
                                 (reset! scp-state {:session session :from from :to to}))
                        :shell (fn [& args] (reset! shell-state args) {:exit 0})}
          parameters {:repo "repo" :remote-path "path/to/"}
          session {:my :session}
          status (transfer/upload-patch! transfer-cmd parameters session)]
      (is (= ["rm" "-f" "test-refresh-local.patch"] @shell-state))
      (is (= {:from    "test-refresh-local.patch"
              :session {:my :session}
              :to      "path/to/repo/test-refresh.remote.patch"}
             @scp-state))
      (is (= {:status :success :step :upload-patch} status))))

  (testing "update failed"
    (let [transfer-cmd {:scp   (fn [_ _ _])
                        :shell (fn [& _] {:exit 1 :err "so wrong"})}
          parameters {:repo "repo" :remote-path "path/to/"}
          session {:my :session}
          status (transfer/upload-patch! transfer-cmd parameters session)]
      (is (= {:error "so wrong" :status :failed :step :upload-patch} status)))))

(deftest ^:unit test-remote-patch-file-path
  (is (= "parent/path/repo/test-refresh.remote.patch"
         (transfer/create-remote-patch-file-path "parent/path/" "repo"))))

(deftest ^:unit pprint-test
  (let [state (atom [])
        print-fn (fn [& args] (swap! state conj args))]
    (rt/pprint-parameter {:param-1 "1" :param-2 "2"} print-fn)
    (is (= ['("\n* Starting session with the parameters:")
            '("   " :param-1 ":" "1")
            '("   " :param-2 ":" "2")
            '("\n")]
           @state))))