package main

// SSTP client (MS-SSTP): PPP over an HTTPS "SSTP_DUPLEX_POST" stream, with
// PAP or MS-CHAPv2 authentication and the SSTP crypto binding, then IPCP.
// The negotiated IPv4 link is carried by a userspace network stack and served
// as SOCKS5, like the awg mode. The wire formats follow [MS-SSTP], RFC 1661
// (PPP/LCP), RFC 1332 + 1877 (IPCP/DNS), RFC 1334 (PAP), RFC 2759 (MS-CHAPv2)
// and RFC 3079 (MPPE keys, used for the crypto binding's HLAK); the
// Open-SSTP-Client project (MIT) was read as a reference.

import (
	"bufio"
	"crypto/des"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha1"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/binary"
	"encoding/hex"
	"errors"
	"flag"
	"fmt"
	"hash"
	"io"
	"net"
	"net/netip"
	"os"
	"strings"
	"sync"
	"time"
	"unicode/utf16"

	"github.com/amnezia-vpn/amneziawg-go/v3/tun"
	"github.com/amnezia-vpn/amneziawg-go/v3/tun/netstack"
	"golang.org/x/crypto/md4"
)

const (
	sstpPath = "/sra_{BA195980-CD49-458b-9E23-C84EE0ADCD75}/"

	sstpMsgConnectRequest = 1
	sstpMsgConnectAck     = 2
	sstpMsgConnectNak     = 3
	sstpMsgConnected      = 4
	sstpMsgAbort          = 5
	sstpMsgDisconnect     = 6
	sstpMsgDisconnectAck  = 7
	sstpMsgEchoRequest    = 8
	sstpMsgEchoResponse   = 9

	sstpAttrEncapsulatedProtocol = 1
	sstpAttrCryptoBinding        = 3
	sstpAttrCryptoBindingReq     = 4

	pppLCP    = 0xc021
	pppPAP    = 0xc023
	pppCHAP   = 0xc223
	pppIPCP   = 0x8021
	pppIPv4   = 0x0021
	chapMSv2  = 0x81
	lcpMRU    = 1
	lcpACCM   = 2
	lcpAuth   = 3
	lcpMagic  = 5
	ipcpAddr  = 3
	ipcpDNS1  = 129
	ipcpDNS2  = 131
	confReq   = 1
	confAck   = 2
	confNak   = 3
	confRej   = 4
	termReq   = 5
	termAck   = 6
	codeRej   = 7
	protoRej  = 8
	echoReq   = 9
	echoReply = 10
)

// sstpConn is the SSTP layer on top of the TLS stream.
type sstpConn struct {
	c       net.Conn
	r       *bufio.Reader
	wmu     sync.Mutex
	certDER []byte
}

func (s *sstpConn) writePacket(control bool, body []byte) error {
	n := 4 + len(body)
	if n > 0x0fff {
		return fmt.Errorf("sstp packet too large: %d", n)
	}
	b := make([]byte, n)
	b[0] = 0x10
	if control {
		b[1] = 1
	}
	binary.BigEndian.PutUint16(b[2:], uint16(n))
	copy(b[4:], body)
	s.wmu.Lock()
	defer s.wmu.Unlock()
	_, err := s.c.Write(b)
	return err
}

func (s *sstpConn) readPacket() (control bool, body []byte, err error) {
	var h [4]byte
	if _, err = io.ReadFull(s.r, h[:]); err != nil {
		return
	}
	if h[0] != 0x10 {
		return false, nil, fmt.Errorf("sstp: bad version 0x%02x", h[0])
	}
	n := int(binary.BigEndian.Uint16(h[2:]) & 0x0fff)
	if n < 4 {
		return false, nil, fmt.Errorf("sstp: bad length %d", n)
	}
	body = make([]byte, n-4)
	if _, err = io.ReadFull(s.r, body); err != nil {
		return
	}
	return h[1]&1 == 1, body, nil
}

// sstpControl builds a control message body: type, attribute count, attributes.
func sstpControl(msgType uint16, attrs ...[]byte) []byte {
	b := make([]byte, 4)
	binary.BigEndian.PutUint16(b, msgType)
	binary.BigEndian.PutUint16(b[2:], uint16(len(attrs)))
	for _, a := range attrs {
		b = append(b, a...)
	}
	return b
}

func sstpAttr(id byte, value []byte) []byte {
	a := make([]byte, 4, 4+len(value))
	a[1] = id
	binary.BigEndian.PutUint16(a[2:], uint16(4+len(value)))
	return append(a, value...)
}

func (s *sstpConn) sendControl(msgType uint16, attrs ...[]byte) error {
	return s.writePacket(true, sstpControl(msgType, attrs...))
}

func (s *sstpConn) sendPPP(proto uint16, payload []byte) error {
	b := make([]byte, 4+len(payload))
	b[0], b[1] = 0xff, 0x03
	binary.BigEndian.PutUint16(b[2:], proto)
	copy(b[4:], payload)
	return s.writePacket(false, b)
}

// splitPPP strips the optional FF 03 header and returns protocol + payload.
func splitPPP(b []byte) (uint16, []byte, error) {
	if len(b) >= 2 && b[0] == 0xff && b[1] == 0x03 {
		b = b[2:]
	}
	if len(b) < 1 {
		return 0, nil, errors.New("ppp: empty frame")
	}
	if b[0]&1 == 1 { // compressed protocol field
		return uint16(b[0]), b[1:], nil
	}
	if len(b) < 2 {
		return 0, nil, errors.New("ppp: short frame")
	}
	return binary.BigEndian.Uint16(b), b[2:], nil
}

// dialSSTP opens TLS, performs the HTTP handshake and returns the SSTP layer.
// A non-empty pin (hex SHA-256 of the server's leaf certificate) replaces
// CA verification, for servers with a self-signed certificate.
func dialSSTP(server, sni, pin string, insecure bool, timeout time.Duration) (*sstpConn, error) {
	host, _, err := net.SplitHostPort(server)
	if err != nil {
		return nil, err
	}
	if sni == "" {
		sni = host
	}
	d := &net.Dialer{Timeout: timeout}
	cfg := &tls.Config{ServerName: sni, InsecureSkipVerify: insecure}
	if pin = strings.ToLower(strings.ReplaceAll(pin, ":", "")); pin != "" {
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
	tc, err := tls.DialWithDialer(d, "tcp", server, cfg)
	if err != nil {
		return nil, fmt.Errorf("tls: %w", err)
	}
	certs := tc.ConnectionState().PeerCertificates
	if len(certs) == 0 {
		tc.Close()
		return nil, errors.New("tls: server sent no certificate")
	}
	var guid [16]byte
	rand.Read(guid[:])
	req := "SSTP_DUPLEX_POST " + sstpPath + " HTTP/1.1\r\n" +
		"Content-Length: 18446744073709551615\r\n" +
		"Host: " + sni + "\r\n" +
		fmt.Sprintf("SSTPCORRELATIONID: {%X-%X-%X-%X-%X}\r\n\r\n", guid[0:4], guid[4:6], guid[6:8], guid[8:10], guid[10:16])
	tc.SetDeadline(time.Now().Add(timeout))
	if _, err := io.WriteString(tc, req); err != nil {
		tc.Close()
		return nil, err
	}
	r := bufio.NewReader(tc)
	status, err := r.ReadString('\n')
	if err != nil {
		tc.Close()
		return nil, fmt.Errorf("http: %w", err)
	}
	if f := strings.Fields(status); len(f) < 2 || f[1] != "200" {
		tc.Close()
		return nil, fmt.Errorf("http: server answered %q (not an SSTP endpoint?)", strings.TrimSpace(status))
	}
	for {
		line, err := r.ReadString('\n')
		if err != nil {
			tc.Close()
			return nil, fmt.Errorf("http: %w", err)
		}
		if strings.TrimSpace(line) == "" {
			break
		}
	}
	tc.SetDeadline(time.Time{})
	return &sstpConn{c: tc, r: r, certDER: certs[0].Raw}, nil
}

// ---- MS-CHAPv2 (RFC 2759) and MPPE master keys (RFC 3079) ----

func ntPasswordHash(password string) []byte {
	u := utf16.Encode([]rune(password))
	b := make([]byte, 2*len(u))
	for i, v := range u {
		binary.LittleEndian.PutUint16(b[2*i:], v)
	}
	h := md4.New()
	h.Write(b)
	return h.Sum(nil)
}

func md4Sum(b []byte) []byte { h := md4.New(); h.Write(b); return h.Sum(nil) }

func challengeHash(peer, auth []byte, user string) []byte {
	h := sha1.New()
	h.Write(peer)
	h.Write(auth)
	h.Write([]byte(user))
	return h.Sum(nil)[:8]
}

func desKey(k []byte) []byte {
	return []byte{
		k[0] & 0xfe,
		k[0]<<7 | k[1]>>1,
		k[1]<<6 | k[2]>>2,
		k[2]<<5 | k[3]>>3,
		k[3]<<4 | k[4]>>4,
		k[4]<<3 | k[5]>>5,
		k[5]<<2 | k[6]>>6,
		k[6] << 1,
	}
}

func challengeResponse(challenge, pwHash []byte) []byte {
	z := make([]byte, 21)
	copy(z, pwHash)
	out := make([]byte, 24)
	for i := 0; i < 3; i++ {
		c, _ := des.NewCipher(desKey(z[i*7 : i*7+7]))
		c.Encrypt(out[i*8:], challenge)
	}
	return out
}

func ntResponse(auth, peer []byte, user, password string) []byte {
	return challengeResponse(challengeHash(peer, auth, user), ntPasswordHash(password))
}

var (
	authMagic1 = []byte("Magic server to client signing constant")
	authMagic2 = []byte("Pad to make it do more than one iteration")
	mppeMagic1 = []byte("This is the MPPE Master Key")
	mppeMagic2 = []byte("On the client side, this is the send key; on the server side, it is the receive key.")
	mppeMagic3 = []byte("On the client side, this is the receive key; on the server side, it is the send key.")
)

func authenticatorResponse(password string, nt, peer, auth []byte, user string) string {
	h := sha1.New()
	h.Write(md4Sum(ntPasswordHash(password)))
	h.Write(nt)
	h.Write(authMagic1)
	d := h.Sum(nil)
	h.Reset()
	h.Write(d)
	h.Write(challengeHash(peer, auth, user))
	h.Write(authMagic2)
	return "S=" + strings.ToUpper(hex.EncodeToString(h.Sum(nil)))
}

func masterKey(password string, nt []byte) []byte {
	h := sha1.New()
	h.Write(md4Sum(ntPasswordHash(password)))
	h.Write(nt)
	h.Write(mppeMagic1)
	return h.Sum(nil)[:16]
}

func asymmetricKey(master, magic []byte) []byte {
	pad1 := make([]byte, 40)
	pad2 := make([]byte, 40)
	for i := range pad2 {
		pad2[i] = 0xf2
	}
	h := sha1.New()
	h.Write(master)
	h.Write(pad1)
	h.Write(magic)
	h.Write(pad2)
	return h.Sum(nil)[:16]
}

// clientHLAK is the SSTP higher-layer authentication key of an MS-CHAPv2
// client: MasterSendKey || MasterReceiveKey.
func clientHLAK(password string, nt []byte) []byte {
	m := masterKey(password, nt)
	return append(asymmetricKey(m, mppeMagic2), asymmetricKey(m, mppeMagic3)...)
}

// callConnected builds the SSTP Call Connected message with its crypto
// binding: nonce from the server, hash of the server certificate and the
// compound MAC keyed from HLAK.
func callConnected(hashProto byte, nonce, certDER, hlak []byte) []byte {
	var newHash func() hash.Hash
	var size int
	if hashProto == 2 {
		newHash, size = sha256.New, 32
	} else {
		newHash, size = sha1.New, 20
	}
	v := make([]byte, 100) // reserved(3) proto(1) nonce(32) cert hash(32) mac(32)
	v[3] = hashProto
	copy(v[4:36], nonce)
	ch := newHash()
	ch.Write(certDER)
	copy(v[36:68], ch.Sum(nil))
	msg := sstpControl(sstpMsgConnected, sstpAttr(sstpAttrCryptoBinding, v))
	pkt := make([]byte, 4+len(msg))
	pkt[0], pkt[1] = 0x10, 1
	binary.BigEndian.PutUint16(pkt[2:], uint16(len(pkt)))
	copy(pkt[4:], msg)

	if hlak == nil {
		hlak = make([]byte, 32)
	}
	seed := append([]byte("SSTP inner method derived CMK"), byte(size), 0, 1)
	m := hmac.New(newHash, hlak)
	m.Write(seed)
	cmk := m.Sum(nil)
	m = hmac.New(newHash, cmk)
	m.Write(pkt)
	copy(v[68:100], m.Sum(nil))
	return sstpControl(sstpMsgConnected, sstpAttr(sstpAttrCryptoBinding, v))
}

// ---- PPP negotiation ----

type pppOpt struct {
	t byte
	v []byte
}

func parseOpts(b []byte) ([]pppOpt, error) {
	var o []pppOpt
	for len(b) > 0 {
		if len(b) < 2 || int(b[1]) < 2 || int(b[1]) > len(b) {
			return nil, errors.New("ppp: bad option")
		}
		o = append(o, pppOpt{b[0], b[2:b[1]]})
		b = b[b[1]:]
	}
	return o, nil
}

func packOpts(o []pppOpt) []byte {
	var b []byte
	for _, x := range o {
		b = append(b, x.t, byte(2+len(x.v)))
		b = append(b, x.v...)
	}
	return b
}

func cp(code, id byte, data []byte) []byte {
	b := make([]byte, 4+len(data))
	b[0], b[1] = code, id
	binary.BigEndian.PutUint16(b[2:], uint16(len(b)))
	copy(b[4:], data)
	return b
}

type sstpOptions struct {
	server, sni, pin, user, pass, auth string
	insecure                           bool
	mtu                                int
	timeout                            time.Duration
	log                                func(string, ...any)
}

type sstpLink struct {
	s      *sstpConn
	local  netip.Addr
	dns    []netip.Addr
	magic  uint32
	frames chan []byte
	errc   chan error
}

// negotiate runs SSTP call setup, LCP, authentication and IPCP until the
// IPv4 link is up.
func negotiate(o sstpOptions) (*sstpLink, error) {
	s, err := dialSSTP(o.server, o.sni, o.pin, o.insecure, o.timeout)
	if err != nil {
		return nil, err
	}
	l := &sstpLink{s: s, frames: make(chan []byte, 256), errc: make(chan error, 1)}
	fail := func(e error) (*sstpLink, error) {
		s.sendControl(sstpMsgAbort)
		s.c.Close()
		return nil, e
	}
	var mb [4]byte
	rand.Read(mb[:])
	l.magic = binary.BigEndian.Uint32(mb[:])

	proto := []byte{0, 1}
	if err := s.sendControl(sstpMsgConnectRequest, sstpAttr(sstpAttrEncapsulatedProtocol, proto)); err != nil {
		return fail(err)
	}

	type inPkt struct {
		control bool
		body    []byte
	}
	in := make(chan inPkt, 64)
	go func() {
		for {
			c, b, err := s.readPacket()
			if err != nil {
				l.errc <- err
				close(in)
				return
			}
			in <- inPkt{c, b}
		}
	}()

	var (
		hashProto  byte
		nonce      []byte
		acked      bool
		lcpOurs    = []pppOpt{{lcpMRU, []byte{byte(o.mtu >> 8), byte(o.mtu)}}, {lcpMagic, mb[:]}}
		lcpOurOK   bool
		lcpPeerOK  bool
		authProto  uint16
		authOK     bool
		papSent    bool
		ipcpOurs   = []pppOpt{{ipcpAddr, make([]byte, 4)}, {ipcpDNS1, make([]byte, 4)}, {ipcpDNS2, make([]byte, 4)}}
		ipcpOurOK  bool
		ipcpPeerOK bool
		ipcpSent   bool
		id         byte = 1
		peerChal        = make([]byte, 16)
		nt         []byte
		authChal   []byte
		hlak       []byte
	)
	rand.Read(peerChal)
	nextID := func() byte { id++; return id }
	lcpID := nextID()
	sendLCPReq := func() error { return s.sendPPP(pppLCP, cp(confReq, lcpID, packOpts(lcpOurs))) }
	ipcpID := nextID()
	sendIPCPReq := func() error { return s.sendPPP(pppIPCP, cp(confReq, ipcpID, packOpts(ipcpOurs))) }
	papID := nextID()
	sendPAP := func() error {
		d := append([]byte{byte(len(o.user))}, o.user...)
		d = append(d, byte(len(o.pass)))
		d = append(d, o.pass...)
		return s.sendPPP(pppPAP, cp(1, papID, d))
	}
	wantAuth := func(p uint16, algo byte) bool {
		switch o.auth {
		case "pap":
			return p == pppPAP
		case "mschapv2":
			return p == pppCHAP && algo == chapMSv2
		}
		return p == pppPAP || (p == pppCHAP && algo == chapMSv2)
	}
	preferred := func() []byte {
		if o.auth == "pap" {
			return []byte{0xc0, 0x23}
		}
		return []byte{0xc2, 0x23, chapMSv2}
	}

	deadline := time.After(o.timeout)
	tick := time.NewTicker(3 * time.Second)
	defer tick.Stop()
	for {
		// Phase transitions.
		if acked && lcpOurOK && lcpPeerOK && authProto == pppPAP && !papSent {
			papSent = true
			o.log("lcp up; pap")
			if err := sendPAP(); err != nil {
				return fail(err)
			}
		}
		if authOK && !ipcpSent {
			ipcpSent = true
			if err := s.writePacket(true, callConnected(hashProto, nonce, s.certDER, hlak)); err != nil {
				return fail(err)
			}
			if err := sendIPCPReq(); err != nil {
				return fail(err)
			}
		}
		if ipcpOurOK && ipcpPeerOK {
			for _, x := range ipcpOurs {
				a, _ := netip.AddrFromSlice(x.v)
				switch x.t {
				case ipcpAddr:
					l.local = a
				case ipcpDNS1, ipcpDNS2:
					if !a.IsUnspecified() {
						l.dns = append(l.dns, a)
					}
				}
			}
			if !l.local.IsValid() || l.local.IsUnspecified() {
				return fail(errors.New("ipcp: server gave no address"))
			}
			go func() {
				for p := range in {
					if p.control {
						l.control(p.body)
						continue
					}
					l.frames <- p.body
				}
			}()
			return l, nil
		}

		var p inPkt
		var ok bool
		select {
		case p, ok = <-in:
			if !ok {
				return fail(fmt.Errorf("connection closed during setup: %v", <-l.errc))
			}
		case <-tick.C:
			if !acked {
				continue
			}
			if !lcpOurOK {
				sendLCPReq()
			} else if authProto == pppPAP && !authOK {
				sendPAP()
			} else if ipcpSent && !ipcpOurOK {
				sendIPCPReq()
			}
			continue
		case <-deadline:
			stage := "sstp call"
			switch {
			case acked && !(lcpOurOK && lcpPeerOK):
				stage = "lcp"
			case lcpOurOK && lcpPeerOK && !authOK:
				stage = "authentication"
			case authOK:
				stage = "ipcp"
			}
			return fail(fmt.Errorf("timeout during %s", stage))
		}

		if p.control {
			if len(p.body) < 4 {
				return fail(errors.New("sstp: short control"))
			}
			switch binary.BigEndian.Uint16(p.body) {
			case sstpMsgConnectAck:
				attrs := p.body[4:]
				if len(attrs) < 40 || attrs[1] != sstpAttrCryptoBindingReq {
					return fail(errors.New("sstp: ack without crypto binding request"))
				}
				mask := attrs[7]
				if mask&2 != 0 {
					hashProto = 2
				} else if mask&1 != 0 {
					hashProto = 1
				} else {
					return fail(fmt.Errorf("sstp: unsupported hash bitmask %d", mask))
				}
				nonce = append([]byte(nil), attrs[8:40]...)
				acked = true
				o.log("sstp call accepted")
				if err := sendLCPReq(); err != nil {
					return fail(err)
				}
			case sstpMsgConnectNak:
				return fail(errors.New("sstp: server refused the call (NAK)"))
			case sstpMsgAbort, sstpMsgDisconnect:
				return fail(errors.New("sstp: server aborted the call"))
			case sstpMsgEchoRequest:
				s.sendControl(sstpMsgEchoResponse)
			}
			continue
		}

		pr, pl, err := splitPPP(p.body)
		if err != nil || len(pl) < 4 {
			continue
		}
		code, pid := pl[0], pl[1]
		n := int(binary.BigEndian.Uint16(pl[2:]))
		if n < 4 || n > len(pl) {
			continue
		}
		data := pl[4:n]
		switch pr {
		case pppLCP:
			switch code {
			case confReq:
				opts, err := parseOpts(data)
				if err != nil {
					continue
				}
				var rej, nak []pppOpt
				var auth uint16
				for _, x := range opts {
					switch x.t {
					case lcpMRU, lcpACCM, lcpMagic:
					case lcpAuth:
						if len(x.v) < 2 {
							rej = append(rej, x)
							continue
						}
						pp := binary.BigEndian.Uint16(x.v)
						var algo byte
						if len(x.v) > 2 {
							algo = x.v[2]
						}
						if wantAuth(pp, algo) {
							auth = pp
						} else {
							nak = append(nak, pppOpt{lcpAuth, preferred()})
						}
					default:
						rej = append(rej, x)
					}
				}
				switch {
				case len(rej) > 0:
					s.sendPPP(pppLCP, cp(confRej, pid, packOpts(rej)))
				case len(nak) > 0:
					s.sendPPP(pppLCP, cp(confNak, pid, packOpts(nak)))
				default:
					authProto = auth
					if auth == 0 {
						authOK = true // server asks no authentication
					}
					lcpPeerOK = true
					s.sendPPP(pppLCP, cp(confAck, pid, data))
				}
			case confAck:
				if pid == lcpID {
					lcpOurOK = true
				}
			case confNak, confRej:
				if pid != lcpID {
					continue
				}
				opts, _ := parseOpts(data)
				for _, x := range opts {
					for i := range lcpOurs {
						if lcpOurs[i].t == x.t {
							if code == confRej {
								lcpOurs = append(lcpOurs[:i], lcpOurs[i+1:]...)
							} else {
								lcpOurs[i].v = x.v
							}
							break
						}
					}
				}
				lcpID = nextID()
				sendLCPReq()
			case echoReq:
				s.sendPPP(pppLCP, cp(echoReply, pid, mb[:]))
			case termReq:
				s.sendPPP(pppLCP, cp(termAck, pid, nil))
				return fail(errors.New("lcp: server terminated the link"))
			}
		case pppPAP:
			if pid != papID {
				continue
			}
			switch code {
			case 2:
				authOK = true
				o.log("pap: accepted")
			case 3:
				return fail(fmt.Errorf("authentication failed (PAP): %s", string(data[min(1, len(data)):])))
			}
		case pppCHAP:
			switch code {
			case 1: // challenge
				if len(data) < 17 || data[0] != 16 {
					return fail(errors.New("chap: bad challenge"))
				}
				authProto = pppCHAP
				authChal = append([]byte(nil), data[1:17]...)
				nt = ntResponse(authChal, peerChal, o.user, o.pass)
				v := make([]byte, 49)
				copy(v, peerChal)
				copy(v[24:], nt)
				d := append([]byte{49}, v...)
				d = append(d, o.user...)
				s.sendPPP(pppCHAP, cp(2, pid, d))
				o.log("lcp up; ms-chapv2")
			case 3: // success
				want := authenticatorResponse(o.pass, nt, peerChal, authChal, o.user)
				if !strings.HasPrefix(strings.ToUpper(string(data)), want) {
					return fail(errors.New("chap: server did not prove it knows the password (bad authenticator response)"))
				}
				hlak = clientHLAK(o.pass, nt)
				authOK = true
				o.log("ms-chapv2: accepted")
			case 4:
				return fail(fmt.Errorf("authentication failed (MS-CHAPv2): %s", string(data)))
			}
		case pppIPCP:
			switch code {
			case confReq:
				opts, err := parseOpts(data)
				if err != nil {
					continue
				}
				var rej []pppOpt
				for _, x := range opts {
					if x.t != ipcpAddr {
						rej = append(rej, x)
					}
				}
				if len(rej) > 0 {
					s.sendPPP(pppIPCP, cp(confRej, pid, packOpts(rej)))
				} else {
					ipcpPeerOK = true
					s.sendPPP(pppIPCP, cp(confAck, pid, data))
				}
			case confAck:
				if pid == ipcpID {
					ipcpOurOK = true
				}
			case confNak, confRej:
				if pid != ipcpID {
					continue
				}
				opts, _ := parseOpts(data)
				for _, x := range opts {
					for i := range ipcpOurs {
						if ipcpOurs[i].t == x.t {
							if code == confRej {
								if x.t == ipcpAddr {
									return fail(errors.New("ipcp: server rejected address negotiation"))
								}
								ipcpOurs = append(ipcpOurs[:i], ipcpOurs[i+1:]...)
							} else if len(x.v) == 4 {
								ipcpOurs[i].v = x.v
							}
							break
						}
					}
				}
				ipcpID = nextID()
				sendIPCPReq()
			}
		default:
			// Protocols we do not run (IPv6CP, CCP, …): Protocol-Reject.
			rej := make([]byte, 2+len(pl))
			binary.BigEndian.PutUint16(rej, pr)
			copy(rej[2:], pl)
			s.sendPPP(pppLCP, cp(protoRej, nextID(), rej))
		}
	}
}

// control answers SSTP control messages once the link is up.
func (l *sstpLink) control(b []byte) {
	if len(b) < 2 {
		return
	}
	switch binary.BigEndian.Uint16(b) {
	case sstpMsgEchoRequest:
		l.s.sendControl(sstpMsgEchoResponse)
	case sstpMsgDisconnect:
		l.s.sendControl(sstpMsgDisconnectAck)
		l.s.c.Close()
	case sstpMsgAbort:
		l.s.c.Close()
	}
}

// pump moves IPv4 packets between the netstack and the PPP link and keeps
// the call alive with SSTP echoes; it returns when the link dies.
func (l *sstpLink) pump(dev tun.Device, mtu int) error {
	errc := make(chan error, 2)
	var lastRx sync.Map
	lastRx.Store(0, time.Now())
	go func() {
		for f := range l.frames {
			lastRx.Store(0, time.Now())
			pr, pl, err := splitPPP(f)
			if err != nil {
				continue
			}
			switch pr {
			case pppIPv4:
				buf := make([]byte, 16+len(pl))
				copy(buf[16:], pl)
				dev.Write([][]byte{buf}, 16)
			case pppLCP:
				if len(pl) >= 4 && pl[0] == echoReq {
					var m [4]byte
					binary.BigEndian.PutUint32(m[:], l.magic)
					l.s.sendPPP(pppLCP, cp(echoReply, pl[1], m[:]))
				} else if len(pl) >= 4 && pl[0] == termReq {
					l.s.sendPPP(pppLCP, cp(termAck, pl[1], nil))
					errc <- errors.New("lcp: server terminated the link")
					return
				}
			}
		}
		errc <- fmt.Errorf("link closed: %v", <-l.errc)
	}()
	go func() {
		bufs := [][]byte{make([]byte, 16+mtu+64)}
		sizes := []int{0}
		for {
			n, err := dev.Read(bufs, sizes, 16)
			if err != nil {
				errc <- err
				return
			}
			for i := 0; i < n; i++ {
				if err := l.s.sendPPP(pppIPv4, bufs[i][16:16+sizes[i]]); err != nil {
					errc <- err
					return
				}
			}
		}
	}()
	t := time.NewTicker(30 * time.Second)
	defer t.Stop()
	for {
		select {
		case err := <-errc:
			return err
		case <-t.C:
			v, _ := lastRx.Load(0)
			if time.Since(v.(time.Time)) > 95*time.Second {
				return errors.New("sstp: no answer from the server for 95s")
			}
			l.s.sendControl(sstpMsgEchoRequest)
		}
	}
}

func runSSTP(args []string) error {
	fs := flag.NewFlagSet("sstp", flag.ContinueOnError)
	listen := fs.Int("listen", 0, "local SOCKS5 port on 127.0.0.1")
	server := fs.String("server", "", "host:port of the SSTP server")
	sni := fs.String("sni", "", "TLS server name (default: server host)")
	user := fs.String("user", "", "user name")
	auth := fs.String("auth", "auto", "auto | pap | mschapv2")
	insecure := fs.Bool("insecure", false, "skip certificate verification")
	pin := fs.String("pin", "", "hex SHA-256 of the server certificate (replaces CA checks)")
	mtu := fs.Int("mtu", 1400, "link MTU")
	dns := fs.String("dns", "", "DNS servers when the server gives none (comma list)")
	verbose := fs.Bool("v", false, "log negotiation")
	if err := fs.Parse(args); err != nil {
		return err
	}
	pass := os.Getenv("SSTP_PASSWORD")
	if pf := os.Getenv("SSTP_PASSWORD_FILE"); pf != "" {
		b, err := os.ReadFile(pf)
		if err != nil {
			return err
		}
		pass = strings.TrimRight(string(b), "\r\n")
	}
	if *server == "" || *user == "" {
		return errors.New("sstp: -server and -user are required")
	}
	if _, _, err := net.SplitHostPort(*server); err != nil {
		*server = net.JoinHostPort(*server, "443")
	}
	logf := func(f string, a ...any) {
		if *verbose {
			fmt.Fprintf(os.Stderr, "sstp: "+f+"\n", a...)
		}
	}
	link, err := negotiate(sstpOptions{server: *server, sni: *sni, pin: *pin, user: *user, pass: pass, auth: *auth,
		insecure: *insecure, mtu: *mtu, timeout: 25 * time.Second, log: logf})
	if err != nil {
		return err
	}
	if len(link.dns) == 0 {
		for _, d := range strings.Split(*dns, ",") {
			if a, err := netip.ParseAddr(strings.TrimSpace(d)); err == nil {
				link.dns = append(link.dns, a)
			}
		}
	}
	if len(link.dns) == 0 {
		link.dns = []netip.Addr{netip.MustParseAddr("1.1.1.1")}
	}
	fmt.Fprintf(os.Stderr, "sstp: link up, address %s, dns %v\n", link.local, link.dns)
	dev, tnet, err := netstack.CreateNetTUN([]netip.Addr{link.local}, link.dns, *mtu)
	if err != nil {
		return fmt.Errorf("netstack: %w", err)
	}
	ln, err := listenLocal(*listen)
	if err != nil {
		return err
	}
	go func() {
		serveSocks5(ln, netstackBackend{tnet})
	}()
	err = link.pump(dev, *mtu)
	link.s.sendControl(sstpMsgDisconnect)
	link.s.c.Close()
	return err
}
