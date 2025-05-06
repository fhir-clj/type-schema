(ns main
  (:require
   [cheshire.core]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [extract-enum]
   [fhir.package]
   [type-schema.core :as type-schema]
   [type-schema.package-index :as package])
  (:gen-class))

(def version "0.0.7")

(defn- fhir-schema->type-schema [fhir-schema-index]
  (->> fhir-schema-index
       (map (fn [[_url fhir-schema]]
              (type-schema/translate fhir-schema)))
       (apply concat)))

(defn- save-as-ndjson [data output-file]
  (let [file (java.io.File. output-file)]
    (io/make-parents output-file)
    (with-open [writer (java.io.BufferedWriter. (java.io.FileWriter. file))]
      (doseq [item data]
        (.write writer (cheshire.core/generate-string item))
        (.newLine writer)))))

(defn process-package
  "Process a FHIR package and generate type schema output.
   If output-dir is missing, outputs to standard output instead of file."
  [package-name & [output-dir]]
  (package/init-from-package! package-name)
  (let [fhir-schemas (package/fhir-schema-index)
        type-schemas (fhir-schema->type-schema fhir-schemas)
        output-file (if (str/ends-with? output-dir ".ndjson")
                      output-dir
                      (str output-dir "/" package-name ".ndjson"))]
    (if output-dir
      (save-as-ndjson type-schemas output-file)
      (doseq [item type-schemas]
        ;; FIXME: for some reason error messages send to stdout instead of stderr
        (println (cheshire.core/generate-string item))))
    :ok))

(defn -main [& args]
  (cond
    (or (= (first args) "--version") (= (first args) "-v"))
    (do
      (println (str "type-schema version " version))
      (System/exit 0))

    (and (not= (count args) 1) (not= (count args) 2))
    (do
      (println "Usage: java -jar program.jar [options] <package-name> [<output-dir>|<ndjson-file>]")
      (println "Options:")
      (println "  --version, -v   Print the current version")
      (println "Example: java -jar program.jar hl7.fhir.r4.core@4.0.1 output")
      (System/exit 1))

    :else
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
