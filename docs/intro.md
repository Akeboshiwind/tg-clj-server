# Introduction

`tg-clj-server` provides three main components to compose a bot:
- [Polling](#polling)
- [Routing](#routing)
- [Middleware](#middleware)

Additionally `tg-clj-server.defaults/make-app` provides:
- [Invoking the response](./defaults.md#invoke)
- [Persistent Storage](./defaults.md#simple-store)
- [The `:me` key](./defaults.md#me)

To build an app without using `make-app` see `examples/no_defaults.clj`.

## Polling

There are two ways to write a telegram bot: [webhooks](https://core.telegram.org/bots/api#setwebhook) (which we don't currently support) and polling.

Polling simply calls the [`:getUpdates`](https://core.telegram.org/bots/api#getupdates) endpoint in a loop with a long timeout, then handles the resulting list of `updates` however is needed.

We provide a function `tg-clj-server.poll/run-server` which does this, calling the given `handler` with a `request`.

A `request` is a map of the `:update` and the `:client`. ([middleware](#middleware) can add other keys to this map)

```clojure
(defn handler [{u :update :keys [client]}
  ...)

(poll/run-server client handler)

; Optionally provide an opts map
; See the docstring for more
(poll/run-server client handler 
                 {:update-opts {:allowed_updates ["message"]}})
```

If the handler throws an exception the server will ignore it and move on to the next `update`, if you want to handle them you will need to include some middleware to do so.

If the Telegram Bot API returns an error when fetching updates, the server will throw an exception and halt.

Otherwise this function does nothing more, you'll need to add [routing](#routing) and [middleware](#middleware) to do more interesting things :D.


## Routing

Inspired by [ring](https://github.com/ring-clojure/ring) web-servers `tg-clj-server` provides a way of specifying "routes".

An individual route is a pair of:
- A predicate which acts on a `request`
- A map of "route data" which must contain a `:handler` function

Which looks something like this:
```clojure
(defn poll? [_]
  ...)

(defn poll-handler [_]
  ...)

[poll? {:handler #'poll-handler}]
```

A set of routes is simply a list of pairs:
```clojure
(def routes
  [[poll? {:handler #'poll-handler}]
   [(constantly true) {:handler #'default-handler}]])
```

The **first** route who's predicate matches will have it's handler called with the `request`.
(This is a slight simplification for more details see [here](./routing-details.md))

As syntactic sugar:
- The list of routes can be a map
  - NOTE: This will mean you can't ensure the order of routes
- The predicate can be a valid command string beginning with `/`
  - This is replaced with a call to `tg-clj-server.utils/command?`
- The "route data" can be a handler function

For example:
```clojure
(def routes
  {poll? {:handler #'poll-handler
          :other-data :goes-here}
   "/mycommand" #'command-handler})
```


## Middleware

Middleware is a feature taken wholesale from [ring](https://github.com/ring-clojure/ring), so if you're familiar with it there then it works much the same here.

Middleware works by "wrapping" the handler function in another function.
You can use this to run code before or after a handler is run.

For example the [`:me` middleware](./included-middleware.md#me) works something like this:
```clojure
(defn get-me [client]
  ...)

(defn me-middleware [handler]
  (fn [request]
    ; Add :me to the request *before* the handler get's it
    (handler (assoc request :me (get-me)))))
```

And the [invoke middleware](./included-middleware.md#invoke) works something like this:
```clojure
(defn do-invoke [client request]
  ...)

(defn invoke-middleware [handler]
  (fn [request]
    (let [response (handler request)]
      ; Use the response from the handler
      (if (:op response)
        (do-invoke (:client request) response)
        response))))
```

By using middleware you can extract parts of your system into more testable components. They can be composed together like so:

```clojure
(def app
  (default/make-app routes
                    {:middleware [my-middleware-1
                                  [my-middleware-2 arg1 arg2]]}))

; Or without using defaults:
(-> handler
    my-middleware-1
    (my-middleware-2 arg1 arg2))
```

For a list of included middleware see [here](./included-middleware.md).

See more about the default provided middleware [here](./defaults.md).
