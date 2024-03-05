(ns tg-clj-server.middleware.log
  (:require [clojure.tools.logging :as log]))

(defn log-middleware
  ([handler]
   (log-middleware handler nil))
  ([handler name]
   (let [name (when name
                (str "(" name ")"))]
     (fn [request]
       (log/info (str "Request" name ":") request)
       (let [response (handler request)]
         (log/info (str "Response" name ":") response)
         response)))))
