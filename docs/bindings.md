# Bindings to valueSet

<!-- TODO: codable concept examples -->
<!-- TODO: Use & Example: http://hl7.org/fhir/StructureDefinition/elementdefinition-bindingName -->

In complex cases, we have only one option to work with bindings -- use an external term server to validate values. For simple cases, where we can simply expand all possible values, we can add this information to FHIR SDK types. Let's see an example with a simple binding.

Patient StructureDefinition, `.difference.elements` (from FHIR):

```json
{
     "id" : "Patient.gender",
     "path" : "Patient.gender",
     "short" : "male | female | other | unknown",
     "definition" : "Administrative Gender - the gender ...",
     "comment" : "The gender might ...",
     "requirements" : "Needed for identification of the individual, in combination with (at least) name and birth date.",
     "min" : 0,
     "max" : "1",
     "base" : {
       "path" : "Patient.gender",
       "min" : 0,
       "max" : "1"
     },
     "type" : [{
       "code" : "code"
     }],
     "constraint" : [{
       "key" : "ele-1",
       "severity" : "error",
       "human" : "All FHIR elements must have a @value or children",
       "expression" : "hasValue() or (children().count() > id.count())",
       "source" : "http://hl7.org/fhir/StructureDefinition/Element"
     }],
     "mustSupport" : false,
     "isModifier" : false,
     "isSummary" : true,
     "binding" : {
       "extension" : [{
         "url" : "http://hl7.org/fhir/StructureDefinition/elementdefinition-bindingName",
         "valueString" : "AdministrativeGender"
       },
       {
         "url" : "http://hl7.org/fhir/StructureDefinition/elementdefinition-isCommonBinding",
         "valueBoolean" : true
       }],
       "strength" : "required",
       "description" : "The gender of a person used for administrative purposes.",
       "valueSet" : "http://hl7.org/fhir/ValueSet/administrative-gender|6.0.0-ballot2"
     },
     "mapping" : [{
       "identity" : "v2",
       "map" : "PID-8"
     },
     {
       "identity" : "rim",
       "map" : "player[classCode=PSN|ANM and determinerCode=INSTANCE]/administrativeGender"
     },
     {
       "identity" : "interface",
       "map" : "ParticipantLiving.gender"
     },
     {
       "identity" : "cda",
       "map" : ".patient.administrativeGenderCode"
     }]
   }
```

The same thing in FHIR Schema, `elements`:

```json
{
  "gender": {
    "short": "male | female | other | unknown",
    "type": "code",
    "binding": {
      "strength": "required",
      "valueSet": "http://hl7.org/fhir/ValueSet/administrative-gender|4.0.1"
    },
    "isSummary": true,
    "index": 1
  }
}
```


And here we can see a problem: programming languages don't have binding concepts directly. We have the following options depending on the programming language:

1. Type Literals. The closest thing to binding, because it has a "base type" and a list of possible values for it, which can be embedded in type definition and the type checker will work with them like sets (TypeScript, Python).
2. Enumerated types. Standalone types, which should be used for binding values (Java, C#).

We will write both examples in TypeScript and Python.

## Type Literals

Some programming languages allow the use of type level literals and permit the following constructs:

```typescript
type MaritalStatus = 'single' | 'married' | 'divorced' | 'widowed';
const status: MaritalStatus = 'married';
```

```python
from typing import Literal
MaritalStatus = Literal['single', 'married', 'divorced', 'widowed']
status: MaritalStatus = 'married'
```

For simple and fast generation of such code, Type Schema provides an `enum` field with pregenerated values:

```json
{
  "identifier": { "kind": "resource", "package": "hl7.fhir.core.r4", "version": "4.0.1", "name": "WithCode", "url": "http://example.io/fhir/WithCode" },
  "base": { "kind": "resource", "package": "hl7.fhir.r4.core", "version": "4.0.1", "name": "DomainResource", "url": "http://hl7.org/fhir/StructureDefinition/DomainResource" },
  "description": "description",
  "fields": {
    "gender": {
      "array": false,
      "required": false,
      "excluded": false,
      "type": { "kind": "primitive-type", "package": "hl7.fhir.r4.core", "version": "4.0.1", "name": "code", "url": "http://hl7.org/fhir/StructureDefinition/code" },
      "enum": ["male", "female", "other", "unknown"]
    }
  },
  "dependencies": [
    { "kind": "resource", "package": "hl7.fhir.r4.core", "version": "4.0.1", "name": "DomainResource", "url": "http://hl7.org/fhir/StructureDefinition/DomainResource" },
    { "kind": "primitive-type", "package": "hl7.fhir.r4.core", "version": "4.0.1", "name": "code", "url": "http://hl7.org/fhir/StructureDefinition/code" }
  ]
}
```

Limitation: we can't actually separate `"unknown"` values from different types.

## Separated Type

What is the problem with enum types? On the programming language level it should be:

1. Separated types from the resource type.
2. Shared, because if we have `Gender` in two resources, they should have the same type for operations like: `practitioner.gender == person.gender`.
3. Binding is not a simple valueset link; binding has include/exclude logic, which can be different for each resource or field in the resource.

```python
class AdministrativeGender(Enum):
    MALE = "male"
    FEMALE = "female"
    OTHER = "other"
    UNKNOWN = "unknown"

gender: AdministrativeGender = AdministrativeGender.UNKNOWN

class Patient(DomainResource):
    gender: AdministrativeGender

class Practitioner(DomainResource):
    gender: AdministrativeGender
```

Current Type Schema limitations:

1. We should infer the type from field description. This raises a naming problem to find the same name for different resources.
2. In structure definition we have extension with TypeName recommendations which disappear at the FHIR Schema generation stage.

Proposed:

```json
{
  "identifier": { "kind": "resource", "package": "hl7.fhir.core.r4", "version": "4.0.1", "name": "WithCode", "url": "http://example.io/fhir/WithCode" },
  "base": { "kind": "resource", "package": "hl7.fhir.r4.core", "version": "4.0.1", "name": "DomainResource", "url": "http://hl7.org/fhir/StructureDefinition/DomainResource" },
  "description": "description",
  "fields": {
    "gender": {
      "array": false,
      "required": false,
      "excluded": false,
      // name & url tail generated from http://hl7.org/fhir/StructureDefinition/elementdefinition-bindingName extension
      // or autogenerated something like: "Patient#gender.binding" which is not shared.
      "type": { "kind": "binding", "package": "hl7.fhir.core.r4", "version": "4.0.1", "name": "AdministrativeGender", "url": "http://example.io/fhir/AdministrativeGender" },
      // duplicate binding content. Can be removed if we have type-schema registry
      "enum": ["male", "female", "other", "unknown"]
    }
  },
  "dependencies": [
    { "kind": "resource", "package": "hl7.fhir.r4.core", "version": "4.0.1", "name": "DomainResource", "url": "http://hl7.org/fhir/StructureDefinition/DomainResource" },
    { "kind": "binding", "package": "hl7.fhir.core.r4", "version": "4.0.1", "name": "AdministrativeGender", "url": "http://example.io/fhir/AdministrativeGender" }
  ]
}

{
  "identifier": { "kind": "binding", "package": "hl7.fhir.core.r4", "version": "4.0.1", "name": "AdministrativeGender", "url": "http://example.io/fhir/AdministrativeGender" },
  "type": { "kind": "primitive-type", "package": "hl7.fhir.r4.core", "version": "4.0.1", "name": "code", "url": "http://hl7.org/fhir/StructureDefinition/code" },
  "valueset": { "kind": "value-set", "package": "hl7.fhir.r4.core", "version": "4.0.1", "name": "AdministrativeGender", "url": "http://hl7.org/fhir/ValueSet/administrative-gender" },
  "strength" : "required",
  "description" : "The gender of a person used for administrative purposes.",
  "enum": ["male", "female", "other", "unknown"],
  "dependencies": [
    { "kind": "value-set", "package": "hl7.fhir.r4.core", "version": "4.0.1", "name": "a-dministrative-gender", "url": "http://hl7.org/fhir/ValueSet/administrative-gender" },
    { "kind": "primitive-type", "package": "hl7.fhir.r4.core", "version": "4.0.1", "name": "code", "url": "http://hl7.org/fhir/StructureDefinition/code" }
  ]
}

{
  "identifier": { "kind": "value-set", "package": "hl7.fhir.r4.core", "version": "4.0.1", "name": "a-dministrative-gender", "url": "http://hl7.org/fhir/ValueSet/administrative-gender" },
  "description": "The gender of a person used for administrative purposes.",
  "concept": [
    { "system": "http://hl7.org/fhir/administrative-gender", "code": "male", "display": "Male" },
    { "system": "http://hl7.org/fhir/administrative-gender", "code": "female", "display": "Female" },
    { "system": "http://hl7.org/fhir/administrative-gender", "code": "other", "display": "Other" },
    { "system": "http://hl7.org/fhir/administrative-gender", "code": "unknown", "display": "Unknown" }
  ]
}
```
