package sharing_test

import (
	"context"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"github.com/miekg/dns"
	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing-tun"
	J "github.com/sagernet/sing/common/json"
	"github.com/sagernet/sing/common/logger"
	"github.com/sagernet/sing/service"
	"golang.org/x/net/proxy"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"sync/atomic"
	"testing"
	"time"
)

func TestPhoneShareResolvesAndTransfersOnlyThroughProxy(t *testing.T) {
	var dnsCount, tcpCount, served atomic.Int32
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { served.Add(1); io.WriteString(w, "share-through-tunnel") }))
	defer target.Close()
	upstream, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer upstream.Close()
	go func() {
		for {
			c, e := upstream.Accept()
			if e != nil {
				return
			}
			go func() {
				defer c.Close()
				c.SetDeadline(time.Now().Add(5 * time.Second))
				h := make([]byte, 2)
				if _, e := io.ReadFull(c, h); e != nil {
					return
				}
				methods := make([]byte, int(h[1]))
				io.ReadFull(c, methods)
				c.Write([]byte{5, 0})
				head := make([]byte, 4)
				if _, e := io.ReadFull(c, head); e != nil {
					return
				}
				var host string
				switch head[3] {
				case 1:
					a := make([]byte, 4)
					io.ReadFull(c, a)
					host = net.IP(a).String()
				case 3:
					l := make([]byte, 1)
					io.ReadFull(c, l)
					a := make([]byte, int(l[0]))
					io.ReadFull(c, a)
					host = string(a)
				default:
					return
				}
				port := make([]byte, 2)
				io.ReadFull(c, port)
				p := binary.BigEndian.Uint16(port)
				c.Write([]byte{5, 0, 0, 1, 0, 0, 0, 0, 0, 0})
				if host == "1.1.1.1" && p == 53 {
					dnsCount.Add(1)
					for {
						l := make([]byte, 2)
						if _, e := io.ReadFull(c, l); e != nil {
							return
						}
						raw := make([]byte, binary.BigEndian.Uint16(l))
						if _, e := io.ReadFull(c, raw); e != nil {
							return
						}
						q := new(dns.Msg)
						if q.Unpack(raw) != nil {
							return
						}
						reply := new(dns.Msg)
						reply.SetReply(q)
						for _, question := range q.Question {
							if question.Name != "only-via-tunnel.invalid." {
								return
							}
							if question.Qtype == dns.TypeA {
								reply.Answer = append(reply.Answer, &dns.A{Hdr: dns.RR_Header{Name: question.Name, Rrtype: dns.TypeA, Class: dns.ClassINET, Ttl: 60}, A: net.IPv4(127, 0, 0, 1)})
							}
						}
						data, _ := reply.Pack()
						binary.BigEndian.PutUint16(l, uint16(len(data)))
						c.Write(l)
						c.Write(data)
					}
				}
				if host != "127.0.0.1" {
					return
				}
				tcpCount.Add(1)
				out, e := net.DialTimeout("tcp", fmt.Sprintf("%s:%d", host, p), time.Second)
				if e != nil {
					return
				}
				defer out.Close()
				go io.Copy(out, c)
				io.Copy(c, out)
			}()
		}
	}()
	raw, err := os.ReadFile(os.Getenv("GHAJAR_SHARING_CORPUS"))
	if err != nil {
		t.Fatal(err)
	}
	var tree map[string]any
	json.Unmarshal(raw, &tree)
	tree["outbounds"] = []any{map[string]any{"type": "socks", "tag": "proxy", "server": "127.0.0.1", "server_port": upstream.Addr().(*net.TCPAddr).Port}, map[string]any{"type": "direct", "tag": "direct"}}
	var sharePort int
	for _, v := range tree["inbounds"].([]any) {
		in := v.(map[string]any)
		s, e := net.Listen("tcp", "127.0.0.1:0")
		if e != nil {
			t.Fatal(e)
		}
		port := s.Addr().(*net.TCPAddr).Port
		s.Close()
		in["listen_port"] = port
		if in["tag"] == "phone-share-in" {
			sharePort = port
		}
	}
	raw, _ = json.Marshal(tree)
	ctx := include.Context(context.Background())
	ctx = service.ContextWith[adapter.PlatformInterface](ctx, testPlatform{})
	var options option.Options
	if err = J.UnmarshalContext(ctx, raw, &options); err != nil {
		t.Fatal(err)
	}
	instance, err := box.New(box.Options{Context: ctx, Options: options})
	if err != nil {
		t.Fatal(err)
	}
	defer instance.Close()
	if err = instance.Start(); err != nil {
		t.Fatal(err)
	}
	dialer, _ := proxy.SOCKS5("tcp", fmt.Sprintf("127.0.0.1:%d", sharePort), nil, &net.Dialer{Timeout: time.Second})
	transport := &http.Transport{Dial: dialer.Dial}
	defer transport.CloseIdleConnections()
	_, port, _ := net.SplitHostPort(target.Listener.Addr().String())
	client := http.Client{Transport: transport, Timeout: 5 * time.Second}
	response, err := client.Get("http://only-via-tunnel.invalid:" + port)
	if err != nil {
		t.Fatal(err)
	}
	body, err := io.ReadAll(response.Body)
	response.Body.Close()
	if err != nil || string(body) != "share-through-tunnel" || dnsCount.Load() == 0 || tcpCount.Load() != 1 {
		t.Fatalf("route proof failed dns=%d tcp=%d", dnsCount.Load(), tcpCount.Load())
	}
	// With the only upstream listener removed, a cached target must not go DIRECT.
	transport.CloseIdleConnections()
	upstream.Close()
	if response, err := client.Get("http://only-via-tunnel.invalid:" + port); err == nil {
		response.Body.Close()
		t.Fatal("direct fallback after upstream failure")
	}
	if served.Load() != 1 {
		t.Fatal("target received traffic outside the proxy path")
	}
}

// Only interface-change monitoring is substituted because the host denies netlink.
// TCP sockets, DNS framing, config parsing and sing-box routing remain real.
type testPlatform struct{ adapter.PlatformInterface }

func (testPlatform) Initialize(adapter.NetworkManager) error  { return nil }
func (testPlatform) UsePlatformDefaultInterfaceMonitor() bool { return true }
func (testPlatform) CreateDefaultInterfaceMonitor(logger.Logger) tun.DefaultInterfaceMonitor {
	return nil
}
func (testPlatform) UsePlatformNetworkInterfaces() bool          { return false }
func (testPlatform) UsePlatformAutoDetectInterfaceControl() bool { return false }
func (testPlatform) UsePlatformWIFIMonitor() bool                { return false }
func (testPlatform) UsePlatformNeighborResolver() bool           { return false }
func (testPlatform) UsePlatformConnectionOwnerFinder() bool      { return false }
func (testPlatform) UsePlatformInterface() bool                  { return false }
func (testPlatform) UsePlatformNotification() bool               { return false }
