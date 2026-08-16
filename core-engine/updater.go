package engine

import (
	"bufio"
	"fmt"
	"net/http"
	"strings"
	"time"
)

// The Peter Lowe Ad/Tracking list (active by default in uBlock Origin)
// Returns a highly accurate clean list of raw domains.
const uBlockListURL = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=nohtml&showintro=0&mimetype=plaintext"

var lastETag string

// StartBlocklistUpdater launches a background goroutine 
func StartBlocklistUpdater() {
	go func() {
		// Run immediately on boot to hydrate the Trie
		fetchAndUpdateList()

		// Then check every 12 hours as architected
		ticker := time.NewTicker(12 * time.Hour)
		defer ticker.Stop()

		for range ticker.C {
			fetchAndUpdateList()
		}
	}()
}

func fetchAndUpdateList() {
	fmt.Println("Checking for uBlock blocklist updates...")
	client := &http.Client{Timeout: 15 * time.Second}
	
	req, err := http.NewRequest("GET", uBlockListURL, nil)
	if err != nil {
		fmt.Printf("Update failed to initialize: %v\n", err)
		return
	}

	// Delta Syncing via ETags
	if lastETag != "" {
		req.Header.Set("If-None-Match", lastETag)
	}

	resp, err := client.Do(req)
	if err != nil {
		fmt.Printf("Update network request failed: %v\n", err)
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNotModified {
		fmt.Println("Blocklist is already up to date (304 Not Modified).")
		return
	}

	if etag := resp.Header.Get("ETag"); etag != "" {
		lastETag = etag
	}

	newTrie := NewRadixTrie()
	
	// Read the plain text domains line by line
	scanner := bufio.NewScanner(resp.Body)
	var count int
	for scanner.Scan() {
		domain := strings.TrimSpace(scanner.Text())
		if domain == "" || strings.HasPrefix(domain, "#") {
			continue // Skip empty or comment lines
		}
		newTrie.Insert(domain)
		count++
	}

	// 1. Atomic Pointer Swap (Zero Downtime)
	// We safely swap the actively routing DNS Trie without dropping any packets
	activeTrie.Store(newTrie)

	fmt.Printf("Blocklist updated successfully. %d uBlock domains atomically loaded into Radix Trie.\n", count)
}
