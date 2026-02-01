(ns tg-clj-server.defaults.webhook
  (:refer-clojure :exclude [atom])
  (:require [tg-clj-server.simple-router :as router]
            [tg-clj-server.middleware.me :as me]
            [tg-clj-server.middleware.simple-store :as store]
            [tg-clj-server.utils :as u]))

(defn make-app
  "Given a set of routes, returns handler with the following middleware setup:

  - simple-router - Routes updates to the correct handler via `routes`
  - me            - Adds :me to the update
  - simple-store  - Adds :store to the update.
                    If :store/path is set persists the store to the given path
                    as edn.

  Unlike `tg-clj-server.defaults.poll/make-app`, this does not include the
  invoke middleware. Instead, the response is returned in the webhook body
  for Telegram to execute.

  Optionally takes a map with the following keys:
  :middleware - A vector of middleware to use.
                To provide arguments to the middleware, use a vector.
                E.g. {:middleware [middleware1
                                   [middleware2 arg1 arg2]]
  :store/path       - Set the path to persist the store to as edn.
  :store/atom       - An external atom to use at the store.
  :router/not-found - The handler to use when a route is not found."
  [routes & {:keys [middleware]
             :store/keys [path atom]
             :router/keys [not-found]}]
  (-> router/execute-route
      (u/chain-middleware middleware)
      ; Before the middleware so they have access to the store
      (store/middleware {:path path :atom atom})
      (router/select-route-middleware routes {:not-found not-found})
      ; Before the router, required for `u/command?` which may be used in routes
      me/middleware))
