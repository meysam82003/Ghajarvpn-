package main

import (
	"bufio"
	"context"
	"crypto/tls"
	"encoding/base64"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// Separate TLS identities: the proxy always uses normal CA + hostname checks.
// Server pin/insecure options are never applied to the proxy's TLS connection.
func dialSSTPTransport(ctx context.Context, server, proxyURL string, timeout time.Duration) (net.Conn, error) {
	if _, _, err := net.SplitHostPort(server); err != nil {
		return nil, err
	}
	if strings.ContainsAny(server, "\r\n\x00") {
		return nil, errors.New("invalid SSTP authority")
	}
	dialer := net.Dialer{Timeout: timeout}
	if proxyURL == "" {
		return dialer.DialContext(ctx, "tcp", server)
	}
	u, err := url.Parse(proxyURL)
	if err != nil || u.Hostname() == "" || (u.Scheme != "http" && u.Scheme != "https") || (u.Path != "" && u.Path != "/") || u.RawQuery != "" || u.Fragment != "" {
		return nil, errors.New("invalid HTTP CONNECT proxy URL")
	}
	port := u.Port()
	if port == "" {
		if u.Scheme == "https" {
			port = "443"
		} else {
			port = "8080"
		}
	}
	conn, err := dialer.DialContext(ctx, "tcp", net.JoinHostPort(u.Hostname(), port))
	if err != nil {
		return nil, errors.New("HTTP proxy unreachable")
	}
	ok := false
	defer func() {
		if !ok {
			conn.Close()
		}
	}()
	conn.SetDeadline(time.Now().Add(timeout))
	if u.Scheme == "https" {
		tc := tls.Client(conn, &tls.Config{MinVersion: tls.VersionTLS12, ServerName: u.Hostname()})
		if err := tc.HandshakeContext(ctx); err != nil {
			return nil, errors.New("HTTPS proxy certificate or handshake failed")
		}
		conn = tc
	}
	req := "CONNECT " + server + " HTTP/1.1\r\nHost: " + server + "\r\n"
	if u.User != nil {
		password, _ := u.User.Password()
		req += "Proxy-Authorization: Basic " + base64.StdEncoding.EncodeToString([]byte(u.User.Username()+":"+password)) + "\r\n"
	}
	if _, err := io.WriteString(conn, req+"\r\n"); err != nil {
		return nil, errors.New("HTTP proxy write failed")
	}
	// Do not consume bytes belonging to the subsequent TLS handshake. Bound headers.
	var header strings.Builder
	one := make([]byte, 1)
	for !strings.HasSuffix(header.String(), "\r\n\r\n") {
		if header.Len() >= 16384 {
			return nil, errors.New("HTTP proxy header too large")
		}
		if _, err := io.ReadFull(conn, one); err != nil {
			return nil, errors.New("HTTP proxy response incomplete")
		}
		header.WriteByte(one[0])
	}
	response, err := http.ReadResponse(bufio.NewReader(strings.NewReader(header.String())), &http.Request{Method: "CONNECT"})
	if err != nil {
		return nil, errors.New("malformed HTTP proxy response")
	}
	if response.StatusCode != 200 {
		return nil, fmt.Errorf("HTTP CONNECT refused: %d", response.StatusCode)
	}
	conn.SetDeadline(time.Time{})
	ok = true
	return conn, nil
}
