package engine

import (
	"bufio"
	"fmt"
	"net/http"
	"strings"
	"time"
)

const uBlockListURL = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=nohtml&showintro=0&mimetype=plaintext"

var lastETag string

func startBlocklistUpdater() {
	go func() {
		fetchAndUpdateList()

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

	newTrie := newRadixTrie()
	
	scanner := bufio.NewScanner(resp.Body)
	var count int
	for scanner.Scan() {
		domain := strings.TrimSpace(scanner.Text())
		if domain == "" || strings.HasPrefix(domain, "#") {
			continue // Skip empty or comment lines
		}
		newTrie.insert(domain)
		count++
	}

	activeTrie.Store(newTrie)

	fmt.Printf("Blocklist updated successfully. %d uBlock domains atomically loaded into Radix Trie.\n", count)
}
