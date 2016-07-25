(ns leiningen.remote.parameter
  (:require [clojure.string :as str]
            [leiningen.remote.utils :as u]))

(defn normalize-remote-path [path]
  (let [lower-case-path (str/lower-case path)]
    (if (.endsWith lower-case-path "/")
      lower-case-path
      (str lower-case-path "/"))))

(defn valid-port? [port]
  (and (not= :empty port)
       (not= :invalid port)
       (> port 1023)))

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

(defn asset-paths [project]
  (vec
   (concat (:source-paths project ["src"])
           (:resource-paths project ["resources"])
           (:test-paths project ["test"]))))

(defn session-option [parameters with-system-agent?]
  (let [default-option {:username                 (:user parameters)
                        :strict-host-key-checking :no}]
    (if with-system-agent?
      default-option
      (merge default-option {:password (get-in parameters [:auth :password])}))))