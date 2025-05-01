(ns main-test
  (:require
   [clojure.test :refer [deftest is]]
   [golden.core :as golden]
   [main]
   [type-schema.core-test :refer [fhir-schema->type-schemas]]
   [type-schema.package-index :as package]))

(deftest smoke-test
  (is (= :ok (main/process-package "hl7.fhir.r4.core@4.0.1" "output")))
  (is (= :ok (main/process-package "hl7.fhir.r5.core" "output")))
  (is (= :ok (main/process-package "hl7.fhir.us.core@6.1.0" "output"))))

(deftest fhir-schema->type-schema-realworld-golden-test
  (package/init-from-package! "hl7.fhir.r4.core")

  (golden/as-jsons ["test/golden/element/element.ts.json"]
                   (fhir-schema->type-schemas "test/golden/element/element.fs.json"))

  (golden/as-jsons ["test/golden/backbone-element/backbone-element.ts.json"]
                   (fhir-schema->type-schemas "test/golden/backbone-element/backbone-element.fs.json"))

  (golden/as-jsons ["test/golden/bundle/bundle.ts.json"]
                   (fhir-schema->type-schemas "test/golden/bundle/bundle.fs.json"))

  (golden/as-jsons ["test/golden/patient/patient.ts.json"]
                   (fhir-schema->type-schemas "test/golden/patient/patient.fs.json"))

  (golden/as-jsons ["test/golden/capability-statement/capability-statement.ts.json"]
                   (fhir-schema->type-schemas "test/golden/capability-statement/capability-statement.fs.json"))

  (golden/as-jsons ["test/golden/questionnaire/questionnaire.ts.json"]
                   (fhir-schema->type-schemas "test/golden/questionnaire/questionnaire.fs.json")))
