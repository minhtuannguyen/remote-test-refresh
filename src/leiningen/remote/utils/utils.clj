(ns leiningen.remote.utils.utils
  (:require [clojure.java.io :as io]
            [leiningen.core.main :as m])
  (:import (java.util Properties)))

(defn read-artifact-version-from [path]
  (let [props (io/resource path)]
    (if props
      (with-open [stream (io/input-stream props)]
        (let [props (doto (Properties.) (.load stream))]
          (.getProperty props "version")))
      "unknown")))

(defn artifact-version []
  (read-artifact-version-from
   "META-INF/maven/minhtuannguyen/remote-test-refresh/pom.properties"))

(defn ask-user
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
   (ask-clear-text question identity (fn [_] true)))
  ([question parse-fn]
   (ask-clear-text question parse-fn (fn [_] true)))
  ([question parse-fn validate-fn]
   (ask-user
    (fn [prompt] (do (m/info prompt) (parse-fn (read-line))))
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

(defn parse-port [s]
  (try
    (if (empty? s)
      :empty
      (new Integer (re-find #"\d+" s)))
    (catch Exception _ :invalid)))