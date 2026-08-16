package engine

import (
	"fmt"
	"sync/atomic"

	"golang.org/x/net/dns/dnsmessage"
	"golang.org/x/net/ipv4"
)

var activeTrie atomic.Pointer[radixTrie]

func init() {
	emptyTrie := newRadixTrie()
	activeTrie.Store(emptyTrie)
}

// processPacket acts as the bouncer
func processPacket(rawPacket []byte) {
	header, err := ipv4.ParseHeader(rawPacket)
	if err != nil {
		return
	}

	if header.Protocol == 17 {
		destPort := extractUDPPort(rawPacket)
		if destPort == 53 {
			handleDNSQuery(rawPacket)
			return
		}
	}

	sendToWireGuard(rawPacket)
}

func handleDNSQuery(rawPacket []byte) {
	var msg dnsmessage.Message
	payload := extractDNSPayload(rawPacket)
	err := msg.Unpack(payload)
	if err != nil || len(msg.Questions) == 0 {
		sendToWireGuard(rawPacket)
		return
	}

	domain := msg.Questions[0].Name.String()
	
	if len(domain) > 0 && domain[len(domain)-1] == '.' {
		domain = domain[:len(domain)-1]
	}

	trie := activeTrie.Load()
	if trie != nil && trie.contains(domain) {
		fmt.Printf("BLOCKED AD REQUEST: %s\n", domain)
		sinkholePacket := forgeDNSZeroResponse(rawPacket, msg)
		writeBackToOS(sinkholePacket)
		return
	}

	sendToWireGuard(rawPacket)
}

func extractUDPPort(packet []byte) int {
	return 53 
}

func extractDNSPayload(packet []byte) []byte {
	return packet 
}

func sendToWireGuard(packet []byte) {
}

func forgeDNSZeroResponse(original []byte, msg dnsmessage.Message) []byte {
	return []byte{}
}

func writeBackToOS(packet []byte) {
}
