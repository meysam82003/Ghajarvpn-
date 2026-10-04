package main

import (
	"fmt"
	C "github.com/metacubex/mihomo/constant"
	"golang.org/x/net/proxy"
	"io"
	"net"
	"net/http"
	"net/http/httptest"

	"sync/atomic"
	"testing"
	"time"
)

func TestFullConfigSelectorTransferAndControlClose(t *testing.T) {
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { io.WriteString(w, "ghajar-loopback") }))
	defer target.Close()
	upstream, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer upstream.Close()
	var traversed atomic.Int32
	go func() {
		for {
			c, e := upstream.Accept()
			if e != nil {
				return
			}
			go func() {
				defer c.Close()
				c.SetDeadline(time.Now().Add(5 * time.Second))
				h := make([]byte, 3)
				if _, e := io.ReadFull(c, h); e != nil {
					return
				}
				c.Write([]byte{5, 0})
				head := make([]byte, 4)
				if _, e := io.ReadFull(c, head); e != nil || head[3] != 1 {
					return
				}
				addr := make([]byte, 6)
				if _, e := io.ReadFull(c, addr); e != nil {
					return
				}
				endpoint := fmt.Sprintf("%s:%d", net.IP(addr[:4]), int(addr[4])*256+int(addr[5]))
				out, e := net.DialTimeout("tcp", endpoint, time.Second)
				if e != nil {
					return
				}
				defer out.Close()
				traversed.Add(1)
				c.Write([]byte{5, 0, 0, 1, 0, 0, 0, 0, 0, 0})
				go io.Copy(out, c)
				io.Copy(c, out)
			}()
		}
	}()
	local, e := net.Listen("tcp", "127.0.0.1:0")
	if e != nil {
		t.Fatal(e)
	}
	port := local.Addr().(*net.TCPAddr).Port
	local.Close()
	C.SetHomeDir(t.TempDir())
	data := fmt.Sprintf("socks-port: %d\nproxies:\n - {name: tunnel, type: socks5, server: 127.0.0.1, port: %d}\nproxy-groups:\n - {name: choice, type: select, proxies: [tunnel, REJECT]}\nrules: ['MATCH,choice']\n", port, upstream.Addr().(*net.TCPAddr).Port)
	cfg, p, e := prepare([]byte(data))
	if e != nil {
		t.Fatal(e)
	}
	control, engineControl := net.Pipe()
	defer control.Close()
	done := make(chan error, 1)
	go func() { done <- serve(cfg, p, engineControl) }()
	control.SetDeadline(time.Now().Add(3 * time.Second))
	control.Write([]byte{0})
	ack := make([]byte, 1)
	if _, e = io.ReadFull(control, ack); e != nil || ack[0] != 1 {
		t.Fatalf("activation %v", e)
	}
	dialer, e := proxy.SOCKS5("tcp", fmt.Sprintf("127.0.0.1:%d", port), nil, &net.Dialer{Timeout: time.Second})
	if e != nil {
		t.Fatal(e)
	}
	transport := &http.Transport{Dial: dialer.Dial}
	defer transport.CloseIdleConnections()
	client := http.Client{Transport: transport, Timeout: 3 * time.Second}
	response, e := client.Get(target.URL)
	if e != nil {
		t.Fatal(e)
	}
	body, e := io.ReadAll(response.Body)
	response.Body.Close()
	if e != nil || string(body) != "ghajar-loopback" || traversed.Load() != 1 {
		t.Fatal("did not traverse selected proxy")
	}
	control.Close()
	select {
	case e := <-done:
		if e != nil {
			t.Fatal(e)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("control close did not stop engine")
	}
	c, e := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", port), time.Second)
	if e == nil {
		c.Close()
		t.Fatal("listener survived shutdown")
	}
}
