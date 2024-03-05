(ns tg-clj-server.utils
  (:refer-clojure :exclude [update])
  (:require [clojure.tools.logging :as log]
            [clojure.string :as str]))

(defn valid-command?
  "Tests if the given command is valid as per Telegram's standards."
  [cmd]
  (re-matches #"/[a-z0-9_]+" cmd))

(defn command?
  "Tests if the request contains the given command.

  To use /command@mybotname, make sure to use the :me middleware."
  [cmd request]
  (assert (valid-command? cmd) (str "Invalid command: " cmd))
  (when-let [text (get-in request [:update :message :text])]
    (let [username (get-in request [:me :username])
          pattern (if username
                    (str "(^| )" cmd "($|@" username "| )")
                    (str "(^| )" cmd "($| )"))]
      (re-find (re-pattern pattern) text))))

(defn reply-to
  "Updates the given response to reply to the message in the given update."
  [response update]
  (if-let [message (:message update)]
    (let [chat-id (get-in message [:chat :id])
          message-id (:message_id message)]
      (-> response
          (assoc-in [:request :chat_id] chat-id)
          (assoc-in [:request :reply_parameters :message_id] message-id)))
    response))

(defn all-mentions
  "Given an update, return a list of all the usernames & user ids of those mentioned in it."
  [update]
  (when-let [entities (get-in update [:message :entities])]
    (set
     (concat
       ; Get the usernames from `mention` entities
      (let [text (get-in update [:message :text])]
        (->> entities
             (filter #(= (:type %) "mention"))
             (map #(subs text (:offset %) (+ (:offset %) (:length %))))
              ; Remove the leading @
             (map #(subs % 1 (count %)))
             (map str/lower-case)))
       ; Get the user id from `text_mention` entities
       ; (users with usernames)
      (->> entities
           (filter #(= (:type %) "text_mention"))
           (map #(get-in % [:user :id])))))))

; A thin wrapper so we can use with-redefs in tests
(defn sleep [ms]
  (Thread/sleep ms))

(defn retry
  "Given a function, retry it until it succeeds or max-retries is reached.
  Waits for wait-time between retries.
  wait-time can be a number or a function that given the retry number (from 0) returns a number."
  ([f]
   (retry f {}))
  ([f {:keys [max-retries wait-time]
       :or {max-retries 3}}]
   (let [wait-time (if (number? wait-time)
                     (constantly wait-time)
                     (or wait-time (constantly 5000)))]
     (loop [retries (inc max-retries)]
       (if-not (pos? retries)
         (throw (Exception. "Max retries reached"))
         (let [{:keys [ok ret throwable]}
               (try
                 {:ok true
                  :ret (f)}
                 (catch Throwable t
                   {:ok false
                    :throwable t}))]
           (if ok
             ret
             (do (log/error throwable "Failed to execute function")
                 (let [wait-time (wait-time (- (inc max-retries)
                                               retries))]
                   (log/info "Waiting" wait-time "ms before retrying")
                   (sleep wait-time))
                 (recur (dec retries))))))))))

(defn normalize-middleware
  "Ensures that the given middleware is a list of vectors."
  [middleware]
  (->> middleware
       (map #(if (vector? %) % [%]))))

(defn chain-middleware
  "Given a handler and a list of middleware, returns a new handler with the middlware applied.
  
  To provide arguments to the middleware, use a vector.
  E.g. (chain-middleware handler [middleware1
                                  [middleware2 arg1 arg2]])"
  [handler middleware]
  (->> middleware
       normalize-middleware
       (reduce (fn [handler [m & args]]
                 (apply m handler args))
               handler)))
