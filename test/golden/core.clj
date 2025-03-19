(ns golden.core
  (:require [clojure.test :refer [is]]
            [cheshire.core :as json]
            [clojure.java.io :as io]))

(defn update-golden? []
  (= "true" (System/getenv "UPDATE_GOLDEN")))

(defmacro vs-json-file [golden-filename input-filename action-fn]
  `(let [input-content#  (-> (slurp ~input-filename)
                             (json/parse-string true))
         actual-content# (~action-fn input-content#)
         golden-content# (when (.exists (io/file ~golden-filename))
                           (-> (slurp ~golden-filename)
                               (json/parse-string true)))]
     (cond
       (or (nil? golden-content#)
           (update-golden?))
       (do (spit ~golden-filename (json/generate-string actual-content# {:pretty true}))
           (is true))

       :else
       (is (= golden-content# actual-content#)
           (str "golden json from " ~golden-filename " is not equal to actual data")))))
