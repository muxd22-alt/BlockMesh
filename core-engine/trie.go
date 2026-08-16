package engine

import "strings"

type radixNode struct {
	Children map[string]*radixNode
	IsEnd    bool
}

type radixTrie struct {
	Root *radixNode
}

func newRadixTrie() *radixTrie {
	return &radixTrie{
		Root: &radixNode{
			Children: make(map[string]*radixNode),
			IsEnd:    false,
		},
	}
}

func (t *radixTrie) insert(domain string) {
	parts := strings.Split(domain, ".")
	for i, j := 0, len(parts)-1; i < j; i, j = i+1, j-1 {
		parts[i], parts[j] = parts[j], parts[i]
	}

	curr := t.Root
	for _, part := range parts {
		if _, exists := curr.Children[part]; !exists {
			curr.Children[part] = &radixNode{
				Children: make(map[string]*radixNode),
				IsEnd:    false,
			}
		}
		curr = curr.Children[part]
	}
	curr.IsEnd = true
}

func (t *radixTrie) contains(domain string) bool {
	parts := strings.Split(domain, ".")
	for i, j := 0, len(parts)-1; i < j; i, j = i+1, j-1 {
		parts[i], parts[j] = parts[j], parts[i]
	}

	curr := t.Root
	for _, part := range parts {
		if next, exists := curr.Children[part]; exists {
			curr = next
			if curr.IsEnd {
				return true
			}
		} else {
			return false
		}
	}
	return false
}
