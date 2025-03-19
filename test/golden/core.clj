(ns golden.core
  (:require [clojure.test :refer [is]]
            [cheshire.core :as json]
            [clojure.java.io :as io]))

(defn update-golden? []
  (= "true" (System/getenv "UPDATE_GOLDEN")))

(defn golden-file-content [expect-filename actual-content]
  (let [expect-content (when (.exists (io/file expect-filename))
                         (slurp expect-filename))]
    (cond
      (or (nil? expect-content)
          (update-golden?))
      (do (spit expect-filename actual-content)
          [actual-content actual-content])
      :else
      [expect-content actual-content])))

(defmacro golden-test [get-content filename & [as-text]]
  (let [f (if as-text identity #(json/parse-string % true))]
    `(let [[expect-content# actual-content#] (->> (~get-content ~filename)
                                                  (map ~f))]
       (is (= expect-content# actual-content#)
           (str "golden-type-schema check fail for: " ~filename)))))
