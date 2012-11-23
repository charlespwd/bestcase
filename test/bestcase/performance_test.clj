(ns bestcase.performance-test
  (:use [bestcase.core]
        [bestcase.for-testing]
        [bestcase.store.redis]
        [bestcase.store.memory]
        [midje.sweet]
        [clojure.test])
  (:require [taoensso.carmine :as car]
            [clojure.math.numeric-tower :as cmath]))

(declare all-performance-tests)

(deftest all-tests
  (println "==========================================================")

  ;; DISABLE ALL REDIS TESTS FOR TRAVIS-CS
  ;; (println "Performance Test With Redis Store")
  ;; (println "---------------------------------")
  ;; (all-performance-tests (create-redis-store (car/make-conn-pool)
  ;;                                            (car/make-conn-spec)))
  ;;
  ;; (println)
  ;; (println)

  (println "Performance Test With In-Memory Store")
  (println "-------------------------------------")
  (all-performance-tests (create-memory-store))

  (println "=========================================================="))

(defn all-performance-tests
  [store]

  ;; set store
  (fact (set-config! {:store store}) =>
        (contains {:store store
                   :test-creation-lock #(not (nil? %))}))
  
  (println)
  (println "100000x picks between 4 evenly-weighed choices:")
  (time
   (do
     (let [test1 (keyword (str (rand)))]
       (doall (repeatedly
               100000
               #(with-identity (str (rand))
                  (alt test1 
                       :a "a"
                       :b "b"
                       :c "c"
                       :d "d"))))
       (let [r (results test1 :a)
             a (some #(if (= :a (:alternative-name %)) %) (:alternatives r))
             b (some #(if (= :b (:alternative-name %)) %) (:alternatives r))
             c (some #(if (= :c (:alternative-name %)) %) (:alternatives r))
             d (some #(if (= :d (:alternative-name %)) %) (:alternatives r))]
         ;; Make sure we are within 2% of the expected distribution.
         ;; Obviously, this can fail on occasion.
         (fact (< (cmath/abs (- (:count a)
                                (* 0.25 100000))) 2000) => truthy)
         (fact (< (cmath/abs (- (:count b)
                                (* 0.25 100000))) 2000) => truthy)
         (fact (< (cmath/abs (- (:count c)
                                (* 0.25 100000))) 2000) => truthy)
         (fact (< (cmath/abs (- (:count d)
                                (* 0.25 100000))) 2000) => truthy)
         (end test1 :a)))))

  (println)
  (println "100000x picks between 4 choices weighed [10/20/30/40]:")
  (time
   (do
     (let [test1 (keyword (str (rand)))]
       (doall (repeatedly
               100000
               #(with-identity (str (rand))
                  (alt test1
                       [:a 10] "a"
                       [:b 20] "b"
                       [:c 30] "c"
                       [:d 40] "d"))))
       (let [r (results test1 :a)
             a (some #(if (= :a (:alternative-name %)) %) (:alternatives r))
             b (some #(if (= :b (:alternative-name %)) %) (:alternatives r))
             c (some #(if (= :c (:alternative-name %)) %) (:alternatives r))
             d (some #(if (= :d (:alternative-name %)) %) (:alternatives r))]
         ;; Make sure we are within 2% of the expected distribution.
         ;; Obviously, this can fail on occasion.
         (fact (< (cmath/abs (- (:count a)
                                (* 0.10 100000))) 2000) => truthy)
         (fact (< (cmath/abs (- (:count b)
                                (* 0.20 100000))) 2000) => truthy)
         (fact (< (cmath/abs (- (:count c)
                                (* 0.30 100000))) 2000) => truthy)
         (fact (< (cmath/abs (- (:count d)
                                (* 0.40 100000))) 2000) => truthy)
         (end test1 :a))))))