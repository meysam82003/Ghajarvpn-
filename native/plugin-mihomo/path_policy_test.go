package main

import "testing"

func TestProviderPathsStayPrivateWithoutRejectingWebSocketPaths(t *testing.T) {
	provider := func(path string) any {
		return map[string]any{"proxy-providers": map[string]any{"feed": map[string]any{"type": "http", "path": path}}}
	}
	for _, path := range []string{"/sdcard/leak.yaml", "../other/config.yaml", "cache/../../other.yaml"} {
		if safePaths(provider(path), "root") {
			t.Fatalf("unsafe provider path: %s", path)
		}
	}
	if !safePaths(provider("providers/feed.yaml"), "root") {
		t.Fatal("valid provider path rejected")
	}
	ws := map[string]any{"proxies": []any{map[string]any{"type": "vmess", "ws-opts": map[string]any{"path": "/tunnel/socket"}}}}
	if !safePaths(ws, "root") {
		t.Fatal("HTTP request path mistaken for a local file")
	}
	private := map[string]any{"proxies": []any{map[string]any{"private-key": "../other/private.pem"}}}
	if safePaths(private, "root") {
		t.Fatal("external key path accepted")
	}
}
