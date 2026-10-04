// Built inside the audited Bettbox module with the profile-sandbox patch.
package main

import (
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"net"
	"net/netip"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"

	"github.com/metacubex/mihomo/adapter/inbound"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/hub/executor"
	"github.com/metacubex/mihomo/listener"
	"github.com/metacubex/mihomo/tunnel"
	"github.com/metacubex/mihomo/tunnel/statistic"
)

const provenance = "mihomo-ghajar 3189346611caeba73aa87feaf708e4fd65115d16"

type plan struct {
	Mode      string   `json:"mode"`
	MTU       uint32   `json:"mtu"`
	Addresses []string `json:"addresses"`
	Routes    []string `json:"routes"`
	DNS       []string `json:"dns"`
	Full      bool     `json:"fullConfigPreserved"`
	Port      int      `json:"socksPort"`
}

func main() {
	version := flag.Bool("version", false, "source identity")
	check := flag.Bool("check", false, "validate original full config")
	path := flag.String("config", "", "private full YAML/JSON file")
	socket := flag.String("socket", "", "private host control socket")
	flag.Parse()
	if *version {
		fmt.Println(provenance)
		return
	}
	if err := run(*path, *socket, *check); err != nil {
		// Never send the upstream parse error: YAML errors may contain credentials.
		fmt.Fprintln(os.Stderr, "Mihomo profile could not be activated; verify Android-compatible full config and provider files")
		os.Exit(1)
	}
}
func run(path, socket string, check bool) error {
	if path == "" {
		return errors.New("missing config")
	}
	info, err := os.Stat(path)
	if err != nil || info.Size() > 8*1024*1024 {
		return errors.New("config limit")
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		return err
	}
	home := filepath.Dir(path)
	C.SetHomeDir(home)
	C.SetConfig(path)
	cfg, p, err := prepare(raw)
	if err != nil {
		return err
	}
	if check {
		return json.NewEncoder(os.Stdout).Encode(p)
	}
	address := &net.UnixAddr{Name: socket, Net: "unix"}
	os.Remove(socket)
	control, err := net.ListenUnix("unix", address)
	if err != nil {
		return err
	}
	defer control.Close()
	defer os.Remove(socket)
	os.Chmod(socket, 0600)
	control.SetDeadline(time.Now().Add(15 * time.Second))
	conn, err := control.AcceptUnix()
	if err != nil {
		return err
	}
	return serve(cfg, p, conn)
}

// Production passes the private Unix control connection; tests may use net.Pipe for SOCKS mode.
func serve(cfg *config.Config, p plan, conn net.Conn) error {
	defer conn.Close()
	conn.SetReadDeadline(time.Now().Add(10 * time.Second))
	if p.Mode == "tun" {
		data := make([]byte, 1)
		ancillary := make([]byte, syscall.CmsgSpace(4))
		unix, ok := conn.(*net.UnixConn)
		if !ok {
			return errors.New("TUN requires Unix descriptor transfer")
		}
		n, oobn, flags, _, err := unix.ReadMsgUnix(data, ancillary)
		if err != nil || n != 1 || data[0] != 1 || flags&syscall.MSG_CTRUNC != 0 {
			return errors.New("missing TUN descriptor")
		}
		messages, err := syscall.ParseSocketControlMessage(ancillary[:oobn])
		if err != nil {
			return err
		}
		var fds []int
		for _, m := range messages {
			descriptors, e := syscall.ParseUnixRights(&m)
			if e != nil {
				return e
			}
			fds = append(fds, descriptors...)
		}
		if len(fds) != 1 {
			for _, fd := range fds {
				syscall.Close(fd)
			}
			return errors.New("exactly one TUN required")
		}
		cfg.General.Tun.FileDescriptor = fds[0]
		// Android VpnService installs routes from p. Native core must never open/configure another TUN.
		cfg.General.Tun.AutoRoute = false
		cfg.General.Tun.AutoDetectInterface = false
		cfg.General.Tun.AutoRedirect = false
	} else {
		var b [1]byte
		if _, err := conn.Read(b[:]); err != nil || b[0] != 0 {
			return errors.New("bad start")
		}
	}
	executor.ApplyConfig(cfg, true)
	defer func() {
		tunnel.OnSuspend()
		listener.ReCreateHTTP(0, tunnel.Tunnel)
		listener.ReCreateSocks(0, tunnel.Tunnel)
		listener.ReCreateMixed(0, tunnel.Tunnel)
		statistic.DefaultManager.Range(func(c statistic.Tracker) bool { c.Close(); return true })
		executor.Shutdown()
	}()
	// This audited fork omits listener activation AND its access policy initialization.
	listener.SetAllowLan(false)
	listener.SetBindAddress("127.0.0.1")
	inbound.SetSkipAuthPrefixes(cfg.General.SkipAuthPrefixes)
	inbound.SetAllowedIPs(cfg.General.LanAllowedIPs)
	inbound.SetDisAllowedIPs(cfg.General.LanDisAllowedIPs)
	listener.ReCreateHTTP(cfg.General.Port, tunnel.Tunnel)
	listener.ReCreateSocks(cfg.General.SocksPort, tunnel.Tunnel)
	listener.ReCreateMixed(cfg.General.MixedPort, tunnel.Tunnel)
	listener.PatchInboundListeners(cfg.Listeners, tunnel.Tunnel, true)
	actualPorts := listener.GetPorts()
	if actualPorts.Port != cfg.General.Port || actualPorts.SocksPort != cfg.General.SocksPort || actualPorts.MixedPort != cfg.General.MixedPort {
		return errors.New("listener activation failed")
	}
	if p.Mode == "tun" {
		listener.ReCreateTun(cfg.General.Tun, tunnel.Tunnel)
		if !listener.GetTunConf().Enable {
			return errors.New("TUN activation failed")
		}
	} else {
		c, err := net.DialTimeout("tcp", net.JoinHostPort("127.0.0.1", fmt.Sprint(p.Port)), 2*time.Second)
		if err != nil {
			return err
		}
		c.Close()
	}
	if _, err := conn.Write([]byte{1}); err != nil {
		return err
	}
	conn.SetReadDeadline(time.Time{})
	signals := make(chan os.Signal, 1)
	signal.Notify(signals, syscall.SIGTERM, syscall.SIGINT)
	defer signal.Stop(signals)
	// Parent death/STOP closes the private control connection, even if the host process disappears.
	closed := make(chan struct{})
	go func() { var b [1]byte; conn.Read(b[:]); close(closed) }()
	select {
	case <-signals:
	case <-closed:
	}
	return nil
}
func prepare(original []byte) (*config.Config, plan, error) {
	var p plan
	raw, err := config.UnmarshalRawConfig(original)
	if err != nil {
		return nil, p, err
	}
	// Explicitly reject OS/server-only semantics; never silently drop them.
	if raw.AllowLan || raw.RedirPort != 0 || raw.TProxyPort != 0 || raw.IPTables.Enable || raw.RoutingMark != 0 || raw.Interface != "" ||
		raw.ExternalController != "" || raw.ExternalControllerTLS != "" || raw.ExternalControllerUnix != "" || raw.ExternalControllerPipe != "" || raw.ExternalUI != "" || (raw.ExternalUIURL != "" && raw.ExternalUIURL != config.DefaultRawConfig().ExternalUIURL) || raw.ExternalDohServer != "" ||
		raw.ShadowSocksConfig != "" || raw.VmessConfig != "" || raw.TuicServer.Enable || len(raw.Listeners) != 0 || len(raw.Tunnels) != 0 {
		return nil, p, errors.New("unsupported rootless or server listener settings")
	}
	if raw.BindAddress != "" && raw.BindAddress != "*" && raw.BindAddress != "127.0.0.1" {
		return nil, p, errors.New("non-loopback listener")
	}
	if raw.DNS.Enable && raw.DNS.Listen != "" {
		host, _, err := net.SplitHostPort(raw.DNS.Listen)
		if err != nil || (host != "127.0.0.1" && host != "::1") {
			return nil, p, errors.New("DNS listener must be loopback")
		}
	}
	seenPorts := make(map[int]bool)
	for _, port := range []int{raw.Port, raw.SocksPort, raw.MixedPort} {
		if port != 0 {
			if port < 1024 || port > 65535 || seenPorts[port] {
				return nil, p, errors.New("invalid or duplicate listener port")
			}
			seenPorts[port] = true
		}
	}
	// The upstream Android flag bypasses Path.IsSafePath. Constrain file-bearing config before parsing.
	var tree any
	// YAML paths are checked in raw provider/proxy maps rather than by re-serializing the document.
	tree = raw
	encoded, err := json.Marshal(tree)
	if err != nil {
		return nil, p, err
	}
	var decoded any
	if err := json.Unmarshal(encoded, &decoded); err != nil {
		return nil, p, err
	}
	if !safePaths(decoded, "root") {
		return nil, p, errors.New("external file path")
	}
	cfg, err := config.ParseRawConfig(raw)
	if err != nil {
		return nil, p, err
	}
	p.Full = true
	p.MTU = 1500
	if !raw.Tun.Enable {
		p.Mode = "socks"
		p.Port = raw.SocksPort
		if p.Port == 0 {
			p.Port = raw.MixedPort
		}
		if p.Port < 1024 || p.Port > 65535 || len(raw.Authentication) != 0 {
			return nil, p, errors.New("configure an unauthenticated local socks-port or mixed-port")
		}
		return cfg, p, nil
	}
	t := cfg.General.Tun
	if t.Stack == C.TunMips {
		return nil, p, errors.New("MIPStack is not this adapter; explicitly select supported stack")
	}
	if !t.AutoRoute || t.FileDescriptor != 0 || t.AutoRedirect || t.StrictRoute || len(t.LoopbackAddress) != 0 ||
		len(t.IncludeMACAddress) != 0 || len(t.ExcludeMACAddress) != 0 || len(t.ExcludeSrcPort) != 0 || len(t.ExcludeDstPort) != 0 || len(t.ExcludeSrcPortRange) != 0 || len(t.ExcludeDstPortRange) != 0 || len(t.RouteExcludeAddress) != 0 || len(t.RouteAddressSet) != 0 || len(t.RouteExcludeAddressSet) != 0 ||
		len(t.IncludeUID) != 0 || len(t.ExcludeUID) != 0 || len(t.IncludeUIDRange) != 0 || len(t.ExcludeUIDRange) != 0 ||
		len(t.IncludePackage) != 0 || len(t.ExcludePackage) != 0 || len(t.IncludeInterface) != 0 || len(t.ExcludeInterface) != 0 || len(t.IncludeAndroidUser) != 0 {
		return nil, p, errors.New("TUN policy requires host API extension")
	}
	p.Mode = "tun"
	if t.MTU > 0 {
		p.MTU = t.MTU
	}
	if p.MTU < 1280 || p.MTU > 9000 {
		return nil, p, errors.New("invalid MTU")
	}
	for _, v := range t.Inet4Address {
		p.Addresses = append(p.Addresses, v.String())
	}
	if cfg.General.IPv6 {
		for _, v := range t.Inet6Address {
			p.Addresses = append(p.Addresses, v.String())
		}
	}
	if len(p.Addresses) == 0 {
		return nil, p, errors.New("TUN address required")
	}
	for _, v := range t.RouteAddress {
		p.Routes = append(p.Routes, v.String())
	}
	if len(p.Routes) == 0 {
		p.Routes = append(p.Routes, "0.0.0.0/0")
		if cfg.General.IPv6 {
			p.Routes = append(p.Routes, "::/0")
		}
	}
	if !cfg.DNS.Enable || len(t.DNSHijack) == 0 {
		return nil, p, errors.New("TUN requires explicit DNS with interception")
	}
	for _, v := range t.Inet4Address {
		ip := v.Addr().Next()
		if ip.IsValid() && v.Contains(ip) {
			p.DNS = []string{ip.String()}
			break
		}
	}
	if len(p.DNS) == 0 {
		return nil, p, errors.New("IPv4 TUN subnet required for DNS")
	}
	hijacks := false
	for _, h := range t.DNSHijack {
		if h == "any:53" || h == "0.0.0.0:53" || h == p.DNS[0]+":53" {
			hijacks = true
		}
	}
	if !hijacks {
		return nil, p, errors.New("TUN DNS address not intercepted")
	}
	// Host API 1 cannot represent exclusions; reject instead of leaking excluded traffic.
	for _, route := range append(t.Inet4RouteExcludeAddress, t.Inet6RouteExcludeAddress...) {
		if route != (netip.Prefix{}) {
			return nil, p, errors.New("route exclusion unsupported by API 1")
		}
	}
	if len(t.Inet4RouteAddress) > 0 || len(t.Inet6RouteAddress) > 0 {
		return nil, p, errors.New("use route-address")
	}
	return cfg, p, nil
}
