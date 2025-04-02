(ns main
  (:require
   [cheshire.core]
   [clojure.java.io :as io]
   [extract-enum]
   [fhir.package]
   [type-schema.core :as type-schema]
   [type-schema.package-index :as package])
  (:gen-class))

(defn- fhir-schema->type-schema [fhir-schema-index]
  (->> fhir-schema-index
       (map (fn [[url fhir-schema]]
              [url (type-schema/translate fhir-schema)]))
       (into {})))

(defn- value-set->type-schema [index]
  (->> index
       (filter (fn [[_ value-set]] (package/is-value-set? value-set)))
       (map (fn [[url value-set]] [url (type-schema/translate value-set)]))
       (into {})))

(defn- save-as-ndjson [data output-file]
  (let [file (java.io.File. output-file)]
    (io/make-parents output-file)
    (with-open [writer (java.io.BufferedWriter. (java.io.FileWriter. file))]
      (doseq [item (vals data)]
        (.write writer (cheshire.core/generate-string item))
        (.newLine writer)))))

(defn process-package
  "Process a FHIR package and generate type schema output.
   If output-dir is missing, outputs to standard output instead of file."
  [package-name & [output-dir]]
  (package/init-from-package! package-name)
  (let [fhir-schemas (package/fhir-schema-index)
        type-schemas (fhir-schema->type-schema fhir-schemas)
        type-schemas-valuesets (value-set->type-schema (package/index))
        output-file (str output-dir "/" package-name ".ndjson")]
    (if output-dir
      (save-as-ndjson (merge type-schemas type-schemas-valuesets) output-file)
      (doseq [item (vals (merge type-schemas type-schemas-valuesets))]
        (println (cheshire.core/generate-string item))))))

(defn -main [& args]
  (if (and (not= (count args) 1) (not= (count args) 2))
    (do
      (println "Usage: java -jar program.jar <package-name> <output-dir>")
      (println "Example: java -jar program.jar hl7.fhir.r4.core@4.0.1 output")
      (System/exit 1))
    (let [[package-name output-dir] args]
      (try
        (process-package package-name output-dir)
        (System/exit 0)
        (catch Exception e
          (println "Error:" (.getMessage e))
          (System/exit 1))))))

(comment
  (fhir.package/pkg-info "hl7.fhir.r4.core@4.0.1")

  (process-package "hl7.fhir.r4.core@4.0.1" "output")

  (declare type-schemas0 fhir-schemas0)

  (-> type-schemas0 (get "http://hl7.org/fhir/StructureDefinition/Patient"))
  (-> fhir-schemas0
      (get "http://hl7.org/fhir/StructureDefinition/string"))
  (-> fhir-schemas0
      (get "http://hl7.org/fhir/StructureDefinition/string")
      (type-schema/translate))

  (-> fhir-schemas0
      (get "http://hl7.org/fhir/StructureDefinition/Coding"))
  (-> fhir-schemas0
      (get "http://hl7.org/fhir/StructureDefinition/Coding")
      (type-schema/translate))

  (-> fhir-schemas0
      (get "http://hl7.org/fhir/StructureDefinition/Patient"))

  (process-package "hl7.fhir.r5.core" "output")
  (process-package "hl7.fhir.r6.core" "output")
  (process-package "hl7.fhir.us.core@6.1.0" "output")

  (fhir.package/pkg-info "hl7.fhir.r4.core@4.0.1")
  (fhir.package/pkg-info "hl7.fhir.us.core@6.1.0")
  (extract-enum/get-enum "http://hl7.org/fhir/ValueSet/administrative-gender"))
