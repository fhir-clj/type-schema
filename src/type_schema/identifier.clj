(ns type-schema.identifier
  (:require
   [clojure.string :as str]
   [type-schema.package-index :as package]))

(defn drop-version-from-url [url-with-version]
  (if (string? url-with-version)
    (first (str/split url-with-version #"\|"))
    url-with-version))

(defn- package-meta [fhir-schema]
  (or (:package-meta fhir-schema)
      (package/package-meta (:url fhir-schema))))

(defn- build-nested-name [path]
  (->> path
       (map name)
       (str/join ".")))

(defn is-constraint? [fhir-schema]
  (= (:derivation fhir-schema) "constraint"))

(defn schema-type
  "to build identifier for primitive-type, complex-type, resource, logic"
  [fhir-schema]
  (let [package-meta (package-meta fhir-schema)]
    {:kind    (cond (and (is-constraint? fhir-schema)
                         (= "complex-type"
                            (:kind (package/fhir-schema (:url fhir-schema))))) "complex-type-constraint"
                    (is-constraint? fhir-schema) "constraint"
                    (some? (:kind fhir-schema)) (:kind fhir-schema)
                    :else "resource")
     :package (:name package-meta)
     :version (:version package-meta)
     :name    (:name fhir-schema)
     :url     (:url fhir-schema)}))

(defn nested-type [fhir-schema path]
  (let [package-meta (package-meta fhir-schema)]
    {:kind    "nested"
     :package (:name package-meta)
     :version (:version package-meta)
     :name    (build-nested-name path)
     :url     (str (:url fhir-schema) "#" (build-nested-name path))}))

(defn value-set-type [curl]
  (let [curl         (drop-version-from-url curl)
        value-set    (package/value-set curl)
        package-meta (package-meta value-set)]
    (if (some? value-set)
      {:kind    "value-set"
       :url     curl
       :name    (:id value-set)
       :package (:name package-meta)
       :version (:version package-meta)}
      {:kind "value-set"
       :url  curl})))

(defn binding-type [fhir-schema path element]
  (let [package-meta (package-meta fhir-schema)
        binding (:binding element)
        name (or (:bindingName binding)
                 (str (:name fhir-schema) "." (build-nested-name path) "_binding"))]
    {:kind    "binding"
     :package (:name package-meta)
     :version (:version package-meta)
     :name    name
     :url     (if (:bindingName binding)
                (str "urn:fhir:binding:" name)
                (str (:url fhir-schema) "#" (build-nested-name path) "_binding"))}))
