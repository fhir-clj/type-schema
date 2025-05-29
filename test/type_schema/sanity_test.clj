(ns type-schema.sanity-test
  (:require [clojure.test :refer [deftest is]]
            [type-schema.sanity :as sanity]))

(deftest find-unresolvable-deps-test
  (is (= #{}
         (sanity/find-unresolvable-deps
          [{:identifier {:kind "resource",
                         :package "custom-package",
                         :version "0.0.1",
                         :name "Custom",
                         :url "http://example.com/StructureDefinition/Custom"},
            :dependencies [{:kind "resource",
                            :package "hl7.fhir.r4.core",
                            :version "4.0.1",
                            :name "Resource",
                            :url "http://hl7.org/fhir/StructureDefinition/Resource"}
                           {:kind "primitive-type",
                            :package "hl7.fhir.r4.core",
                            :version "4.0.1",
                            :name "string",
                            :url "http://hl7.org/fhir/StructureDefinition/string"}
                           {:kind "nested",
                            :package "hl7.fhir.core.r4",
                            :version "4.0.1",
                            :name "link",
                            :url "http://example.io/fhir/WithNestedType#link"},
                           #_{:kind "binding",
                              :package "hl7.fhir.core.r4",
                              :version "4.0.1",
                              :name "AdministrativeGender",
                              :url "urn:fhir:binding:AdministrativeGender"}]}
           {:identifier {:kind "resource",
                         :package "hl7.fhir.r4.core",
                         :version "4.0.1",
                         :name "Resource",
                         :url "http://hl7.org/fhir/StructureDefinition/Resource"}}
           {:identifier {:kind "primitive-type",
                         :package "hl7.fhir.r4.core",
                         :version "4.0.1",
                         :name "string",
                         :url "http://hl7.org/fhir/StructureDefinition/string"}}])))

  (is (= #{{:kind "resource",
            :package "hl7.fhir.r4.core",
            :version "4.0.1",
            :name "Resource",
            :url "http://hl7.org/fhir/StructureDefinition/Resource"}}
         (sanity/find-unresolvable-deps
          [{:identifier {:kind "resource",
                         :package "custom-package",
                         :version "0.0.1",
                         :name "Custom",
                         :url "http://example.com/StructureDefinition/Custom"},
            :dependencies [{:kind "resource",
                            :package "hl7.fhir.r4.core",
                            :version "4.0.1",
                            :name "Resource",
                            :url "http://hl7.org/fhir/StructureDefinition/Resource"}
                           {:kind "primitive-type",
                            :package "hl7.fhir.r4.core",
                            :version "4.0.1",
                            :name "string",
                            :url "http://hl7.org/fhir/StructureDefinition/string"}]}
           {:identifier {:kind "primitive-type",
                         :package "hl7.fhir.r4.core",
                         :version "4.0.1",
                         :name "string",
                         :url "http://hl7.org/fhir/StructureDefinition/string"}}]))))
