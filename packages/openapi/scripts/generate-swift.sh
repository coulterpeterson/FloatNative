#!/bin/bash
#
# Generate Swift models from Floatplane OpenAPI specification
# This script regenerates models from the centralized spec and auto-copies them to the iOS project
# Uses a temporary staging folder that is automatically cleaned up
#

set -e  # Exit on error

# Paths relative to packages/openapi/scripts/
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OPENAPI_DIR="$(dirname "$SCRIPT_DIR")"  # packages/openapi
SPEC_FILE="$OPENAPI_DIR/floatplane-openapi-specification.json"
IOS_PROJECT_DIR="$OPENAPI_DIR/../../apps/ios"
PROJECT_MODELS_DIR="$IOS_PROJECT_DIR/FloatNative/Models/Generated"

# Validate spec file exists
if [ ! -f "$SPEC_FILE" ]; then
    echo "❌ Error: OpenAPI spec not found at $SPEC_FILE"
    echo "💡 Run 'pnpm openapi:update-spec' to download the latest spec"
    exit 1
fi

# Use temporary directory for generation (auto-cleaned up)
TEMP_OUTPUT_DIR=$(mktemp -d -t floatplane-openapi)
GENERATED_MODELS_DIR="$TEMP_OUTPUT_DIR/Sources/OpenAPIClient/Models"

# Ensure cleanup on exit (even if script fails)
trap "rm -rf '$TEMP_OUTPUT_DIR'" EXIT

echo "🔄 Generating Floatplane API models..."
echo "📁 Using spec: $SPEC_FILE"
echo "📁 Using temporary staging: $TEMP_OUTPUT_DIR"

# Generate models to temporary directory
echo "🏗️  Generating Swift5 models..."
openapi-generator generate \
    -i "$SPEC_FILE" \
    -g swift5 \
    -o "$TEMP_OUTPUT_DIR" \
    --additional-properties=responseAs=AsyncAwait,useSPMFileStructure=true \
    --skip-validate-spec \
    > /dev/null 2>&1

TOTAL_GENERATED=$(find "$GENERATED_MODELS_DIR" -name "*.swift" | wc -l | tr -d ' ')
echo "✅ Generated $TOTAL_GENERATED model files"

# Define models we need (patterns for glob matching)
REQUIRED_MODELS=(
    # Delivery/CDN models (for video streaming)
    "CdnDeliveryV3*.swift"
    "EdgeDataCenter.swift"

    # Content models (for blog posts, videos, etc)
    "BlogPostModelV3*.swift"
    "ContentVideoV3Response*.swift"
    "ContentPictureV3Response*.swift"
    "ContentCreatorListV3Response.swift"
    "ContentCreatorListLastItems.swift"
    "ContentPostV3Response.swift"

    # Creator models
    "CreatorModelV3*.swift"
    "ChannelModel.swift"

    # Auth models
    "AuthLoginV2*.swift"
    "AuthLoginV3*.swift"
    "CheckFor2faLoginRequest.swift"

    # User models
    "UserModel.swift"
    "UserSelfV3Response.swift"
    "UserStatusV3Response.swift"
    "UserSubscriptionModel.swift"
    "SubscriptionsV3Response.swift"

    # Comment models
    "CommentModel*.swift"
    "CommentV3*.swift"

    # Image models
    "ImageModel.swift"
    "ChildImageModel.swift"
    "ImageFileModel.swift"

    # Subscription/Plan models
    "SubscriptionPlanModel.swift"
    "PlanInfoV2Response*.swift"

    # Attachment models
    "VideoAttachmentModel.swift"
    "AudioAttachmentModel.swift"
    "PictureAttachmentModel.swift"

    # Interaction models
    "UserInteractionModel.swift"
    "SocialLinksModel.swift"

    # Metadata models
    "PostMetadataModel.swift"

    # Misc models
    "LiveStreamModel*.swift"
    "DiscordServerModel.swift"
    "DiscordRoleModel.swift"
    "FaqSectionModel*.swift"
    "UserNotificationModel*.swift"
)

echo ""
echo "📦 Copying required models to iOS project..."

# Create project models directory if it doesn't exist
mkdir -p "$PROJECT_MODELS_DIR"

# Backup and preserve our custom helper files
CUSTOM_FILES=("JSONEncodable.swift" "ArrayRule.swift")
TEMP_DIR=$(mktemp -d)
for file in "${CUSTOM_FILES[@]}"; do
    if [ -f "$PROJECT_MODELS_DIR/$file" ]; then
        cp "$PROJECT_MODELS_DIR/$file" "$TEMP_DIR/"
    fi
done

# Models to exclude (none currently - we auto-fix issues instead)
EXCLUDED_MODELS=()

# Clear old generated models (but not the whole directory)
find "$PROJECT_MODELS_DIR" -name "*.swift" ! -name "JSONEncodable.swift" ! -name "ArrayRule.swift" -delete 2>/dev/null || true

# Copy required models using patterns
COPIED_COUNT=0
for pattern in "${REQUIRED_MODELS[@]}"; do
    for file in "$GENERATED_MODELS_DIR"/$pattern; do
        if [ -f "$file" ]; then
            FILENAME=$(basename "$file")

            # Check if this file is excluded
            EXCLUDED=false
            for excluded in "${EXCLUDED_MODELS[@]}"; do
                if [ "$FILENAME" = "$excluded" ]; then
                    EXCLUDED=true
                    break
                fi
            done

            if [ "$EXCLUDED" = false ]; then
                cp "$file" "$PROJECT_MODELS_DIR/"
                ((COPIED_COUNT++))
            fi
        fi
    done
done

# Restore custom helper files
for file in "${CUSTOM_FILES[@]}"; do
    if [ -f "$TEMP_DIR/$file" ]; then
        cp "$TEMP_DIR/$file" "$PROJECT_MODELS_DIR/"
    fi
done
rm -rf "$TEMP_DIR"

echo "✅ Copied $COPIED_COUNT OpenAPI models to apps/ios/FloatNative/Models/Generated/"

# Auto-detect and copy dependencies
echo ""
echo "🔍 Resolving dependencies..."

# Find all type references in copied files and copy missing dependencies
ITERATIONS=0
MAX_ITERATIONS=5
while [ $ITERATIONS -lt $MAX_ITERATIONS ]; do
    ADDED_THIS_ROUND=0

    # Extract type references from all Swift files in project models dir
    REFERENCED_TYPES=$(find "$PROJECT_MODELS_DIR" -name "*.swift" -exec grep -oh '\b[A-Z][a-zA-Z0-9_]*\b' {} \; | sort -u)

    for type in $REFERENCED_TYPES; do
        # Check if this type exists as a generated model
        SOURCE_FILE="$GENERATED_MODELS_DIR/${type}.swift"
        DEST_FILE="$PROJECT_MODELS_DIR/${type}.swift"
        FILENAME="${type}.swift"

        # Check if excluded
        EXCLUDED=false
        for excluded in "${EXCLUDED_MODELS[@]}"; do
            if [ "$FILENAME" = "$excluded" ]; then
                EXCLUDED=true
                break
            fi
        done

        if [ -f "$SOURCE_FILE" ] && [ ! -f "$DEST_FILE" ] && [ "$EXCLUDED" = false ]; then
            cp "$SOURCE_FILE" "$DEST_FILE"
            ((ADDED_THIS_ROUND++))
            ((COPIED_COUNT++))
        fi
    done

    if [ $ADDED_THIS_ROUND -eq 0 ]; then
        break
    fi

    ((ITERATIONS++))
done

if [ $ITERATIONS -gt 0 ]; then
    echo "✅ Resolved and copied dependency models"
fi

# Post-processing: Fix known issues in generated models
echo ""
echo "🔧 Post-processing models..."

# Fix BlogPostModelV3Channel - remove invalid Identifiable conformance
# The OpenAPI generator incorrectly adds this for oneOf/union types
BLOGPOST_CHANNEL="$PROJECT_MODELS_DIR/BlogPostModelV3Channel.swift"
if [ -f "$BLOGPOST_CHANNEL" ]; then
    # Remove the @available line and the extension line together
    sed -i '' '/@available.*macOS/,/extension BlogPostModelV3Channel: Identifiable/d' "$BLOGPOST_CHANNEL"
    # Remove any trailing empty lines at the end of file
    sed -i '' -e :a -e '/^\n*$/{$d;N;ba' -e '}' "$BLOGPOST_CHANNEL"
    echo "✅ Fixed BlogPostModelV3Channel (removed invalid Identifiable conformance)"
fi

# Cleanup happens automatically via trap on EXIT
echo "🧹 Cleaning up temporary staging folder..."

# Final summary
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✨ Generation Complete!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 Summary:"
echo "   • Total generated: $TOTAL_GENERATED models"
echo "   • Copied to project: $COPIED_COUNT models"
echo "   • Location: apps/ios/FloatNative/Models/Generated/"
echo ""
echo "✅ Models are ready to use in your Xcode project!"
echo ""
echo "💡 Note: Type aliases in MediaModels.swift map to these generated models"
echo "   Example: DeliveryInfo = CdnDeliveryV3Response"
echo ""
echo "⚠️  Important: Only FloatNative/Models/Generated/ should be in Xcode"
echo "   The temporary staging folder is automatically deleted"
