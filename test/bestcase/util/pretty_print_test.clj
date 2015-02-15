(ns bestcase.util.pretty-print-test
  (:use [bestcase.core]
        [bestcase.for-testing]
        [bestcase.store.memory]
        [bestcase.util.pretty-print]
        [midje.sweet]
        [clojure.test])
  (:require [taoensso.carmine :as car]))

(deftest all-tests
  (time
   (let [r1 {:test-name :test-1
            :test-type :ab-test
            :alternatives [{:alternative-name :c
                            :count 188
                            :goal-results [{:goal-name :registered
                                            :score 61
                                            :z-score 2.941015722492861}]}
                           {:alternative-name :b
                            :count 189
                            :goal-results [{:goal-name :registered
                                            :score 28
                                            :z-score -1.1322354025976662}]}
                           {:alternative-name :a
                            :count 180
                            :goal-results [{:goal-name :paid
                                            :score 175
                                            :z-score 54.83514153989365}
                                           {:goal-name :registered
                                            :score 45
                                            :z-score 1.3252611961151077}]}
                           {:control true
                            :alternative-name :control
                            :count 182
                            :goal-results [{:goal-name :paid
                                            :score 5
                                            :z-score 0.0}
                                           {:goal-name :registered
                                            :score 35
                                            :z-score 0.0}]}]}
         r2 {:test-name :test-1
             :test-type :ab-test
             :alternatives [{:alternative-name :c
                             :count 188
                             :goal-results [{:goal-name :registered
                                             :score 61
                                             :z-score 1.5849428764730593}]}
                            {:alternative-name :b
                             :count 189
                             :goal-results [{:goal-name :registered
                                             :score 28
                                             :z-score -2.4634739311304963}]}
                            {:control true
                             :alternative-name :a
                             :count 180
                             :goal-results [{:goal-name :paid
                                             :score 175
                                             :z-score 0.0}
                                            {:goal-name :registered
                                             :score 45
                                             :z-score 0.0}]}
                            {:alternative-name :control
                             :count 182
                             :goal-results [{:goal-name :paid
                                             :score 5
                                             :z-score -54.83514153989365}
                                            {:goal-name :registered
                                             :score 35
                                             :z-score -1.3252611961151077}]}]}
         r3 {:test-name :test-2
             :test-type :ab-test
             :alternatives [{:alternative-name :a
                             :count 8
                             :goal-results [{:goal-name :registered
                                             :score 2
                                             :z-score -4.898979485566357}]}
                            {:control true
                             :alternative-name :control
                             :count 10
                             :goal-results [{:goal-name :registered
                                             :score 10
                                             :z-score 0.0}]}]}]
     (fact (result->string-seq r1) =>
           ["Test Name: :test-1"
            "Test Type: AB"
            "  :c (188 trials)"
            "    :registered  (61 or 32.446808%) [2.941015722492861 99%] *** very confident"
            "  :b (189 trials)"
            "    :registered  (28 or 14.814815%) [-1.1322354025976662] not yet confident"
            "  :a (180 trials)"
            "    :paid  (175 or 97.22222%) [54.83514153989365 99.9%] **** extremely confident"
            "    :registered  (45 or 25.0%) [1.3252611961151077 90%] * fairly confident"
            "  :control (182 trials) CONTROL"
            "    :paid  (5 or 2.7472527%) [0.0] not yet confident"
            "    :registered  (35 or 19.23077%) [0.0] not yet confident"])
     (fact (result->string-seq r2) =>
           ["Test Name: :test-1"
            "Test Type: AB"
            "  :c (188 trials)"
            "    :registered  (61 or 32.446808%) [1.5849428764730593 90%] * fairly confident"
            "  :b (189 trials)"
            "    :registered  (28 or 14.814815%) [-2.4634739311304963 99%] *** very confident"
            "  :a (180 trials) CONTROL"
            "    :paid  (175 or 97.22222%) [0.0] not yet confident"
            "    :registered  (45 or 25.0%) [0.0] not yet confident"
            "  :control (182 trials)"
            "    :paid  (5 or 2.7472527%) [-54.83514153989365 99.9%] **** extremely confident"
            "    :registered  (35 or 19.23077%) [-1.3252611961151077 90%] * fairly confident"])
     (fact (result->string-seq r3) =>
           ["Test Name: :test-2"
            "Test Type: AB"
            "  :a (8 trials)"
            "  Take these results with a grain of salt since your sample size is small!"
            "    :registered  (2 or 25.0%) [-4.898979485566357 99.9%] **** extremely confident"
            "  :control (10 trials) CONTROL"
            "  Take these results with a grain of salt since your sample size is small!"
            "    :registered  (10 or 100.0%) [0.0] not yet confident"]))))
