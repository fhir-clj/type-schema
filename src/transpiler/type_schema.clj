(ns transpiler.type-schema
  (:require [transpiler.package-index :refer [get-fhir-schema get-valueset]]
            [extract-enum]
            [clojure.string :as str]))

(defn derive-kind-from-schema [schema]
  (cond (= (:resourceType schema) "ValueSet") "valueset"
        :else (:kind schema)))

(defn split-url-version [url-with-version]
  (if (and url-with-version (string? url-with-version))
    (first (str/split url-with-version #"\|")) url-with-version))

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

(defn get-valueset-identifier [value-set]
  #_(assert (some? (:url value-set)))
  (let [package-meta (package-meta value-set)]
    {:kind    "valueset"
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

#_(defn attach-enum [binding]
    (let [enum (get-valueset-concepts (split-url-version (:valueSet binding)))]
      (if enum (assoc binding :concept enum) binding)))

(defn build-binding [element]
  (when (:binding element)
    (let [strength (get-in element [:binding :strength])
          valueset-url (get-in element [:binding :valueSet])
          valueset (get-valueset (split-url-version valueset-url))]
      {:strength strength
       :valueset (get-valueset-identifier valueset)})))

(defn remove-empty-vals [m]
  (->> m
       (remove (fn [[_ v]]
                 (when (seqable? v)
                   (empty? v))))
       (into {})))

(defn build-field [fhir-schema path element]
  (let [type (or (some-> (:type element)
                         (get-fhir-schema)
                         (get-identifier))
                 (when-let [fhir-schema-path (:element-reference element)]
                   (get-nested-identifier fhir-schema
                                          (->> fhir-schema-path
                                               (drop 1)
                                               (keep-indexed (fn [i key]
                                                               (when (odd? i)
                                                                 key)))
                                               (map keyword)
                                               (into [])))))]
    (remove-empty-vals {:array    (true? (:array element))
                        :required (is-required? fhir-schema path element)
                        :excluded (is-excluded? fhir-schema path element)
                        :type     type
                        :choices  (:choices element)
                        :choiceOf (:choiceOf element)

                        :binding  (build-binding element)})))

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
                     :base       (some-> "BackboneElement"
                                         (get-fhir-schema)
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
               (keep (fn [[_key element]] (get-in element [:binding :valueset]))))))

(defn extract-dependencies-from-nested [nested-types]
  (->> nested-types
       (map (fn [nested-type]
              (extract-dependencies (:fields nested-type))))
       (apply concat)))

(defn translate [fhir-schema]
  (let [parent      (-> fhir-schema :base (get-fhir-schema))

        identifier  (get-identifier fhir-schema)
        base        (some-> parent
                            (get-identifier))
        description (:description fhir-schema)

        elements    (:elements fhir-schema)
        fields      (iterate-over-elements fhir-schema [] elements)

        nested      (iterate-over-backbone-element fhir-schema [] elements)

        depends
        (->> (concat [base]
                     (extract-dependencies fields)
                     (extract-dependencies-from-nested nested))
             (distinct)
             (sort-by #(get-in % [:idetifier :name]))
             (into []))]

    (remove-empty-vals {:identifier identifier
                        :base base
                        :description description
                        :fields fields
                        :nested nested
                        :dependencies depends})))
