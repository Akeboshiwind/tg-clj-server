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

(defn middleware
  "Adds a :store key to the request.
  
  If the response contains :set-store the store will be updated.

  Accepts a map of options:
  :path - The path to save the store to as edn.
          If not provided the store is in-memory only.
  :atom - An external atom to use as the store.

  One store is available per instance of this middleware, unless an :atom is
  provided.
  
  # Example handler
  (defn handler [{:keys [store]}]
    (if store
      {:op :sendMessage
       :request {:text (str store)}}
      {:op :sendMessage
       :request {:text \"Loading store\"}
       :set-store {:a 1}}))"
  ([handler]
   (middleware handler {}))
  ([handler {:keys [path] external-atom :atom}]
   (let [store (if external-atom
                 external-atom
                 (atom nil))]
     (assert (instance? clojure.lang.Atom store) ":atom must be an Atom")
     (reset! store (if path (load-edn path) nil))
     (fn [request]
       (let [response (-> request
                          (assoc :store @store)
                          (handler))]
         (when-let [updated-store (:set-store response)]
           (when path
             (save-edn updated-store path))
           (reset! store updated-store))
         response)))))
