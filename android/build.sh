#!/bin/bash
# Bash script to build Android app
# Usage: ./build.sh [--debug|-d]
#   --debug, -d: Build debug version (doesn't increment version, doesn't require signing)

set -e  # Exit on error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="$SCRIPT_DIR/build_output.log"

# Configure logging
exec > >(tee -a "$LOG_FILE")
exec 2>&1

echo ""
echo "Logging build output to $LOG_FILE"
echo ""

# Check for debug flag
BUILD_DEBUG=false
for arg in "$@"; do
    if [ "$arg" = "--debug" ] || [ "$arg" = "-d" ]; then
        BUILD_DEBUG=true
        break
    fi
done

echo ""
echo "========================================"
if [ "$BUILD_DEBUG" = true ]; then
    echo "Building debug version (patch will be incremented)"
else
    echo "Building release version (patch will be incremented)"
fi
echo "========================================"
echo ""

# Read java.home from local.properties if it exists
LOCAL_PROPS_PATH="$SCRIPT_DIR/local.properties"
if [ -f "$LOCAL_PROPS_PATH" ]; then
    JAVA_HOME_FROM_PROPS=$(grep "^java.home=" "$LOCAL_PROPS_PATH" | cut -d'=' -f2 | tr -d '"' | tr -d ' ')
    if [ -n "$JAVA_HOME_FROM_PROPS" ]; then
        export JAVA_HOME="$JAVA_HOME_FROM_PROPS"
        echo "Using JDK from local.properties: $JAVA_HOME"
    fi
fi

# Read and increment version BEFORE build (so APK has the new version)
VERSION_FILE="$SCRIPT_DIR/version.json"
NEW_PATCH=""

if [ ! -f "$VERSION_FILE" ]; then
    echo "ERROR: version.json not found at $VERSION_FILE" >&2
    exit 1
fi

# Check if jq is available for JSON parsing
if command -v jq &> /dev/null; then
    CURRENT_PATCH=$(jq -r '.patch' "$VERSION_FILE")
    
    if [ "$CURRENT_PATCH" = "null" ] || [ -z "$CURRENT_PATCH" ]; then
        echo "ERROR: Could not read patch version from version.json" >&2
        exit 1
    fi
    
    echo "Current patch version: $CURRENT_PATCH"
    
    # Increment patch version BEFORE build (always, for both debug and release)
    NEW_PATCH=$((CURRENT_PATCH + 1))
    echo "Incrementing patch version: $CURRENT_PATCH -> $NEW_PATCH"
    
    # Update version.json BEFORE build
    jq ".patch = $NEW_PATCH" "$VERSION_FILE" > "$VERSION_FILE.tmp" && mv "$VERSION_FILE.tmp" "$VERSION_FILE"
    
    echo "APK will be built with version: $NEW_PATCH"
    echo ""
else
    # Fallback: manual JSON parsing if jq is not available
    echo "WARNING: jq not found, using manual JSON parsing"
    CURRENT_PATCH=$(grep -o '"patch"[[:space:]]*:[[:space:]]*[0-9]*' "$VERSION_FILE" | grep -o '[0-9]*')
    
    if [ -z "$CURRENT_PATCH" ]; then
        echo "ERROR: Could not read patch version from version.json" >&2
        exit 1
    fi
    
    echo "Current patch version: $CURRENT_PATCH"
    
    # Increment patch version BEFORE build (always, for both debug and release)
    NEW_PATCH=$((CURRENT_PATCH + 1))
    echo "Incrementing patch version: $CURRENT_PATCH -> $NEW_PATCH"
    
    # Update version.json manually
    sed -i "s/\"patch\"[[:space:]]*:[[:space:]]*$CURRENT_PATCH/\"patch\": $NEW_PATCH/" "$VERSION_FILE"
    
    echo "APK will be built with version: $NEW_PATCH"
    echo ""
fi

cd "$SCRIPT_DIR"

# Clean previous build
echo "Cleaning previous build..."
if ! ./gradlew clean > /dev/null 2>&1; then
    echo "WARNING: Clean failed, but continuing..." >&2
fi

echo ""
if [ "$BUILD_DEBUG" = true ]; then
    echo "Building debug APK..."
else
    echo "Building release APK..."
fi
echo ""

# Build APK
if [ "$BUILD_DEBUG" = true ]; then
    if ! ./gradlew assembleDebug; then
        echo ""
        echo "========================================"
        echo "BUILD FAILED!"
        echo "========================================"
        echo ""
        exit 1
    fi
else
    if ! ./gradlew assembleRelease; then
        echo ""
        echo "========================================"
        echo "BUILD FAILED!"
        echo "========================================"
        echo ""
        exit 1
    fi
fi

echo ""
echo "========================================"
echo "BUILD SUCCESSFUL!"
echo "========================================"
echo ""

# Find the APK file
if [ "$BUILD_DEBUG" = true ]; then
    APK_DIR="$SCRIPT_DIR/app/build/outputs/apk/debug"
else
    APK_DIR="$SCRIPT_DIR/app/build/outputs/apk/release"
fi

# Look for homeplanner_v*.apk files
APK_FILE=$(find "$APK_DIR" -name "homeplanner_v*.apk" -type f 2>/dev/null | sort -V | tail -1)
if [ -z "$APK_FILE" ]; then
    # Fallback: look for any .apk file
    APK_FILE=$(find "$APK_DIR" -name "*.apk" -type f 2>/dev/null | sort -V | tail -1)
fi

if [ -n "$APK_FILE" ] && [ -f "$APK_FILE" ]; then
    echo "APK file created:"
    echo "$APK_FILE"
    echo ""
    
    APK_SIZE_MB=$(du -h "$APK_FILE" | cut -f1)
    echo "File size: $APK_SIZE_MB"
    echo ""
else
    echo "WARNING: Could not find APK file in $APK_DIR" >&2
    echo "Please check the build output manually." >&2
fi

echo ""
echo "Build completed successfully!"
if [ -n "$NEW_PATCH" ]; then
    NEXT_PATCH=$((NEW_PATCH + 1))
    echo "Next build will use patch: $NEXT_PATCH"
fi
echo ""

# Ask if user wants to install
read -p "Do you want to install on connected device? (Y/N) " install
if [ "$install" = "Y" ] || [ "$install" = "y" ]; then
    echo ""
    echo "Installing on device..."
    if [ "$BUILD_DEBUG" = true ]; then
        if ./gradlew :app:installDebug; then
            echo ""
            echo "Installation completed successfully!"
        else
            echo ""
            echo "Installation failed. Make sure device is connected with USB debugging enabled." >&2
            exit 1
        fi
    else
        if ./gradlew :app:installRelease; then
            echo ""
            echo "Installation completed successfully!"
        else
            echo ""
            echo "Installation failed. Make sure device is connected with USB debugging enabled." >&2
            exit 1
        fi
    fi
fi

echo ""
echo "Build log saved to: $LOG_FILE"

