(ns type-schema.core-test
  (:require [cheshire.core :as json]
            [clojure.test :refer [deftest is]]
            [golden.core :as golden]
            [type-schema.core :as type-schema]
            [type-schema.package-index :as package]))

(deftest build-field-test
  (is (= {:array false
          :required true
          :excluded false}
         (type-schema/build-field {:required ["type"]}
                                  ["type"]
                                  {}))))

(defn fhir-schema->json [fhir-schema-file]
  (-> (slurp fhir-schema-file)
      (json/parse-string true)
      (type-schema/translate)))

(deftest fhir-schema->type-schema-golden-test
  (package/init-from-package! "hl7.fhir.r4.core")

  (golden/vs-json "test/golden/element.ts.json"
                  (fhir-schema->json "test/golden/element.fs.json"))

  (golden/vs-json "test/golden/backbone-element.ts.json"
                  (fhir-schema->json "test/golden/backbone-element.fs.json"))

  (golden/vs-json "test/golden/bundle.ts.json"
                  (fhir-schema->json "test/golden/bundle.fs.json"))

  (golden/vs-json "docs/examples/string.ts.json"
                  (fhir-schema->json "docs/examples/fhir-schema/string.fs.json"))

  (golden/vs-json "docs/examples/coding.ts.json"
                  (fhir-schema->json "docs/examples/fhir-schema/coding.fs.json"))

  (golden/vs-json "docs/examples/resource-with-string.ts.json"
                  (fhir-schema->json "docs/examples/fhir-schema/resource-with-string.fs.json"))

  (golden/vs-json "docs/examples/resource-with-choice.ts.json"
                  (fhir-schema->json "docs/examples/fhir-schema/resource-with-choice.fs.json"))

  (golden/vs-json "docs/examples/resource-with-nested-type.ts.json"
                  (fhir-schema->json "docs/examples/fhir-schema/resource-with-nested-type.fs.json"))

  (golden/vs-json "docs/examples/resource-with-codable-concept.ts.json"
                  (fhir-schema->json "docs/examples/fhir-schema/resource-with-codable-concept.fs.json"))

  (golden/vs-json "docs/examples/resource-with-code.ts.json"
                  (fhir-schema->json "docs/examples/fhir-schema/resource-with-code.fs.json"))

  (golden/vs-json "test/golden/patient.ts.json"
                  (fhir-schema->json "test/golden/patient.fs.json"))

  (golden/vs-json "test/golden/capability-statement.ts.json"
                  (fhir-schema->json "test/golden/capability-statement.fs.json"))

  (golden/vs-json "test/golden/questionnaire.ts.json"
                  (fhir-schema->json "test/golden/questionnaire.fs.json")))

(defn value-set->json [fhir-schema-file]
  (-> (slurp fhir-schema-file)
      (json/parse-string true)
      (type-schema/translate-value-set)))

(deftest value-set->type-schema-golden-test
  (package/init-from-package! "hl7.fhir.r4.core")

  (golden/vs-json "docs/examples/administrative-gender.ts.json"
                  (value-set->json "docs/examples/value-set/administrative-gender.json"))

  (golden/vs-json "docs/examples/all-languages.ts.json"
                  (value-set->json "docs/examples/value-set/all-languages.json"))

  (golden/vs-json "docs/examples/marital-status.ts.json"
                  (value-set->json "docs/examples/value-set/marital-status.json")))

(comment
  (package/fhir-schema-index "MolecularSequence")
  (-> (transpiler.fhir-schema/translate
       (package/index "http://hl7.org/fhir/StructureDefinition/MolecularSequence"))
      (type-schema/translate-fhir-schema))

  (package/init-from-package! "hl7.fhir.r4.core")
  (package/fhir-schema-index "Coding"))
