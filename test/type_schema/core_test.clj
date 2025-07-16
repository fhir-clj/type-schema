(ns type-schema.core-test
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [deftest is testing]]
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

(deftest build-form-type-hierachy-test
  (package/initialize! {:package-names ["hl7.fhir.r4.core"]})

  (package/load-fhir-schema! {:url      "A"
                              :elements {:foo {:type  "string"
                                               :array true}}})
  (package/load-fhir-schema! {:base     "A"
                              :url      "B"
                              :required ["foo"]
                              :elements {:foo {:min 1
                                               :max 2}
                                         :bar {:type "code"}}})

  (matcho/match (type-schema/translate-fhir-schema
                 (package/fhir-schema "A"))
    [{:identifier   {:url "A"}
      :fields       {:foo {:excluded false,
                           :type     {:name "string"}
                           :array    true
                           :required false}}
      :dependencies [{:name "string"}]}
     nil])

  (matcho/match (type-schema/translate-fhir-schema
                 (package/fhir-schema "B"))
    [{:identifier   {:url "B"},
      :base         {:url "A"},
      :fields       {:foo {:type     {:name "string"}
                           :required true
                           :excluded false
                           :array    true
                           :min      1
                           :max      2}
                     :bar {:excluded false,
                           :type     {:name "code"}
                           :array    false
                           :required false}}
      :dependencies [{:name "code"}
                     {:name "string"}
                     nil]}
     nil]))

(deftest required-and-excluded-test
  (matcho/match (type-schema/build-field
                 {:elements {:topString {:type "code"}}
                  :required ["topString"]}
                 [:topString]
                 {:type "code"})
    {:type {:kind "primitive-type",
            :name "code"}
     :required true
     :excluded false})

  (matcho/match (type-schema/build-field
                 {:elements {:topString {:type "code"}}
                  :excluded ["topString"]}
                 [:topString]
                 {:type "code"})
    {:type {:kind "primitive-type",
            :name "code"}
     :required false
     :excluded true})

  (testing "for nested types"
    (matcho/match (type-schema/build-field
                   {:elements {:link
                               {:type "BackboneElement",
                                :elements {:someString {:type "string"}},
                                :required ["someString"]}}}
                   [:link :someString]
                   {:short "some description",
                    :type "string",
                    :isSummary true,
                    :index 10})
      {:type {:kind "primitive-type",
              :name "string"}
       :required true
       :excluded false})

    (matcho/match (type-schema/build-field
                   {:elements {:link
                               {:type "BackboneElement",
                                :elements {:someString {:type "string"}},
                                :excluded ["someString"]}}}
                   [:link :someString]
                   {:short "some description",
                    :type "string",
                    :isSummary true,
                    :index 10})
      {:type {:kind "primitive-type",
              :name "string"}
       :required false
       :excluded true})))

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

  (golden/as-json "docs/examples/with-cardinality.ts.json"
                  (fhir-schema->type-schema "docs/examples/fhir-schema/with-cardinality.fs.json"))

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
