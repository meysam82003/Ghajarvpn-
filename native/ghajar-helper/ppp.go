package main

// PPP client (RFC 1661 LCP, RFC 1334 PAP, RFC 2759 MS-CHAPv2, RFC 1332 +
// RFC 1877 IPCP) shared by the SSTP and L2TP modes. The transport only has
// to deliver PPP frames (with or without the FF 03 header) on a channel and
// send them with sendPPP.

import (
	"crypto/rand"
	"encoding/binary"
	"errors"
	"fmt"
	"net/netip"
	"os"
	"strings"
	"sync"
	"time"

	"github.com/amnezia-vpn/amneziawg-go/v3/tun"
	"github.com/amnezia-vpn/amneziawg-go/v3/tun/netstack"
)

const (
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

type pppConfig struct {
	user, pass, auth string // auth: auto | pap | mschapv2
	mtu              int
	timeout          time.Duration
	log              func(string, ...any)
	// onAuth runs once authentication succeeded (or none was asked), before
	// IPCP; hlak is the MS-CHAPv2 key material (nil for PAP / none).
	onAuth func(hlak []byte) error
}

type pppLink struct {
	local netip.Addr
	dns   []netip.Addr
	magic uint32
}

// pppNegotiate runs LCP, authentication and IPCP over (send, in) until the
// IPv4 link is up. in is closed by the transport when it dies.
func pppNegotiate(send func(proto uint16, payload []byte) error, in <-chan []byte, o pppConfig) (*pppLink, error) {
	var mb [4]byte
	rand.Read(mb[:])
	l := &pppLink{magic: binary.BigEndian.Uint32(mb[:])}
	mru := o.mtu
	if mru <= 0 {
		mru = 1400
	}
	var (
		lcpOurs    = []pppOpt{{lcpMRU, []byte{byte(mru >> 8), byte(mru)}}, {lcpMagic, mb[:]}}
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
	sendLCPReq := func() error { return send(pppLCP, cp(confReq, lcpID, packOpts(lcpOurs))) }
	ipcpID := nextID()
	sendIPCPReq := func() error { return send(pppIPCP, cp(confReq, ipcpID, packOpts(ipcpOurs))) }
	papID := nextID()
	sendPAP := func() error {
		d := append([]byte{byte(len(o.user))}, o.user...)
		d = append(d, byte(len(o.pass)))
		d = append(d, o.pass...)
		return send(pppPAP, cp(1, papID, d))
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
	if err := sendLCPReq(); err != nil {
		return nil, err
	}

	deadline := time.After(o.timeout)
	tick := time.NewTicker(3 * time.Second)
	defer tick.Stop()
	for {
		if lcpOurOK && lcpPeerOK && authProto == pppPAP && !papSent {
			papSent = true
			o.log("lcp up; pap")
			if err := sendPAP(); err != nil {
				return nil, err
			}
		}
		if authOK && !ipcpSent {
			ipcpSent = true
			if o.onAuth != nil {
				if err := o.onAuth(hlak); err != nil {
					return nil, err
				}
			}
			if err := sendIPCPReq(); err != nil {
				return nil, err
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
				return nil, errors.New("ipcp: server gave no address")
			}
			return l, nil
		}

		var frame []byte
		select {
		case f, ok := <-in:
			if !ok {
				return nil, errors.New("connection closed during PPP setup")
			}
			frame = f
		case <-tick.C:
			if !lcpOurOK {
				sendLCPReq()
			} else if authProto == pppPAP && !authOK {
				sendPAP()
			} else if ipcpSent && !ipcpOurOK {
				sendIPCPReq()
			}
			continue
		case <-deadline:
			stage := "lcp"
			switch {
			case lcpOurOK && lcpPeerOK && !authOK:
				stage = "authentication"
			case authOK:
				stage = "ipcp"
			}
			return nil, fmt.Errorf("timeout during %s", stage)
		}

		pr, pl, err := splitPPP(frame)
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
					send(pppLCP, cp(confRej, pid, packOpts(rej)))
				case len(nak) > 0:
					send(pppLCP, cp(confNak, pid, packOpts(nak)))
				default:
					authProto = auth
					if auth == 0 {
						authOK = true // the server asks no authentication
					}
					lcpPeerOK = true
					send(pppLCP, cp(confAck, pid, data))
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
				send(pppLCP, cp(echoReply, pid, mb[:]))
			case termReq:
				send(pppLCP, cp(termAck, pid, nil))
				return nil, errors.New("lcp: server terminated the link")
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
				return nil, fmt.Errorf("authentication failed (PAP): %s", string(data[min(1, len(data)):]))
			}
		case pppCHAP:
			switch code {
			case 1: // challenge
				if len(data) < 17 || data[0] != 16 {
					return nil, errors.New("chap: bad challenge")
				}
				authProto = pppCHAP
				authChal = append([]byte(nil), data[1:17]...)
				nt = ntResponse(authChal, peerChal, o.user, o.pass)
				v := make([]byte, 49)
				copy(v, peerChal)
				copy(v[24:], nt)
				d := append([]byte{49}, v...)
				d = append(d, o.user...)
				send(pppCHAP, cp(2, pid, d))
				o.log("lcp up; ms-chapv2")
			case 3: // success
				want := authenticatorResponse(o.pass, nt, peerChal, authChal, o.user)
				if !strings.HasPrefix(strings.ToUpper(string(data)), want) {
					return nil, errors.New("chap: server did not prove it knows the password (bad authenticator response)")
				}
				hlak = clientHLAK(o.pass, nt)
				authOK = true
				o.log("ms-chapv2: accepted")
			case 4:
				return nil, fmt.Errorf("authentication failed (MS-CHAPv2): %s", string(data))
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
					send(pppIPCP, cp(confRej, pid, packOpts(rej)))
				} else {
					ipcpPeerOK = true
					send(pppIPCP, cp(confAck, pid, data))
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
									return nil, errors.New("ipcp: server rejected address negotiation")
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
			send(pppLCP, cp(protoRej, nextID(), rej))
		}
	}
}

// pppPump moves IPv4 between the netstack device and the PPP link until one
// side fails. alive is called for every frame received, so the transport's
// own keep-alive logic can watch for silence.
func pppPump(send func(proto uint16, payload []byte) error, in <-chan []byte, dev tun.Device, mtu int, magic uint32, alive func()) error {
	errc := make(chan error, 2)
	go func() {
		for f := range in {
			alive()
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
					binary.BigEndian.PutUint32(m[:], magic)
					send(pppLCP, cp(echoReply, pl[1], m[:]))
				} else if len(pl) >= 4 && pl[0] == termReq {
					send(pppLCP, cp(termAck, pl[1], nil))
					errc <- errors.New("lcp: server terminated the link")
					return
				}
			}
		}
		errc <- errors.New("link closed")
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
				if err := send(pppIPv4, bufs[i][16:16+sizes[i]]); err != nil {
					errc <- err
					return
				}
			}
		}
	}()
	return <-errc
}

// secretFromEnv reads NAME, or the file named by NAME_FILE.
func secretFromEnv(name string) (string, error) {
	if pf := os.Getenv(name + "_FILE"); pf != "" {
		b, err := os.ReadFile(pf)
		if err != nil {
			return "", err
		}
		return strings.TrimRight(string(b), "\r\n"), nil
	}
	return os.Getenv(name), nil
}

// servePPPLink brings the negotiated IPv4 link up on a userspace netstack,
// serves it as SOCKS5 and pumps packets until the link dies. keepalive runs
// the transport's liveness check with the time of the last received frame.
func servePPPLink(tag string, link *pppLink, dnsFlag string, mtu, listen int, send func(uint16, []byte) error,
	frames <-chan []byte, keepalive func(last func() time.Time) error, bye func()) error {
	if len(link.dns) == 0 {
		for _, d := range strings.Split(dnsFlag, ",") {
			if a, err := netip.ParseAddr(strings.TrimSpace(d)); err == nil {
				link.dns = append(link.dns, a)
			}
		}
	}
	if len(link.dns) == 0 {
		link.dns = []netip.Addr{netip.MustParseAddr("1.1.1.1")}
	}
	fmt.Fprintf(os.Stderr, "%s: link up, address %s, dns %v\n", tag, link.local, link.dns)
	dev, tnet, err := netstack.CreateNetTUN([]netip.Addr{link.local}, link.dns, mtu)
	if err != nil {
		return fmt.Errorf("netstack: %w", err)
	}
	ln, err := listenLocal(listen)
	if err != nil {
		return err
	}
	go serveSocks5(ln, netstackBackend{tnet})
	var mu sync.Mutex
	last := time.Now()
	lastFn := func() time.Time { mu.Lock(); defer mu.Unlock(); return last }
	errc := make(chan error, 2)
	go func() {
		errc <- pppPump(send, frames, dev, mtu, link.magic, func() { mu.Lock(); last = time.Now(); mu.Unlock() })
	}()
	go func() { errc <- keepalive(lastFn) }()
	err = <-errc
	bye()
	return err
}
