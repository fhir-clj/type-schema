(ns type-schema.binding
  (:require
   [type-schema.identifier :as identifier]
   [type-schema.log :as log]
   [type-schema.package-index :as package]
   [type-schema.value-set :as value-set]))

(defn build-enum
  "Extract enum values from an element with a value set binding"
  [el-snapshot]
  (let [value-set-url (get-in el-snapshot [:binding :valueSet])
        strength      (get-in el-snapshot [:binding :strength])
        type          (get-in el-snapshot [:type])
        value-set     (package/value-set (identifier/drop-version-from-url value-set-url))
        concepts      (value-set/value-set->concepts value-set)]
    (when (and (= strength "required")
               (= type "code")
               value-set)
      (->> concepts
           (mapv (fn [concept] (:code concept)))))))

(defn build-binding
  "Create a binding identifier for an element"
  [fhir-schema path el-snapshot]
  (let [binding (:binding el-snapshot)]
    (when-not (empty? binding)
      (let [value-set-url (:valueSet binding)
            value-set (package/value-set (identifier/drop-version-from-url value-set-url))]
        (when (nil? value-set)
          (log/warn "Unresolvable value-set:" value-set-url))
        (identifier/binding-type fhir-schema path el-snapshot)))))

(defn generate-binding-type
  "Transform binding information into a schema format"
  [fhir-schema path el-snapshot field-type]
  (let [value-set-url (get-in el-snapshot [:binding :valueSet])
        valueset      (identifier/value-set-type value-set-url)]
    (->> {:identifier   (identifier/binding-type fhir-schema path el-snapshot)
          :type         field-type
          :valueset     valueset
          :strength     (get-in el-snapshot [:binding :strength])
          :enum         (build-enum el-snapshot)
          :dependencies (->> (concat (when (some? field-type) [field-type])
                                     [valueset])
                             (sort-by #(-> % :identifier :name)))}
         (remove (fn [[_ v]]
                   (when (seqable? v)
                     (empty? v))))
         (into {}))))
