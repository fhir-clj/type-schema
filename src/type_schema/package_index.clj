(ns type-schema.package-index
  (:require
   [cheshire.core :as json]
   [clojure.string :as str]
   [fhir.package]
   [fhir.schema.translate :as fhir-schema]))

(def *index (atom nil))

(defn index
  ([] @*index)
  ([id] (get @*index id)))

(def *package-meta (atom nil))

(defn package-meta [_url]
  @*package-meta)

(def *fhir-schema-index (atom nil))

(defn fhir-schema-index
  ([] @*fhir-schema-index)
  ([url-or-name]
   (or (get @*fhir-schema-index url-or-name)
       (some (fn [[_ res]]
               (when (= url-or-name (:name res))
                 res))
             @*fhir-schema-index))))

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

(defn is-structure-definition? [fhir-schema]
  (= "StructureDefinition" (:resourceType fhir-schema)))

(defn is-value-set? [fhir-schema]
  (= "ValueSet" (:resourceType fhir-schema)))

(defn clean! []
  (reset! *package-meta nil)
  (reset! *index nil)
  (reset! *fhir-schema-index nil))

;; NOTE: to avoid multiple calls during tests
(def pkg-info (memoize fhir.package/pkg-info))

(defn init-from-package! [package-name]
  (let [;; NOTE: not a part of SD till 5.2.0-ballot
        ;; https://build.fhir.org/ig/HL7/fhir-extensions/StructureDefinition-package-source.html
        ;; So we add that info manually to fhir-schema in :package-meta, like in
        ;; custom Aidbox resources.
        pkg-info (pkg-info package-name)

        package-meta {:name    (:name pkg-info)
                      :version (or (:version pkg-info)
                                   (get-in pkg-info [:dist-tags :latest])
                                   (-> pkg-info :versions first :version))}

        package-index (get-package-index pkg-info)

        fhir-schemas-index (->> package-index
                                (filter (fn [[_ res]] (is-structure-definition? res)))
                                (map (fn [[url structure-definition]]
                                       [url (fhir-schema/translate {:package-meta package-meta}
                                                                   structure-definition)]))
                                (into {}))]
    (reset! *package-meta package-meta)
    (reset! *index package-index)
    (reset! *fhir-schema-index fhir-schemas-index)
    :ok))

(defn append-fhir-schema! [fhir-schema]
  (swap! *fhir-schema-index
         assoc (:url fhir-schema) fhir-schema))

(defn initialize! [{package-name :package-name
                    fhir-schema-fns :fhir-schema-fns
                    default-package-meta :default-package-meta
                    verbose :verbose}]
  (clean!)
  (when package-name
    (when verbose (println "Processing package:" package-name))
    (init-from-package! package-name))

  (doseq [fhir-schema-fn fhir-schema-fns]
    (when verbose (println "Processing FHIR schema file:" fhir-schema-fn))
    (let [fhir-schema (-> fhir-schema-fn
                          (slurp)
                          (json/parse-string true))

          fhir-schema (cond-> fhir-schema
                        (:package-meta fhir-schema)
                        (assoc :package-meta default-package-meta))]

      (append-fhir-schema! fhir-schema)))

  (when verbose (println "Package initialized, generating schema...")))
