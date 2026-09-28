/*
 * ndpi-classify: how does nDPI classify one recorded flow?
 *
 * Part of Ghajar VPN (GPL-3.0). Links nDPI (LGPL-3.0) initialised with
 * NDPI_LICENSE_FOR_PROFIT_LGPL, so only nDPI's LGPL components run.
 *
 * Input (written by `ghajar-helper dpirelay`):
 *   "GDPI" | proto (1: 6 = TCP, 17 = UDP) | server port (2, big endian)
 *   then records: direction (1: 0 = client->server, 1 = back) |
 *                 length (2, big endian) | payload
 * The flow is replayed as IPv4 packets 10.0.0.2:40000 <-> 10.0.0.1:port
 * (TCP with a handshake and real sequence numbers). Output: one JSON line.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <arpa/inet.h>
#include "ndpi_api.h"

static void json_str(const char *s) {
  putchar('"');
  for (; s && *s; s++) {
    if (*s == '"' || *s == '\\') { putchar('\\'); putchar(*s); }
    else if ((unsigned char)*s < 0x20) printf("\\u%04x", *s);
    else putchar(*s);
  }
  putchar('"');
}

static size_t build(uint8_t *out, int proto, int dir, uint16_t sport, uint16_t dport,
                    uint32_t seq, uint32_t ack, uint8_t flags, const uint8_t *p, size_t n) {
  size_t l4 = proto == 6 ? 20 : 8;
  size_t total = 20 + l4 + n;
  memset(out, 0, 20 + l4);
  out[0] = 0x45; out[2] = total >> 8; out[3] = total & 0xff; out[8] = 64; out[9] = proto;
  uint32_t cli = htonl(0x0a000002), srv = htonl(0x0a000001);
  memcpy(out + 12, dir ? &srv : &cli, 4);
  memcpy(out + 16, dir ? &cli : &srv, 4);
  uint8_t *t = out + 20;
  t[0] = sport >> 8; t[1] = sport; t[2] = dport >> 8; t[3] = dport;
  if (proto == 6) {
    uint32_t s = htonl(seq), a = htonl(ack);
    memcpy(t + 4, &s, 4); memcpy(t + 8, &a, 4);
    t[12] = 5 << 4; t[13] = flags; t[14] = 0xff; t[15] = 0xff;
  } else {
    t[4] = (8 + n) >> 8; t[5] = (8 + n) & 0xff;
  }
  memcpy(t + l4, p, n);
  return total;
}

int main(int argc, char **argv) {
  if (argc != 2) { fprintf(stderr, "usage: ndpi-classify <flow file>\n"); return 2; }
  FILE *f = fopen(argv[1], "rb");
  if (!f) { perror("open"); return 1; }
  uint8_t hdr[7];
  if (fread(hdr, 1, 7, f) != 7 || memcmp(hdr, "GDPI", 4) || (hdr[4] != 6 && hdr[4] != 17)) {
    fprintf(stderr, "not a flow file\n"); return 1;
  }
  int proto = hdr[4];
  uint16_t port = (hdr[5] << 8) | hdr[6];

  struct ndpi_detection_module_struct *nd = ndpi_init_detection_module(NULL, NDPI_LICENSE_FOR_PROFIT_LGPL);
  if (!nd || ndpi_finalize_initialization(nd) != 0) { fprintf(stderr, "ndpi init failed\n"); return 1; }
  struct ndpi_flow_struct *flow = calloc(1, SIZEOF_FLOW_STRUCT);
  static uint8_t pkt[70000], payload[65536];
  uint64_t ts = 1700000000000ULL;
  uint32_t cseq = 1000, sseq = 5000;
  int packets = 0;
  ndpi_protocol res;
  memset(&res, 0, sizeof(res));

  if (proto == 6) { /* handshake */
    size_t n;
    n = build(pkt, 6, 0, 40000, port, cseq++, 0, 0x02, NULL, 0);
    res = ndpi_detection_process_packet(nd, flow, pkt, n, ts++, NULL); packets++;
    n = build(pkt, 6, 1, port, 40000, sseq++, cseq, 0x12, NULL, 0);
    res = ndpi_detection_process_packet(nd, flow, pkt, n, ts++, NULL); packets++;
    n = build(pkt, 6, 0, 40000, port, cseq, sseq, 0x10, NULL, 0);
    res = ndpi_detection_process_packet(nd, flow, pkt, n, ts++, NULL); packets++;
  }
  for (;;) {
    uint8_t rh[3];
    if (fread(rh, 1, 3, f) != 3) break;
    int dir = rh[0] & 1;
    size_t len = (rh[1] << 8) | rh[2];
    if (len > sizeof(payload) || fread(payload, 1, len, f) != len) break;
    /* split big TCP chunks into MSS-sized segments */
    size_t off = 0;
    do {
      size_t chunk = len - off;
      if (proto == 6 && chunk > 1400) chunk = 1400;
      if (proto == 17 && chunk > 65000) chunk = 65000;
      size_t n = dir
        ? build(pkt, proto, 1, port, 40000, sseq, cseq, 0x18, payload + off, chunk)
        : build(pkt, proto, 0, 40000, port, cseq, sseq, 0x18, payload + off, chunk);
      if (dir) sseq += chunk; else cseq += chunk;
      res = ndpi_detection_process_packet(nd, flow, pkt, n, ts, NULL);
      ts += 5; packets++; off += chunk;
    } while (off < len);
  }
  fclose(f);
  if (res.proto.master_protocol == NDPI_PROTOCOL_UNKNOWN && res.proto.app_protocol == NDPI_PROTOCOL_UNKNOWN)
    res = ndpi_detection_giveup(nd, flow);

  char name[128];
  ndpi_protocol2name(nd, res.proto, name, sizeof(name));
  printf("{\"protocol\":"); json_str(name);
  printf(",\"master\":"); json_str(ndpi_get_proto_name(nd, res.proto.master_protocol));
  printf(",\"app\":"); json_str(ndpi_get_proto_name(nd, res.proto.app_protocol));
  printf(",\"category\":"); json_str(ndpi_category_get_name(nd, res.category));
  printf(",\"confidence\":"); json_str(ndpi_confidence_get_name(flow->confidence));
  printf(",\"packets\":%d,\"license\":\"LGPL components only (dual-licensed TLS, QUIC, DNS, DHCP dissectors off)\",\"risks\":[", packets);
  int first = 1;
  for (int r = 1; r < NDPI_MAX_RISK; r++) {
    /* The TCP/IP headers are synthesised here, so a risk computed from the
       TCP fingerprint says nothing about the real flow: leave it out. */
    if (r == NDPI_MALICIOUS_FINGERPRINT) continue;
    if (NDPI_ISSET_BIT(flow->risk, r)) {
      if (!first) putchar(',');
      json_str(ndpi_risk2str((ndpi_risk_enum)r)); first = 0;
    }
  }
  printf("]}\n");
  ndpi_flow_free(flow);
  ndpi_exit_detection_module(nd);
  return 0;
}
