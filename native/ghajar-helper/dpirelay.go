package main

// dpirelay forwards one engine's connection to its real server unchanged and
// records the first bytes in each direction, so ndpi-classify can report how
// a DPI engine (nDPI) classifies the traffic this profile produces. It is a
// diagnostic for the TEST screen, never part of a normal connection.

import (
	"bytes"
	"encoding/binary"
	"errors"
	"flag"
	"fmt"
	"io"
	"net"
	"os"
	"strconv"
	"sync"
	"time"
)

type flowRecorder struct {
	mu      sync.Mutex
	buf     bytes.Buffer
	budget  int
	records int
	maxRec  int
	out     string
	written bool
}

func newRecorder(out string, proto byte, port int, budget, maxRec int) *flowRecorder {
	r := &flowRecorder{budget: budget, maxRec: maxRec, out: out}
	r.buf.WriteString("GDPI")
	r.buf.WriteByte(proto)
	binary.Write(&r.buf, binary.BigEndian, uint16(port))
	return r
}

func (r *flowRecorder) add(dir byte, p []byte) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.written || r.budget <= 0 || r.records >= r.maxRec || len(p) == 0 {
		return
	}
	if len(p) > r.budget {
		p = p[:r.budget]
	}
	if len(p) > 65535 {
		p = p[:65535]
	}
	r.buf.WriteByte(dir)
	binary.Write(&r.buf, binary.BigEndian, uint16(len(p)))
	r.buf.Write(p)
	r.budget -= len(p)
	r.records++
	if r.budget <= 0 || r.records >= r.maxRec {
		r.flushLocked()
	}
}

func (r *flowRecorder) flush() { r.mu.Lock(); r.flushLocked(); r.mu.Unlock() }

func (r *flowRecorder) flushLocked() {
	if r.written {
		return
	}
	r.written = true
	tmp := r.out + ".tmp"
	if os.WriteFile(tmp, r.buf.Bytes(), 0o600) == nil {
		os.Rename(tmp, r.out)
	}
}

type recWriter struct {
	w   io.Writer
	r   *flowRecorder
	dir byte
}

func (w recWriter) Write(p []byte) (int, error) {
	w.r.add(w.dir, append([]byte(nil), p...))
	return w.w.Write(p)
}

func runDPIRelay(args []string) error {
	fs := flag.NewFlagSet("dpirelay", flag.ContinueOnError)
	listen := fs.Int("listen", 0, "local port on 127.0.0.1")
	target := fs.String("target", "", "real server host:port")
	udp := fs.Bool("udp", false, "relay UDP instead of TCP")
	out := fs.String("out", "", "flow file to write")
	budget := fs.Int("bytes", 16384, "bytes to record")
	maxRec := fs.Int("records", 24, "reads/writes to record")
	idle := fs.Duration("idle", 30*time.Second, "record at most this long after the first byte")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if *target == "" || *out == "" {
		return errors.New("dpirelay: -target and -out are required")
	}
	_, ps, err := net.SplitHostPort(*target)
	if err != nil {
		return err
	}
	port, _ := strconv.Atoi(ps)
	if *udp {
		return relayUDP(*listen, *target, newRecorder(*out, 17, port, *budget, *maxRec), *idle)
	}
	rec := newRecorder(*out, 6, port, *budget, *maxRec)
	ln, err := listenLocal(*listen)
	if err != nil {
		return err
	}
	var once sync.Once
	for {
		c, err := ln.Accept()
		if err != nil {
			return err
		}
		go func(c net.Conn) {
			// Readiness checks connect and close without data: wait for the
			// first byte before dialling, and record only the first
			// connection that carries data.
			first := make([]byte, 32*1024)
			n, _ := c.Read(first)
			if n == 0 {
				c.Close()
				return
			}
			up, derr := net.DialTimeout("tcp", *target, 10*time.Second)
			if derr != nil {
				c.Close()
				fmt.Fprintln(os.Stderr, "dpirelay:", derr)
				return
			}
			record := false
			once.Do(func() { record = true })
			if !record {
				up.Write(first[:n])
				go func() { io.Copy(up, c); up.Close() }()
				io.Copy(c, up)
				c.Close()
				return
			}
			time.AfterFunc(*idle, rec.flush)
			rec.add(0, append([]byte(nil), first[:n]...))
			up.Write(first[:n])
			go func() { io.Copy(recWriter{up, rec, 0}, c); up.Close(); rec.flush() }()
			io.Copy(recWriter{c, rec, 1}, up)
			c.Close()
			rec.flush()
		}(c)
	}
}

func relayUDP(listen int, target string, rec *flowRecorder, idle time.Duration) error {
	raddr, err := net.ResolveUDPAddr("udp", target)
	if err != nil {
		return err
	}
	pc, err := net.ListenUDP("udp", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1), Port: listen})
	if err != nil {
		return err
	}
	fmt.Printf("ready %s\n", pc.LocalAddr())
	// A TCP listener on the same port lets the sidecar runner see "ready".
	if tl, err := net.Listen("tcp", pc.LocalAddr().String()); err == nil {
		go func() {
			for {
				c, err := tl.Accept()
				if err != nil {
					return
				}
				c.Close()
			}
		}()
	}
	up, err := net.DialUDP("udp", nil, raddr)
	if err != nil {
		return err
	}
	var client *net.UDPAddr
	var mu sync.Mutex
	go func() {
		b := make([]byte, 65536)
		for {
			n, err := up.Read(b)
			if err != nil {
				return
			}
			rec.add(1, append([]byte(nil), b[:n]...))
			mu.Lock()
			c := client
			mu.Unlock()
			if c != nil {
				pc.WriteToUDP(b[:n], c)
			}
		}
	}()
	b := make([]byte, 65536)
	started := false
	for {
		n, from, err := pc.ReadFromUDP(b)
		if err != nil {
			return err
		}
		if !started {
			started = true
			time.AfterFunc(idle, rec.flush)
		}
		mu.Lock()
		client = from
		mu.Unlock()
		rec.add(0, append([]byte(nil), b[:n]...))
		up.Write(b[:n])
	}
}
