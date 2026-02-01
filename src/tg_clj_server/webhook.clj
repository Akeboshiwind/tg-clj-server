(ns tg-clj-server.webhook
  (:require [org.httpkit.server :as http-kit]
            [clojure.tools.logging :as log]
            [cheshire.core :as json])
  (:import (java.lang InterruptedException)))

(defn- parse-body [request]
  (-> request :body slurp (json/decode true)))

(defn- validate-request
  "Returns error response map if invalid, nil if valid"
  [{:keys [secret-token]} {:keys [request-method headers]}]
  (cond
    (not= :post request-method)
    {:status 405 :body "Method Not Allowed"}

    (and secret-token
         (not= secret-token (get headers "x-telegram-bot-api-secret-token")))
    {:status 401 :body "Unauthorized"}))

(defn- ->telegram-method [{:keys [op request]}]
  (assoc request :method (name op)))

(defn- make-ring-handler [client handler validate]
  (fn [request]
    (if-let [error-response (validate request)]
      error-response
      (try
        (let [update (parse-body request)
              _ (log/info "Processing update" (:update_id update))
              response (handler {:client client :update update})]
          (if (:op response)
            {:status 200
             :headers {"Content-Type" "application/json"}
             :body (json/encode (->telegram-method response))}
            {:status 200 :body "OK"}))
        (catch Throwable t
          ;; Allow interrupts to bubble up
          (when (instance? InterruptedException t)
            (throw t))
          ;; Throw away errors, middleware can handle them
          (log/error t "Webhook handler error")
          {:status 200 :body "OK"})))))

(defn run-server
  "Given a client and handler, runs a webhook server which receives updates
  from Telegram and runs the handler on each update.

  Returns a stop function. Call it to stop the server.

  Discards errors from the handler, add middleware to handle them.
  Always returns 200 OK to Telegram (to prevent retries).

  If the handler returns a response with :op, it is returned in the webhook
  body for Telegram to execute.

  Optionally, you can pass in a map of options:
  :port         - The port to listen on (default 8080)
  :secret-token - The secret token to validate requests (recommended)"
  ([client handler]
   (run-server client handler {}))
  ([client handler {:keys [port secret-token]
                    :or {port 8080}}]
   (let [validate (partial validate-request {:secret-token secret-token})
         ring-handler (make-ring-handler client handler validate)
         server (http-kit/run-server ring-handler {:port port})]
     (log/info "Webhook server started on port" port)
     (fn []
       (server :timeout 5000)
       (log/info "Webhook server stopped")))))
