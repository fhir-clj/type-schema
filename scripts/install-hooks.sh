#!/bin/bash

# Ensure hooks directory exists
mkdir -p .git/hooks

# Copy the pre-push hook
cp scripts/pre-push .git/hooks/pre-push

# Make the hook executable
chmod +x .git/hooks/pre-push

echo "Git hooks installed successfully!"
