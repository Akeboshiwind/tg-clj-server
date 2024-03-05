(ns simple
  (:require [tg-clj.core :as tg]
            [tg-clj-server.poll :as tg-poll]
            [tg-clj-server.utils :as u]
            [tg-clj-server.defaults :as defaults]

            [clojure.tools.logging :as log]))


;; >> Handlers

(defn hello-handler [{u :update}]
  (log/info "Hello handler")
  ;; See the Telegram API docs for the request format
  {:op :sendMessage
   :request {:text "Hi! 🤖"
             :chat_id (get-in u [:message :chat :id])}})

(def my-count (atom 0))

(defn reply-handler [{u :update}]
  (log/info "Reply handler")
  (swap! my-count inc)
  (-> {:op :sendMessage
       :request {:text (str @my-count " messages seen 👀")}}
      ; We have a handy util for replying to a message directly
      (u/reply-to u)))



;; >> Routes

(def routes
  ; Routes are processed in order from top to bottom
  [; Routes can start with a string command or a predicate
   ["/hello" #'hello-handler]

   ; To add a default route you can:
   ; - Use (constantly true) as the last route
   ; - Set :router/not-found on default/make-app
   [(constantly true) #'reply-handler]])



;; >> App

(def app
  (defaults/make-app routes))

(defn main []
  (let [client (tg/make-client {:token "<your token>"})]
    (tg-poll/run-server client app)))

(comment
  ;; Available commands:
  ;; - /hello - Posts a message after the command is input
  ;;
  ;; All other messages are replied to
  (main))
