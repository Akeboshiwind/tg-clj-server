# tg-clj-server

A more framework-y library for use with [tg-clj](https://github.com/Akeboshiwind/tg-clj) inspired by [ring](https://github.com/ring-clojure/ring) web-servers.

<p>
  <a href="#installation">Installation</a> |
  <a href="#getting-started">Getting Started</a> |
  <a href="examples/">Examples</a> |
  <a href="docs/intro.md">Introduction</a> |
  <a href="docs/defaults.md#simple-store">Persistent Storage</a> |
  <a href="https://github.com/Akeboshiwind/tg-clj">tg-clj</a>
</p>

> [!CAUTION]
> `tg-clj-server` and `tg-clj` are considered alpha!
>
> I'll put a warning in the [changelog](/CHANGELOG.md) when a breaking change happens.
> This warning will be removed once I consider the API stable.



## Why

When writing a bot that's more complicated than a few `:sendMessage`s, it's much
nicer (and more testable) to build it out of modular components:

```clojure
(defn hello-handler [{u :update}]
  {:op :sendMessage
   :request {:text "Hi! 🤖"
             :chat_id (get-in u [:message :chat :id])}})

(defn reply-handler [{u :update}]
  (-> {:op :sendMessage
       :request {:text "Message received 📨"}}
      (u/reply-to u)))

(def routes
  [["/hello" #'hello-handler]
   [(constantly true) #'reply-handler]])

;; The client timeout must be greater than the getUpdates timeout (default to 30s)
(let [client (tg/make-client {:token "<your token>" :timeout 35000})
      app (defaults/make-app routes)]
  (tg-poll/run-server client app))
```

For the full bot see `/examples/simple.clj`.



## Installation

Use as a dependency in `deps.edn` or `bb.edn`:

```clojure
io.github.akeboshiwind/tg-clj {:git/tag "v0.3.0" :git/sha "4852eb5"}
io.github.akeboshiwind/tg-clj-server {:git/tag "v0.3.1" :git/sha "8ec86b2"}
```



## Getting Started

To get things setup you'll need some routes:

```clojure
(require '[tg-clj-server.utils :as u])

(defn poll? [request]
  (get-in request [:update :message :poll]))

(def routes
  [; A route is made up of:
   ; - A predicate that takes a request
   ; - Route-data which must contain a `:handler` function
   [poll?
    {:handler (fn [{u :update}]
                ; Return a map with an :op key to automatically call `tg-clj/invoke`
                (-> {:op :sendMessage
                     :request {:text "Woah, that's a poll!"}}
                    ; We have a handy util for replying to a message directly
                    (u/reply-to u)))}]
   ; As syntactic sugar:
   ; - A handler function
   ["/command"
    (fn [{u :update}]
      (-> {:op :sendMessage
           :request {:text "Woah, that's a command!"}}
          (u/reply-to u)))]])
```

Then you'll need to create an `app` with those routes:

```clojure
(require '[tg-clj-server.defaults.poll :as defaults])

(def app
  (defaults/make-app routes {; You can supply additional middleware here
                             :middleware []
                             ; You can set options on provided middleware like so
                             ; (The store is in-memory only by default)
                             :store/path "/path/to/store.edn"}))
```

`defaults/make-app` provides some handy middleware like [simple-router](docs/intro.md#routing) and [invoke](docs/included-middleware.md#invoke). It's not required, see `examples/no_defaults.clj`

Then finally you can run the server with a client:

```clojure
(require '[tg-clj.core :as tg]
         '[tg-clj-server.poll :as tg-poll])

(let [client (tg/make-client {:token "<your token>" :timeout 35000})]
  ; Warning, This will block!
  (tg-poll/run-server client app))
```

And that's it! Try out your new bot 🤖

For some complete bots take a look at the `/examples` folder.

If you want to learn more start at the [intro](docs/intro.md).



## Dev

`clj -M:dev`

To run tests:

`clj -X:dev:test`



## Releasing

1. Tag the commit `v<version>`
2. `git push --tags`
3. Update the README.md with the new version and git hash
4. Update the CHANGELOG.md
