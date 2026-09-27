package main

import (
	"errors"
	"flag"
	"fmt"
	"net"
	"os"
	"path/filepath"
	"strconv"
	"time"

	"github.com/enfein/mieru/v3/pkg/appctl"
	pb "github.com/enfein/mieru/v3/pkg/appctl/appctlpb"
	"github.com/enfein/mieru/v3/pkg/cli"
	"google.golang.org/protobuf/encoding/protojson"
	"google.golang.org/protobuf/proto"
)

// runMieru runs the upstream mieru client (enfein/mieru, GPL-3.0) in this
// process exactly as its own `mieru run` does, from a mieru:// or mierus://
// link. Only the local ports and the active profile are set here; the SOCKS5
// server (with UDP associate) is mieru's own.
func runMieru(args []string) error {
	fs := flag.NewFlagSet("mieru", flag.ContinueOnError)
	listen := fs.Int("listen", 0, "local SOCKS5 port")
	rpc := fs.Int("rpc", 0, "local RPC port mieru requires")
	link := fs.String("url", "", "mieru:// or mierus:// link")
	dir := fs.String("dir", ".", "working directory")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if *listen <= 0 || *rpc <= 0 || *link == "" {
		return errors.New("-listen, -rpc and -url are required")
	}
	cfg, err := appctl.ParseURLClientConfig(*link)
	if err != nil {
		return err
	}
	if len(cfg.GetProfiles()) == 0 {
		return errors.New("the link carries no profile")
	}
	if cfg.GetActiveProfile() == "" {
		cfg.ActiveProfile = proto.String(cfg.GetProfiles()[0].GetProfileName())
	}
	cfg.Socks5Port = proto.Int32(int32(*listen))
	cfg.RpcPort = proto.Int32(int32(*rpc))
	cfg.Socks5ListenLAN = proto.Bool(false)
	cfg.HttpProxyPort = nil
	cfg.LoggingLevel = pb.LoggingLevel_INFO.Enum()
	js, err := protojson.Marshal(cfg)
	if err != nil {
		return err
	}
	path := filepath.Join(*dir, "mieru-client.json")
	if err := os.WriteFile(path, js, 0o600); err != nil {
		return err
	}
	_ = os.Setenv("MIERU_CONFIG_JSON_FILE", path)
	_ = os.Setenv("HOME", *dir)
	go announceWhenListening(*listen, path)
	appctl.RecordAppStartTime()
	appctl.SetAppType(appctl.CLIENT_APP)
	cli.RegisterClientCommands()
	os.Args = []string{"mieru", "run"}
	return cli.ParseAndExecute()
}

// announceWhenListening prints the ready line once the port accepts, and
// removes the configuration file (it holds the password) after that.
func announceWhenListening(port int, secret string) {
	for i := 0; i < 600; i++ {
		c, err := net.DialTimeout("tcp", net.JoinHostPort("127.0.0.1", strconv.Itoa(port)), 300*time.Millisecond)
		if err == nil {
			c.Close()
			fmt.Printf("ready 127.0.0.1:%d\n", port)
			_ = os.Remove(secret)
			return
		}
		time.Sleep(100 * time.Millisecond)
	}
}
