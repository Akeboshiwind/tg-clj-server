(ns tg-clj-server.poll-test
  (:require [clojure.test :refer [deftest testing is]]
            [tg-clj.core :as tg]
            [tg-clj-server.poll :as poll]
            [tg-clj-server.utils :as u]
            [tg-clj-server.test-utils :as tu])
  (:import (clojure.lang ExceptionInfo)))

(deftest handle-update!-test
  (testing "Calls :handler with :client and the first update"
    (let [handler-arg (atom :unset)
          handler #(reset! handler-arg %)]
      (poll/handle-update! {:client :client
                            :handler handler
                            :updates [1 2 3]})
      (is (= {:client :client :update 1}
             @handler-arg))))

  (testing "Doesn't call the :handler when there is no next update"
    (let [calls (atom 0)
          handler (fn [_] (swap! calls inc))]
      (poll/handle-update! {:client :client
                            :handler handler
                            :updates []})
      (is (= 0 @calls))))

  (testing "Swallows exceptions"
    (let [handler (fn [_] (throw (Exception. "My Error")))]
      (is (nil? (poll/handle-update! {:client :client
                                      :handler handler
                                      :updates [1 2 3]})))))

  (let [wait-handler (fn [_] (Thread/sleep 1000))]
    (tu/test-can-be-cancelled
     #(poll/handle-update! {:client :client
                            :handler wait-handler
                            :updates [1 2 3]}))))

(deftest next-updates-test
  (testing "Advances to the next update"
    (let [state {:updates [{:update_id 1} {:update_id 2}]}]
      (is (= [{:update_id 2}]
             (:updates (poll/next-updates state))))))

  (testing "Sets the latest-offset if nil"
    (let [state {:latest-offset nil
                 :updates [{:update_id 1}]}]
      (is (= 1 (:latest-offset (poll/next-updates state))))))

  (testing "Uses the max of the latest-offset and the update_id"
    (let [state {:latest-offset 2
                 :updates [{:update_id 1}]}]
      (is (= 2 (:latest-offset (poll/next-updates state)))))
    (let [state {:latest-offset 1
                 :updates [{:update_id 2}]}]
      (is (= 2 (:latest-offset (poll/next-updates state))))))

  (testing "Doesn't advance either latest-offset or :updates if no updates available"
    (let [state {:latest-offset 1
                 :updates []}]
      (is (= {:latest-offset 1
              :updates []}
             (poll/next-updates state))))))

(deftest updates-request-test
  (is (= {:op :getUpdates
          :request {}}
         (poll/updates-request {})))
  (is (= {:op :getUpdates
          :request {:a 1
                    :offset 2}}
         (poll/updates-request {:latest-offset 1
                                :fetch/update-opts {:a 1}}))))

(deftest fetch-updates!-test
  (testing "A successful response returns the updates"
    (with-redefs [tg/invoke (fn [_client _request]
                              {:ok true
                               :result [1 2 3]})]
      (is (= [1 2 3]
             (poll/fetch-updates! {:client :client
                                   :fetch/wait-time (constantly 0)}
                                  :myRequest)))))

  (testing "Errors from the Telegram API throw an error"
    (let [response {:ok false :data "Some error"}]
      (with-redefs [tg/invoke (fn [_client _request]
                                response)]
        (try
          (poll/fetch-updates! {:client :client
                                :fetch/wait-time (constantly 0)}
                               :myRequest)
          (is false "Should error")
          (catch ExceptionInfo e
            (is (= "Telegram API returned an error"
                   (ex-message e)))
            (is (= {:response response}
                   (ex-data e))))))))

  (testing "Thrown exceptions"
    (let [sleep-called? (atom false)]
      (with-redefs [tg/invoke (fn [_client _request]
                                (throw (Exception. "My Error")))
                    u/sleep (fn [_] (reset! sleep-called? true))]
        (testing "Returns an empty list"
          (is (= [] (poll/fetch-updates!
                     {:client :client
                      :fetch/wait-time (constantly 0)}
                     :myRequest))))
        (testing "Sleeps for some time"
          (is @sleep-called?)))))

  (with-redefs [tg/invoke (fn [_client _request]
                            (Thread/sleep 1000))]
    (tu/test-can-be-cancelled
     #(poll/fetch-updates! {:client :client
                            :fetch/wait-time (constantly 0)}
                           :myRequest))))

(deftest concat-updates-test
  (is (= {:updates []}
         (poll/concat-updates {} [])))
  (is (= {:updates [1 2 3 4]}
         (poll/concat-updates {:updates [1 2]} [3 4]))))
