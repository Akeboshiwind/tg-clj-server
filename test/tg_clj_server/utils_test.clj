(ns tg-clj-server.utils-test
  (:refer-clojure :exclude [update])
  (:require [clojure.test :refer [deftest testing is]]
            [tg-clj-server.utils :as u]
            [tg-clj-server.middleware.me :as me]
            [tg-clj.core :as tg]
            [tg-clj-server.test-utils :as tu]))

(deftest valid-command?-test
  (is (u/valid-command? "/version"))
  (is (not (u/valid-command? "/version@mybot")))
  (is (not (u/valid-command? "/")))
  (is (not (u/valid-command? "version"))))

(deftest command?-test
  (testing "Standard command"
    (let [cmd? (partial u/command? "/version")
          ->request (fn [text] {:update {:message {:text text}}})]
      (is (not (cmd? (->request "/ver"))))
      (is (not (cmd? (->request "/versionxxx"))))
      (is (not (cmd? (->request "/version@mybot"))))
      (is (not (cmd? (->request "something/version"))))
      (is (cmd? (->request "/version")))
      (is (cmd? (->request "some /version text")))))

  (testing ":me middleware integration"
    (let [cmd? (partial u/command? "/version")
          handler (-> (fn [request] (cmd? request))
                      me/middleware)
          ->request (fn [text] {:update {:message {:text text}}})]
      (with-redefs [tg/invoke (fn [_client _request]
                                {:result {:username "mybot"}})]
        (is (handler (->request "/version@mybot")))
        (is (not (handler (->request "/version@notmybot"))))))))

(deftest reply-to-test
  (testing "Updates the response when a message is in the update"
    (let [chat-id 1234
          message-id 5678
          response {}
          update {:message {:message_id message-id
                            :chat {:id chat-id}}}]
      (is (= {:request {:chat_id chat-id
                        :reply_parameters {:message_id message-id}}}
             (u/reply-to response update)))))

  (testing "Leaves the response alone if no message is present"
    (let [response {:a 1}
          update {}]
      (is (= response (u/reply-to response update))))))

(deftest all-mentions-test
  (testing "no mentions"
    (let [update {:message {:entities []}}]
      (is (empty? (u/all-mentions update)))))
  (testing "Works for users with username and without"
    (let [update {:message {:text "/test user1 @User2",
                            :entities [{:type "bot_command"
                                        :offset 0
                                        :length 5}
                                       {:type "text_mention"
                                        :offset 6
                                        :length 5
                                        :user {:id 1234}}
                                       {:type "mention"
                                        :offset 12
                                        :length 6}]}}]
      (is (= #{1234 "user2"}
             (u/all-mentions update))))))

(deftest retry-test
  (testing "Returns the result when no error"
    (is (= 1 (u/retry (constantly 1)))))

  (testing "Retries on throwable"
    (let [slept? (atom false)]
      (with-redefs [u/sleep (fn [_] (reset! slept? true))]
        (let [retries (atom 0)
              f #(if (zero? @retries)
                   (do (swap! retries inc)
                       (throw (Exception. "My Error")))
                   :some-value)]
          (is (= :some-value (u/retry f)))
          (is (= 1 @retries))
          (testing "Sleeps between retries"
            (is @slept?))))))

  (testing "Throws an exception when max retries is reached"
    (with-redefs [u/sleep identity]
      (let [calls (atom 0)
            f (fn [] (do (swap! calls inc)
                         (throw (Exception. "My Error"))))]
        (is (thrown-with-msg? Exception #"Max retries reached"
                              (u/retry f {:max-retries 3})))
        ; 3 retries + 1 initial call
        (is (= 4 @calls)))))

  (testing "wait-time is given the retry number"
    (with-redefs [u/sleep identity]
      (let [wait-time-args (atom [])
            wait-time (fn [n] (swap! wait-time-args conj n))
            f #(throw (Exception. "My Error"))]
        (is (thrown-with-msg? Exception #"Max retries reached"
                              (u/retry f {:max-retries 3
                                          :wait-time wait-time})))
        ; TODO: Does this make sense or is there a bug?
        ; 3 retries + 1 initial call
        (is (= [0 1 2 3] @wait-time-args)))))

  (let [wait #(Thread/sleep 1000)]
    (tu/test-can-be-cancelled
     #(u/retry wait {:max-retries 3 :wait-time 1000}))))

(deftest normalize-middleware-test
  (is (= [[1] [2] [3] [4 5]]
         (u/normalize-middleware
          [1 [2] 3 [4 5]])))
  (is (= [] (u/normalize-middleware []))))

(deftest chain-middleware-test
  (let [middleware1 (fn [handler]
                      (fn [request]
                        (-> request
                            (conj :pre-middleware1)
                            handler
                            (conj :post-middleware1))))
        middleware2 (fn [handler value]
                      (fn [request]
                        (-> request
                            (conj [:pre-middleware2 value])
                            handler
                            (conj [:post-middleware2 value]))))
        handler #(conj % :handler)

        thread-handler (-> handler
                           middleware1
                           (middleware2 :value))
        chain-handler (u/chain-middleware handler
                                          [middleware1
                                           [middleware2 :value]])

        ; A list, each middleware and the handler will conj onto it
        ; This allows us to check the order
        request []]
    (is (= (thread-handler request)
           (chain-handler request)
           [[:pre-middleware2 :value]
            :pre-middleware1
            :handler
            :post-middleware1
            [:post-middleware2 :value]]))))
