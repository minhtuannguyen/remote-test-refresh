(ns leiningen.remote-test-refresh
  (:require [clojure.tools.namespace.dir :as dir]
            [clojure.tools.namespace.track :as t]
            [clojure.string :as str]
            [leiningen.core.main :as m]
            [leiningen.remote.utils.utils :as u]
            [clj-ssh.ssh :as ssh]
            [clojure.core.async :as async]
            [clojure.java.shell :as sh]
            [clojure.java.io :as io]))

;;; TRANSFER STEPS
(def verbose false)
(def local-patch-file-name "test-refresh-local.patch")
(def remote-patch-file-name "test-refresh.remote.patch")

(defn remote-patch-file-path [remote-path repo]
  (str remote-path repo "/" remote-patch-file-name))

(defn create-patch! [transfer-cmd _ _]
  (try (->> ["git" "diff" "HEAD"]
            (apply (:shell transfer-cmd))
            (:out)
            (spit local-patch-file-name))
       (if (u/exists? local-patch-file-name)
         {:step :create-patch :status :success}
         {:step :create-patch :status :failed :error "could not create local patch file"})
       (catch Exception e
         {:step :create-patch :status :failed :error (.getMessage e)})))

(defn upload-patch! [transfer-cmd {repo :repo path :remote-path} session]
  (let [_ ((:scp transfer-cmd) session local-patch-file-name (remote-patch-file-path path repo))
        result-remove ((:shell transfer-cmd) "rm" "-f" local-patch-file-name)]
    (if (zero? (:exit result-remove))
      {:step :upload-patch :status :success}
      {:step :upload-patch :status :failed :error (:err result-remove)})))

(defn apply-patch! [transfer-cmd {repo :repo path :remote-path} session]
  (let [cmd (str "cd " path repo ";"
                 "git reset --hard;"
                 (str "git apply --whitespace=warn " remote-patch-file-name ";")
                 "rm -f " remote-patch-file-name ";")
        result-apply-patch ((:ssh transfer-cmd) session {:cmd cmd :agent-forwarding true})]
    (if (zero? (:exit result-apply-patch))
      {:step :apply-patch :status :success}
      {:step :apply-patch :status :failed :error (:err result-apply-patch)})))

;;; TRANSFER LOGIC
(def WAIT-TIME 500)
(def TRANSFER-STEPS [create-patch! upload-patch! apply-patch!])
(def TRANSER-CMD {:shell        sh/sh
                  :scp          ssh/scp-to
                  :ssh          ssh/ssh
                  :forward-port (fn [session port func]
                                  (ssh/with-local-port-forward [session port port] (func)))
                  :log          m/info})

(defn transfer-per-ssh [parameters session transfer-cmd run-steps]
  (let [failed-steps (->> run-steps
                          (map #(% transfer-cmd parameters session))
                          (filter #(= :failed (:status %)))
                          (reduce str))]
    (if (empty? failed-steps)
      {:status :success :msg "Change has been transferred successfully"}
      {:status :failed :msg (str "Transfer per SSH failed in: " failed-steps)})))

;;; MAIN

(defn create-notify-command [notify-cmd msg]
  (let [cmd (if (string? notify-cmd) [notify-cmd] notify-cmd)]
    (concat cmd [(str/replace msg #"\*" "")])))

(defn endless-loop []
  (loop []
    (Thread/sleep WAIT-TIME)
    (recur)))

(defn valid-port? [port]
  (and (not= :empty port)
       (not= :invalid port)
       (> port 1023)))

(defn has-port? [parameters]
  (contains? parameters :forwarding-port))

(defn run-cmd-remotely [ssh
                        log
                        {run-command :command repo :repo path :remote-path}
                        session]
  (let [output (->> {:cmd              (str "cd " path repo ";" run-command)
                     :out              :stream
                     :pty              true
                     :agent-forwarding true}
                    (ssh session)
                    (:out-stream))]
    (with-open [rdr (io/reader output)]
      (doseq [line (line-seq rdr)] (log line)))))

(defn notify [shell log notify-cmd msg]
  (let [should-notify? (not (empty? notify-cmd))]
    (when should-notify?
      (try
        (apply shell (create-notify-command notify-cmd msg))
        (catch Exception e (m/info "* Could not notify: " (.getMessage e))))))
  (log "*" msg))

(defn run-command-and-forward-port [session
                                    {port :forwarding-port cmd :command :as parameters}
                                    {ssh :ssh port-forward :forward-port log :log}]
  (let [has-cmd? (not (empty? cmd))]
    (cond
      (and (has-port? parameters) has-cmd?)
      (port-forward session port (partial run-cmd-remotely ssh log parameters session))

      (and (not (has-port? parameters)) has-cmd?)
      (run-cmd-remotely ssh log parameters session)

      (has-port? parameters)
      (port-forward session port endless-loop)

      :else (endless-loop))))

(defn notify-log [console parameters {sh :shell log :log}]
  (async/go-loop [{msg :msg} (async/<! console)]
    (notify sh log (:notify-command parameters) msg)
    (recur (async/<! console))))

(defn log-to [console msg]
  (async/go (async/>! console msg)))

(defn sync-code-change
  ([console session dirs parameters transfer-cmd transfer-steps]
   (sync-code-change console session dirs parameters (apply dir/scan (t/tracker) dirs) transfer-cmd transfer-steps))
  ([console session dirs parameters old-tracker transfer-cmd transfer-steps]
   (let [new-tracker (apply dir/scan old-tracker dirs)]
     (if (not= new-tracker old-tracker)
       (->> transfer-steps
            (transfer-per-ssh parameters session transfer-cmd)
            (log-to console))
       (Thread/sleep WAIT-TIME))
     (recur console session dirs parameters new-tracker transfer-cmd transfer-steps))))

(defn start-remote-routine [session asset-paths parameters transfer-cmd transfer-steps]
  (let [console (async/chan)]
    (future (sync-code-change console session asset-paths parameters transfer-cmd transfer-steps))
    (notify-log console parameters transfer-cmd)
    (run-command-and-forward-port session parameters transfer-cmd)))

(defn session-option [parameters with-system-agent?]
  (let [default-option {:username                 (:user parameters)
                        :strict-host-key-checking :no}]
    (if with-system-agent?
      default-option
      (merge default-option {:password (get-in parameters [:auth :password])}))))

(defn create-session [parameters]
  (m/info "\n* Starting with the parameters:" (assoc-in parameters [:auth :password] "***"))
  (let [with-system-agent? (get-in parameters [:auth :with-system-agent])
        agent (ssh/ssh-agent {:use-system-ssh-agent with-system-agent?})
        session-params (session-option parameters with-system-agent?)
        session (ssh/session agent (:host parameters) session-params)]
    (m/info "* Starting session with the parameters:"
            (-> session-params
                (assoc :password "***")
                (assoc :use-system-ssh-agent with-system-agent?)) "\n")
    (ssh/connect session)
    session))

(defn normalize-remote-path [path]
  (let [lower-case-path (str/lower-case path)]
    (if (.endsWith lower-case-path "/")
      lower-case-path
      (str lower-case-path "/"))))

(defn should-use-system-agent? [with-system-agent]
  (or (= "y" with-system-agent)
      (true? with-system-agent)))

(defn ask-for-auth [project]
  (let [with-system-agent (or (get-in project [:remote-test :with-system-agent])
                              (u/ask-clear-text
                               "* ==> Do you want to use ssh system agent (y/n):"
                               identity
                               u/yes-or-no))]
    (if (should-use-system-agent? with-system-agent)
      {:with-system-agent true}
      {:with-system-agent false
       :password          (u/ask-for-password
                           "* ==> Please enter your ssh password:"
                           #(not (empty? %)))})))

(defn ask-for-parameters [project]
  (let [project-name (:name project)
        user (or (get-in project [:remote-test :user])
                 (u/ask-clear-text
                  "* ==> Please enter your ssh user:"
                  identity
                  #(not (empty? %))))

        host (or (get-in project [:remote-test :host])
                 (u/ask-clear-text
                  "* ==> Please enter your remote host:"
                  identity
                  #(not (empty? %))))

        auth (ask-for-auth project)

        path (or (get-in project [:remote-test :remote-path])
                 (u/ask-clear-text
                  "* ==> Please enter parent folder path of repository on remote machine:"
                  identity
                  #(not (empty? %))))

        command (or (get-in project [:remote-test :command])
                    (u/ask-clear-text
                     "* ==> Which command do you want to run on the repository of remote machine (optional):"))

        forwarding-port (or (get-in project [:remote-test :forwarding-port])
                            (u/ask-clear-text
                             "* ==> Enter a port > 1023 if you need a port to be forwarded (optional):"
                             u/parse-port
                             #(or (= :empty %) (valid-port? %))))

        forwarding-parameter (if (= :empty forwarding-port) {} {:forwarding-port forwarding-port})

        required-parameters {:repo        project-name
                             :user        user
                             :auth        auth
                             :host        host
                             :command     command
                             :remote-path (normalize-remote-path path)}

        notify-parameter {:notify-command (get-in project [:remote-test :notify-command])}]

    (assert (not (empty? project-name)) project-name)
    (assert (not (empty? user)) user)
    (assert (not (empty? host)) host)
    (assert (not (empty? path)) path)
    (assert (not (empty? auth)) auth)

    (merge required-parameters forwarding-parameter notify-parameter)))

(defn find-asset-paths [project]
  (->> (concat (:source-paths project ["src"])
               (:resource-paths project ["resources"])
               (:test-paths project ["test"]))
       (vec)))

(defn remote-test-refresh [project & _]
  (m/info "* Remote-Test-Refresh version:" (u/artifact-version))
  (try
    (let [parameters (ask-for-parameters project)
          session (create-session parameters)]
      (ssh/with-connection
        session
        (start-remote-routine session (find-asset-paths project) parameters TRANSER-CMD TRANSFER-STEPS)))
    (catch Exception e (m/info "* [error] " (.getMessage e) (when verbose e)))))