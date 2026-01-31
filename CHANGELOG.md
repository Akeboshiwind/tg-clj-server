# Changelog

## Unreleased

**Breaking**: `tg-clj-server.defaults` moved to `tg-clj-server.defaults.poll`

```clojure
; Before
(require '[tg-clj-server.defaults :as defaults])

; After
(require '[tg-clj-server.defaults.poll :as defaults])
```

This prepares for webhook support which will have its own `tg-clj-server.defaults.webhook`.

## 0.3.1

- Bumped the `tg-clj` version to 0.3.0
- Bump the `:getUpdates` timeout to 30s
- Encourage setting a client timeout in `make-client`

Working on a library called `gully` which will probably replace the router.
It's still got a lot of time left in the hammock though I think.

## 0.3.0

Add lots more docs! Start with the [readme](./README.md) then head over to the [intro](./docs/intro.md).

Rename the middleware so they look nicer when imported:
```clojure
(require '[tg-clj-server.middleware.me :as me]

(-> handler
    me/middleware)
```

I expect this to be the last breaking change for a while :)

## 0.2.1

Ensure retries & `tg-clj-server.poll/run-server` are cancellable.

This means you can use a future to store your app and then cancel it with [`future-cancel`](https://clojuredocs.org/clojure.core/future-cancel).

```clojure
(defn handler [_]
  ...)

; Run the server
(def f (future (poll/run-server client handler)))

; Stop the server
(future-cancel f)
```

See the [examples](./examples/) for more.

## 0.2.0

Allow the store middleware to accept an external atom like so:
```clojure
(def routes
  {"/mycommand" (fn [{:keys [store]}]
                  {:set-store (assoc store :a 1)})})

(def my-store (atom nil))
(def app
  (defaults/make-app {:store/atom my-store}))

(app {...})
@my-store
```

This is useful when other parts of your application need to access the state.

Be warned that changes made outside of a handler will be **overwritten** when a handler changes the store.

## 0.1.0

The initial version with:
- Polling
- Routing
- Middleware
- Defaults
- etc
