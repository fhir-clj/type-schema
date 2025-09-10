(ns type-schema.identifier-test
  (:require [clojure.test :refer [deftest is]]
            [type-schema.identifier :as identifier]))

(deftest schema-type-test
  (is {:kind    "complex-type-constraint",
       :package "hl7.fhir.r4.core",
       :version "4.0.1",
       :name    "systemRef",
       :url     "http://hl7.org/fhir/StructureDefinition/valueset-systemRef"}
      (identifier/schema-type {:derivation "constraint",
                               :name       "systemRef",
                               :type       "Extension",                               ,
                               :kind       "complex-type",
                               :url        "http://hl7.org/fhir/StructureDefinition/valueset-systemRef",
                               :base       "http://hl7.org/fhir/StructureDefinition/Extension"}))

  (is {:kind    "complex-type-constraint",
       :package "hl7.fhir.r4.core",
       :version "4.0.1",
       :name    "SimpleQuantity",
       :url     "http://hl7.org/fhir/StructureDefinition/SimpleQuantity"}
      (identifier/schema-type {:name       "SimpleQuantity",
                               :type       "Quantity",
                               :derivation "constraint",
                               :kind       "complex-type",
                               :url        "http://hl7.org/fhir/StructureDefinition/SimpleQuantity",
                               :base       "http://hl7.org/fhir/StructureDefinition/Quantity"})))
