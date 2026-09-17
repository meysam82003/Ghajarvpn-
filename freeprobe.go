package gozarcore

import (
    "context"
    "io"
    "net"
    "net/http"
    "strings"
    "time"
    v2net "github.com/xtls/xray-core/common/net"
    "github.com/xtls/xray-core/core"
    "github.com/xtls/xray-core/infra/conf/serial"
)

// MeasureDelayBounded verifies an HTTP response THROUGH this outbound, including
// handshake latency. The deadline is enforced in Go, not around a blocking JNI call.
func MeasureDelayBounded(configJSON string, timeoutMillis int64) int64 {
    if timeoutMillis < 500 || timeoutMillis > 10000 { timeoutMillis = 4000 }
    ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeoutMillis)*time.Millisecond)
    defer cancel()
    cfg, err := serial.LoadJSONConfig(strings.NewReader(configJSON))
    if err != nil { return -1 }
    inst, err := core.New(cfg)
    if err != nil { return -1 }
    defer inst.Close()
    if err = inst.Start(); err != nil { return -1 }
    transport := &http.Transport{
        DisableKeepAlives: true,
        DialContext: func(c context.Context, network, addr string) (net.Conn, error) {
            dest, err := v2net.ParseDestination(network + ":" + addr)
            if err != nil { return nil, err }
            return core.Dial(c, inst, dest)
        },
    }
    defer transport.CloseIdleConnections()
    client := &http.Client{Transport: transport, Timeout: time.Duration(timeoutMillis)*time.Millisecond,
        CheckRedirect: func(req *http.Request, via []*http.Request) error { return http.ErrUseLastResponse }}
    req, err := http.NewRequestWithContext(ctx, http.MethodGet, "https://www.gstatic.com/generate_204", nil)
    if err != nil { return -1 }
    start := time.Now()
    resp, err := client.Do(req)
    if err != nil { return -1 }
    defer resp.Body.Close()
    if resp.StatusCode != http.StatusNoContent { return -1 }
    if _, err = io.Copy(io.Discard, io.LimitReader(resp.Body, 4096)); err != nil { return -1 }
    return time.Since(start).Milliseconds()
}
