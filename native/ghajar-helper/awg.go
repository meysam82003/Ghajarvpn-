package main

import (
	"bufio"
	"context"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"flag"
	"fmt"
	"net"
	"net/netip"
	"os"
	"strconv"
	"strings"

	"github.com/amnezia-vpn/amneziawg-go/v3/conn"
	"github.com/amnezia-vpn/amneziawg-go/v3/device"
	"github.com/amnezia-vpn/amneziawg-go/v3/tun/netstack"
)

// awgConfig is a wg-quick / AmneziaWG .conf: [Interface] + [Peer] sections.
type awgConfig struct {
	privateKey string
	addresses  []netip.Addr
	dns        []netip.Addr
	mtu        int
	obfs       map[string]string // jc jmin jmax s1..s4 h1..h4 i1..i5
	peers      []awgPeer
}

type awgPeer struct {
	publicKey, presharedKey, endpoint string
	allowedIPs                        []string
	keepalive                         int
}

var awgObfsKeys = map[string]bool{"jc": true, "jmin": true, "jmax": true, "s1": true, "s2": true, "s3": true, "s4": true,
	"h1": true, "h2": true, "h3": true, "h4": true, "i1": true, "i2": true, "i3": true, "i4": true, "i5": true}

func parseAWGConfig(text string) (*awgConfig, error) {
	c := &awgConfig{mtu: 1280, obfs: map[string]string{}}
	section := ""
	var peer *awgPeer
	sc := bufio.NewScanner(strings.NewReader(text))
	sc.Buffer(make([]byte, 0, 64*1024), 1<<20)
	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())
		if i := strings.IndexAny(line, "#;"); i == 0 {
			continue
		}
		if line == "" {
			continue
		}
		if strings.HasPrefix(line, "[") {
			section = strings.ToLower(strings.Trim(line, "[] "))
			if section == "peer" {
				c.peers = append(c.peers, awgPeer{})
				peer = &c.peers[len(c.peers)-1]
			}
			continue
		}
		k, v, ok := strings.Cut(line, "=")
		if !ok {
			continue
		}
		key := strings.ToLower(strings.TrimSpace(k))
		val := strings.TrimSpace(v)
		switch section {
		case "interface":
			switch {
			case key == "privatekey":
				c.privateKey = val
			case key == "address":
				for _, a := range strings.Split(val, ",") {
					p, err := netip.ParsePrefix(strings.TrimSpace(a))
					if err != nil {
						ad, err2 := netip.ParseAddr(strings.TrimSpace(a))
						if err2 != nil {
							return nil, fmt.Errorf("address %q", a)
						}
						c.addresses = append(c.addresses, ad)
						continue
					}
					c.addresses = append(c.addresses, p.Addr())
				}
			case key == "dns":
				for _, a := range strings.Split(val, ",") {
					if ad, err := netip.ParseAddr(strings.TrimSpace(a)); err == nil {
						c.dns = append(c.dns, ad)
					}
				}
			case key == "mtu":
				c.mtu, _ = strconv.Atoi(val)
			case awgObfsKeys[key]:
				c.obfs[key] = val
			}
		case "peer":
			switch key {
			case "publickey":
				peer.publicKey = val
			case "presharedkey":
				peer.presharedKey = val
			case "endpoint":
				peer.endpoint = val
			case "allowedips":
				for _, a := range strings.Split(val, ",") {
					peer.allowedIPs = append(peer.allowedIPs, strings.TrimSpace(a))
				}
			case "persistentkeepalive":
				peer.keepalive, _ = strconv.Atoi(val)
			}
		}
	}
	if c.privateKey == "" || len(c.addresses) == 0 || len(c.peers) == 0 {
		return nil, errors.New("config needs PrivateKey, Address and a [Peer]")
	}
	if len(c.dns) == 0 {
		c.dns = []netip.Addr{netip.MustParseAddr("1.1.1.1")}
	}
	return c, nil
}

func keyHex(b64 string) (string, error) {
	b, err := base64.StdEncoding.DecodeString(b64)
	if err != nil || len(b) != 32 {
		return "", fmt.Errorf("bad key")
	}
	return hex.EncodeToString(b), nil
}

// uapi renders the configuration in the device's IPC format. Endpoints given
// by name are resolved here, on the real network (this process is outside
// the VPN), because the device only accepts IP:port.
func (c *awgConfig) uapi() (string, error) {
	var b strings.Builder
	pk, err := keyHex(c.privateKey)
	if err != nil {
		return "", fmt.Errorf("PrivateKey: %w", err)
	}
	fmt.Fprintf(&b, "private_key=%s\n", pk)
	for _, k := range []string{"jc", "jmin", "jmax", "s1", "s2", "s3", "s4", "h1", "h2", "h3", "h4", "i1", "i2", "i3", "i4", "i5"} {
		if v, ok := c.obfs[k]; ok && v != "" {
			fmt.Fprintf(&b, "%s=%s\n", k, v)
		}
	}
	for _, p := range c.peers {
		pub, err := keyHex(p.publicKey)
		if err != nil {
			return "", fmt.Errorf("PublicKey: %w", err)
		}
		fmt.Fprintf(&b, "public_key=%s\n", pub)
		if p.presharedKey != "" {
			psk, err := keyHex(p.presharedKey)
			if err != nil {
				return "", fmt.Errorf("PresharedKey: %w", err)
			}
			fmt.Fprintf(&b, "preshared_key=%s\n", psk)
		}
		if p.endpoint != "" {
			host, port, err := net.SplitHostPort(p.endpoint)
			if err != nil {
				return "", fmt.Errorf("Endpoint %q: %w", p.endpoint, err)
			}
			ips, err := net.DefaultResolver.LookupNetIP(context.Background(), "ip", host)
			if err != nil || len(ips) == 0 {
				return "", fmt.Errorf("cannot resolve endpoint %s", host)
			}
			fmt.Fprintf(&b, "endpoint=%s\n", net.JoinHostPort(ips[0].Unmap().String(), port))
		}
		if p.keepalive > 0 {
			fmt.Fprintf(&b, "persistent_keepalive_interval=%d\n", p.keepalive)
		}
		for _, a := range p.allowedIPs {
			if a != "" {
				fmt.Fprintf(&b, "allowed_ip=%s\n", a)
			}
		}
	}
	return b.String(), nil
}

type netstackBackend struct{ tnet *netstack.Net }

func (n netstackBackend) DialTCP(ctx context.Context, addr string) (net.Conn, error) {
	return n.tnet.DialContext(ctx, "tcp", addr)
}

func (n netstackBackend) ListenUDP() (net.PacketConn, error) {
	return n.tnet.ListenUDP(&net.UDPAddr{})
}

func (n netstackBackend) ResolveUDP(ctx context.Context, host string, port int) (net.Addr, error) {
	if ip, err := netip.ParseAddr(host); err == nil {
		return net.UDPAddrFromAddrPort(netip.AddrPortFrom(ip, uint16(port))), nil
	}
	ips, err := n.tnet.LookupContextHost(ctx, host)
	if err != nil || len(ips) == 0 {
		return nil, fmt.Errorf("resolve %s: %v", host, err)
	}
	ip, err := netip.ParseAddr(ips[0])
	if err != nil {
		return nil, err
	}
	return net.UDPAddrFromAddrPort(netip.AddrPortFrom(ip, uint16(port))), nil
}

// runAWG brings up AmneziaWG (or plain WireGuard, when no obfuscation keys
// are set) on a userspace network stack and serves it as SOCKS5.
func runAWG(args []string) error {
	fs := flag.NewFlagSet("awg", flag.ContinueOnError)
	listen := fs.Int("listen", 0, "local SOCKS5 port on 127.0.0.1")
	file := fs.String("config", "", "wg-quick style .conf")
	verbose := fs.Bool("v", false, "verbose device log")
	if err := fs.Parse(args); err != nil {
		return err
	}
	raw, err := os.ReadFile(*file)
	if err != nil {
		return err
	}
	cfg, err := parseAWGConfig(string(raw))
	if err != nil {
		return err
	}
	ipc, err := cfg.uapi()
	if err != nil {
		return err
	}
	tunDev, tnet, err := netstack.CreateNetTUN(cfg.addresses, cfg.dns, cfg.mtu)
	if err != nil {
		return fmt.Errorf("netstack: %w", err)
	}
	level := device.LogLevelError
	if *verbose {
		level = device.LogLevelVerbose
	}
	dev := device.NewDevice(tunDev, conn.NewDefaultBind(), device.NewLogger(level, "awg: "))
	if err := dev.IpcSet(ipc); err != nil {
		return fmt.Errorf("device config: %w", err)
	}
	if err := dev.Up(); err != nil {
		return fmt.Errorf("device up: %w", err)
	}
	defer dev.Close()
	ln, err := listenLocal(*listen)
	if err != nil {
		return err
	}
	return serveSocks5(ln, netstackBackend{tnet})
}
