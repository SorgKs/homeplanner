#!/bin/bash
# Bash script to install and run Android app on emulator
# Automatically finds latest APK and installs it

set -e  # Exit on error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="$SCRIPT_DIR/install_output.log"
TIMESTAMP=$(date "+%Y-%m-%d %H:%M:%S")

# Start logging to file
{
    echo ""
    echo "========================================"
    echo "Install and Run - Started at $TIMESTAMP"
    echo "========================================"
    echo ""
} >> "$LOG_FILE"

# Log both to file and console
exec > >(tee -a "$LOG_FILE")
exec 2>&1

echo ""
echo "========================================"
echo "Installing and running on emulator"
echo "========================================"
echo ""

# Read paths from local.properties
LOCAL_PROPS_PATH="$SCRIPT_DIR/local.properties"
if [ ! -f "$LOCAL_PROPS_PATH" ]; then
    echo "ERROR: local.properties not found at $LOCAL_PROPS_PATH" >&2
    exit 1
fi

# Parse local.properties
SDK_DIR=""
while IFS='=' read -r key value; do
    # Skip comments and empty lines
    [[ "$key" =~ ^#.*$ ]] && continue
    [[ -z "$key" ]] && continue
    
    key=$(echo "$key" | xargs)
    value=$(echo "$value" | xargs | tr -d '"')
    
    if [ "$key" = "sdk.dir" ]; then
        SDK_DIR="$value"
    fi
done < "$LOCAL_PROPS_PATH"

if [ -z "$SDK_DIR" ]; then
    echo "ERROR: sdk.dir not defined in local.properties" >&2
    exit 1
fi

# Convert Windows path to Unix path if needed
SDK_DIR=$(echo "$SDK_DIR" | sed 's|\\|/|g')
ADB_PATH="$SDK_DIR/platform-tools/adb"

if [ ! -f "$ADB_PATH" ]; then
    echo "ERROR: adb not found at $ADB_PATH" >&2
    exit 1
fi

# Check for connected devices/emulators
echo "Checking for connected devices..."
DEVICES_OUTPUT=$("$ADB_PATH" devices 2>&1)
echo "$DEVICES_OUTPUT"

DEVICE_COUNT=$(echo "$DEVICES_OUTPUT" | grep -c "device$" || true)

if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo ""
    echo "ERROR: No devices or emulators found!" >&2
    echo "Please start an emulator or connect a device." >&2
    echo ""
    exit 1
fi

echo ""
echo "Found $DEVICE_COUNT device(s)"
echo ""

# Find latest APK (prefer release, fallback to debug)
RELEASE_APK_DIR="$SCRIPT_DIR/app/build/outputs/apk/release"
DEBUG_APK_DIR="$SCRIPT_DIR/app/build/outputs/apk/debug"

APK_FILE=""

# Try release first
if [ -d "$RELEASE_APK_DIR" ]; then
    # Look for homeplanner_v*.apk files
    RELEASE_APK=$(find "$RELEASE_APK_DIR" -name "homeplanner_v*.apk" -type f 2>/dev/null | sort -V | tail -1)
    if [ -z "$RELEASE_APK" ]; then
        # Fallback: look for any .apk file
        RELEASE_APK=$(find "$RELEASE_APK_DIR" -name "*.apk" -type f 2>/dev/null | sort -V | tail -1)
    fi
    if [ -n "$RELEASE_APK" ] && [ -f "$RELEASE_APK" ]; then
        APK_FILE="$RELEASE_APK"
        echo "Found release APK: $(basename "$APK_FILE")"
    fi
fi

# Fallback to debug
if [ -z "$APK_FILE" ] && [ -d "$DEBUG_APK_DIR" ]; then
    # Look for homeplanner_v*.apk files
    DEBUG_APK=$(find "$DEBUG_APK_DIR" -name "homeplanner_v*.apk" -type f 2>/dev/null | sort -V | tail -1)
    if [ -z "$DEBUG_APK" ]; then
        # Fallback: look for any .apk file
        DEBUG_APK=$(find "$DEBUG_APK_DIR" -name "*.apk" -type f 2>/dev/null | sort -V | tail -1)
    fi
    if [ -n "$DEBUG_APK" ] && [ -f "$DEBUG_APK" ]; then
        APK_FILE="$DEBUG_APK"
        echo "Found debug APK: $(basename "$APK_FILE")"
    fi
fi

if [ -z "$APK_FILE" ] || [ ! -f "$APK_FILE" ]; then
    echo ""
    echo "ERROR: No APK file found!" >&2
    echo ""
    echo "Searched in:" >&2
    if [ -d "$RELEASE_APK_DIR" ]; then
        echo "  - $RELEASE_APK_DIR" >&2
        echo "    Files: $(ls -1 "$RELEASE_APK_DIR"/*.apk 2>/dev/null | wc -l)" >&2
    else
        echo "  - $RELEASE_APK_DIR (directory does not exist)" >&2
    fi
    if [ -d "$DEBUG_APK_DIR" ]; then
        echo "  - $DEBUG_APK_DIR" >&2
        echo "    Files: $(ls -1 "$DEBUG_APK_DIR"/*.apk 2>/dev/null | wc -l)" >&2
    else
        echo "  - $DEBUG_APK_DIR (directory does not exist)" >&2
    fi
    echo ""
    echo "Please build the app first:" >&2
    echo "  ./build.sh          # Build release version" >&2
    echo "  ./build.sh --debug  # Build debug version" >&2
    echo "  ./gradlew :app:assembleDebug  # Or use Gradle directly" >&2
    echo ""
    exit 1
fi

echo "APK path: $APK_FILE"
APK_SIZE_MB=$(du -h "$APK_FILE" | cut -f1)
echo "APK size: $APK_SIZE_MB"
echo ""

# Install APK
echo "Installing APK on device..."
if ! "$ADB_PATH" install -r "$APK_FILE"; then
    echo ""
    echo "========================================"
    echo "INSTALLATION FAILED!"
    echo "========================================"
    echo ""
    exit 1
fi

echo ""
echo "Installation successful!"
echo ""

# Launch app
PACKAGE_NAME="com.homeplanner"
MAIN_ACTIVITY="com.homeplanner.MainActivity"

echo "Launching app..."
if "$ADB_PATH" shell am start -n "$PACKAGE_NAME/$MAIN_ACTIVITY" > /dev/null 2>&1; then
    echo ""
    echo "========================================"
    echo "App launched successfully!"
    echo "========================================"
    echo ""
else
    echo ""
    echo "WARNING: App may have failed to launch" >&2
    echo ""
fi

echo "Log saved to: $LOG_FILE"

