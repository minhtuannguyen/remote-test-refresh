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

;;; Transfer steps
(def verbose true)
(def local-patch-file-name "test-refresh-local.patch")
(def remote-patch-file-name "test-refresh.remote.patch")

(defn remote-patch-file-path [remote-path repo]
  (str remote-path repo "/" remote-patch-file-name))

(defn create-patch! [_ _]
  (try (->> ["git" "diff" "HEAD"]
            (apply sh/sh)
            (:out)
            (spit local-patch-file-name))
       (if (u/exists? local-patch-file-name)
         {:step :create-patch :status :success}
         {:step :create-patch :status :failed :error "could not create local patch file"})
       (catch Exception e
         {:step :create-patch :status :failed :error (.getMessage e)})))

(defn upload-patch! [{repo :repo path :remote-path} session]
  (let [_ (ssh/scp-to session local-patch-file-name (remote-patch-file-path path repo))
        result-remove (sh/sh "rm" "-f" local-patch-file-name)]
    (if (= 0 (:exit result-remove))
      {:step :upload-patch :status :success}
      {:step :upload-patch :status :failed :error (:err result-remove)})))

(defn apply-patch! [{repo :repo path :remote-path} session]
  (let [cmd (str "cd " path repo ";"
                 "git reset --hard;"
                 (str "git apply --whitespace=warn " remote-patch-file-name ";")
                 "rm -f " remote-patch-file-name ";")
        result-apply-patch (ssh/ssh session {:cmd cmd})]
    (if (= 0 (:exit result-apply-patch))
      {:step :apply-patch :status :success}
      {:step :apply-patch :status :failed :error (:err result-apply-patch)})))

;;; Transfer logic

(defn transfer-per-ssh [parameters session run-steps]
  (let [failed-steps (->> run-steps
                          (map #(% parameters session))
                          (filter #(= :failed (:status %)))
                          (reduce str))]
    (if (empty? failed-steps)
      {:status :success :msg "* Change has been transfered successfully to your remote repository"}
      {:status :failed :msg (str "* Transfer per SSH failed in: " failed-steps)})))

(defn normalize-remote-path [path]
  (let [lower-case-path (str/lower-case path)]
    (if (.endsWith lower-case-path "/")
      lower-case-path
      (str lower-case-path "/"))))

(defn ssh-parameters [project]
  (let [project-name (:name project)
        user (or (get-in project [:remote-test :user])
                 (u/ask-clear-text "* ==> SSH-User:"))
        password (or (get-in project [:remote-test :password])
                     (u/ask-for-password "* ==> SSH-Password:"))
        host (or (get-in project [:remote-test :host])
                 (u/ask-clear-text "* ==> SSH-Host:"))
        path (or (get-in project [:remote-test :remote-path])
                 (u/ask-clear-text "* ==> Path to parent folder of repository on remote machine:"))]
    (assert (not (empty? project-name)) project-name)
    (assert (not (empty? user)) user)
    (assert (not (empty? user)) password)
    (assert (not (empty? host)) host)
    (assert (not (empty? path)) path)
    {:repo        project-name
     :user        user
     :password    password
     :host        host
     :remote-path (normalize-remote-path path)}))

(defn find-asset-paths [project]
  (->> (concat (:source-paths project ["src"])
               (:resource-paths project ["resources"])
               (:test-paths project ["test"]))
       (vec)))

;;;; Main

(def TRANSFER-STEPS [create-patch! upload-patch! apply-patch!])
(def WAIT-TIME 500)

(defn sync-code-change
  ([console session dirs parameters]
   (sync-code-change console session dirs parameters (apply dir/scan (t/tracker) dirs)))
  ([console session dirs parameters old-tracker]
   (let [new-tracker (apply dir/scan old-tracker dirs)]
     (if (not= new-tracker old-tracker)
       (->> TRANSFER-STEPS
            (transfer-per-ssh parameters session)
            (async/>!! console))
       (Thread/sleep WAIT-TIME))
     (recur console session dirs parameters new-tracker))))

(defn run-test-refresh-remotely [{repo :repo path :remote-path} session]
  (let [output (->> {:cmd (str "cd " path repo ";" "./lein.sh test-refresh;")
                     :out :stream
                     :pty true}
                    (ssh/ssh session)
                    (:out-stream))]
    (with-open [rdr (io/reader output)]
      (doseq [line (line-seq rdr)] (m/info line)))))

(defn print-to-console [console]
  (while true
    (let [{msg :msg} (async/<!! console)]
      (m/info msg))))

(defn start-remote-routine [session asset-paths parameters]
  (let [console (async/chan)]
    (future (sync-code-change console session asset-paths parameters))
    (future (print-to-console console))
    (run-test-refresh-remotely parameters session)))

(defn session-option [parameters]
  {:user                     (:user parameters)
   :password                 (:password parameters)
   :strict-host-key-checking :no})

(defn remote-test-refresh [project & _]
  (m/info "* Remote-Test-Refresh version:" (u/artifact-version))
  (try
    (let [asset-paths (find-asset-paths project)
          parameters (ssh-parameters project)
          agent (ssh/ssh-agent {:use-system-ssh-agent false})
          session (ssh/session agent (:host parameters) (session-option parameters))]
      (m/info "* Starting with the parameters:" (assoc parameters :password "***") "\n")
      (ssh/connect session)
      (ssh/with-connection session (start-remote-routine session asset-paths parameters)))
    (catch Exception e (m/info "* [error] " (.getMessage e) (when verbose e)))))