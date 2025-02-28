(ns transpiler.type-schema
  (:require [clojure.test :refer :all]
            [transpiler.type-schema :as type-schema]
            [cheshire.core :as json]
            [clojure.string :as str]
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

(defn type-schema-content [fs-filename] 
  (let [ts-filename (str/replace fs-filename #".fs.json$" ".ts.json")
        sd          (-> (slurp fs-filename)
                        (json/parse-string true))
        ts-json     (-> sd
                        (type-schema/translate)
                        (json/generate-string {:pretty true}))] 
    (println ts-json)
    (golden-file-content ts-filename ts-json)))

(defmacro golden-test [get-content filename & [as-text]]
  (let [f (if as-text identity #(json/parse-string % true))]
    `(let [[expect-content# actual-content#] (->> (~get-content ~filename)
                                                  (map ~f))]
       (is (= expect-content# actual-content#)
           (str "golden-type-schema check fail for: " ~filename)))))

(deftest structure-definition-test
  (golden-test type-schema-content "test/golden/patient.fs.json")
  (golden-test type-schema-content "test/golden/bundle.fs.json"))


(comment)
