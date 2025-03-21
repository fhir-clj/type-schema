(ns golden.core
  (:require [cheshire.core :as json]
            [clojure.data :as data]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [clojure.test :refer [is]]))

(defn update-golden? []
  (= "true" (System/getenv "UPDATE_GOLDEN")))

(defn force-update-golden? []
  (= "true" (System/getenv "FORCE_UPDATE_GOLDEN")))

(defmacro vs-json [golden-filename content-gen]
  `(let [actual-content# ~content-gen
         golden-content# (when (.exists (io/file ~golden-filename))
                           (-> (slurp ~golden-filename)
                               (json/parse-string true)))]
     (cond
       (force-update-golden?)
       (do (spit ~golden-filename (json/generate-string actual-content# {:pretty true}))
           (is true))

       (or (nil? golden-content#)
           (and (update-golden?)
                (not= golden-content# actual-content#)))
       (do (spit ~golden-filename (json/generate-string actual-content# {:pretty true}))
           (is true))

       :else
       (is (= golden-content# actual-content#)
           (str "golden json from " ~golden-filename " is not equal: \n"
                (->> (data/diff golden-content# actual-content#)
                     (map #(with-out-str (pprint/pprint %)))
                     (str/join "")))))))
