(ns bestcase.for-testing
  (:use [bestcase.core]))

;; only use the hereunder if you really know what you are doing
;; ------------------------------------------------------------

(defn force-alt
  "Increments a test-name / alternative-name without requiring an identity."
  [test-name alternative-name]
  (let [store (:store (get-config))
        t (get-test store test-name)]
    (if (not (contains? (:alternative-names t) alternative-name))
      (set-test! store test-name
                 {:test-name test-name
                  :alternative-names
                  (into #{} (remove nil?
                                    (concat #{alternative-name}
                                            (:alternative-names t))))}))
    (if (not (:winner t))
      (do (increment-count! store test-name alternative-name)
          true)
      false)))

(defn force-score
  "Increments a test-name / goal-name / alternative-name without requiring
   an identity."
  [test-name goal-name alternative-name]
  (let [store (:store (get-config))]
    (increment-score! store test-name (str (name alternative-name) "|"
                                           (name goal-name)))))