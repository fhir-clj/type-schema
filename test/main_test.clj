(ns main-test
  (:require
   [clojure.test :refer [deftest is]]
   [fhir.schema.translate :as fhir-schema]
   [golden.core :as golden]
   [main]
   [type-schema.core-test :refer [fhir-schema->type-schemas]]
   [type-schema.package-index :as package]))

(deftest smoke-test
  (is (= :ok (main/process-package "hl7.fhir.r4.core@4.0.1" {:output-dir "output"})))
  (is (= :ok (main/process-package "hl7.fhir.r5.core" {:output-dir "output"})))
  (is (= :ok (main/process-package "hl7.fhir.us.core@6.1.0" {:output-dir "output"}))))

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
