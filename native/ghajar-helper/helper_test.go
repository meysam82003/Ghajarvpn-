package main

import (
	"crypto/sha256"
	"encoding/hex"
	"strings"
	"testing"
)

func TestPayloadExpandAndSplit(t *testing.T) {
	tr := &sshTransport{host: "ssh.example.com", port: 22, ua: "UA"}
	got := tr.expand("CONNECT [host_port] [protocol][crlf]Host: [host][crlf]User-Agent: [ua][crlf][crlf]")
	want := "CONNECT ssh.example.com:22 HTTP/1.1\r\nHost: ssh.example.com\r\nUser-Agent: UA\r\n\r\n"
	if got != want {
		t.Fatalf("expand: %q", got)
	}
	parts := splitPayload("GET / HTTP/1.1\r\n\r\n[split]CONNECT x[delay_split]tail")
	if len(parts) != 3 || parts[1].text != "CONNECT x" || parts[1].delay || !parts[2].delay || parts[2].text != "tail" {
		t.Fatalf("split: %+v", parts)
	}
}

func TestAWGConfigToUAPI(t *testing.T) {
	conf := `[Interface]
PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=
Address = 10.8.0.2/32, fd00::2/128
DNS = 1.1.1.1
Jc = 4
Jmin = 40
H1 = 12345

[Peer]
PublicKey = xTIBA5rboUvnH4htodjb6e697QjLERt1NAB4mZqp8Dg=
Endpoint = 127.0.0.1:51820
AllowedIPs = 0.0.0.0/0
PersistentKeepalive = 25
`
	c, err := parseAWGConfig(conf)
	if err != nil {
		t.Fatal(err)
	}
	if len(c.addresses) != 2 || c.obfs["jc"] != "4" || c.obfs["h1"] != "12345" {
		t.Fatalf("parsed: %+v", c)
	}
	u, err := c.uapi()
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{"private_key=c809f3e5317e9575c9b5ed78b638b7ce530dabe85ddab614220241801ddf0669", "jc=4\n", "h1=12345\n",
		"endpoint=127.0.0.1:51820", "allowed_ip=0.0.0.0/0", "persistent_keepalive_interval=25"} {
		if !strings.Contains(u, want) {
			t.Fatalf("uapi missing %q:\n%s", want, u)
		}
	}
	if _, err := parseAWGConfig("[Interface]\nAddress = 10.0.0.2/32\n"); err == nil {
		t.Fatal("incomplete config accepted")
	}
}

func TestSocksUDPHeaderRoundTrip(t *testing.T) {
	h := socksUDPHeader(&fakeAddr{"203.0.113.5:53"})
	host, port, off, err := parseUDPAddr(append(h, 'x'))
	if err != nil || host != "203.0.113.5" || port != 53 || off != len(h) {
		t.Fatalf("%v %v %v %v", host, port, off, err)
	}
}

type fakeAddr struct{ s string }

func (f *fakeAddr) Network() string { return "udp" }
func (f *fakeAddr) String() string  { return f.s }

// RFC 2759 section 9.2 and RFC 3079 section 3.5.3 test vectors.
func TestMSCHAPv2Vectors(t *testing.T) {
	unhex := func(s string) []byte { b, _ := hex.DecodeString(s); return b }
	auth := unhex("5B5D7C7D7B3F2F3E3C2C602132262628")
	peer := unhex("21402324255E262A28295F2B3A337C7E")
	nt := ntResponse(auth, peer, "User", "clientPass")
	if got := hex.EncodeToString(nt); got != strings.ToLower("82309ECD8D708B5EA08FAA3981CD83544233114A3D85D6DF") {
		t.Fatalf("nt-response %s", got)
	}
	if got := authenticatorResponse("clientPass", nt, peer, auth, "User"); got != "S=407A5589115FD0D6209F510FE9C04566932CDA56" {
		t.Fatalf("authenticator %s", got)
	}
	if got := hex.EncodeToString(masterKey("clientPass", nt)); got != strings.ToLower("FDECE3717A8C838CB388E527AE3CDD31") {
		t.Fatalf("master key %s", got)
	}
	// RFC 3079's sample is the server's send key, which is the client's
	// receive key: the second half of the client HLAK.
	if got := hex.EncodeToString(clientHLAK("clientPass", nt)[16:]); got != strings.ToLower("8B7CDC149B993A1BA118CB153F56DCCB") {
		t.Fatalf("send key %s", got)
	}
}

func TestSSTPCallConnectedLayout(t *testing.T) {
	nonce := make([]byte, 32)
	for i := range nonce {
		nonce[i] = byte(i)
	}
	m := callConnected(2, nonce, []byte("cert"), nil)
	if len(m) != 4+104 || m[1] != sstpMsgConnected || m[3] != 1 || m[5] != sstpAttrCryptoBinding || m[7] != 104 {
		t.Fatalf("layout % x", m[:8])
	}
	if m[11] != 2 || m[12] != 0 || m[43] != 31 {
		t.Fatal("hash protocol / nonce misplaced")
	}
	sum := sha256.Sum256([]byte("cert"))
	if string(m[44:76]) != string(sum[:]) {
		t.Fatal("certificate hash misplaced")
	}
	// The MAC must be deterministic and change with the key.
	if string(m[76:]) != string(callConnected(2, nonce, []byte("cert"), nil)[76:]) || string(m[76:]) == string(callConnected(2, nonce, []byte("cert"), []byte("another key"))[76:]) {
		t.Fatal("compound MAC is not keyed by HLAK")
	}
	proto, payload, err := splitPPP([]byte{0xff, 0x03, 0x00, 0x21, 0x45})
	if err != nil || proto != pppIPv4 || len(payload) != 1 {
		t.Fatalf("splitPPP %x %v %v", proto, payload, err)
	}
}
