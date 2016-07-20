(ns leiningen.remote-test-refresh
  (:require [clojure.tools.namespace.dir :as dir]
            [clojure.tools.namespace.track :as t]
            [leiningen.core.main :as m]
            [clojure.java.shell :as sh]))

(def WAIT-TIME 500)

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

(defn transfer-per-ssh [option]
  (let [failed-steps (->> [create-patch! upload-patch! apply-patch!]
                          (map #(% option))
                          (filter #(= :failed (:status %)))
                          (map :step))]
    (if (empty? failed-steps)
      (m/info "* Change has been transfered successfully")
      (m/info "* transfer-per-ssh failed in:" failed-steps))))

(defn sync-code-change
  ([dirs options]
   (sync-code-change dirs options (apply dir/scan (t/tracker) dirs)))
  ([dirs option old-tracker]
   (let [new-tracker (apply dir/scan old-tracker dirs)]
     (if (not= new-tracker old-tracker)
       (transfer-per-ssh option)
       (Thread/sleep WAIT-TIME))
     (recur dirs option new-tracker))))

(defn asset-paths [project]
  (->> (concat (:source-paths project ["src"])
               (:resource-paths project ["resources"])
               (:test-paths project ["test"]))
       (vec)))

(defn remote-test-refresh [project & _]
  (sync-code-change
   (asset-paths project)
   (merge {:repo (:name project)}
          (:remote-test project))))
