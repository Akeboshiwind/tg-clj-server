(ns tg-clj-server.simple-router-test
  (:require [clojure.test :refer [deftest testing is]]
            [tg-clj-server.simple-router :as router]))

(deftest normalize-route-test
  (testing "A predicate and map are returned unchanged"
    (let [route-pair [(constantly true) {:handler identity}]]
      (is (= route-pair (router/normalize-route route-pair)))))

  (testing "String predicates"
    (testing "Must be valid commands"
      (is (thrown? IllegalArgumentException
                   (router/normalize-route ["invalid" {:handler identity}]))))
    (testing "If valid are turned into predicates"
      (let [[pred _] (router/normalize-route ["/command" {:handler identity}])]
        (is (fn? pred))
        (is (pred {:update {:message {:text "/command"}}}))
        (is (not (pred {:message {:text "/not-command"}}))))))

  (testing "Handler functions are wrapped in a map"
    (let [[_ route] (router/normalize-route [(constantly true) identity])]
      (is (map? route))
      (is (= identity (:handler route)))))

  (testing "Providing a map without a :handler key is an error"
    (is (thrown? IllegalArgumentException
                 (router/normalize-route [(constantly true) {}])))))

(deftest select-route-test
  (testing "Selects the first route that matches"
    (let [routes [[(constantly false) {:handler :a}]
                  [(constantly true) {:handler :b}]]]
      (is (= {:handler :b} (router/select-route {} routes)))))
  (testing "When provided no routes, selects nothing"
    (is (nil? (router/select-route {} [])))))

(deftest select-route-middleware-test
  (testing "Adds the first matching route under :route"
    (let [routes [[(constantly false) {:handler :a}]
                  [(constantly true) {:handler :b}]]
          final-request (atom :unset)
          handler (router/select-route-middleware
                   (fn [request]
                     (reset! final-request request))
                   routes)]
      (handler {})
      (is (= {:route {:handler :b}} @final-request))))

  (testing "Routes are normalised as expected"
    (let [routes [["/test" :a]]
          final-request (atom :unset)
          handler (router/select-route-middleware
                   (fn [request]
                     (reset! final-request request))
                   routes)]
      (handler {:update {:message {:text "/test"}}})
      (is (contains? @final-request :route))
      (is (= :a (get-in @final-request [:route :handler]))))))

(deftest execute-route-test
  (testing "Executes the :handler under :route"
    (let [calls (atom 0)
          request {:route {:handler (fn [_]
                                      (swap! calls inc)
                                      :response)}}]
      (is (= :response (router/execute-route request)))
      (is (= 1 @calls))))

  (testing "Returns nil when no route and handler is present"
    (is (nil? (router/execute-route {})))))
