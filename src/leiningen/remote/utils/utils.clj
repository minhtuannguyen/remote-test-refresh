(ns leiningen.remote.utils.utils
  (:require [clojure.java.io :as io]
            [leiningen.core.main :as m])
  (:import (java.util Properties)))

(defn artifact-version []
  (let [path (str "META-INF/maven/minhtuannguyen/remote-test-refresh/pom.properties")
        props (io/resource path)]
    (if props
      (with-open [stream (io/input-stream props)]
        (let [props (doto (Properties.) (.load stream))]
          (.getProperty props "version"))))))

(defn- ask-user
  ([capture-fn question] (ask-user capture-fn question (fn [_] true)))
  ([capture-fn question validate-fn]
   (loop [input (capture-fn question)]
     (if (validate-fn input)
       input
       (do
         (m/info "The input was not correct")
         (recur (capture-fn question)))))))

(defn ask-clear-text
  ([question]
   (ask-clear-text question (fn [_] true)))
  ([question validate-fn]
   (ask-user
    (fn [prompt] (do (m/info prompt) (read-line)))
    question
    validate-fn)))

(defn ask-for-password
  ([question]
   (ask-for-password question (fn [_] true)))
  ([question validate-fn]
   (ask-user
    (fn [prompt]
      (String/valueOf (.readPassword (System/console) prompt nil)))
    question
    validate-fn)))

(defn yes-or-no [input]
  (or (= "y" input)
      (= "n" input)))

(defn exists? [path]
  (-> path
      (io/as-file)
      (.exists)))

(defn parse-int [s]
  (try
    (new Integer (re-find #"\d+" s))
    (catch Exception _ -1)))