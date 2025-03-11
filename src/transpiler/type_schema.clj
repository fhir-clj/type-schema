(ns transpiler.type-schema
  (:require [transpiler.package-index :refer [get-schema]]))

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

;; ITypeSchema {
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

(defn is-schema [kind]
  (contains? #{"resource" "profile" "logical" "complex-type" "primitive-type"} kind))

(defn derive-kind-from-schema [schema]
  (cond (= (:derivation schema) "constraint") "profile"
        ;; (= (:type schema) "BackboneElement") "nested"
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
        (cond-> (and element-type (:base element-schema)) (assoc-in [:type :base] (:base element-schema)))
        (cond-> (:elementReference element) (assoc-in [:elementReference] (:elementReference element))))))

(defn build-backbone-element [element fhir-schema path]
  (let [required (some #{name} (:required fhir-schema))]
    (-> (select-keys element [:array])
        (assoc-in [:type :kind] "nested")
        (assoc-in [:type :path] path)
        (cond-> required (assoc-in [:required] required)))))

(defn get-base-info [fhir-schema]
  (-> {:package {:version (:version fhir-schema)}}
      (assoc-in [:type] (select-keys fhir-schema (filter fhir-schema [:name :base :url :version])))
      (assoc-in [:type :kind] (derive-kind-from-schema fhir-schema))))

(defn iterate-over-elements [fhir-schema elements path]
  (reduce (fn [acc [key element]]
            (if (= (:type element) "BackboneElement")
              (assoc acc key (build-backbone-element element fhir-schema (concat path [key])))
              (assoc acc key (build-element element fhir-schema)))) {} elements))

(defn iterate-over-backbone-element [fhir-schema elements parent-path]
  (reduce (fn [acc [key element]]
            (if (= (:type element) "BackboneElement")
              (let [path (concat parent-path [(name key)])
                    fields (iterate-over-elements fhir-schema (:elements element) path)]
                (concat acc
                        [{:path path :schema {:type (build-element element fhir-schema) :fields fields}}]
                        (iterate-over-backbone-element fhir-schema (:elements element) path))) acc)) [] elements))

(defn extract-dependencies [elements]
  (reduce (fn [acc [_ element]]
            (if (is-schema (get-in element [:type :kind])) (conj acc (:type element)) acc)) [] elements))

(defn extract-dependencies-from-backbone-elements [elements]
  (reduce (fn [acc element]
            (let [schema-type (get-in [:schema :type :type] element)]
              (-> (concat acc (extract-dependencies (:fields (:schema element))))
                  (cond-> schema-type (concat [schema-type]))))) [] elements))

(defn translate [fhir-schema]
  (let [base-info (get-base-info fhir-schema)
        elements (get-in fhir-schema [:elements])
        transformed-elements (iterate-over-elements fhir-schema elements [])
        transformed-backbone-elements (iterate-over-backbone-element fhir-schema elements [])]

    (merge base-info {:fields transformed-elements
                      :nestedTypes (vec transformed-backbone-elements)
                      :dependencies (distinct (concat [(get-in base-info [:type])]
                                                      (extract-dependencies transformed-elements)
                                                      (extract-dependencies-from-backbone-elements transformed-backbone-elements)))})))