(ns transpiler.package-index
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]))

(defn load-json-file [file]
  (-> (slurp file)
      (json/parse-string true)))

(defn load-directory [dir]
  (->> (io/file dir)
       file-seq
       (filter #(.isFile %))
       (filter #(.endsWith (.getName %) ".json"))
       (map (fn [f] [(.getName f) (load-json-file f)]))
       (into {})))

(defn load-merge-schemas []
  (let [complex (load-directory "test/golden/complex")
        primitive (load-directory "test/golden/primitive")]
    (->> (merge complex primitive)
         (reduce (fn [acc [_ schema]]
                   (assoc acc (:url schema) schema)) {}))))

(def index (atom nil))

(defn init! []
  (reset! index (load-merge-schemas)))

(defn get-schema [url] (get @index url))

#_(defn get-schema [url]
    (let [schema (get @index url)]
      (assert (some? schema) (str "Missing schema for: " url)) schema))

;; (get @index "http://hl7.org/fhir/StructureDefinition/boolean")
;; (get-schema "http://hl7.org/fhir/StructureDefinition/boolean")

(defn get-all [] @index)

(init!)

