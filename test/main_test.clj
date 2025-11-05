(ns main-test
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [deftest is testing]]
   [fhir.schema.translate :as fhir-schema]
   [golden.core :as golden]
   [main]
   [matcho.core :as matcho]
   [type-schema.core-test :refer [fhir-schema->type-schemas]]
   [type-schema.package-index :as package]))

(deftest smoke-test
  (is (= :ok (main/process-packages {:package-names ["hl7.fhir.r4.core@4.0.1"] :output-dir "output"})))
  (is (= :ok (main/process-packages {:package-names ["hl7.fhir.r5.core"] :output-dir "output"})))
  (is (= :ok (main/process-packages {:pacakge-names ["hl7.fhir.us.core@6.1.0"] :output-dir "output"}))))

(deftest fhir-schemas-actual-tests
  (package/initialize! {:package-names   ["hl7.fhir.r4.core"]})

  (golden/as-json
   "docs/examples/fhir-schema/coding.fs.json"
   (-> "http://hl7.org/fhir/StructureDefinition/Coding"
       (package/structure-definition)
       (fhir-schema/translate)))

  (golden/as-json
   "docs/examples/fhir-schema/string.fs.json"
   (-> "http://hl7.org/fhir/StructureDefinition/string"
       (package/structure-definition)
       (fhir-schema/translate)))

  (golden/as-json
   "test/golden/backbone-element/backbone-element.fs.json"
   (-> "http://hl7.org/fhir/StructureDefinition/BackboneElement"
       (package/structure-definition)
       (fhir-schema/translate)))

  (golden/as-json
   "test/golden/bundle/bundle.fs.json"
   (-> "http://hl7.org/fhir/StructureDefinition/Bundle"
       (package/structure-definition)
       (fhir-schema/translate)))

  (golden/as-json
   "test/golden/capability-statement/capability-statement.fs.json"
   (-> "http://hl7.org/fhir/StructureDefinition/CapabilityStatement"
       (package/structure-definition)
       (fhir-schema/translate)))

  (golden/as-json
   "test/golden/element/element.fs.json"
   (-> "http://hl7.org/fhir/StructureDefinition/Element"
       (package/structure-definition)
       (fhir-schema/translate)))

  (golden/as-json
   "test/golden/patient/patient.fs.json"
   (-> "http://hl7.org/fhir/StructureDefinition/Patient"
       (package/structure-definition)
       (fhir-schema/translate)))

  (golden/as-json
   "test/golden/questionnaire/questionnaire.fs.json"
   (-> "http://hl7.org/fhir/StructureDefinition/Questionnaire"
       (package/structure-definition)
       (fhir-schema/translate))))

(deftest fhir-schema->type-schema-realworld-golden-test
  (package/initialize! {:package-names   ["hl7.fhir.r4.core"]})

  (golden/as-jsons ["test/golden/element/element.ts.json"]
                   (fhir-schema->type-schemas "test/golden/element/element.fs.json"))

  (golden/as-jsons ["test/golden/backbone-element/backbone-element.ts.json"]
                   (fhir-schema->type-schemas "test/golden/backbone-element/backbone-element.fs.json"))

  (golden/as-jsons ["test/golden/bundle/bundle.ts.json"
                    "test/golden/bundle/binding-BundleType.ts.json"
                    "test/golden/bundle/binding-HTTPVerb.ts.json"
                    "test/golden/bundle/binding-SearchEntryMode.ts.json"]
                   (fhir-schema->type-schemas "test/golden/bundle/bundle.fs.json"))

  (golden/as-jsons ["test/golden/patient/patient.ts.json"
                    "test/golden/patient/binding-AdministrativeGender.ts.json"
                    "test/golden/patient/binding-ContactRelationship.ts.json"
                    "test/golden/patient/binding-Language.ts.json"
                    "test/golden/patient/binding-LinkType.ts.json"
                    "test/golden/patient/binding-MaritalStatus.ts.json"]
                   (fhir-schema->type-schemas "test/golden/patient/patient.fs.json"))

  (golden/as-jsons ["test/golden/capability-statement/capability-statement.ts.json"
                    "test/golden/capability-statement/binding-CapabilityStatementKind.ts.json"
                    "test/golden/capability-statement/binding-ConditionalDeleteStatus.ts.json"
                    "test/golden/capability-statement/binding-ConditionalReadStatus.ts.json"
                    "test/golden/capability-statement/binding-DocumentMode.ts.json"
                    "test/golden/capability-statement/binding-EventCapabilityMode.ts.json"
                    "test/golden/capability-statement/binding-FHIRVersion.ts.json"
                    "test/golden/capability-statement/binding-Jurisdiction.ts.json"
                    "test/golden/capability-statement/binding-MessageTransport.ts.json"
                    "test/golden/capability-statement/binding-MimeType.ts.json"
                    "test/golden/capability-statement/binding-PublicationStatus.ts.json"
                    "test/golden/capability-statement/binding-ReferenceHandlingPolicy.ts.json"
                    "test/golden/capability-statement/binding-ResourceType.ts.json"
                    "test/golden/capability-statement/binding-ResourceVersionPolicy.ts.json"
                    "test/golden/capability-statement/binding-RestfulCapabilityMode.ts.json"
                    "test/golden/capability-statement/binding-RestfulSecurityService.ts.json"
                    "test/golden/capability-statement/binding-SearchParamType.ts.json"
                    "test/golden/capability-statement/binding-SystemRestfulInteraction.ts.json"
                    "test/golden/capability-statement/binding-TypeRestfulInteraction.ts.json"]
                   (fhir-schema->type-schemas "test/golden/capability-statement/capability-statement.fs.json"))

  (golden/as-jsons ["test/golden/questionnaire/questionnaire.ts.json"
                    "test/golden/questionnaire/binding-EnableWhenBehavior.ts.json"
                    "test/golden/questionnaire/binding-Jurisdiction.ts.json"
                    "test/golden/questionnaire/binding-PublicationStatus.ts.json"
                    "test/golden/questionnaire/binding-QuestionnaireConcept.ts.json"
                    "test/golden/questionnaire/binding-QuestionnaireItemOperator.ts.json"
                    "test/golden/questionnaire/binding-QuestionnaireItemType.ts.json"
                    "test/golden/questionnaire/binding-ResourceType.ts.json"]
                   (fhir-schema->type-schemas "test/golden/questionnaire/questionnaire.fs.json")))

;; CLI flag tests

(deftest cli-options-parsing-test
  (testing "CLI options are correctly parsed"
    (let [result (main/validate-args ["--include-profile-constraints"
                                       "--include-field-docs"
                                       "hl7.fhir.r4.core"])]
      (is (true? (get-in result [:options :include-profile-constraints?])))
      (is (true? (get-in result [:options :include-field-docs?])))
      (is (= ["hl7.fhir.r4.core"] (get result :package-name))))))

(deftest profile-constraints-flag-integration-test
  (testing "Profile constraints flag works with Norwegian Basis profiles"
    ;; IMPORTANT: Both packages needed - Norwegian profiles reference base FHIR types
    (package/initialize! {:package-names ["hl7.fhir.r4.core" "hl7.fhir.no.basis@2.2.2"]})

    ;; Test that the flag is accepted and processes both packages without error
    (is (= :ok (main/process-packages {:package-names ["hl7.fhir.r4.core" "hl7.fhir.no.basis@2.2.2"]
                                        :include-profile-constraints? true
                                        :output-dir "output"})))

    (testing "Flag is accepted in CLI parsing"
      (let [result (main/validate-args ["--include-profile-constraints"
                                         "hl7.fhir.r4.core"
                                         "hl7.fhir.no.basis@2.2.2"])]
        (is (true? (get-in result [:options :include-profile-constraints?])))))))

(deftest field-docs-flag-integration-test
  (testing "Field documentation flag produces output with documentation fields"
    (package/initialize! {:package-names ["hl7.fhir.r4.core"]})

    ;; Capture output with flag enabled
    (let [output (with-out-str
                   (main/process-packages {:package-names ["hl7.fhir.r4.core"]
                                           :include-field-docs? true}))
          type-schemas (->> (clojure.string/split-lines output)
                            (filter not-empty)
                            (map json/parse-string))
          patient-schema (->> type-schemas
                              (filter #(= "Patient" (get-in % ["identifier" "name"])))
                              (first))]

      (testing "Patient schema is found"
        (is (some? patient-schema)))

      (testing "Patient.gender has documentation fields"
        (let [gender-field (get-in patient-schema ["fields" "gender"])]
          (is (some? (get gender-field "short")))
          (is (= "male | female | other | unknown" (get gender-field "short"))))))))

(deftest both-flags-disabled-by-default-integration-test
  (testing "Without flags, profile constraints and docs are NOT included (backward compatibility)"
    (package/initialize! {:package-names ["hl7.fhir.r4.core"]})

    ;; Capture output WITHOUT flags
    (let [output (with-out-str
                   (main/process-packages {:package-names ["hl7.fhir.r4.core"]}))
          type-schemas (->> (clojure.string/split-lines output)
                            (filter not-empty)
                            (map json/parse-string))
          patient-schema (->> type-schemas
                              (filter #(= "Patient" (get-in % ["identifier" "name"])))
                              (first))]

      (testing "Patient schema is found"
        (is (some? patient-schema)))

      (testing "Patient.name has NO profileConstraints by default"
        (let [name-field (get-in patient-schema ["fields" "name"])]
          (is (nil? (get name-field "profileConstraints")))))

      (testing "Patient.name has NO documentation fields by default"
        (let [name-field (get-in patient-schema ["fields" "name"])]
          (is (nil? (get name-field "short")))
          (is (nil? (get name-field "definition"))))))))
