(ns bestcase.store.redis-test
  (:use [midje.sweet]
        [clojure.test]
        [bestcase.store.store-test-util]
        [bestcase.store.redis])
  (:require [taoensso.carmine :as car]))

(def redis-server-conn {:pool nil :spec nil}) ;; use defaults

(deftest all-tests
  (let [store (create-redis-store redis-server-conn)]
    (all-store-tests store)))

