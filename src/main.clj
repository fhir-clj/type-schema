(ns main
  (:require [fhir.package]
            [transpiler.fhir-schema :as fhir-schema]
            [transpiler.type-schema :as type-schema]
            [transpiler.package-index :as index]
            [extract-enum]
            [clojure.string :as str]
            [cheshire.core])
  (:gen-class))

(defn keep-fhir-resource-file [acc file-name read-fn]
  (if (str/ends-with? file-name ".json")
    (try (let [res (read-fn true)]
           (if (and (:resourceType res) (:url res))
             (assoc-in acc [(:url res)] res)
             acc))
         (catch Exception e
           (binding [*out* *err*]
             (println "SKIP: " file-name
                      " error: " (str/replace (.getMessage e) #"\n" " ")))
           acc)) acc))

(defn get-package-index [package-name]
  (fhir.package/reduce-package (fhir.package/pkg-info package-name)
                               keep-fhir-resource-file))

(defn structure-definition->fhir-schema [conf structure-definition-index]
  (->> structure-definition-index
       (map (fn [[url structure-definition]]
              [url (fhir-schema/translate conf structure-definition)]))
       (into {})))

(defn fhir-schema->type-schema [fhir-schema-index]
  (->> fhir-schema-index
       (map (fn [[url fhir-schema]]
              [url (type-schema/translate fhir-schema)]))
       (into {})))

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
  (index/init-from-package! package-name)
  (let [fhir-schemas (index/get-fhir-schema-index)
        type-schemas (fhir-schema->type-schema fhir-schemas)]
    (save-as-ndjson type-schemas output-dir)))

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
