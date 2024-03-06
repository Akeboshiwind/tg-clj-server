(ns use-store
  (:require [tg-clj.core :as tg]
            [tg-clj-server.poll :as tg-poll]
            [tg-clj-server.utils :as u]
            [tg-clj-server.defaults :as defaults]

            [clojure.tools.logging :as log]))


;; >> Handlers

(defn reply-handler [{:keys [store] u :update}]
  (log/info "Reply handler")
  (let [my-count (or (:count store) 0)]
    (-> {:op :sendMessage
         :request {:text (str my-count " messages seen 👀")}}
        ; We have a handy util for replying to a message directly
        (u/reply-to u)
        (assoc :set-store (assoc store :count (inc my-count))))))



;; >> Routes

(def routes
  {"/reply" #'reply-handler})



;; >> App

(def app
  (defaults/make-app routes {; Use :store/path to specify a file to store the
                             ; app's state.
                             ; If unset then the store is kept in memory only
                             :store/path "/tmp/store.edn"}))

(defn main []
  (let [client (tg/make-client {:token "<your token>"})]
    (tg-poll/run-server client app)))

(comment
  ;; Run this to start
  (def f (future (main)))

  ;; Available commands:
  ;; - /reply - Replies with the count of calls so far and increases the count
  ;;
  ;; Try sending /reply a couple of times.
  ;; Then try stopping the repl and then try again.
  ;; You'll notice that the count is persisted.
  ;; Take a look at /tmp/store.edn

  ;; Run this to stop
  (future-cancel f))
