(ns leiningen.remote-test-refresh
  (:require [clojure.tools.namespace.dir :as dir]
            [clojure.tools.namespace.track :as t]
            [clojure.string :as str]
            [leiningen.core.main :as m]
            [leiningen.remote.utils.utils :as u]
            [clj-ssh.ssh :as ssh]
            [clojure.java.shell :as sh]))

;;; Transfer steps

(def local-patch-file-name "test-refresh-local.patch")
(def remote-patch-file-name "test-refresh.remote.patch")

(defn remote-patch-file-path [remote-path repo]
  (str remote-path repo "/" remote-patch-file-name))

(defn create-patch! [_ _]
  (try (->> ["git" "diff" "HEAD"]
            (apply sh/sh)
            (:out)
            (spit local-patch-file-name))
       {:step :create-patch :status :success}
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

(defn transfer-per-ssh [run-steps parameters session]
  (let [failed-steps (->> run-steps
                          (map #(% parameters session))
                          (filter #(= :failed (:status %)))
                          (reduce str))]
    (if (empty? failed-steps)
      {:status :success :msg "* Change has been transfered successfully to your remote repo"}
      {:status :failed :msg (str "* Transfer per SSH failed in: " failed-steps)})))

(defn normalize-remote-path [path]
  (let [lower-case-path (str/lower-case path)]
    (if (.endsWith lower-case-path "/")
      lower-case-path
      (str lower-case-path "/"))))

(defn ssh-parameters [project]
  (let [project-name (:name project)
        user (get-in project [:remote-test :user])
        password (get-in project [:remote-test :password])
        host (get-in project [:remote-test :host])
        path (get-in project [:remote-test :remote-path])]
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
  ([session dirs parameters]
   (sync-code-change session dirs parameters (apply dir/scan (t/tracker) dirs)))
  ([session dirs parameters old-tracker]
   (let [new-tracker (apply dir/scan old-tracker dirs)]
     (if (not= new-tracker old-tracker)
       (-> TRANSFER-STEPS
           (transfer-per-ssh parameters session)
           (:msg)
           (m/info))
       (Thread/sleep WAIT-TIME))
     (recur session dirs parameters new-tracker))))

(defn remote-test-refresh [project & _]
  (m/info "* Remote-Test-Refresh version:" (u/artifact-version))
  (let [asset-paths (find-asset-paths project)
        parameters (ssh-parameters project)
        agent (ssh/ssh-agent {:use-system-ssh-agent false})
        session (ssh/session agent
                             (:host parameters)
                             {:user                     (:user parameters)
                              :password                 (:password parameters)
                              :strict-host-key-checking :no})]
    (m/info "* Starting with the parameters:" (assoc parameters :password "**"))
    (ssh/with-connection session (sync-code-change session asset-paths parameters))))