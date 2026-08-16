package engine

import (
	"fmt"
	"sync/atomic"

	"golang.org/x/net/dns/dnsmessage"
	"golang.org/x/net/ipv4"
)

var activeTrie atomic.Pointer[RadixTrie]

func init() {
	emptyTrie := NewRadixTrie()
	activeTrie.Store(emptyTrie)
}

// ProcessPacket acts as the bouncer. 
// It inspects the packet before it gets passed to WireGuard.
func ProcessPacket(rawPacket []byte) {
	// 1. Parse the IPv4 Header
	header, err := ipv4.ParseHeader(rawPacket)
	if err != nil {
		return
	}

	// 2. Is this UDP traffic? (Protocol 17)
	if header.Protocol == 17 {
		// Extract UDP destination port (bytes 20-24 typically in ipv4 payload)
		// For simplicity, using a helper function mock
		destPort := extractUDPPort(rawPacket)

		// 3. Is this a DNS Query? (Port 53)
		if destPort == 53 {
			handleDNSQuery(rawPacket)
			return // DROP packet from moving to WireGuard
		}
	}

	// 4. Not DNS? Send it to WireGuard for encryption and libp2p routing
	sendToWireGuard(rawPacket)
}

func handleDNSQuery(rawPacket []byte) {
	// Mock parsing DNS payload
	var msg dnsmessage.Message
	payload := extractDNSPayload(rawPacket)
	err := msg.Unpack(payload)
	if err != nil || len(msg.Questions) == 0 {
		sendToWireGuard(rawPacket)
		return
	}

	// Get the requested domain
	domain := msg.Questions[0].Name.String()
	
	// Remove trailing dot 
	if len(domain) > 0 && domain[len(domain)-1] == '.' {
		domain = domain[:len(domain)-1]
	}

	// 5. Query our ultra-fast Radix Trie atomically
	trie := activeTrie.Load()
	if trie != nil && trie.Contains(domain) {
		fmt.Printf("BLOCKED AD REQUEST: %s\n", domain)
		// 6. Forge the Sinkhole Response (0.0.0.0)
		sinkholePacket := forgeDNSZeroResponse(rawPacket, msg)
		
		// 7. Write the fake response directly back to the OS
		writeBackToOS(sinkholePacket)
		return
	}

	// If the domain is safe, wrap it back up and send to WireGuard to resolve normally
	sendToWireGuard(rawPacket)
}

// --- Mocks for low-level packet manipulation ---

func extractUDPPort(packet []byte) int {
	// In real implementation: extract from UDP header bytes
	return 53 
}

func extractDNSPayload(packet []byte) []byte {
	// In real implementation: strip IPv4 and UDP headers
	return packet 
}

func sendToWireGuard(packet []byte) {
	// Write directly to wireguard-go interface
}

func forgeDNSZeroResponse(original []byte, msg dnsmessage.Message) []byte {
	// In real implementation: build a valid DNS response pointing to 0.0.0.0
	return []byte{}
}

func writeBackToOS(packet []byte) {
	// In real implementation: Write bytes back to iOS NEPacketTunnelFlow or Android TUN
}
