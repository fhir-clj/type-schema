(ns transpiler.type-schema
  (:require [clojure.string :as str]))

;; 1. load everything in memory {"canonical-url" {...}}
;; 2. iterate over { :kind resource } and { :derivation "specialization" }
;; 3. 
(defn build-element-type [element] ;; index
  {:name (:type element)
   :package "..."
   :parent "..."
   :url "..."
   :type "primitive-type"})

(defn build-element [element required]
  (-> (select-keys element [:choices :array :binding :choiceOf])
      (merge (build-element-type element))
      (cond-> required (assoc :required required))))

;; TODO: get somewhere packageId
;; TODO: url from splited url?
(defn get-base-info [fhir-schema]
  {:type (:kind fhir-schema)
   :name (select-keys fhir-schema (filter fhir-schema [:name :url :version]))
   :package {:packageId "hl7.fhir.core.r4" :version (:version fhir-schema) :url "http://hl7.org/fhir"}})

(defn translate [fhir-schema]
  (let [base-info (get-base-info fhir-schema)
        elements (get-in fhir-schema [:elements])
        transformed-elements (reduce (fn [acc [name element]]
                                       (assoc acc name (build-element element (some #{name} (:required fhir-schema))))) {} elements)]
    (merge base-info transformed-elements)))