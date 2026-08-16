package engine

import "fmt"

// StartEngine initializes the DNS sinkhole and blocklist updater.
// Called by Kotlin/Swift when the app boots.
func StartEngine() {
	fmt.Println("BlockMesh DNS Sinkhole Engine starting...")
	startBlocklistUpdater()
	fmt.Println("BlockMesh DNS Engine ready.")
}

// CheckDomain returns true if a domain is on the blocklist.
// Called by the native VPN layer before forwarding DNS queries.
func CheckDomain(domain string) bool {
	trie := activeTrie.Load()
	if trie == nil {
		return false
	}
	return trie.contains(domain)
}

// ProcessDNSQuery takes a raw domain string and returns "0.0.0.0" if blocked,
// or an empty string if allowed. Simple FFI-friendly interface.
func ProcessDNSQuery(domain string) string {
	if CheckDomain(domain) {
		fmt.Printf("BLOCKED: %s\n", domain)
		return "0.0.0.0"
	}
	return ""
}

// GetBlockedCount returns the number of domains in the active blocklist.
func GetBlockedCount() int {
	trie := activeTrie.Load()
	if trie == nil {
		return 0
	}
	return trie.count
}

// StopEngine gracefully shuts down the sinkhole.
func StopEngine() {
	fmt.Println("BlockMesh DNS Engine stopped.")
}
