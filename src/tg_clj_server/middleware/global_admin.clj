(ns tg-clj-server.middleware.global-admin
  (:refer-clojure :exclude [update])
  (:require [tg-clj-server.utils :as u]
            [clojure.string :as str]))

(defn add-admin-handler [{:keys [update store]}]
  (let [admins (or (:admins store) #{})
        new-admins (into admins (u/all-mentions update))]
    (-> {:op :sendMessage
         :request {:text (str "Admin added, current admins:\n"
                              (str/join "\n" new-admins))}}
        (u/reply-to update)
        (assoc :set-store (assoc store :admins new-admins)))))

(defn remove-admin-handler [{:keys [update store]}]
  (let [admins (:admins store)]
    (if (empty? admins)
      (-> {:op :sendMessage
           :request {:text "No admins to remove"}}
          (u/reply-to update))
      (let [new-admins (reduce disj admins (u/all-mentions update))]
        (-> {:op :sendMessage
             :request {:text
                       (if (empty? new-admins)
                         "Admin removed, no admins left"
                         (str "Admin removed, current admins:\n"
                              (str/join "\n" new-admins)))}}
            (u/reply-to update)
            (assoc :set-store (assoc store :admins new-admins)))))))

(defn list-admin-handler [{:keys [store update]}]
  (let [admins (:admins store)]
    (-> {:op :sendMessage
         :request {:text (if (seq admins)
                           (str/join "\n" (:admins store))
                           "No admins (yet)")}}
        (u/reply-to update))))

(def global-admin-routes
  {"/admin_add" {:handler #'add-admin-handler
                 :admin-only true}
   "/admin_remove" {:handler #'remove-admin-handler
                    :admin-only true}
   "/admin_list" {:handler #'list-admin-handler
                  :admin-only true}})

(defn authorized? [request base-admins]
  (if (get-in request [:route :admin-only])
    (let [store-admins (get-in request [:store :admins])
          admins (into (or base-admins #{}) store-admins)]
      (if (empty? admins)
        true
        (let [username (get-in request [:update :message :from :username])
              user-id (get-in request [:update :message :from :id])]
          (or (contains? admins (str/lower-case username))
              (contains? admins user-id)))))
    true))

(defn global-admin-middleware
  "Adds global admin functionalilty to the bot.

  To tell this middleware that a route should be admin-only set the `:admin-only`
  key to true on the route. See the example below for more.
  
  You can provide a set of admins in two ways:
  1. As a parameter to the middleware, this list of admins cannot be modified at runtime.
  2. Under [:store :admins] in the request, a set of commands under
     `global-admin-routes` is proivded to modify the list of admins.

  These lists are combined when checking for admin status.
  If no admins are provided, all users are considered admins.

  These lists of admins are global for the bot instance, meaning that in
  different chats the same user will be considered an admin.

  # Usage Notes

  The `global-admin-routes` require the simple-file-store middleware, and for
  the routes to reply to messages they require the invoke middleware.

  If using the simple-file-store middleware, make sure it runs *before* this
  middleware so that [:store :admin] is available.

  Ensure that the router select-route middleware runs *before* this so that the
  route data (:admin-only) is available.
  
  # Example setup:

  (def routes
    (merge
      {\"/public\" #'public-handler
       \"/secret\" {:handler #'secret-handler :admin-only true}}
      admin/global-admin-routes))

  (def app
    (-> router/execute-route
        (admin/global-admin-middleware #{\"me\" 1234})
        (store/simple-store-middleware {:path \"/tmp/store.edn\"})
        invoke/invoke-middleware
        (router/select-route-middleware routes)))"
  ([handler]
   (global-admin-middleware handler nil))
  ([handler base-admins]
   (fn [{:keys [update] :as request}]
     (if (authorized? request base-admins)
       (handler request)
       (-> {:op :sendMessage
            :request {:text "You are not authorized to do that."}}
           (u/reply-to update))))))
