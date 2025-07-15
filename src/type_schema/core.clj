(ns type-schema.core
  (:require
   [type-schema.binding :as binding]
   [type-schema.identifier :as identifier]
   [type-schema.log :as log]
   [type-schema.package-index :as package]
   [type-schema.value-set :as value-set]))

(defn ensure-url [type-name]
  (if (re-matches #".*/.*" type-name)
    type-name
    (or (:url (package/fhir-schema type-name))
        (str "http://hl7.org/fhir/StructureDefinition/" type-name))))

(defn- is-required? [fhir-schema path]
  (let [required (-> (get-in fhir-schema (->> (drop-last path)
                                              (map (fn [k] [:elements k]))
                                              (apply concat)))
                     :required
                     (set))
        elem-name (name (last path))]
    (contains? required elem-name)))

(defn- is-excluded? [fhir-schema path]
  (let [required (-> (get-in fhir-schema (->> (drop-last path)
                                              (map (fn [k] [:elements k]))
                                              (apply concat)))
                     :excluded
                     (set))
        elem-name (name (last path))]
    (contains? required elem-name)))

(defn build-reference [element]
  (when (:refers element)
    (let [references (get-in element [:refers])]
      (->> references
           (map (fn [refer]
                  (if-let [fhir-schema (package/fhir-schema refer)]
                    (identifier/schema-type fhir-schema)
                    (identifier/schema-type {:url  refer
                                             :name refer}))))))))

(defn remove-empty-vals [m]
  (->> m
       (remove (fn [[_ v]]
                 (when (seqable? v)
                   (empty? v))))
       (into {})))

(defn build-field-type [fhir-schema path element]
  (let [url (some-> element :type (ensure-url))]
    (or (some-> (or url
                    (:defaultType element))
                (package/fhir-schema)
                (identifier/schema-type))
        (when-let [fhir-schema-path (:elementReference element)]
          (identifier/nested-type fhir-schema
                                  (->> fhir-schema-path
                                       (drop 1)
                                       (keep-indexed (fn [i key]
                                                       (when (odd? i)
                                                         key)))
                                       (map keyword)
                                       (into []))))

        (when (and (nil? (:type element))
                   (some? (:base fhir-schema)))
          (let [bases ((fn collect-bases [fs]
                         (when-let [base (some-> fs
                                                 :base
                                                 package/fhir-schema)]
                           (cons base (collect-bases base))))
                       fhir-schema)
                base-elems (->> bases
                                (keep (fn [base]
                                        {:fs base
                                         :e (reduce (fn [st elem-name] (get-in st [:elements elem-name]))
                                                    base
                                                    path)}))
                                (filter #(-> % :e :type)))
                base-elem (first base-elems)]
            (when (< 1 (count (distinct base-elems)))
              (log/warn :multiple-base-types (:name fhir-schema) path (count base-elems)))

            (when (some? base-elem)
              (build-field-type (:fs base-elem) path (:e base-elem)))))

        (when (and (= (:kind fhir-schema) "logical")
                   (nil? (:type element))
                   (not (contains? element :choices)))
          (log/warn :force-default-type-for-logic-model (:name fhir-schema) path element)
          (-> (package/fhir-schema "string")
              (identifier/schema-type))))))

(defn build-field [fhir-schema path element]
  (let [type (build-field-type fhir-schema path element)]
    (remove-empty-vals {:type      type
                        :array     (true? (:array element))
                        :required  (is-required? fhir-schema path)
                        :excluded  (is-excluded? fhir-schema path)

                        :min (:min element)
                        :max (:max element)

                        :choices   (:choices element)
                        :choiceOf  (:choiceOf element)

                        :enum      (binding/build-enum element)
                        :binding   (binding/build-binding fhir-schema path element)
                        :reference (build-reference element)})))

(defn build-nested-field [fhir-schema path element]
  {:type     (identifier/nested-type fhir-schema path)
   :array    (true? (:array element))
   :required (is-required? fhir-schema path)
   :excluded (is-excluded? fhir-schema path)})

(defn is-nested-element? [element]
  (or (= (:type element) "BackboneElement")
      (< 0 (count (:elements element)))))

(defn iterate-over-elements [fhir-schema path elements]
  (->> elements
       (map (fn [[key element]]
              (let [path (conj path key)]
                (if (is-nested-element? element)
                  [key (build-nested-field fhir-schema path element)]
                  [key (build-field fhir-schema path element)]))))
       (into {})))

(defn collect-nested-elements [fhir-schema path elements]
  (->> elements
       (map (fn [[key element]]
              (let [path (conj path key)
                    nested (when (is-nested-element? element)
                             (collect-nested-elements fhir-schema path (:elements element)))]
                (cons [path element] nested))))
       (apply concat)
       (into [])))

(defn iterate-over-backbone-element [fhir-schema path elements]
  (->> (collect-nested-elements fhir-schema path elements)
       (filter (fn [[_path element]] (is-nested-element? element)))
       (map (fn [[path element]]
              {:identifier (identifier/nested-type fhir-schema path)
               :base       (some-> (ensure-url "BackboneElement")
                                   (package/fhir-schema)
                                   (identifier/schema-type))
               :fields     (iterate-over-elements fhir-schema path (:elements element))}))
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
        (keep (fn [nested-type]
                (extract-dependencies (:fields nested-type))))
        (apply concat))
   (when-let [base (get-in (first nested-types) [:base])]
     [base])))

(defn collect-binding-schemas
  "Collect all binding schemas from a FHIR schema"
  [fhir-schema deep-nested-elements]
  (->> (deep-nested-elements fhir-schema [] (:elements fhir-schema))
       (keep (fn [[path element]]
               (when (some? (:binding element))
                 (binding/generate-binding-type fhir-schema path element
                                                (build-field-type fhir-schema path element)))))
       (sort-by #(get-in % [:identifier :name]))
       (distinct)))

(defn translate-fhir-schema [fhir-schema]
  (let [parent      (-> fhir-schema :base (package/fhir-schema))

        identifier  (identifier/schema-type fhir-schema)
        base        (when parent
                      (identifier/schema-type parent))
        description (:description fhir-schema)

        elements    (:elements fhir-schema)
        fields      (iterate-over-elements fhir-schema [] elements)

        nested      (->> (iterate-over-backbone-element fhir-schema [] elements)
                         (sort-by #(-> % :identifier :url)))

        binding-type-schemas
        (collect-binding-schemas fhir-schema collect-nested-elements)

        depends
        (->> (concat (when base [base])
                     (extract-dependencies fields)
                     (extract-dependencies-from-nested nested))
             (distinct)
             (sort-by #(get-in % [:name]))
             (remove #(-> % :name (= (:name identifier))))
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
  (let [identifier  (identifier/value-set-type (:url value-set))
        description (:description value-set)

        concepts    (value-set/value-set->concepts value-set)

        compose     (when (empty? concepts)
                      (:compose value-set))
        ;; FIXME: collect deps
        depends     []]

    (remove-empty-vals {:identifier   identifier
                        :description  description
                        :concept      concepts
                        :compose      compose
                        :dependencies depends})))

#_(main/process-package "hl7.fhir.r4.core@4.0.1" "output")
