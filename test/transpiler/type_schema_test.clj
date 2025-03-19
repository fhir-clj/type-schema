(ns transpiler.type-schema-test
  (:require [clojure.test :refer [deftest] ]
            [transpiler.type-schema :as type-schema]
            [transpiler.package-index :as index]
            [cheshire.core :as json]
            [clojure.string :as str]
            [golden.core :as golden]))

(deftest structure-definition-test
  (index/init-fhir-schema-index! (index/load-merge-schemas))

  (golden/vs-json-file "test/golden/backbone-element.fs.json"
                       "test/golden/backbone-element.ts.json"
                       type-schema/translate)

  (golden/vs-json-file "test/golden/patient.fs.json"
                       "test/golden/patient.ts.json"
                       type-schema/translate)

  (golden/vs-json-file "test/golden/bundle.fs.json"
                       "test/golden/bundle.ts.json"
                       type-schema/translate)

  (golden/vs-json-file "test/golden/capability-statement.fs.json"
                       "test/golden/capability-statement.ts.json"
                       type-schema/translate)

  (golden/vs-json-file "test/golden/questionnaire.fs.json"
                       "test/golden/questionnaire.ts.json"
                       type-schema/translate))

(comment)
