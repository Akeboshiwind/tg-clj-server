(ns no-defaults
  (:require [tg-clj.core :as tg]
            [tg-clj-server.poll :as tg-poll]
            [tg-clj-server.simple-router :as router]
            [tg-clj-server.middleware.me :as me]

            [clojure.tools.logging :as log]))


;; >> Handlers

(defn hello-handler [{u :update :keys [client me]}]
  (log/info "Hello handler")
  ; No `invoke` middleware so we call `invoke` ourselves
  (tg/invoke client
             {:op :sendMessage
              :request {:text (str "Hello, I am " (:username me))

                        :chat_id (get-in u [:message :chat :id])}}))



;; >> Routes

(def routes
  [["/hello" #'hello-handler]])



;; >> App

(def app
  (-> router/execute-route
      ; Put more middleware here
      me/middleware
      (router/select-route-middleware routes)))
      ; Put more middleware here

(defn main []
  (let [client (tg/make-client {:token "<your token>" :timeout 35000})]
    (tg-poll/run-server client app)))

(comment
  ;; Run this to start
  (def stop (main))

  ;; Available commands:
  ;; - /hello - Posts a message after the command is input

  ;; Run this to stop
  (stop))
