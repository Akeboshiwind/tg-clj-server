(ns tg-clj-server.middleware.invoke
  (:require [tg-clj.core :as tg]
            [tg-clj-server.utils :as u]))

(defn middleware
  "When the response is a map with `:op` it will be invoked using tg-clj/invoke.
  
  Will be retried using function supplied by :retry (default to 3 times with 5s between)."
  ([handler]
   (middleware handler {}))
  ([handler {:keys [retry]
             :or {retry u/retry}}]
   (fn [{:keys [client] :as request}]
     (let [{:keys [op] :as response} (handler request)]
       (if op
         (let [{:keys [ok] :as ret}
               (retry #(tg/invoke client response))]
           (if ok
             ret
             (throw (ex-info "Failure from Telegram API"
                             {:response response
                              :api-response ret}))))
         response)))))
