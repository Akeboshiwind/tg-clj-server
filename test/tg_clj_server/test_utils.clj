(ns tg-clj-server.test-utils
  (:require [clojure.test :refer [testing is]]))

(defn test-can-be-cancelled
  "Given f a function that waits at least 15ms, test that it can be cancelled."
  [f]
  (testing "Can be cancelled"
    (let [caught? (atom false)
          fut (future
                (try
                  (f)
                  (catch InterruptedException _
                    (reset! caught? true))))]
      (is (and (not (future-cancelled? fut))
               (not (future-done? fut))))
      (Thread/sleep 10)
      ; At this point fut should be sleeping
      ; Cancel it so an exception is thrown internally
      (is (future-cancel fut))
      (Thread/sleep 10)
      (is (future-cancelled? fut))
      ; If the future was cancelled properly then the InterruptedException
      ; should have been thrown
      (is (true? @caught?)))))
