(ns tg-clj-server.middleware.invoke-test
  (:require [clojure.test :refer [deftest testing is]]
            [tg-clj.core :as tg]
            [tg-clj-server.middleware.invoke :as invoke]
            [tg-clj-server.utils :as u])
  (:import (clojure.lang ExceptionInfo)))

(deftest invoke-middleware
  (testing "Calls tg/invoke with the request if :op is present in the response"
    (testing ":op present"
      (let [response {:op :myOp :request {:a 1}}
            handler (invoke/invoke-middleware (constantly response))
            invoke-op (atom :unset)]
        (with-redefs [tg/invoke (fn [_client op]
                                  (reset! invoke-op op)
                                  {:ok true})]
          (handler {:client :client}))
        (is (= response @invoke-op))))
    (testing ":op not present"
      (let [handler (invoke/invoke-middleware identity)
            calls (atom 0)]
        (with-redefs [tg/invoke (fn [_client _op]
                                  (swap! calls inc)
                                  {:ok true})]
          (handler {:client :client}))
        (is (= 0 @calls)))))

  (testing "Retry on exception"
    (let [retry-fn #(u/retry % {:max-retries 1
                                :wait-time 0})
          handler (invoke/invoke-middleware (constantly {:op :myOp})
                                            {:retry retry-fn})
          calls (atom 0)]
      (with-redefs [tg/invoke (fn [_client _op]
                                (swap! calls inc)
                                ; Some client error
                                (throw (Exception. "My Error")))]
        (is (thrown-with-msg? Exception #"Max retries reached"
                              (handler {:client :client})))
        (is (> @calls 1)))))

  (testing "Doesn't retry on Telegram API error"
    (let [handler (invoke/invoke-middleware (constantly {:op :myOp}))
          calls (atom 0)]
      (with-redefs [tg/invoke (fn [_client _op]
                                (swap! calls inc)
                                ; Failure from telegram
                                {:ok false})]
        (is (thrown-with-msg? ExceptionInfo #"Failure from Telegram API"
                              (handler {:client :client})))
        (is (= 1 @calls))))))
