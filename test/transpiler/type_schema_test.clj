(ns transpiler.type-schema-test
  (:require [clojure.test :refer [deftest]]
            [transpiler.type-schema :as type-schema]
            [transpiler.package-index :as index]
            [cheshire.core :as json]
            [clojure.string :as str]
            [golden.core :as golden]))

(deftest structure-definition-test
  (index/init-from-package! "hl7.fhir.r4.core")

  (golden/vs-json-file "test/golden/backbone-element.ts.json"
                       "test/golden/backbone-element.fs.json"
                       type-schema/translate)

  #_(golden/vs-json-file "test/golden/bundle.ts.json"
                         "test/golden/bundle.fs.json"
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

(comment)
