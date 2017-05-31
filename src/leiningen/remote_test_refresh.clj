(ns leiningen.remote-test-refresh
  (:require [clojure.tools.namespace.dir :as dir]
            [clojure.tools.namespace.track :as t]
            [clojure.core.async :as async]
            [clojure.java.shell :as sh]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [leiningen.remote.transfer :as transfer]
            [leiningen.remote.parameter :as p]
            [leiningen.core.main :as m]
            [leiningen.remote.utils :as u]
            [clj-ssh.ssh :as ssh]))

(def VERBOSE false)
(def WAIT-TIME 500)
(def TRANSFER-STEPS [transfer/create-patch! transfer/upload-patch! transfer/apply-patch!])
(def TRANSER-CMD {:shell        sh/sh
                  :scp          ssh/scp-to
                  :ssh          ssh/ssh
                  :log          m/info
                  :loop         u/endless-loop
                  :forward-port (fn [session port callback]
                                  (ssh/with-local-port-forward [session port port] (callback)))})

(defn make-notify-command [notify-cmd msg]
  (concat
   (if (string? notify-cmd)
     [notify-cmd]
     notify-cmd)
   [(str/replace msg #"\*" "")]))

(defn make-console-reader [ssh session {:keys [command remote-path repo]}]
  (->> {:cmd              (str "cd " remote-path repo ";" command)
        :out              :stream
        :pty              true
        :agent-forwarding true}
       (ssh session)
       (:out-stream)
       (io/reader)))

(defn run-cmd-remotely [ssh log parameters session]
  (with-open [rdr (make-console-reader ssh session parameters)]
    (doseq [line (line-seq rdr)]
      (log line))))

(defn notify [shell log notify-cmd msg]
  (when (not (empty? notify-cmd))
    (try
      (apply shell (make-notify-command notify-cmd msg))
      (catch Exception e (m/info "* Could not notify: " (.getMessage e)))))
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

(defn pprint-parameter
  ([m] (pprint-parameter m (fn [& args] (apply m/info args))))
  ([m log]
   (log "\n* Starting session with the parameters:")
   (doall (map (fn [[k v]] (log "   " k ":" v)) (seq m)))
   (log "\n")))

(defn create-session [parameters]
  (let [with-system-agent? (get-in parameters [:auth :with-system-agent])
        agent (ssh/ssh-agent {:use-system-ssh-agent with-system-agent?})
        session-params (p/session-option parameters with-system-agent?)
        session (ssh/session agent (:host parameters) session-params)]
    (-> parameters
        (merge session-params)
        (assoc-in [:auth :password] "***")
        (assoc :password "***")
        (assoc :use-system-ssh-agent with-system-agent?)
        (pprint-parameter))
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
    (catch Exception e (m/info "* [error] " (if VERBOSE e (.getMessage e))))))