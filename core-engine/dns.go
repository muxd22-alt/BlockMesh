package engine

import (
	"fmt"
	"sync/atomic"
)

var activeTrie atomic.Pointer[radixTrie]

func init() {
	emptyTrie := newRadixTrie()
	activeTrie.Store(emptyTrie)
}

// processPacket inspects a raw IP packet for DNS queries and blocks ads.
// This is called internally by the native VPN service layer.
func processPacket(rawPacket []byte) {
	if len(rawPacket) < 28 {
		return // Too short for IPv4 + UDP header
	}

	// Check IPv4 protocol field (byte 9) for UDP (17)
	if rawPacket[9] != 17 {
		return // Not UDP, skip
	}

	// IPv4 header length (lower nibble of byte 0, in 32-bit words)
	ihl := int(rawPacket[0]&0x0F) * 4
	if len(rawPacket) < ihl+8 {
		return // Packet too short for UDP header
	}

	// UDP destination port: 2 bytes at ihl+2
	destPort := int(rawPacket[ihl+2])<<8 | int(rawPacket[ihl+3])
	if destPort != 53 {
		return // Not DNS
	}

	// Extract DNS payload (after IPv4 + UDP headers)
	dnsPayload := rawPacket[ihl+8:]
	if len(dnsPayload) < 12 {
		return // DNS header too short
	}

	// Parse domain name from DNS question section
	domain := extractDomainFromDNS(dnsPayload)
	if domain == "" {
		return
	}

	trie := activeTrie.Load()
	if trie != nil && trie.contains(domain) {
		fmt.Printf("BLOCKED AD REQUEST: %s\n", domain)
	}
}

// extractDomainFromDNS parses a domain name from a raw DNS question section.
func extractDomainFromDNS(payload []byte) string {
	// Skip DNS header (12 bytes)
	offset := 12
	var domain string

	for offset < len(payload) {
		labelLen := int(payload[offset])
		if labelLen == 0 {
			break
		}
		offset++
		if offset+labelLen > len(payload) {
			return ""
		}
		if domain != "" {
			domain += "."
		}
		domain += string(payload[offset : offset+labelLen])
		offset += labelLen
	}

	return domain
}
