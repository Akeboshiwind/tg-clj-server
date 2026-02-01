(ns webhook
  (:require [tg-clj.core :as tg]
            [tg-clj-server.webhook :as webhook]
            [tg-clj-server.utils :as u]
            [tg-clj-server.defaults.webhook :as defaults]

            [clojure.tools.logging :as log]))


;; >> Handlers

(defn hello-handler [{u :update}]
  (log/info "Hello handler")
  {:op :sendMessage
   :request {:text "Hi from webhook! 🤖"
             :chat_id (get-in u [:message :chat :id])}})

(defn reply-handler [{u :update}]
  (log/info "Reply handler")
  (-> {:op :sendMessage
       :request {:text "Message received via webhook 📨"}}
      (u/reply-to u)))



;; >> Routes

(def routes
  [["/hello" #'hello-handler]
   [(constantly true) #'reply-handler]])


;; >> App

(def app
  (defaults/make-app routes))

(defn main [{:keys [token port secret-token webhook-url]}]
  (let [client (tg/make-client {:token token})]
    ;; Register webhook with Telegram
    (log/info "Setting webhook to" webhook-url)
    (tg/invoke client {:op :setWebhook
                       :request {:url webhook-url
                                 :secret_token secret-token}})
    ;; Start server
    (webhook/run-server client app {:port port
                                    :secret-token secret-token})))

(defn cleanup [{:keys [token]}]
  (let [client (tg/make-client {:token token})]
    (log/info "Deleting webhook")
    (tg/invoke client {:op :deleteWebhook})))

(comment
  ;; Requires a public HTTPS URL pointing to port 8080

  ;; 1. Set your config:
  (def config {:token "<your bot token>"
               :port 8080
               :secret-token "my-secret-token"
               :webhook-url "https://<your public url>"})

  ;; 2. Start the server:
  (def stop (main config))

  ;; 3. Send a message to your bot!
  ;;    - /hello - replies with a greeting
  ;;    - anything else - echoes back

  ;; 4. Stop the server and cleanup:
  (stop)
  (cleanup config))
