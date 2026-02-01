(ns switchable
  (:require [tg-clj.core :as tg]
            [tg-clj-server.poll :as poll]
            [tg-clj-server.webhook :as webhook]
            [tg-clj-server.utils :as u]
            [tg-clj-server.defaults.poll :as poll-defaults]
            [tg-clj-server.defaults.webhook :as webhook-defaults]

            [clojure.tools.logging :as log]))


;; >> Handlers

(defn hello-handler [{u :update}]
  (log/info "Hello handler")
  {:op :sendMessage
   :request {:text "Hi! 🤖"
             :chat_id (get-in u [:message :chat :id])}})

(defn reply-handler [{u :update}]
  (log/info "Reply handler")
  (-> {:op :sendMessage
       :request {:text "Message received 📨"}}
      (u/reply-to u)))



;; >> Routes

(def routes
  [["/hello" #'hello-handler]
   [(constantly true) #'reply-handler]])



;; >> App

(defn main []
  (let [token (System/getenv "BOT_TOKEN")
        webhook-url (System/getenv "WEBHOOK_URL")
        webhook-secret (System/getenv "WEBHOOK_SECRET")
        client (tg/make-client {:token token :timeout 35000})]
    (if webhook-url
      ;; Webhook mode
      (let [app (webhook-defaults/make-app routes)
            port (parse-long (or (System/getenv "PORT") "8080"))]
        (log/info "Starting webhook mode on port" port)
        (tg/invoke client {:op :setWebhook
                           :request {:url webhook-url
                                     :secret_token webhook-secret}})
        (webhook/run-server client app {:port port
                                        :secret-token webhook-secret}))
      ;; Polling mode
      (let [app (poll-defaults/make-app routes)]
        (log/info "Starting polling mode")
        (tg/invoke client {:op :deleteWebhook})
        (poll/run-server client app)))))

(comment
  ;; Polling mode (default):
  ;; BOT_TOKEN=<token> clj -M:dev -m switchable

  ;; Webhook mode (requires a public HTTPS URL):
  ;; BOT_TOKEN=<token> WEBHOOK_URL=https://... WEBHOOK_SECRET=... PORT=8080 clj -M:dev -m switchable
  ,)
