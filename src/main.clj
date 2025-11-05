(ns main
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.stacktrace :as stacktrace]
   [clojure.string :as str]
   [clojure.tools.cli :as cli]
   [extract-enum]
   [fhir.package]
   [fhir.schema.translate]
   [type-schema.core :as type-schema]
   [type-schema.log :as log]
   [type-schema.package-index :as package]
   [type-schema.sanity :as sanity])
  (:gen-class))

(def version "0.0.16")

(def cli-options
  [["-o" "--output DIR" "Output directory or .ndjson file"
    :id :output-dir]
   [nil "--separated-files" "Output each type schema to a separate file (requires -o to be set to a directory)"
    :id :separated-files]
   [nil "--treeshake TYPES" "Required type(s) to include in output"
    :multi true
    :update-fn (fnil conj [])
    :id :treeshake]
   [nil "--fhir-schema FILE" "Add specific FHIR schema to be processed. Can be used multiple times"
    :multi true
    :update-fn (fnil conj [])
    :id :fhir-schemas]
   [nil "--include-profile-constraints" "Include FHIR profile constraints in output (adds profileConstraints field)"
    :id :include-profile-constraints?]
   [nil "--include-field-docs" "Include field documentation in output (adds short, definition, comment, etc.)"
    :id :include-field-docs?]
   [nil "--drop-cache" "Drop all package caches before processing"
    :id :drop-cache]
   ["-v" "--verbose" "Enable verbose output"
    :id :verbose]
   [nil "--version" "Print version information and exit"]
   ["-h" "--help" "Show this help message"]])

(defn- extract-dependencies [type-schema]
  (let [deps (get type-schema :dependencies)]
    (if (seq deps) deps [])))

(defn- treeshake-type-schemas [type-schemas required-types verbose]
  (let [required-types-set (into #{} required-types)
        id-to-schema (into {} (map (fn [schema] [(get-in schema [:identifier :name]) schema]) type-schemas))
        required-types-schemas (filter (fn [schema] (contains? required-types-set (get-in schema [:identifier :name]))) type-schemas)]

    (log/info verbose "Starting treeshaking process for types:" required-types)

    (loop [result {}
           to-process (into #{} (map (fn [schema] (get-in schema [:identifier :name])) required-types-schemas))]
      (if (empty? to-process)
        (vals result)
        (let [current-type (first to-process)
              current-schema (get id-to-schema current-type)
              dependencies (if current-schema
                             (->> (extract-dependencies current-schema)
                                  (map (fn [dep] (get-in dep [:name])))
                                  (filter (fn [dep] (and dep (not (contains? result dep)))))
                                  (into #{}))
                             #{})]
          (if (nil? current-schema)
            (do
              (log/warn "Warning: Required type not found:" current-type)
              (recur result (disj to-process current-type)))
            (do
              (log/info verbose "Including type:" current-type "with" (count dependencies) "dependencies")
              (recur (assoc result current-type current-schema)
                     (set/union (disj to-process current-type) dependencies)))))))))

(defn- save-as-ndjson [data output-file]
  (let [file (java.io.File. output-file)]
    (io/make-parents output-file)
    (with-open [writer (java.io.BufferedWriter. (java.io.FileWriter. file))]
      (doseq [item data]
        (.write writer (json/generate-string item))
        (.newLine writer)))))

(defn- save-as-separate-files [data output-dir verbose]
  (let [name-count (atom {})]
    (doseq [item data]
      (let [name         (get-in item [:identifier :name])
            package-name (get-in item [:identifier :package])
            file-base    (str output-dir "/" package-name "/" name)
            file-path    (str file-base
                              (when-let [n (get @name-count (str/lower-case file-base))]
                                (str "-" n))
                              ".ts.json")]
        ;; NOTE: We use a counter to avoid overwriting files with the same name
        (swap! name-count update (str/lower-case file-base) (fnil inc 1))

        (when verbose (println "Saving type schema:" name "to" file-path))
        (let [file (java.io.File. file-path)]
          (io/make-parents file-path)
          (with-open [writer (java.io.BufferedWriter. (java.io.FileWriter. file))]
            (.write writer (json/generate-string item {:pretty true}))))))))

(defn process-packages [{package-names :package-names
                         output-dir :output-dir
                         fhir-schemas :fhir-schemas
                         verbose :verbose
                         separated-files :separated-files
                         treeshake :treeshake
                         drop-cache :drop-cache
                         include-profile-constraints? :include-profile-constraints?
                         include-field-docs? :include-field-docs?}]
  (when drop-cache
    (log/info verbose "Dropping package cache")
    (fhir.package/drop-cache))

  (package/initialize! {:package-names   package-names
                        :fhir-schema-fns fhir-schemas
                        :verbose         verbose})

  (let [translation-opts {:include-profile-constraints? include-profile-constraints?
                          :include-field-docs?          include-field-docs?}
        fhir-schemas (package/fhir-schema)
        type-schemas (concat (->> fhir-schemas
                                  (map (fn [[_url fhir-schema]]
                                         (type-schema/translate-fhir-schema fhir-schema translation-opts)))
                                  (apply concat))
                             (->> (package/value-set)
                                  (vals)
                                  (map type-schema/translate-value-set)))
        type-schemas (if treeshake
                       (do (log/info verbose "Treeshaking output based on required types:" treeshake)
                           (treeshake-type-schemas type-schemas treeshake verbose))
                       type-schemas)]
    (cond
      (and output-dir separated-files)
      (do (log/info verbose "Saving each type schema to separate files in:" output-dir)
          (save-as-separate-files type-schemas output-dir verbose))

      output-dir
      (let [output-file (if (str/ends-with? output-dir ".ndjson")
                          output-dir
                          (str output-dir "/"
                               (str/join "-" package-names)
                               ".ndjson"))]
        (log/info verbose "Saving output to:" output-file)
        (save-as-ndjson type-schemas output-file))

      :else
      (doseq [item type-schemas]
        (println (json/generate-string item))))

    (let [unresolvable-deps (sanity/find-unresolvable-deps type-schemas)]
      (if (empty? unresolvable-deps)
        (log/info verbose "Processing completed successfully")
        (doseq [dep unresolvable-deps
                ;; NOTE: skip value sets because we already have this warning earlier
                :when (not= "value-set" (get dep :kind))]
          (log/warn "Unresolvable dependency found:" dep))))
    :ok))

(defn usage [options-summary]
  (->> ["Type Schema Generator for FHIR packages"
        ""
        "Usage: type-schema [options] [<package-name>]"
        ""
        "Options:"
        options-summary
        ""
        "Examples:"
        "  type-schema hl7.fhir.r4.core@4.0.1                                  # Output to stdout"
        "  type-schema -v hl7.fhir.r4.core@4.0.1                               # Verbose mode"
        "  type-schema -o output hl7.fhir.r4.core@4.0.1                        # Output to directory"
        "  type-schema -o result.ndjson hl7.fhir.r4.core@4.0.1                 # Output to file"
        "  type-schema -o output --separated-files hl7.fhir.r4.core            # Output each type schema to a separate file"
        "  type-schema --treeshake Patient,Observation hl7.fhir.r4.core        # Only include specified types and dependencies"
        "  type-schema --include-profile-constraints hl7.fhir.no.basis@2.2.2   # Include profile constraint information"
        "  type-schema --include-field-docs hl7.fhir.r4.core                   # Include field documentation (short, definition, etc.)"
        "  type-schema --drop-cache                                            # Drop all package caches"
        "  type-schema --version                                               # Show version"]
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

      (and (get options :drop-cache)
           (nil? (first arguments)))
      {:drop-cache true :options options :ok? true}

      errors
      {:exit-message (str/join "\n" errors)}

      (and (get options :separated-files) (not (get options :output-dir)))
      {:exit-message (str "Error: --separated-files requires -o/--output to be set to a directory.\n\n" (usage summary))}

      (and (get options :separated-files) (get options :output-dir) (str/ends-with? (get options :output-dir) ".ndjson"))
      {:exit-message (str "Error: --separated-files requires -o/--output to be set to a directory, not a file.\n\n" (usage summary))}

      :else
      {:package-name arguments
       :options options
       :drop-cache (get options :drop-cache)})))

(defn exit [status msg]
  (if (= status 0)
    (println msg)
    (binding [*out* *err*]
      (println msg)))
  (System/exit status))

(defn -main [& args]
  (let [parsed (validate-args args)
        package-names (get parsed :package-name)
        options (get parsed :options)
        exit-message (get parsed :exit-message)
        ok? (get parsed :ok?)]
    (cond
      exit-message
      (exit (if ok? 0 1) exit-message)

      (and (:drop-cache parsed)
           (nil? package-names))
      (do
        (log/info (:verbose options) "Dropping package cache")
        (fhir.package/drop-cache)
        (System/exit 0))

      :else
      (try
        (process-packages (-> options
                              (assoc :package-names package-names)))
        (System/exit 0)
        (catch Exception e
          (binding [*out* *err*]
            (println "Error:" (.getMessage e))
            (when (:verbose options)
              (stacktrace/print-stack-trace e)))
          (System/exit 1))))))

(comment
  (fhir.package/pkg-info "hl7.fhir.r4.core@4.0.1")
  (process-packages {:package-names ["hl7.fhir.r4.core"] :output-dir "output" :separated-files true})

  ;; StructureDefinition
  (->> (package/structure-definition "http://hl7.org/fhir/StructureDefinition/Dosage")
       ;; :differential :element (map :id) sort
       :differential :element (filter #(= "Dosage.doseAndRate" (:id %))) (first))

;; FHIR Schema
  (->> (package/structure-definition "http://hl7.org/fhir/StructureDefinition/Dosage")
       (package/sd->fhir-schema)
       ;; :elements keys sort
       :elements :doseAndRate)

;; Type Schema

  (->> (package/structure-definition "http://hl7.org/fhir/StructureDefinition/Dosage")
       (package/sd->fhir-schema)
       (type-schema/translate-fhir-schema)
       ;; first :dependencies (filter #(= "nested" (:kind %)))
       ;; first :nested (map :identifier)
       ;; first :fields keys sort
       first :fields :doseAndRate)
  ;;
  )
