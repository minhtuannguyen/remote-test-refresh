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

(defn ask-clear-text [question]
  (ask-user
   (fn [prompt] (do (m/info prompt) (read-line)))
   question))

(defn ask-for-password [question]
  (ask-user
   (fn [prompt]
     (String/valueOf (.readPassword (System/console) prompt nil)))
   question))