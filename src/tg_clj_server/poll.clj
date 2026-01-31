(ns tg-clj-server.poll
  (:refer-clojure :exclude [update])
  (:require [tg-clj.core :as tg]
            [clojure.tools.logging :as log]
            [tg-clj-server.utils :as u])
  (:import (java.lang InterruptedException)))

(defn handle-update! [{:keys [client handler updates]}]
  (when-let [update (first updates)]
    (try
      (log/info "Processing update" (:update_id update))
      (handler {:client client :update update})
      (catch Throwable t
        ;; Allow interrupts to bubble up
        (when (instance? InterruptedException t)
          (throw t))
        (log/error t "Failed to process update" (:update_id update))
         ;; Throw away errors, middleware can handle them
        nil))))

(defn next-updates [{:keys [updates] :as state}]
  (if-let [update (first updates)]
    (-> state
        (clojure.core/update :updates rest)
        (clojure.core/update :latest-offset #(if %
                                               (max % (:update_id update))
                                               (:update_id update))))
    state))

(defn updates-request [{:keys [latest-offset]
                        :fetch/keys [update-opts]}]
  {:op :getUpdates
   :request (merge {} update-opts
                   (when latest-offset
                     {:offset (inc latest-offset)}))})

(defn fetch-updates! [{:keys [client] :fetch/keys [wait-time]}
                      get-updates-request]
  (let [response
        (try
          (log/info "Fetching updates")
          (tg/invoke client get-updates-request)
          (catch Throwable t
            ;; Allow interrupts to bubble up
            (when (instance? InterruptedException t)
              (throw t))
            t))]
    (if-not (instance? Throwable response)
      (if (:ok response)
        (:result response)
        (throw (ex-info "Telegram API returned an error"
                        {:response response})))
      (do (log/error response "Failed to fetch updates")
          (let [wait-time (wait-time)]
            (log/info "Waiting" wait-time "ms before retrying")
            (u/sleep wait-time))
          []))))

(defn concat-updates [state updates]
  (clojure.core/update state :updates concat updates))

; This architecture is inspired by "think, do, assimilate" from the
; Clojure Design Podcast
(defn run-server
  "Given a client and handler, runs a 'server' which polls for updates and runs
  the handler on each update.

  Returns a stop function. Call it to stop the server.

  Discards errors from the handler, add middleware to handle them.
  Throws an exception if the Telegram API returns an error.
  Retries internal errors (like connection errors) infinitely.

  Optionally, you can pass in a map of options:
  :update-opts - A map of options to pass to the getUpdates request.
                 See the Telegram API for details.
  :wait-time   - A number in ms or a function called to get ms to wait on retry."
  ([client handler]
   (run-server client handler {}))
  ([client handler {:keys [update-opts wait-time]}]
   (let [f (future
             (loop [state {:client client
                           :handler handler
                           :updates []
                           :latest-offset nil
                           :fetch/update-opts (merge {:timeout 30} update-opts)
                           :fetch/wait-time (if (number? wait-time)
                                              (constantly wait-time)
                                              (or wait-time (constantly 5000)))}]
               (if (seq (:updates state))
                 (do
                   (handle-update! state)
                   (recur (next-updates state)))
                 (let [request (updates-request state)
                       updates (fetch-updates! state request)]
                   (recur (concat-updates state updates))))))]
     (log/info "Poll server started")
     (fn []
       (future-cancel f)
       (log/info "Poll server stopped")))))
