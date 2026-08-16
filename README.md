# BlockMesh: Decentralized Pi-Hole + Mesh VPN

BlockMesh is a serverless, single-application mesh VPN with a built-in DNS sinkhole. No cloud subscriptions, no Raspberry Pi required, and no complicated router configurations. By targeting mobile and smart TVs first, BlockMesh turns devices you already own into the backbone of your private network.

## 🚀 The Vision: An "Anti-Cloud" Architecture

**There is no central server, no telemetry, and no premium subscription.** All routing is 100% peer-to-peer. 

BlockMesh combines the official `wireguard-go` library with `go-libp2p` to create a flawless, central-server-free mesh network. Your devices communicate directly with each other, bypassing strict firewalls automatically.

---

## 🏗️ Architecture

BlockMesh decouples the "Engine" from the "UI", allowing for a highly optimized, cross-platform core.

### The Monorepo Structure

* `/core-engine` - The Go code containing the VPN tuner, P2P mesh router, and DNS sinkhole.
* `/client-android` - Kotlin UI for Android and Android TV using Jetpack Compose.
* `/client-ios` - Swift UI for iOS and tvOS.

### How it Works (Visualized)

```mermaid
graph TD
    subgraph Public Internet
        STUN[STUN/TURN Servers]
        Relay[libp2p Circuit Relay Node]
        DHT[Kademlia DHT]
    end

    subgraph User Devices
        Phone[Android/iOS Phone]
        TV[Android TV / Exit Node]
    end

    Phone -- 1. Query Peer ID --> DHT
    DHT -. Returns IP .-> Phone
    Phone -- 2. What's my open IP/Port? --> STUN
    TV -- 2. What's my open IP/Port? --> STUN
    Phone <== 3. UDP Hole Punching (Direct WireGuard Tunnel) ==> TV
    Phone -.- 4. Fallback (If strictly firewalled) -.- Relay
    Relay -.- 4. Fallback (Encrypted routing) -.- TV
```

## 🧠 The Core Engine (Built in Go)

The Go engine acts as a "black box" library. The Android/iOS apps simply hand it a raw network connection (`tun` interface), and Go handles all the cryptography, peer discovery, and ad-blocking.

### 1. libp2p & NAT Traversal (The Magic Trick)

When you are at a coffee shop and your phone tries to connect to your Android TV at home, your router's NAT inherently blocks the incoming connection. Standard VPNs require manual "Port Forwarding". BlockMesh automates this using **UDP Hole Punching**:

1. **The Kademlia DHT (The Phonebook):** The phone queries the libp2p DHT asking for the Android TV's cryptographic peer ID. The network responds with the TV's last known public IP.
2. **AutoNAT & STUN:** Both devices ask public STUN servers to identify their outside IP/port structure.
3. **UDP Hole Punching:** The TV sends a dummy UDP packet *out* to the phone's IP. The router opens a temporary outbound hole. The phone simultaneously sends a WireGuard handshake. The router lets the packet slip right through!
4. **Circuit Relay (Fallback):** If behind ultra-strict symmetric corporate firewalls, libp2p utilizes a "Relay" node to temporarily ferry fully encrypted packets until a direct connection routes.

### 2. The Local DNS Sinkhole (Ad-Blocker)

Before a packet goes into the WireGuard tunnel, the Go engine checks if it's a DNS request (Port 53).

- **If blocked:** It instantly returns a fake response (e.g., `0.0.0.0`), dropping the ad.
- **If allowed:** It forwards the request through WireGuard.

#### Automating Blocklist Updates

To keep blocklists fresh without battery drain:

* **Background Goroutine:** Checks for updates silently every 12 hours.
* **Delta Syncing via ETags:** Uses HTTP `If-None-Match` with ETags to only download the list if it has actually changed, saving data.
* **Atomic Pointer Swapping:** To prevent network stalling when building the Radix Trie of 300,000 domains, the engine builds the new Trie in a separate memory block, swapping pointers atomically in a single nanosecond.
* **Binary Caching:** The engine caches the Trie in binary (`gob`) format. Offline reboots load the cache instantly.

---

## 📱 Platform Integrations

### Android & Android TV (Kotlin + Jetpack Compose)

Android's `VpnService` API is leveraged. Using `detachFd()`, the raw file descriptor is passed seamlessly out of the JVM into `wireguard-go` via C bindings. This removes unnecessary data marshaling latency and battery drain. The same APK builds a stable Exit Node on an Android TV (always plugged in, always connected).

### iOS & tvOS (SwiftUI)

Apple requires using `NEPacketTunnelProvider`. Since we cannot hand off a raw File Descriptor to Go, Swift leverages `NEPacketTunnelFlow` to read `NSData` packets. 

1. **The Interceptor:** Go sniffs the layer 3 IPv4 header.
2. **DNS Unpacking:** If destination port is 53, extract domain.
3. **Radix Trie Matcher:** Match against blocklist.
4. **The Forge:** Reconstruct `0.0.0.0` DNS payload, swap IPs/Ports, and send directly back to OS, bypassing WireGuard completely for blocked domains.

## 🌟 "Super Smart" UX Features

* **Context-Aware Split Tunneling:** When on home Wi-Fi, only local ad-blocking runs. Once you leave, the WireGuard tunnel directly spins up.
* **QR Code Mesh Pairing:** To add an iPad to your mesh, simply tap "Add Device" on the Android phone and scan a QR code. WireGuard keys + libp2p IDs exchange securely in a second. No accounts, no emails, no passwords.
* **Zero Battery Drain:** Combines stateless WireGuard performance with efficient local DNS blocking.
