package main

// SoftEther VPN client (SoftEther's own protocol over HTTPS, TCP mode).
//
// Ported from SoftEther VPN 5.02.5187 (Apache-2.0): Cedar/Protocol.c
// (ClientUploadSignature, ClientDownloadHello, ClientUploadAuth,
// PackLoginWithPassword / PlainPassword / Ticket, the Welcome packet and
// cluster Redirect), Mayaqua/Pack.c (PACK wire format), Cedar/Sam.c and
// Account.c (SHA-0 password hashing) and Cedar/Connection.c (TCP block
// framing and keep-alives). softether_watermark.gif is the WaterMark array
// from Cedar/WaterMark.c, unchanged.
//
// A Virtual Hub is a layer-2 switch: the helper gets an address by DHCP on
// the hub (or uses a static one), answers ARP, resolves the gateway and
// carries IPv4 through a userspace network stack served as SOCKS5.

import (
	"bufio"
	"bytes"
	"crypto/rand"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	_ "embed"
	"encoding/binary"
	"encoding/hex"
	"errors"
	"flag"
	"fmt"
	"io"
	"math/big"
	"net"
	"net/netip"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/amnezia-vpn/amneziawg-go/v3/tun"
	"github.com/amnezia-vpn/amneziawg-go/v3/tun/netstack"
)

//go:embed softether_watermark.gif
var seWaterMark []byte

// ---- SHA-0 (FIPS 180, without SHA-1's one-bit rotation) ----

func sha0(data []byte) [20]byte {
	h := [5]uint32{0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476, 0xC3D2E1F0}
	msg := append([]byte(nil), data...)
	bits := uint64(len(data)) * 8
	msg = append(msg, 0x80)
	for len(msg)%64 != 56 {
		msg = append(msg, 0)
	}
	var l [8]byte
	binary.BigEndian.PutUint64(l[:], bits)
	msg = append(msg, l[:]...)
	rotl := func(x uint32, n uint) uint32 { return x<<n | x>>(32-n) }
	var w [80]uint32
	for off := 0; off < len(msg); off += 64 {
		for i := 0; i < 16; i++ {
			w[i] = binary.BigEndian.Uint32(msg[off+4*i:])
		}
		for i := 16; i < 80; i++ {
			w[i] = w[i-3] ^ w[i-8] ^ w[i-14] ^ w[i-16]
		}
		a, b, c, d, e := h[0], h[1], h[2], h[3], h[4]
		for i := 0; i < 80; i++ {
			var f, k uint32
			switch {
			case i < 20:
				f, k = (b&c)|(^b&d), 0x5A827999
			case i < 40:
				f, k = b^c^d, 0x6ED9EBA1
			case i < 60:
				f, k = (b&c)|(b&d)|(c&d), 0x8F1BBCDC
			default:
				f, k = b^c^d, 0xCA62C1D6
			}
			t := rotl(a, 5) + f + e + k + w[i]
			a, b, c, d, e = t, a, rotl(b, 30), c, d
		}
		h[0] += a
		h[1] += b
		h[2] += c
		h[3] += d
		h[4] += e
	}
	var out [20]byte
	for i, v := range h {
		binary.BigEndian.PutUint32(out[4*i:], v)
	}
	return out
}

// seHashPassword is SoftEther's HashPassword: SHA-0(password + UPPER(user)).
func seHashPassword(user, password string) [20]byte {
	return sha0([]byte(password + strings.ToUpper(user)))
}

// ---- PACK ----

const (
	packInt    = 0
	packData   = 1
	packStr    = 2
	packUniStr = 3
	packInt64  = 4
)

type packValue struct {
	i   uint64
	b   []byte
	typ uint32
}

type pack struct {
	names []string
	vals  map[string][]packValue
}

func newPack() *pack { return &pack{vals: map[string][]packValue{}} }

func (p *pack) add(name string, v packValue) {
	if _, ok := p.vals[name]; !ok {
		p.names = append(p.names, name)
	}
	p.vals[name] = append(p.vals[name], v)
}
func (p *pack) addInt(n string, v uint32) { p.add(n, packValue{i: uint64(v), typ: packInt}) }
func (p *pack) addBool(n string, v bool) {
	if v {
		p.addInt(n, 1)
	} else {
		p.addInt(n, 0)
	}
}
func (p *pack) addStr(n, v string)         { p.add(n, packValue{b: []byte(v), typ: packStr}) }
func (p *pack) addData(n string, v []byte) { p.add(n, packValue{b: v, typ: packData}) }

func (p *pack) getInt(n string) uint32 {
	if v := p.vals[n]; len(v) > 0 && (v[0].typ == packInt || v[0].typ == packInt64) {
		return uint32(v[0].i)
	}
	return 0
}
func (p *pack) getData(n string) []byte {
	if v := p.vals[n]; len(v) > 0 {
		return v[0].b
	}
	return nil
}
func (p *pack) getStr(n string) string {
	b := p.getData(n)
	return strings.TrimRight(string(b), "\x00")
}

func (p *pack) marshal() []byte {
	var b bytes.Buffer
	w32 := func(v uint32) { binary.Write(&b, binary.BigEndian, v) }
	w32(uint32(len(p.names)))
	for _, n := range p.names {
		vs := p.vals[n]
		w32(uint32(len(n) + 1))
		b.WriteString(n)
		w32(vs[0].typ)
		w32(uint32(len(vs)))
		for _, v := range vs {
			switch v.typ {
			case packInt:
				w32(uint32(v.i))
			case packInt64:
				binary.Write(&b, binary.BigEndian, v.i)
			case packData, packStr:
				w32(uint32(len(v.b)))
				b.Write(v.b)
			case packUniStr:
				w32(uint32(len(v.b) + 1))
				b.Write(v.b)
				b.WriteByte(0)
			}
		}
	}
	return b.Bytes()
}

func unmarshalPack(data []byte) (*pack, error) {
	r := bytes.NewReader(data)
	r32 := func() (uint32, error) {
		var v uint32
		err := binary.Read(r, binary.BigEndian, &v)
		return v, err
	}
	readN := func(n uint32) ([]byte, error) {
		if int64(n) > int64(r.Len()) {
			return nil, errors.New("pack: truncated")
		}
		b := make([]byte, n)
		_, err := io.ReadFull(r, b)
		return b, err
	}
	p := newPack()
	count, err := r32()
	if err != nil || count > 4096 {
		return nil, errors.New("pack: bad element count")
	}
	for i := uint32(0); i < count; i++ {
		nl, err := r32()
		if err != nil || nl == 0 || nl > 64 {
			return nil, errors.New("pack: bad name")
		}
		nb, err := readN(nl - 1)
		if err != nil {
			return nil, err
		}
		typ, _ := r32()
		num, err := r32()
		if err != nil || num > 65536 {
			return nil, errors.New("pack: bad value count")
		}
		for j := uint32(0); j < num; j++ {
			v := packValue{typ: typ}
			switch typ {
			case packInt:
				x, err := r32()
				if err != nil {
					return nil, err
				}
				v.i = uint64(x)
			case packInt64:
				if err := binary.Read(r, binary.BigEndian, &v.i); err != nil {
					return nil, err
				}
			case packData, packStr, packUniStr:
				n, err := r32()
				if err != nil {
					return nil, err
				}
				if v.b, err = readN(n); err != nil {
					return nil, err
				}
			default:
				return nil, fmt.Errorf("pack: value type %d", typ)
			}
			p.add(string(nb), v)
		}
	}
	return p, nil
}

// ---- connection setup ----

var seErrors = map[uint32]string{
	2: "the server is not a SoftEther VPN server", 3: "disconnected", 4: "protocol error",
	7: "the authentication method is not supported", 8: "the Virtual Hub does not exist",
	9: "authentication failed (user name or password)", 10: "the Virtual Hub is stopped",
	12: "access denied", 15: "too many connections", 16: "the Virtual Hub is full",
	21: "server licence error", 63: "too many users", 68: "this user does not use password authentication",
	76: "the server is offline", 109: "your IP address is not allowed",
}

func seError(code uint32) error {
	if m, ok := seErrors[code]; ok {
		return fmt.Errorf("softether: %s (error %d)", m, code)
	}
	return fmt.Errorf("softether: server error %d", code)
}

type seOptions struct {
	server, sni, pin, hub, user, pass string
	plain, insecure                   bool
	timeout                           time.Duration
	log                               func(string, ...any)
}

func seDial(server string, o seOptions) (*tls.Conn, error) {
	host, _, err := net.SplitHostPort(server)
	if err != nil {
		return nil, err
	}
	sni := o.sni
	if sni == "" {
		sni = host
	}
	cfg := &tls.Config{ServerName: sni, InsecureSkipVerify: o.insecure}
	if pin := strings.ToLower(strings.ReplaceAll(o.pin, ":", "")); pin != "" {
		cfg.InsecureSkipVerify = true
		cfg.VerifyPeerCertificate = func(raw [][]byte, _ [][]*x509.Certificate) error {
			if len(raw) > 0 {
				if sum := sha256.Sum256(raw[0]); hex.EncodeToString(sum[:]) == pin {
					return nil
				}
			}
			return errors.New("server certificate does not match the pinned SHA-256")
		}
	}
	return tls.DialWithDialer(&net.Dialer{Timeout: o.timeout}, "tcp", server, cfg)
}

func randN(max int) int {
	n, _ := rand.Int(rand.Reader, big.NewInt(int64(max)))
	return int(n.Int64())
}

func httpPost(w io.Writer, host, target, ctype string, body []byte) error {
	var b bytes.Buffer
	fmt.Fprintf(&b, "POST %s HTTP/1.1\r\nHost: %s\r\nContent-Type: %s\r\nConnection: Keep-Alive\r\nContent-Length: %d\r\n\r\n",
		target, host, ctype, len(body))
	b.Write(body)
	_, err := w.Write(b.Bytes())
	return err
}

func httpRecvPack(r *bufio.Reader) (*pack, error) {
	status, err := r.ReadString('\n')
	if err != nil {
		return nil, err
	}
	if f := strings.Fields(status); len(f) < 2 || f[1] != "200" {
		return nil, fmt.Errorf("server answered %q (not a SoftEther VPN server?)", strings.TrimSpace(status))
	}
	length := -1
	ctype := ""
	for {
		line, err := r.ReadString('\n')
		if err != nil {
			return nil, err
		}
		line = strings.TrimSpace(line)
		if line == "" {
			break
		}
		k, v, _ := strings.Cut(line, ":")
		switch strings.ToLower(strings.TrimSpace(k)) {
		case "content-length":
			length, _ = strconv.Atoi(strings.TrimSpace(v))
		case "content-type":
			ctype = strings.TrimSpace(v)
		}
	}
	if !strings.EqualFold(ctype, "application/octet-stream") || length <= 0 || length > 8<<20 {
		return nil, errors.New("server is not a SoftEther VPN server (unexpected reply)")
	}
	body := make([]byte, length)
	if _, err := io.ReadFull(r, body); err != nil {
		return nil, err
	}
	return unmarshalPack(body)
}

type seSession struct {
	c      *tls.Conn
	r      *bufio.Reader
	wmu    sync.Mutex
	server string
}

// seConnect runs signature, hello, login and the Welcome packet, following
// one cluster redirect if the server asks for it.
func seConnect(o seOptions) (*seSession, error) {
	server := o.server
	var ticket []byte
	for attempt := 0; attempt < 2; attempt++ {
		c, err := seDial(server, o)
		if err != nil {
			return nil, fmt.Errorf("tls: %w", err)
		}
		c.SetDeadline(time.Now().Add(o.timeout))
		r := bufio.NewReader(c)
		host, _, _ := net.SplitHostPort(server)

		water := append(append([]byte(nil), seWaterMark...), make([]byte, randN(2000))...)
		rand.Read(water[len(seWaterMark):])
		if err := httpPost(c, host, "/vpnsvc/connect.cgi", "image/jpeg", water); err != nil {
			c.Close()
			return nil, err
		}
		hello, err := httpRecvPack(r)
		if err != nil {
			c.Close()
			return nil, err
		}
		if e := hello.getInt("error"); e != 0 {
			c.Close()
			return nil, seError(e)
		}
		random := hello.getData("random")
		if len(random) != 20 {
			c.Close()
			return nil, errors.New("softether: hello without a challenge")
		}
		o.log("server %q version %d build %d", hello.getStr("hello"), hello.getInt("version"), hello.getInt("build"))

		p := newPack()
		p.addStr("method", "login")
		p.addStr("hubname", o.hub)
		p.addStr("username", o.user)
		switch {
		case ticket != nil:
			p.addInt("authtype", 99) // AUTHTYPE_TICKET
			p.addData("ticket", ticket)
		case o.plain:
			p.addInt("authtype", 2) // CLIENT_AUTHTYPE_PLAIN_PASSWORD (RADIUS / NT domain)
			p.addStr("plain_password", o.pass)
		default:
			hp := seHashPassword(o.user, o.pass)
			sp := sha0(append(hp[:], random...))
			p.addInt("authtype", 1) // CLIENT_AUTHTYPE_PASSWORD
			p.addData("secure_password", sp[:])
		}
		p.addStr("client_str", "Ghajar VPN SoftEther client")
		p.addInt("client_ver", 502)
		p.addInt("client_build", 5187)
		p.addInt("protocol", 0) // CONNECTION_TCP
		p.addStr("hello", "Ghajar VPN SoftEther client")
		p.addInt("version", 502)
		p.addInt("build", 5187)
		p.addInt("client_id", 0)
		p.addInt("max_connection", 1)
		p.addInt("use_encrypt", 1)
		p.addInt("use_compress", 0)
		p.addInt("half_connection", 0)
		p.addBool("require_bridge_routing_mode", false)
		p.addBool("require_monitor_mode", false)
		p.addBool("qos", true)
		unique := make([]byte, 20)
		rand.Read(unique)
		p.addData("unique_id", unique)
		p.addStr("ClientProductName", "Ghajar VPN")
		p.addStr("ClientOsName", "Android")
		// No node-info "HubName": PACK element names are case-insensitive and
		// it would collide with "hubname", making the server reject the pack.
		p.addData("UniqueId", unique)
		p.addInt("ClientProductVer", 502)
		p.addInt("ClientProductBuild", 5187)
		pad := make([]byte, randN(1000))
		rand.Read(pad)
		p.addData("pencore", pad)
		if err := httpPost(c, host, "/vpnsvc/vpn.cgi", "application/octet-stream", p.marshal()); err != nil {
			c.Close()
			return nil, err
		}
		welcome, err := httpRecvPack(r)
		if err != nil {
			c.Close()
			return nil, err
		}
		if e := welcome.getInt("error"); e != 0 {
			c.Close()
			return nil, seError(e)
		}
		if welcome.getInt("Redirect") != 0 && attempt == 0 {
			ip := welcome.getInt("Ip")
			port := welcome.getInt("Port")
			ticket = welcome.getData("Ticket")
			c.Close()
			if ip == 0 || port == 0 || len(ticket) != 20 {
				return nil, errors.New("softether: cluster redirect without an address or ticket")
			}
			// IPToUINT keeps the address bytes in memory order.
			var a [4]byte
			binary.LittleEndian.PutUint32(a[:], ip)
			server = net.JoinHostPort(netip.AddrFrom4(a).String(), strconv.Itoa(int(port)))
			o.log("cluster redirect to %s", server)
			continue
		}
		o.log("session %s", welcome.getStr("session_name"))
		c.SetDeadline(time.Time{})
		return &seSession{c: c, r: r, server: server}, nil
	}
	return nil, errors.New("softether: redirect loop")
}

// send writes Ethernet frames as one block batch.
func (s *seSession) send(frames ...[]byte) error {
	var b bytes.Buffer
	binary.Write(&b, binary.BigEndian, uint32(len(frames)))
	for _, f := range frames {
		binary.Write(&b, binary.BigEndian, uint32(len(f)))
		b.Write(f)
	}
	s.wmu.Lock()
	defer s.wmu.Unlock()
	_, err := s.c.Write(b.Bytes())
	return err
}

func (s *seSession) keepAlive() error {
	n := randN(512)
	b := make([]byte, 8+n)
	binary.BigEndian.PutUint32(b, 0xffffffff)
	binary.BigEndian.PutUint32(b[4:], uint32(n))
	rand.Read(b[8:])
	s.wmu.Lock()
	defer s.wmu.Unlock()
	_, err := s.c.Write(b)
	return err
}

// recv reads the next batch of frames; a keep-alive returns an empty batch.
func (s *seSession) recv() ([][]byte, error) {
	var hdr [4]byte
	{
		if _, err := io.ReadFull(s.r, hdr[:]); err != nil {
			return nil, err
		}
		num := binary.BigEndian.Uint32(hdr[:])
		if num == 0xffffffff {
			if _, err := io.ReadFull(s.r, hdr[:]); err != nil {
				return nil, err
			}
			n := binary.BigEndian.Uint32(hdr[:])
			if n > 4096 {
				return nil, errors.New("softether: bad keep-alive")
			}
			if _, err := io.CopyN(io.Discard, s.r, int64(n)); err != nil {
				return nil, err
			}
			return nil, nil // keep-alive: the link is alive, no frames
		}
		if num > 8192 {
			return nil, errors.New("softether: bad block count")
		}
		out := make([][]byte, 0, num)
		for i := uint32(0); i < num; i++ {
			if _, err := io.ReadFull(s.r, hdr[:]); err != nil {
				return nil, err
			}
			n := binary.BigEndian.Uint32(hdr[:])
			if n > 3200 {
				return nil, errors.New("softether: oversized block")
			}
			f := make([]byte, n)
			if _, err := io.ReadFull(s.r, f); err != nil {
				return nil, err
			}
			out = append(out, f)
		}
		return out, nil
	}
}

// ---- layer 2: Ethernet, ARP, DHCP ----

var bcastMAC = net.HardwareAddr{0xff, 0xff, 0xff, 0xff, 0xff, 0xff}

func ethFrame(dst, src net.HardwareAddr, typ uint16, payload []byte) []byte {
	f := make([]byte, 14+len(payload))
	copy(f, dst)
	copy(f[6:], src)
	binary.BigEndian.PutUint16(f[12:], typ)
	copy(f[14:], payload)
	return f
}

func arpPacket(op uint16, sha net.HardwareAddr, spa netip.Addr, tha net.HardwareAddr, tpa netip.Addr) []byte {
	p := make([]byte, 28)
	binary.BigEndian.PutUint16(p, 1)
	binary.BigEndian.PutUint16(p[2:], 0x0800)
	p[4], p[5] = 6, 4
	binary.BigEndian.PutUint16(p[6:], op)
	copy(p[8:], sha)
	s4 := spa.As4()
	copy(p[14:], s4[:])
	copy(p[18:], tha)
	t4 := tpa.As4()
	copy(p[24:], t4[:])
	return p
}

func ipChecksum(b []byte) uint16 {
	var s uint32
	for i := 0; i+1 < len(b); i += 2 {
		s += uint32(binary.BigEndian.Uint16(b[i:]))
	}
	if len(b)%2 == 1 {
		s += uint32(b[len(b)-1]) << 8
	}
	for s>>16 != 0 {
		s = s&0xffff + s>>16
	}
	return ^uint16(s)
}

// udpIPv4 builds an IPv4/UDP packet (UDP checksum 0 is allowed for IPv4).
func udpIPv4(src, dst netip.Addr, sport, dport uint16, payload []byte) []byte {
	p := make([]byte, 28+len(payload))
	p[0] = 0x45
	binary.BigEndian.PutUint16(p[2:], uint16(len(p)))
	p[8] = 64
	p[9] = 17
	s4, d4 := src.As4(), dst.As4()
	copy(p[12:], s4[:])
	copy(p[16:], d4[:])
	binary.BigEndian.PutUint16(p[10:], ipChecksum(p[:20]))
	binary.BigEndian.PutUint16(p[20:], sport)
	binary.BigEndian.PutUint16(p[22:], dport)
	binary.BigEndian.PutUint16(p[24:], uint16(8+len(payload)))
	copy(p[28:], payload)
	return p
}

type dhcpLease struct {
	addr, router, server netip.Addr
	prefix               int
	dns                  []netip.Addr
	lease                time.Duration
}

func dhcpMessage(msgType byte, xid uint32, mac net.HardwareAddr, ciaddr netip.Addr, opts ...[]byte) []byte {
	m := make([]byte, 240)
	m[0], m[1], m[2] = 1, 1, 6
	binary.BigEndian.PutUint32(m[4:], xid)
	binary.BigEndian.PutUint16(m[10:], 0x8000) // broadcast reply
	if ciaddr.IsValid() {
		c4 := ciaddr.As4()
		copy(m[12:], c4[:])
	}
	copy(m[28:], mac)
	copy(m[236:], []byte{99, 130, 83, 99})
	m = append(m, 53, 1, msgType)
	m = append(m, 61, 7, 1)
	m = append(m, mac...)
	m = append(m, 12, 6)
	m = append(m, "ghajar"...)
	m = append(m, 55, 4, 1, 3, 6, 51)
	for _, o := range opts {
		m = append(m, o...)
	}
	return append(m, 255)
}

func parseDHCP(b []byte, xid uint32) (msgType byte, yiaddr netip.Addr, l dhcpLease, ok bool) {
	if len(b) < 240 || b[0] != 2 || binary.BigEndian.Uint32(b[4:]) != xid || !bytes.Equal(b[236:240], []byte{99, 130, 83, 99}) {
		return
	}
	yiaddr = netip.AddrFrom4([4]byte(b[16:20]))
	l.prefix = 24
	for i := 240; i < len(b); {
		t := b[i]
		if t == 255 {
			break
		}
		if t == 0 {
			i++
			continue
		}
		if i+1 >= len(b) || i+2+int(b[i+1]) > len(b) {
			break
		}
		v := b[i+2 : i+2+int(b[i+1])]
		switch t {
		case 53:
			if len(v) == 1 {
				msgType = v[0]
			}
		case 1:
			if len(v) == 4 {
				ones, _ := net.IPMask(v).Size()
				l.prefix = ones
			}
		case 3:
			if len(v) >= 4 {
				l.router = netip.AddrFrom4([4]byte(v[:4]))
			}
		case 6:
			for j := 0; j+4 <= len(v); j += 4 {
				l.dns = append(l.dns, netip.AddrFrom4([4]byte(v[j:j+4])))
			}
		case 51:
			if len(v) == 4 {
				l.lease = time.Duration(binary.BigEndian.Uint32(v)) * time.Second
			}
		case 54:
			if len(v) == 4 {
				l.server = netip.AddrFrom4([4]byte(v))
			}
		}
		i += 2 + len(v)
	}
	l.addr = yiaddr
	return msgType, yiaddr, l, msgType != 0
}

// seLink is the client's port on the Virtual Hub.
type seLink struct {
	s      *seSession
	mac    net.HardwareAddr
	lease  dhcpLease
	mu     sync.Mutex
	arp    map[netip.Addr]net.HardwareAddr
	arpAsk map[netip.Addr]time.Time
	dhcpCh chan []byte
	dev    tun.Device
	lastRx time.Time
	o      seOptions
}

func (l *seLink) sendIP(dst netip.Addr, pkt []byte) error {
	next := dst
	pfx := netip.PrefixFrom(l.lease.addr, l.lease.prefix)
	if !pfx.Contains(dst) && l.lease.router.IsValid() {
		next = l.lease.router
	}
	if dst == netip.AddrFrom4([4]byte{255, 255, 255, 255}) {
		return l.s.send(ethFrame(bcastMAC, l.mac, 0x0800, pkt))
	}
	l.mu.Lock()
	mac, ok := l.arp[next]
	ask := !ok && time.Since(l.arpAsk[next]) > time.Second
	if ask {
		l.arpAsk[next] = time.Now()
	}
	l.mu.Unlock()
	if ok {
		return l.s.send(ethFrame(mac, l.mac, 0x0800, pkt))
	}
	if ask {
		// The packet is dropped; TCP retransmits once the MAC is known.
		return l.s.send(ethFrame(bcastMAC, l.mac, 0x0806, arpPacket(1, l.mac, l.lease.addr, make(net.HardwareAddr, 6), next)))
	}
	return nil
}

func (l *seLink) handleFrame(f []byte) {
	if len(f) < 14 {
		return
	}
	dst := net.HardwareAddr(f[:6])
	if !bytes.Equal(dst, l.mac) && !bytes.Equal(dst, bcastMAC) {
		return
	}
	payload := f[14:]
	switch binary.BigEndian.Uint16(f[12:]) {
	case 0x0806:
		if len(payload) < 28 || binary.BigEndian.Uint16(payload[2:]) != 0x0800 {
			return
		}
		op := binary.BigEndian.Uint16(payload[6:])
		sha := net.HardwareAddr(append([]byte(nil), payload[8:14]...))
		spa := netip.AddrFrom4([4]byte(payload[14:18]))
		tpa := netip.AddrFrom4([4]byte(payload[24:28]))
		if spa.IsValid() && !spa.IsUnspecified() {
			l.mu.Lock()
			l.arp[spa] = sha
			l.mu.Unlock()
		}
		if op == 1 && l.lease.addr.IsValid() && tpa == l.lease.addr {
			l.s.send(ethFrame(sha, l.mac, 0x0806, arpPacket(2, l.mac, l.lease.addr, sha, spa)))
		}
	case 0x0800:
		if len(payload) < 20 || payload[0]>>4 != 4 {
			return
		}
		total := int(binary.BigEndian.Uint16(payload[2:]))
		if total < 20 || total > len(payload) {
			return
		}
		payload = payload[:total]
		ihl := int(payload[0]&0x0f) * 4
		if payload[9] == 17 && len(payload) >= ihl+8 && binary.BigEndian.Uint16(payload[ihl+2:]) == 68 {
			select {
			case l.dhcpCh <- append([]byte(nil), payload[ihl+8:]...):
			default:
			}
			return
		}
		if l.dev != nil {
			buf := make([]byte, 16+len(payload))
			copy(buf[16:], payload)
			l.dev.Write([][]byte{buf}, 16)
		}
	}
}

// dhcp runs DISCOVER/OFFER/REQUEST/ACK (or a renewing REQUEST when ciaddr
// is set) on the hub.
func (l *seLink) dhcp(renew bool) (dhcpLease, error) {
	var xb [4]byte
	rand.Read(xb[:])
	xid := binary.BigEndian.Uint32(xb[:])
	zero := netip.AddrFrom4([4]byte{})
	bcast := netip.AddrFrom4([4]byte{255, 255, 255, 255})
	send := func(m []byte, src netip.Addr) error {
		return l.s.send(ethFrame(bcastMAC, l.mac, 0x0800, udpIPv4(src, bcast, 68, 67, m)))
	}
	wait := func(want byte, d time.Duration) (dhcpLease, bool) {
		t := time.After(d)
		for {
			select {
			case m := <-l.dhcpCh:
				if typ, _, lease, ok := parseDHCP(m, xid); ok {
					if typ == 6 {
						return lease, false // NAK
					}
					if typ == want {
						return lease, true
					}
				}
			case <-t:
				return dhcpLease{}, false
			}
		}
	}
	var offer dhcpLease
	if renew {
		offer = l.lease
	} else {
		for try := 0; ; try++ {
			if try == 4 {
				return dhcpLease{}, errors.New("dhcp: no offer from the Virtual Hub (enable SecureNAT / a DHCP server, or set a static address)")
			}
			send(dhcpMessage(1, xid, l.mac, netip.Addr{}), zero)
			var ok bool
			if offer, ok = wait(2, 3*time.Second); ok {
				break
			}
		}
	}
	for try := 0; try < 4; try++ {
		a4, s4 := offer.addr.As4(), offer.server.As4()
		opts := [][]byte{append([]byte{50, 4}, a4[:]...)}
		src := zero
		var ci netip.Addr
		if renew {
			ci, src = l.lease.addr, l.lease.addr
		} else if offer.server.IsValid() {
			opts = append(opts, append([]byte{54, 4}, s4[:]...))
		}
		send(dhcpMessage(3, xid, l.mac, ci, opts...), src)
		if ack, ok := wait(5, 3*time.Second); ok {
			if !ack.server.IsValid() {
				ack.server = offer.server
			}
			return ack, nil
		}
	}
	return dhcpLease{}, errors.New("dhcp: the Virtual Hub did not acknowledge the address")
}

func runSoftEther(args []string) error {
	fs := flag.NewFlagSet("softether", flag.ContinueOnError)
	listen := fs.Int("listen", 0, "local SOCKS5 port on 127.0.0.1")
	server := fs.String("server", "", "host:port of the SoftEther VPN server")
	hub := fs.String("hub", "DEFAULT", "Virtual Hub name")
	user := fs.String("user", "", "user name")
	plain := fs.Bool("plain", false, "send the password for RADIUS / NT-domain authentication")
	sni := fs.String("sni", "", "TLS server name")
	pin := fs.String("pin", "", "hex SHA-256 of the server certificate")
	insecure := fs.Bool("insecure", false, "skip certificate verification")
	static := fs.String("ip", "", "static address a.b.c.d/nn instead of DHCP")
	gw := fs.String("gw", "", "gateway for a static address")
	dns := fs.String("dns", "", "DNS servers (comma list), overrides DHCP")
	mtu := fs.Int("mtu", 1400, "IP MTU")
	verbose := fs.Bool("v", false, "log negotiation")
	if err := fs.Parse(args); err != nil {
		return err
	}
	pass := os.Getenv("SE_PASSWORD")
	if pf := os.Getenv("SE_PASSWORD_FILE"); pf != "" {
		b, err := os.ReadFile(pf)
		if err != nil {
			return err
		}
		pass = strings.TrimRight(string(b), "\r\n")
	}
	if *server == "" || *user == "" {
		return errors.New("softether: -server and -user are required")
	}
	if _, _, err := net.SplitHostPort(*server); err != nil {
		*server = net.JoinHostPort(*server, "443")
	}
	o := seOptions{server: *server, sni: *sni, pin: *pin, hub: *hub, user: *user, pass: pass, plain: *plain,
		insecure: *insecure, timeout: 25 * time.Second,
		log: func(f string, a ...any) {
			if *verbose {
				fmt.Fprintf(os.Stderr, "softether: "+f+"\n", a...)
			}
		}}
	sess, err := seConnect(o)
	if err != nil {
		return err
	}
	defer sess.c.Close()
	mac := make(net.HardwareAddr, 6)
	rand.Read(mac)
	mac[0] = 0x5e // SoftEther's convention; locally administered, unicast
	l := &seLink{s: sess, mac: mac, arp: map[netip.Addr]net.HardwareAddr{}, arpAsk: map[netip.Addr]time.Time{},
		dhcpCh: make(chan []byte, 16), o: o, lastRx: time.Now()}

	errc := make(chan error, 3)
	var rxMu sync.Mutex
	go func() {
		for {
			frames, err := sess.recv()
			if err != nil {
				errc <- fmt.Errorf("softether: link closed: %w", err)
				return
			}
			rxMu.Lock()
			l.lastRx = time.Now()
			rxMu.Unlock()
			for _, f := range frames {
				l.handleFrame(f)
			}
		}
	}()
	go func() {
		for {
			time.Sleep(5 * time.Second)
			if err := sess.keepAlive(); err != nil {
				errc <- err
				return
			}
		}
	}()

	if *static != "" {
		p, err := netip.ParsePrefix(*static)
		if err != nil {
			return fmt.Errorf("softether: static address: %w", err)
		}
		l.lease = dhcpLease{addr: p.Addr(), prefix: p.Bits()}
		if *gw != "" {
			if l.lease.router, err = netip.ParseAddr(*gw); err != nil {
				return fmt.Errorf("softether: gateway: %w", err)
			}
		}
	} else {
		lease, err := l.dhcp(false)
		if err != nil {
			return err
		}
		l.lease = lease
	}
	if *dns != "" {
		l.lease.dns = nil
		for _, d := range strings.Split(*dns, ",") {
			if a, err := netip.ParseAddr(strings.TrimSpace(d)); err == nil {
				l.lease.dns = append(l.lease.dns, a)
			}
		}
	}
	if len(l.lease.dns) == 0 {
		if l.lease.router.IsValid() {
			l.lease.dns = []netip.Addr{l.lease.router}
		} else {
			l.lease.dns = []netip.Addr{netip.MustParseAddr("1.1.1.1")}
		}
	}
	fmt.Fprintf(os.Stderr, "softether: link up on hub %s, address %s/%d, gateway %s, dns %v\n",
		*hub, l.lease.addr, l.lease.prefix, l.lease.router, l.lease.dns)
	// Announce ourselves and learn the gateway's MAC early.
	sess.send(ethFrame(bcastMAC, mac, 0x0806, arpPacket(1, mac, l.lease.addr, make(net.HardwareAddr, 6), l.lease.addr)))
	if l.lease.router.IsValid() {
		l.mu.Lock()
		l.arpAsk[l.lease.router] = time.Now()
		l.mu.Unlock()
		sess.send(ethFrame(bcastMAC, mac, 0x0806, arpPacket(1, mac, l.lease.addr, make(net.HardwareAddr, 6), l.lease.router)))
	}

	dev, tnet, err := netstack.CreateNetTUN([]netip.Addr{l.lease.addr}, l.lease.dns, *mtu)
	if err != nil {
		return fmt.Errorf("netstack: %w", err)
	}
	l.dev = dev
	go func() {
		bufs := [][]byte{make([]byte, 16+*mtu+64)}
		sizes := []int{0}
		for {
			n, err := dev.Read(bufs, sizes, 16)
			if err != nil {
				errc <- err
				return
			}
			for i := 0; i < n; i++ {
				pkt := append([]byte(nil), bufs[i][16:16+sizes[i]]...)
				if len(pkt) >= 20 && pkt[0]>>4 == 4 {
					if err := l.sendIP(netip.AddrFrom4([4]byte(pkt[16:20])), pkt); err != nil {
						errc <- err
						return
					}
				}
			}
		}
	}()
	if *static == "" && l.lease.lease > 0 {
		go func() {
			for {
				time.Sleep(max(l.lease.lease/2, 30*time.Second))
				if lease, err := l.dhcp(true); err == nil {
					if lease.addr != l.lease.addr {
						errc <- errors.New("softether: DHCP renewal changed the address; reconnecting")
						return
					}
					l.lease.lease = lease.lease
				}
			}
		}()
	}
	ln, err := listenLocal(*listen)
	if err != nil {
		return err
	}
	go serveSocks5(ln, netstackBackend{tnet})
	t := time.NewTicker(15 * time.Second)
	defer t.Stop()
	for {
		select {
		case err := <-errc:
			return err
		case <-t.C:
			rxMu.Lock()
			idle := time.Since(l.lastRx)
			rxMu.Unlock()
			if idle > 60*time.Second {
				return errors.New("softether: no answer from the server for 60s")
			}
		}
	}
}
