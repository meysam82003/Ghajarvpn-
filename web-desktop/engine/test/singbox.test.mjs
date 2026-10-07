// Validates web-desktop/engine/singbox.js against the pinned sing-box
// (SagerNet/sing-box 132b38e, v1.15.0-alpha.9): every protocol the Android
// builder supports, as a proxy-only and as a TUN configuration, plus the TUN
// variants (Xray SOCKS upstream, per-app include / exclude, Iran rule-set,
// per-OS), each run through `sing-box check`. Also pins the sidecar command
// lines to the ones engine/SidecarRunner.kt builds.
//
//   node web-desktop/engine/test/singbox.test.mjs
//   SING_BOX=/path/to/sing-box node ...   (default /tmp/claude-0/cores/sing-box)
//
// The test binary has no Cronet (with_naive_outbound), so NaiveProxy is built
// and shape-checked but not passed to `check`. Exits 0 on success.
import { createRequire } from 'node:module'
import { spawnSync } from 'node:child_process'
import { mkdtempSync, writeFileSync, rmSync, existsSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import crypto from 'node:crypto'
import assert from 'node:assert/strict'

const require = createRequire(import.meta.url)
const here = dirname(fileURLToPath(import.meta.url))
const sb = require(join(here, '..', 'singbox.js'))

const SING_BOX = process.env.SING_BOX || '/tmp/claude-0/cores/sing-box'
if (!existsSync(SING_BOX)) { console.error('sing-box not found at ' + SING_BOX); process.exit(1) }

const work = mkdtempSync(join(tmpdir(), 'ghajar-singbox-test-'))
let checks = 0
let failures = 0
const results = []

/** A configuration sing-box must refuse, with [pattern] in its error. */
function checkRejected(name, config, pattern) {
  const file = join(work, `${String(++checks).padStart(3, '0')}.json`)
  writeFileSync(file, JSON.stringify(config, null, 2))
  const r = spawnSync(SING_BOX, ['check', '-c', file, '-D', work, '--disable-color'], { encoding: 'utf8', timeout: 30000 })
  const ok = r.status !== 0 && pattern.test(r.stderr + r.stdout)
  if (!ok) { failures++; console.error(`FAIL ${name}: expected rejection ${pattern}, got status ${r.status}\n  ${(r.stderr || '').trim()}`) }
  results.push(`${ok ? 'ok  ' : 'FAIL'} ${name}`)
}

function check(name, config) {
  const file = join(work, `${String(++checks).padStart(3, '0')}.json`)
  writeFileSync(file, JSON.stringify(config, null, 2))
  const r = spawnSync(SING_BOX, ['check', '-c', file, '-D', work, '--disable-color'], { encoding: 'utf8', timeout: 30000 })
  const ok = r.status === 0
  if (!ok) {
    failures++
    console.error(`FAIL ${name}\n  ${(r.stderr || r.stdout || String(r.error)).trim().split('\n').slice(-3).join('\n  ')}\n  config: ${file}`)
  }
  results.push(`${ok ? 'ok  ' : 'FAIL'} ${name}`)
  return ok
}

function test(name, fn) {
  try { fn() } catch (e) { failures++; console.error(`FAIL ${name}: ${e.stack || e}`); results.push(`FAIL ${name}`); return }
}

// ------------------------------------------------------------------ fixtures

// An SSH key pair as the app stores them: private key PEM, host key in authorized_keys form.
const ed = crypto.generateKeyPairSync('ed25519')
const sshPrivate = ed.privateKey.export({ type: 'pkcs8', format: 'pem' })
const rawPub = ed.publicKey.export({ format: 'der', type: 'spki' }).subarray(-32)
const sshStr = (b) => { const l = Buffer.alloc(4); l.writeUInt32BE(b.length); return Buffer.concat([l, b]) }
const sshHostKey = 'ssh-ed25519 ' + Buffer.concat([sshStr(Buffer.from('ssh-ed25519')), sshStr(rawPub)]).toString('base64')
const key32 = () => crypto.randomBytes(32).toString('base64')
const HEX64 = 'a1'.repeat(32)
const PUBKEY = 'AB'.repeat(32)

// The Iran IP rule-set, compiled by the same binary from a small source file.
const irSource = join(work, 'geoip-ir.json')
const irSrs = join(work, 'geoip-ir.srs')
writeFileSync(irSource, JSON.stringify({ version: 3, rules: [{ ip_cidr: ['2.144.0.0/14', '5.22.0.0/17', '185.143.232.0/22'] }] }))
{
  const r = spawnSync(SING_BOX, ['rule-set', 'compile', '--output', irSrs, irSource], { encoding: 'utf8' })
  assert.equal(r.status, 0, 'rule-set compile: ' + r.stderr)
}

/** A ProxyConfig.toJson()-shaped object with the app's defaults. */
const P = (o) => ({
  id: crypto.randomUUID(), name: o.protocol + ' test', address: 'example.com', port: 443, uuid: '', password: '',
  method: '', alterId: 0, encryption: 'none', flow: '', network: 'tcp', security: 'none', sni: '', publicKey: '',
  shortId: '', fingerprint: 'chrome', path: '', host: '', serviceName: '', mode: '', alpn: '', source: 'PERSONAL',
  headerType: '', subId: '', privateKey: '', localAddress: '', mtu: 0, reserved: '', locked: false, favorite: false,
  hyObfs: '', hyObfsPassword: '', hyUpMbps: 0, hyDownMbps: 0, allowInsecure: false, pinnedCertSha256: '', extra: '',
  ...o,
  extra: o.extra === undefined ? '' : typeof o.extra === 'string' ? o.extra : JSON.stringify(o.extra)
})

const AWG_CONF = `[Interface]
PrivateKey = ${key32()}
Address = 10.8.0.2/32
Jc = 4
Jmin = 40
Jmax = 70
[Peer]
PublicKey = ${key32()}
Endpoint = awg.example.com:51820
AllowedIPs = 0.0.0.0/0
`

const profiles = [
  P({ protocol: 'tuic', uuid: crypto.randomUUID(), password: 'pw', method: 'bbr', mode: 'quic', sni: 'tuic.example.com', alpn: 'h3' }),
  P({ protocol: 'tuic', uuid: crypto.randomUUID(), method: 'weird', mode: 'other', host: 'cdn.example.com', allowInsecure: true }),
  P({ protocol: 'hysteria', password: 'auth', hyUpMbps: 20, hyDownMbps: 100, hyObfsPassword: 'obfs-secret', sni: 'hy.example.com' }),
  P({ protocol: 'hysteria', hyObfs: 'xplus', alpn: 'hysteria' }),
  P({ protocol: 'anytls', password: 'pw', sni: 'any.example.com', fingerprint: 'firefox' }),
  P({ protocol: 'naive', uuid: 'user', password: 'pw', mode: 'quic' }),
  P({ protocol: 'shadowtls', password: 'stls-pw', alterId: 3, sni: 'www.microsoft.com', extra: { ss: { method: '2022-blake3-aes-128-gcm', password: crypto.randomBytes(16).toString('base64') } } }),
  P({ protocol: 'shadowtls', password: 'stls-pw', alterId: 2, fingerprint: '', extra: { ss: { method: 'aes-256-gcm', password: 'x' } } }),
  P({ protocol: 'ssh', port: 22, uuid: 'admin', password: 'pw', publicKey: sshHostKey }),
  P({ protocol: 'ssh', port: 22, privateKey: sshPrivate }),
  ...['payload', 'http-proxy', 'https-proxy', 'tls', 'payload-tls', 'ws', 'wss'].map((mode) => P({
    protocol: 'ssh', port: 22, uuid: 'u', password: 'p', publicKey: sshHostKey,
    extra: { transport: { mode, proxyHost: mode.includes('proxy') || mode.startsWith('ws') ? 'front.example.com' : '', sni: 'sni.example.com',
      payload: mode.startsWith('payload') ? 'GET / HTTP/1.1[crlf]Host: [host][crlf]Upgrade: websocket[crlf][crlf]' : '',
      wsPath: mode.startsWith('ws') ? '/ssh' : '', wsHost: mode.startsWith('ws') ? 'cdn.example.com' : '', wsFraming: mode === 'wss', verify: mode === 'tls' } }
  })),
  P({ protocol: 'snell', password: 'psk', alterId: 4, hyObfs: 'http', host: 'bing.com' }),
  P({ protocol: 'snell', password: 'psk', alterId: 6, hyObfs: 'tls', host: 'ignored.example.com' }),
  ...['anyconnect', 'gp', 'fortinet', 'f5', 'pulse', 'nc', ''].map((mode) => P({
    protocol: 'openconnect', port: mode === 'gp' ? 8443 : 443, mode, uuid: 'vpnuser', password: 'pw', mtu: 1300, allowInsecure: mode === 'f5',
    sni: 'vpn.example.com', pinnedCertSha256: mode === 'anyconnect' ? 'pin-sha256:' + crypto.randomBytes(32).toString('base64') : '',
    extra: { authGroup: 'Staff', reportedOs: 'win', userAgent: 'AnyConnect Windows 4.10', reconnect: 30, noUdp: mode === 'pulse', ipv6Off: true }
  })),
  P({ protocol: 'masque', uuid: 'u', password: 'p', path: '/.well-known/masque/ip/*/*/', mode: '3', mtu: 1400, alpn: 'h3', pinnedCertSha256: HEX64 }),
  P({ protocol: 'masque', mode: '2', fingerprint: '', allowInsecure: true }),
  P({ protocol: 'tailscale', password: 'tskey-auth-xyz', host: 'https://headscale.example.com', sni: 'my-laptop', path: '100.64.0.1', headerType: 'ephemeral,routes,lan' }),
  P({ protocol: 'tailscale', password: '', host: '' }),
  P({ protocol: 'tailcat', privateKey: key32(), publicKey: key32(), uuid: key32(), password: key32(), host: 'https://derp.example.com/derpmap.json', mode: '901' }),
  P({ protocol: 'tailcat', publicKey: key32(), uuid: key32(), mode: '0' }),
  P({ protocol: 'amneziawg', extra: { conf: AWG_CONF } }),
  P({ protocol: 'mieru', extra: { url: 'mierus://user:pass@mieru.example.com?profile=default&port=8964&protocol=TCP' } }),
  P({ protocol: 'brook', extra: { url: 'brook://server?server=brook.example.com%3A9999&password=hello' } }),
  P({ protocol: 'juicity', uuid: crypto.randomUUID(), password: 'pw', sni: 'j.example.com', method: 'bbr', pinnedCertSha256: HEX64 }),
  P({ protocol: 'sstp', uuid: 'vpnuser', password: 'pw', method: 'mschapv2', sni: 'sstp.example.com', pinnedCertSha256: HEX64, mtu: 1350 }),
  P({ protocol: 'softether', port: 5555, uuid: 'u', password: 'p', sni: 'se.example.com', allowInsecure: true, mtu: 9999,
    extra: { hub: 'VPN', plain: true, ip: '10.0.0.2/24', gw: '10.0.0.1', dns: '1.1.1.1' } }),
  P({ protocol: 'dnstt', address: '8.8.8.8', port: 53, host: 't.example.com', publicKey: PUBKEY, method: 'ssh', uuid: 'root', password: 'pw' }),
  P({ protocol: 'dnstt', address: 'dns.google', port: 0, mode: 'doh', host: 't.example.com', publicKey: PUBKEY }),
  P({ protocol: 'vaydns', address: '1.1.1.1', port: 853, mode: 'dot', host: 'v.example.com', publicKey: PUBKEY, uuid: 'su', password: 'sp',
    extra: { recordType: 'txt', maxQnameLen: 101, clientIdSize: 8 } }),
  P({ protocol: 'noizdns', address: '9.9.9.9', port: 53, host: 'n.example.com', publicKey: PUBKEY, extra: { stealth: true } }),
  P({ protocol: 'slipstream', address: '8.8.4.4', port: 53, host: 's.example.com', method: 'ssh', privateKey: sshPrivate,
    extra: { authoritative: '203.0.113.5:53', cc: 'bbr', cert: '-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----\n' } }),
  P({ protocol: 'masterdns', address: '8.8.8.8', port: 53, host: 'm.example.com', password: 'enc-key', extra: { enc: 2, resolvers: '1.1.1.1:53, 9.9.9.9' } }),
  P({ protocol: 'stormdns', address: '8.8.8.8', port: 0, host: 'st.example.com,st2.example.com', password: 'k' }),
  P({ protocol: 'cottendns', address: '', port: 0, mode: 'doh', host: 'c.example.com', password: 'k', extra: { resolvers: 'https://dns.example/dns-query' } })
]

// ------------------------------------------------------------------ every protocol

const covered = new Set()
let port = 21000
for (const cfg of profiles) {
  const label = `${cfg.protocol}${cfg.mode ? '/' + cfg.mode : ''}${cfg.extra && JSON.parse(cfg.extra).transport ? '/' + JSON.parse(cfg.extra).transport.mode : ''}`
  test(label, () => {
    assert.ok(sb.handles(cfg), label + ' handled')
    const spec = sb.buildSingboxSpec(cfg)
    const proxy = spec.outbound || spec.endpoint
    assert.equal(proxy.tag, 'proxy')
    assert.deepEqual(sb.buildSingboxOutbound(cfg), proxy)
    let outbound = spec
    if (spec.sidecar) {
      const localPort = port++
      const rpcPort = port++
      const plan = sb.sidecarPlan(cfg, { localPort, rpcPort, dir: join(work, 'side') })
      assert.ok(plan, 'sidecar plan')
      assert.equal(plan.outbound.server, '127.0.0.1')
      assert.equal(plan.outbound.server_port, localPort)
      assert.ok(!plan.args.some((a) => /\{port2?\}|\{dir\}/.test(a)), 'placeholders filled')
      outbound = [plan.outbound, ...(spec.extraOutbounds || [])]
    }
    covered.add(cfg.protocol)
    if (cfg.protocol === 'naive') {
      assert.deepEqual(proxy, { tag: 'proxy', type: 'naive', server: 'example.com', server_port: 443, username: 'user', password: 'pw',
        tls: { enabled: true, server_name: 'example.com' }, quic: true })
      results.push(`skip ${label}: test binary has no with_naive_outbound (Cronet)`)
      return
    }
    if (cfg.protocol === 'tailcat' && !proxy.derp_region) {
      // Same output as the Kotlin builder (derp_region only from a positive
      // mode), which this sing-box refuses: a Tailcat profile needs a region.
      checkRejected(`${label} rejected without derp_region (as on Android)`, sb.buildProxyOnlyConfig({ outbound, socksPort: 10808 }), /derp_region/)
      return
    }
    check(`${label} proxy-only`, sb.buildProxyOnlyConfig({ outbound, socksPort: 10808, httpPort: 10809, settings: { iranDirect: true } }))
    check(`${label} tun`, sb.buildTunConfig({ outbound, settings: { iranDirect: true, iranRuleSet: irSrs }, platform: 'linux' }))
  })
}
for (const p of sb.PROTOCOLS) if (!covered.has(p)) { failures++; console.error('FAIL protocol not covered: ' + p) }

// ------------------------------------------------------------------ outbound shapes (Kotlin parity)

test('outbound shapes', () => {
  const tuic = sb.buildSingboxOutbound(profiles[0])
  assert.deepEqual(tuic, { tag: 'proxy', type: 'tuic', server: 'example.com', server_port: 443, uuid: profiles[0].uuid, password: 'pw',
    congestion_control: 'bbr', udp_relay_mode: 'quic', tls: { enabled: true, server_name: 'tuic.example.com', alpn: ['h3'] } })
  assert.equal(sb.buildSingboxOutbound(profiles[1]).congestion_control, undefined)
  assert.equal(sb.buildSingboxOutbound(profiles[1]).tls.server_name, 'cdn.example.com')
  const hy = sb.buildSingboxOutbound(profiles[3])
  assert.equal(hy.up_mbps, 10); assert.equal(hy.down_mbps, 50); assert.equal(hy.obfs, undefined)
  assert.equal(sb.buildSingboxOutbound(profiles[2]).obfs, 'obfs-secret')
  assert.deepEqual(sb.buildSingboxOutbound(profiles[4]).tls.utls, { enabled: true, fingerprint: 'firefox' })
  const stls = sb.buildSingboxSpec(profiles[6])
  assert.equal(stls.outbound.type, 'shadowsocks'); assert.equal(stls.outbound.detour, 'shadowtls-out')
  assert.equal(stls.extraOutbounds[0].version, 3); assert.equal(stls.extraOutbounds[0].tls.server_name, 'www.microsoft.com')
  assert.equal(sb.buildSingboxSpec(profiles[7]).extraOutbounds[0].tls.utls, undefined)
  assert.throws(() => sb.buildProxyOnlyConfig({ outbound: stls.outbound, socksPort: 1080 }), /shadowtls-out/)
  const snell4 = profiles.find((p) => p.protocol === 'snell' && p.alterId === 4)
  assert.deepEqual(sb.buildSingboxOutbound(snell4), { tag: 'proxy', type: 'snell', server: 'example.com', server_port: 443, psk: 'psk', version: 4, obfs_mode: 'http', obfs_host: 'bing.com' })
  const snell6 = sb.buildSingboxOutbound(profiles.find((p) => p.protocol === 'snell' && p.alterId === 6))
  assert.equal(snell6.version, 6); assert.equal(snell6.obfs_mode, undefined)
  const gp = sb.buildSingboxSpec(profiles.find((p) => p.protocol === 'openconnect' && p.mode === 'gp')).endpoint
  assert.equal(gp.server, 'example.com:8443'); assert.equal(gp.flavor, 'gp'); assert.equal(gp.reconnect_timeout, '30s')
  assert.equal(sb.buildSingboxOutbound(profiles.find((p) => p.protocol === 'openconnect' && p.mode === '')).flavor, 'anyconnect')
  const masque = sb.buildSingboxSpec(profiles.find((p) => p.protocol === 'masque' && p.mode === '3')).endpoint
  assert.equal(masque.type, 'masque-client'); assert.equal(masque.version, 3)
  assert.deepEqual(masque.tls.certificate_sha256, [Buffer.from(HEX64, 'hex').toString('base64')])
  const ts = sb.buildSingboxSpec(profiles.find((p) => p.protocol === 'tailscale' && p.password)).endpoint
  assert.match(ts.state_directory, /^tailscale-[0-9a-f]{12}$/)
  assert.equal(ts.ephemeral, true); assert.equal(ts.accept_routes, true); assert.equal(ts.exit_node_allow_lan_access, true)
  assert.equal(ts.hostname, 'my-laptop'); assert.equal(ts.control_url, 'https://headscale.example.com')
  const tc = sb.buildSingboxOutbound(profiles.find((p) => p.protocol === 'tailcat' && p.mode === '901'))
  assert.equal(tc.derp_region, 901)
  assert.equal(sb.buildSingboxOutbound(profiles.find((p) => p.protocol === 'tailcat' && p.mode === '0')).derp_region, undefined)
  const plainSsh = sb.buildSingboxOutbound(profiles[8])
  assert.deepEqual(plainSsh, { tag: 'proxy', type: 'ssh', server: 'example.com', server_port: 22, user: 'admin', password: 'pw', host_key: [sshHostKey] })
  assert.equal(sb.buildSingboxOutbound(profiles[9]).user, 'root')
  assert.equal(sb.sidecarSpec(profiles[8]), null)
  assert.equal(sb.handles(P({ protocol: 'vless' })), false)
  assert.equal(sb.buildSingboxSpec(P({ protocol: 'vless' })), null)
  assert.throws(() => sb.buildSingboxOutbound(P({ protocol: 'vless' })), /not a sing-box protocol/)
})

// ------------------------------------------------------------------ sidecar command lines (SidecarRunner.kt parity)

test('sidecar command lines', () => {
  const D = '/w'
  const plan = (proto, pred, prefs) => sb.sidecarPlan(profiles.find((p) => p.protocol === proto && (!pred || pred(p))), { localPort: 4000, rpcPort: 4001, dir: D }, prefs)
  const j = (...a) => join(...a)

  let p = plan('dnstt', (c) => c.method === 'ssh')
  assert.equal(p.binary, 'dnstt')
  assert.deepEqual(p.args, ['-udp', '8.8.8.8:53', '-pubkey', 'ab'.repeat(32), 't.example.com', '127.0.0.1:4000'])
  assert.equal(p.outbound.type, 'ssh'); assert.equal(p.socks, false)
  p = plan('dnstt', (c) => c.mode === 'doh')
  assert.deepEqual(p.args.slice(0, 2), ['-doh', 'https://dns.google/dns-query'])
  assert.equal(p.outbound.type, 'socks')

  p = plan('vaydns')
  assert.deepEqual(p.args, ['-dot', '1.1.1.1:853', '-pubkey', 'ab'.repeat(32), '-domain', 'v.example.com', '-listen', '127.0.0.1:4000',
    '-record-type', 'txt', '-max-qname-len', '101', '-clientid-size', '8'])
  assert.equal(p.outbound.username, 'su')

  p = plan('noizdns')
  assert.deepEqual(p.args, ['-udp', '9.9.9.9:53', '-pubkey', 'ab'.repeat(32), '-noiz', '-stealth', 'n.example.com', '127.0.0.1:4000'])

  p = plan('slipstream')
  assert.deepEqual(p.args, ['--tcp-listen-host', '127.0.0.1', '--tcp-listen-port', '4000', '--resolver', '8.8.4.4:53', '--domain', 's.example.com',
    '--authoritative', '203.0.113.5:53', '-c', 'bbr', '--cert', j(D, 'server.pem')])
  assert.deepEqual(p.secretFiles, [])
  assert.throws(() => sb.sidecarPlan(P({ protocol: 'slipstream', address: '1.1.1.1', mode: 'dot', host: 'x.y', publicKey: PUBKEY }), 4000), /plain UDP DNS only/)

  p = plan('masterdns')
  assert.equal(p.binary, 'masterdns')
  assert.deepEqual(p.args, ['-config', j(D, 'client.toml'), '-resolvers', j(D, 'resolvers.txt')])
  assert.equal(p.files['client.toml'], 'DOMAINS = ["m.example.com"]\nDATA_ENCRYPTION_METHOD = 2\nENCRYPTION_KEY = "enc-key"\nPROTOCOL_TYPE = "SOCKS5"\nLISTEN_IP = "127.0.0.1"\nLISTEN_PORT = 4000\nLOCAL_DNS_ENABLED = false\n')
  assert.equal(p.files['resolvers.txt'], '8.8.8.8:53\n1.1.1.1:53\n9.9.9.9\n')
  assert.equal(p.readyTimeoutMs, 90000); assert.deepEqual(p.secretFiles, ['client.toml'])
  p = plan('stormdns', null, { workers: 4, duplication: 2, keepSlowResolvers: true, pool: ['4.4.4.4'] })
  assert.match(p.files['client.toml'], /DOMAINS = \["st.example.com", "st2.example.com"\]/)
  assert.match(p.files['client.toml'], /RX_TX_WORKERS = 4\nTUNNEL_PROCESS_WORKERS = 4\nUPLOAD_PACKET_DUPLICATION_COUNT = 2\nAUTO_DISABLE_TIMEOUT_SERVERS = false\n/)
  assert.equal(p.files['resolvers.txt'], '4.4.4.4\n')
  p = plan('cottendns')
  assert.match(p.files['client.toml'], /RESOLVER_TRANSPORT = "doh"\n$/)

  p = plan('ssh', (c) => c.extra.includes('"payload-tls"'))
  assert.equal(p.binary, 'ghajar-helper')
  assert.deepEqual(p.args, ['sshtransport', '-listen', '4000', '-mode', 'payload-tls', '-host', 'example.com', '-port', '22', '-sni', 'sni.example.com',
    '-payload', Buffer.from('GET / HTTP/1.1[crlf]Host: [host][crlf]Upgrade: websocket[crlf][crlf]').toString('base64')])
  assert.equal(p.outbound.type, 'ssh'); assert.deepEqual(p.outbound.host_key, [sshHostKey])
  p = plan('ssh', (c) => c.extra.includes('"wss"'))
  assert.deepEqual(p.args, ['sshtransport', '-listen', '4000', '-mode', 'wss', '-host', 'example.com', '-port', '22', '-proxy', 'front.example.com:443',
    '-sni', 'sni.example.com', '-ws-path', '/ssh', '-ws-host', 'cdn.example.com', '-ws-framing'])
  p = plan('ssh', (c) => c.extra.includes('"http-proxy"'))
  assert.ok(p.args.includes('front.example.com:80'))

  p = plan('amneziawg')
  assert.deepEqual(p.args, ['awg', '-listen', '4000', '-config', j(D, 'awg.conf')])
  assert.equal(p.files['awg.conf'], AWG_CONF); assert.equal(p.env.HOME, D)
  assert.throws(() => sb.sidecarPlan(P({ protocol: 'amneziawg', extra: { conf: '[Interface]' } }), 4000), /\[Peer\]/)

  p = plan('mieru')
  assert.deepEqual(p.args, ['mieru', '-listen', '4000', '-rpc', '4001', '-url', JSON.parse(profiles.find((c) => c.protocol === 'mieru').extra).url, '-dir', D])
  assert.equal(p.readyTimeoutMs, 20000)
  assert.throws(() => sb.sidecarPlan(profiles.find((c) => c.protocol === 'mieru'), 4000), /rpcPort/)

  p = plan('brook')
  assert.deepEqual(p.args.slice(0, 3), ['brook', '-listen', '4000'])

  p = plan('juicity')
  assert.equal(p.binary, 'juicity')
  assert.deepEqual(p.args, ['run', '-c', j(D, 'juicity.json')])
  assert.deepEqual(JSON.parse(p.files['juicity.json']), { listen: '127.0.0.1:4000', server: 'example.com:443', uuid: profiles.find((c) => c.protocol === 'juicity').uuid,
    password: 'pw', sni: 'j.example.com', allow_insecure: false, congestion_control: 'bbr', log_level: 'info', pinned_certchain_sha256: HEX64 })

  p = plan('sstp')
  assert.deepEqual(p.args, ['sstp', '-listen', '4000', '-server', 'example.com:443', '-user', 'vpnuser', '-auth', 'mschapv2', '-mtu', '1350',
    '-sni', 'sstp.example.com', '-pin', HEX64])
  assert.equal(p.env.SSTP_PASSWORD_FILE, j(D, 'sstp.pass')); assert.equal(p.files['sstp.pass'], 'pw'); assert.equal(p.readyTimeoutMs, 30000)

  p = plan('softether')
  assert.deepEqual(p.args, ['softether', '-listen', '4000', '-server', 'example.com:5555', '-hub', 'VPN', '-user', 'u', '-mtu', '1400',
    '-plain', '-sni', 'se.example.com', '-insecure', '-ip', '10.0.0.2/24', '-gw', '10.0.0.1', '-dns', '1.1.1.1'])
  assert.equal(p.env.SE_PASSWORD_FILE, j(D, 'se.pass'))

  // DNS-protocol resolver override (DnsTunnelPrefs.applyTo).
  p = plan('dnstt', (c) => c.method === 'ssh', { overrideResolver: 'https://doh.example/dns-query', overrideTransport: 'doh' })
  assert.deepEqual(p.args.slice(0, 2), ['-doh', 'https://doh.example/dns-query'])
  p = plan('slipstream', null, { overrideResolver: '1.0.0.1:853', overrideTransport: 'dot' })
  assert.equal(p.args[5], '8.8.4.4:53')

  // Placeholders survive when no dir is given; executable names per OS.
  const bare = sb.sidecarPlan(profiles.find((c) => c.protocol === 'sstp'), 4000)
  assert.equal(bare.env.SSTP_PASSWORD_FILE, '{dir}/sstp.pass')
  assert.equal(sb.executableName('ghajar-helper', 'win32'), 'ghajar-helper.exe')
  assert.equal(sb.executableName('dnstt', 'darwin'), 'dnstt')
  assert.equal(sb.sidecarPlan(profiles[0], 4000), null)
})

// ------------------------------------------------------------------ TUN variants

test('tun variants', () => {
  const tuic = sb.buildSingboxSpec(profiles[0])

  // (a) Xray / Psiphon / Tor upstream over SOCKS5.
  const plain = sb.buildTunConfig({ socksPort: 10808, settings: { serverHosts: ['my-server.example.com'] }, platform: 'linux' })
  const tun = plain.inbounds[0]
  assert.deepEqual(tun.address, ['172.19.0.1/30', 'fdfe:dcba:9876::1/126'])
  assert.equal(tun.interface_name, 'ghajar-tun'); assert.equal(tun.auto_route, true); assert.equal(tun.strict_route, true); assert.equal(tun.mtu, 9000)
  assert.equal(tun.stack, undefined)
  assert.deepEqual(plain.outbounds[0], { type: 'socks', tag: 'proxy', server: '127.0.0.1', server_port: 10808, version: '5' })
  assert.equal(plain.route.final, 'proxy'); assert.equal(plain.route.auto_detect_interface, true)
  assert.ok(plain.route.rules.find((r) => r.process_name && r.process_name.includes('xray') && r.outbound === 'direct'))
  assert.ok(plain.dns.rules.find((r) => r.domain && r.domain.includes('my-server.example.com') && r.server === 'local'))
  assert.equal(plain.dns.servers[0].type, 'https')
  check('tun socks linux', plain)

  // Windows: ".exe" names; macOS: no interface name (utunN only).
  const win = sb.buildTunConfig({ socksPort: 10808, platform: 'win32' })
  assert.ok(win.route.rules.some((r) => r.process_name && r.process_name.includes('xray.exe') && r.process_name.includes('ghajar-helper.exe')))
  check('tun socks win32', win)
  const mac = sb.buildTunConfig({ socksPort: 10808, platform: 'darwin' })
  assert.equal(mac.inbounds[0].interface_name, undefined)
  check('tun socks darwin', mac)

  // Iran direct: domain suffix only, then with the local rule-set.
  const ir1 = sb.buildTunConfig({ socksPort: 10808, settings: { iranDirect: true }, platform: 'linux' })
  assert.equal(ir1.route.rule_set, undefined)
  assert.ok(ir1.route.rules.some((r) => r.domain_suffix && r.domain_suffix[0] === '.ir'))
  check('tun iran domain-only', ir1)
  const ir2 = sb.buildTunConfig({ socksPort: 10808, settings: { iranDirect: true, iranRuleSet: irSrs }, platform: 'linux' })
  assert.deepEqual(ir2.route.rule_set, [{ type: 'local', tag: 'geoip-ir', format: 'binary', path: irSrs }])
  check('tun iran rule-set (.srs)', ir2)
  check('tun iran rule-set (source .json)', sb.buildTunConfig({ socksPort: 10808, settings: { iranDirect: true, iranRuleSet: irSource }, platform: 'linux' }))

  // Per-app include: only the listed apps are proxied; the cores still go direct first.
  const inc = sb.buildTunConfig({ outbound: tuic, settings: { iranDirect: true, perApp: { mode: 'include', processes: ['chrome.exe', 'firefox', 'Google Chrome', '/usr/bin/curl'] } }, platform: 'linux' })
  assert.equal(inc.route.final, 'direct')
  const incRules = inc.route.rules
  const coreIdx = incRules.findIndex((r) => r.process_name && r.process_name.includes('xray'))
  const incIdx = incRules.findIndex((r) => r.process_name && r.process_name.includes('firefox'))
  assert.ok(coreIdx >= 0 && incIdx > coreIdx && incRules[incIdx].outbound === 'proxy')
  assert.ok(incRules.some((r) => r.process_path && r.process_path[0] === '/usr/bin/curl' && r.outbound === 'proxy'))
  check('tun per-app include linux', inc)
  const incMac = sb.buildTunConfig({ socksPort: 10808, settings: { perApp: { mode: 'include', processes: ['Google Chrome', 'Firefox.app'] } }, platform: 'darwin' })
  const rx = incMac.route.rules.find((r) => r.process_path_regex)
  assert.deepEqual(rx.process_path_regex, ['(?i)/Google Chrome\\.app/', '(?i)/Firefox\\.app/'])
  assert.equal(rx.outbound, 'proxy')
  check('tun per-app include darwin', incMac)
  const incWin = sb.buildTunConfig({ socksPort: 10808, settings: { perApp: { mode: 'include', processes: ['chrome', 'Telegram.exe'] } }, platform: 'win32' })
  assert.ok(incWin.route.rules.some((r) => r.process_name && r.process_name.join() === 'chrome.exe,Telegram.exe' && r.outbound === 'proxy'))
  check('tun per-app include win32', incWin)

  // Per-app exclude: the listed apps go direct, everything else is proxied.
  const exc = sb.buildTunConfig({ socksPort: 10808, settings: { perApp: { mode: 'exclude', processes: ['steam', 'Spotify'] } }, platform: 'darwin' })
  assert.equal(exc.route.final, 'proxy')
  assert.ok(exc.route.rules.some((r) => r.process_name && r.process_name.includes('steam') && r.outbound === 'direct'))
  assert.ok(exc.route.rules.some((r) => r.process_path_regex && r.outbound === 'direct'))
  check('tun per-app exclude darwin', exc)
  check('tun per-app exclude linux (sing-box outbound)', sb.buildTunConfig({ outbound: tuic, settings: { perApp: { mode: 'exclude', processes: ['steam'] } }, platform: 'linux' }))
  // An empty include list falls back to the whole device rather than proxying nothing.
  assert.equal(sb.buildTunConfig({ socksPort: 10808, settings: { perApp: { mode: 'include', processes: [] } }, platform: 'linux' }).route.final, 'proxy')

  // Remote DNS forms, MTU and an explicit stack.
  const udpDns = sb.buildTunConfig({ socksPort: 10808, settings: { remoteDns: '8.8.8.8' }, platform: 'linux' })
  assert.deepEqual(udpDns.dns.servers[0], { type: 'udp', tag: 'remote', detour: 'proxy', server: '8.8.8.8' })
  check('tun remote dns udp', udpDns)
  const dohName = sb.buildTunConfig({ socksPort: 10808, settings: { remoteDns: 'https://dns.google/dns-query' }, platform: 'linux' })
  assert.equal(dohName.dns.servers[0].domain_resolver, 'local')
  check('tun remote dns doh by name', dohName)
  check('tun remote dns dot', sb.buildTunConfig({ socksPort: 10808, settings: { remoteDns: 'tls://1.1.1.1' }, platform: 'linux' }))
  const sys = sb.buildTunConfig({ socksPort: 10808, settings: { stack: 'system', mtu: 1500 }, platform: 'linux' })
  assert.equal(sys.inbounds[0].stack, 'system'); assert.equal(sys.inbounds[0].mtu, 1500)
  check('tun stack system mtu 1500', sys)

  // Endpoint protocols (openconnect, masque, tailscale) land in "endpoints".
  const ep = sb.buildTunConfig({ outbound: sb.buildSingboxSpec(profiles.find((p) => p.protocol === 'masque')), platform: 'linux' })
  assert.equal(ep.endpoints[0].type, 'masque-client'); assert.equal(ep.outbounds.length, 1)
  assert.throws(() => sb.buildTunConfig({}), /socksPort or outbound/)
})

// ------------------------------------------------------------------ proxy-only variants

test('proxy-only variants', () => {
  const tuic = sb.buildSingboxSpec(profiles[0])
  const a = sb.buildProxyOnlyConfig({ outbound: tuic, socksPort: 10808, httpPort: 10809 })
  assert.deepEqual(a.inbounds, [
    { type: 'mixed', tag: 'mixed-in', listen: '127.0.0.1', listen_port: 10808 },
    { type: 'http', tag: 'http-in', listen: '127.0.0.1', listen_port: 10809 }])
  assert.equal(a.dns.final, undefined)
  check('proxy-only mixed+http', a)
  const b = sb.buildProxyOnlyConfig({ outbound: tuic, socksPort: 10808, httpPort: 10808, settings: { remoteDns: '1.1.1.1', iranDirect: true, iranRuleSet: irSrs } })
  assert.equal(b.inbounds.length, 1); assert.equal(b.dns.final, 'remote')
  assert.ok(b.route.rules.some((r) => r.action === 'hijack-dns'))
  check('proxy-only remote dns + iran rule-set', b)
  check('proxy-only array outbound', sb.buildProxyOnlyConfig({ outbound: sb.singboxOutbounds(profiles[6]), socksPort: 10808 }))
  assert.throws(() => sb.buildProxyOnlyConfig({ outbound: tuic }), /socksPort/)
})

// ------------------------------------------------------------------ report

console.log(results.join('\n'))
console.log(`\n${checks} sing-box checks, ${failures} failure(s); protocols covered: ${[...covered].sort().join(', ')}`)
if (!failures) rmSync(work, { recursive: true, force: true })
process.exit(failures ? 1 : 0)
