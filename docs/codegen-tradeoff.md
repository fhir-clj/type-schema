# CodeGen Hints

## Choice type

- [Choice of Datatypes](https://build.fhir.org/formats.html#choice)

### Recursive better

```json
{
  "fields": {
    "deceased": {
      "choices": {
        "deceasedBoolean": {
          "type": {
            "type": "primitive-type",
            "url": "http://hl7.org/fhir/StructureDefinition/boolean",
            "name": "boolean",
            "base": "http://hl7.org/fhir/StructureDefinition/Element"
          }
        },
        "deceasedDateTime": {
          "type": {
            "type": "primitive-type",
            "url": "http://hl7.org/fhir/StructureDefinition/dateTime",
            "name": "dateTime",
            "base": "http://hl7.org/fhir/StructureDefinition/Element"
          }
        }
      }
    }
  }
}
```

```haskell
data Patient
    = Patient
    { deceased :: Deceased
    ...
    }

data Deceased
    = DeceasedBoolean Bool
    | DeceasedDateTime String
```

```c
struct Deceased {
    bool deceasedBoolean;
    char* deceasedDateTime;
};

bool is_deceased_valid(struct Deceased* deceased) { ... }

struct Patient {
    struct Deceased deceased;
    ...
};

bool is_patient_valid(struct Deceased* deceased) {
    return is_deceased_valid(&deceased->deceased)
        && ...;
}
```

### Flat better

```json
{
  "fields": {
    "deceasedBoolean": {
      "choiceOf": "deceased",
      "type": {
        "type": "primitive-type",
        "url": "http://hl7.org/fhir/StructureDefinition/boolean",
        "name": "boolean",
        "base": "http://hl7.org/fhir/StructureDefinition/Element"
      }
    },
    "deceasedDateTime": {
      "choiceOf": "deceased",
      "type": {
        "type": "primitive-type",
        "url": "http://hl7.org/fhir/StructureDefinition/dateTime",
        "name": "dateTime",
        "base": "http://hl7.org/fhir/StructureDefinition/Element"
      }
    }
  }
}
```

```c
struct Patient {
    bool deceasedBoolean;
    char* deceasedDateTime;
    ...
};

bool is_patient_valid(struct Patient* patient) {
    return (patient->deceasedBoolean && !patient->deceasedDateTime)
        || (!patient->deceasedBoolean && patient->deceasedDateTime)
        && ...;
}
```
