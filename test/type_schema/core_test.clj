(ns type-schema.core-test
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [deftest is]]
   [fhir.schema.translate :as fhir-schema]
   [golden.core :as golden]
   [matcho.core :as matcho]
   [type-schema.core :as type-schema]
   [type-schema.package-index :as package]))

(deftest build-field-test
  (is (= {:array false
          :required true
          :excluded false}
         (type-schema/build-field {:required ["type"]}
                                  ["type"]
                                  {}))))

(deftest binding-test
  (is (= {:package "TEST",
          :name "AdministrativeGender",
          :kind "binding",
          :url "urn:fhir:binding:AdministrativeGender",
          :version "0.0.0"}
         (type-schema/build-binding
          {:package-meta {:name "TEST" :version "0.0.0"}}
          ["gender"]
          {:type "code",
           :binding {:strength "required",
                     :valueSet "http://hl7.org/fhir/ValueSet/administrative-gender|4.0.1",
                     :bindingName "AdministrativeGender"}})))

  (is (= {:kind "binding",
          :package "TEST",
          :version "0.0.0",
          :name "Patient.gender_binding",
          :url "http://hl7.org/fhir/StructureDefinition/Patient#gender_binding"}
         (type-schema/build-binding
          {:package-meta {:name "TEST" :version "0.0.0"}
           :name "Patient"
           :url "http://hl7.org/fhir/StructureDefinition/Patient"}
          ["gender"]
          {:type "code",
           :binding {:strength "required",
                     :valueSet "http://hl7.org/fhir/ValueSet/administrative-gender|4.0.1"}})))

  (matcho/match (type-schema/translate-binding
                 {:package-meta {:name "TEST" :version "0.0.0"}}
                 ["Patient" "gender"]
                 {:type "code",
                  :binding {:strength "required",
                            :valueSet "http://hl7.org/fhir/ValueSet/administrative-gender|4.0.1",
                            :bindingName "AdministrativeGender"}})
    {:identifier {:kind "binding", :name "AdministrativeGender"}
     :type       {:kind "primitive-type", :name "code"},
     :valueset   {:kind "value-set", :name "administrative-gender"}
     :enum ["male" "female" "other" "unknown"],
     :strength "required",
     :dependencies [{:kind "primitive-type", :name "code"}
                    {:kind "value-set", :name "administrative-gender"}]}))

(defn fhir-schema->type-schemas [fhir-schema-file]
  (-> (slurp fhir-schema-file)
      (json/parse-string true)
      (type-schema/translate-fhir-schema)))

(defn fhir-schema->type-schema [fhir-schema-file]
  (let [type-schemas (fhir-schema->type-schemas fhir-schema-file)]
    (assert (= (count type-schemas) 1) (str "Expected 1 type schema, but found " (count type-schemas)
                                            " type schemas in " fhir-schema-file))
    (first type-schemas)))

(deftest fhir-schemas-tests
  (golden/as-json
   "docs/examples/fhir-schema/coding.fs.json"
   (fhir-schema/translate (package/structure-definition "http://hl7.org/fhir/StructureDefinition/Coding")))

  (golden/as-json
   "docs/examples/fhir-schema/string.fs.json"
   (fhir-schema/translate (package/structure-definition "http://hl7.org/fhir/StructureDefinition/string"))))

(deftest fhir-schema->type-schema-small-golden-test
  (package/initialize! {:package-names   ["hl7.fhir.r4.core"]
                        :fhir-schema-fns ["test/golden/custom/TutorNotification.fs.json"
                                          "test/golden/custom/TutorNotificationTemplate.fs.json"]})

  (golden/as-json "docs/examples/string.ts.json"
                  (fhir-schema->type-schema "docs/examples/fhir-schema/string.fs.json"))

  (golden/as-json "docs/examples/coding.ts.json"
                  (fhir-schema->type-schema "docs/examples/fhir-schema/coding.fs.json"))

  (golden/as-json "docs/examples/resource-with-string.ts.json"
                  (fhir-schema->type-schema "docs/examples/fhir-schema/resource-with-string.fs.json"))

  (golden/as-json "docs/examples/resource-with-choice.ts.json"
                  (fhir-schema->type-schema "docs/examples/fhir-schema/resource-with-choice.fs.json"))

  (golden/as-json "docs/examples/resource-with-nested-type.ts.json"
                  (fhir-schema->type-schema "docs/examples/fhir-schema/resource-with-nested-type.fs.json"))

  (golden/as-json "docs/examples/resource-with-nested-type-2.ts.json"
                  (fhir-schema->type-schema "docs/examples/fhir-schema/resource-with-nested-type-2.fs.json"))

  (golden/as-jsons ["docs/examples/resource-with-codable-concept.ts.json"
                    "docs/examples/binding-MaritalStatus.ts.json"]
                   (fhir-schema->type-schemas "docs/examples/fhir-schema/resource-with-codable-concept.fs.json"))

  (golden/as-jsons ["docs/examples/resource-with-code.ts.json"
                    "docs/examples/binding-AdministrativeGender.ts.json"]
                   (fhir-schema->type-schemas "docs/examples/fhir-schema/resource-with-code.fs.json"))

  (golden/as-json "test/golden/custom/TutorNotificationTemplate.ts.json"
                  (fhir-schema->type-schema "test/golden/custom/TutorNotificationTemplate.fs.json"))

  (golden/as-jsons ["test/golden/custom/TutorNotification.ts.json"
                    "test/golden/custom/binding-TutorNotification#status.ts.json"
                    "test/golden/custom/binding-TutorNotification#type.ts.json"]
                   (fhir-schema->type-schemas "test/golden/custom/TutorNotification.fs.json")))

(defn value-set->json [fhir-schema-file]
  (-> (slurp fhir-schema-file)
      (json/parse-string true)
      (type-schema/translate-value-set)))

(deftest value-set->type-schema-golden-test
  (package/initialize! {:package-names ["hl7.fhir.r4.core"]})

  (golden/as-json "docs/examples/administrative-gender.ts.json"
                  (value-set->json "docs/examples/value-set/administrative-gender.json"))

  (golden/as-json "docs/examples/all-languages.ts.json"
                  (value-set->json "docs/examples/value-set/all-languages.json"))

  (golden/as-json "docs/examples/marital-status.ts.json"
                  (value-set->json "docs/examples/value-set/marital-status.json")))
