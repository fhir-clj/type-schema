# FHIR Type Schema

[![.github/workflows/ci.yaml](https://github.com/fhir-clj/type-schema/actions/workflows/ci.yaml/badge.svg)](https://github.com/fhir-clj/type-schema/actions/workflows/ci.yaml)

Status: **Draft**

This repository contains the Clojure library to generate the Type Schema, an intermediate representation for FHIR SDK generation.

Recommended file extension for the FHIR Type Schema is `.ts.json`.

<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-refresh-toc -->
**Table of Contents**

- [FHIR Type Schema](#fhir-type-schema)
  - [Motivation](#motivation)
  - [Overview](#overview)
    - [Common Structure](#common-structure)
    - [`"primitive-type"`](#primitive-type)
    - [`"resource"`, `"complex-type"`](#resource-complex-type)
    - [`"nested"`](#nested)
    - [Validation by JSON Schema](#validation-by-json-schema)
  - [SDK Pipeline](#sdk-pipeline)
  - [TODO](#todo)
  - [Installation](#installation)
    - [Using the Released JAR](#using-the-released-jar)
  - [Local Build & Run](#local-build--run)

<!-- markdown-toc end -->

## Motivation

Creating a universal SDK for FHIR would be extremely complex due to the vast scope of FHIR and the diverse requirements of healthcare applications. Furthermore, most projects have specific requirements that would not be adequately addressed by a one-size-fits-all approach. Type Schema provides a simplified, normalized representation of these FHIR entities that is specifically designed for easy consumption by code generators.

The FHIR Type Schema aims to:

1. Provide a consistent representation of FHIR types across different FHIR versions
2. Simplify the complex nesting and references in FHIR Structure Definitions or FHIR Schema
3. Normalize value sets and code systems for easier binding validation
4. Support efficient code generation in multiple programming languages
5. Enable better tooling for FHIR implementation with strong type safety
6. Support for custom resources described in FHIR Schema
7. Serve as a foundation for project-specific SDK customizations

By using Type Schema as an intermediate representation, we can separate the concerns of parsing FHIR packages from the language-specific code generation, making both processes more maintainable and extensible.

## Overview

The Type Schema is a JSON-based format that provides a simplified representation of FHIR entities (resources, primitive types, etc.).

JSON Schema to validate the FHIR Type Schemas is placed in <docs/type-schema.schema.json>.

Examples of Type Schema and related FHIR Schema are placed in <docs/examples>.

### Common Structure

All FHIR Type Schema entities share a common structure with an `identifier` field which is an ID if defined in the root, or reference if used in other ways:

- `kind`: The type of entity, which can be:
  - `"primitive-type"`: Primitive types like string, boolean, decimal
  - `"resource"`: FHIR resources like Patient, Observation
  - `"complex-type"`: Complex data types like HumanName, Address
  - `"nested"`: Backbone elements within resources
- `package`: The FHIR package source (e.g., "hl7.fhir.r4.core")
- `version`: The version of the FHIR package (e.g., "4.0.1")
- `name`: The name of the type (e.g., "Patient", "string")
- `url`: The canonical URL of the type (e.g., "http://hl7.org/fhir/StructureDefinition/Patient")

Other fields are specific to the kind.

### `"primitive-type"`

Primitive types are simpler with:

- `identifier`: As described above
- `description`: Human-readable description of the entity
- `base`: Reference to the base type in accordance to Structure Definition
- `dependencies`: References to all mentioned FHIR entities

### `"resource"`, `"complex-type"`

Resources and complex types include:

- `identifier`: As described above
- `description`: Human-readable description of the entity
- `base`: Reference to the base type in accordance to Structure Definition
- `fields`: Object mapping field names to their definitions, including:
  - `type`: Reference to another type
  - `reference`: Reference to another type
  - `array`: Boolean indicating if the field can have multiple values
  - `required`: Boolean indicating if the field is required
  - `enum`: List of possible values for the primitive type if we can simply expand value set for it
  - `choices`: For choice[x] elements, lists possible type options
  - `choiceOf`: Name of the choice type
- `nested`: Array of nested backbone element type definitions
- `dependencies`: References to all mentioned FHIR entities

For details on special cases like choice types, see [./docs](./docs).

### `"nested"`

Backbone elements are represented as:

- `identifier`: As described above with kind="nested"
- `base`: Reference to the base type in accordance to Structure Definition. Usually references BackboneElement type
- `fields`: Object mapping field names to their definitions, see above
- `dependencies`: References to all mentioned FHIR entities

This normalized structure eliminates the complexity of navigating differential and snapshot views in Structure Definitions, making it straightforward to generate strongly-typed code in any programming language.

### Snippet of the Patient resource from hl7.fhir.r4.core
You can see the full type-schema structure in [./test/golden/patient.ts.json](./test/golden/patient.ts.json), which demonstrates how a Patient resource is represented in our normalized format.

This structure makes it easy to understand the relationships between FHIR resources and their properties during generation process without having to parse and lookup for the more complex original FHIR definitions.

```json
{
  "identifier" : {
    "kind" : "resource",
    "package" : "hl7.fhir.core.r4",
    "version" : "4.0.1",
    "name" : "Patient",
    "url" : "http://hl7.org/fhir/StructureDefinition/Patient"
  },
  "base" : {
    "kind" : "resource",
    "package" : "hl7.fhir.r4.core",
    "version" : "4.0.1",
    "name" : "DomainResource",
    "url" : "http://hl7.org/fhir/StructureDefinition/DomainResource"
  },
  "description" : "Demographics and other administrative information about an individual or animal receiving care or other health-related services.",
  "fields" : {
    "address" : {
      "array" : true,
      "required" : false,
      "excluded" : false,
      "type" : {
        "kind" : "complex-type",
        "package" : "hl7.fhir.r4.core",
        "version" : "4.0.1",
        "name" : "Address",
        "url" : "http://hl7.org/fhir/StructureDefinition/Address"
      }
    },
    "managingOrganization" : {
      "array" : false,
      "required" : false,
      "excluded" : false,
      "type" : {
        "kind" : "complex-type",
        "package" : "hl7.fhir.r4.core",
        "version" : "4.0.1",
        "name" : "Reference",
        "url" : "http://hl7.org/fhir/StructureDefinition/Reference"
      },
      "reference" : [ {
        "kind" : "resource",
        "package" : "hl7.fhir.r4.core",
        "version" : "4.0.1",
        "name" : "Organization",
        "url" : "http://hl7.org/fhir/StructureDefinition/Organization"
      } ]
    },
    ...
  },
  "nested": [...],
  "dependencies" : [ {
    "kind" : "resource",
    "package" : "hl7.fhir.r4.core",
    "version" : "4.0.1",
    "name" : "DomainResource",
    "url" : "http://hl7.org/fhir/StructureDefinition/DomainResource"
  },
  ...
  ]
}
```

### Validation by JSON Schema

JSON Schema for the FHIR Type Schema is placed in <docs/type-schema.schema.json>.

How to check JSON by this schema:

```shell
$ npm install -g ajv-cli
$ ajv test -s docs/type-schema.schema.json -d docs/example.ts.json --valid
$ find . -name "*.ts.json" | xargs -n 1 ajv test -s docs/type-schema.schema.json --valid -d
```

## SDK Pipeline

![Type Schema Pipeline](docs/assets/image.png)

The typical SDK generation pipeline with Type Schema consists of several transformation steps:

1. **FHIR Package Loader** [Github](https://github.com/fhir-clj/fhir-package-registry) → Start with a FHIR package (e.g., `hl7.fhir.r4.core@4.0.1`)
   - Contains Structure Definitions, Value Sets, and other FHIR artifacts and Aidbox configuration packages with Custom Resource, Acess Policy, User etc.
   - Provides the canonical definitions for FHIR resources and types

2. **FHIR Artifact Regestry** : TBD

3. **FHIR Schema Generation** [Github](https://github.com/fhir-clj/fhir-schema)
   - Transform Structure Definitions into FHIR Schema
   - Simplifies the complex differential/snapshot structure
   - Normalizes resource and type definitions
   - Recommended extension: `.fs.json`

4. **Type Schema Generation** [Github](https://github.com/fhir-clj/type-schema)
   - Transform FHIR Schema into Type Schema
   - Further simplifies and normalizes for code generation
   - Makes the structure more language-agnostic
   - Recommended extension: `.ts.json`

5. **SDK Generation** (language-specific part)
   - Consume Type Schema to generate language-specific code
   - Generate classes, types, or structs for each resource
   - Add validation, serialization, and deserialization capabilities
   - Implement FHIR operations as language-specific methods
   - Output is ready-to-use SDK code in target language

See the example of the last step implemented in TypeScript for several languages here: [fhir-schema-codegen](https://github.com/fhir-schema/fhir-schema-codegen)

This pipeline separates concerns between parsing FHIR packages, transforming into intermediate representations, and generating language-specific code, making each step more maintainable and reusable.

## TODO

- [ ] ndjson/path output from CLI
- [ ] Bindings
- [ ] Extension
- [ ] Docstrings
- [ ] Profiles
- [ ] Search Parameters
- [ ] Operations

## Installation

### Using the Released JAR

You can download the latest release jar from the [GitHub Releases page](https://github.com/fhir-clj/type-schema/releases).

### Command Line Usage

The type-schema tool can be used directly from the command line to process FHIR packages and generate Type Schema files.

```bash
java -jar type-schema.jar <fhir-package-name> <output-dir>
```

Example:
```bash
java -jar type-schema.jar hl7.fhir.r4.core@4.0.1 ./output
```

This will:
1. Process the specified FHIR package (e.g., hl7.fhir.r4.core@4.0.1)
2. Generate a .ndjson file containing all the type schemas
3. Save it to `<output-dir>/<package-name>.ndjson`


## Local Build & Run

```shell
make build && java -jar target/type-schema.jar hl7.fhir.r4.core@4.0.1 ./output

bash -c '
    PACKAGE=hl7.fhir.r4.core@4.0.1
    OUTPUT_DIR=output
    mkdir -p "$OUTPUT_DIR/$PACKAGE"
    cat "$OUTPUT_DIR/$PACKAGE.ndjson" | jq -c "." | while read -r line; do
        name=$(echo "$line" | jq -r ".identifier.name")
        out_file=$OUTPUT_DIR/$PACKAGE/${name}.json
        echo $out_file
        echo "$line" | jq "." > "$out_file"
    done
'

make clean r4-ts-json check-output-json
```

Generate r4 package and check the output JSON files in accordance with JSON Schema.

<!-- FIXME: support ndjson/path output to avoid bash inlines. -->
<!-- FIXME: required, array consistency with field declaration (name in choiceOf). -->
