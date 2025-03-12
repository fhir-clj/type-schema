# FHIR Type Schema

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
