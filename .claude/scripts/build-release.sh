#!/bin/sh
# Build release APK
./gradlew assembleRelease
echo "APK location: app/build/outputs/apk/release/"
