# BlockMesh

Serverless mesh VPN with a built-in DNS sinkhole. No cloud, no subscriptions, no Pi-hole box.

The Go core handles DNS filtering and ad-blocking. Native Android/iOS apps wrap it via gomobile. Devices talk peer-to-peer — your phone becomes the firewall.

[![Build](https://github.com/muxd22-alt/BlockMesh/actions/workflows/build.yml/badge.svg)](https://github.com/muxd22-alt/BlockMesh/actions/workflows/build.yml)

## Install

Grab the latest APK from [Actions → build-android → Artifacts](https://github.com/muxd22-alt/BlockMesh/actions/workflows/build.yml).

Or build it yourself:

```bash
# one-liner: clone, build core, build apk
git clone https://github.com/muxd22-alt/BlockMesh.git && cd BlockMesh && \
  cd core-engine && go mod tidy && gomobile bind -target=android -androidapi 24 -o ../client-android/engine.aar . && \
  cd ../client-android && gradle assembleDebug
```

Output: `client-android/app/build/outputs/apk/debug/app-debug.apk`

### Prerequisites

- Go 1.25+
- gomobile (`go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init`)
- JDK 17
- Gradle 8.9+
- Android SDK (API 24+)

## What's in the repo

```
core-engine/          Go library — DNS sinkhole + radix trie blocker
  engine.go           Public API: StartEngine, StopEngine, CheckDomain, ProcessDNSQuery, GetBlockedCount
  dns.go              Packet inspection, DNS question parser
  trie.go             Radix trie for domain matching (reversed label storage)
  updater.go          Background blocklist fetcher with ETag delta sync
client-android/       Kotlin Android app (VpnService wrapper)
client-ios/           Swift/iOS shell (XCFramework target)
.github/workflows/    CI — builds AAR + APK (Android) and XCFramework (iOS)
```

## How the DNS sinkhole works

1. The VPN service intercepts all traffic on the device
2. DNS queries (UDP port 53) are extracted from raw IP packets
3. Domain names are checked against a radix trie loaded with blocklist entries
4. Blocked domains get `0.0.0.0` — the request dies instantly
5. Everything else passes through normally

The trie stores domains reversed (`ads.google.com` → `com → google → ads`) so parent domain blocks automatically catch all subdomains.

### Blocklist updates

- Fetches from [pgl.yoyo.org](https://pgl.yoyo.org/adservers/) every 12 hours
- Uses HTTP `If-None-Match` / ETag headers — only downloads when the list actually changes
- Builds a new trie in a separate allocation, then swaps it in atomically via `sync/atomic.Pointer` — zero downtime, no locks

## Architecture

```
┌─────────────────────────────────────┐
│  Android / iOS App                  │
│  (Kotlin VpnService / Swift NEPT)   │
│                                     │
│  ┌───────────────────────────────┐  │
│  │  Go Core Engine (gomobile)    │  │
│  │                               │  │
│  │  DNS Interceptor              │  │
│  │    ↓                          │  │
│  │  Radix Trie Matcher           │  │
│  │    ↓                          │  │
│  │  Block (0.0.0.0) or Pass      │  │
│  │                               │  │
│  │  Background Updater (12h)     │  │
│  │    → ETag check               │  │
│  │    → Atomic trie swap         │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

## CI/CD

GitHub Actions builds on every push to `main`:

| Job | Runner | Output |
|-----|--------|--------|
| `build-android` | `ubuntu-latest` | `engine.aar` + `app-debug.apk` |
| `build-ios-core` | `macos-latest` | `Engine.xcframework` |

Both artifacts are uploaded and downloadable from the Actions tab.

## Roadmap

- [ ] WireGuard tunnel integration via `wireguard-go`
- [ ] P2P mesh routing with `go-libp2p` + Kademlia DHT
- [ ] NAT traversal (STUN/TURN + UDP hole punching)
- [ ] QR code device pairing (key exchange)
- [x] Split tunneling (app-based filtering supported)
- [ ] Android TV exit node mode
- [x] Binary trie caching for offline cold starts
- [x] Multiple blocklist sources

## License

[MIT](LICENSE.md)
