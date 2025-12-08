#!/bin/bash
# Bash script to launch Android emulator using paths from local.properties.
# Usage:
#     ./start_emulator.sh                # uses avd.name from local.properties or default
#     ./start_emulator.sh Pixel_6_34     # start specific AVD

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

LOCAL_PROPS_PATH="$SCRIPT_DIR/local.properties"
if [ ! -f "$LOCAL_PROPS_PATH" ]; then
    echo "ERROR: local.properties not found at $LOCAL_PROPS_PATH" >&2
    exit 1
fi

# Parse local.properties
SDK_DIR=""
AVD_NAME=""

while IFS='=' read -r key value; do
    # Skip comments and empty lines
    [[ "$key" =~ ^[[:space:]]*#.*$ ]] && continue
    [[ -z "$key" ]] && continue
    
    key=$(echo "$key" | xargs)
    value=$(echo "$value" | xargs | tr -d '"')
    
    if [ "$key" = "sdk.dir" ]; then
        SDK_DIR="$value"
    elif [ "$key" = "avd.name" ]; then
        AVD_NAME="$value"
    fi
done < "$LOCAL_PROPS_PATH"

if [ -z "$SDK_DIR" ]; then
    echo "ERROR: sdk.dir not defined in local.properties" >&2
    exit 1
fi

# Convert Windows path to Unix path if needed
SDK_DIR=$(echo "$SDK_DIR" | sed 's|\\|/|g')
EMULATOR_PATH="$SDK_DIR/emulator/emulator"

if [ ! -f "$EMULATOR_PATH" ]; then
    echo "ERROR: Emulator binary not found at $EMULATOR_PATH" >&2
    exit 1
fi

# Use AVD name from argument, or from local.properties, or default
if [ -n "$1" ]; then
    AVD_NAME="$1"
elif [ -z "$AVD_NAME" ]; then
    # Try to find a suitable AVD
    AVAILABLE_AVDS=$("$EMULATOR_PATH" -list-avds 2>/dev/null)
    if echo "$AVAILABLE_AVDS" | grep -q "Pixel_API_34"; then
        AVD_NAME="Pixel_API_34"
    elif echo "$AVAILABLE_AVDS" | grep -q "Medium_Phone_API_36.1"; then
        AVD_NAME="Medium_Phone_API_36.1"
    elif [ -n "$AVAILABLE_AVDS" ]; then
        # Use first available AVD
        AVD_NAME=$(echo "$AVAILABLE_AVDS" | head -n1)
    else
        echo "ERROR: No AVDs found. Please create an AVD first." >&2
        exit 1
    fi
fi

echo "Starting Android emulator: $AVD_NAME"
echo "Emulator binary: $EMULATOR_PATH"
echo ""

# Start emulator in background
"$EMULATOR_PATH" -avd "$AVD_NAME" -netdelay none -netspeed full > /dev/null 2>&1 &

echo ""
echo "Emulator launching. Use 'adb devices' to verify once booted."
echo ""

