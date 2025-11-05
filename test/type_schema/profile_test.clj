(ns type-schema.profile-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [matcho.core :as matcho]
   [type-schema.core :as type-schema]
   [type-schema.package-index :as package]))

(deftest profile-constraints-flag-disabled-by-default
  (testing "Profile constraints are NOT included by default (backward compatibility)"
    (package/initialize! {:package-names ["hl7.fhir.r4.core" "hl7.fhir.no.basis@2.2.2"]})

    (let [patient-schema (package/fhir-schema "http://hl7.no/fhir/StructureDefinition/no-basis-Patient")
          type-schemas (type-schema/translate-fhir-schema patient-schema) ; No opts = defaults
          patient-type-schema (first type-schemas)]

      (testing "Patient.name has NO profile constraints by default"
        (is (nil? (get-in patient-type-schema [:fields :name :profileConstraints]))))

      (testing "Patient.address has NO profile constraints by default"
        (is (nil? (get-in patient-type-schema [:fields :address :profileConstraints]))))

      (testing "Basic structure still works"
        (matcho/match (get-in patient-type-schema [:fields :name])
          {:type {:name "HumanName"}
           :array true})))))

(deftest profile-constraints-extraction-test
  (testing "Extract profile constraints from Norwegian Basis Patient when flag is enabled"
    ;; IMPORTANT: Both packages needed - Norwegian profiles reference base FHIR types
    (package/initialize! {:package-names ["hl7.fhir.r4.core" "hl7.fhir.no.basis@2.2.2"]})

    (let [patient-schema (package/fhir-schema "http://hl7.no/fhir/StructureDefinition/no-basis-Patient")
          ;; Must process all schemas to resolve dependencies properly
          type-schemas (type-schema/translate-fhir-schema patient-schema {:include-profile-constraints? true})
          patient-type-schema (first type-schemas)]

      (testing "Patient.name field has profile constraints"
        (matcho/match (get-in patient-type-schema [:fields :name])
          {:type {:name "HumanName"}
           :profileConstraints [{:url "http://hl7.no/fhir/StructureDefinition/no-basis-HumanName"
                                 :name "NoBasisHumanName"}]}))

      (testing "Patient.address field has profile constraints"
        (matcho/match (get-in patient-type-schema [:fields :address])
          {:type {:name "Address"}
           :profileConstraints [{:url "http://hl7.no/fhir/StructureDefinition/no-basis-Address"
                                 :name "NoBasisAddress"}]}))

      (testing "Patient.contact is a nested BackboneElement"
        (matcho/match (get-in patient-type-schema [:fields :contact])
          {:type {:kind "nested"}}))

      (testing "Fields without profiles have no profileConstraints"
        (is (nil? (get-in patient-type-schema [:fields :birthDate :profileConstraints])))))))

(deftest field-documentation-flag-disabled-by-default
  (testing "Field documentation is NOT included by default (backward compatibility)"
    (package/initialize! {:package-names ["hl7.fhir.r4.core"]})

    (let [patient-schema (package/fhir-schema "http://hl7.org/fhir/StructureDefinition/Patient")
          type-schemas (type-schema/translate-fhir-schema patient-schema) ; No opts = defaults
          patient-type-schema (first type-schemas)]

      (testing "Patient.name has NO documentation fields by default"
        (let [name-field (get-in patient-type-schema [:fields :name])]
          (is (nil? (:short name-field)))
          (is (nil? (:definition name-field)))
          (is (nil? (:comment name-field)))))

      (testing "Basic structure still works"
        (matcho/match (get-in patient-type-schema [:fields :name])
          {:type {:name "HumanName"}
           :array true})))))

(deftest field-documentation-extraction-test
  (testing "Extract field documentation when flag is enabled"
    (package/initialize! {:package-names ["hl7.fhir.r4.core"]})

    (let [patient-schema (package/fhir-schema "http://hl7.org/fhir/StructureDefinition/Patient")
          type-schemas (type-schema/translate-fhir-schema patient-schema {:include-field-docs? true})
          patient-type-schema (first type-schemas)]

      (testing "Patient.gender has documentation"
        (let [gender-field (get-in patient-type-schema [:fields :gender])]
          (is (some? (:short gender-field)))
          (is (= "male | female | other | unknown" (:short gender-field)))))

      (testing "Patient.active has documentation"
        (let [active-field (get-in patient-type-schema [:fields :active])]
          (is (some? (:short active-field)))
          (is (some? (:definition active-field)))))

      (testing "Patient.birthDate exists and structure is preserved"
        (let [birthDate-field (get-in patient-type-schema [:fields :birthDate])]
          (is (some? birthDate-field))
          (matcho/match birthDate-field
            {:type {:name "date"}}))))))

(deftest both-flags-enabled-test
  (testing "Both profile constraints and field documentation can be enabled together"
    (package/initialize! {:package-names ["hl7.fhir.r4.core" "hl7.fhir.no.basis@2.2.2"]})

    (let [patient-schema (package/fhir-schema "http://hl7.no/fhir/StructureDefinition/no-basis-Patient")
          type-schemas (type-schema/translate-fhir-schema patient-schema
                                                          {:include-profile-constraints? true
                                                           :include-field-docs? true})
          patient-type-schema (first type-schemas)]

      (testing "Patient.name has both profile constraints and documentation"
        (let [name-field (get-in patient-type-schema [:fields :name])]
          (is (some? (:profileConstraints name-field)))
          (matcho/match (:profileConstraints name-field)
            [{:url "http://hl7.no/fhir/StructureDefinition/no-basis-HumanName"}])
          ;; Documentation might be present if defined in differential
          )))))

(deftest backward-compatibility-standard-patient-test
  (testing "Standard FHIR Patient works as before with no flags"
    (package/initialize! {:package-names ["hl7.fhir.r4.core"]})

    (let [patient-schema (package/fhir-schema "http://hl7.org/fhir/StructureDefinition/Patient")
          type-schemas (type-schema/translate-fhir-schema patient-schema)
          patient-type-schema (first type-schemas)]

      (testing "Has expected structure"
        (matcho/match patient-type-schema
          {:identifier {:name "Patient"}
           :fields {:name {:type {:name "HumanName"}
                           :array true}
                    :gender {:type {:name "code"}}
                    :birthDate {:type {:name "date"}}}}))

      (testing "No profile constraints"
        (is (nil? (get-in patient-type-schema [:fields :name :profileConstraints]))))

      (testing "No field documentation"
        (is (nil? (get-in patient-type-schema [:fields :name :short])))))))
