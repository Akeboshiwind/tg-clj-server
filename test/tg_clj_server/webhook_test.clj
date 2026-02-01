(ns tg-clj-server.webhook-test
  (:require [clojure.test :refer [deftest testing is]]
            [tg-clj-server.webhook :as webhook]
            [cheshire.core :as json])
  (:import [java.io ByteArrayInputStream]))

(defn- string->input-stream [s]
  (ByteArrayInputStream. (.getBytes s "UTF-8")))

(defn- make-request
  "Helper to create a Ring request map"
  [{:keys [method body headers]
    :or {method :post
         headers {}}}]
  {:request-method method
   :headers headers
   :body (when body (string->input-stream body))})

(deftest validate-request-test
  (let [handler (fn [_] nil)
        validate (partial #'webhook/validate-request {:secret-token "mysecret"})
        ring-handler (#'webhook/make-ring-handler nil handler validate)]

    (testing "rejects non-POST methods"
      (let [response (ring-handler (make-request {:method :get}))]
        (is (= 405 (:status response)))
        (is (= "Method Not Allowed" (:body response)))))

    (testing "rejects missing secret token"
      (let [response (ring-handler (make-request {:body "{}"}))]
        (is (= 401 (:status response)))
        (is (= "Unauthorized" (:body response)))))

    (testing "rejects wrong secret token"
      (let [response (ring-handler (make-request
                                     {:body "{}"
                                      :headers {"x-telegram-bot-api-secret-token" "wrong"}}))]
        (is (= 401 (:status response)))
        (is (= "Unauthorized" (:body response)))))

    (testing "accepts valid request"
      (let [response (ring-handler (make-request
                                     {:body "{\"update_id\": 123}"
                                      :headers {"x-telegram-bot-api-secret-token" "mysecret"}}))]
        (is (= 200 (:status response))))))

  (testing "without secret token configured"
    (let [handler (fn [_] nil)
          validate (partial #'webhook/validate-request {:secret-token nil})
          ring-handler (#'webhook/make-ring-handler nil handler validate)]
      (testing "accepts request without token"
        (let [response (ring-handler (make-request {:body "{\"update_id\": 123}"}))]
          (is (= 200 (:status response))))))))

(deftest telegram-method-conversion-test
  (testing "converts response to Telegram format"
    (let [response {:op :sendMessage
                    :request {:chat_id 123
                              :text "Hello"}}
          result (#'webhook/->telegram-method response)]
      (is (= "sendMessage" (:method result)))
      (is (= 123 (:chat_id result)))
      (is (= "Hello" (:text result))))))

(deftest handler-response-test
  (testing "returns response in body when handler returns :op"
    (let [handler (fn [_] {:op :sendMessage
                           :request {:chat_id 123 :text "Hi"}})
          validate (constantly nil)
          ring-handler (#'webhook/make-ring-handler nil handler validate)
          response (ring-handler (make-request {:body "{\"update_id\": 1}"}))]
      (is (= 200 (:status response)))
      (is (= "application/json" (get-in response [:headers "Content-Type"])))
      (let [body (json/decode (:body response) true)]
        (is (= "sendMessage" (:method body)))
        (is (= 123 (:chat_id body)))
        (is (= "Hi" (:text body))))))

  (testing "returns plain OK when handler returns nil"
    (let [handler (fn [_] nil)
          validate (constantly nil)
          ring-handler (#'webhook/make-ring-handler nil handler validate)
          response (ring-handler (make-request {:body "{\"update_id\": 1}"}))]
      (is (= 200 (:status response)))
      (is (= "OK" (:body response)))))

  (testing "returns 200 OK on handler error"
    (let [handler (fn [_] (throw (Exception. "boom")))
          validate (constantly nil)
          ring-handler (#'webhook/make-ring-handler nil handler validate)
          response (ring-handler (make-request {:body "{\"update_id\": 1}"}))]
      (is (= 200 (:status response)))
      (is (= "OK" (:body response))))))
