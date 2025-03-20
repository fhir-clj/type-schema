# FHIR Type Schema

[![.github/workflows/ci.yaml](https://github.com/fhir-clj/type-schema/actions/workflows/ci.yaml/badge.svg)](https://github.com/fhir-clj/type-schema/actions/workflows/ci.yaml)

Status: **Draft**

This repository contains the Clojure library to generate the FHIR Type Schema from the FHIR Schema.

Recommended file extension for the FHIR Type Schema is `.ts.json`.

## JSON Schema

JSON Schema for the FHIR Type Schema is placed in <docs/type-schema.schema.json>.

How to check JSON by this schema:

```shell
$ npm install -g ajv-cli
$ ajv test -s docs/type-schema.schema.json -d docs/example.ts.json --valid
$ fd -e .ts.json | xargs -n 1 ajv test -s docs/type-schema.schema.json --valid -d
$ find . -name "*.ts.json" | xargs -n 1 ajv test -s docs/type-schema.schema.json --valid -d
```

## Local Build & Run

```shell
make build && java -jar target/type-schema.jar hl7.fhir.us.core@6.1.0 ./output-us-core

cat type-schema.ndjson | jq -c '.' | while read -r line; do id=$(echo "$line" | jq -r '.type.name'); echo "$line" | jq > "output_${id}.json"; done
```

---

TODO:

- [ ] remove obejct from `nested`. Use array.
