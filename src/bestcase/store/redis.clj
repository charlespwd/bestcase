(ns bestcase.store.redis
  (:use [bestcase.core])
  (:require [taoensso.carmine :as car]))

;; redis implementation of the Store protocol in bestcase.core
;; -----------------------------------------------------------

(defonce key-root "bestcase|")
(defonce key-active-test-descriptor-tail "active-test-descriptor|")
(defonce key-inactive-test-tail "inactive-test|")
(defonce key-alternative-tail "|alternative|")
(defonce key-count-tail "|count|")
(defonce key-score-tail "|score|")
(defonce key-user-scored-tail "|user-scored|")

(def active-test-descriptor-key (str key-root key-active-test-descriptor-tail))

(def inactive-test-descriptor-key (str key-root key-inactive-test-tail))

(defmacro test-identity-alternative-key
  [test-name i]
  `(str key-root ~test-name key-alternative-tail ~i))

(defmacro test-count-key
  [test-name]
  `(str key-root ~test-name key-count-tail))

(defmacro test-score-key
  [test-name]
  `(str key-root ~test-name key-score-tail))

(defmacro test-user-scored-key
  [test-name goal-name]
  `(str key-root ~test-name key-user-scored-tail ~goal-name))

(deftype RedisStore [pool spec-server]
  Store
  (get-test [_ test-name]
    (if-let [t (car/with-conn pool spec-server
                 (car/hget active-test-descriptor-key test-name))]
      t (car/with-conn pool spec-server
          (car/hget inactive-test-descriptor-key test-name))))
  (set-test! [_ test-name descriptor]
    (let [t (if-let [t (car/with-conn pool spec-server
                         (car/hget active-test-descriptor-key test-name))]
              t {})]
      (car/with-conn pool spec-server
        (car/hset active-test-descriptor-key test-name (merge t descriptor)))))
  (get-all-active-tests [_]
    (car/with-conn pool spec-server
      (car/hgetall* active-test-descriptor-key)))
  (get-all-inactive-tests [_]
    (car/with-conn pool spec-server
      (car/hgetall* inactive-test-descriptor-key)))
  (end-test! [_ test-name]
    (let [t (car/with-conn pool spec-server
              (car/hget active-test-descriptor-key test-name))]
      (car/with-conn pool spec-server
        (car/hdel active-test-descriptor-key test-name)
        (car/hset inactive-test-descriptor-key test-name t))))
  (get-alternative-name [_ test-name i]
    (keyword (car/with-conn pool spec-server
      (car/get (test-identity-alternative-key test-name i) ))))
  (set-alternative-name! [_ test-name i alternative-name]
    (car/with-conn pool spec-server
      (car/set (test-identity-alternative-key test-name i) alternative-name)))
  (get-count [_ test-name alternative-name]
    (if-let [c
             (car/as-long
              (car/with-conn pool spec-server
                (car/hget (test-count-key test-name) alternative-name)))] c 0))
  (get-all-counts [_ test-name]
    (into {} (for [[k v] (car/with-conn pool spec-server
                           (car/hgetall* (test-count-key test-name)))]
               [(keyword k) (car/as-long v)])))
  (get-all-scores [_ test-name]
    (into {} (for [[k v] (car/with-conn pool spec-server
                           (car/hgetall* (test-score-key test-name)))]
               [(keyword k) (car/as-long v)])))
  (increment-count! [_ test-name alternative-name]
    (car/with-conn pool spec-server
      (car/hincrby (test-count-key test-name) alternative-name 1)))
  (increment-score! [_ test-name alternative-name-and-goal-name]
    (car/with-conn pool spec-server
      (car/hincrby (test-score-key test-name)
                   alternative-name-and-goal-name 1)))
  (get-user-scored-count [_ test-name goal-name i]
    (car/with-conn pool spec-server
      (if-let [c (car/hget (test-user-scored-key test-name goal-name) i)]
        c 0)))
  (increment-user-scored! [_ test-name goal-name i]
    (car/with-conn pool spec-server
      (car/hincrby (test-user-scored-key test-name goal-name) i 1))))

(defn create-redis-store
  "Takes a Carmine pool and server spec and returns a redis-backed Store."
  [pool spec-server]
  (RedisStore. pool spec-server))
