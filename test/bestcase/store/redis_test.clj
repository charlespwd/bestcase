(ns bestcase.store.redis-test
  (:use [midje.sweet]
        [clojure.test]
        [bestcase.store.store-test-util]
        [bestcase.store.redis])
  (:require [taoensso.carmine :as car]))

(deftest all-tests
  (let [store (create-redis-store (car/make-conn-pool) (car/make-conn-spec))]
    (all-store-tests store)))

