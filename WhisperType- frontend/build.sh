#!/bin/bash

# Set JAVA_HOME to Android Studio's JDK
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

echo "Using Java from: $JAVA_HOME"
java -version

echo ""
echo "Cleaning project..."
./gradlew clean

echo ""
echo "Building debug APK..."
./gradlew assembleDebug

echo ""
echo "✅ Build complete! APK location:"
find app/build/outputs -name "*.apk" -type f
