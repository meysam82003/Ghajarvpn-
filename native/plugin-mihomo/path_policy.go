package main

import (
	"path/filepath"
	"strings"
)

func safePaths(v any, scope string) bool {
	switch x := v.(type) {
	case map[string]any:
		for k, child := range x {
			if (k == "path" && scope == "provider") || strings.HasSuffix(k, "-path") || (k == "private-key" && x["type"] != "wireguard") || k == "certificate" {
				if s, ok := child.(string); ok && s != "" && !strings.Contains(s, "-----BEGIN") {
					cleaned := filepath.Clean(s)
					if filepath.IsAbs(s) || cleaned == ".." || strings.HasPrefix(cleaned, ".."+string(filepath.Separator)) {
						return false
					}
				}
			}
			next := ""
			if k == "proxy-providers" || k == "rule-providers" {
				next = "providers"
			} else if scope == "providers" {
				next = "provider"
			}
			if !safePaths(child, next) {
				return false
			}
		}
	case []any:
		for _, child := range x {
			if !safePaths(child, "") {
				return false
			}
		}
	}
	return true
}
