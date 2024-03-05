# tg-clj-server

<!-- TODO: Links everywhere! -->
A more framework-y library for use with `tg-clj` inspired by ring web-servers.

Allows you to setup routes with handler functions. A set of default middleware
will handle things like persistent storage.

There's also optionally a middleware to add a "global admin" to ensure handlers
are only called by a subset of users.

[Babashka](https://github.com/babashka/babashka) compatible!

## Installation

Use as a dependency in `deps.edn` or `bb.edn`:

```clojure
io.github.akeboshiwind/tg-clj {:git/tag "v0.2.1" :git/sha "1a913bc"}
io.github.akeboshiwind/tg-clj-server {:git/tag "v0.1.0" :git/sha "a32c34"}
```

## Usage

For some complete bots take a look at the `/examples` folder :)

### Basic Setup

To get things setup you'll need some routes:

```clojure
(require '[tg-clj-server.utils :as u])

(defn poll? [request]
  (get-in request [:update :message :poll]))

(def routes
  ; Routes are processed in order from top to bottom
  [; Routes can start with a string command
   ["/command" (fn [{u :update}]
                 ; Return a map with an :op key to automatically call ttg-clj/invoke
                 (-> {:op :sendMessage
                      :request {:text "Woah, that's a command!"}}
                     ; We have a handy util for replying to a message directly
                     (u/reply-to u)))]
   ; Or a predicate
   ; The handler can either be a function or a map (so you can provide some
   ; extra data, see the global-admin middleware)
   [poll? {:handler (fn [{u :update}]
                      (-> {:op :sendMessage
                           :request {:text "Woah, that's a poll!"}}
                          (u/reply-to u)))}]])
```

Then you'll need to create an `app` with those routes:

```clojure
(require '[tg-clj-server.defaults :as defaults])

(def app
  (defaults/make-app routes))
```

(If you want to do this manually take a look at defaults/make-app for the default setup)

Then finally you can run the server with a client:

```clojure
(require '[tg-clj.core :as tg]
         '[tg-clj-server.poll :as tg-poll])

(let [client (tg/make-client {:token "<your token>"})]
  ; Warning, This will block!
  (tg-poll/run-server client app))
```

And that's it! Try out your new bot 🤖


### Persistent storage middleware

Sometimes you want to store some data between requests, or even between restarts.
For that the `simple-store-middleware` is included by default.

With it you can store a clojure map in an internal store that's passed to each request.

```clojure
(defn count-handler [{:keys [store] u :update}]
  ; Use the `:store` key to get the current state of the store
  (let [new-count (inc (or (:count store) 0))]
    (-> {:op :sendMessage
         :request {:text (str "Command called " new-count " times so far")}}
        (u/reply-to u)
        ; Set the `:set-store` key to update the store
        ; WARNING: Make sure to modify the existing store,
        ;         `:set-store` *overwrites* the previous value!
        (assoc :set-store (assoc store :count new-count)))))

(def routes
  [["/command" #'count-handler]])

(def app
  (defaults/make-app routes {; Set `:store/path` to persist the store between restarts
                             ; Otherwise the store is kept in memory only
                             :store/path "/tmp/store.edn"}))

; Everything else is the same :)
```


### Invoke middleware

As you've seen in previous examples, you can reply with a map that contains the `:op` key.

This calls the `tg-clj.core/invoke` function on the response.
See that library and the [Telegram Bot API Docs](https://core.telegram.org/bots/api) for more.


### Me middleware

Sometimes you want to know who your bot is.

```clojure
(def routes
  [["/whoami" (fn [{:keys [me] u :update}]
                (-> {:op :sendMessage
                     :request {:text (pr-str me)}}
                    (u/reply-to u)))]])
```

This is used by the command detection to allow `/command@mybot` detection.


### Global admin middleware

Some commands you want to be only called by a select group of people.

This middleware works by keeping a list of global admins in the store.
These admins will have these permissions in all chats.

You can indicate a route is only for admins with the `:admin-only` key:

```clojure
(def routes
  [["/admin" {:handler (fn [{u :update}]
                         (-> {:op :sendMessage
                              :request {:text "Private"}}
                             (u/reply-to u)))
              :admin-only true}]
   ["/public" (fn [{u :update}]
                (-> {:op :sendMessage
                     :request {:text "Public"}}
                    (u/reply-to u)))]])
; Try /admin & /public
; Both should work for anyone

; Now try /admin_add @your_username
; Now only you can call /admin, but anyone can still call /public

; Use /admin_list to list the admins
; And /admin_remove <user> to remove the admin
```

Make sure to set `:store/path` if you want these admins persisted between restarts!

See the `with-admin` example for a more detailed look.

## Dev

`clj -M:dev`

To run tests:

`clj -X:dev:test`

## Releasing

1. Tag the commit `v<version>`
2. `git push --tags`
2. Update the README.md with the new version and git hash
