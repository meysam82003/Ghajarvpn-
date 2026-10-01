package main

import (
	"bufio"
	"context"
	"io"
	"net"
	"net/http"
	"strings"
	"testing"
	"time"
)

func TestConnectProxyAuthorityAuthAndTunnel(t *testing.T) {
	server, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer server.Close()
	results := make(chan error, 1)
	go func() {
		c, e := server.Accept()
		if e != nil {
			results <- e
			return
		}
		defer c.Close()
		c.SetDeadline(time.Now().Add(3 * time.Second))
		req, e := http.ReadRequest(bufio.NewReader(c))
		if e != nil {
			results <- e
			return
		}
		if req.Method != "CONNECT" || req.Host != "vpn.example:443" || req.Header.Get("Proxy-Authorization") != "Basic dTpw" {
			results <- io.ErrUnexpectedEOF
			return
		}
		_, e = io.WriteString(c, "HTTP/1.1 200 Connection established\r\n\r\nhello")
		results <- e
	}()
	c, err := dialSSTPTransport(context.Background(), "vpn.example:443", "http://u:p@"+server.Addr().String(), time.Second)
	if err != nil {
		t.Fatal(err)
	}
	defer c.Close()
	b := make([]byte, 5)
	if _, err := io.ReadFull(c, b); err != nil || string(b) != "hello" {
		t.Fatalf("tunnel bytes lost: %q %v", b, err)
	}
	if err := <-results; err != nil {
		t.Fatal(err)
	}
}
func TestConnectRefusalAndHeaderBound(t *testing.T) {
	for _, response := range []string{"HTTP/1.1 407 Proxy Authentication Required\r\n\r\n", "HTTP/1.1 200 OK\r\nX: " + strings.Repeat("a", 17000) + "\r\n\r\n"} {
		server, _ := net.Listen("tcp", "127.0.0.1:0")
		go func() {
			c, e := server.Accept()
			if e != nil {
				return
			}
			defer c.Close()
			c.SetDeadline(time.Now().Add(time.Second))
			http.ReadRequest(bufio.NewReader(c))
			io.WriteString(c, response)
		}()
		c, err := dialSSTPTransport(context.Background(), "vpn.example:443", "http://"+server.Addr().String(), time.Second)
		server.Close()
		if c != nil {
			c.Close()
		}
		if err == nil {
			t.Fatal("unsafe response accepted")
		}
	}
}
func TestConnectRejectsInvalidSchemesAndInjection(t *testing.T) {
	for _, proxy := range []string{"socks5://127.0.0.1:1080", "http://host/path", "http://host?key=secret"} {
		if c, e := dialSSTPTransport(context.Background(), "vpn.example:443", proxy, time.Millisecond); e == nil {
			c.Close()
			t.Fatal("invalid proxy accepted")
		}
	}
	if c, e := dialSSTPTransport(context.Background(), "vpn.example\r\nX:443", "", time.Millisecond); e == nil {
		c.Close()
		t.Fatal("injection accepted")
	}
}
