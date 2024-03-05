(ns tg-clj-server.middleware.simple-store
  (:require [clojure.edn :as edn])
  (:import [java.io FileNotFoundException]))

(defn load-edn [path]
  (try
    (-> path slurp edn/read-string)
    (catch FileNotFoundException _
      nil)))

(defn save-edn [data path]
  (spit path (pr-str data)))

(defn simple-store-middleware
  "Adds a :store key to the request.
  
  If the response contains :set-store the store will be updated.

  If a `path` is provided to the middleware then the store is saved on update
  as edn to a file at `path`.
  Otherwise the store is in-memory only and will be lost on server restart.

  One store is available per instance of this middleware.
  
  # Example handler
  (defn handler [{:keys [store]}]
    (if store
      {:op :sendMessage
       :request {:text (str store)}}
      {:op :sendMessage
       :request {:text \"Loading store\"}
       :set-store {:a 1}}))"
  ([handler]
   (simple-store-middleware handler nil))
  ([handler path]
   (let [store (atom (if path
                       (load-edn path)
                       nil))]
     (fn [request]
       (let [response (-> request
                          (assoc :store @store)
                          (handler))]
         (when-let [updated-store (:set-store response)]
           (when path
             (save-edn updated-store path))
           (reset! store updated-store))
         response)))))
