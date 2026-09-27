package main

import (
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
