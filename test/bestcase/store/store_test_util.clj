(ns bestcase.store.store-test-util
  (:use [bestcase.core]
        [midje.sweet]))

(defn all-store-tests
  [store]
  (time
   (let [description {:a 1
                      :b 2}
         test0 (keyword (str (rand)))
         test1 (keyword (str (rand)))]
     (fact (get-test store test0) => nil)
     (fact (set-test! store test1 description) => 1)
     (fact (get-test store test1) => description)
     (fact (get-alternative-name store test0 "robert") => nil)
     (fact (set-alternative-name! store test1 "robert" :alternative1) => "OK")
     (fact (get-alternative-name store test1 "robert") => :alternative1)
     (fact (get-count store test0 :alternative0) => 0)
     (fact (increment-count! store test1 :alternative1) => 1)
     (fact (get-count store test1 :alternative1) => 1)
     (fact (increment-count! store test1 :alternative1) => 2)
     (fact (get-count store test1 :alternative1) => 2)
     (fact (increment-count! store test1 :alternative2) => 1)
     (fact (get-count store test1 :alternative2) => 1)
     (fact (get-all-counts store test0) => {})
     (fact (get-all-counts store test1) => {:alternative1 2
                                            :alternative2 1})
     (fact (increment-score! store test1 :alternative1-goal1) => 1)
     (fact (increment-score! store test1 :alternative1-goal1) => 2)
     (fact (increment-score! store test1 :alternative1-goal2) => 1)
     (fact (get-all-scores store test0) => {})
     (fact (get-all-scores store test1) => {:alternative1-goal1 2
                                            :alternative1-goal2 1})
     (end-test! store test0)
     (end-test! store test1))))
  
