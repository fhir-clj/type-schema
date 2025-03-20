(ns transpiler.package-index
  (:require [clojure.java.io :as io]
            [fhir.package]
            [clojure.string :as str]
            [transpiler.fhir-schema :as fhir-schema]
            [extract-enum]
            [cheshire.core :as json])
  (:import [java.util.zip GZIPInputStream]))

(defn load-json-ndjson-file [file]
  (with-open [reader (io/reader (-> file io/input-stream GZIPInputStream.))]
    (->> (line-seq reader)
         (filter seq)
         (map #(json/parse-string % true))
         (filter #(= "FHIRSchema" (:resourceType %)))
         (doall))))

(defn load-merge-schemas []
  (let [packages-dir (io/file "resources/packages")]
    (->> (file-seq packages-dir)
         (filter #(.isFile %))
         (filter #(.endsWith (.getName %) ".ndjson.gz"))
         (mapcat load-json-ndjson-file)
         (reduce (fn [acc schema]
                   (if-let [url (:url schema)] (assoc acc url schema) acc)) {}))))

(def fhir-schema-index (atom nil))
(defn init-fhir-schema-index! [schemas] (reset! fhir-schema-index schemas))
(defn get-fhir-schema-index [] @fhir-schema-index)
(defn get-fhir-schema [url] (get @fhir-schema-index url))

(def valueset-index (atom nil))
(defn init-valueset-index! [schemas] (reset! valueset-index schemas))
(defn get-enum [url] (get @valueset-index url))

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

        structure-definitions-index
        (->> package-index
             (filter (fn [[_ r]] (= (:resourceType r) "StructureDefinition")))
             (into {}))

        value-sets-index
        (->> package-index
             (filter (fn [[_ r]] (= (:resourceType r) "ValueSet")))
             (into {}))

        fhir-schemas-by-url (->> structure-definitions-index
                                 (map (fn [[url structure-definition]]
                                        [url (fhir-schema/translate {:package-meta package-meta}
                                                                    structure-definition)]))
                                 (into {}))
        fhir-schemas-by-name (->> fhir-schemas-by-url
                                  (map (fn [[_url fhir-schema]]
                                         [(:name fhir-schema) fhir-schema]))
                                  (into {}))
        fhir-schemas-index (merge fhir-schemas-by-url
                                  fhir-schemas-by-name)]

    (assert (= (+ (count fhir-schemas-by-url) (count fhir-schemas-by-name))
               (count fhir-schemas-index)))

    (init-fhir-schema-index! fhir-schemas-index)
    (init-valueset-index! (extract-enum/get-resolvable-valueset-codes value-sets-index))

    :ok))
