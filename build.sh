#!/bin/bash
# StudyGuard Build Script
# Builds APK without Android Studio - just requires JDK 17+ and Android SDK

set -e

echo "========================================"
echo "  StudyGuard - APK Build Script"
echo "========================================"

# Check for Java
if ! command -v java &> /dev/null; then
    echo "ERROR: Java not found. Please install JDK 17 or higher."
    echo "Download: https://adoptium.net/"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt "17" ]; then
    echo "ERROR: JDK 17+ required. Found version: $JAVA_VERSION"
    exit 1
fi

echo "Java version: $(java -version 2>&1 | head -1)"

# Check for ANDROID_SDK_ROOT or ANDROID_HOME
if [ -z "$ANDROID_SDK_ROOT" ] && [ -z "$ANDROID_HOME" ]; then
    echo "WARNING: ANDROID_SDK_ROOT or ANDROID_HOME not set."
    echo "Set it to your Android SDK path, e.g.:"
    echo "  export ANDROID_SDK_ROOT=\$HOME/Android/Sdk"
    echo ""
    echo "Attempting to auto-detect..."
    # Common locations
    for path in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" "/opt/android-sdk" "/usr/lib/android-sdk"; do
        if [ -d "$path" ]; then
            export ANDROID_SDK_ROOT="$path"
            echo "Found Android SDK at: $path"
            break
        fi
    done
fi

if [ -z "$ANDROID_SDK_ROOT" ] && [ -z "$ANDROID_HOME" ]; then
    echo "ERROR: Android SDK not found."
    echo "Install it from: https://developer.android.com/studio#downloads"
    echo "Or set ANDROID_SDK_ROOT manually."
    exit 1
fi

# Use gradlew
GRADLE_CMD="./gradlew"

# Download wrapper if needed
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "Downloading Gradle wrapper..."
    mkdir -p gradle/wrapper
    curl -L -o "gradle/wrapper/gradle-wrapper.jar" \
        "https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar" \
        2>/dev/null || echo "Could not download wrapper - trying local gradle"
fi

# If wrapper jar still not available, try system gradle
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    if command -v gradle &> /dev/null; then
        GRADLE_CMD="gradle"
        echo "Using system gradle"
    else
        echo "ERROR: No Gradle found. Install Gradle or download the wrapper jar."
        exit 1
    fi
fi

echo ""
echo "Starting build..."
echo ""

# Build APK
$GRADLE_CMD assembleRelease --no-daemon

echo ""
echo "========================================"
echo "  Build Complete!"
echo "========================================"
echo ""
echo "APK Location:"
echo "  app/build/outputs/apk/release/app-release.apk"
echo ""
echo "To install on a connected device:"
echo "  adb install app/build/outputs/apk/release/app-release.apk"
echo ""

# Also list the APK file
if [ -f "app/build/outputs/apk/release/app-release.apk" ]; then
    ls -lh app/build/outputs/apk/release/app-release.apk
fi
