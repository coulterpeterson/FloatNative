#!/bin/bash
#
# Generate Kotlin models from Floatplane OpenAPI specification
# This script regenerates models from the centralized spec and auto-copies them to the Android project
#

set -e  # Exit on error

# Paths relative to packages/openapi/scripts/
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OPENAPI_DIR="$(dirname "$SCRIPT_DIR")"  # packages/openapi
SPEC_FILE="$OPENAPI_DIR/floatplane-openapi-specification.json"
ANDROID_PROJECT_DIR="$OPENAPI_DIR/../../apps/android"
# Target package location
TARGET_PACKAGE_DIR="$ANDROID_PROJECT_DIR/app/src/main/java/com/coulterpeterson/floatnative/openapi"

# Validate spec file exists
if [ ! -f "$SPEC_FILE" ]; then
    echo "❌ Error: OpenAPI spec not found at $SPEC_FILE"
    echo "💡 Run 'pnpm openapi:update-spec' to download the latest spec"
    exit 1
fi

# Use temporary directory for generation (auto-cleaned up)
TEMP_OUTPUT_DIR=$(mktemp -d -t floatplane-openapi-kotlin)

# Ensure cleanup on exit
trap "rm -rf '$TEMP_OUTPUT_DIR'" EXIT

echo "🔄 Generating Floatplane API Kotlin models..."
echo "📁 Using spec: $SPEC_FILE"
echo "📁 Using temporary staging: $TEMP_OUTPUT_DIR"

# Generate models to temporary directory
echo "🏗️  Generating Kotlin models..."
# Using jvm-retrofit2 library with moshi serialization
# We set the package name to match our target structure
openapi-generator generate \
    -i "$SPEC_FILE" \
    -g kotlin \
    -o "$TEMP_OUTPUT_DIR" \
    --additional-properties=library=jvm-retrofit2,serializationLibrary=moshi,packageName=com.coulterpeterson.floatnative.openapi,useCoroutines=true \
    --skip-validate-spec \
    > /dev/null 2>&1

echo "✅ Generation successful"

echo ""
echo "📦 Copying generated code to Android project..."

# Create target directory if it doesn't exist
mkdir -p "$TARGET_PACKAGE_DIR"

# Clean old generated code
# Be careful not to delete manual extensions if they are mixed in, but ideally extensions should be in a separate directory/package
# For now, we assume this directory is owned by the generator
rm -rf "$TARGET_PACKAGE_DIR"/*

# Copy the generated source code
# The generator outputs to src/main/kotlin/com/coulterpeterson/floatnative/openapi
# We move that content to our target content
SOURCE_CODE_DIR="$TEMP_OUTPUT_DIR/src/main/kotlin/com/coulterpeterson/floatnative/openapi"

if [ -d "$SOURCE_CODE_DIR" ]; then
    cp -r "$SOURCE_CODE_DIR/" "$TARGET_PACKAGE_DIR/"
    echo "✅ Copied files to $TARGET_PACKAGE_DIR"
else
    echo "❌ Error: Generated source directory not found at $SOURCE_CODE_DIR"
    exit 1
fi

TOTAL_FILES=$(find "$TARGET_PACKAGE_DIR" -name "*.kt" | wc -l | tr -d ' ')

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✨ Kotlin Generation Complete!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 Summary:"
echo "   • Total generated files: $TOTAL_FILES"
echo "   • Location: apps/android/app/src/main/java/com/coulterpeterson/floatnative/openapi"
echo ""

