(ns type-schema.package-index
  (:require
   [clojure.string :as str]
   [fhir.package]
   [transpiler.fhir-schema :as fhir-schema]))

(def *index (atom nil))

(defn index
  ([] @*index)
  ([id] (get @*index id)))

(def *fhir-schema-index (atom nil))

(defn fhir-schema-index
  ([] @*fhir-schema-index)
  ([url] (get @*fhir-schema-index url)))

(defn- keep-fhir-resource-file [acc file-name read-fn]
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

(defn- get-package-index [package-name]
  (fhir.package/reduce-package (fhir.package/pkg-info package-name)
                               keep-fhir-resource-file))

(defn is-structure-definition? [resource]
  (= "StructureDefinition" (:resourceType resource)))

(defn is-value-set? [resource]
  (= "ValueSet" (:resourceType resource)))

(defn init-from-package! [package-name]
  (let [;; NOTE: not a part of SD till 5.2.0-ballot
        ;; https://build.fhir.org/ig/HL7/fhir-extensions/StructureDefinition-package-source.html
        ;; So we add that info manually to fhir-schema in :package-meta, like in
        ;; custom Aidbox resources.
        pkg-info (fhir.package/pkg-info package-name)
        package-meta {:name (:name pkg-info)
                      :version (or (:version pkg-info)
                                   (get-in pkg-info [:dist-tags :latest])
                                   (-> pkg-info :versions first :version))}

        package-index (get-package-index package-name)

        fhir-schemas-index (->> package-index
                                (filter (fn [[_ res]] (is-structure-definition? res)))
                                (map (fn [[url structure-definition]]
                                       [url (fhir-schema/translate {:package-meta package-meta}
                                                                   structure-definition)]))
                                (into {}))]

    (reset! *index package-index)
    (reset! *fhir-schema-index fhir-schemas-index)
    :ok))
