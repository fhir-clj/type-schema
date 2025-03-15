(ns main
  (:require [fhir.package]
            [transpiler.fhir-schema :as fhir-schema]
            [transpiler.type-schema :as type-schema]
            [transpiler.package-index :refer [get-enum init-fhir-schema-index! init-valueset-index!]]
            [extract-enum]
            [clojure.string :as str]
            [cheshire.core])
  (:gen-class))

(defn create-package-index [acc file-name read-fn]
  (if (str/ends-with? file-name ".json")
    (let [res (read-fn true)]
      (if (and (:resourceType res) (:url res))
        (assoc-in acc [(:url res)] res) acc)) acc))

(defn get-package [package-name]
  (fhir.package/reduce-package (fhir.package/pkg-info package-name) create-package-index))

(defn structure-definition->fhir-schema [structure-definition-index]
  (reduce (fn [acc [url structure-definition]]
            (assoc acc url (fhir-schema/translate structure-definition))) {} structure-definition-index))

(defn fhir-schema->type-schema [fhir-schema-index]
  (reduce (fn [acc [url fhir-schema]]
            (assoc acc url (type-schema/translate fhir-schema))) {} fhir-schema-index))

(defn logging [data text] (println text) data)

(defn save-as-ndjson [data output-dir]
  (let [file (java.io.File. (str output-dir "/type-schema.ndjson"))
        parent-dir (.getParentFile file)]
    (when (and parent-dir (not (.exists parent-dir)))
      (.mkdirs parent-dir))
    (with-open [writer (java.io.BufferedWriter. (java.io.FileWriter. file))]
      (doseq [item (vals data)]
        (.write writer (cheshire.core/generate-string item))
        (.newLine writer)))))

(defn process-package [package-name output-dir]
  (let [package-index (get-package package-name)
        fhir-schema (structure-definition->fhir-schema package-index)
        type-schema (fhir-schema->type-schema fhir-schema)]

    (init-valueset-index! (extract-enum/get-resolvable-valueset-codes package-index))
    (init-fhir-schema-index! fhir-schema)

    (save-as-ndjson type-schema output-dir)))

(defn -main [& args]
  (if (not= (count args) 2)
    (do
      (println "Usage: java -jar program.jar <package-name> <output-dir>")
      (println "Example: java -jar program.jar hl7.fhir.r4.core@4.0.1 output")
      (System/exit 1))
    (let [package-name (first args)
          output-dir (second args)]
      (try
        (process-package package-name output-dir)
        (System/exit 0)
        (catch Exception e
          (println "Error:" (.getMessage e))
          (System/exit 1))))))

(comment
  (process-package "hl7.fhir.r4.core@4.0.1" "output")

  (get-enum "http://hl7.org/fhir/ValueSet/administrative-gender"))