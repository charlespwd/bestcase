(ns bestcase.core
  (:require [clojure.math.numeric-tower :as cmath]))

;; store
;; -----

(defprotocol Store
  "Abstraction of a store for bestcase tests, counts, scores, etc."
  (get-test [store test-name]
    "Get a test's descriptor.")
  (set-test! [store test-name descriptor]
    "Set an active test's descriptor (not per-user) by merging with
     the current descriptor.")
  (get-all-active-tests [store]
    "Get the descriptors for all tests that have not been ended.")
  (get-all-inactive-tests [store]
    "Get the descriptors for all tests that have been ended.")
  (end-test! [store test-name]
    "End a test.")
  (get-alternative-name [store test-name i]
    "Get the alternative-name for a test-identity.")
  (set-alternative-name! [store test-name i alternative-name]
    "Set the alternative-name for a test-identity.")
  (get-count [store test-name alternative-name]
    "Get the number of times this test-alternative has been run.")
  (get-all-counts [store test-name]
    "Get the number of times every one of the test's
     alternatives have been run.")
  (get-all-scores [store test-name]
    "Get the scores for all of the test's alternatives.")
  (increment-count! [store test-name alternative-name]
    "Increment the count of the number of times this test-alternative
     has been run.")
  (increment-score! [store test-name alternative-name-and-goal-name]
    "Increment the score for this test-goal-alternative.")
  (get-user-scored-count [store test-name goal-name i]
    "How many times a user scored for a particular test-goal.")
  (increment-user-scored! [store test-name goal-name i]
    "Set that a user scored for a particular test-goal."))

;; config
;; ------

(def ^:dynamic *config*
  (atom {:throw-exceptions false
         :test-creation-lock (atom #{})}))

(defn set-config!
  "Set the config. The map should have :store -> Store.  The map
   may have :throw-exceptions -> boolean that determines whether
   bestcase should try to suppress exceptions a bit (but never
   totally; use (try ... (catch ...)) if you want guarantees)."
  [{:keys [store throw-exceptions]
    :as c}]
  (if-not (nil? store)
    (let [c (assoc c :test-creation-lock (atom #{}))]
      (reset! *config* (select-keys c [:store
                                       :throw-exceptions
                                       :test-creation-lock]))
      @*config*)
    (if (:throw-exceptions @*config*)
      (throw (Exception. "set-config! store is nil")))))

(defn get-config
  "Get the current relevant config."
  []
  @*config*)

(defmacro with-config
  "Dynamically set the config for all bestcase function calls within."
  [config & body]
  `(binding [*config* (atom (assoc ~config :test-creation-lock (atom #{})))]
     ~@body))

;; identity
;; --------

(def ^:dynamic *identity* nil)

(def ^:dynamic *bot* false)

(def ^:dynamic *easy-testing* false)

(defmacro with-identity
  "Dynamically set the identity of all bestcase function calls within."
  [i & body]
  `(binding [*identity* ~i] ~@body))

(defmacro with-identity-and-bot-and-easy-testing
  "Dynamically sets the identity of all bestcase function calls within.
   Also allows you to set whether the current user is a bot, as well
   as the ability to pre-determine (alt ...) choices through the
   et (easy-testing) map:
   {:test-name-1 :force-alternative-name
    :test-name-2 :force-alternative-name-2
    ...}

   You probably shouldn't be using this."
  [i b? et? & body]
  `(binding [*identity* ~i
             *bot* ~b?
             *easy-testing*
             (if ~et? (into {} (for [[k# v#] ~et?]
                                 [(keyword k#) (keyword v#)])))]
     ~@body))

(defn- pick-weighed-random
  [alternative-names-and-value-fns]
  (let [ks (vec (keys alternative-names-and-value-fns))
        pick (rand-int (apply + (map second ks)))
        key-index (loop [i 0 p pick]
                    (if (< p (second (nth ks i)))
                      i (recur (inc i) (- p (second (nth ks i))))))]
    (first (nth ks key-index))))

(defmacro alt
  "Takes a test-name and a series of key-value paris, followed by an
   option map (optional). The test-name and keys should all be
   clojure keywords or vectors [:alternative-name int-weight] for
   weighed alternatives.

   Example:
   (alt :my-test
        :alternative-1 \"a1\"
        :alternative-2 \"a2\"
        :alternative-3 \"a3\"
        {:multiple-participation true})

   (alt :my-test
        [:alternative-1 10] \"a1\"
        [:alternative-2 40] \"a2\"
        [:alternative-3 50] \"a3\"
        {:multiple-participation true})

   The macro wraps values in anonymous functions, so \"a1\" becomes
   #(\"a1\"), which are called each and everytime the corresponding
   alternative is chosen.

   The option map currently only supports one option :multiple-particpation
   which determines whether the same identity can be counted more than once
   for the test.  It defaults to false."
  [test-name & alternatives-and-options]
  (let [[alternatives options]
        (if (even? (count alternatives-and-options))
          [alternatives-and-options nil]
          [(drop-last alternatives-and-options)
           (last alternatives-and-options)])
        alternatives-names-and-value-fns
        (into {} (for [[k v] (partition 2 alternatives)] [k `(fn [] ~v)]))]
    `(alt-fns ~test-name ~alternatives-names-and-value-fns ~options)))

(defn alt-fns
  "Does the work of the (alt ...) macro.  It works the same except that its
   second argument is a map of key-value pairs where the keys are
   clojure keywords (or vectors of [:alternative-name int-weight]) and
   the values are functions.

   Example:
   (alt-fns my-test
            {:alternative-1 #(\"a1\")
             :alternative-2 #(\"a2\")}
            {:multiple-participation true})"
  ([test-name alternative-names-and-value-fns]
     (alt-fns test-name alternative-names-and-value-fns nil))
  ([test-name alternative-names-and-value-fns options]
     (if (and *identity*
              test-name
              (map? alternative-names-and-value-fns)
              (not (empty? alternative-names-and-value-fns)))
       (let [store (:store @*config*)
             ;; support weighed-alternatives
             an->fn (into {} (for [[k v] alternative-names-and-value-fns]
                               (if (vector? k) [(first k) v] [k v])))
             t (if-let [t (get-test store test-name)]
                 t
                 ;; create a new test descriptor, make sure no one
                 ;; else is currently creating a descriptor
                 ;; (not multi-server safe, and may break with
                 ;; extremely high request)
                 (do (while (get (deref (:test-creation-lock @*config*))
                                 test-name) (Thread/sleep 10))
                     (reset! (:test-creation-lock @*config*)
                             (conj (deref (:test-creation-lock @*config*))
                                   test-name))
                     (let [descriptor {:test-name test-name
                                       :alternative-names (keys an->fn)}]
                       (set-test! store test-name descriptor)
                       (reset! (:test-creation-lock @*config*)
                               (disj (deref (:test-creation-lock @*config*))
                                     test-name))
                       descriptor)))]
         (if (:winner t)
           ((an->fn (:winner t)))
           (let [alternative-name
                 (or (and *easy-testing* (get *easy-testing* test-name))
                     (get-alternative-name store test-name *identity*))
                 first-time (nil? alternative-name)
                 alternative-name
                 (if alternative-name alternative-name
                     (let [a ;; pick an alternative
                           (if (vector?
                                (first (keys alternative-names-and-value-fns)))
                             (pick-weighed-random
                              alternative-names-and-value-fns)
                             (rand-nth
                              (keys alternative-names-and-value-fns)))]
                       ;; store the alternative for this user for future
                       ;; invocations of this test
                       (set-alternative-name! store test-name
                                              *identity* a) a))]
             (if (and (or first-time (:multiple-participation options))
                      (not *bot*))
               (increment-count! store test-name alternative-name))
             ((an->fn alternative-name)))))
       (if (:throw-exceptions @*config*)
         (do (if (not *identity*)
               (throw (Exception. "identity not set")))
             (if (not test-name)
               (throw (Exception. "no test name")))
             (if (or (not (map? alternative-names-and-value-fns))
                     (empty? alternative-names-and-value-fns))
               (throw (Exception. "nil or empty alternative map")))
             (throw (Exception. "unknown error")))))))

(defn score
  "Takes a test-name and a goal-name and tracks a conversion for the
   current identity for that test and goal.  You may also pass an
   option.

   Example:
   (score :my-test :purchase)

   (score :my-test :purchase {:multiple-participation true})

   The option map currently only supports :multiple-participation which
   determines whether the same identity can be scored more than once
   for the test-goal combination.  It defaults to false."
  ([test-name goal-name]
     (score test-name goal-name nil))
  ([test-name goal-name options]
     (if (and *identity* test-name)
       (let [store (:store @*config*)
             t (get-test store test-name)
             alternative-name (get-alternative-name store test-name *identity*)
             user-scored-count
             (if (not (:multiple-participation options))
               (get-user-scored-count store test-name goal-name *identity*))]
         (if (and (nil? user-scored-count) (not (:winner t))
                  alternative-name
                  (not *bot*))
           (do (increment-user-scored! store test-name goal-name *identity*)
               (increment-score! store test-name
                                 (str (name alternative-name) "|"
                                      (name goal-name))))))
       (if (:throw-exceptions @*config*)
         (do (if (not *identity*)
               (throw (Exception. "identity not set")))
             (if (not test-name)
               (throw (Exception. "no such test name"))))))))

(defn results
  "Returns a result map for test-name, where control-alternative-name is
   used as the control (base case) for purposes of statistical analysis.

   The result map has the following structure:

   {:test-name <keyword>
    :test-type :ab-test
    :alternatives [...]}

   where the value of the :alternatives key is itself a list of
   alternative maps:

   {:alternative-name <keyword>
    :count <int>
    :control true ;; optional, only set for control alternative
    :goal-results [...]}

   where the value of the :goal-results key is itself a list of
   goal maps:

   {:goal-name <keyword>
    :score <int>
    :z-score <float>}

   The z-score is calculated based on the current control alternative."
  [test-name control-alternative-name]
  (if test-name
    (let [store (:store @*config*)
          t (get-test store test-name)
          cs (get-all-counts store test-name)
          ss (get-all-scores store test-name)
          rs (for [[k c] cs]
               {:alternative-name k
                :count c
                :goal-results
                (for [[k2 s] ss :when (.startsWith (name k2) (str (name k) "|"))]
                  {:goal-name (subs (name k2) (inc (.length (name k))))
                   :score s})})
          control
          (if-let [c (some #(if (= control-alternative-name
                                   (:alternative-name %1)) %1) rs)]
            c (first rs))]
      (if (and t control)
        {:test-name test-name
         :test-type :ab-test
         :alternatives
         (for [r rs
               :let [nc (:count control)
                     r {:alternative-name (:alternative-name r)
                        :count (:count r)
                        :goal-results
                        (for [gr (:goal-results r)
                              :let [n (:count r)
                                    p (/ (:score gr) n)
                                    tmp (some #(if (= (:goal-name gr)
                                                      (:goal-name %1))
                                                 (:score %1) nil)
                                              (:goal-results control))
                                    tmp (if tmp tmp 0)
                                    pc (/ tmp nc)
                                    denom (cmath/sqrt
                                           (+ (/ (* p  (- 1 p))  n)
                                              (/ (* pc (- 1 pc)) nc)))]]
                          {:goal-name (keyword (:goal-name gr))
                           :score (:score gr)
                           :z-score (if-not (zero? denom)
                                      (/ (- p pc) denom) 0.0)})}]]
           (if (= control-alternative-name (:alternative-name r))
             (assoc r :control true) r))}
        (if (:throw-exceptions @*config*)
          (do (if (not t)
                (throw (Exception. (str "no such test name"))))
              (if (not control)
                (throw (Exception.
                        (str "no such control alternative: "
                             control-alternative-name))))))))
    (if (:throw-exceptions @*config*)
      (do (if (not test-name)
            (throw (Exception. "no such test name")))
          (throw (Exception. "unknown error"))))))

(defn get-all-active-test-results
  "Returns a list of (results ...) for all active (not-ended) tests."
  []
  (let [store (:store @*config*)
        ts (get-all-active-tests store)]
    (doall (for [[name v] ts
                 :when (and (not (nil? v))
                            (not (empty? v)))
                 :let [r (results name (first (:alternative-names v)))]]
             r))))

(defn get-all-inactive-test-results
  []
  "Returns a list of descriptors for all inactive (ended) tests, including
   the winning-alternative for that test."
  (get-all-inactive-tests (:store @*config*)))

(defn end
  "Ends test-name with alternative-name as the winner."
  [test-name alternative-name]
  (let [store (:store @*config*)
        t (get-test store test-name)]
    (if (and test-name
             t
             alternative-name
             (some #(= alternative-name %1) (:alternative-names t)))
      (do (set-test! store test-name {:winner alternative-name})
          (end-test! store test-name))
      (if (:throw-exceptions @*config*)
        (do (if (or (not test-name) (not t))
              (throw (Exception. "no such test name")))
            (if (or (not alternative-name)
                    (not (some #(= alternative-name %1)
                               (:alternative-names t))))
              (throw (Exception. "no such alternative")))
            (throw (Exception. "unknown error")))))))
