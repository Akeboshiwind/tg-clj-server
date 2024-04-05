# Included Middleware

Some additional middleware provided are:
- [`log-middleware`](#log)
- [`global-admin-middleware`](#global-admin)

And part of `tg-clj-server.defaults` we have:
- [`invoke-middleware`](#invoke)
- [`simple-store-middleware`](#simple-store)
- [`me-middleware`](#me)

## Log

Logs the request & response for every request, useful for debugging.

Optionally takes a `name` which is prepended to the log line.

```clojure
(-> handler
    log/log-middleware)

; In a `defaults` app:
(defaults/make-app routes {:middleware [log/log-middleware]})

; Optionally supply a name
(-> handler
    (log/log-middleware "pre-middleware1"))

; In a `defaults` app:
(defaults/make-app routes {:middleware [[log/log-middleware "pre-middleware1"]]})

; Log outputs look something like:
; INFO: Request(pre-middleware1): {<the request>}
; ... some other log lines
; INFO: Response(pre-middleware1): {<the response>}
```



## Global Admin

There are a few ways to control access to routes/commands on a bot:
- Manually checking the `:status` of a [user](https://core.telegram.org/bots/api#getchatmember) (be careful of users messaging the bot in other chats)
- Set the `:scope` of commands when [setting them up](https://core.telegram.org/bots/api#setmycommands) (same warning as above)
- Have a pool of users who are admins for the bot regardless of chat
- Something else

This middleware helps with the third option by having a global pool of admins.

By including a `:admin-only true` in the route data for a route you can ensure that only admins in the global list will be allowed to use that route:

```clojure
(defn public-handler [_]
  ...)

(defn secret-handler [_]
  ...)

(def routes
  {"/public" #'public-handler
   "/secret" {:handler #'secret-handler
              :admin-only true}})

(defaults/make-app routes {:middleware [admin/global-admin-middleware]})
```

The admin list is split into two parts: `base admins` and `stored admins`.

`Base admins` are provided statically on as a set on the middleware and can only be removed by changing this list and restarting the app:
```clojure
(defaults/make-app routes {:middleware [[admin/global-admin-middleware #{"me" 1234}]]})
```

And `stored admins` are stored at `[:store :admins]`. Use the [`simple store`](#simple-store) to persist this list of admins.

A set of routes is provided at `tg-clj-server.middleware.global-admin-middleware/routes`:

```clojure
(def routes
  (merge {...} admin/global-admin-routes))
```

These commands are:
- `/admin_list`   - List the admins (not the `base admins`)
- `/admin_add`    - Add an admin to the `stored admins`
- `/admin_remove` - Remove and admin from the `stored admins`

NOTE: These routes make use of the `invoke` middleware to reply to users, but other than the `/admin_list` command this isn't required

See `/examples/with_admin.clj` for a complete example.



## Invoke

See the [defaults docs](docs/defaults.md#invoke) for a description.

Usage of the raw middleware:

```clojure
(defn handler [_]
  {:op :sendMessage
   :request {:chat_id 1234
             :text "hi"}})

(-> handler
    invoke/invoke-middleware)

; Optionally supply a retry function
; Retries on client errors, not telegram api errors
; Defaults to retrying 3 times every 100ms
(-> handler
    (invoke/invoke-middleware {:retry #(u/retry % {:max-retries 4})}))
```



## Simple Store

See the [defaults docs](docs/defaults.md#simple-store) for a description.

Usage of the raw middleware:

```clojure
(defn handler [{:keys [store]}]
  (println store)
  {:set-store (assoc store :a 1)})

(-> handler
    store/simple-store-middleware)

; Optionally supply a path
(-> handler
    (store/simple-store-middleware {:path "/tmp/file.edn"}))

; Optionally supply an atom
(def my-store (atom nil))
(def app
  (-> handler
      (store/simple-store-middleware {:atom my-store})))

(app {})
@my-store ; => {:a 1}
```



## Me

See the [defaults docs](docs/defaults.md#me) for a description.

Usage of the raw middleware:

```clojure
(defn handler [{:keys [me]}]
  ...)

(-> handler
    me/me-middleware)
```
