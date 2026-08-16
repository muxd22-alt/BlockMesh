package engine

import "strings"

type radixNode struct {
	children map[string]*radixNode
	isEnd    bool
}

type radixTrie struct {
	root  *radixNode
	count int
}

func newRadixTrie() *radixTrie {
	return &radixTrie{
		root: &radixNode{
			children: make(map[string]*radixNode),
		},
	}
}

// insert adds a domain to the blocklist (stored reversed: com -> google -> ads)
func (t *radixTrie) insert(domain string) {
	parts := strings.Split(domain, ".")
	for i, j := 0, len(parts)-1; i < j; i, j = i+1, j-1 {
		parts[i], parts[j] = parts[j], parts[i]
	}

	curr := t.root
	for _, part := range parts {
		if _, exists := curr.children[part]; !exists {
			curr.children[part] = &radixNode{
				children: make(map[string]*radixNode),
			}
		}
		curr = curr.children[part]
	}
	curr.isEnd = true
	t.count++
}

// contains checks if a domain (or its parent) is blocked
func (t *radixTrie) contains(domain string) bool {
	parts := strings.Split(domain, ".")
	for i, j := 0, len(parts)-1; i < j; i, j = i+1, j-1 {
		parts[i], parts[j] = parts[j], parts[i]
	}

	curr := t.root
	for _, part := range parts {
		if next, exists := curr.children[part]; exists {
			curr = next
			if curr.isEnd {
				return true
			}
		} else {
			return false
		}
	}
	return false
}
