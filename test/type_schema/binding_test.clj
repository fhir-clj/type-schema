(ns type-schema.binding-test
  (:require
   [clojure.test :refer [deftest is]]
   [matcho.core :as matcho]
   [type-schema.binding :as binding]))

(deftest binding-test
  (is (= {:package "TEST",
          :name "AdministrativeGender",
          :kind "binding",
          :url "urn:fhir:binding:AdministrativeGender",
          :version "0.0.0"}
         (binding/build-binding
          {:package-meta {:name "TEST" :version "0.0.0"}}
          ["gender"]
          {:type "code",
           :binding {:strength "required",
                     :valueSet "http://hl7.org/fhir/ValueSet/administrative-gender|4.0.1",
                     :bindingName "AdministrativeGender"}})))

  (is (= {:kind "binding",
          :package "TEST",
          :version "0.0.0",
          :name "Patient.gender_binding",
          :url "http://hl7.org/fhir/StructureDefinition/Patient#gender_binding"}
         (binding/build-binding
          {:package-meta {:name "TEST" :version "0.0.0"}
           :name "Patient"
           :url "http://hl7.org/fhir/StructureDefinition/Patient"}
          ["gender"]
          {:type "code",
           :binding {:strength "required",
                     :valueSet "http://hl7.org/fhir/ValueSet/administrative-gender|4.0.1"}})))

  (matcho/match (binding/generate-binding-type
                 {:package-meta {:name "TEST" :version "0.0.0"}}
                 ["Patient" "gender"]
                 {:type "code",
                  :binding {:strength "required",
                            :valueSet "http://hl7.org/fhir/ValueSet/administrative-gender|4.0.1",
                            :bindingName "AdministrativeGender"}}
                 {:kind "primitive-type", :name "code"})
    {:identifier {:kind "binding", :name "AdministrativeGender"}
     :type       {:kind "primitive-type", :name "code"},
     :valueset   {:kind "value-set", :name "administrative-gender"}
     :enum ["male" "female" "other" "unknown"],
     :strength "required",
     :dependencies [{:kind "primitive-type", :name "code"}
                    {:kind "value-set", :name "administrative-gender"}]}))
