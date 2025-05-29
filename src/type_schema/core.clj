(ns type-schema.core
  (:require [clojure.string :as str]
            [extract-enum]
            [type-schema.package-index :as package]
            [type-schema.value-set :as value-set]))

(defn type-to-url [type]
  ;; TODO: why don't we keep type as url? what if we reference type from another IG?
  (str "http://hl7.org/fhir/StructureDefinition/" type))

(defn derive-kind-from-schema [schema]
  (cond (= (:resourceType schema) "ValueSet") "valueset"
        (= (:derivation schema) "constraint") "constraint"
        (some? (:kind schema)) (:kind schema)
        :else "resource"))

(defn split-url-version [url-with-version]
  (if (string? url-with-version)
    (first (str/split url-with-version #"\|"))
    url-with-version))

(defn- package-meta-fallback [fhir-schema]
  {:name (cond
           (and (some-> fhir-schema :url (str/starts-with? "http://hl7.org/fhir"))
                (some-> fhir-schema :version (str/starts-with? "4.")))
           "hl7.fhir.r4.core"

           (and (some-> fhir-schema :url (str/starts-with? "http://hl7.org/fhir"))
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
     :name    (:name fhir-schema)
     :url     (:url fhir-schema)}))

(defn get-value-set-identifier [value-set]
  #_(assert (some? (:url value-set)))
  (let [package-meta (package-meta value-set)]
    {:kind    "value-set"
     :package (:name package-meta)
     :version (:version package-meta)
     :name    (:id value-set)
     :url     (:url value-set)}))

(defn get-nested-identifier [fhir-schema path]
  #_(assert (some? (:url fhir-schema)))
  (let [package-meta (package-meta fhir-schema)]
    {:kind    "nested"
     :package (:name package-meta)
     :version (:version package-meta)
     :name    (build-nested-name path)
     :url     (build-nested-url fhir-schema path)}))

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

(defn build-enum [element]
  (let [value-set-url (get-in element [:binding :valueSet])
        strength (get-in element [:binding :strength])
        type (get-in element [:type])
        value-set (package/index (split-url-version value-set-url))
        concepts (value-set/value-set->concepts (package/index) value-set)]
    (when (and (= strength "required") (= type "code") value-set)
      (mapv (fn [concept] (:code concept)) concepts))))

(defn get-binding-identifier [fhir-schema path element]
  (let [package-meta (package-meta fhir-schema)
        binding (:binding element)
        name (or (:bindingName binding)
                 (build-nested-name path))]
    {:kind    "binding"
     :package (:name package-meta)
     :version (:version package-meta)
     :name    name
     :url     (str "urn:fhir:binding:" name)}))

(defn build-binding [fhir-schema path element]
  (let [binding (:binding element)]
    (when (not (empty? binding))
      (let [value-set-url (:valueSet binding)
            value-set (package/index (split-url-version value-set-url))]
        (if (nil? value-set)
          (binding [*out* *err*]
            (println "WARN: unknown value set:" value-set-url))
          (get-binding-identifier fhir-schema path element))))))

(defn build-reference [element]
  (when (:refers element)
    (let [references (get-in element [:refers])]
      (->> references
           (map #(-> %
                     (package/fhir-schema-index)
                     (get-identifier)))))))

(defn remove-empty-vals [m]
  (->> m
       (remove (fn [[_ v]]
                 (when (seqable? v)
                   (empty? v))))
       (into {})))

(defn build-field-type [fhir-schema element]
  (or (some-> (type-to-url (:type element))
              (package/fhir-schema-index)
              (get-identifier))
      (when-let [fhir-schema-path (:elementReference element)]
        (get-nested-identifier fhir-schema
                               (->> fhir-schema-path
                                    (drop 1)
                                    (keep-indexed (fn [i key]
                                                    (when (odd? i)
                                                      key)))
                                    (map keyword)
                                    (into []))))))

(defn build-field [fhir-schema path element]
  (let [type (build-field-type fhir-schema element)]
    (remove-empty-vals {:type      type
                        :array     (true? (:array element))
                        :required  (is-required? fhir-schema path element)
                        :excluded  (is-excluded? fhir-schema path element)

                        :choices   (:choices element)
                        :choiceOf  (:choiceOf element)

                        :enum      (build-enum element)
                        :binding   (build-binding fhir-schema path element)
                        :reference (build-reference element)})))

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
                     :base       (some-> (type-to-url "BackboneElement")
                                         (package/fhir-schema-index)
                                         (get-identifier))
                     :fields     (iterate-over-elements fhir-schema path (:elements element))}

                    nested
                    (iterate-over-backbone-element fhir-schema path (:elements element))]
                (conj nested current))))
       (apply concat)
       (into [])))

(defn extract-dependencies [fields]
  (concat (->> fields
               (remove (fn [[_key element]] (= "nested" (:kind element))))
               (keep (fn [[_key element]] (:type element))))
          (->> fields
               (keep (fn [[_key element]] (get-in element [:binding]))))))

(defn extract-dependencies-from-nested [nested-types]
  (concat
   (->> nested-types
        (map (fn [nested-type]
               (extract-dependencies (:fields nested-type))))
        (apply concat))
   (when-let [base (get-in (first nested-types) [:base])]
     [base])))

(defn translate-binding [fhir-schema path element]
  (let [type          (build-field-type fhir-schema element)
        value-set-url (get-in element [:binding :valueSet])
        valueset (-> (package/index (split-url-version value-set-url))
                     (get-value-set-identifier))]
    (remove-empty-vals
     {:identifier (get-binding-identifier fhir-schema path element)
      :type type
      :valueset valueset
      :strength (get-in element [:binding :strength])
      :enum (build-enum element)
      :dependencies [type valueset]})))

(defn translate-fhir-schema [fhir-schema]
  (let [parent      (-> fhir-schema :base (package/fhir-schema-index))

        identifier  (get-identifier fhir-schema)
        base        (some-> parent
                            (get-identifier))
        description (:description fhir-schema)

        elements    (:elements fhir-schema)
        fields      (iterate-over-elements fhir-schema [] elements)

        nested      (iterate-over-backbone-element fhir-schema [] elements)

        binding-type-schemas
        (->> elements
             ;; FIXME: nested binding?
             (filter (fn [[_ename element]] (some? (:binding element))))
             (map (fn [[ename element]]
                    (translate-binding fhir-schema
                                       [(:name identifier) ename] element)))
             (sort-by #(get-in % [:identifier :name]))
             (distinct))

        depends
        (->> (concat (when base [base])
                     (extract-dependencies fields)
                     (extract-dependencies-from-nested nested))
             (distinct)
             (sort-by #(get-in % [:idetifier :name]))
             (into []))

        resource-type-schema
        (remove-empty-vals {:identifier identifier
                            :base base
                            :description description
                            :fields fields
                            :nested nested
                            :dependencies depends})]
    (cons resource-type-schema
          binding-type-schemas)))

(defn translate-value-set [value-set]
  (let [identifier  (get-value-set-identifier value-set)
        description (:description value-set)

        concepts    (value-set/value-set->concepts (package/index) value-set)

        compose     (when (empty? concepts)
                      (:compose value-set))
        ;; FIXME: collect deps
        depends     []]

    (remove-empty-vals {:identifier   identifier
                        :description  description
                        :concept      concepts
                        :compose      compose
                        :dependencies depends})))

(defn translate [resource]
  (cond
    (package/is-value-set? resource)
    [(translate-value-set resource)]

    :else
    (translate-fhir-schema resource)))

#_(main/process-package "hl7.fhir.r4.core@4.0.1" "output")
