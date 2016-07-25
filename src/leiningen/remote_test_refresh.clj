(ns leiningen.remote-test-refresh
  (:require [clojure.tools.namespace.dir :as dir]
            [clojure.tools.namespace.track :as t]
            [clojure.string :as str]
            [leiningen.remote.transfer :as transfer]
            [leiningen.remote.parameter :as p]
            [leiningen.core.main :as m]
            [leiningen.remote.utils :as u]
            [clj-ssh.ssh :as ssh]
            [clojure.core.async :as async]
            [clojure.java.shell :as sh]
            [clojure.java.io :as io]))

(def verbose false)
(def WAIT-TIME 500)
(def TRANSFER-STEPS [transfer/create-patch! transfer/upload-patch! transfer/apply-patch!])
(def TRANSER-CMD {:shell        sh/sh
                  :scp          ssh/scp-to
                  :ssh          ssh/ssh
                  :log          m/info
                  :loop         u/endless-loop
                  :forward-port (fn [session port fun]
                                  (ssh/with-local-port-forward [session port port] (fun)))})

(defn create-notify-command [notify-cmd msg]
  (let [cmd (if (string? notify-cmd) [notify-cmd] notify-cmd)]
    (concat cmd [(str/replace msg #"\*" "")])))

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

(defn has-port? [parameters]
  (contains? parameters :forwarding-port))

(defn run-command-and-forward-port [session
                                    {port :forwarding-port cmd :command :as parameters}
                                    {ssh :ssh port-forward :forward-port log :log wait-loop :loop}]
  (let [has-cmd? (not (empty? cmd))]
    (cond
      (and (has-port? parameters) has-cmd?)
      (port-forward session port (partial run-cmd-remotely ssh log parameters session))

      (and (not (has-port? parameters)) has-cmd?)
      (run-cmd-remotely ssh log parameters session)

      (has-port? parameters)
      (port-forward session port wait-loop)

      :else (wait-loop))))

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
            (transfer/run-per-ssh parameters session transfer-cmd)
            (log-to console))
       (Thread/sleep WAIT-TIME))
     (recur console session dirs parameters new-tracker transfer-cmd transfer-steps))))

(defn start-remote-routine [session asset-paths parameters transfer-cmd transfer-steps]
  (let [console (async/chan)]
    (future (sync-code-change console session asset-paths parameters transfer-cmd transfer-steps))
    (notify-log console parameters transfer-cmd)
    (run-command-and-forward-port session parameters transfer-cmd)))

(defn create-session [parameters]
  (m/info "\n* Starting with the parameters:" (assoc-in parameters [:auth :password] "***"))
  (let [with-system-agent? (get-in parameters [:auth :with-system-agent])
        agent (ssh/ssh-agent {:use-system-ssh-agent with-system-agent?})
        session-params (p/session-option parameters with-system-agent?)
        session (ssh/session agent (:host parameters) session-params)]
    (m/info "* Starting session with the parameters:"
            (-> session-params
                (assoc :password "***")
                (assoc :use-system-ssh-agent with-system-agent?)) "\n")
    (ssh/connect session)
    session))

(defn remote-test-refresh [project & _]
  (m/info "* Remote-Test-Refresh version:" (u/artifact-version))
  (try
    (let [parameters (p/ask-for-parameters project)
          session (create-session parameters)]
      (ssh/with-connection
        session
        (start-remote-routine session (p/asset-paths project) parameters TRANSER-CMD TRANSFER-STEPS)))
    (catch Exception e (m/info "* [error] " (.getMessage e) (when verbose e)))))