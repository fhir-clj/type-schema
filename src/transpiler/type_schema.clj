(ns transpiler.type-schema
  (:require [transpiler.package-index :refer [get-fhir-schema get-enum]]
            [clojure.string :as str]))

(defn is-schema [kind]
  (contains? #{"resource" "profile" "logical" "complex-type" "primitive-type"} kind))

(defn derive-kind-from-schema [schema]
  (cond (= (:derivation schema) "constraint") "profile"
        ;; (= (:type schema) "BackboneElement") "nested"
        (:choices schema) "choices"
        :else (:kind schema)))

(defn split-url-version [url-with-version]
  (if (and url-with-version (string? url-with-version))
    (first (str/split url-with-version #"\|")) url-with-version))

(defn attach-enum [binding]
  (let [enum (get-enum (split-url-version (:valueSet binding)))]
    (if enum (assoc binding :enum enum) binding)))

(defn get-identifier [fhir-schema]
  #_(assert (some? (:url fhir-schema)))
  (let [package-meta-fallback
        {:name (cond
                 (and (some-> fhir-schema (str/starts-with? "http://hl7.org/fhir"))
                      (some-> fhir-schema :version (str/starts-with? "4.")))
                 "hl7.fhir.r4.core"

                 (and (some-> fhir-schema (str/starts-with? "http://hl7.org/fhir"))
                      (some-> fhir-schema :version (str/starts-with? "5.")))
                 "hl7.fhir.r5.core"

                 :else "undefined")
         :version (:version fhir-schema)}

        package-meta (or (:package-meta fhir-schema)
                         package-meta-fallback)]

    {:kind    (derive-kind-from-schema fhir-schema)
     :package (:name package-meta)
     :version (:version package-meta)
     :name    (:id fhir-schema)
     :url     (:url fhir-schema)}))

(defn build-element [element fhir-schema]
  (let [required (some #{name} (:required fhir-schema))
        element-type (:type element)
        element-url (str "http://hl7.org/fhir/StructureDefinition/" element-type)
        element-schema (get-fhir-schema element-url)]
    (-> (select-keys element [:choices :array :choiceOf])
        (cond-> required (assoc-in [:required] required))
        (cond-> (:binding element) (assoc-in [:binding] (attach-enum (:binding element))))
        (cond-> element-type (assoc-in [:type :kind] (derive-kind-from-schema element-schema)))
        (cond-> element-type (assoc-in [:type :url] element-url))
        (cond-> element-type (assoc-in [:type :name] element-type))
        (cond-> (and element-type (:base element-schema)) (assoc-in [:type :base] (:base element-schema)))
        (cond-> (:elementReference element) (assoc-in [:elementReference] (:elementReference element))))))

(defn build-field [element fhir-schema]
  (let [required (some #{name} (:required fhir-schema))
        type (some-> (:type element)
                     (get-fhir-schema)
                     (get-identifier))]

    (cond-> (select-keys element [:array :choices :choiceOf])
      (some? required) (assoc :required true)
      (some? type)     (assoc :type type))

    #_(->
       (cond-> (:binding element) (assoc-in [:binding] (attach-enum (:binding element))))
       (cond-> (and element-type (:base element-schema)) (assoc-in [:type :base] (:base element-schema)))
       (cond-> (:elementReference element) (assoc-in [:elementReference] (:elementReference element))))))

(defn build-backbone-element [element fhir-schema path]
  (let [required (some #{name} (:required fhir-schema))]
    (-> (select-keys element [:array])
        (assoc-in [:type :kind] "nested")
        (assoc-in [:type :path] path)
        (cond-> required (assoc-in [:required] required)))))

#_(defn get-base-info [fhir-schema]
    (-> {:package {:version (:version fhir-schema)}}
        (assoc-in [:type] (select-keys fhir-schema (filter fhir-schema [:name :base :url :version])))
        (assoc-in [:type :kind] (derive-kind-from-schema fhir-schema))))

(defn iterate-over-elements [fhir-schema elements path]
  (->> elements
       (map (fn [[key element]]
              (if (= (:type element) "BackboneElement")
                [key (build-backbone-element element fhir-schema (concat path [key]))]
                [key (build-field element fhir-schema)])))
       (into {})))

(defn iterate-over-backbone-element [fhir-schema elements parent-path]
  (reduce (fn [acc [key element]]
            (if (= (:type element) "BackboneElement")
              (let [path (concat parent-path [(name key)])
                    fields (iterate-over-elements fhir-schema (:elements element) path)
                    type (:type (build-element element fhir-schema))]
                (concat acc
                        [{:path path
                          :fields fields
                          :type (-> type (assoc :base (:url type)) (dissoc :url))}]
                        (iterate-over-backbone-element fhir-schema (:elements element) path))) acc)) [] elements))

(defn extract-dependencies [elements]
  (->> elements
       (keep (fn [[_ element]]
               (when (is-schema (get-in element [:type :kind]))
                 (:type element))))
       (into [])))

(defn extract-dependencies-from-backbone-elements [elements]
  (reduce (fn [acc element]
            (let [schema-type (:type element)]
              (-> (concat acc (extract-dependencies (:fields element)))
                  (cond-> schema-type (concat [schema-type]))))) [] elements))

(defn translate [fhir-schema]
  (let [parent      (-> fhir-schema :base (get-fhir-schema))

        identifier  (get-identifier fhir-schema)
        base        (some-> parent (get-identifier))
        description (:description fhir-schema)

        elements    (:elements fhir-schema)
        fields      (iterate-over-elements fhir-schema elements [(get-in fhir-schema [:url])])

        transformed-backbone-elements (iterate-over-backbone-element fhir-schema elements [(get-in fhir-schema [:url])])

        nested []

        depends
        (->> (concat [base]
                     (extract-dependencies fields)
                     #_(extract-dependencies-from-backbone-elements transformed-backbone-elements))
             (distinct)
             (into []))]

    (cond-> {:identifier identifier}
      base                   (assoc :base base)
      description            (assoc :description description)
      (not (empty? fields))  (assoc :fields fields)
      (not (empty? nested))  (assoc :nested nested)
      (not (empty? depends)) (assoc :dependencies depends))))
