(ns tg-clj-server.middleware.me
  (:require [tg-clj.core :as tg]))

(defn- get-me [client cache]
  (if-let [cached-me @cache]
    cached-me
    (let [new-me (:result (tg/invoke client {:op :getMe}))]
      ;; Possible a race condition here
      ;; We don't care because this middleware assumes that each request will
      ;; have the same client, meaning that the user object will be the same.
      (reset! cache new-me)
      new-me)))

(defn me-middleware
  "Adds the :me key to the request map, containing the user object for the client.

  Assumes the client is the same for each request."
  [handler]
  (let [cache (atom nil)]
    (fn [{:keys [client] :as request}]
      (-> request
          (assoc :me (get-me client cache))
          (handler)))))
