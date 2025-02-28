.PHONY: repl test clean build run format lint

# Start a REPL
repl:
	clj -M:dev -m nrepl.cmdline --middleware "[cider.nrepl/cider-middleware]"

# Run tests
test:
	clj -M:test

update-golden:
	UPDATE_GOLDEN=true clj -M:test

lint:
	clj -M:lint

# Clean target directory
clean:
	rm -rf target
	rm -rf .cpcache

# Build uberjar
build:
	clj -T:build uber

# Run theapplication
run:
	clj -M -m transpiler.core

# Format code (requires cljfmt)
format:
	clj -M:cljfmt fix

# Install dependencies
deps:
	clj -P
