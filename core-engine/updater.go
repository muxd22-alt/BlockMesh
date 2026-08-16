package engine

import (
	"bufio"
	"fmt"
	"net/http"
	"os"
	"strings"
	"sync"
	"time"
)

var (
	blocklistURLs = []string{
		"https://pgl.yoyo.org/adservers/serverlist.php?hostformat=nohtml&showintro=0&mimetype=plaintext",
	}
	urlMutex sync.Mutex
	stopCh   chan struct{}
)

func addURL(url string) {
	url = strings.TrimSpace(url)
	if url == "" {
		return
	}
	urlMutex.Lock()
	defer urlMutex.Unlock()
	for _, u := range blocklistURLs {
		if u == url {
			return
		}
	}
	blocklistURLs = append(blocklistURLs, url)
}

func removeURL(url string) {
	urlMutex.Lock()
	defer urlMutex.Unlock()
	for i, u := range blocklistURLs {
		if u == url {
			blocklistURLs = append(blocklistURLs[:i], blocklistURLs[i+1:]...)
			return
		}
	}
}

func getURLs() string {
	urlMutex.Lock()
	defer urlMutex.Unlock()
	return strings.Join(blocklistURLs, "\n")
}

func setURLs(urls string) {
	urlMutex.Lock()
	defer urlMutex.Unlock()
	blocklistURLs = nil
	for _, u := range strings.Split(urls, "\n") {
		u = strings.TrimSpace(u)
		if u != "" {
			blocklistURLs = append(blocklistURLs, u)
		}
	}
}

func startBlocklistUpdater() {
	stopCh = make(chan struct{})
	go func() {
		fetchAndUpdateAllLists()
		ticker := time.NewTicker(12 * time.Hour)
		defer ticker.Stop()
		for {
			select {
			case <-ticker.C:
				fetchAndUpdateAllLists()
			case <-stopCh:
				return
			}
		}
	}()
}

func stopBlocklistUpdater() {
	if stopCh != nil {
		close(stopCh)
		stopCh = nil
	}
}

func fetchAndUpdateAllLists() {
	urlMutex.Lock()
	urls := make([]string, len(blocklistURLs))
	copy(urls, blocklistURLs)
	urlMutex.Unlock()

	fmt.Printf("Updating blocklists from %d sources...\n", len(urls))
	newTrie := newRadixTrie()
	var totalCount int
	var allDomains []string // to cache

	for _, url := range urls {
		count := fetchFromURL(url, newTrie, &allDomains)
		totalCount += count
	}

	activeTrie.Store(newTrie)
	fmt.Printf("Blocklist updated: %d domains from %d sources.\n", totalCount, len(urls))

	go saveCache(allDomains)
}

func fetchFromURL(url string, trie *radixTrie, allDomains *[]string) int {
	client := &http.Client{Timeout: 30 * time.Second}
	resp, err := client.Get(url)
	if err != nil {
		fmt.Printf("Failed to fetch %s: %v\n", url, err)
		return 0
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		fmt.Printf("HTTP %d from %s\n", resp.StatusCode, url)
		return 0
	}

	scanner := bufio.NewScanner(resp.Body)
	var count int
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") || strings.HasPrefix(line, "!") {
			continue
		}
		if strings.HasPrefix(line, "0.0.0.0 ") || strings.HasPrefix(line, "127.0.0.1 ") {
			parts := strings.Fields(line)
			if len(parts) >= 2 {
				line = parts[1]
			}
		}
		if strings.HasPrefix(line, "||") {
			line = strings.TrimPrefix(line, "||")
			line = strings.TrimSuffix(line, "^")
		}
		if line != "" && strings.Contains(line, ".") {
			trie.insert(line)
			*allDomains = append(*allDomains, line)
			count++
		}
	}
	fmt.Printf("Loaded %d domains from %s\n", count, url)
	return count
}

func cacheFilePath() string {
	if cacheDir == "" {
		return "trie.cache"
	}
	return cacheDir + "/trie.cache"
}

func loadCache() {
	importDomainsFromFile(cacheFilePath())
}

func saveCache(domains []string) {
	file, err := os.Create(cacheFilePath())
	if err != nil {
		fmt.Printf("Failed creating cache: %v\n", err)
		return
	}
	defer file.Close()
	
	writer := bufio.NewWriter(file)
	for _, domain := range domains {
		writer.WriteString(domain + "\n")
	}
	writer.Flush()
	fmt.Println("Wrote offline domain cache to disk.")
}

func importDomainsFromFile(path string) {
	file, err := os.Open(path)
	if (err != nil) {
		return
	}
	defer file.Close()
	
	newTrie := newRadixTrie()
	scanner := bufio.NewScanner(file)
	count := 0
	for scanner.Scan() {
		domain := strings.TrimSpace(scanner.Text())
		if domain != "" {
			newTrie.insert(domain)
			count++
		}
	}
	if count > 0 {
		activeTrie.Store(newTrie)
		fmt.Printf("Loaded %d domains instantly from fast cold cache binary!\n", count)
	}
}
