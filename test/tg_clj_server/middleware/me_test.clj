(ns tg-clj-server.middleware.me-test
  (:require [clojure.test :refer [deftest testing is]]
            [tg-clj-server.middleware.me :as me]
            [tg-clj.core :as tg]))

(deftest middleware
  (testing "Adds the :me key to the request map, containing the user object for the client."
    (let [final-request (atom :unset)
          handler (me/middleware
                   (fn [request]
                     (reset! final-request request)))
          request {:client :client}]
      (with-redefs [tg/invoke (fn [_client _op]
                                {:result :called})]
        (handler request))
      (is (= (assoc request :me :called)
             @final-request))))

  (testing "The user object is cached"
    (let [handler (me/middleware identity)
          request {:client :client}
          calls (atom 0)]
      (with-redefs [tg/invoke (fn [_client _op]
                                {:result (swap! calls inc)})]
        (is (= (handler request)
               (handler request)
               (assoc request :me 1)))
        (is (= 1 @calls)))))

  (testing "Two instances have different caches"
    (let [handler1 (me/middleware identity)
          handler2 (me/middleware identity)
          request {:client :client}
          calls (atom 0)]
      (with-redefs [tg/invoke (fn [_client _op]
                                {:result (swap! calls inc)})]
        (is (= (handler1 request)
               (handler1 request)
               (assoc request :me 1)))
        (is (= (handler2 request)
               (handler2 request)
               (assoc request :me 2)))
        (is (= 2 @calls))))))
