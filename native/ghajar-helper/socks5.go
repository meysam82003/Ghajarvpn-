package main

import (
	"context"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"log"
	"net"
	"strconv"
	"sync"
	"time"
)

// socksBackend is what a SOCKS5 front-end needs from an engine.
type socksBackend interface {
	DialTCP(ctx context.Context, addr string) (net.Conn, error)
	// ListenUDP returns nil, nil when the engine carries no UDP.
	ListenUDP() (net.PacketConn, error)
	ResolveUDP(ctx context.Context, host string, port int) (net.Addr, error)
}

// serveSocks5 runs a SOCKS5 server (no auth, CONNECT and UDP ASSOCIATE) on ln.
func serveSocks5(ln net.Listener, b socksBackend) error {
	for {
		c, err := ln.Accept()
		if err != nil {
			return err
		}
		go func() {
			defer c.Close()
			if err := handleSocks(c, b); err != nil && !errors.Is(err, io.EOF) {
				log.Printf("socks: %v", err)
			}
		}()
	}
}

func handleSocks(c net.Conn, b socksBackend) error {
	_ = c.SetDeadline(time.Now().Add(30 * time.Second))
	h := make([]byte, 2)
	if _, err := io.ReadFull(c, h); err != nil {
		return err
	}
	if h[0] != 5 {
		return fmt.Errorf("not socks5")
	}
	methods := make([]byte, h[1])
	if _, err := io.ReadFull(c, methods); err != nil {
		return err
	}
	if _, err := c.Write([]byte{5, 0}); err != nil {
		return err
	}
	req := make([]byte, 4)
	if _, err := io.ReadFull(c, req); err != nil {
		return err
	}
	host, port, err := readAddr(c, req[3])
	if err != nil {
		return err
	}
	_ = c.SetDeadline(time.Time{})
	switch req[1] {
	case 1: // CONNECT
		ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
		up, err := b.DialTCP(ctx, net.JoinHostPort(host, strconv.Itoa(port)))
		cancel()
		if err != nil {
			_, _ = c.Write([]byte{5, 5, 0, 1, 0, 0, 0, 0, 0, 0})
			return fmt.Errorf("connect %s:%d: %w", host, port, err)
		}
		defer up.Close()
		if _, err := c.Write([]byte{5, 0, 0, 1, 0, 0, 0, 0, 0, 0}); err != nil {
			return err
		}
		relay(c, up)
		return nil
	case 3: // UDP ASSOCIATE
		return udpAssociate(c, b)
	default:
		_, _ = c.Write([]byte{5, 7, 0, 1, 0, 0, 0, 0, 0, 0})
		return fmt.Errorf("command %d not supported", req[1])
	}
}

func readAddr(r io.Reader, atyp byte) (string, int, error) {
	var host string
	switch atyp {
	case 1:
		b := make([]byte, 4)
		if _, err := io.ReadFull(r, b); err != nil {
			return "", 0, err
		}
		host = net.IP(b).String()
	case 4:
		b := make([]byte, 16)
		if _, err := io.ReadFull(r, b); err != nil {
			return "", 0, err
		}
		host = net.IP(b).String()
	case 3:
		l := make([]byte, 1)
		if _, err := io.ReadFull(r, l); err != nil {
			return "", 0, err
		}
		b := make([]byte, l[0])
		if _, err := io.ReadFull(r, b); err != nil {
			return "", 0, err
		}
		host = string(b)
	default:
		return "", 0, fmt.Errorf("address type %d", atyp)
	}
	p := make([]byte, 2)
	if _, err := io.ReadFull(r, p); err != nil {
		return "", 0, err
	}
	return host, int(binary.BigEndian.Uint16(p)), nil
}

func relay(a, b net.Conn) {
	var wg sync.WaitGroup
	wg.Add(2)
	cp := func(dst, src net.Conn) {
		defer wg.Done()
		_, _ = io.Copy(dst, src)
		if cw, ok := dst.(interface{ CloseWrite() error }); ok {
			_ = cw.CloseWrite()
		} else {
			_ = dst.Close()
		}
	}
	go cp(a, b)
	go cp(b, a)
	wg.Wait()
}

// udpAssociate relays SOCKS5 UDP datagrams between the client and the
// engine's packet connection until the control connection closes.
func udpAssociate(c net.Conn, b socksBackend) error {
	pc, err := b.ListenUDP()
	if err != nil || pc == nil {
		_, _ = c.Write([]byte{5, 7, 0, 1, 0, 0, 0, 0, 0, 0})
		if err == nil {
			err = fmt.Errorf("this engine carries no UDP")
		}
		return err
	}
	defer pc.Close()
	local, err := net.ListenUDP("udp", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	if err != nil {
		return err
	}
	defer local.Close()
	la := local.LocalAddr().(*net.UDPAddr)
	reply := []byte{5, 0, 0, 1, 127, 0, 0, 1, 0, 0}
	binary.BigEndian.PutUint16(reply[8:], uint16(la.Port))
	if _, err := c.Write(reply); err != nil {
		return err
	}
	var client *net.UDPAddr
	var mu sync.Mutex
	done := make(chan struct{})
	go func() { // engine -> client
		buf := make([]byte, 65535)
		for {
			n, from, err := pc.ReadFrom(buf)
			if err != nil {
				return
			}
			mu.Lock()
			to := client
			mu.Unlock()
			if to == nil {
				continue
			}
			hdr := socksUDPHeader(from)
			_, _ = local.WriteToUDP(append(hdr, buf[:n]...), to)
		}
	}()
	go func() { // client -> engine
		defer close(done)
		buf := make([]byte, 65535)
		for {
			n, from, err := local.ReadFromUDP(buf)
			if err != nil {
				return
			}
			if n < 10 || buf[2] != 0 { // fragmentation unsupported
				continue
			}
			mu.Lock()
			client = from
			mu.Unlock()
			host, port, off, err := parseUDPAddr(buf[:n])
			if err != nil {
				continue
			}
			ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			dst, err := b.ResolveUDP(ctx, host, port)
			cancel()
			if err != nil {
				continue
			}
			_, _ = pc.WriteTo(buf[off:n], dst)
		}
	}()
	_, _ = io.Copy(io.Discard, c) // control connection closes -> association ends
	_ = local.Close()
	<-done
	return nil
}

func parseUDPAddr(p []byte) (string, int, int, error) {
	switch p[3] {
	case 1:
		if len(p) < 10 {
			return "", 0, 0, fmt.Errorf("short")
		}
		return net.IP(p[4:8]).String(), int(binary.BigEndian.Uint16(p[8:10])), 10, nil
	case 4:
		if len(p) < 22 {
			return "", 0, 0, fmt.Errorf("short")
		}
		return net.IP(p[4:20]).String(), int(binary.BigEndian.Uint16(p[20:22])), 22, nil
	case 3:
		l := int(p[4])
		if len(p) < 7+l {
			return "", 0, 0, fmt.Errorf("short")
		}
		return string(p[5 : 5+l]), int(binary.BigEndian.Uint16(p[5+l : 7+l])), 7 + l, nil
	}
	return "", 0, 0, fmt.Errorf("atyp")
}

func socksUDPHeader(a net.Addr) []byte {
	var ip net.IP
	port := 0
	switch v := a.(type) {
	case *net.UDPAddr:
		ip, port = v.IP, v.Port
	default:
		h, p, _ := net.SplitHostPort(a.String())
		ip = net.ParseIP(h)
		port, _ = strconv.Atoi(p)
	}
	if ip4 := ip.To4(); ip4 != nil {
		h := []byte{0, 0, 0, 1}
		h = append(h, ip4...)
		return binary.BigEndian.AppendUint16(h, uint16(port))
	}
	h := []byte{0, 0, 0, 4}
	h = append(h, ip.To16()...)
	return binary.BigEndian.AppendUint16(h, uint16(port))
}

// listenLocal listens on 127.0.0.1:port and announces readiness.
func listenLocal(port int) (net.Listener, error) {
	ln, err := net.Listen("tcp", net.JoinHostPort("127.0.0.1", strconv.Itoa(port)))
	if err != nil {
		return nil, err
	}
	fmt.Printf("ready %s\n", ln.Addr())
	return ln, nil
}
