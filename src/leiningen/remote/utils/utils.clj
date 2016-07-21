(ns leiningen.remote.utils.utils
  (:require [clojure.java.io :as io])
  (:import (java.util Properties)))

(defn artifact-version []
  (let [path (str "META-INF/maven/minhtuannguyen/remote-test-refresh/pom.properties")
        props (io/resource path)]
    (if props
      (with-open [stream (io/input-stream props)]
        (let [props (doto (Properties.) (.load stream))]
          (.getProperty props "version"))))))
