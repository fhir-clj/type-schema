(ns main
  (:require
   [cheshire.core]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.cli :as cli]
   [extract-enum]
   [fhir.package]
   [type-schema.core :as type-schema]
   [type-schema.package-index :as package])
  (:gen-class))

(def version "0.0.8")

(def cli-options
  [["-o" "--output DIR" "Output directory or .ndjson file"
    :id :output-dir]
   [nil "--separated-files" "Output each type schema to a separate file (requires -o to be set to a directory)"
    :id :separated-files]
   ["-v" "--verbose" "Enable verbose output"
    :id :verbose]
   [nil "--version" "Print version information and exit"]
   ["-h" "--help" "Show this help message"]])

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

(defn- save-as-separate-files [data output-dir verbose]
  (doseq [item data]
    (let [identifier (get item :identifier)
          name (get identifier :name)
          file-path (str output-dir "/" name ".ts.json")]
      (when verbose (println "Saving type schema:" name "to" file-path))
      (let [file (java.io.File. file-path)]
        (io/make-parents file-path)
        (with-open [writer (java.io.BufferedWriter. (java.io.FileWriter. file))]
          (.write writer (cheshire.core/generate-string item)))))))

(defn process-package [package-name & [{output-dir :output-dir
                                        verbose :verbose
                                        separated-files :separated-files}]]
  (when verbose (println "Processing package:" package-name))
  (package/init-from-package! package-name)
  (when verbose (println "Package initialized, generating schema..."))
  (let [fhir-schemas (package/fhir-schema-index)
        type-schemas (fhir-schema->type-schema fhir-schemas)]
    (cond
      (and output-dir separated-files)
      (do (when verbose (println "Saving each type schema to separate files in:" output-dir))
          (save-as-separate-files type-schemas output-dir verbose))

      output-dir
      (let [output-file (if (str/ends-with? output-dir ".ndjson")
                          output-dir
                          (str output-dir "/" package-name ".ndjson"))]
        (when verbose (println "Saving output to:" output-file))
        (save-as-ndjson type-schemas output-file))

      :else
      (doseq [item type-schemas]
        (println (cheshire.core/generate-string item))))

    (when verbose (println "Processing completed successfully"))
    :ok))

(defn usage [options-summary]
  (->> ["Type Schema Generator for FHIR packages"
        ""
        "Usage: program [options] <package-name>"
        ""
        "Options:"
        options-summary
        ""
        "Examples:"
        "  program hl7.fhir.r4.core@4.0.1                              # Output to stdout"
        "  program -v hl7.fhir.r4.core@4.0.1                           # Verbose mode"
        "  program -o output hl7.fhir.r4.core@4.0.1                    # Output to directory"
        "  program -o result.ndjson hl7.fhir.r4.core@4.0.1             # Output to file"
        "  program -o output --separated-files hl7.fhir.r4.core@4.0.1  # Output each type schema to a separate file"
        "  program --version                                           # Show version"]
       (str/join "\n")))

(defn validate-args [args]
  (let [parsed (cli/parse-opts args cli-options)
        options (get parsed :options)
        arguments (get parsed :arguments)
        errors (get parsed :errors)
        summary (get parsed :summary)]
    (cond
      (get options :help)
      {:exit-message (usage summary) :ok? true}

      (get options :version)
      {:exit-message (str "type-schema version " version) :ok? true}

      errors
      {:exit-message (str/join "\n" errors)}

      (not= (count arguments) 1)
      {:exit-message (str "Error: Exactly one package name is required.\n\n" (usage summary))}

      (and (get options :separated-files) (not (get options :output-dir)))
      {:exit-message (str "Error: --separated-files requires -o/--output to be set to a directory.\n\n" (usage summary))}

      (and (get options :separated-files) (get options :output-dir) (str/ends-with? (get options :output-dir) ".ndjson"))
      {:exit-message (str "Error: --separated-files requires -o/--output to be set to a directory, not a file.\n\n" (usage summary))}

      :else
      {:package-name (first arguments)
       :options options})))

(defn exit [status msg]
  (if (= status 0)
    (println msg)
    (binding [*out* *err*]
      (println msg)))
  (System/exit status))

(defn -main [& args]
  (let [parsed (validate-args args)
        package-name (get parsed :package-name)
        options (get parsed :options)
        exit-message (get parsed :exit-message)
        ok? (get parsed :ok?)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (try
        (process-package package-name options)
        (System/exit 0)
        (catch Exception e
          (binding [*out* *err*]
            (println "Error:" (.getMessage e)))
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
