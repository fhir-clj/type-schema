(ns transpiler.type-schema-test
  (:require [clojure.test :refer [deftest is]]
            [transpiler.type-schema :as type-schema]
            [transpiler.package-index :as index]
            [cheshire.core :as json]
            [clojure.string :as str]
            [golden.core :as golden]))

(deftest build-field-test
  (is (= {:array false
          :required true
          :excluded false}
         (type-schema/build-field {:required ["type"]}
                                  ["type"]
                                  {}))))

(deftest golden-test
  (index/init-from-package! "hl7.fhir.r4.core")

  (golden/vs-json-file "test/golden/backbone-element.ts.json"
                       "test/golden/backbone-element.fs.json"
                       type-schema/translate)

  (golden/vs-json-file "test/golden/bundle.ts.json"
                       "test/golden/bundle.fs.json"
                       type-schema/translate)

  (golden/vs-json-file "docs/examples/string.ts.json"
                       "docs/examples/fhir-schema/string.fs.json"
                       type-schema/translate)

  (golden/vs-json-file "docs/examples/coding.ts.json"
                       "docs/examples/fhir-schema/coding.fs.json"
                       type-schema/translate)

  (golden/vs-json-file "docs/examples/resource-with-string.ts.json"
                       "docs/examples/fhir-schema/resource-with-string.fs.json"
                       type-schema/translate)

  #_(golden/vs-json-file "docs/examples/resource-with-choice.ts.json"
                         "docs/examples/fhir-schema/resource-with-choice.fs.json"
                         type-schema/translate)

  #_(golden/vs-json-file "docs/examples/resource-with-nested-type.ts.json"
                         "docs/examples/fhir-schema/resource-with-nested-type.fs.json"
                         type-schema/translate)

  #_(golden/vs-json-file "docs/examples/resource-with-codable-concept.ts.json"
                         "docs/examples/fhir-schema/resource-with-codable-concept.fs.json"
                         type-schema/translate)

  #_(golden/vs-json-file "docs/examples/resource-with-code.ts.json"
                         "docs/examples/fhir-schema/resource-with-code.fs.json"
                         type-schema/translate)

  #_(golden/vs-json-file "test/golden/patient.ts.json"
                         "test/golden/patient.fs.json"
                         type-schema/translate)

  #_(golden/vs-json-file "test/golden/capability-statement.ts.json"
                         "test/golden/capability-statement.fs.json"
                         type-schema/translate)

  #_(golden/vs-json-file "test/golden/questionnaire.ts.json"
                         "test/golden/questionnaire.fs.json"
                         type-schema/translate))

(comment
  (index/init-from-package! "hl7.fhir.r4.core")
  (index/get-fhir-schema "Coding"))
