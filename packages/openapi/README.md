# OpenAPI Package

Centralized OpenAPI specification and client generation for the Floatplane API.

## Overview

This package houses the Floatplane API OpenAPI specification and provides scripts to generate type-safe API clients for iOS (Swift) and Android (Kotlin).

## Spec Source

**File**: `floatplane-openapi-specification.json`

**Origin**: Community-maintained [FloatplaneAPI](https://github.com/jamamp/FloatplaneAPI)

**Version**: 3.10.0-c

The spec is version-controlled in this repository to ensure build reproducibility. It can be updated manually using:

```bash
pnpm openapi:update-spec
```

## Generation Scripts

### Swift (iOS/tvOS)

Generates Swift 5 models with async/await support for the iOS app.

```bash
pnpm openapi:generate:swift
```

**Output**: `apps/ios/FloatNative/Models/Generated/`

**Features**:
- Selective model copying (only includes models actually used by the app)
- Automatic dependency resolution
- Post-processing fixes for known generator bugs
- Preserves custom helper files (`JSONEncodable.swift`, `ArrayRule.swift`)

### Kotlin (Android) - Planned

```bash
pnpm openapi:generate:kotlin
```

**Status**: Not yet implemented (placeholder script)

## Custom Model Overrides

While most models are auto-generated, the iOS app maintains custom wrappers and extensions in `apps/ios/FloatNative/Models/` for:

### 1. **API Spec Gaps**
- **`BlogPostDetailedWithInteraction`**: Adds missing `selfUserInteraction` field not in OpenAPI spec
- **`WatchHistoryBlogPost`**: Different response format for watch history endpoint

### 2. **Convenience Wrappers**
- **Type aliases**: `BlogPost`, `Creator`, `DeliveryInfo`, etc. (shorthand for verbose generated names)
- **Extensions**: Helper methods on generated models (`ImageModel.fullURL`, `DeliveryInfo.bestThumbnail`)

### 3. **Companion API Models**
- **`CompanionModels.swift`**: Models for the proprietary Companion API (Watch Later, Playlists, Enhanced Search)
- Not part of official Floatplane API, so not in OpenAPI spec

## Known Spec Gaps

1. **Missing field**: `selfUserInteraction` in `ContentPostV3Response`
   - **Impact**: Shows which reaction (like/dislike) the user made on a post
   - **Workaround**: `BlogPostDetailedWithInteraction` wrapper
   - **Status**: Could be contributed back to community spec

2. **Generator bug**: Invalid `Identifiable` conformance on union types
   - **Impact**: `BlogPostModelV3Channel` fails to compile
   - **Workaround**: Post-processing script removes invalid conformance

## Updating the Spec

1. Download latest spec:
   ```bash
   pnpm openapi:update-spec
   ```

2. Test generation:
   ```bash
   pnpm openapi:generate:swift
   ```

3. Verify in Xcode that generated models compile and work correctly

4. Commit both the spec and newly generated models

## Contributing Back

If you discover missing fields or API inconsistencies:

1. Document the finding with network inspector proof
2. Open an issue on [FloatplaneAPI](https://github.com/jamamp/FloatplaneAPI)
3. Submit a PR with the spec change
4. Update your local spec once merged

This helps the entire Floatplane development community!
