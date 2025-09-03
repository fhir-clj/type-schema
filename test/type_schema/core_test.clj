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

(deftest constraint-generation-test
  (package/initialize! {:package-names ["hl7.fhir.r4.core"]})

  (package/load-fhir-schema! {:url      "A"
                              :derivation "specialization"
                              :name     "a"
                              :elements {:foo
                                         {:type     "BackboneElement",
                                          :elements {:bar {:type "string"}}}}})

  (matcho/match (type-schema/translate-fhir-schema
                 (package/fhir-schema "A"))
    [{:identifier   {:kind "resource",
                     :name "a",
                     :url  "A"},
      :fields       {:foo {:type {:kind "nested",
                                  :name "foo",
                                  :url  "A#foo"}}},
      :nested       [{:identifier {:kind "nested",
                                   :name "foo",
                                   :url  "A#foo"},
                      :base       {:kind "complex-type",
                                   :name "BackboneElement",
                                   :url  "http://hl7.org/fhir/StructureDefinition/BackboneElement"},
                      :fields     {:bar {:type {:kind "primitive-type",
                                                :name "string",
                                                :url  "http://hl7.org/fhir/StructureDefinition/string"}}}}],
      :dependencies [{:kind "complex-type",
                      :name "BackboneElement",
                      :url  "http://hl7.org/fhir/StructureDefinition/BackboneElement"}
                     {:kind "primitive-type",
                      :name "string",
                      :url  "http://hl7.org/fhir/StructureDefinition/string"}
                     nil]}
     nil])

  (package/load-fhir-schema! {:base       "A"
                              :url        "B"
                              :name       "b"
                              :derivation "constraint"
                              :elements   {:foo {:min 1}}})

  (matcho/match (type-schema/translate-fhir-schema
                 (package/fhir-schema "B"))
    [{:identifier   {:kind "constraint",
                     :name "b",
                     :url  "B"},
      :base         {:kind "resource",
                     :name "a",
                     :url  "A"},
      :fields       {:foo {:type {:kind "nested",
                                  :name "foo",
                                  :url  "A#foo"}}}
      :nested       nil?,
      :dependencies [{:kind "resource",
                      :name "a",
                      :url "A"}
                     {:kind "nested",
                      :name "foo",
                      :url "A#foo"}
                     nil]}
     nil])

  (package/load-fhir-schema! {:base       "B"
                              :url        "C"
                              :name       "c"
                              :derivation "constraint"
                              :elements   {:foo {:max 1}}})

  (matcho/match (type-schema/translate-fhir-schema
                 (package/fhir-schema "C"))
    [{:identifier   {:kind "constraint",
                     :name "c",
                     :url  "C"},
      :base         {:kind "constraint",
                     :name "b",
                     :url  "B"},
      :fields       {:foo {:type {:kind "nested",
                                  :name "foo",
                                  :url  "A#foo"}}}
      :nested       nil?,
      :dependencies [{:kind "constraint",
                      :name "b",
                      :url "B"}
                     {:kind "nested",
                      :name "foo",
                      :url "A#foo"}
                     nil]}
     nil]))

(deftest choice-type-test
  (package/initialize! {:package-names ["hl7.fhir.r4.core"]})

  (package/load-fhir-schema!
   {:url      "OptionalChoice"
    :kind     "resource"
    :elements {:deceasedDateTime {:type     "dateTime"
                                  :choiceOf "deceased"}
               :deceasedBoolean  {:type "boolean"}
               :deceased         {:choices ["deceasedBoolean" "deceasedDateTime"]}}})

  (matcho/match (type-schema/translate-fhir-schema
                 (package/fhir-schema "OptionalChoice"))
    [{:identifier   {:url "OptionalChoice"}
      :fields       {:deceasedDateTime {:excluded false,
                                        :type     {:name "dateTime"},
                                        :array    false,
                                        :choiceOf "deceased",
                                        :required false},
                     :deceasedBoolean  {:excluded false,
                                        :type     {:name "boolean"},
                                        :array    false,
                                        :required false},
                     :deceased         {:excluded false,
                                        :choices  ["deceasedBoolean" "deceasedDateTime"],
                                        :array    false,
                                        :required false}},
      :dependencies [{:name "boolean"}
                     {:name "dateTime"}]}
     nil])

  (package/load-fhir-schema!
   {:url      "RequiredChoice"
    :kind     "resource"
    :required ["deceased"]
    :elements {:deceased         {:choices ["deceasedBoolean" "deceasedDateTime"]}
               :deceasedDateTime {:type     "dateTime"
                                  :choiceOf "deceased"}
               :deceasedBoolean  {:type "boolean"}}})

  (matcho/match (type-schema/translate-fhir-schema
                 (package/fhir-schema "RequiredChoice"))
    [{:identifier   {:url "RequiredChoice"}
      :fields       {:deceased         {:excluded false
                                        :choices  ["deceasedBoolean" "deceasedDateTime"]
                                        :required true}
                     :deceasedDateTime {:choiceOf "deceased"
                                        :type     {:name "dateTime"}
                                        :excluded false
                                        :required false}
                     :deceasedBoolean  {:type     {:name "boolean"}
                                        :excluded false
                                        :required false}}
      :dependencies [{:name "boolean"}
                     {:name "dateTime"}]}
     nil])

  (testing "remove choice options. Base have 2 options, child - 1 option"
    (package/load-fhir-schema!
     {:url      "RequiredChoiceLimited"
      :base     "RequiredChoice"
      :kind     "resource"
      :elements {:deceased         {:choices ["deceasedBoolean"]}}})

    (matcho/match (type-schema/translate-fhir-schema
                   (package/fhir-schema "RequiredChoiceLimited"))
      [{:identifier   {:url "RequiredChoiceLimited"}
        :fields       {:deceased         {:excluded false
                                          :choices  ["deceasedBoolean"]
                                          :required true}
                       :deceasedBoolean  {:type     {:name "boolean"}
                                          :excluded false
                                          :required false}}
        :dependencies [{:name "boolean"}]}
       nil])))

(deftest build-form-type-hierachy-test
  (package/initialize! {:package-names ["hl7.fhir.r4.core"]})

  (package/load-fhir-schema! {:url      "A"
                              :elements {:foo {:type  "string"
                                               :array true}}})
  (package/load-fhir-schema! {:base     "A"
                              :url      "B"
                              :required ["foo"]
                              :elements {:foo {:min 1}
                                         :bar {:type "code"}}})

  (package/load-fhir-schema! {:base     "B"
                              :url      "C"
                              :required ["bar" "baz"]
                              :elements {:foo {:max 2}
                                         :baz {:type "string"}}})

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
                           :min      1}
                     :bar {:excluded false,
                           :type     {:name "code"}
                           :array    false
                           :required false}}
      :dependencies [{:name "code"}
                     {:name "string"}
                     nil]}
     nil])

  (matcho/match (type-schema/translate-fhir-schema
                 (package/fhir-schema "C"))
    [{:identifier {:url "C"}
      :base       {:url "B"}
      :fields     {:foo {:excluded false,
                         :type     {:name "string"},
                         :array    true,
                         :min      1,
                         :max      2,
                         :required true}
                   :baz {:excluded false,
                         :type     {:name "string"},
                         :array    false,
                         :required true}

                   ;; FIXME: we should see it in C because of :required
                   :bar nil #_{:excluded false,
                               :type     {:name "code"}
                               :array    false
                               :required true}},
      :dependencies
      [{:kind    "primitive-type",
        :package "hl7.fhir.r4.core",
        :version "4.0.1",
        :name    "string",
        :url     "http://hl7.org/fhir/StructureDefinition/string"}]}
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
