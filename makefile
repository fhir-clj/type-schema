.PHONY: install-hooks repl test clean build run format lint check fix check-json update-golden deps

all: test format lint format-example-json check-json

install-hooks:
	@bash scripts/install-hooks.sh

deps:
	clj -P

repl:
	clj -M:dev -m nrepl.cmdline --middleware "[cider.nrepl/cider-middleware]"

check: test lint format-check

fix: format update-golden

###########################################################

test:
	clj -M:test

update-golden:
	UPDATE_GOLDEN=true clj -M:test

lint:
	clj -M:lint

format:
	clj -M:format fix

format-check:
	clj -M:format check

###########################################################

run:
	clj -M -m transpiler.core

build:
	clj -T:build uber

clean:
	rm -rf target .cpcache output

###########################################################

r4-ndjson:
	mkdir -p output
	java -jar target/type-schema.jar hl7.fhir.r4.core@4.0.1 ./output

r4-ts-json: build r4-ndjson
	@PACKAGE=hl7.fhir.r4.core@4.0.1 && \
	OUTPUT_DIR=output && \
	mkdir -p "$$OUTPUT_DIR/$$PACKAGE" && \
	cat "$$OUTPUT_DIR/$$PACKAGE.ndjson" | jq -c "." | while read -r line; do \
		name=$$(echo "$$line" | jq -r ".identifier.name"); \
		if [ -z "$$name" ]; then \
			continue; \
		fi; \
		out_file=$$OUTPUT_DIR/$$PACKAGE/$$(echo $$name | tr " " "_").ts.json; \
		echo $$out_file; \
		echo "$$line" | jq "." > "$$out_file"; \
	done

check-example-json:
	find docs/examples -name "*.ts.json" | xargs -P4 -n 1 ajv test -s docs/type-schema.schema.json --valid -d

check-json:
	find . -name "*.ts.json" | xargs -P4 -n 1 ajv test -s docs/type-schema.schema.json --valid -d

check-json-fail-fast:
	find . -name "*.ts.json" | xargs -n 1 sh -c 'ajv test -s docs/type-schema.schema.json --valid -d $$0 || exit 255'

check-output-json:
	find output -name "*.json" | grep -v ndjson | xargs -P0 -n 1 ajv test -s docs/type-schema.schema.json --valid -d

check-output-json-fail-fast:
	find output -name "*.json" | grep -v ndjson | xargs -P1 -n 1 sh -c 'ajv test -s docs/type-schema.schema.json --valid -d $$0 || exit 255'

format-example-json:
	prettier -w docs/**/*.json
