# FloatNative

A modern, native iOS/tvOS client for Floatplane with liquid glass UI design, background audio playback, playlists, enhanced LTT search, Picture-in-Picture support, and more.

## Features:
* Clean, streamlined, native iOS design
* Watch Later and Playlists (through supplementary API)
* "Enhanced LTT Search" (through supplementary API)
* Token login and QR code token login (to workaround the shut down V2 auth)
* Native Picture-in-Picture support
* Background play
* Video download
* Tappable timestamps in video description
* Light/Dark mode
* iPad styles/support
* Full-featured tvOS app from same codebase
  * Long press menu
  * Custom player controls
* Screen stays awake during playback
* Pretty icon that supports clear/tinted variants
* Watch progress is saved very regularly
* Displays watch progress

## Architecture

FloatNative uses a dual-API approach:

- **Floatplane API** Official 1st party API for accessing Floatplane content (videos, posts, creators, subscriptions)
- **Companion API** (`packages/api`) - Custom Cloudflare Worker providing enhanced features:
  - Watch Later and custom playlists
  - Enhanced LTT content search

For the Companion API documentation, [see its readme here](packages/api/README.md).

The iOS app integrates with both APIs: auto-generated Swift models from the community-maintained Floatplane API spec for content delivery, and a custom client for companion features.

## Repository Structure

This is a pnpm monorepo containing a native iOS application and a Cloudflare Worker API.

```
floatnative/
├── packages/
│   └── api/              # Companion API (Cloudflare Worker)
│
└── apps/
    └── ios/              # Native iOS app (SwiftUI)
    └── android/          # Placeholder for native Android app (Kotlin)
```

### Package Details

#### `packages/api`

Cloudflare Worker providing companion features for the FloatNative iOS app.

- **Tech**: TypeScript, Cloudflare Workers, Hono, Drizzle ORM, D1 Database
- **Features**: Watch Later, Playlists, Enhanced LTT Search
- **Deploy**: `pnpm api:deploy`

#### `apps/ios`

Native iOS application built with SwiftUI.

- **Requirements**: Xcode 16.4+, iOS 18.5+, Swift 5.0
- **Platforms**: iOS, tvOS
- **API Integration**:
  - Auto-generated models from community FloatplaneAPI specification
  - Custom Companion API client for enhanced features
  - Secure credential storage via iOS Keychain

## Getting Started

### Prerequisites

- Node.js 18+
- pnpm 8+
- Xcode 16.4+ (for iOS development)

### Setup

1. Clone the repository

2. Install dependencies:
   ```bash
   pnpm install
   ```

3. Open the iOS project in Xcode:
   ```bash
   open apps/ios/FloatNative.xcodeproj
   ```

## Development Workflow

### API Development

Start local development server:
```bash
pnpm api:dev
```

Stream live logs from deployed worker:
```bash
pnpm api:tail
```

Deploy to Cloudflare:
```bash
pnpm api:deploy
```

### Database Management

Generate database migrations:
```bash
pnpm api:db:generate
```

Run migrations locally:
```bash
pnpm api:db:migrate:local
```

Run migrations on production:
```bash
pnpm api:db:migrate:remote
```

### iOS Development

Open `apps/ios/FloatNative.xcodeproj` in Xcode and build/run the project.

The iOS app auto-generates API models from the community FloatplaneAPI specification during build. See `apps/ios/generate-api-models.sh` for details.

## 🙏 Acknowledgments

- The Community-maintained (FloatplaneAPI)[https://github.com/jamamp/FloatplaneAPI]
- (Hydravion-AndroidTV)[https://github.com/bmlzootown/Hydravion-AndroidTV] for providing a great basis of code to draw inspiration and reverse-engineering from
- (Wasserflug-tvOS)[https://github.com/jamamp/Wasserflug-tvOS] for providing a great basis of code to draw inspiration and reverse-engineering from (and for giving my wife and I a convenient tvOS app we use every day)
- (The Apple Docs MCP server)[https://github.com/kimsungwhee/apple-docs-mcp]
- Claude Code (why hide it)
- The Floatplane team for all their incredible work building this great service and putting up with nerds like me 👏

## License

MIT License - see [LICENSE](LICENSE) for details.

This is an unofficial third-party client and is not affiliated with Floatplane Media Inc.
