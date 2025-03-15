(ns transpiler.package-index
  (:require [clojure.java.io :as io]
            [cheshire.core :as json])
  (:import [java.util.zip GZIPInputStream]))

(defn load-json-ndjson-file [file]
  (with-open [reader (io/reader (-> file io/input-stream GZIPInputStream.))]
    (->> (line-seq reader)
         (filter seq)
         (map #(json/parse-string % true))
         (filter #(= "FHIRSchema" (:resourceType %)))
         (doall))))

(defn load-merge-schemas []
  (let [packages-dir (io/file "resources/packages")]
    (->> (file-seq packages-dir)
         (filter #(.isFile %))
         (filter #(.endsWith (.getName %) ".ndjson.gz"))
         (mapcat load-json-ndjson-file)
         (reduce (fn [acc schema]
                   (if-let [url (:url schema)] (assoc acc url schema) acc)) {}))))

(def fhir-schema-index (atom nil))
(defn init-fhir-schema-index! [schemas] (reset! fhir-schema-index schemas))
(defn get-fhir-schema [url] (get @fhir-schema-index url))

(def valueset-index (atom nil))
(defn init-valueset-index! [schemas] (reset! valueset-index schemas))
(defn get-enum [url] (get @valueset-index url))
