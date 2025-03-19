(ns transpiler.type-schema-test
  (:require [clojure.test :refer [deftest]]
            [transpiler.type-schema :as type-schema]
            [transpiler.package-index :as index]
            [cheshire.core :as json]
            [clojure.string :as str]
            [golden.core :refer [golden-file-content golden-test]]))

(index/init-fhir-schema-index! (index/load-merge-schemas))
(index/init-fhir-schema-index! (index/load-merge-schemas))

(defn type-schema-content [fs-filename]
  (let [ts-filename (str/replace fs-filename #".fs.json$" ".ts.json")
        sd          (-> (slurp fs-filename)
                        (json/parse-string true))
        ts-json     (-> sd
                        (type-schema/translate)
                        (json/generate-string {:pretty true}))]
    (golden-file-content ts-filename ts-json)))

(deftest structure-definition-test
  (golden-test type-schema-content "test/golden/backbone-element.fs.json")
  (golden-test type-schema-content "test/golden/patient.fs.json")
  (golden-test type-schema-content "test/golden/bundle.fs.json")
  (golden-test type-schema-content "test/golden/capability-statement.fs.json")
  (golden-test type-schema-content "test/golden/questionnaire.fs.json"))

(comment)
