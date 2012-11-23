(ns bestcase.util.ring-test
  (:use [bestcase.core]
        [bestcase.util.ring]
        [bestcase.store.memory]
        [bestcase.for-testing]
        [compojure.core]
        [ring.mock.request]
        [ring.middleware.params]
        [ring.adapter.jetty]
        [midje.sweet]
        [clojure.test]))

(def ring-test (keyword (str "a" (rand))))

(deftest all-tests

  (fact (set-config! {:store (create-memory-store)}) =>
        #(not (nil? %)))

  (defroutes test-app
    (GET "/" [] "index")
    (GET "/ab-route" []
         (alt ring-test
              :alternative-1 "a1"
              :alternative-2 "a2"
              :alternative-3 "a3"))
    ;; add the bestcase dashboard routes
    (dashboard-routes "/bestcase" {:css []}))
  
  (time
   (let [test-app-with-identity ((identity-middleware-wrapper
                                  default-identity-fn
                                  {:simple-no-bots true}) test-app)
         r (request :get "/")]
     ;; normal route, no a/b test, still set a bestcase id if none
     (fact (test-app-with-identity r) =>
           (just
            {:session (just {:bestcase string?})
             :status 200
             :headers {"Content-Type" "text/html; charset=utf-8"}
             :body "index"}))
     ;; normal route, no a/b test, if user has a bestcase id, use it
     (fact (test-app-with-identity (assoc-in r [:session :bestcase] "id"))
           =>
           {:status 200
            :headers {"Content-Type" "text/html; charset=utf-8"}
            :body "index"})))

  (time
   (let [test1 (keyword (str (rand)))
         test2 (keyword (str (rand)))]
     ;; simulate tests
     (doall (repeatedly 182 #(force-alt test1 :control)))
     (doall (repeatedly 35 #(force-score test1 :registered :control)))
     (doall (repeatedly 5 #(force-score test1 :paid :control)))
     (doall (repeatedly 180 #(force-alt test1 :a)))
     (doall (repeatedly 45 #(force-score test1 :registered :a)))
     (doall (repeatedly 175 #(force-score test1 :paid :a)))
     (doall (repeatedly 189 #(force-alt test1 :b)))
     (doall (repeatedly 28 #(force-score test1 :registered :b)))
     (doall (repeatedly 188 #(force-alt test1 :c)))
     (doall (repeatedly 61 #(force-score test1 :registered :c)))
     (doall (repeatedly 10 #(force-alt test2 :control)))
     (doall (repeatedly 10 #(force-score test2 :registered :control)))
     (doall (repeatedly 8 #(force-alt test2 :a)))
     (doall (repeatedly 2 #(force-score test2 :registered :a)))
     (fact (test-app (request :get "/")) =>
           {:status 200
            :headers  {"Content-Type" "text/html; charset=utf-8"}
            :body "index"})
     ;; check that dashboard has results
     (fact (test-app (request :get "/bestcase")) =>
           (contains
            {:status 200
             :headers  {"Content-Type" "text/html; charset=utf-8"}
             :body string?}))
     ;; can end test, as expected
     (fact (test-app (request :post (str "/bestcase/test/" (name test1)
                                        "/alternative/" (name :b)
                                        "/choose-and-end-test")))
           =>
           {:status 302, :headers {"Location" "/bestcase"}, :body ""})
     ;; can't end test that's already ended
     (fact (test-app (request :post (str "/bestcase/test/" "dne"
                                         "/alternative/" (name :b)
                                         "/choose-and-end-test")))
           =>
           {:status 400
            :headers {}
            :body "Error"})
     ;; can end second test
     (fact (test-app (request :post (str "/bestcase/test/" (name test2)
                                         "/alternative/" (name :a)
                                         "/choose-and-end-test")))
           =>
           {:status 302, :headers {"Location" "/bestcase"}, :body ""})
     ;; dashboard empty if there are no more tests
     (fact (test-app (request :get "/bestcase")) =>
           {:status 200
            :headers {"Content-Type" "text/html; charset=utf-8"}
            :body "<!DOCTYPE html>\n<html><head></head><body><div class=\"container\"><div class=\"row\"><div class=\"span12\"><div class=\"navbar\"><div class=\"navbar-inner\"><ul class=\"nav\"><li><a href=\"/bestcase\">Bestcase Dashboard</a></li><li class=\"divider-vertical\"></li><li><a href=\"/bestcase/old-tests\">Old Tests</a></li></ul></div></div><div class=\"results\"><div class=\"no-results\">No current tests.</div></div></div></div></div></body></html>"})
     ;; can get results on a per-test basis
     (fact (test-app (request :get (str "/bestcase/test/" (name test1)
                                         "/alternative/" (name :b)
                                         "/control")))
           =>
           (contains
            {:status 200
             :headers  {"Content-Type" "text/html; charset=utf-8"}
             :body string?}))
     ;; can get a list of old tests
     (fact (test-app (request :get "/bestcase/old-tests"))
           =>
           (contains
            {:status 200
             :headers  {"Content-Type" "text/html; charset=utf-8"}
             :body string?}))))

  ;; check ab routes w/ easy-testing
  (time
   (let [test-app-with-identity
         (wrap-params ((identity-middleware-wrapper
                        default-identity-fn
                        {:easy-testing true}) test-app))
         r0 (request :get "/ab-route")
         r1 (request :get (str "/ab-route?" (name ring-test) "=alternative-1"))
         r2 (request :get (str "/ab-route?" (name ring-test) "=alternative-2"))]
     ;; get a random results
     (fact (test-app-with-identity r0) =>
           (contains {:body #(or (= "a1" %) (= "a2" %) (= "a3" %))}))
     ;; can force one alterantive
     (fact (test-app-with-identity r1) =>
           (contains {:body (just "a1")}))
     ;; or the other
     (fact (test-app-with-identity r2) =>
           (contains {:body (just "a2")}))
     ;; ending the test trumps attempts to force an alternative
     (end ring-test :alternative-1)
     (fact (test-app-with-identity r2) =>
           (contains {:body (just "a1")})))))
  

