(ns tg-clj-server.file-utils
  (:import (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)))

(defn create-temp-file [prefix suffix]
  (-> (Files/createTempFile prefix suffix (into-array FileAttribute nil))
      .toFile))

(defmacro with-delete
  [bindings & body]
  (assert (vector? bindings) "Must have a vector for its binding")
  (assert (even? (count bindings)) "Must have an even number of forms in binding vector")
  (cond
    (= (count bindings) 0) `(do ~@body)
    (symbol? (bindings 0)) `(let ~(subvec bindings 0 2)
                              (try
                                (with-delete ~(subvec bindings 2) ~@body)
                                (finally
                                  (. ~(bindings 0) delete))))
    :else (throw (IllegalArgumentException.
                  "with-delete only allows Symbols in bindings"))))
