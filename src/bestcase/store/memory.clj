(ns bestcase.store.memory
  (:use [bestcase.core]
        [clojure.core.incubator :only [dissoc-in]]))

;; memory implementation of the Store protocol in bestcase.core
;; ------------------------------------------------------------

(deftype MemoryStore [am]
  Store
  (get-test [_ test-name]
    (if-let [t (get-in @am [:active-tests test-name])]
      t (get-in @am [:inactive-tests test-name])))
  (set-test! [_ test-name descriptor]
    (let [t (if-let [t (get-in @am [:active-tests test-name])] t {})]
      (swap! am (fn [am]
                  (assoc-in am [:active-tests test-name] (merge t descriptor))))
      1)) ;; same return value as the RedisStore
  (get-all-active-tests [_]
    (get-in @am [:active-tests]))
  (get-all-inactive-tests [_]
    (get-in @am [:inactive-tests]))
  (end-test! [_ test-name]
    (if-let [t (get-in @am [:active-tests test-name])]
      (swap! am (fn [am] (assoc-in (dissoc-in am [:active-tests test-name])
                                   [:inactive-tests test-name] t)))))
  (get-alternative-name [_ test-name i]
    (get-in @am [:alternative-name test-name i]))
  (set-alternative-name! [_ test-name i alternative-name]
    (swap! am (fn [am] (assoc-in am [:alternative-name test-name i]
                                 alternative-name)))
    "OK") ;; same return value as the RedisStore
  (get-count [_ test-name alternative-name]
    (if-let [c (get-in @am [:test-counts test-name alternative-name])] c 0))
  (get-all-counts [_ test-name]
    (if-let [cs (get-in @am [:test-counts test-name])] cs {}))
  (get-all-scores [_ test-name]
    (if-let [ss (get-in @am [:test-scores test-name])] ss {}))
  (increment-count! [_ test-name alternative-name]
    (get-in
     (swap! am (fn [am] (update-in am [:test-counts test-name alternative-name]
                                   #(if (integer? %1) (inc %1) 1))))
     [:test-counts test-name alternative-name]))
  (increment-score! [_ test-name alternative-name]
    (get-in
     (swap! am (fn [am] (update-in am [:test-scores test-name alternative-name]
                                   #(if (integer? %1) (inc %1) 1))))
     [:test-scores test-name alternative-name]))
  (get-user-scored-count [_ test-name goal-name i]
    (get-in @am [:test-user-scored test-name goal-name i]))
  (increment-user-scored! [_ test-name goal-name i]
    (get-in
     (swap! am (fn [am] (update-in am [:test-user-scored test-name goal-name i]
                                   #(if (integer? %1) (inc %1) 1))))
     [:test-user-scored test-name goal-name i])))

(defn create-memory-store
  "Returns a new memory-backed Store."
  []
  (MemoryStore. (atom {})))