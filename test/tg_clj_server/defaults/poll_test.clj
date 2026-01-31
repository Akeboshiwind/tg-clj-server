(ns tg-clj-server.defaults.poll-test
  (:require [clojure.test :refer [deftest testing is]]
            [tg-clj-server.defaults.poll :as defaults]
            [tg-clj.core :as tg]))

(defn funky? [request]
  (:funky request))

(deftest make-app-test
  (testing "Integration"
    (testing "No middleware"
      (let [test-calls (atom 0)
            funky-calls (atom 0)
            not-found-calls (atom 0)
            invoke-calls (atom 0)
            last-test-request (atom :unset)
            routes [["/test" (fn [request]
                               (reset! last-test-request request)
                               (swap! test-calls inc)
                               {:set-store {:a 1}
                                :op :my-operator})]
                    [funky? (fn [_] (swap! funky-calls inc))]]
            app (defaults/make-app routes
                                   {:router/not-found
                                    (fn [_] (swap! not-found-calls inc))})]
        (with-redefs [tg/invoke (fn [_client _request]
                                  (swap! invoke-calls inc)
                                  {:ok true :result :called})]
          (testing "After app created"
            (is (= 0 @test-calls))
            (is (= 0 @funky-calls))
            (is (= 0 @not-found-calls))
            (is (= 0 @invoke-calls))
            (is (= :unset @last-test-request)))

          (testing "No route called"
            (app {})
            (is (= 0 @test-calls))
            (is (= 0 @funky-calls))
            (is (= 1 @not-found-calls) "No route found")
            (is (= 1 @invoke-calls) "Invoked by :me middleware")
            (is (= :unset @last-test-request)))

          (testing "Funky route called"
            (app {:funky true})
            (is (= 0 @test-calls))
            (is (= 1 @funky-calls) "Route called")
            (is (= 1 @not-found-calls))
            (is (= 1 @invoke-calls))
            (is (= :unset @last-test-request)))

          (testing "Test route called"
            (app {:update {:message {:text "/test"}}})
            (is (= 1 @test-calls) "Route called")
            (is (= 1 @funky-calls))
            (is (= 1 @not-found-calls))
            (is (= 2 @invoke-calls) "Invoke middleware")
            (is (= #{:update :store :route :me}
                   (-> @last-test-request keys set)))))))

    (testing "With middleware"
      (let [last-test-request (atom :unset)
            routes [["/test" (fn [request]
                               (reset! last-test-request request))]]
            my-middleware (fn [handler arg]
                            (fn [request]
                              (handler (assoc request :my-middleware arg))))
            app (defaults/make-app routes
                                   {:middleware [[my-middleware :called]]})]
        (with-redefs [tg/invoke (fn [_client _request]
                                  {:ok true :result :called})]
          (testing "Test route called"
            (app {:update {:message {:text "/test"}}})
            (is (= #{:update :store :route :me :my-middleware}
                   (-> @last-test-request keys set)))))))))
