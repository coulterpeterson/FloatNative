# FloatNative

<table align="center"><tr>
  <td>
    <a href="https://apps.apple.com/ca/app/floatnative/id6754177516">
      <img alt="Download on the App Store"
           src="https://tools.applemediaservices.com/api/badges/download-on-the-app-store/black/en-us?size=250x83"
           height="40">
    </a>
  </td>
  <td>
    <a href="https://play.google.com/store/apps/details?id=com.coulterpeterson.floatnative">
      <img alt="Get it on Google Play"
           src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"
           height="60">
    </a>
  </td>
  <td>
    <a href="https://coulterpeterson.com/floatnative/">
      <img alt="APK Download"
           src="https://img.shields.io/badge/APK_Download-181717?style=for-the-badge&logo=android&logoColor=white"
           height="40">
    </a>
  </td>
</tr></table>

A modern, native client for [Floatplane](https://www.floatplane.com) — built from scratch for iPhone, iPad, Apple TV, Android phone, Android TV, and Fire TV.

## Highlights

- **Native everywhere.** SwiftUI on Apple platforms, Jetpack Compose on Android. Not a wrapped web view.
- **Closed captions** on every platform, with system-styling support.
- **Picture-in-picture and background audio** on iPhone, iPad, and Android.
- **Watch Later, custom Playlists, and Enhanced LTT search** via a companion API.
- **Simple Floatplane OAuth login** — sign in with your Floatplane account through Floatplane's official OAuth flow; tokens are DPoP-bound to a private key in the device Keychain / Keystore so they can't be replayed if intercepted.
- **Player polish**: sleep timer, force-landscape, screen-stay-awake, playback-speed memory, tappable timestamps in descriptions, watch-progress restore, multi-part post support.
- **TV-first TV apps** for tvOS, Android TV, and Fire TV — not phone apps stretched onto a 65" screen.

## Repository layout

This is a pnpm + Gradle monorepo.

```
floatnative/
├── apps/
│   ├── ios/               # SwiftUI app — iOS, iPadOS, tvOS
│   ├── android/           # Jetpack Compose app — Android phone, Android TV, Fire TV
│   └── chrome-extension/  # Companion extension for Floatplane playlists in the web UI
│
├── packages/
│   ├── api/               # Companion API — original Cloudflare Workers (TS, Hono, D1)
│   ├── api-go/            # Companion API — Go reimplementation for self-hosting (chi, Postgres)
│   ├── api-php/           # Companion API — PHP 8.4 port of api-go for CloudPanel (MySQL/MariaDB)
│   └── openapi/           # Community-maintained OpenAPI spec + Swift/Kotlin model generation
│
└── tools/
    └── floatcli/          # Diagnostic CLI that mirrors what the apps do, for reproducing bugs
```

## Getting started

### iOS / iPadOS / tvOS

```bash
pnpm install
open apps/ios/FloatNative.xcodeproj
```

Requirements: Xcode 16.4+, iOS 17+ / tvOS 17+.

### Android / Android TV / Fire TV

Open `apps/android/` in Android Studio (Hedgehog or newer) and let Gradle sync. Min SDK 26, target SDK 36.

### Companion API

```bash
pnpm api:dev           # Cloudflare Workers local dev
pnpm api:deploy        # ship to Cloudflare
```

For the Go variant (self-hosting on a Linux box with Postgres), see [`packages/api-go/README.md`](packages/api-go/README.md). For the PHP port of it (plain PHP 8.4 + MySQL/MariaDB, e.g. on CloudPanel), see [`packages/api-php/README.md`](packages/api-php/README.md).

### Regenerate API models

```bash
pnpm openapi:generate:swift    # iOS models
pnpm openapi:generate:kotlin   # Android models
pnpm openapi:generate:all      # both
pnpm openapi:update-spec       # pull latest community spec
```

Spec and generation scripts live in [`packages/openapi/`](packages/openapi/).

## Authentication & DPoP

Floatplane's V2 auth uses **DPoP (Demonstrating Proof-of-Possession)** to bind OAuth tokens to a device-held private key, so intercepted tokens can't be replayed elsewhere.

Both apps store the DPoP key in the platform's secure enclave (iOS Keychain, Android Keystore) and sign every request with a fresh proof. For HLS playback — where neither AVPlayer nor ExoPlayer can sign individual segment requests — we use a **manifest interception** strategy: a custom resource loader signs the master playlist and key requests with DPoP, then rewrites segment URIs to absolute `https://` so the player streams them natively without DPoP. The backend is designed around this split: keys require session-bound auth, segments don't.

## Acknowledgments

- The community-maintained [FloatplaneAPI](https://github.com/jamamp/FloatplaneAPI) spec
- [Hydravion-AndroidTV](https://github.com/bmlzootown/Hydravion-AndroidTV) — invaluable as a reverse-engineering reference
- [Wasserflug-tvOS](https://github.com/jamamp/Wasserflug-tvOS) — a great tvOS client my wife and I used every day for years, and a strong codebase to learn from
- [apple-docs MCP](https://github.com/kimsungwhee/apple-docs-mcp)
- Claude Code (why hide it)
- The Floatplane team — for building a service worth writing a client for, and for putting up with nerds like me

## License

MIT — see [LICENSE](LICENSE).

This is an unofficial third-party client and is not affiliated with Floatplane Media Inc.
