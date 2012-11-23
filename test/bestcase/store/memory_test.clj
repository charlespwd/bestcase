(ns bestcase.store.memory-test
  (:use [midje.sweet]
        [clojure.test]
        [bestcase.store.store-test-util]
        [bestcase.store.memory]))

(deftest all-tests
  (let [store (create-memory-store)]
    (all-store-tests store)))

