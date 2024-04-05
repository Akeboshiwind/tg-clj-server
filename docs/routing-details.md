# How Routing Works

Routing implemented as two components:
- [The route selector](#route-selector) - implemented as a middleware
- [The route executor](#route-executor) - implemented as a handler

## Route Selector

The `select-route-middleware` function is ordinary middleware that accepts a list of routes:

```clojure
(def routes
  ...)

(-> handler
    (router/select-route-middleware routes))

; Optionally provide a default route:
(def not-found-handler [_]
  ...)

(-> handler
    (router/select-route-middleware routes {:not-found not-found-handler}))
```

The middleware selects a route then attaches the `route data` to the `request` like so:

```clojure
(def routes
  ...)

(def app
  (-> handler
      (router/select-route-middleware routes)))

(app {<my request>})
; => {:route <selected route> ...}
```

This allows other middleware to intercept the selected route and modify it. See the [global admin middleware](docs/included-middleware.md#global-admin) for an example of this.

Note that the middleware does not execute the `:handler` in the `route data`, that's what we need the second component for:



## Route Executor

This function is a replacement handler function, seeing as you'll be putting your application handlers in the router.

It works very simply by just extracting the handler from `[:route :handler]` and then executing it on the request.
