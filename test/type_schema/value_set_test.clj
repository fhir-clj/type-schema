(ns type-schema.value-set-test
  (:require [clojure.test :refer [deftest is testing]]
            [matcho.core :as matcho]
            [type-schema.package-index :as package]
            [type-schema.value-set :as value-set]))

(deftest concepts-test
  (package/initialize! {:package-names ["hl7.fhir.r4.core"]})

  (testing "extracting concepts from value set with nested systems and filtering"
    (is (= [{:system "http://terminology.hl7.org/CodeSystem/v3-NullFlavor",
             :code "UNK"}]
           (value-set/value-set->concepts
            {:compose
             {:include
              [{:system "http://terminology.hl7.org/CodeSystem/v3-NullFlavor",
                :concept [{:code "UNK"}]}]}}))))
  (testing "extracting concepts from value set with nested systems without filtering"
    (matcho/match
     (value-set/value-set->concepts
      {:compose
       {:include
        [{:system "http://terminology.hl7.org/CodeSystem/v3-NullFlavor"}]}})
      [{:system "http://terminology.hl7.org/CodeSystem/v3-NullFlavor",
        :code "NI",
        :display "NoInformation"}
       {:code "NP"}
       {:code "INV"}
       {:code "MSK"}
       {:code "NA"}
       {:code "UNK"}
       {:code "DER"}
       {:code "OTH"}
       {:code "UNC"}
       {:code "NINF"}
       {:code "PINF"}
       {:code "ASKU"}
       {:code "NASK"}
       {:code "NAVU"}
       {:code "QS"}
       {:code "TRC"}
       {:code "NAV"}
       nil]))
  (testing "extracting single concept"
    (package/load-resource! {:url "http://hl7.org/fhir/test-issue-type-2"
                             :resourceType "CodeSystem"
                             :concept [{:code "invalid",
                                        :display "Invalid Content",
                                        :definition "Content invalid against the specification or a profile."}]})
    (is (= [{:code "invalid",
             :display "Invalid Content",
             :definition
             "Content invalid against the specification or a profile.",
             :system "http://hl7.org/fhir/test-issue-type-2"}]
           (value-set/extract-concepts-from-codesystem "http://hl7.org/fhir/test-issue-type-2"))))
  (testing "extracting multiple levels of concepts"
    (package/load-resource! {:url "http://hl7.org/fhir/test-issue-type"
                             :resourceType "CodeSystem"
                             :concept [{:code "invalid",
                                        :display "Invalid Content",
                                        :definition "Content invalid against the specification or a profile.",
                                        :concept [{:code "structure",
                                                   :display "Structural Issue"}
                                                  {:code "required",
                                                   :display "Required element missing"}]}]})
    (is (= [{:code "invalid",
             :display "Invalid Content",
             :definition
             "Content invalid against the specification or a profile.",
             :system "http://hl7.org/fhir/test-issue-type"}
            {:code "structure",
             :display "Structural Issue",
             :system "http://hl7.org/fhir/test-issue-type"}
            {:code "required",
             :display "Required element missing",
             :system "http://hl7.org/fhir/test-issue-type"}]
           (value-set/extract-concepts-from-codesystem "http://hl7.org/fhir/test-issue-type")))))
