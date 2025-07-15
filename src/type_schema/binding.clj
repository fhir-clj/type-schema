(ns type-schema.binding
  (:require
   [type-schema.identifier :as identifier]
   [type-schema.log :as log]
   [type-schema.package-index :as package]
   [type-schema.value-set :as value-set]))

(defn build-enum
  "Extract enum values from an element with a value set binding"
  [element]
  (let [value-set-url (get-in element [:binding :valueSet])
        strength      (get-in element [:binding :strength])
        type          (get-in element [:type])
        value-set     (package/value-set (identifier/drop-version-from-url value-set-url))
        concepts      (value-set/value-set->concepts value-set)]
    (when (and (= strength "required")
               (= type "code")
               value-set)
      (->> concepts
           (mapv (fn [concept] (:code concept)))))))

(defn build-binding
  "Create a binding identifier for an element"
  [fhir-schema path element]
  (let [binding (:binding element)]
    (when-not (empty? binding)
      (let [value-set-url (:valueSet binding)
            value-set (package/value-set (identifier/drop-version-from-url value-set-url))]
        (when (nil? value-set)
          (log/warn "Unresolvable value-set:" value-set-url))
        (identifier/binding-type fhir-schema path element)))))

(defn generate-binding-type
  "Transform binding information into a schema format"
  [fhir-schema path element field-type]
  (let [value-set-url (get-in element [:binding :valueSet])
        valueset      (identifier/value-set-type value-set-url)]
    (->> {:identifier (identifier/binding-type fhir-schema path element)
          :type field-type
          :valueset valueset
          :strength (get-in element [:binding :strength])
          :enum (build-enum element)
          :dependencies (->> (concat (when (some? field-type) [field-type])
                                     [valueset])
                             (sort-by #(-> % :identifier :name)))}
         (remove (fn [[_ v]]
                   (when (seqable? v)
                     (empty? v))))
         (into {}))))
