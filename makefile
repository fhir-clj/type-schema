.PHONY: install-hooks repl test clean build run format lint check fix check-json update-golden deps

all: test format lint

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
	rm -rf target .cpcache

###########################################################

check-example-json:
	find docs/examples -name "*.ts.json" | xargs -n 1 ajv test -s docs/type-schema.schema.json --valid -d

check-json:
	find . -name "*.ts.json" | xargs -n 1 ajv test -s docs/type-schema.schema.json --valid -d

check-json-fail-fast:
	find . -name "*.ts.json" | xargs -n 1 sh -c 'ajv test -s docs/type-schema.schema.json --valid -d $$0 || exit 255'm
