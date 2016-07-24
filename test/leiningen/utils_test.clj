(ns leiningen.utils-test
  (:require [clojure.test :refer :all]
            [leiningen.remote.utils.utils :as u]))

(deftest ^:unit test-parse-init
  (is (= 20 (u/parse-port "20")))
  (is (= 0 (u/parse-port "0")))
  (is (= :invalid (u/parse-port "sds")))
  (is (= :empty (u/parse-port ""))))

(deftest ^:unit test-exists?
  (is (true? (u/exists? "test-resources/test.patch")))
  (is (false? (u/exists? "test-resources/unknow n.patch"))))

(deftest ^:unit test-yes-or-no
  (is (true? (u/yes-or-no "y")))
  (is (true? (u/yes-or-no "n")))
  (is (false? (u/yes-or-no "")))
  (is (false? (u/yes-or-no nil)))
  (is (false? (u/yes-or-no "ja"))))

(deftest ^:unit test-read-artifact-version-from
  (is (= "unknown" (u/read-artifact-version-from "test-resources/unknown.properties"))))

(deftest ^:unit test-ask-user
  (let [capture-fn (fn [x] "answer")
        question ""
        validate-fn (fn [x] true)]
    (is (= "answer" (u/ask-user capture-fn question validate-fn))))

  (let [answer-seq (atom ["wrong answer" "right answer"])
        capture-fn (fn [_] (let [[f & r] @answer-seq] (reset! answer-seq r) f))
        question ""
        validate-fn (fn [x] (= "right answer" x))]
    (is (= "right answer" (u/ask-user capture-fn question validate-fn)))))
