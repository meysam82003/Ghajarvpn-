package main

import (
	"errors"
	"flag"
	"fmt"
	"strconv"

	"github.com/txthinking/brook"
)

// runBrook runs the upstream brook client library (txthinking/brook,
// GPL-3.0) for a brook:// link - the same calls `brook connect --link` makes.
// Its SOCKS5 server carries UDP as well.
func runBrook(args []string) error {
	fs := flag.NewFlagSet("brook", flag.ContinueOnError)
	listen := fs.Int("listen", 0, "local SOCKS5 port")
	link := fs.String("url", "", "brook:// link")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if *listen <= 0 || *link == "" {
		return errors.New("-listen and -url are required")
	}
	b, err := brook.NewBrookLink(*link)
	if err != nil {
		return err
	}
	if err := b.PrepareSocks5Server("127.0.0.1:"+strconv.Itoa(*listen), "127.0.0.1", 0, 0); err != nil {
		return err
	}
	go announceWhenListening(*listen, "")
	fmt.Println("brook client starting")
	return b.ListenAndServe()
}
