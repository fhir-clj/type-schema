(ns type-schema.package-index
  (:require
   [cheshire.core :as json]
   [clojure.string :as str]
   [fhir.package]
   [fhir.schema.translate :as fhir-schema]
   [type-schema.log :as log]))

(defonce *index (atom nil))

(defn index
  ([] @*index)
  ([curl] (get @*index curl)))

(defn resource [curl]
  (get-in @*index [curl :resource]))

(defn fhir-schema
  ([] (->> @*index
           (keep (fn [[curl res]]
                   (when (:fhir-schema res)
                     [curl (:fhir-schema res)])))
           (into {})))
  ([curl-or-name]
   (or (get-in @*index [curl-or-name :fhir-schema])
       (->> @*index
            (some (fn [[_ res]]
                    (when (= curl-or-name (get-in res [:fhir-schema :name]))
                      (:fhir-schema res))))))))

(defn package-meta [curl]
  (or (some-> (fhir-schema curl) :package-meta)
      (get-in @*index [curl :package-meta])
      {:name    "undefined"
       :version "undefined"}))

(defn structure-definition
  ([] (->> @*index
           (keep (fn [[curl res]]
                   (when (:structure-definition res)
                     [curl (:structure-definition res)])))
           (into {})))
  ([curl-or-name]
   (or (get-in @*index [curl-or-name :structure-definition])
       (->> @*index
            (some (fn [[_ res]]
                    (when (= curl-or-name (get-in res [:structure-definition :name]))
                      (:structure-definition res))))))))

(defn value-set
  ([] (->> @*index
           (keep (fn [[curl res]]
                   (when (:value-set res)
                     [curl (:value-set res)])))
           (into {})))
  ([curl] (get-in @*index [curl :value-set])))

(defn- keep-fhir-resource-file [acc file-name read-fn]
  (if (str/ends-with? file-name ".json")
    (try (let [res (read-fn true)]
           (if (and (:resourceType res) (:url res))
             (assoc-in acc [(:url res)] res)
             acc))
         (catch Exception e
           (binding [*out* *err*]
             (println "SKIP: " file-name
                      " error: " (some-> (.getMessage e)
                                         (str/replace #"\n" " "))))
           acc)) acc))

(defn- get-package-index [package-info]
  (fhir.package/reduce-package package-info keep-fhir-resource-file))

(defn is-structure-definition? [resource]
  (= "StructureDefinition" (:resourceType resource)))

(defn is-value-set? [resource]
  (= "ValueSet" (:resourceType resource)))

(defn structure-definition-hierarchy [sd]
  (let [base (-> sd :baseDefinition (structure-definition))]
    (if base
      (cons sd (structure-definition-hierarchy base))
      [sd])))

;; NOTE: to avoid multiple calls during tests
(def pkg-info (memoize fhir.package/pkg-info))

(defn sd->fhir-schema [sd & [package-meta]]
  (let [hierarchy (structure-definition-hierarchy sd)]
    (fhir.schema.translate/translate
     {:package-meta                   package-meta
      :structure-definition-ancestors hierarchy}
     sd)))

(defn load-package! [package-name]
  (let [;; NOTE: not a part of SD till 5.2.0-ballot
        ;; https://build.fhir.org/ig/HL7/fhir-extensions/StructureDefinition-package-source.html
        ;; So we add that info manually to fhir-schema in :package-meta, like in
        ;; custom Aidbox resources.
        pkg-info (pkg-info package-name)

        package-meta {:name    (:name pkg-info)
                      :version (or (:version pkg-info)
                                   (get-in pkg-info [:dist-tags :latest])
                                   (-> pkg-info :versions first :version))}

        index (->> (get-package-index pkg-info)
                   (map (fn [[url resource]]
                          [url (cond-> {:package-meta package-meta
                                        :resource     resource}
                                 (is-structure-definition? resource)
                                 (assoc :structure-definition resource
                                        :fhir-schema (sd->fhir-schema resource package-meta))
                                 (is-value-set? resource)
                                 (assoc :value-set resource))]))
                   (into {}))]

    (doseq [[curl entity] index]
      (when-let [existed-entity (get @*index curl)]
        (log/warn (format "Duplicate FHIR resource found: `%s`: `%s` -> `%s`"
                          curl
                          (get-in existed-entity [:package-meta :name])
                          (get-in entity [:package-meta :name]))))
      (swap! *index assoc curl entity))
    :ok))

(defn load-fhir-schema! [fhir-schema]
  (swap! *index
         assoc (:url fhir-schema) {:fhir-schema fhir-schema}))

(defn load-resource! [resource]
  (swap! *index
         assoc (:url resource) {:resource resource}))

(defn initialize! [{package-names        :package-names
                    fhir-schema-fns      :fhir-schema-fns
                    default-package-meta :default-package-meta
                    verbose              :verbose}]
  (reset! *index nil)

  (doseq [package-name package-names]
    (log/info verbose "Processing package:" package-name)
    (load-package! package-name))

  (doseq [fhir-schema-fn fhir-schema-fns]
    (log/info verbose "Processing FHIR schema file:" fhir-schema-fn)
    (let [fhir-schema (-> fhir-schema-fn
                          (slurp)
                          (json/parse-string true))

          fhir-schema (cond-> fhir-schema
                        (:package-meta fhir-schema)
                        (assoc :package-meta default-package-meta))]

      (load-fhir-schema! fhir-schema)))

  (log/info verbose "Package initialized, generating schema..."))
