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

(defn- package-meta-fallback [fhir-schema]
  {:name (cond
           (and (some-> fhir-schema (str/starts-with? "http://hl7.org/fhir"))
                (some-> fhir-schema :version (str/starts-with? "4.")))
           "hl7.fhir.r4.core"

           (and (some-> fhir-schema (str/starts-with? "http://hl7.org/fhir"))
                (some-> fhir-schema :version (str/starts-with? "5.")))
           "hl7.fhir.r5.core"

           :else "undefined")
   :version (:version fhir-schema)})

(defn package-meta [fhir-schema]
  (or (:package-meta fhir-schema)
      (package-meta-fallback fhir-schema)))

(defn- build-nested-name [path]
  (->> path
       (map name)
       (str/join ".")))

(defn- build-nested-url [fhir-schema path]
  (str (:url fhir-schema) "#" (build-nested-name path)))

(defn get-identifier [fhir-schema]
  #_(assert (some? (:url fhir-schema)))
  (let [package-meta (package-meta fhir-schema)]
    {:kind    (derive-kind-from-schema fhir-schema)
     :package (:name package-meta)
     :version (:version package-meta)
     :name    (:id fhir-schema)
     :url     (:url fhir-schema)}))

(defn get-nested-identifier [fhir-schema path]
  #_(assert (some? (:url fhir-schema)))
  (let [package-meta (package-meta fhir-schema)]
    {:kind    "nested"
     :package (:name package-meta)
     :version (:version package-meta)
     :name    (build-nested-name path)
     :url     (build-nested-url fhir-schema path)}))

#_(defn build-element [element fhir-schema]
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

(defn- is-required? [fhir-schema path element]
  (contains? (-> (if (= 1 (count path)) fhir-schema element)
                 :required
                 (set))
             (last path)))

(defn- is-excluded? [fhir-schema path element]
  (contains? (-> (if (= 1 (count path)) fhir-schema element)
                 :excluded
                 (set))
             (last path)))

(defn build-field [fhir-schema path element]
  (let [type (some-> (:type element)
                     (get-fhir-schema)
                     (get-identifier))]
    (cond-> {:array    (true? (:array element))
             :required (is-required? fhir-schema path element)
             :excluded (is-excluded? fhir-schema path element)}
      (some? type)        (assoc :type type)
      (:choices element)  (assoc :choices (:choices element))
      (:choiceOf element) (assoc :choiceOf (:choiceOf element)))))

#_(defn build-backbone-element [element fhir-schema path]
    (let [;; FIXME:
          required (some #{name} (:required fhir-schema))]
      (-> (select-keys element [:array])
          (assoc-in [:type :kind] "nested")
          (assoc-in [:type :path] path)
          (cond-> required (assoc-in [:required] required)))))

(defn build-nested-field [fhir-schema path element]
  (let [package-meta (package-meta fhir-schema)]
    (cond-> {:type {:kind    "nested"
                    :package (:name package-meta)
                    :version (:version package-meta)
                    :name    (build-nested-name path)
                    :url     (build-nested-url fhir-schema path)}
             :array    (true? (:array element))
             :required (is-required? fhir-schema path element)
             :excluded (is-excluded? fhir-schema path element)})))

(defn iterate-over-elements [fhir-schema path elements]
  (->> elements
       (map (fn [[key element]]
              (let [path (conj path key)]
                (if (= (:type element) "BackboneElement")
                  [key (build-nested-field fhir-schema path element)]
                  [key (build-field fhir-schema path element)]))))
       (into {})))

(defn iterate-over-backbone-element [fhir-schema path elements]
  (->> elements
       (filter (fn [[_key element]] (= (:type element) "BackboneElement")))
       (map (fn [[key element]]
              (let [path (conj path key)

                    current
                    {:identifier (get-nested-identifier fhir-schema path)
                     :base       (some-> "BackboneElement" (get-fhir-schema) (get-identifier))
                     :fields     (iterate-over-elements fhir-schema path (:elements element))}

                    nested
                    (iterate-over-backbone-element fhir-schema path (:elements element))]
                (conj nested current))))
       (apply concat)
       (into [])))

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
        fields      (iterate-over-elements fhir-schema [] elements)

        nested      (iterate-over-backbone-element fhir-schema [] elements)

        depends
        (->> (concat [base]
                     (extract-dependencies fields)
                     #_(extract-dependencies-from-backbone-elements transformed-backbone-elements))
             (distinct)
             (sort-by #(get-in % [:idetifier :name]))
             (into []))]

    (cond-> {:identifier identifier}
      base                   (assoc :base base)
      description            (assoc :description description)
      (not (empty? fields))  (assoc :fields fields)
      (not (empty? nested))  (assoc :nested nested)
      (not (empty? depends)) (assoc :dependencies depends))))
