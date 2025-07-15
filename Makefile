.PHONY: install-hooks repl test clean build run format lint check fix check-json update-golden deps release test-cli

all: format test lint format-json check-json

install-hooks:
	@bash scripts/install-hooks.sh

deps:
	clj -P

repl:
	clj -M:dev -m nrepl.cmdline --middleware "[cider.nrepl/cider-middleware]"

check: test test-cli lint format-check

###########################################################

test:
	clj -M:test

test-cli: build
	@echo "> Test 1: Version check"
	java -jar target/type-schema.jar --version
	@echo "> Test 2: Help display"
	java -jar target/type-schema.jar --help
	@echo "> Test 3: Basic package processing (redirecting output)"
	java -jar target/type-schema.jar hl7.fhir.r4.core@4.0.1 > /dev/null
	@echo "> Test 4: Verbose output"
	java -jar target/type-schema.jar -v hl7.fhir.r4.core@4.0.1 > /dev/null
	@echo "> Test 5: Output to NDJSON file"
	java -jar target/type-schema.jar -o target/test-output.ndjson hl7.fhir.r4.core@4.0.1
	@echo "> Test 6: Output to directory"
	java -jar target/type-schema.jar -o target/test-output hl7.fhir.r4.core@4.0.1
	@echo "> Test 7: Output separate files"
	java -jar target/type-schema.jar -o target/test-output-separate --separated-files hl7.fhir.r4.core@4.0.1
	@echo "> Test 8: Error case - missing package"
	java -jar target/type-schema.jar 2>/dev/null || echo "Error case correctly handled"
	@echo "> Test 9: Error case - separated files without output directory"
	java -jar target/type-schema.jar --separated-files hl7.fhir.r4.core@4.0.1 2>/dev/null || echo "Error case correctly handled"

update-golden:
	UPDATE_GOLDEN=true clj -M:test
	make format-json

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
	find . -name "*.ts.json"| xargs -P0 -n 1 rm

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
	find test docs -name "*.ts.json" | xargs -P4 -n 1 ajv test -s docs/type-schema.schema.json --valid -d

check-json-fail-fast:
	find test docs -name "*.ts.json" | xargs -n 1 sh -c 'ajv test -s docs/type-schema.schema.json --valid -d $$0 || exit 255'

check-output-json:
	find output -name "*.json" | grep -v ndjson | xargs -P0 -n 1 ajv test -s docs/type-schema.schema.json --valid -d

check-output-json-fail-fast:
	find output -name "*.json" | grep -v ndjson | xargs -P1 -n 1 sh -c 'ajv test -s docs/type-schema.schema.json --valid -d $$0 || exit 255'

format-json: format-golden-json format-example-json

format-example-json:
	prettier -w docs/**/*.json

format-golden-json:
	find test/golden -name "*.json" -type f -exec sh -c 'jq . "{}" > "{}.tmp" && mv "{}.tmp" "{}"' \;

###########################################################

release: lint test format-check
	@VERSION=$$(grep -E '^\(def version "[^"]+"\)' src/main.clj | sed -E 's/^\(def version "([^"]+)"\)/\1/'); \
	TAG="v$$VERSION"; \
	COMMIT_VERSION=$$(git rev-parse --short HEAD); \
	if git rev-parse "$$TAG" >/dev/null 2>&1; then \
		echo "Error: Tag $$TAG already exists. Please update the version in src/main.clj."; \
		exit 1; \
	fi; \
	echo "Creating release $$VERSION with tag $$TAG (commit $$COMMIT_VERSION)"; \
	git tag -a "$$TAG" -m "Release $$VERSION ($$COMMIT_VERSION)"; \
	git push origin "$$TAG"; \
	echo "Tag $$TAG created and pushed to remote origin"
