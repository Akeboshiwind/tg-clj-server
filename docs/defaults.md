# Defaults

As a convenience there is a function `tg-clj-server.defaults/make-app` which provides:
- [Routing](docs/intro.md#routing) (as explained in the intro)
- [Invoking the response](#invoke)
- [Persistent Storage](#simple-store)
- [The `:me` key](#me)

## Invoke

When you get an `update` you'll want to perform some action, usually make an api call to the Telegram Bot API.

There are two ways to do this:
- Call `tg-clj.core/invoke`, this is useful if you need to call multiple endpoints per update
- Return a map with an `:op` key in the same way you'd call `invoke`

This second option works like so:
```clojure
(defn handler [{u :update}]
  {:op :sendMessage
   :request {:text "Hi!"
             :chat_id (get-in u [:message :chat :id])}})
```

NOTE: The response is discarded. If you want to use the response call `tg-clj.core/invoke`.


## Simple Store

Sometimes it's useful to store state in between updates, and even between restarts.

An [`atom`](https://clojuredocs.org/clojure.core/atom) can work just fine as long as you don't mind:
- Not persisting to disk
- Global state

This middleware aims to solve both of these problems by:
- Providing a `:store` key in the `request` which contains the **current state** of the store
- Accepting a `:set-store` key in the `response` which resets the store to the given value

For example:
```clojure
(defn handler [{:keys [store}]
  (println "The store is: " store)
  ; WARNING: Ensure to *modify* the existing store
  ;          `:set-store` *overwrites the previous value!
  {:set-store (assoc store :my-value 1)})
```

By default the store is in-memory only, meaning that state is persisted between requests but not between restarts.

To persist state between restarts as an edn file use the `:store/path` key on `tg-clj-server.defaults/make-app`:
```clojure
(defaults/make-app routes {:store/path "/data/mystore.edn"})
```

Sometimes you want to be able to read from the atom elsewhere in the app.
This is useful when you 
You can provide your own atom by using the `:store/atom` key:
```clojure
; NOTE: Will be `reset!` by the middleware, so external changes & initial values aren't persisted
(def my-store (atom nil))

(defaults/make-app routes {:store/atom my-store})
```

For a complete example see `examples/use_store.clj`


## Me

On the first update received by your bot a request to [`:getMe`](https://core.telegram.org/bots/api#getme) is made and cached.

It's then added into the request under `:me`:

```clojure
(defn handler [{:keys [me]}]
  (println "Hi, I'm " (:username me)))
```

NOTE: This is used with command routes to allow calls like `/mycommand@mybot`
