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
	"context"
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
	"os"
	"strings"
	"sync"
	"time"
	"unicode/utf16"

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

// dialSSTP opens TLS, performs the HTTP handshake and returns the SSTP layer.
// A non-empty pin (hex SHA-256 of the server's leaf certificate) replaces
// CA verification, for servers with a self-signed certificate.
func dialSSTP(server, sni, pin string, insecure bool, timeout time.Duration) (*sstpConn, error) {
	return dialSSTPWithProxy(server, sni, pin, insecure, timeout, "", tls.VersionTLS12)
}
func dialSSTPWithProxy(server, sni, pin string, insecure bool, timeout time.Duration, proxyURL string, minimumTLS uint16) (*sstpConn, error) {
	host, _, err := net.SplitHostPort(server)
	if err != nil {
		return nil, err
	}
	if sni == "" {
		sni = host
	}
	cfg := &tls.Config{ServerName: sni, InsecureSkipVerify: insecure, MinVersion: minimumTLS}
	if pin = strings.ToLower(strings.ReplaceAll(pin, ":", "")); pin != "" {
		cfg.InsecureSkipVerify = true
		cfg.VerifyPeerCertificate = func(raw [][]byte, _ [][]*x509.Certificate) error {
			if len(raw) > 0 {
				if sum := sha256.Sum256(raw[0]); hex.EncodeToString(sum[:]) == pin {
					cert, err := x509.ParseCertificate(raw[0])
					if err != nil {
						return err
					}
					if !insecure {
						return cert.VerifyHostname(sni)
					}
					return nil
				}
			}
			return errors.New("server certificate does not match the pinned SHA-256")
		}
	}
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	raw, err := dialSSTPTransport(ctx, server, proxyURL, timeout)
	if err != nil {
		return nil, err
	}
	tc := tls.Client(raw, cfg)
	if err := tc.HandshakeContext(ctx); err != nil {
		raw.Close()
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

// ---- SSTP call ----

type sstpOptions struct {
	server, sni, pin, user, pass, auth, proxyURL string
	minimumTLS                                   uint16
	insecure                                     bool
	mtu                                          int
	timeout                                      time.Duration
	log                                          func(string, ...any)
}

// sstpCall opens the SSTP call and returns the PPP frame channel; control
// messages after the call is accepted are answered here.
func sstpCall(o sstpOptions) (*sstpConn, byte, []byte, chan []byte, chan error, error) {
	minimum := o.minimumTLS
	if minimum == 0 {
		minimum = tls.VersionTLS12
	}
	s, err := dialSSTPWithProxy(o.server, o.sni, o.pin, o.insecure, o.timeout, o.proxyURL, minimum)
	if err != nil {
		return nil, 0, nil, nil, nil, err
	}
	fail := func(e error) (*sstpConn, byte, []byte, chan []byte, chan error, error) {
		s.sendControl(sstpMsgAbort)
		s.c.Close()
		return nil, 0, nil, nil, nil, e
	}
	if err := s.sendControl(sstpMsgConnectRequest, sstpAttr(sstpAttrEncapsulatedProtocol, []byte{0, 1})); err != nil {
		return fail(err)
	}
	s.c.SetReadDeadline(time.Now().Add(o.timeout))
	var hashProto byte
	var nonce []byte
	for nonce == nil {
		control, body, err := s.readPacket()
		if err != nil {
			return fail(fmt.Errorf("sstp call: %w", err))
		}
		if !control || len(body) < 4 {
			continue
		}
		switch binary.BigEndian.Uint16(body) {
		case sstpMsgConnectAck:
			attrs := body[4:]
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
		case sstpMsgConnectNak:
			return fail(errors.New("sstp: server refused the call (NAK)"))
		case sstpMsgAbort, sstpMsgDisconnect:
			return fail(errors.New("sstp: server aborted the call"))
		case sstpMsgEchoRequest:
			s.sendControl(sstpMsgEchoResponse)
		}
	}
	s.c.SetReadDeadline(time.Time{})
	o.log("sstp call accepted")
	frames := make(chan []byte, 256)
	errc := make(chan error, 1)
	go func() {
		defer close(frames)
		for {
			control, body, err := s.readPacket()
			if err != nil {
				errc <- err
				return
			}
			if !control {
				frames <- body
				continue
			}
			if len(body) < 2 {
				continue
			}
			switch binary.BigEndian.Uint16(body) {
			case sstpMsgEchoRequest:
				s.sendControl(sstpMsgEchoResponse)
			case sstpMsgDisconnect:
				s.sendControl(sstpMsgDisconnectAck)
				s.c.Close()
			case sstpMsgAbort:
				s.c.Close()
			}
		}
	}()
	return s, hashProto, nonce, frames, errc, nil
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
	tlsMin := fs.String("tls-min", "1.2", "minimum TLS: 1.2 | 1.3")
	dns := fs.String("dns", "", "DNS servers when the server gives none (comma list)")
	verbose := fs.Bool("v", false, "log negotiation")
	if err := fs.Parse(args); err != nil {
		return err
	}
	minimumTLS := uint16(tls.VersionTLS12)
	if *tlsMin == "1.3" {
		minimumTLS = tls.VersionTLS13
	} else if *tlsMin != "1.2" {
		return errors.New("invalid minimum TLS version")
	}
	for _, addr := range strings.Split(*dns, ",") {
		if addr != "" && net.ParseIP(strings.TrimSpace(addr)) == nil {
			return errors.New("DNS must be an IP address")
		}
	}
	proxyURL, err := secretFromEnv("SSTP_PROXY")
	if err != nil {
		return err
	}
	pass, err := secretFromEnv("SSTP_PASSWORD")
	if err != nil {
		return err
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
	o := sstpOptions{server: *server, sni: *sni, pin: *pin, user: *user, pass: pass, auth: *auth,
		insecure: *insecure, mtu: *mtu, proxyURL: proxyURL, minimumTLS: minimumTLS, timeout: 25 * time.Second, log: logf}
	s, hashProto, nonce, frames, errc, err := sstpCall(o)
	if err != nil {
		return err
	}
	defer s.c.Close()
	send := func(proto uint16, payload []byte) error { return s.sendPPP(proto, payload) }
	link, err := pppNegotiate(send, frames, pppConfig{user: *user, pass: pass, auth: *auth, mtu: *mtu, timeout: o.timeout, log: logf,
		onAuth: func(hlak []byte) error { return s.writePacket(true, callConnected(hashProto, nonce, s.certDER, hlak)) }})
	if err != nil {
		s.sendControl(sstpMsgAbort)
		select {
		case e := <-errc:
			return fmt.Errorf("%v (%v)", err, e)
		default:
		}
		return err
	}
	return servePPPLink("sstp", link, *dns, *mtu, *listen, send, frames, func(alive func() time.Time) error {
		t := time.NewTicker(30 * time.Second)
		defer t.Stop()
		for range t.C {
			if time.Since(alive()) > 95*time.Second {
				return errors.New("sstp: no answer from the server for 95s")
			}
			s.sendControl(sstpMsgEchoRequest)
		}
		return nil
	}, func() { s.sendControl(sstpMsgDisconnect) })
}
