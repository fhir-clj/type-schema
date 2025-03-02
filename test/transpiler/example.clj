(ns transpiler.example
  (:require [clojure.test :refer :all]
            [transpiler.type-schema :as type-schema]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.java.io :as io]))


(deftest derive-type
  (is (= {"url" "http://hl7.org/fhir/StructureDefinition/Resource"
          "id" "Resource"
          "name" "Resource"
          "kind" "resource"
          "type" "Resource"
          "version" "4.0.1"
          "resourceType" "FHIRSchema"
          "elements" {}} 1) "resource")

  (is (= {"url" "http://hl7.org/fhir/StructureDefinition/DomainResource"
          "id" "DomainResource"
          "base" "http://hl7.org/fhir/StructureDefinition/Resource"
          "name" "DomainResource"
          "kind" "resource"
          "type" "DomainResource"
          "version" "4.0.1"
          "resourceType" "FHIRSchema"
          "derivation" "specialization"
          "elements" {}} 1) "resource")

  (is (= {"url" "http://hl7.org/fhir/StructureDefinition/Patient"
          "id" "Patient"
          "base" "http://hl7.org/fhir/StructureDefinition/DomainResource"
          "name" "Patient"
          "kind" "resource"
          "type" "Patient"
          "version" "4.0.1"
          "resourceType" "FHIRSchema"
          "derivation" "specialization"
          "elements" {}} 1) "resource")

  (is (= {"url" "http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient"
          "id" "us-core-patient"
          "base" "http://hl7.org/fhir/StructureDefinition/Patient"
          "name" "us-core-patient"
          "kind" "resource"
          "required" ["identifier", "name", "gender"]
          "type" "Patient"
          "version" "7.0.0"
          "resourceType" "FHIRSchema"
          "derivation" "constraint"
          "elements" {}} 1) "profile")

  (is (= {"url" "http://hl7.org/fhir/StructureDefinition/MetadataResource"
          "id" "MetadataResource"
          "base" "http://hl7.org/fhir/StructureDefinition/DomainResource"
          "name" "MetadataResource"
          "kind" "logical"
          "required" ["status"]
          "type" "MetadataResource"
          "version" "4.0.1"
          "resourceType" "FHIRSchema"
          "derivation" "specialization"
          "elements" {}} 1))

  (is (= {"url" "http://hl7.org/fhir/StructureDefinition/Element"
          "id" "Element"
          "name" "Element"
          "kind" "complex-type"
          "type" "Element"
          "version" "4.0.1"
          "resourceType" "FHIRSchema"
          "elements" {}} 1))

  (is (= {"url" "http://hl7.org/fhir/StructureDefinition/string"
          "scalar" true
          "id" "string"
          "base" "http://hl7.org/fhir/StructureDefinition/Element"
          "name" "string"
          "kind" "primitive-type"
          "type" "string"
          "version" "4.0.1"
          "resourceType" "FHIRSchema"
          "derivation" "specialization"
          "system-type" "http://hl7.org/fhirpath/System.String"} 1))

  (is (= {"url" "http://hl7.org/fhir/StructureDefinition/Quantity"
          "id" "Quantity"
          "base" "http://hl7.org/fhir/StructureDefinition/Element"
          "name" "Quantity"
          "kind" "complex-type"
          "type" "Quantity"
          "version" "4.0.1"
          "resourceType" "FHIRSchema"
          "elements" {}} 1))

  (is (= {"url" "http://hl7.org/fhir/StructureDefinition/MoneyQuantity"
          "id" "MoneyQuantity"
          "base" "http://hl7.org/fhir/StructureDefinition/Quantity"
          "name" "MoneyQuantity"
          "kind" "complex-type"
          "type" "Quantity"
          "version" "4.0.1"
          "resourceType" "FHIRSchema"
          "derivation" "constraint"} 1)))
