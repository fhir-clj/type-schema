(ns type-schema.core
  (:require
   [clojure.string :as str]
   [extract-enum]
   [type-schema.log :as log]
   [type-schema.package-index :as package]
   [type-schema.value-set :as value-set]))

(defn ensure-url [type-name]
  (if (re-matches #".*/.*" type-name)
    type-name
    (or (:url (package/fhir-schema type-name))
        (str "http://hl7.org/fhir/StructureDefinition/" type-name))))

(defn derive-kind-from-schema [schema]
  (cond (= (:resourceType schema) "ValueSet") "valueset"
        (= (:derivation schema) "constraint") "constraint"
        (some? (:kind schema)) (:kind schema)
        :else "resource"))

(defn drop-version-from-url [url-with-version]
  (if (string? url-with-version)
    (first (str/split url-with-version #"\|"))
    url-with-version))

;; FIXME: Move it to package-index
(defn- package-meta-fallback [fhir-schema]
  (cond
    (and (some-> fhir-schema :url (str/starts-with? "http://hl7.org/fhir"))
         (some-> fhir-schema :version (str/starts-with? "4.")))
    {:name    "hl7.fhir.r4.core"
     :version (:version fhir-schema)}

    (and (some-> fhir-schema :url (str/starts-with? "http://hl7.org/fhir"))
         (some-> fhir-schema :version (str/starts-with? "5.")))
    {:name    "hl7.fhir.r5.core"
     :version (:version fhir-schema)}

    :else
    {:name    "not-specified"
     :version "not-specified"}))

(defn package-meta [fhir-schema]
  (or (:package-meta fhir-schema)
      (package/package-meta (:url fhir-schema))
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

(defn get-value-set-identifier [curl]
  (let [curl         (drop-version-from-url curl)
        value-set    (package/value-set curl)
        package-meta (package-meta value-set)]
    (if (some? value-set)
      {:kind    "value-set"
       :url     curl
       :name    (:id value-set)
       :package (:name package-meta)
       :version (:version package-meta)}
      {:kind "value-set"
       :url  curl})))

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
        strength      (get-in element [:binding :strength])
        type          (get-in element [:type])
        value-set     (package/value-set (drop-version-from-url value-set-url))
        concepts      (value-set/value-set->concepts value-set)]
    (when (and (= strength "required")
               (= type "code")
               value-set)
      (->> concepts
           (mapv (fn [concept] (:code concept)))))))

(defn get-binding-identifier [fhir-schema path element]
  (let [package-meta (package-meta fhir-schema)
        binding (:binding element)
        name (or (:bindingName binding)
                 (str (:name fhir-schema) "." (build-nested-name path) "_binding"))]
    {:kind    "binding"
     :package (:name package-meta)
     :version (:version package-meta)
     :name    name
     :url     (if (:bindingName binding)
                (str "urn:fhir:binding:" name)
                (str (:url fhir-schema) "#" (build-nested-name path) "_binding"))}))

(defn build-binding [fhir-schema path element]
  (let [binding (:binding element)]
    (when (not (empty? binding))
      (let [value-set-url (:valueSet binding)
            value-set (package/value-set (drop-version-from-url value-set-url))]
        (when (nil? value-set)
          (log/warn "Unresolvable value-set:" value-set-url))
        (get-binding-identifier fhir-schema path element)))))

(defn build-reference [element]
  (when (:refers element)
    (let [references (get-in element [:refers])]
      (->> references
           (map (fn [refer]
                  (if-let [fhir-schema (package/fhir-schema refer)]
                    (get-identifier fhir-schema)
                    (get-identifier {:url  refer
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
                (get-identifier))
        (when-let [fhir-schema-path (:elementReference element)]
          (get-nested-identifier fhir-schema
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
              (get-identifier))))))

(defn build-field [fhir-schema path element]
  (let [type (build-field-type fhir-schema path element)]
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

(defn deep-nested-elements [fhir-schema path elements]
  (->> elements
       (map (fn [[key element]]
              (let [path (conj path key)
                    nested (when (is-nested-element? element)
                             (deep-nested-elements fhir-schema path (:elements element)))]
                (cons [path element] nested))))
       (apply concat)
       (into [])))

(defn iterate-over-backbone-element [fhir-schema path elements]
  (->> (deep-nested-elements fhir-schema path elements)
       (filter (fn [[_path element]] (is-nested-element? element)))
       (map (fn [[path element]]
              {:identifier (get-nested-identifier fhir-schema path)
               :base       (some-> (ensure-url "BackboneElement")
                                   (package/fhir-schema)
                                   (get-identifier))
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

(defn translate-binding [fhir-schema path element]
  (let [type          (build-field-type fhir-schema path element)
        value-set-url (get-in element [:binding :valueSet])
        valueset      (get-value-set-identifier value-set-url)]
    (remove-empty-vals
     {:identifier (get-binding-identifier fhir-schema path element)
      :type type
      :valueset valueset
      :strength (get-in element [:binding :strength])
      :enum (build-enum element)
      :dependencies (->> (concat (when (some? type) [type])
                                 [valueset])
                         (sort-by #(-> % :identifier :name)))})))

(defn translate-fhir-schema [fhir-schema]
  (let [parent      (-> fhir-schema :base (package/fhir-schema))

        identifier  (get-identifier fhir-schema)
        base        (when parent
                      (get-identifier parent))
        description (:description fhir-schema)

        elements    (:elements fhir-schema)
        fields      (iterate-over-elements fhir-schema [] elements)

        nested      (->> (iterate-over-backbone-element fhir-schema [] elements)
                         (sort-by #(-> % :identifier :url)))

        binding-type-schemas
        (->> (deep-nested-elements fhir-schema [] elements)
             (keep (fn [[path element]]
                     (when (some? (:binding element))
                       (translate-binding fhir-schema path element))))
             (sort-by #(get-in % [:identifier :name]))
             (distinct))

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
  (let [identifier  (get-value-set-identifier (:url value-set))
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
