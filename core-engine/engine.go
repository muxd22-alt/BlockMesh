package engine

import (
	"fmt"
	"sync"
)

var (
	running  bool
	runMutex sync.Mutex
)

// StartEngine initializes the DNS sinkhole and blocklist updater.
func StartEngine() {
	runMutex.Lock()
	defer runMutex.Unlock()
	if running {
		return
	}
	running = true
	fmt.Println("BlockMesh DNS Sinkhole Engine starting...")
	startBlocklistUpdater()
	fmt.Println("BlockMesh DNS Engine ready.")
}

// StopEngine gracefully shuts down the sinkhole.
func StopEngine() {
	runMutex.Lock()
	defer runMutex.Unlock()
	if !running {
		return
	}
	running = false
	stopBlocklistUpdater()
	fmt.Println("BlockMesh DNS Engine stopped.")
}

// IsRunning returns whether the engine is active.
func IsRunning() bool {
	runMutex.Lock()
	defer runMutex.Unlock()
	return running
}

// CheckDomain returns true if a domain is on the blocklist.
func CheckDomain(domain string) bool {
	trie := activeTrie.Load()
	if trie == nil {
		return false
	}
	return trie.contains(domain)
}

// ProcessDNSQuery returns "0.0.0.0" if blocked, empty string if allowed.
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

// AddBlocklistURL adds a new blocklist source URL.
func AddBlocklistURL(url string) {
	addURL(url)
}

// RemoveBlocklistURL removes a blocklist source URL.
func RemoveBlocklistURL(url string) {
	removeURL(url)
}

// GetBlocklistURLs returns all configured blocklist URLs, newline-separated.
func GetBlocklistURLs() string {
	return getURLs()
}

// SetBlocklistURLs replaces all blocklist URLs. Pass newline-separated URLs.
func SetBlocklistURLs(urls string) {
	setURLs(urls)
}

// RefreshBlocklists forces an immediate re-fetch of all blocklists.
func RefreshBlocklists() {
	go fetchAndUpdateAllLists()
}
