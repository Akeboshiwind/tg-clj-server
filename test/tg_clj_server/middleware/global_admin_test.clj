(ns tg-clj-server.middleware.global-admin-test
  (:require [clojure.test :refer [deftest testing is]]
            [tg-clj-server.middleware.global-admin :as admin]))

(deftest add-admin-handler-test
  (testing "Updates the store with the new admin"
    (testing "Empty store"
      (let [request
            {:update {:message {:entities [{:type "text_mention"
                                            :user {:id 1234}}]}}}
            response (admin/add-admin-handler request)]
        (is (contains? response :set-store))
        (is (= {:admins #{1234}}
               (:set-store response)))))
    (testing "Non-empty store"
      (let [request
            {:update {:message {:entities [{:type "text_mention"
                                            :user {:id 1234}}]}}
             :store {:admins #{5678}
                     :other :stuff}}
            response (admin/add-admin-handler request)]
        (is (contains? response :set-store))
        (is (= {:admins #{1234 5678}
                :other :stuff}
               (:set-store response)))))))

(deftest remove-admin-handler-test
  (testing "If there are no admins the store is unchanged"
    (let [request {:update {}
                   :store {:admins #{}}}
          response (admin/remove-admin-handler request)]
      (is (not (contains? response :set-store)))))

  (testing "Removes the admin from the store"
    (let [request
          {:update {:message {:entities [{:type "text_mention"
                                          :user {:id 1234}}]}}
           :store {:admins #{1234 5678}
                   :other :stuff}}
          response (admin/remove-admin-handler request)]
      (is (contains? response :set-store))
      (is (= {:admins #{5678}
              :other :stuff}
             (:set-store response))))))

(deftest list-admin-handler-test
  (testing "Message contains list of admins"
    (let [admins #{1234 5678 "user1" "user2"}
          request {:store {:admins admins}}
          response (admin/list-admin-handler request)
          text (get-in response [:request :text])]
      (is (every? #(re-find (re-pattern (str %)) text)
                  admins)))))

(deftest routes-test
  (testing "All admin-only"
    (is (->> admin/routes
             vals
             (every? :admin-only)))))

(deftest authorized?-test
  (testing "Not admin-only"
    (is (true? (admin/authorized? {:store {:admins #{1234}}
                                   :route {:admin-only false}
                                   :update {:message {:from {:id 1234}}}}
                                  #{5678}))))

  (testing "No admins in store & no base-admins"
    (is (true? (admin/authorized? {:route {:admin-only true}} #{}))))

  (testing "Username in store"
    (is (true? (admin/authorized? {:store {:admins #{"user"}}
                                   :route {:admin-only true}
                                   :update {:message {:from {:username "user"}}}}
                                  #{}))))

  (testing "Username in base-admins"
    (is (true? (admin/authorized? {:route {:admin-only true}
                                   :update {:message {:from {:username "user"}}}}
                                  #{"user"}))))

  (testing "Username in neither"
    (is (false? (admin/authorized? {:store {:admins #{"user1"}}
                                    :route {:admin-only true}
                                    :update {:message {:from {:username "user"}}}}
                                   #{"user2"})))))
