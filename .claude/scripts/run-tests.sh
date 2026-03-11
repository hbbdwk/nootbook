#!/bin/sh
# Run unit tests and instrumented tests
echo "Running unit tests..."
./gradlew test
echo ""
echo "Running instrumented tests..."
./gradlew connectedAndroidTest
