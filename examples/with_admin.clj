(ns with-admin
  (:require [tg-clj.core :as tg]
            [tg-clj-server.poll :as tg-poll]
            [tg-clj-server.utils :as u]
            [tg-clj-server.middleware.global-admin :as admin]
            [tg-clj-server.defaults :as defaults]

            [clojure.tools.logging :as log]))


;; >> Utils

(defn seconds-since [millis]
  (-> (System/currentTimeMillis)
    (- millis)
    (/ 1000)
    float))



;; >> Handlers

(defn start-timer-handler [{:keys [store] u :update}]
  (log/info "Start timer handler")
  (if (nil? (:timer store))
    (let [now (System/currentTimeMillis)]
      (-> {:op :sendMessage
           :request {:text "Timer started"}}
        (u/reply-to u)
        (assoc :set-store (assoc store :timer now))))
    (-> {:op :sendMessage
         :request {:text "Timer already running"}}
      (u/reply-to u))))

(defn stop-timer-handler [{:keys [store] u :update}]
  (log/info "Stop timer handler")
  (if-let [timer (:timer store)]
    (-> {:op :sendMessage
         :request {:text (format "Timer stopped after %.2f seconds" (seconds-since timer))}}
      (u/reply-to u)
      (assoc :set-store (dissoc store :timer)))
    (-> {:op :sendMessage
         :request {:text "No timer is running."}}
      (u/reply-to u))))

(defn status-handler [{:keys [store] u :update}]
  (log/info "Status handler")
  (if-let [timer (:timer store)]
    (-> {:op :sendMessage
         :request {:text (str (format "Timer running for %.2f seconds" (seconds-since timer)))}}
      (u/reply-to u))
    (-> {:op :sendMessage
         :request {:text (str "All's well, but no timer is running.")}}
      (u/reply-to u))))



;; >> Routes

(def routes
  (merge
    {"/start" {:handler #'start-timer-handler
               :admin-only true}
     "/stop" {:handler #'stop-timer-handler
              :admin-only true}
     "/status" #'status-handler}
    admin/global-admin-routes))



;; >> App

(def app
  (defaults/make-app routes {:middleware [admin/global-admin-middleware]}))

(defn main []
  (let [client (tg/make-client {:token "<your token>"})]
    (tg-poll/run-server client app)))

(comment
  ;; Run this to start
  (def f (future (main)))

  ;; Available commands:
  ;; - /start - starts a timer
  ;; - /status - shows the time elapsed since the timer was started
  ;; - /stop - stops the timer
  ;; - /admin_add <user> - adds a user as an admin
  ;; - /admin_remove <user> - removes a user as an admin
  ;; - /admin_list - lists the admins
  ;;
  ;; Try /status, /start, /stop
  ;; Initially anyone should be able to interact with the timer.
  ;; Try /admin_add @your_username
  ;; Now Only you (and anyone else added as an admin) can call /start and /stop
  ;; Anyone can still call /status
  ;; Try /admin_list to see who's an admin
  ;; Try it in another group chat
  ;; Try /admin_remove @your_username to reset the admin list

  ;; Run this to stop
  (future-cancel f))
