// ghajar-helper is the small helper engine that Ghajar VPN runs in front of
// sing-box for protocols no pinned upstream core covers on its own:
//
//	sshtransport  SSH over HTTP payload / HTTP(S) proxy / TLS-SNI / WebSocket
//	awg           AmneziaWG (and plain WireGuard) in userspace, as SOCKS5
//	mieru         Mieru client (upstream enfein/mieru code, unmodified)
//	brook         Brook client (upstream txthinking/brook library, unmodified)
//	sstp          SSTP (PPP over HTTPS, PAP / MS-CHAPv2) in userspace, as SOCKS5
//	softether     SoftEther VPN protocol (Virtual Hub, DHCP, ARP) in userspace, as SOCKS5
//	dpirelay      records a test connection for the nDPI fingerprint check
//
// Every mode listens on one local TCP port on 127.0.0.1, printed as
// "ready 127.0.0.1:PORT" once it accepts connections. Licence: GPL-3.0 (the
// same as Ghajar VPN); the upstream libraries keep their own licences
// (see third_party/ghajar-helper/NOTICE).
package main

import (
	"fmt"
	"os"
)

func main() {
	if len(os.Args) < 2 {
		fmt.Fprintln(os.Stderr, "usage: ghajar-helper <sshtransport|awg|mieru|brook|sstp|softether|dpirelay> [flags]")
		os.Exit(2)
	}
	var err error
	switch os.Args[1] {
	case "sshtransport":
		err = runSSHTransport(os.Args[2:])
	case "awg":
		err = runAWG(os.Args[2:])
	case "mieru":
		err = runMieru(os.Args[2:])
	case "brook":
		err = runBrook(os.Args[2:])
	case "sstp":
		err = runSSTP(os.Args[2:])
	case "softether":
		err = runSoftEther(os.Args[2:])
	case "dpirelay":
		err = runDPIRelay(os.Args[2:])
	case "version":
		fmt.Println("ghajar-helper 1")
		return
	default:
		err = fmt.Errorf("unknown mode %q", os.Args[1])
	}
	if err != nil {
		fmt.Fprintln(os.Stderr, "error:", err)
		os.Exit(1)
	}
}
