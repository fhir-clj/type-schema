(ns transpiler.type-schema
  (:require [clojure.string :as str]
            [transpiler.package-index :refer [get-schema]]))

;; TypeRefType = 'resource' | 'profile' | 'logical' | 'complex-type' | 'primitive-type' | 'nested' | 'valueset' | 'choice' | 'unknown'

;;  TypeRef {
;;     name: string;
;;     package: string;
;;     parent?: string;
;;     type?: TypeRefType;
;;     url?: string;
;; }

;; ClassField {
;;     required?: boolean;
;;     type: TypeRef;
;;     array?: boolean;
;;     choiceOf?: string;
;;     choices?: string[];
;;     binding?: {
;;         valueSet?: TypeRef;
;;         strength?: 'required' | 'extensible' | 'preferred' | 'example';
;;     };
;; }

;; ChoiceField {
;;     required?: boolean;
;;     choices?: string[];
;; }

;; {
;;     kind: TypeRefType;
;;     name: TypeRef (Patient)
;;     base?: TypeRef (DomainResource)
;;     fields?:  { [key: string]: ClassField };
;;     choices?: { [key: string]: ChoiceField };
;;     allDependencies?: TypeRef[];
;;     nestedTypes?: ITypeSchema[];
;;     derivation?: DerivationType;
;; }


;; 1. load everything in memory {"canonical-url" {...}}
;; 2. iterate over { :kind resource } and { :derivation "specialization" }
;; 3. 

(defn derive-kind-from-schema [schema]
  (cond (= (:derivation schema) "constraint") "profile"
        (= (:type schema) "BackboneElement") "nested"
        (:choices schema) "choices"
        :else (:kind schema)))

(defn build-element [element fhir-schema]
  (let [required (some #{name} (:required fhir-schema))
        element-type (:type element)
        element-url (str "http://hl7.org/fhir/StructureDefinition/" element-type)
        element-schema (get-schema element-url)]
    (-> (select-keys element [:choices :array :binding :choiceOf])
        (cond-> required (assoc-in [:required] required))
        (cond-> element-type (assoc-in [:type :kind] (derive-kind-from-schema element-schema)))
        (cond-> element-type (assoc-in [:type :url] element-url))
        (cond-> element-type (assoc-in [:type :name] element-type))
        (cond-> (and element-type (:base element-schema)) (assoc-in [:type :base] (:base element-schema))))))

(defn get-base-info [fhir-schema]
  (-> {:package {:version (:version fhir-schema)}}
      (assoc-in [:type] (select-keys fhir-schema (filter fhir-schema [:name :base :url :version])))
      (assoc-in [:type :kind] (derive-kind-from-schema fhir-schema))))

(defn translate [fhir-schema]
  (let [base-info (get-base-info fhir-schema)
        elements (get-in fhir-schema [:elements])
        transformed-elements (reduce (fn [acc [name element]]
                                       (assoc acc name (build-element element fhir-schema))) {} elements)]
    (merge base-info {:fields transformed-elements})))