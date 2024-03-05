(ns tg-clj-server.middleware.simple-store-test
  (:require [clojure.test :refer [deftest testing is]]
            [tg-clj-server.middleware.simple-store :as store]
            [tg-clj-server.file-utils :refer [with-delete create-temp-file]]))

(deftest load-save
  (testing "e2e"
    (with-delete [tmp (create-temp-file "store" ".edn")]
      (let [path (str tmp)
            data {:a 1 :b 2}]
        (is (= nil (store/load-edn path))
            "Loading from non-existent file should return nil")
        (store/save-edn data path)
        (is (= data (store/load-edn path))
            "Loading from file should return the same data")))))

(deftest store-middleware
  (testing "The store is added to the request"
    (let [final-request (atom :unset)
          handler (store/simple-store-middleware
                   (fn [request]
                     (reset! final-request request)))]
      (handler {})
      (is (= {:store nil} @final-request))))

  (testing "A user can save data to the store"
    (let [data {:a 1}
          final-request (atom :unset)
          handler (store/simple-store-middleware
                   (fn [{:keys [op] :as request}]
                     (reset! final-request request)
                     (case op
                       :set {:set-store data}
                       :get {}
                       (throw (ex-info "Unexpected op" {:op op})))))]
      (testing "Store is initially nil"
        (handler {:op :get})
        (is (= {:op :get :store nil}
               @final-request)))

      ; Set the store
      (handler {:op :set})

      (testing "Store now contains data"
        (handler {:op :get})
        (is (= {:op :get :store data}
               @final-request)))

      (testing "If :set-store isn't used the store is not updated"
        (handler {:op :get})
        (is (= {:op :get :store data}
               @final-request)))))

  (testing "In-memory stores lose data between restarts"
    (let [; Create first middleware instance
          last-store (atom :unset)
          handler (store/simple-store-middleware
                   (fn [{:keys [store]}]
                     (reset! last-store store)
                     {:set-store {:a 1}}))]
      ; Set the store
      (handler {})
      (is (= nil @last-store))
      (handler {})
      (is (= {:a 1} @last-store)))
    (let [; Create a new middleware instance
          last-store (atom :unset)
          handler (store/simple-store-middleware
                   (fn [{:keys [store]}]
                     (reset! last-store store)))]
      ; Get the store
      (handler {})
      (is (= nil @last-store))))

  (testing "File stores persist data between restarts"
    (with-delete [tmp (create-temp-file "store" ".edn")]
      (let [path (str tmp)
            data {:a 1}]
        (let [; Create first middleware instance
              handler (-> (fn [_request]
                            {:set-store data})
                          (store/simple-store-middleware {:path path}))]
          ; Set the store
          (handler {}))
        (let [; Create a new middleware instance
              final-request (atom :unset)
              handler (-> (fn [request]
                            (reset! final-request request))
                          (store/simple-store-middleware {:path path}))]
          ; Get the store
          (handler {})
          (is (= {:store data} @final-request))))))

  (testing "The user can provide thir own atom"
    (let [external-atom (atom nil)
          data {:a 1}
          last-store (atom :unset)
          handler (-> (fn [{:keys [store]}]
                        (reset! last-store store)
                        {:set-store data})
                      (store/simple-store-middleware
                       {:atom external-atom}))]
      ; Set the store
      (handler {})
      (is (= data @external-atom))
      ; Get the store via middleware
      (handler {})
      (is (= data @last-store)))))
