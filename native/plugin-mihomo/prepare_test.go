package main

import (
	C "github.com/metacubex/mihomo/constant"
	"os"
	"path/filepath"
	"testing"
)

func TestFullConfigurationAcceptance(t *testing.T) {
	C.SetHomeDir(t.TempDir())
	good := []string{
		"socks-port: 1080\nproxies: []\nrules: ['MATCH,DIRECT']\n",
		"mixed-port: 1081\nproxies: []\nproxy-groups:\n - name: choice\n   type: select\n   proxies: [DIRECT, REJECT]\nrules: ['MATCH,choice']\n",
	}
	for _, raw := range good {
		if _, p, e := prepare([]byte(raw)); e != nil || p.Mode != "socks" {
			t.Fatalf("accepted corpus: %v", e)
		}
	}
}
func TestUnsupportedSemanticsAreRejected(t *testing.T) {
	C.SetHomeDir(t.TempDir())
	for _, extra := range []string{"allow-lan: true", "external-controller: 127.0.0.1:9090", "dns: {enable: true, listen: '0.0.0.0:1053'}", "tun: {enable: true, auto-route: true, stack: gvisor, include-package: [com.example]}", "proxy-providers: {local: {type: file, path: '../outside'}}"} {
		if _, _, err := prepare([]byte("socks-port: 1080\n" + extra + "\n")); err == nil {
			t.Fatalf("accepted unsupported %s", extra)
		}
	}
}
func TestProviderAndCertificateSandboxIncludesSymlinks(t *testing.T) {
	root := t.TempDir()
	C.SetHomeDir(root)
	outside := t.TempDir()
	if err := os.Symlink(outside, filepath.Join(root, "escape")); err != nil {
		t.Fatal(err)
	}
	for _, path := range []string{filepath.Join(outside, "secret"), filepath.Join(root, "escape", "secret"), "../secret"} {
		if C.Path.IsSafePath(path) {
			t.Fatalf("unsafe path: %s", path)
		}
	}
	if !C.Path.IsSafePath(filepath.Join(root, "providers", "new.yaml")) {
		t.Fatal("new sandbox file rejected")
	}
}
func TestLocalFileProviderParsesWithoutRewritingPayload(t *testing.T) {
	root := t.TempDir()
	C.SetHomeDir(root)
	if err := os.WriteFile(filepath.Join(root, "nodes.yaml"), []byte("proxies: []\n"), 0600); err != nil {
		t.Fatal(err)
	}
	raw := []byte("socks-port: 1080\nproxy-providers:\n  local:\n    type: file\n    path: nodes.yaml\nproxy-groups:\n - name: choice\n   type: select\n   use: [local]\nrules: ['MATCH,DIRECT']\n")
	cfg, p, e := prepare(raw)
	if e != nil {
		t.Fatal(e)
	}
	if !p.Full || cfg.Providers["local"] == nil {
		t.Fatal("provider not preserved")
	}
}
