# FHIR Type Schema

[![.github/workflows/ci.yaml](https://github.com/fhir-clj/type-schema/actions/workflows/ci.yaml/badge.svg)](https://github.com/fhir-clj/type-schema/actions/workflows/ci.yaml)

Status: **Draft**

This repository contains the Clojure library to generate the FHIR Type Schema.

Recommended file extension for the FHIR Type Schema is `.ts.json`.

## Motivation

FHIR resources, as defined in FHIR Implementation Guides, are represented in complex, nested Structure Definitions that are difficult to directly use in code generation. Type Schema provides a simplified, normalized representation of these FHIR types that is specifically designed for easy consumption by code generators.

The FHIR Type Schema aims to:

1. Provide a consistent representation of FHIR types across different FHIR versions
2. Simplify the complex nesting and references in FHIR Structure Definitions or FHIR Schema
3. Normalize value sets and code systems for easier binding validation
4. Support efficient code generation in multiple programming languages
5. Enable better tooling for FHIR implementation with strong type safety
6. Support for custom resources described in FHIR Schema

By using Type Schema as an intermediate representation, we can separate the concerns of parsing FHIR packages from the language-specific code generation, making both processes more maintainable and extensible.

## Overview

The Type Schema is a JSON-based format that provides a simplified representation of FHIR entities (resources, value sets, primitive types, etc).

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

### Validation by JSON Schema

JSON Schema for the FHIR Type Schema is placed in <docs/type-schema.schema.json>.

How to check JSON by this schema:

```shell
$ npm install -g ajv-cli
$ ajv test -s docs/type-schema.schema.json -d docs/example.ts.json --valid
$ fd -e .ts.json | xargs -n 1 ajv test -s docs/type-schema.schema.json --valid -d
$ find . -name "*.ts.json" | xargs -n 1 ajv test -s docs/type-schema.schema.json --valid -d
```

## TODO:

- [ ] ndjson/path output from CLI
- [ ] Bindings
- [ ] Extension
- [ ] Docstrings
- [ ] Profiles
- [ ] Search Parameters
- [ ] Operations

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

Generate r4 package and check the output JSON files in accordance JSON Schema.

<!-- FIXME: support ndjson/path output to avoid bash inlines. -->
<!-- FIXME: required, array consistency with field declaration (name in choiceOf). -->
