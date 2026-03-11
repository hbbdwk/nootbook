#!/bin/sh
# Run lint and ktlint checks
echo "Running Android Lint..."
./gradlew lint

echo ""
echo "Running ktlint..."
./gradlew ktlintCheck
