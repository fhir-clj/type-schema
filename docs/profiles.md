# Basic FHIR Profiles Representation

Scope: This document covers only the basic representation of the relationship between profiles and resources. Profile specific constraints are not represented in this document.

## Profiles

Profiles in FHIR represent constraints on resources or data types that tailor them for specific use cases. This document describes approaches for representing and working with profiles in the SDK.

Let's see an example of an `Observation` resource and `us-core-blood-pressure` profile in the JSON example:

```json
{
  "resourceType" : "Observation",
  "id" : "blood-pressure",
  "meta" : {
    "profile" : ["http://hl7.org/fhir/us/core/StructureDefinition/us-core-blood-pressure|8.0.0"]
  },
  "status" : "final",
  "category" : [{
    "coding" : [{
      "system" : "http://terminology.hl7.org/CodeSystem/observation-category",
      "code" : "vital-signs",
      "display" : "Vital Signs"
    }],
    "text" : "Vital Signs"
  }],
  "code" : {
    "coding" : [{
      "system" : "http://loinc.org",
      "code" : "85354-9",
      "display" : "Blood pressure panel with all children optional"
    }],
    "text" : "Blood pressure systolic and diastolic"
  },
  "subject" : {
    "reference" : "Patient/example",
    "display" : "Amy Shaw"
  },
  "encounter" : {
    "display" : "GP Visit"
  },
  "effectiveDateTime" : "1999-07-02",
  "performer" : [{
    "reference" : "Practitioner/practitioner-1",
    "display" : "Dr Ronald Bone"
  }],
  "component" : [{
    "code" : {
      "coding" : [{
        "system" : "http://loinc.org",
        "code" : "8480-6",
        "display" : "Systolic blood pressure"
      }],
      "text" : "Systolic blood pressure"
    },
    "valueQuantity" : {
      "value" : 109,
      "unit" : "mmHg",
      "system" : "http://unitsofmeasure.org",
      "code" : "mm[Hg]"
    }
  },
  {
    "code" : {
      "coding" : [{
        "system" : "http://loinc.org",
        "code" : "8462-4",
        "display" : "Diastolic blood pressure"
      }],
      "text" : "Diastolic blood pressure"
    },
    "valueQuantity" : {
      "value" : 44,
      "unit" : "mmHg",
      "system" : "http://unitsofmeasure.org",
      "code" : "mm[Hg]"
    }
  }]
}
```

## Type Schema & SDK

The schema representation should maintain a clear inheritance hierarchy:

```
DomainResource
  └── Observation (base resource)
       └── USCoreBloodPressure (profile)
            └── [Other more specific blood pressure profiles]
```

This allows for proper type checking and validation while preserving the relationship between profiles and their base resources.

### Profile related Procedures

1. Resource to profile cast
2. Profile to resource cast
3. Attaching profile to existing resource

### Multiple Profile Support

A FHIR resource can conform to multiple profiles simultaneously. The SDK should:

1. Allow attaching multiple profiles to a resource
2. Support accessing the resource through any of its profiles
3. Ensure changes made through one profile view are reflected in other profile views
4. Maintain all profile URLs in the resource's meta.profile array

### Pseudo-Code Example

```python
class ObservationComponent(Observation): pass

class Observation(DomainResource): pass
class UsCoreBloodPressure(Observation): pass

# Create Observation
obs1 = Observation(...)
pressure1 = obs1.attachProfile(UsCoreBloodPressure)
pressure1 = obs1.profile.UsCoreBloodPressure

# Create UsCoreBloodPressure directly
pressure2 = UsCoreBloodPressure(...)
pressure2.diastolic = Quantity(...)
obs2 = pressure2.resource
```
