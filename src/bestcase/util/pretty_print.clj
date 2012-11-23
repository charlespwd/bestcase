(ns bestcase.util.pretty-print
  (:require [clojure.math.numeric-tower :as cmath]))

;; (not proud of this one)
(defn result->string-seq
  "Takes a result map and returns a sequence of strings that are
   easier to look at for a human."
  [result]
  (let [all-goals (into #{} (flatten (for [a (:alternatives result)
                                           r (:goal-results a)]
                                       (:goal-name r))))]
    (remove
     nil?
     (flatten
      [(str "Test Name: " (:test-name result))
       (str "Test Type: " (if (= :ab-test (:test-type result)) "AB" "Unknown"))
       (for [a (:alternatives result)
             :let [r (into {} (for [r (:goal-results a)]
                                [(:goal-name r) r]))]]
         [(str "  " (:alternative-name a) " (" (:count a) " trials)"
               (if (:control a) " CONTROL"))
          (if (< (:count a) 20)
            (str "  Take these results with a grain "
                 "of salt since your sample size is small!"))
          (for [g all-goals
                :when (not (nil? (g r)))]
            (str "    " g "  (" (:score (g r)) " or "
                 (float (* (/ (:score (g r)) (:count a)) 100)) "%) ["
                 (:z-score (g r))
                 (condp < (cmath/abs (:z-score (g r)))
                   3.08 " 99.9%] **** extremely confident"
                   2.33 " 99%] *** very confident"
                   1.65 " 95%] ** confident"
                   1.29 " 90%] * fairly confident"
                   "] not yet confident")))])]))))
       