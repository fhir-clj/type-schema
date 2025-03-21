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
