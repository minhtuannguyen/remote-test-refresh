(ns leiningen.remote-test-refresh
  (:require [clojure.tools.namespace.dir :as dir]
            [clojure.tools.namespace.track :as t]
            [clojure.string :as str]
            [leiningen.core.main :as m]
            [clojure.java.shell :as sh]))

;;; Transfer steps

(defn create-patch! [_]
  (try (->> ["git" "diff" "HEAD"]
            (apply sh/sh)
            (:out)
            (spit "test-refresh.patch"))
       {:step :upload-patch :status :success}
       (catch Exception e
         {:step :upload-patch :status :failed :error (.getMessage e)})))

(defn upload-patch! [{repo        :repo
                      user        :user
                      host        :host
                      remote-path :remote-path}]
  (let [result-copy (sh/sh "scp" "test-refresh.patch"
                           (str user "@" host ":" remote-path repo "/" "test-refresh.patch"))
        result-remove (sh/sh "rm" "-f" "test-refresh.patch")]
    (if (and (= 0 (:exit result-copy)) (= 0 (:exit result-remove)))
      {:step :upload-patch :status :success}
      {:step :upload-patch :status :failed :error-1 (str (:err result-copy) (:err result-remove))})))

(defn apply-patch! [{repo        :repo
                     user        :user
                     host        :host
                     remote-path :remote-path}]
  (let [result (sh/sh "ssh" (str user "@" host)
                      (str "cd " remote-path repo ";")
                      "git reset --hard;"
                      "git apply --whitespace=warn test-refresh.patch;"
                      (str "rm -f " remote-path repo "/" "test-refresh.patch;"))]
    (if (= 0 (:exit result))
      {:step :apply-patch :status :success}
      {:step :apply-patch :status :failed :error (:err result)})))

;;; Transfer logic

(defn determin-asset-paths [project]
  (->> (concat (:source-paths project ["src"])
               (:resource-paths project ["resources"])
               (:test-paths project ["test"]))
       (vec)))

(defn normalize-remote-path [path]
  (let [lower-case-path (str/lower-case path)]
    (if (.endsWith lower-case-path "/")
      lower-case-path
      (str lower-case-path "/"))))

(defn determine-connection-parameter [project]
  (let [project-name (:name project)
        user (get-in project [:remote-test :user])
        host (get-in project [:remote-test :host])
        path (get-in project [:remote-test :remote-path])]
    (assert (not (empty? project-name)) project-name)
    (assert (not (empty? user)) user)
    (assert (not (empty? host)) host)
    (assert (not (empty? path)) path)
    {:repo        project-name
     :user        user
     :host        host
     :remote-path (normalize-remote-path path)}))

(defn transfer-per-ssh [run-steps option]
  (let [failed-steps (->> run-steps
                          (map #(% option))
                          (filter #(= :failed (:status %))))]
    (if (empty? failed-steps)
      {:status :success :msg "* Change has been transfered successfully"}
      {:status :failed :msg (str "* Transfer per SSH failed in: " failed-steps)})))

;;;; Main

(def TRANSFER-STEPS [create-patch! upload-patch! apply-patch!])
(def WAIT-TIME 500)

(defn sync-code-change
  ([dirs parameters]
   (m/info "Remote test runs with parameters:" parameters)
   (sync-code-change dirs parameters (apply dir/scan (t/tracker) dirs)))
  ([dirs option old-tracker]
   (let [new-tracker (apply dir/scan old-tracker dirs)]
     (if (not= new-tracker old-tracker)
       (-> TRANSFER-STEPS
           (transfer-per-ssh option)
           (:msg)
           (m/info))
       (Thread/sleep WAIT-TIME))
     (recur dirs option new-tracker))))

(defn remote-test-refresh [project & _]
  (sync-code-change
   (determin-asset-paths project)
   (determine-connection-parameter project)))
