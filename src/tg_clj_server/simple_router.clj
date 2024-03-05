(ns tg-clj-server.simple-router
  (:refer-clojure :exclude [update])
  (:require [tg-clj-server.utils :as u]))

;; >> Utils

(defn normalize-route
  "Return a normalized predicate and route.
  For use in `normalize-routes`.

  # Predicate normalization
  The input predicate may either be a string or a predicate function takes an update.
  If it is a string then it must be a valid command, otherwise an error will be thrown.
  If it is a valid command it will be converted into a predicate.

  # Route normalization
  The input route may either be a handler function or a map with a :handler key.
  If it is a map without a :handler key an error will be thrown.
  If it is a function it will be wrapped in a map with a :handler key."
  [[pred route]]
  (let [pred (cond
               (string? pred)
               (if (u/valid-command? pred)
                 (partial u/command? pred)
                 (throw (IllegalArgumentException. (str "Invalid route command: " pred))))
               (fn? pred) pred
               :else (throw (IllegalArgumentException. "Invalid route predicate")))
        route (if (map? route)
                (if (contains? route :handler)
                  route
                  (throw (IllegalArgumentException. "Invalid route: missing :handler key")))
                {:handler route})]
    [pred route]))

(defn normalize-routes
  "Normalize the given routes into a list of [pred route] pairs.
  See `normalize-route` for details."
  [routes]
  (map normalize-route routes))

(defn select-route
  "Given a request and a list of routes, select the first route whose predicate returns true."
  [request routes]
  (some (fn [[pred route]]
          (when (pred request)
            route))
        routes))

(defn select-route-middleware
  "Given a handler and a list of routes, return a middleware that selects the
  first route whose predicate returns true.
  Predicates may be string commands as a convenience.
  Routes may be handler function as a convenience.

  Optionally takes a map of options:

  :not-found - A handler to call if no route is found. If not provided, the
               middleware will return.

  Use with `execute-route` to execute the selected route.

  # Example setup

  (defn poll? [{u :update}]
    (contains? u :poll))

  (def routes
    {; You can supply a command string (must be a valid command)
     ; And a handler function
     \"/myCommand\" #'my-command-handler
     ; You can also supply a map with the :handler key set
     \"/myOtherCommand\" {:handler #'my-other-command-handler
                        ; You can set other keys for use in other middleware
                        ; See the global-admin middleware for an example
                        :other-data \"foo\"}
     ; You can also use a predicate function to select whatever you like
     poll? {:handler #'poll-handler}})

  (def app
    (-> router/execute-route
        (router/select-route-middleware routes)
        invoke/invoke-middleware))"
  ([handler routes]
   (select-route-middleware handler routes {}))
  ([handler routes {:keys [not-found]}]
   (let [routes (normalize-routes routes)]
     (fn [request]
       (if-let [route (select-route request routes)]
         (-> request
             (assoc :route route)
             (handler))
         (when not-found
           (not-found request)))))))

(defn execute-route
  "A handler that executes the handler under [:route :handler].
  If no route is selected, nil is returned.
  
  See `select-route-middleware` for more information."
  [request]
  (when-let [handler (get-in request [:route :handler])]
    (handler request)))
