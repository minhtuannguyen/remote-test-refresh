(ns leiningen.remote.transfer
  (:require [leiningen.remote.utils :as u]))

(def local-patch-file-name "test-refresh-local.patch")
(def remote-patch-file-name "test-refresh.remote.patch")

(defn create-remote-patch-file-path [remote-path repo]
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

(defn upload-patch! [{scp-to :scp sh :shell} {repo :repo path :remote-path} session]
  (let [_ (scp-to session local-patch-file-name (create-remote-patch-file-path path repo))
        result-remove (sh "rm" "-f" local-patch-file-name)]
    (if (zero? (:exit result-remove))
      {:step :upload-patch :status :success}
      {:step :upload-patch :status :failed :error (:err result-remove)})))

(defn apply-patch! [{ssh :ssh} {repo :repo path :remote-path} session]
  (let [cmd (str "cd " path repo ";"
                 "git reset --hard;"
                 (str "git apply --whitespace=warn " remote-patch-file-name ";")
                 "rm -f " remote-patch-file-name ";")
        result-apply-patch (ssh session {:cmd cmd :agent-forwarding true})]
    (if (zero? (:exit result-apply-patch))
      {:step :apply-patch :status :success}
      {:step :apply-patch :status :failed :error (:err result-apply-patch)})))

(defn run-per-ssh [parameters session transfer-cmd run-steps]
  (let [failed-steps (->> run-steps
                          (map #(% transfer-cmd parameters session))
                          (filter #(= :failed (:status %)))
                          (reduce str))]
    (if (empty? failed-steps)
      {:status :success :msg "Change has been transferred successfully"}
      {:status :failed :msg (str "Transfer per SSH failed in: " failed-steps)})))