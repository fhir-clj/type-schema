(ns type-schema.primitive-types)

;; TODO: get them from "fhir base package"
(def default-identifier
  {;; FIXME: it is FHIR Object
   "http://hl7.org/fhir/StructureDefinition/Base"
   {:kind    "primitive-type"
    :package "hl7.fhir.r4.core"
    :version "4.0.1"
    :name    "Base"
    :url     "http://hl7.org/fhir/StructureDefinition/Base"}

   "string" {:kind    "primitive-type"
             :package "hl7.fhir.r4.core"
             :version "4.0.1"
             :name    "string"
             :url     "http://hl7.org/fhir/StructureDefinition/string"}
   "code" {:kind    "primitive-type"
           :package "hl7.fhir.r4.core"
           :version "4.0.1"
           :name    "code"
           :url     "http://hl7.org/fhir/StructureDefinition/code"}
   "integer" {:kind    "primitive-type"
              :package "hl7.fhir.r4.core"
              :version "4.0.1"
              :name    "integer"
              :url     "http://hl7.org/fhir/StructureDefinition/integer"}
   "codeSystem" {:kind    "primitive-type"
                 :package "hl7.fhir.r4.core"
                 :version "4.0.1"
                 :name    "codeSystem"
                 :url     "http://hl7.org/fhir/StructureDefinition/codeSystem"}
   "boolean" {:kind    "primitive-type"
              :package "hl7.fhir.r4.core"
              :version "4.0.1"
              :name    "boolean"
              :url     "http://hl7.org/fhir/StructureDefinition/boolean"}
   "dateTime" {:kind    "primitive-type"
               :package "hl7.fhir.r4.core"
               :version "4.0.1"
               :name    "dateTime"
               :url     "http://hl7.org/fhir/StructureDefinition/dateTime"}
   "id" {:kind    "primitive-type"
         :package "hl7.fhir.r4.core"
         :version "4.0.1"
         :name    "id"
         :url     "http://hl7.org/fhir/StructureDefinition/id"}
   "base64Binary" {:kind    "primitive-type"
                   :package "hl7.fhir.r4.core"
                   :version "4.0.1"
                   :name    "base64Binary"
                   :url     "http://hl7.org/fhir/StructureDefinition/base64Binary"}

   "xhtml" {:kind    "primitive-type"
            :package "hl7.fhir.r4.core"
            :version "4.0.1"
            :name    "xhtml"
            :url     "http://hl7.org/fhir/StructureDefinition/xhtml"}

   "decimal" {:kind    "primitive-type"
              :package "hl7.fhir.r4.core"
              :version "4.0.1"
              :name    "decimal"
              :url     "http://hl7.org/fhir/StructureDefinition/decimal"}})
