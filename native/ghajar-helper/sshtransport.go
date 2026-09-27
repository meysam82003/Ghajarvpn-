package main

import (
	"bufio"
	"crypto/rand"
	"crypto/tls"
	"encoding/base64"
	"encoding/binary"
	"errors"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"strconv"
	"strings"
	"sync"
	"time"
)

// sshTransport carries a raw byte stream (sing-box's SSH client on the
// local side) to an SSH server through one of the disguises injector apps
// use. It does not speak SSH itself: SSH authentication and the host key
// check stay in sing-box, so the disguise never weakens them.
//
// Modes:
//
//	direct       plain TCP (for completeness)
//	payload      send an HTTP payload first, drop the HTTP answer(s), then relay
//	http-proxy   HTTP CONNECT through a proxy (custom headers allowed)
//	https-proxy  TLS to the proxy, then HTTP CONNECT
//	tls          TLS with a chosen SNI (SSH over TLS / stunnel)
//	payload-tls  TLS with a chosen SNI, then the payload
//	ws           HTTP Upgrade to WebSocket, then relay (raw or framed)
//	wss          the same over TLS
type sshTransport struct {
	mode, host, proxy, sni, payload, ua, wsPath, wsHost string
	port                                              int
	framing                                           bool
	verify                                            bool
	splitDelay                                        time.Duration
}

func runSSHTransport(args []string) error {
	fs := flag.NewFlagSet("sshtransport", flag.ContinueOnError)
	var t sshTransport
	var listen int
	var payloadB64 string
	fs.IntVar(&listen, "listen", 0, "local port on 127.0.0.1")
	fs.StringVar(&t.mode, "mode", "direct", "direct|payload|http-proxy|https-proxy|tls|payload-tls|ws|wss")
	fs.StringVar(&t.host, "host", "", "SSH server host")
	fs.IntVar(&t.port, "port", 22, "SSH server port")
	fs.StringVar(&t.proxy, "proxy", "", "proxy or front host:port to dial instead of the SSH server")
	fs.StringVar(&t.sni, "sni", "", "TLS server name")
	fs.StringVar(&payloadB64, "payload", "", "payload, base64 (placeholders: [host] [port] [host_port] [crlf] [lf] [cr] [protocol] [ua] [raw] [split] [delay_split])")
	fs.StringVar(&t.ua, "ua", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36", "User-Agent for [ua]")
	fs.StringVar(&t.wsPath, "ws-path", "/", "WebSocket path")
	fs.StringVar(&t.wsHost, "ws-host", "", "WebSocket Host header")
	fs.BoolVar(&t.framing, "ws-framing", false, "use RFC 6455 frames after the upgrade (default: raw stream, as injector servers expect)")
	fs.BoolVar(&t.verify, "verify", false, "verify the TLS certificate (SSH host key authentication applies either way)")
	fs.DurationVar(&t.splitDelay, "split-delay", 200*time.Millisecond, "delay for [delay_split]")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if t.host == "" || listen <= 0 {
		return errors.New("-host and -listen are required")
	}
	if payloadB64 != "" {
		b, err := base64.StdEncoding.DecodeString(payloadB64)
		if err != nil {
			return fmt.Errorf("payload: %w", err)
		}
		t.payload = string(b)
	}
	switch t.mode {
	case "direct", "payload", "http-proxy", "https-proxy", "tls", "payload-tls", "ws", "wss":
	default:
		return fmt.Errorf("unknown mode %q", t.mode)
	}
	ln, err := listenLocal(listen)
	if err != nil {
		return err
	}
	for {
		c, err := ln.Accept()
		if err != nil {
			return err
		}
		go func() {
			defer c.Close()
			up, err := t.open()
			if err != nil {
				log.Printf("sshtransport: %v", err)
				return
			}
			defer up.Close()
			relay(c, up)
		}()
	}
}

func (t *sshTransport) target() string { return net.JoinHostPort(t.host, strconv.Itoa(t.port)) }

// open dials, applies the disguise and returns a stream ready for SSH bytes.
func (t *sshTransport) open() (net.Conn, error) {
	addr := t.target()
	if t.proxy != "" {
		addr = t.proxy
	}
	raw, err := net.DialTimeout("tcp", addr, 15*time.Second)
	if err != nil {
		return nil, fmt.Errorf("dial %s: %w", addr, err)
	}
	var c net.Conn = raw
	if t.mode == "https-proxy" || t.mode == "tls" || t.mode == "payload-tls" || t.mode == "wss" {
		sni := t.sni
		if sni == "" {
			sni, _, _ = net.SplitHostPort(addr)
		}
		tc := tls.Client(raw, &tls.Config{ServerName: sni, InsecureSkipVerify: !t.verify, NextProtos: []string{"http/1.1"}})
		_ = tc.SetDeadline(time.Now().Add(15 * time.Second))
		if err := tc.Handshake(); err != nil {
			raw.Close()
			return nil, fmt.Errorf("tls: %w", err)
		}
		_ = tc.SetDeadline(time.Time{})
		c = tc
	}
	switch t.mode {
	case "direct", "tls":
		return c, nil
	case "payload", "payload-tls":
		return t.handshake(c, t.payload, false)
	case "http-proxy", "https-proxy":
		p := t.payload
		if p == "" {
			p = "CONNECT [host_port] [protocol][crlf]Host: [host_port][crlf]User-Agent: [ua][crlf][crlf]"
		}
		return t.handshake(c, p, false)
	case "ws", "wss":
		p := t.payload
		if p == "" {
			host := t.wsHost
			if host == "" {
				host = t.sni
			}
			if host == "" {
				host = t.host
			}
			key := make([]byte, 16)
			_, _ = rand.Read(key)
			p = "GET " + t.wsPath + " HTTP/1.1[crlf]Host: " + host + "[crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf]" +
				"Sec-WebSocket-Key: " + base64.StdEncoding.EncodeToString(key) + "[crlf]Sec-WebSocket-Version: 13[crlf]User-Agent: [ua][crlf][crlf]"
		}
		return t.handshake(c, p, t.framing)
	}
	return nil, fmt.Errorf("mode %q", t.mode)
}

// expand replaces the placeholders injector payloads use.
func (t *sshTransport) expand(p string) string {
	hp := t.target()
	r := strings.NewReplacer(
		"[crlf]", "\r\n", "[CRLF]", "\r\n", "[lf]", "\n", "[LF]", "\n", "[cr]", "\r", "[CR]", "\r",
		"\\r\\n", "\r\n", "\\n", "\n", "\\r", "\r",
		"[host_port]", hp, "[HOST_PORT]", hp, "[ssh]", hp, "[SSH]", hp,
		"[host]", t.host, "[HOST]", t.host, "[port]", strconv.Itoa(t.port), "[PORT]", strconv.Itoa(t.port),
		"[protocol]", "HTTP/1.1", "[PROTOCOL]", "HTTP/1.1",
		"[ua]", t.ua, "[UA]", t.ua, "[user_agent]", t.ua,
		"[raw]", "CONNECT "+hp+" HTTP/1.1", "[RAW]", "CONNECT "+hp+" HTTP/1.1",
	)
	return r.Replace(p)
}

// handshake writes the payload (honouring [split]/[delay_split]), then
// consumes any HTTP response blocks the far end sends before the SSH banner.
func (t *sshTransport) handshake(c net.Conn, payload string, framing bool) (net.Conn, error) {
	p := t.expand(payload)
	parts := splitPayload(p)
	for i, part := range parts {
		if i > 0 && part.delay {
			time.Sleep(t.splitDelay)
		}
		if part.text == "" {
			continue
		}
		if _, err := io.WriteString(c, part.text); err != nil {
			c.Close()
			return nil, fmt.Errorf("payload: %w", err)
		}
	}
	br := bufio.NewReader(c)
	_ = c.SetReadDeadline(time.Now().Add(20 * time.Second))
	upgraded := false
	for {
		peek, err := br.Peek(5)
		if err != nil {
			c.Close()
			return nil, fmt.Errorf("no answer after payload: %w", err)
		}
		if string(peek) != "HTTP/" {
			break // the SSH banner (or anything that is not HTTP) starts here
		}
		status, err := br.ReadString('\n')
		if err != nil {
			c.Close()
			return nil, err
		}
		code := 0
		if f := strings.Fields(status); len(f) >= 2 {
			code, _ = strconv.Atoi(f[1])
		}
		for { // headers
			line, err := br.ReadString('\n')
			if err != nil {
				c.Close()
				return nil, err
			}
			if line == "\r\n" || line == "\n" {
				break
			}
		}
		log.Printf("sshtransport: far end answered %d", code)
		if code == 101 {
			upgraded = true
		}
		if code == 101 || (code >= 200 && code < 300) {
			// The stream is ours now. Do not wait for more: the SSH server
			// may be waiting for the client's banner.
			break
		}
		// Injector fronts often answer 400/403/502 first and still relay, or
		// send a second answer. Wait briefly; if nothing else comes, hand the
		// stream to SSH and let its own handshake decide.
		if br.Buffered() == 0 {
			_ = c.SetReadDeadline(time.Now().Add(1500 * time.Millisecond))
			if _, err := br.Peek(1); err != nil {
				var ne net.Error
				if errors.As(err, &ne) && ne.Timeout() {
					break
				}
				c.Close()
				return nil, fmt.Errorf("far end closed after %s", strings.TrimSpace(status))
			}
		}
	}
	_ = c.SetReadDeadline(time.Time{})
	bc := &bufferedConn{Conn: c, r: br}
	if framing {
		if !upgraded {
			c.Close()
			return nil, errors.New("websocket upgrade refused")
		}
		return newWSConn(bc), nil
	}
	return bc, nil
}

type payloadPart struct {
	text  string
	delay bool
}

func splitPayload(p string) []payloadPart {
	var out []payloadPart
	delay := false
	for {
		i := strings.Index(strings.ToLower(p), "[split]")
		j := strings.Index(strings.ToLower(p), "[delay_split]")
		if i < 0 && j < 0 {
			out = append(out, payloadPart{p, delay})
			return out
		}
		if j >= 0 && (i < 0 || j < i) {
			out = append(out, payloadPart{p[:j], delay})
			p = p[j+len("[delay_split]"):]
			delay = true
		} else {
			out = append(out, payloadPart{p[:i], delay})
			p = p[i+len("[split]"):]
			delay = false
		}
	}
}

type bufferedConn struct {
	net.Conn
	r *bufio.Reader
}

func (b *bufferedConn) Read(p []byte) (int, error) { return b.r.Read(p) }

func (b *bufferedConn) CloseWrite() error {
	if cw, ok := b.Conn.(interface{ CloseWrite() error }); ok {
		return cw.CloseWrite()
	}
	return nil
}

// wsConn frames writes as masked binary WebSocket frames and unframes reads.
type wsConn struct {
	net.Conn
	rmu, wmu sync.Mutex
	left     int64
}

func newWSConn(c net.Conn) *wsConn { return &wsConn{Conn: c} }

func (w *wsConn) Write(p []byte) (int, error) {
	w.wmu.Lock()
	defer w.wmu.Unlock()
	h := []byte{0x82}
	n := len(p)
	switch {
	case n < 126:
		h = append(h, byte(0x80|n))
	case n < 65536:
		h = append(h, 0x80|126, byte(n>>8), byte(n))
	default:
		h = append(h, 0x80|127)
		h = binary.BigEndian.AppendUint64(h, uint64(n))
	}
	mask := make([]byte, 4)
	_, _ = rand.Read(mask)
	h = append(h, mask...)
	body := make([]byte, n)
	for i := range p {
		body[i] = p[i] ^ mask[i%4]
	}
	if _, err := w.Conn.Write(append(h, body...)); err != nil {
		return 0, err
	}
	return n, nil
}

func (w *wsConn) Read(p []byte) (int, error) {
	w.rmu.Lock()
	defer w.rmu.Unlock()
	for w.left == 0 {
		h := make([]byte, 2)
		if _, err := io.ReadFull(w.Conn, h); err != nil {
			return 0, err
		}
		op := h[0] & 0x0f
		masked := h[1]&0x80 != 0
		ln := int64(h[1] & 0x7f)
		switch ln {
		case 126:
			b := make([]byte, 2)
			if _, err := io.ReadFull(w.Conn, b); err != nil {
				return 0, err
			}
			ln = int64(binary.BigEndian.Uint16(b))
		case 127:
			b := make([]byte, 8)
			if _, err := io.ReadFull(w.Conn, b); err != nil {
				return 0, err
			}
			ln = int64(binary.BigEndian.Uint64(b))
		}
		if masked { // servers must not mask; tolerate by skipping the key
			if _, err := io.ReadFull(w.Conn, make([]byte, 4)); err != nil {
				return 0, err
			}
		}
		if op == 0x8 {
			return 0, io.EOF
		}
		if op == 0x9 || op == 0xA { // ping/pong: discard payload
			if _, err := io.CopyN(io.Discard, w.Conn, ln); err != nil {
				return 0, err
			}
			continue
		}
		w.left = ln
	}
	if int64(len(p)) > w.left {
		p = p[:w.left]
	}
	n, err := w.Conn.Read(p)
	w.left -= int64(n)
	return n, err
}
