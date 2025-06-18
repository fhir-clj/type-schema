(ns type-schema.primitive-types)

(def default-identifier
  {"string" {:kind    "primitive-type"
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
                 :url     "http://hl7.org/fhir/StructureDefinition/codeSystem"}})
