(ns bestcase.core-test
  (:use [bestcase.core]
        [bestcase.for-testing]
        [bestcase.store.redis]
        [bestcase.store.memory]
        [midje.sweet]
        [clojure.test])
  (:require [taoensso.carmine :as car]
            [clojure.math.numeric-tower :as cmath]))

(declare all-core-tests)

(deftest all-tests
  (all-core-tests (create-redis-store {:pool nil :spec nil})) ;; defaults
  (all-core-tests (create-memory-store)))

(defn all-core-tests
  [store]

  ;; one alternative
  (time
   (let [identity1 (keyword (str (rand)))
         identity2 (keyword (str (rand)))
         identity3 (keyword (str (rand)))
         identity4 (keyword (str (rand)))
         test1 (keyword (str (rand)))]
     (fact (set-config! {:store store}) =>
           (contains {:store store
                      :test-creation-lock #(not (nil? %))}))
     (with-identity identity1
       (fact (alt-fns test1 {:alternative1 (fn [] "alt1")}) => "alt1")
       (fact (results test1 :alternative1) =>
             {:test-name test1
              :test-type :ab-test
              :alternatives (list {:alternative-name :alternative1
                                   :count 1
                                   :control true
                                   :goal-results []})})
       (fact (score test1 :goal1) => 1)
       (fact (results test1 :alternative1) =>
             {:test-name test1
              :test-type :ab-test
              :alternatives
              (list {:alternative-name :alternative1
                     :count 1
                     :control true
                     :goal-results [{:goal-name :goal1
                                     :score 1
                                     :z-score 0.0}]})})

       ;; only count once even if you get the same test multiple times
       ;; and cannot score twice w/o multiple-participation
       (fact (alt-fns test1 {:alternative1 (fn [] "alt1")}) => "alt1")
       (fact (score test1 :goal1) => nil)
       (fact (results test1 :alternative1) =>
             {:test-name test1
              :test-type :ab-test
              :alternatives
              (list {:alternative-name :alternative1
                     :count 1
                     :control true
                     :goal-results [{:goal-name :goal1
                                     :score 1
                                     :z-score 0.0}]})})

       ;; can count twice with multiple-participation
       (fact (alt-fns test1 {:alternative1 (fn [] "alt1")}
                      {:multiple-participation true}) => "alt1")
       (fact (score test1 :goal1 {:multiple-participation true}) => 2)
       (fact (results test1 :alternative1) =>
             {:test-name test1
              :test-type :ab-test
              :alternatives
              (list {:alternative-name :alternative1
                     :count 2
                     :control true
                     :goal-results [{:goal-name :goal1
                                     :score 2
                                     :z-score 0.0}]})}))

     ;; multiple goals, multiple identities
     (with-identity identity2
       (fact (alt-fns test1 {:alternative1 (fn [] "alt1")}) => "alt1")
       (fact (score test1 :goal2) => 1))
     (with-identity identity3
       (fact (alt-fns test1 {:alternative1 (fn [] "alt1")}) => "alt1")
       (fact (score test1 :goal2) => 2))
     (fact (results test1 :alternative1) =>
           (just
            {:test-name test1
             :test-type :ab-test
             :alternatives
             (just [(just {:alternative-name :alternative1
                           :count 4
                           :control true
                           :goal-results (just
                                          [{:goal-name :goal1
                                            :score 2
                                            :z-score 0.0}
                                           {:goal-name :goal2
                                            :score 2
                                            :z-score 0.0}] :in-any-order)})])}))
     (end test1 :alternative1)))

  ;; ending tests & multiple stores
  (time
   (do
     (let [test1 (keyword (str (rand)))
           identity1 (keyword (str (rand)))
           identity2 (keyword (str (rand)))]
       (with-identity identity1
         (fact (alt-fns test1 {:alternative1 (fn [] "alt1")
                               :alternative2 (fn [] "alt2")})
               => #(not (nil? %1)))
         (let [a (alt-fns test1 {:alternative1 (fn [] "alt1")
                                 :alternative2 (fn [] "alt2")})]
           (end test1 :alternative2)

           ;; keep getting the same alternative after the tests is ended
           (fact (alt-fns test1 {:alternative1 (fn [] "alt1")
                                 :alternative2 (fn [] "alt2")}) => "alt2")

           ;; tests with different stores don't interefere
           (with-config {:store (create-memory-store)}
             (fact (alt-fns test1 {:alternative1 (fn [] "alt1")})
                   => "alt1"))
           (fact (results test1 (if (= a "alt1")
                                  :alternative1 :alternative2)) =>
                                  (just
                                   {:test-name test1
                                    :test-type :ab-test
                                    :alternatives
                                    (just (contains {:count 1}))}))))

       ;; same for a different identity
       (with-identity identity2
         (fact (alt-fns test1 {:alternative1 (fn [] "alt1")
                               :alternative2 (fn [] "alt2")}) => "alt2")
         (end test1 :alternative1))
     (let [test1 (keyword (str (rand)))
           identity1 (keyword (str (rand)))]
       (with-identity identity1
         (fact (alt test1
                    :alternative1 "alt1") => "alt1")
         (end test1 :alternative1)
         (fact (alt test1
                    :alternative1 "alt1") => "alt1")
         (score test1 :alternative1)
         (fact (results test1 :alternative1) =>
               (just {:test-name test1
                      :test-type :ab-test
                      :alternatives
                      (just {:alternative-name :alternative1
                             :control true
                             :count 1
                             :goal-results []})}))
         (end test1 :alternative1))))))

  ;; multiple alternative tests using helper functions in bestcase.for-testing
  ;; (data from http://20bits.com/article/statistical-analysis-and-ab-testing)
  (time
   (let [test1 (keyword (str (rand)))]
     (doall (repeatedly 182 #(force-alt test1 :control)))
     (doall (repeatedly 35 #(force-score test1 :registered :control)))
     (doall (repeatedly 180 #(force-alt test1 :a)))
     (doall (repeatedly 45 #(force-score test1 :registered :a)))
     (doall (repeatedly 189 #(force-alt test1 :b)))
     (doall (repeatedly 28 #(force-score test1 :registered :b)))
     (doall (repeatedly 188 #(force-alt test1 :c)))
     (doall (repeatedly 61 #(force-score test1 :registered :c)))
     (fact (results test1 :control) =>
           (just
            {:test-name test1
             :test-type :ab-test
             :alternatives
             (just [{:alternative-name :control
                     :count 182
                     :control true
                     :goal-results [{:goal-name :registered
                                     :score 35
                                     :z-score 0.0}]}
                    {:alternative-name :a
                     :count 180
                     :goal-results [{:goal-name :registered
                                     :score 45
                                     :z-score 1.3252611961151077}]}
                    {:alternative-name :b
                     :count 189
                     :goal-results [{:goal-name :registered
                                     :score 28
                                     :z-score -1.1322354025976662}]}
                    {:alternative-name :c
                     :count 188
                     :goal-results [{:goal-name :registered
                                     :score 61
                                     :z-score 2.941015722492861}]}]
                   :in-any-order)}))
     (end test1 :a)))

  ;; multiple alternatives, multiple goals using helper functions in
  ;; bestcase.for-testing
  (time
   (let [test1 (keyword (str (rand)))
         test2 (keyword (str (rand)))]
     (doall (repeatedly 182 #(force-alt test1 :control)))
     (doall (repeatedly 35 #(force-score test1 :registered :control)))
     (doall (repeatedly 180 #(force-alt test1 :a)))
     (doall (repeatedly 45 #(force-score test1 :registered :a)))
     (doall (repeatedly 189 #(force-alt test1 :b)))
     (doall (repeatedly 28 #(force-score test1 :registered :b)))
     (doall (repeatedly 188 #(force-alt test1 :c)))
     (doall (repeatedly 61 #(force-score test1 :registered :c)))
     (doall (repeatedly 182 #(force-alt test2 :control)))
     (doall (repeatedly 35 #(force-score test2 :registered :control)))
     (doall (repeatedly 179 #(force-alt test2 :a)))
     (doall (repeatedly 46 #(force-score test2 :registered :a)))
     (fact (results test1 :control) =>
           (just
            {:test-name test1
             :test-type :ab-test
             :alternatives
             (just
              [{:alternative-name :control
                :count 182
                :control true
                :goal-results [{:goal-name :registered
                                :score 35
                                :z-score 0.0}]}
               {:alternative-name :a
                :count 180
                :goal-results [{:goal-name :registered
                                :score 45
                                :z-score 1.3252611961151077}]}
               {:alternative-name :b
                :count 189
                :goal-results [{:goal-name :registered
                                :score 28
                                :z-score -1.1322354025976662}]}
               {:alternative-name :c
                :count 188
                :goal-results [{:goal-name :registered
                                :score 61
                                :z-score 2.941015722492861}]}] :in-any-order)}))
     (fact (results test2 :control) =>
           (just {:test-name test2
                  :test-type :ab-test
                  :alternatives
                  (just
                   [{:alternative-name :control
                     :count 182
                     :control true
                     :goal-results [{:goal-name :registered
                                     :score 35
                                     :z-score 0.0}]}
                    {:alternative-name :a
                     :count 179
                     :goal-results [{:goal-name :registered
                                     :score 46
                                     :z-score 1.4759505897323522}]}]
                   :in-any-order)}))
     (end test1 :a)
     (end test2 :a)))

  ;; get results for all tests
  (time
   (do
     (fact (get-all-active-test-results) => [])
     (let [identity1 (keyword (str (rand)))
           test1 (keyword (str (rand)))
           test2 (keyword (str (rand)))]
       (with-identity identity1
         (alt-fns test1 {:a (fn [] "a")})
         (alt-fns test2 {:b (fn [] "b")})
         (fact (get-all-active-test-results) => #(= (count %) 2))
         (end test1 :a)
         (end test2 :b)))
     (fact (get-all-active-test-results) => [])))

  ;; all alternatives are randomly distributed
  (time
   (do
     (let [test1 (keyword (str (rand)))]
       (doall (repeatedly
               10000
               #(with-identity (str (rand))
                  (alt-fns test1 {:a (fn [] "a")
                                  :b (fn [] "b")
                                  :c (fn [] "c")}))))
       (let [r (results test1 :a)]
         ;; Make sure we are within 2% of the expected distribution.
         ;; Obviously, this can fail on occasion.
         (fact (< (cmath/abs (- (:count (first (:alternatives r)))
                                (:count (second (:alternatives r))))) 200) =>
                                truthy)
         (fact (< (cmath/abs (- (:count (last (:alternatives r)))
                                (:count (second (:alternatives r))))) 200) =>
                                truthy))
       (end test1 :a))))

  ;; all weighed alternatives are randomdly distributed by weights
  (time
   (do
     (let [test1 (keyword (str (rand)))]
       (doall (repeatedly
               10000
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
                                (* 0.10 10000))) 200) => truthy)
         (fact (< (cmath/abs (- (:count b)
                                (* 0.20 10000))) 200) => truthy)
         (fact (< (cmath/abs (- (:count c)
                                (* 0.30 10000))) 200) => truthy)
         (fact (< (cmath/abs (- (:count d)
                                (* 0.40 10000))) 200) => truthy)
         (end test1 :a)))))

  ;; exceptions and bad parameters
  (time
   (do
     (let [test1 (keyword (str (rand)))
           non-existent-test (keyword (str (rand)))
           identity1 (keyword (str (rand)))]
       ;; exceptions off
       (fact (set-config! {}) => nil)
       (fact (alt-fns test1 {:a (fn [] "a")}) => nil)
       (with-identity identity1
         (fact (alt-fns nil {:a (fn [] "a")}) => nil)
         (fact (alt-fns test1 nil) => nil)
         (fact (alt-fns test1 {}) => nil)
         (fact (alt-fns test1 {:a (fn [] "a")}) => "a")
         (fact (results nil :a) => nil)
         (fact (results non-existent-test :a) => nil)
         (fact (score nil :a) => nil))
       (fact (score test1 :a) => nil)
       (fact (end nil :a) => nil)
       (fact (end non-existent-test :a) => nil)
       (fact (end test1 :b) => nil)
       (fact (end test1 nil) => nil)
       (end test1 :a))

     ;; exceptions on
     (let [test1 (keyword (str (rand)))
           non-existent-test (keyword (str (rand)))
           identity1 (keyword (str (rand)))]
       (fact (set-config! {:store store :throw-exceptions true}) =>
             (contains {:store store :throw-exceptions true
                        :test-creation-lock #(not (nil? %))}))
       (fact (set-config! {}) =>
             (throws Exception "set-config! store is nil"))
       (fact (alt-fns test1 {:a (fn [] "a")}) =>
             (throws Exception "identity not set"))
       (with-identity identity1
         (fact (alt-fns nil {:a (fn [] "a")}) =>
               (throws Exception "no test name"))
         (fact (alt-fns test1 nil) =>
               (throws Exception "nil or empty alternative map"))
         (fact (alt-fns test1 {}) =>
               (throws Exception "nil or empty alternative map"))
         (fact (alt-fns test1 {:a (fn [] "a")}) => "a")
         (fact (results nil :a) =>
               (throws Exception "no such test name"))
         (fact (results non-existent-test :a) =>
               (throws Exception "no such test name"))
         (fact (score nil :a) =>
               (throws Exception "no such test name")))
       (fact (score test1 :a) =>
             (throws Exception "identity not set"))
       (fact (end nil :a) =>
             (throws Exception "no such test name"))
       (fact (end non-existent-test :a) =>
             (throws Exception "no such test name"))
       (fact (end test1 :b) =>
             (throws Exception "no such alternative"))
       (fact (end test1 nil) =>
             (throws Exception "no such alternative"))
       (end test1 :a)))))



