package engine

import "strings"

// RadixNode represents a node in the domain prefix tree
// We store domains backwards (com -> google -> ads)
type RadixNode struct {
	Children map[string]*RadixNode
	IsEnd    bool
}

// RadixTrie is the main structure for O(K) domain matching
type RadixTrie struct {
	Root *RadixNode
}

// NewRadixTrie initializes an empty trie
func NewRadixTrie() *RadixTrie {
	return &RadixTrie{
		Root: &RadixNode{
			Children: make(map[string]*RadixNode),
			IsEnd:    false,
		},
	}
}

// Insert adds a domain to the blocklist (stores it backwards)
func (t *RadixTrie) Insert(domain string) {
	parts := strings.Split(domain, ".")
	// Reverse the parts
	for i, j := 0, len(parts)-1; i < j; i, j = i+1, j-1 {
		parts[i], parts[j] = parts[j], parts[i]
	}

	curr := t.Root
	for _, part := range parts {
		if _, exists := curr.Children[part]; !exists {
			curr.Children[part] = &RadixNode{
				Children: make(map[string]*RadixNode),
				IsEnd:    false,
			}
		}
		curr = curr.Children[part]
	}
	curr.IsEnd = true
}

// Contains checks if a domain or its parent domain is blocked
func (t *RadixTrie) Contains(domain string) bool {
	parts := strings.Split(domain, ".")
	// Reverse the parts to check from TLD inwards
	for i, j := 0, len(parts)-1; i < j; i, j = i+1, j-1 {
		parts[i], parts[j] = parts[j], parts[i]
	}

	curr := t.Root
	for _, part := range parts {
		if next, exists := curr.Children[part]; exists {
			curr = next
			// If we reached an end node, it means this sub-domain or exact domain is blocked
			if curr.IsEnd {
				return true
			}
		} else {
			return false
		}
	}
	return false
}
