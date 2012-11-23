(ns bestcase.util.ring
  (:use [bestcase.core]
        [compojure.core]
        [ring.util.response]
        [hiccup.core]
	[hiccup.page])
  (:require [clojure.math.numeric-tower :as cmath])
  (:import java.util.UUID))

;; identity middlware
;; ------------------

(defn default-identity-fn
  "Basic identity function that looks in the \"bestcase\" cookie for a
   unique identifier, and if it doesn't find one, generates one using
   (java.util.UUID/randomUUID)."
  [request]
  (if-let [i (get-in request [:session :bestcase])] i (str (UUID/randomUUID))))

(def bot-regexp
  #"(alexa|bot|crawl(er|ing)|facebook|feedburner|google|google web preview|nagios|postrank|pingdom|slurp|spider|yahoo!|yandex)")

(defn- bot?
  "Returns true if the requets's user-agent header suggests the visitor is
   a bot.

   Note: in general, bots don't play nice, so this will miss just the kind
   of bots you probably want to exclude."
  [request]
  (if-let [user-agent (get-in request [:headers "user-agent"])]
    (and (not (nil? user-agent))
         (string? user-agent)
         (re-find bot-regexp (.toLowerCase user-agent)))))

(defn identity-middleware-wrapper
  "Function to create Ring middleware to automatically take care of
   setting the appropriate bestcase identity.  Can use a custom identity
   function if you want to, e.g., consistently track users from unregistered
   to registered. See the bestcase User Guide for more information.

   The final form takes both an identity function and an options map which
   supports two keys, :simple-no-bots and :easy-testing.

   :simple-no-bots => if true, tries to detect bots through the user-agent
                      header (defaults to false)
   :easy-testing => if true, looks at params in the request map to force
                    chosing a particular alternative; used for testing
                    (defaults to false)"
  ([] (identity-middleware-wrapper default-identity-fn {}))
  ([id-fn] (identity-middleware-wrapper id-fn {}))
  ([id-fn options]
     (fn [handler]
       (fn [request]
         (let [i (id-fn request)
               old-session (get-in request [:session :bestcase])
               resp (with-identity-and-bot-and-easy-testing
                      i
                      (and (:simple-no-bots options) (bot? request))
                      (and (:easy-testing options) (:params request))
                      (handler (assoc-in request [:session :bestcase] i)))]
           (if (nil? old-session)
             (assoc-in resp [:session :bestcase] i) resp))))))

;; bestcase web dashboard
;; ----------------------

(declare dashboard)
(declare old-tests)

(defn dashboard-routes
  "Returns a ring handle that matches the root route (and some children
   routes) to display a web-page dashboard of bestcase test results.

   The options map can take a list of paths to css files for the :css key
   and/or a list of paths to css files for the :js key.

   Example:

   (dashboard-routes \"/bestcase\" {:css [\"/css/bootstrap.min.css\"
                                        \"/css/dashboard.css\"]
                                  :js [\"/js/dashboard.js\"]})"
  ([] (dashboard-routes "/bestcase" {}))
  ([root] (dashboard-routes root {}))
  ([root options]
     (fn [request]
       (routing request

                ;; main dashboard
                (GET root []
                     (let [results (get-all-active-test-results)]
                       (dashboard root results options)))

                ;; list of old tests and winners
                (GET (str root "/old-tests") []
                     (let [results (get-all-inactive-test-results)]
                       (old-tests root results options)))

                ;; results for a specific test with a specific control
                (GET (str root "/test/:test-name/"
                          "alternative/:control-alternative-name/control")
                     [test-name control-alternative-name]
                     (dashboard root
                                [(results (keyword test-name)
                                          (keyword control-alternative-name))]
                                options))

                ;; choose a winner and end the test
                (POST (str root "/test/:test-name/"
                           "alternative/:alternative-name/"
                           "choose-and-end-test")
                      [test-name alternative-name]
                      (try
                        (if-let [r (end (keyword test-name)
                                        (keyword alternative-name))]
                          (redirect root) {:status 400
                                           :body "Error"})
                        (catch Exception e
                          {:status 400
                           :body "Error"})))))))

;; dashboard views
;; ---------------

(defn navbar
  [root]
  [:div.navbar
   [:div.navbar-inner
    [:ul.nav
     [:li [:a {:href root} "Bestcase Dashboard"]]
     [:li.divider-vertical]
     [:li [:a {:href (str root "/old-tests")} "Old Tests"]]]]])

(defn dashboard
  [root results options]
  (html5
   [:head
    (map include-css (:css options))
    (map include-js (:js options))]
   [:body
    [:div.container
     [:div.row
      [:div.span12
       (navbar root)
       [:div.results
        (if (< 0 (count results))
          (for [result results
                :let [all-goals
                      (into #{} (flatten (for [a (:alternatives result)
                                               r (:goal-results a)]
                                           (:goal-name r))))]]
            [:div.result
             [:div.test-name
              [:h2 "Test: " (:test-name result)]]
             [:div.test-type
              [:span "Type: " (:test-type result)]]
             [:div.alternatives
              (if (< 0 (count (:alternatives result)))
                [:table.table.table-bordered
                 [:thead
                  [:tr
                   [:td "Alternative"]
                   [:td "Trials"]
                   [:td "Goal"]
                   [:td "Score"]
                   [:td "Score %"]
                   [:td "Z-Score"]
                   [:td "Confidence %"]
                   [:td "Confidence"]
                   [:td "Actions"]]
                  (concat
                   [[:tbody]]
                   (for [a (:alternatives result)
                         :let [r (into {} (for [r (:goal-results a)]
                                            [(:goal-name r) r]))]]
                     (concat
                      [[:tr
                        [(if (:control a)
                           :td.alternative-name.control :td.alternative-name)
                         (if (:control a)
                           [:strong (:alternative-name a)]
                           (:alternative-name a))]
                        [:td.count (:count a)]
                        [:td {:colspan 6}]
                        [:td.actions
                         [:form {:action 
                                 (str root "/test/"
                                      (name (:test-name result))
                                      "/alternative/"
                                      (name (:alternative-name a))
                                      "/choose-and-end-test")
                                 :method "post"}
                          [:a.btn {:href "javascript:;"
                                   :onclick "parentNode.submit();"}
                           "choose and end"]]
                         (if (not (:control a))
                           [:a.btn {:href
                                    (str root "/test/"
                                         (name (:test-name result))
                                         "/alternative/"
                                         (name (:alternative-name a))
                                         "/control")}
                            "as control"])]]]
                      (for [g all-goals
                            :when (not (nil? (g r)))]
                        [:tr
                         [:td ""]
                         [:td ""]
                         [:td.goal-name g]
                         [:td.goal-score (:score (g r))]
                         [:td.goal-store-percentage
                          (str (float (* (/ (:score (g r))
                                            (:count a)) 100)) "%")]
                         [:td.z-score (:z-score (g r))]
                         [:td.confidence-percentage
                          (condp < (cmath/abs (:z-score (g r)))
                            3.08 "99.9%"
                            2.33 " 99%"
                            1.65 " 95%"
                            1.29 " 90%"
                             "")]
                         [:td.confidence
                          (if (> 20 (:count a))
                            [:div
                             "(Take these results with a grain of "
                             "salt since your sample size is small)"])
                          (condp < (cmath/abs (:z-score (g r)))
                            3.08 "**** extremely confident"
                            2.33 "*** very confident"
                            1.65 "** confident"
                            1.29 "* fairly confident"
                            "not yet confident")]
                         [:td]]))))]])]])
          [:div.no-results "No current tests."])]]]]]))

(defn old-tests
  [root results options]
  (html5
   [:head
    (map include-css (:css options))
    (map include-js (:js options))
    ]
   [:body
    (html5
     [:div.container
      [:div.row
       (navbar root)
       [:div
        [:table.table.table-bordered.old-test-tesults
         [:thead
          [:tr
           [:td "Test Name"]
           [:td "Winner Name"]]]
         [:tbody
          (for [[k v] results]
            [:tr
             [:td.test-name k]
             [:td.winner-name (:winner v)]])]]]]])]))
