// Tests for engine/xray.js: structure checks against ConfigBuilder.kt's
// behaviour, validation of every generated config with the real core
// (`xray run -test`), and an end-to-end delay test through one Xray instance.
//
//   node web-desktop/engine/test/xray.test.mjs
//
// XRAY_BIN (default /tmp/claude-0/cores/xray/xray) and XRAY_ASSETS (default:
// the binary's directory) select the core. The stock geosite.dat lacks
// geosite:ads (only in the Chocolate4U Iran-v2ray-rules file the Android app
// bundles), so against stock assets that one rule is removed from the copy
// under test; when app/src/main/assets has the Iran files, the configs are
// also validated unmodified against them.

import { createRequire } from 'node:module'
import { spawn, spawnSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import http from 'node:http'
import net from 'node:net'
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const here = path.dirname(fileURLToPath(import.meta.url))
const X = require(path.join(here, '..', 'xray.js'))
const {
  DEFAULT_SETTINGS, IRAN_ONLY_GEO_RULES, buildXrayConfig, buildChainToSocks, buildDelayTestConfig,
  measureDelay, supportsXray, buildNoises, buildMaskNoise, _internal
} = X

const XRAY_BIN = process.env.XRAY_BIN || '/tmp/claude-0/cores/xray/xray'
const STOCK_ASSETS = process.env.XRAY_ASSETS || path.dirname(XRAY_BIN)
const APP_ASSETS = path.resolve(here, '..', '..', '..', 'app', 'src', 'main', 'assets')
const TMP = fs.mkdtempSync(path.join(os.tmpdir(), 'xray-test-'))

let passed = 0
let failed = 0
const failures = []
function check(name, fn) {
  try { fn(); passed++ } catch (e) { failed++; failures.push(`${name}: ${e.message}`) }
}

// ------------------------------------------------------------------ fixtures

const UUID = '8ad19672-65b5-492c-a9cf-09f5c3f8b3be'
const REALITY_PUB = 'jr7hQAfwYemzbSqh8XuODhvZEdw1rV86gWVCIHGaFAY'
const WG_PRIV = '0F4W3x12LPXKb+DoFRQLgaLiQxBiqUP+Rs32RDtatUU='
const WG_PUB = 'MNpPQQf+S20aQ/x0ctqtYZjUFXhCMfkFlfUChPjT6D4='
const PIN = 'a'.repeat(64)
const PORTS = { socksPort: 10808, httpPort: 10809 }

const base = { name: 't', address: 'example.com', port: 443 }
const CONFIGS = {
  'vless-tcp-none': { ...base, protocol: 'vless', uuid: UUID, network: 'tcp', security: 'none' },
  'vless-raw-reality-vision': { ...base, protocol: 'vless', uuid: UUID, network: 'raw', security: 'reality', flow: 'xtls-rprx-vision', sni: 'www.microsoft.com', publicKey: REALITY_PUB, shortId: '6ba85179e30d4fc2', fingerprint: 'chrome' },
  'vless-reality-randomsub': { ...base, protocol: 'vless', uuid: UUID, security: 'reality', sni: 'cdn.example.com', publicKey: REALITY_PUB, shortId: 'ab', fingerprint: 'firefox', randomSubdomain: true },
  'vless-ws-tls': { ...base, protocol: 'vless', uuid: UUID, network: 'ws', security: 'tls', path: '/ws?ed=2048', host: 'cdn.example.com', sni: 'cdn.example.com', alpn: 'h2,http/1.1', fingerprint: 'safari' },
  'vless-websocket-alias': { ...base, protocol: 'vless', uuid: UUID, network: 'websocket', security: 'tls', host: 'a.example.com,b.example.com' },
  'vless-grpc-multi-tls': { ...base, protocol: 'vless', uuid: UUID, network: 'grpc', security: 'tls', serviceName: 'grpcsvc', mode: 'multi', sni: 'g.example.com' },
  'vless-grpc-gun-reality': { ...base, protocol: 'vless', uuid: UUID, network: 'grpc', security: 'reality', serviceName: 'svc', mode: 'gun', sni: 'www.apple.com', publicKey: REALITY_PUB, shortId: '' },
  'vless-xhttp-reality': { ...base, protocol: 'vless', uuid: UUID, network: 'xhttp', security: 'reality', path: '/xh', host: 'x.example.com', mode: 'stream-one', sni: 'www.apple.com', publicKey: REALITY_PUB, shortId: 'ab' },
  'vless-splithttp-tls': { ...base, protocol: 'vless', uuid: UUID, network: 'splithttp', security: 'tls', path: '/sh', mode: 'packet-up', alpn: 'h3' },
  'vless-httpupgrade-tls': { ...base, protocol: 'vless', uuid: UUID, network: 'httpupgrade', security: 'tls', path: '/up', host: 'up.example.com' },
  'vless-tcp-http-header': { ...base, protocol: 'vless', uuid: UUID, network: 'tcp', headerType: 'http', path: '/a,/b', host: 'fast.com,speedtest.net', port: 80 },
  'vless-kcp-seed': { ...base, protocol: 'vless', uuid: UUID, network: 'kcp', headerType: 'wechat-video', path: 'myseed' },
  'vless-mkcp-noheader': { ...base, protocol: 'vless', uuid: UUID, network: 'mkcp' },
  'vless-tls-all-options': { ...base, protocol: 'vless', uuid: UUID, network: 'tcp', security: 'tls', sni: 'a.example.com', alpn: 'h2', fingerprint: 'randomized', pinnedCertSha256: PIN + ',' + 'B'.repeat(64), cipherSuites: 'TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256:TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256', echConfigList: 'cloudflare-ech.com+https://1.1.1.1/dns-query', randomSubdomain: true },
  'vless-tls-sudoku-mask': { ...base, protocol: 'vless', uuid: UUID, network: 'tcp', security: 'tls', maskType: 'sudoku', maskPassword: 'secret' },
  'vless-kcp-xdns-mask': { ...base, protocol: 'vless', uuid: UUID, network: 'kcp', maskType: 'xdns', maskDomain: 't.example.com' },
  'vless-kcp-noise-mask': { ...base, protocol: 'vless', uuid: UUID, network: 'kcp', maskType: 'noise' },
  'vless-ws-noise-mask-ignored': { ...base, protocol: 'vless', uuid: UUID, network: 'ws', maskType: 'noise' },
  'vmess-ws-tls': { ...base, protocol: 'vmess', uuid: UUID, alterId: 0, encryption: 'auto', network: 'ws', security: 'tls', path: '/vm', host: 'vm.example.com' },
  'vmess-tcp-http-header': { ...base, protocol: 'vmess', uuid: UUID, encryption: '', network: 'tcp', headerType: 'http', host: 'a.com', port: 80 },
  'vmess-kcp-aes': { ...base, protocol: 'vmess', uuid: UUID, encryption: 'aes-128-gcm', network: 'kcp', headerType: 'srtp' },
  'vmess-grpc-none-sec': { ...base, protocol: 'vmess', uuid: UUID, network: 'grpc', serviceName: 's', security: 'tls' },
  'trojan-tcp-tls': { ...base, protocol: 'trojan', password: 'pw', network: 'tcp', security: 'tls', sni: 't.example.com' },
  'trojan-grpc-tls': { ...base, protocol: 'trojan', password: 'pw', network: 'grpc', serviceName: 'tg', security: 'tls', alpn: 'h2' },
  'trojan-ws-none': { ...base, protocol: 'trojan', password: 'pw', network: 'ws', path: '/tr' },
  'ss-aes-gcm': { ...base, protocol: 'shadowsocks', method: 'aes-256-gcm', password: 'pw', network: 'tcp' },
  'ss-2022': { ...base, protocol: 'shadowsocks', method: '2022-blake3-aes-128-gcm', password: 'AAAAAAAAAAAAAAAAAAAAAA==', network: 'tcp' },
  'ss-chacha-ws-tls': { ...base, protocol: 'shadowsocks', method: 'chacha20-ietf-poly1305', password: 'pw', network: 'ws', security: 'tls', host: 'p.example.com', path: '/p' },
  'socks-auth': { ...base, protocol: 'socks', port: 1080, uuid: 'user', password: 'pass' },
  'socks-noauth': { ...base, protocol: 'socks', port: 1080 },
  'http-auth': { ...base, protocol: 'http', port: 8080, uuid: 'user', password: 'pass' },
  'http-tls': { ...base, protocol: 'http', port: 443, security: 'tls' },
  'hy2-plain': { ...base, protocol: 'hysteria2', password: 'auth', sni: 'hy.example.com' },
  'hy2-obfs-bw-pin': { ...base, protocol: 'hysteria2', password: 'auth', hyObfs: 'salamander', hyObfsPassword: 'obfs', hyUpMbps: 50, hyDownMbps: 200, pinnedCertSha256: PIN, alpn: 'h3' },
  'hy2-obfs-noise-mask': { ...base, protocol: 'hysteria2', password: 'auth', hyObfsPassword: 'obfs', maskType: 'noise' },
  'hy2-xdns-mask': { ...base, protocol: 'hysteria2', password: 'auth', maskType: 'xdns', maskDomain: 't.example.com' },
  'wg-plain': { ...base, protocol: 'wireguard', address: '203.0.113.5', port: 51820, privateKey: WG_PRIV, publicKey: WG_PUB, localAddress: '10.0.0.2/32, fd00::2/128', mtu: 1420 },
  'wg-warp-reserved': { ...base, protocol: 'wireguard', address: 'engage.cloudflareclient.com', port: 2408, privateKey: WG_PRIV, publicKey: WG_PUB, localAddress: '172.16.0.2/32,2606:4700:110:8a36::2/128', reserved: '12, 34, 56', mtu: 1280 },
  'wg-bad-reserved': { ...base, protocol: 'wireguard', address: '203.0.113.5', port: 51820, privateKey: WG_PRIV, publicKey: WG_PUB, localAddress: '10.0.0.2/32', reserved: '1,2' }
}

const SETTINGS = {
  defaults: {},
  fragment: { fragment: true },
  'fragment-custom': { fragment: true, fragmentPackets: '1-3', fragmentLength: '100-200', fragmentInterval: '1-5' },
  'fragment-blank-values': { fragment: true, fragmentPackets: ' ', fragmentLength: '', fragmentInterval: '' },
  'noise-light': { noiseSpec: 'light' },
  'noise-standard': { noiseSpec: 'standard' },
  'noise-aggressive': { noiseSpec: 'aggressive' },
  'noise-quic': { noiseSpec: 'quic' },
  'noise-custom': { noiseSpec: 'rand:10-20:5-10\nstr:hello\nhex:deadbeef:1-2;base64:aGVsbG8=;bogus:1;rand' },
  'noise-off': { noiseSpec: 'off' },
  mux: { mux: true, muxConcurrency: 500 },
  'split-routing': { splitRouting: true },
  sniffing: { sniffing: true, sniffTypes: ['http', 'tls'] },
  'sniffing-fakedns-others': { sniffing: true, sniffTypes: ['fakedns+others'] },
  'ad-block': { adBlock: true },
  'youtube-direct': { youtubeDirect: true },
  'fake-dns': { fakeDns: true },
  'encrypted-dns': { encryptedDns: true },
  'custom-dns-doh': { customDns: 'https://dns.mullvad.net/dns-query' },
  'custom-dns-ip': { customDns: '178.22.122.100' },
  'direct-only': { directOnly: true, adBlock: true },
  'share-lan': { shareOnLan: true, shareUser: 'u', sharePass: 'p', shareListenAddress: '192.168.43.1' },
  everything: {
    fragment: true, noiseSpec: 'standard', mux: true, muxConcurrency: 16, splitRouting: true, sniffing: true,
    sniffTypes: ['http', 'tls', 'quic', 'fakedns'], adBlock: true, youtubeDirect: true, fakeDns: true,
    encryptedDns: true, customDns: 'https://1.1.1.1/dns-query', coreLogLevel: 'debug'
  }
}

// ------------------------------------------------------------------ xray -test

function stripIranOnly(cfg) {
  const copy = JSON.parse(JSON.stringify(cfg))
  for (const r of copy.routing?.rules || []) {
    if (Array.isArray(r.domain)) r.domain = r.domain.filter(d => !IRAN_ONLY_GEO_RULES.includes(d))
  }
  return copy
}

let fileSeq = 0
function xrayTestAsync(cfg, assetDir) {
  const file = path.join(TMP, `cfg-${++fileSeq}.json`)
  fs.writeFileSync(file, JSON.stringify(cfg, null, 1))
  return new Promise(resolve => {
    const p = spawn(XRAY_BIN, ['run', '-test', '-c', file], { env: { ...process.env, XRAY_LOCATION_ASSET: assetDir } })
    let out = ''
    p.stdout.on('data', d => { out += d })
    p.stderr.on('data', d => { out += d })
    p.on('close', code => resolve({ ok: code === 0 && /Configuration OK/.test(out), out, file }))
  })
}

async function pool(tasks, n) {
  const results = new Array(tasks.length)
  let i = 0
  await Promise.all(Array.from({ length: n }, async () => {
    while (i < tasks.length) { const k = i++; results[k] = await tasks[k]() }
  }))
  return results
}

// ------------------------------------------------------------------ unit checks

function unitChecks() {
  const S = (c, s, p = PORTS) => buildXrayConfig(c, s, p)
  const proxyOf = cfg => cfg.outbounds.find(o => o.tag === 'proxy')
  const rule = (cfg, pred) => cfg.routing.rules.find(pred)

  check('defaults mirror ConfigStore', () => {
    assert.equal(DEFAULT_SETTINGS.fragmentPackets, 'tlshello')
    assert.equal(DEFAULT_SETTINGS.fragmentLength, '10-20')
    assert.equal(DEFAULT_SETTINGS.muxConcurrency, 8)
    assert.deepEqual([...DEFAULT_SETTINGS.sniffTypes], ['http', 'tls', 'quic'])
    assert.equal(DEFAULT_SETTINGS.coreLogLevel, 'warning')
    for (const k of ['fragment', 'splitRouting', 'sniffing', 'mux', 'adBlock', 'fakeDns', 'encryptedDns', 'youtubeDirect', 'directOnly']) {
      assert.equal(DEFAULT_SETTINGS[k], false, k)
    }
  })

  check('default config: no dns, no sniffing, catch-all to proxy', () => {
    const c = S(CONFIGS['vless-tcp-none'], {})
    assert.equal(c.dns, undefined)
    assert.equal(c.fakedns, undefined)
    assert.deepEqual(c.inbounds.map(i => [i.tag, i.listen, i.port, i.protocol]), [['socks', '127.0.0.1', 10808, 'socks'], ['http', '127.0.0.1', 10809, 'http']])
    assert.equal(c.inbounds[0].settings.udp, true)
    assert.equal(c.inbounds[0].sniffing, undefined)
    assert.deepEqual(c.routing, { domainStrategy: 'AsIs', rules: [{ type: 'field', inboundTag: ['socks', 'http'], outboundTag: 'proxy' }] })
    assert.deepEqual(c.outbounds.map(o => o.tag), ['proxy', 'direct', 'block'])
    assert.deepEqual(c.outbounds[1], { tag: 'direct', protocol: 'freedom' })
    assert.equal(c.log.loglevel, 'warning')
  })

  check('logLevel from ports overrides coreLogLevel', () => {
    assert.equal(S(CONFIGS['vless-tcp-none'], { coreLogLevel: 'debug' }, { ...PORTS, logLevel: 'error' }).log.loglevel, 'error')
    assert.equal(S(CONFIGS['vless-tcp-none'], { coreLogLevel: 'debug' }).log.loglevel, 'debug')
  })

  check('vless user + reality', () => {
    const o = proxyOf(S(CONFIGS['vless-raw-reality-vision'], {}))
    assert.deepEqual(o.settings.vnext[0].users[0], { id: UUID, encryption: 'none', flow: 'xtls-rprx-vision' })
    assert.equal(o.streamSettings.network, 'tcp')
    assert.deepEqual(o.streamSettings.realitySettings, { serverName: 'www.microsoft.com', publicKey: REALITY_PUB, shortId: '6ba85179e30d4fc2', fingerprint: 'chrome', spiderX: '/' })
  })

  check('random subdomain', () => {
    const o = proxyOf(S(CONFIGS['vless-reality-randomsub'], {}))
    assert.match(o.streamSettings.realitySettings.serverName, /^[a-z0-9]{5,9}\.cdn\.example\.com$/)
    assert.equal(_internal.randomLabel(''), '')
  })

  check('vmess security fallback', () => {
    assert.equal(proxyOf(S(CONFIGS['vmess-tcp-http-header'], {})).settings.vnext[0].users[0].security, 'auto')
    assert.equal(proxyOf(S(CONFIGS['vmess-kcp-aes'], {})).settings.vnext[0].users[0].security, 'aes-128-gcm')
    // ProxyConfig.fromJson default for a missing encryption is "none".
    assert.equal(proxyOf(S({ ...base, protocol: 'vmess', uuid: UUID }, {})).settings.vnext[0].users[0].security, 'none')
  })

  check('tls options', () => {
    const t = proxyOf(S(CONFIGS['vless-tls-all-options'], {})).streamSettings.tlsSettings
    assert.match(t.serverName, /^[a-z0-9]{5,9}\.a\.example\.com$/)
    assert.equal(t.fingerprint, 'randomized')
    assert.deepEqual(t.alpn, ['h2'])
    assert.equal(t.pinnedPeerCertSha256, PIN + ',' + 'B'.repeat(64))
    assert.ok(t.cipherSuites)
    assert.equal(t.echConfigList, 'cloudflare-ech.com+https://1.1.1.1/dns-query')
    assert.equal(t.allowInsecure, undefined)
    const w = proxyOf(S(CONFIGS['vless-websocket-alias'], {})).streamSettings
    assert.equal(w.network, 'ws')
    assert.equal(w.tlsSettings.serverName, 'a.example.com') // first Host when no SNI
    assert.equal(proxyOf(S({ ...CONFIGS['vless-tcp-none'], security: 'tls', pinnedCertSha256: 'nothex' }, {})).streamSettings.tlsSettings.pinnedPeerCertSha256, undefined)
    assert.equal(proxyOf(S({ ...CONFIGS['vless-tcp-none'], security: 'tls' }, {})).streamSettings.tlsSettings.serverName, 'example.com')
  })

  check('transports', () => {
    const h = proxyOf(S(CONFIGS['vless-tcp-http-header'], {})).streamSettings.tcpSettings.header
    assert.equal(h.type, 'http')
    assert.deepEqual(h.request.path, ['/a', '/b'])
    assert.deepEqual(h.request.headers.Host, ['fast.com', 'speedtest.net'])
    assert.equal(h.request.headers.Pragma, 'no-cache')
    // Legacy kcp header/seed are emitted as their finalmask replacements.
    const k = proxyOf(S(CONFIGS['vless-kcp-seed'], {})).streamSettings
    assert.deepEqual(k.kcpSettings, {})
    assert.deepEqual(k.finalmask, { udp: [{ type: 'header-wechat' }, { type: 'mkcp-aes128gcm', settings: { password: 'myseed' } }] })
    const k2 = proxyOf(S(CONFIGS['vless-mkcp-noheader'], {})).streamSettings
    assert.equal(k2.network, 'kcp')
    assert.deepEqual(k2.finalmask, { udp: [{ type: 'mkcp-original' }] })
    assert.deepEqual(_internal.kcpMasks(_internal.normalizeConfig({ headerType: 'dns', host: 'q.example.com' })), [{ type: 'header-dns', settings: { domain: 'q.example.com' } }, { type: 'mkcp-original' }])
    assert.deepEqual(proxyOf(S(CONFIGS['vless-ws-tls'], {})).streamSettings.wsSettings, { path: '/ws?ed=2048', headers: { Host: 'cdn.example.com' } })
    assert.deepEqual(proxyOf(S(CONFIGS['vless-grpc-multi-tls'], {})).streamSettings.grpcSettings, { serviceName: 'grpcsvc', multiMode: true })
    assert.deepEqual(proxyOf(S(CONFIGS['vless-xhttp-reality'], {})).streamSettings.xhttpSettings, { path: '/xh', host: 'x.example.com', mode: 'stream-one' })
    assert.equal(proxyOf(S(CONFIGS['vless-splithttp-tls'], {})).streamSettings.network, 'xhttp')
    assert.deepEqual(proxyOf(S(CONFIGS['vless-httpupgrade-tls'], {})).streamSettings.httpupgradeSettings, { path: '/up', host: 'up.example.com' })
    // h2 (and legacy quic) were removed from the bundled core: unsupported, never built.
    const h2 = { ...base, protocol: 'vless', uuid: UUID, network: 'h2', security: 'tls' }
    assert.equal(supportsXray(h2), false)
    assert.throws(() => S(h2, {}), /unsupported transport/)
    assert.equal(supportsXray({ ...h2, network: 'http' }), false)
  })

  check('finalmask sides', () => {
    assert.deepEqual(proxyOf(S(CONFIGS['vless-tls-sudoku-mask'], {})).streamSettings.finalmask, { tcp: [{ type: 'sudoku', settings: { password: 'secret' } }] })
    assert.deepEqual(proxyOf(S(CONFIGS['vless-kcp-xdns-mask'], {})).streamSettings.finalmask, { udp: [{ type: 'xdns', settings: { domain: 't.example.com' } }, { type: 'mkcp-original' }] })
    assert.deepEqual(proxyOf(S(CONFIGS['vless-kcp-noise-mask'], {})).streamSettings.finalmask.udp[0].settings.noise, buildMaskNoise('standard'))
    const warnings = []
    const c = buildXrayConfig(CONFIGS['vless-ws-noise-mask-ignored'], {}, { ...PORTS, onWarning: w => warnings.push(w) })
    assert.equal(proxyOf(c).streamSettings.finalmask, undefined)
    assert.equal(warnings.length, 1)
    assert.equal(_internal.maskEntry({ maskType: 'xdns', maskDomain: ' ' }), null)
    assert.equal(_internal.maskEntry({ maskType: 'sudoku', maskPassword: '' }), null)
    assert.equal(_internal.maskEntry({ maskType: 'other' }), null)
  })

  check('hysteria2', () => {
    const o = proxyOf(S(CONFIGS['hy2-obfs-bw-pin'], {}))
    assert.equal(o.protocol, 'hysteria')
    assert.deepEqual(o.settings, { version: 2, address: 'example.com', port: 443 })
    assert.deepEqual(o.streamSettings.tlsSettings, { serverName: 'example.com', alpn: ['h3'], pinnedPeerCertSha256: PIN })
    assert.deepEqual(o.streamSettings.hysteriaSettings, { version: 2, auth: 'auth', udpIdleTimeout: 60, up: '50mbps', down: '200mbps' })
    assert.deepEqual(o.streamSettings.finalmask, { udp: [{ type: 'salamander', settings: { password: 'obfs' } }] })
    const n = proxyOf(S(CONFIGS['hy2-obfs-noise-mask'], {}))
    assert.deepEqual(n.streamSettings.finalmask.udp.map(m => m.type), ['salamander', 'noise'])
    assert.equal(proxyOf(S(CONFIGS['hy2-plain'], {})).streamSettings.finalmask, undefined)
    assert.equal(proxyOf(S(CONFIGS['hy2-plain'], {})).streamSettings.tlsSettings.serverName, 'hy.example.com')
  })

  check('wireguard + WARP', () => {
    const w = proxyOf(S(CONFIGS['wg-warp-reserved'], {}))
    assert.equal(w.settings.peers[0].endpoint, '162.159.192.1:2408')
    assert.deepEqual(w.settings.reserved, [12, 34, 56])
    assert.deepEqual(w.settings.address, ['172.16.0.2/32', '2606:4700:110:8a36::2/128'])
    assert.equal(w.settings.mtu, 1280)
    assert.equal(w.streamSettings, undefined)
    assert.equal(proxyOf(S(CONFIGS['wg-bad-reserved'], {})).settings.reserved, undefined)
    assert.equal(proxyOf(S(CONFIGS['wg-plain'], {})).settings.peers[0].endpoint, '203.0.113.5:51820')
  })

  check('socks/http users', () => {
    assert.deepEqual(proxyOf(S(CONFIGS['socks-auth'], {})).settings.servers[0].users, [{ user: 'user', pass: 'pass' }])
    assert.equal(proxyOf(S(CONFIGS['socks-noauth'], {})).settings.servers[0].users, undefined)
  })

  check('fragment', () => {
    const c = S(CONFIGS['vless-ws-tls'], { fragment: true })
    assert.deepEqual(proxyOf(c).streamSettings.sockopt, { dialerProxy: 'fragment' })
    assert.deepEqual(c.outbounds[1], { tag: 'fragment', protocol: 'freedom', settings: { fragment: { packets: 'tlshello', length: '10-20', interval: '10-20' } } })
    const b = S(CONFIGS['vless-ws-tls'], SETTINGS['fragment-blank-values'])
    assert.deepEqual(b.outbounds[1].settings.fragment, { packets: 'tlshello', length: '10-20', interval: '10-20' })
    // wireguard has no streamSettings: the fragment outbound exists but is not dialed through.
    const w = S(CONFIGS['wg-plain'], { fragment: true })
    assert.equal(proxyOf(w).streamSettings, undefined)
    assert.ok(w.outbounds.find(o => o.tag === 'fragment'))
  })

  check('chain base wins over fragment', () => {
    const c = buildXrayConfig({ ...CONFIGS['vless-ws-tls'], id: 'a' }, { fragment: true }, { ...PORTS, chainBase: { ...CONFIGS['trojan-tcp-tls'], id: 'b' } })
    assert.deepEqual(proxyOf(c).streamSettings.sockopt, { dialerProxy: 'chain' })
    assert.equal(c.outbounds.find(o => o.tag === 'chain').protocol, 'trojan')
    const self = buildXrayConfig({ ...CONFIGS['vless-ws-tls'], id: 'a' }, {}, { ...PORTS, chainBase: { ...CONFIGS['vless-ws-tls'], id: 'a' } })
    assert.equal(self.outbounds.find(o => o.tag === 'chain'), undefined)
  })

  check('mux clamp', () => {
    assert.deepEqual(proxyOf(S(CONFIGS['vless-ws-tls'], { mux: true, muxConcurrency: 500 })).mux, { enabled: true, concurrency: 128 })
    assert.deepEqual(proxyOf(S(CONFIGS['vless-ws-tls'], { mux: true, muxConcurrency: 0 })).mux, { enabled: true, concurrency: 8 })
    assert.deepEqual(proxyOf(S(CONFIGS['vless-ws-tls'], { mux: true, muxConcurrency: -3 })).mux, { enabled: true, concurrency: 1 })
  })

  check('noises', () => {
    assert.equal(buildNoises(''), null)
    assert.equal(buildNoises('off'), null)
    assert.deepEqual(buildNoises('light'), [{ type: 'rand', packet: '10-30', delay: '0-3', applyTo: 'ip' }])
    assert.equal(buildNoises('standard').length, 2)
    assert.equal(buildNoises('aggressive').length, 3)
    assert.deepEqual(buildNoises('quic'), [{ type: 'hex', packet: 'c00000000108', delay: '0-5', applyTo: 'ip' }])
    assert.deepEqual(buildNoises(SETTINGS['noise-custom'].noiseSpec), [
      { type: 'rand', packet: '10-20', delay: '5-10', applyTo: 'ip' },
      { type: 'str', packet: 'hello', applyTo: 'ip' },
      { type: 'hex', packet: 'deadbeef', delay: '1-2', applyTo: 'ip' },
      { type: 'base64', packet: 'aGVsbG8=', applyTo: 'ip' }
    ])
    assert.equal(buildNoises('nonsense'), null)
    const c = S(CONFIGS['vless-ws-tls'], { noiseSpec: 'light' })
    assert.deepEqual(c.outbounds.find(o => o.tag === 'direct').settings, { noises: buildNoises('light') })
    assert.deepEqual(buildMaskNoise('custom stuff'), buildMaskNoise('standard'))
    assert.equal(buildMaskNoise('light').length, 1)
  })

  check('sniffing semantics (tun-in -> socks, socks-in -> http)', () => {
    let c = S(CONFIGS['vless-ws-tls'], { splitRouting: true })
    assert.deepEqual(c.inbounds[0].sniffing, { enabled: true, destOverride: ['http', 'tls', 'quic'], routeOnly: true })
    assert.deepEqual(c.inbounds[1].sniffing, { enabled: true, destOverride: ['http', 'tls', 'quic'], routeOnly: false })
    c = S(CONFIGS['vless-ws-tls'], { sniffing: true, sniffTypes: ['http', 'tls'] })
    assert.deepEqual(c.inbounds[0].sniffing, { enabled: true, destOverride: ['http', 'tls'], routeOnly: false })
    c = S(CONFIGS['vless-ws-tls'], { sniffing: true, sniffTypes: [] })
    assert.deepEqual(c.inbounds[0].sniffing.destOverride, ['http', 'tls'])
    c = S(CONFIGS['vless-ws-tls'], { sniffing: true, sniffTypes: ['fakedns+others'] })
    assert.deepEqual(c.inbounds[0].sniffing.destOverride, ['fakedns', 'http', 'tls', 'quic'])
    c = S(CONFIGS['vless-ws-tls'], { sniffing: true, sniffTypes: ['http'], adBlock: true, fakeDns: true })
    assert.deepEqual(c.inbounds[0].sniffing, { enabled: true, destOverride: ['http', 'fakedns', 'tls', 'quic'], routeOnly: false })
    c = S(CONFIGS['vless-ws-tls'], { fakeDns: true })
    assert.deepEqual(c.inbounds[0].sniffing, { enabled: true, destOverride: ['http', 'tls', 'quic', 'fakedns'], routeOnly: false })
    assert.equal(c.inbounds[1].sniffing, undefined)
    c = S(CONFIGS['vless-ws-tls'], { splitRouting: true, adBlock: true })
    assert.equal(c.inbounds[0].sniffing.routeOnly, false)
  })

  check('dns', () => {
    let c = S(CONFIGS['vless-ws-tls'], { fakeDns: true, encryptedDns: true, customDns: ' https://dns.mullvad.net/dns-query ' })
    assert.deepEqual(c.fakedns, [{ ipPool: '198.18.0.0/15', poolSize: 65535 }])
    assert.deepEqual(c.dns, { servers: ['fakedns', 'https://dns.mullvad.net/dns-query', 'https://1.1.1.1/dns-query', 'https://8.8.8.8/dns-query', '1.1.1.1', '8.8.8.8'], queryStrategy: 'UseIPv4' })
    assert.equal(c.outbounds.at(-1).tag, 'dns-out')
    assert.deepEqual(c.routing.rules[0], { type: 'field', port: 53, outboundTag: 'dns-out' })
    assert.equal(c.routing.rules[1].domain.length, 10)
    assert.equal(c.routing.rules[1].outboundTag, 'block')
    c = S(CONFIGS['vless-ws-tls'], { customDns: '178.22.122.100' })
    assert.deepEqual(c.dns.servers, ['178.22.122.100', '1.1.1.1', '8.8.8.8'])
    assert.equal(c.fakedns, undefined)
  })

  check('routing order: dns, ads, youtube, iran, catch-all', () => {
    const c = S(CONFIGS['vless-ws-tls'], { encryptedDns: true, adBlock: true, youtubeDirect: true, splitRouting: true })
    const r = c.routing.rules
    assert.deepEqual(r.map(x => x.outboundTag), ['dns-out', 'block', 'block', 'block', 'direct', 'direct', 'direct', 'proxy'])
    assert.ok(r[2].domain.includes('geosite:category-ads-all') && r[2].domain.includes('geosite:ads') && r[2].domain.includes('domain:tapsell.ir'))
    assert.deepEqual(r[3], { type: 'field', network: 'udp', port: '443', outboundTag: 'block' })
    assert.equal(r[4].domain[0], 'geosite:youtube')
    assert.deepEqual(r[5], { type: 'field', ip: ['geoip:private', 'geoip:ir'], outboundTag: 'direct' })
    assert.deepEqual(r[6], { type: 'field', domain: ['geosite:category-ir'], outboundTag: 'direct' })
  })

  check('direct only', () => {
    const c = S({ name: 'adblock', protocol: 'freedom', address: '127.0.0.1', port: 1 }, { directOnly: true, adBlock: true })
    assert.deepEqual(c.outbounds[0], { tag: 'proxy', protocol: 'freedom', settings: { domainStrategy: 'UseIP' } })
  })

  check('share on LAN fail-safe', () => {
    let c = S(CONFIGS['vless-ws-tls'], SETTINGS['share-lan'])
    assert.equal(c.inbounds[0].listen, '192.168.43.1')
    assert.deepEqual(c.inbounds[0].settings, { auth: 'password', udp: true, accounts: [{ user: 'u', pass: 'p' }] })
    assert.equal(c.inbounds[1].listen, '192.168.43.1')
    c = S(CONFIGS['vless-ws-tls'], { ...SETTINGS['share-lan'], sharePass: '' })
    assert.equal(c.inbounds[0].listen, '127.0.0.1')
    assert.equal(c.inbounds[0].settings.auth, 'noauth')
    c = S(CONFIGS['vless-ws-tls'], { ...SETTINGS['share-lan'], shareListenAddress: '0.0.0.0' })
    assert.equal(c.inbounds[0].listen, '127.0.0.1')
    assert.equal(c.inbounds[1].listen, '127.0.0.1')
  })

  check('api port', () => {
    const c = S(CONFIGS['vless-ws-tls'], {}, { ...PORTS, apiPort: 10085 })
    assert.deepEqual(c.api, { tag: 'api', services: ['StatsService'] })
    assert.equal(c.inbounds[2].tag, 'api')
    assert.deepEqual(c.routing.rules[0], { type: 'field', inboundTag: ['api'], outboundTag: 'api' })
  })

  check('buildChainToSocks mirrors buildPsiphon/buildAether', () => {
    const c = buildChainToSocks('127.0.0.1', 1819, { fragment: true, mux: true, splitRouting: true }, PORTS)
    assert.deepEqual(c.outbounds[0], { tag: 'proxy', protocol: 'socks', settings: { servers: [{ address: '127.0.0.1', port: 1819 }] }, mux: { enabled: true, concurrency: 8 } })
    assert.ok(c.outbounds.find(o => o.tag === 'fragment'))
    assert.throws(() => buildChainToSocks('127.0.0.1', 0, {}, PORTS))
  })

  check('supportsXray', () => {
    for (const k of Object.keys(CONFIGS)) assert.equal(supportsXray(CONFIGS[k]), true, k)
    for (const p of ['tuic', 'hysteria', 'psiphon', 'aether', 'tor', 'ssh', 'openvpn', 'ikev2', 'amneziawg', '']) {
      assert.equal(supportsXray({ protocol: p }), false, p)
    }
    assert.equal(supportsXray({ protocol: 'shadowsocks', network: 'quic' }), false)
    assert.equal(supportsXray(null), false)
    assert.throws(() => buildXrayConfig({ protocol: 'psiphon' }, {}, PORTS), /buildChainToSocks/)
    assert.throws(() => buildXrayConfig(CONFIGS['vless-ws-tls'], {}, {}), /socksPort/)
  })

  check('buildDelayTestConfig shape', () => {
    const list = [CONFIGS['vless-ws-tls'], { protocol: 'tuic', address: 'x', port: 1 }, CONFIGS['wg-warp-reserved'],
      { config: { ...CONFIGS['vmess-ws-tls'], id: 'v' }, chainBase: { ...CONFIGS['trojan-tcp-tls'], id: 't' } }]
    const { config, ports } = buildDelayTestConfig(list, 20000)
    assert.deepEqual(ports, [{ index: 0, port: 20000 }, { index: 2, port: 20001 }, { index: 3, port: 20002 }])
    assert.deepEqual(config.inbounds.map(i => i.tag), ['in-0', 'in-2', 'in-3'])
    assert.deepEqual(config.outbounds.map(o => o.tag), ['block', 'out-0', 'out-2', 'out-3', 'chain-3'])
    assert.deepEqual(config.outbounds[3].streamSettings.sockopt, { dialerProxy: 'chain-3' })
    assert.deepEqual(config.routing.rules[2], { type: 'field', inboundTag: ['in-3'], outboundTag: 'out-3' })
    assert.equal(config.log.loglevel, 'none')
  })
}

// ------------------------------------------------------------------ core validation

async function coreValidation() {
  if (!fs.existsSync(XRAY_BIN)) {
    console.log(`SKIP core validation: ${XRAY_BIN} not found`)
    return
  }
  const appAssetsOk = fs.existsSync(path.join(APP_ASSETS, 'geosite.dat')) && fs.existsSync(path.join(APP_ASSETS, 'geoip.dat'))
  const jobs = []
  const add = (label, cfg) => {
    jobs.push({ label: `${label} [stock geo]`, cfg: stripIranOnly(cfg), assets: STOCK_ASSETS })
    if (appAssetsOk) jobs.push({ label: `${label} [app geo]`, cfg, assets: APP_ASSETS })
  }
  // Every profile with the defaults and with everything on.
  for (const [name, cfg] of Object.entries(CONFIGS)) {
    add(`${name} / defaults`, buildXrayConfig(cfg, {}, PORTS))
    add(`${name} / everything`, buildXrayConfig(cfg, SETTINGS.everything, PORTS))
  }
  // Every setting on a representative profile.
  for (const [name, s] of Object.entries(SETTINGS)) {
    add(`vless-ws-tls / ${name}`, buildXrayConfig(CONFIGS['vless-ws-tls'], s, PORTS))
  }
  add('vless-reality / fragment+mux+noise', buildXrayConfig(CONFIGS['vless-raw-reality-vision'], { fragment: true, noiseSpec: 'aggressive' }, PORTS))
  add('vless-xhttp / mux', buildXrayConfig(CONFIGS['vless-xhttp-reality'], { mux: true }, PORTS))
  add('chain vless->trojan / fragment', buildXrayConfig({ ...CONFIGS['vless-ws-tls'], id: 'a' }, { fragment: true }, { ...PORTS, chainBase: { ...CONFIGS['trojan-tcp-tls'], id: 'b' } }))
  add('chain hy2 -> wg', buildXrayConfig({ ...CONFIGS['vless-grpc-multi-tls'], id: 'a' }, {}, { ...PORTS, chainBase: { ...CONFIGS['wg-warp-reserved'], id: 'b' } }))
  add('api port', buildXrayConfig(CONFIGS['vless-ws-tls'], SETTINGS.everything, { ...PORTS, apiPort: 10085 }))
  add('chainToSocks psiphon / defaults', buildChainToSocks('127.0.0.1', 1080, {}, PORTS))
  add('chainToSocks aether / everything', buildChainToSocks('127.0.0.1', 1819, SETTINGS.everything, PORTS))
  add('direct only block-when-off', buildXrayConfig({ name: 'adblock', protocol: 'freedom', address: '127.0.0.1', port: 1 }, SETTINGS['direct-only'], PORTS))
  const delay = buildDelayTestConfig(Object.values(CONFIGS), 30000)
  add('delay test (all profiles)', delay.config)

  const t0 = Date.now()
  const results = await pool(jobs.map(j => () => xrayTestAsync(j.cfg, j.assets)), Math.max(2, Math.min(8, os.cpus().length)))
  let ok = 0
  results.forEach((r, i) => {
    if (r.ok) { ok++; passed++ } else {
      failed++
      failures.push(`xray -test ${jobs[i].label}: ${(r.out.split('\n').find(l => /Failed|error/i.test(l)) || r.out.trim().split('\n').pop())} (${r.file})`)
    }
  })
  console.log(`xray -test: ${ok}/${jobs.length} configs OK in ${((Date.now() - t0) / 1000).toFixed(1)}s` +
    (appAssetsOk ? ' (stock geo with geosite:ads removed, and app geo unmodified)' : ' (stock geo only; app assets not found)'))

  // The stock geosite really lacks geosite:ads, which is why the strip exists.
  const unstripped = await xrayTestAsync(buildXrayConfig(CONFIGS['vless-ws-tls'], { adBlock: true }, PORTS), STOCK_ASSETS)
  check('stock geosite.dat lacks geosite:ads (Iran rules file required for ad block)', () => {
    assert.equal(unstripped.ok, false)
    assert.match(unstripped.out, /geosite:ads|ads/)
  })
}

// ------------------------------------------------------------------ end to end

function freePort() {
  return new Promise((resolve, reject) => {
    const s = net.createServer()
    s.once('error', reject)
    s.listen(0, '127.0.0.1', () => { const p = s.address().port; s.close(() => resolve(p)) })
  })
}

async function freeRange(n) {
  for (let attempt = 0; attempt < 50; attempt++) {
    const start = 20000 + Math.floor(Math.random() * 30000)
    let ok = true
    for (let i = 0; i < n && ok; i++) {
      ok = await new Promise(resolve => {
        const s = net.createServer()
        s.once('error', () => resolve(false))
        s.listen(start + i, '127.0.0.1', () => s.close(() => resolve(true)))
      })
    }
    if (ok) return start
  }
  throw new Error('no free port range')
}

function waitPort(port, ms) {
  const until = Date.now() + ms
  return new Promise(resolve => {
    const attempt = () => {
      const s = net.connect(port, '127.0.0.1')
      s.once('connect', () => { s.destroy(); resolve(true) })
      s.once('error', () => { s.destroy(); Date.now() < until ? setTimeout(attempt, 100) : resolve(false) })
    }
    attempt()
  })
}

function startXray(cfg, name) {
  const file = path.join(TMP, `${name}.json`)
  fs.writeFileSync(file, JSON.stringify(cfg, null, 1))
  const p = spawn(XRAY_BIN, ['run', '-c', file], { env: { ...process.env, XRAY_LOCATION_ASSET: STOCK_ASSETS }, stdio: ['ignore', 'pipe', 'pipe'] })
  let log = ''
  p.stdout.on('data', d => { log += d })
  p.stderr.on('data', d => { log += d })
  p.log = () => log
  return p
}

// Raw request through the HTTP inbound (forward-proxy form).
function viaHttpProxy(port, url) {
  return new Promise(resolve => {
    const req = http.request({ host: '127.0.0.1', port, method: 'GET', path: url, headers: { Host: new URL(url).host } }, res => {
      res.resume(); res.on('end', () => resolve(res.statusCode))
    })
    req.setTimeout(5000, () => req.destroy())
    req.on('error', () => resolve(-1))
    req.end()
  })
}

async function endToEnd() {
  if (!fs.existsSync(XRAY_BIN)) { console.log('SKIP end to end: no xray binary'); return }
  // Target: answers 204 on /generate_204, a 200 with a chunked body elsewhere.
  const target = http.createServer((req, res) => {
    if (req.url.startsWith('/generate_204')) { res.writeHead(204); res.end(); return }
    res.writeHead(200, { 'Content-Type': 'text/plain' }); res.write('hello '); res.end('world')
  })
  await new Promise(r => target.listen(0, '127.0.0.1', r))
  const targetPort = target.address().port

  const SS_KEY = 'AAAAAAAAAAAAAAAAAAAAAA=='
  const sp = {}
  for (const k of ['vlessTcp', 'vmessWs', 'trojan', 'ss', 'socks', 'http', 'vlessGrpc', 'vlessXhttp', 'vlessHu', 'vlessKcp', 'vlessKcpHdr']) sp[k] = await freePort()
  const deadPort = await freePort()
  const server = {
    log: { loglevel: 'warning' },
    inbounds: [
      { tag: 'a', listen: '127.0.0.1', port: sp.vlessTcp, protocol: 'vless', settings: { clients: [{ id: UUID }], decryption: 'none' } },
      { tag: 'b', listen: '127.0.0.1', port: sp.vmessWs, protocol: 'vmess', settings: { clients: [{ id: UUID }] }, streamSettings: { network: 'ws', wsSettings: { path: '/vm' } } },
      { tag: 'c', listen: '127.0.0.1', port: sp.trojan, protocol: 'trojan', settings: { clients: [{ password: 'pw' }] } },
      { tag: 'd', listen: '127.0.0.1', port: sp.ss, protocol: 'shadowsocks', settings: { method: '2022-blake3-aes-128-gcm', password: SS_KEY, network: 'tcp,udp' } },
      { tag: 'e', listen: '127.0.0.1', port: sp.socks, protocol: 'socks', settings: { auth: 'password', accounts: [{ user: 'user', pass: 'pass' }] } },
      { tag: 'f', listen: '127.0.0.1', port: sp.http, protocol: 'http', settings: { accounts: [{ user: 'user', pass: 'pass' }] } },
      { tag: 'g', listen: '127.0.0.1', port: sp.vlessGrpc, protocol: 'vless', settings: { clients: [{ id: UUID }], decryption: 'none' }, streamSettings: { network: 'grpc', grpcSettings: { serviceName: 'gs' } } },
      { tag: 'h', listen: '127.0.0.1', port: sp.vlessXhttp, protocol: 'vless', settings: { clients: [{ id: UUID }], decryption: 'none' }, streamSettings: { network: 'xhttp', xhttpSettings: { path: '/xh' } } },
      { tag: 'i', listen: '127.0.0.1', port: sp.vlessHu, protocol: 'vless', settings: { clients: [{ id: UUID }], decryption: 'none' }, streamSettings: { network: 'httpupgrade', httpupgradeSettings: { path: '/up' } } },
      { tag: 'j', listen: '127.0.0.1', port: sp.vlessKcp, protocol: 'vless', settings: { clients: [{ id: UUID }], decryption: 'none' }, streamSettings: { network: 'kcp', kcpSettings: {}, finalmask: { udp: [{ type: 'mkcp-aes128gcm', settings: { password: 'myseed' } }] } } },
      { tag: 'k', listen: '127.0.0.1', port: sp.vlessKcpHdr, protocol: 'vless', settings: { clients: [{ id: UUID }], decryption: 'none' }, streamSettings: { network: 'kcp', kcpSettings: {}, finalmask: { udp: [{ type: 'header-wechat' }, { type: 'mkcp-original' }] } } }
    ],
    outbounds: [{ tag: 'out', protocol: 'freedom', settings: { redirect: `127.0.0.1:${targetPort}` } }]
  }
  const L = '127.0.0.1'
  const profiles = [
    { name: 'vless tcp', protocol: 'vless', address: L, port: sp.vlessTcp, uuid: UUID },
    { name: 'vmess ws', protocol: 'vmess', address: L, port: sp.vmessWs, uuid: UUID, encryption: 'auto', network: 'ws', path: '/vm', host: 'h.example' },
    { name: 'trojan', protocol: 'trojan', address: L, port: sp.trojan, password: 'pw' },
    { name: 'ss2022', protocol: 'shadowsocks', address: L, port: sp.ss, method: '2022-blake3-aes-128-gcm', password: SS_KEY },
    { name: 'socks', protocol: 'socks', address: L, port: sp.socks, uuid: 'user', password: 'pass' },
    { name: 'http', protocol: 'http', address: L, port: sp.http, uuid: 'user', password: 'pass' },
    { name: 'tuic (skipped)', protocol: 'tuic', address: L, port: 1 },
    { name: 'vless grpc', protocol: 'vless', address: L, port: sp.vlessGrpc, uuid: UUID, network: 'grpc', serviceName: 'gs' },
    { name: 'vless xhttp', protocol: 'vless', address: L, port: sp.vlessXhttp, uuid: UUID, network: 'xhttp', path: '/xh' },
    { name: 'vless httpupgrade', protocol: 'vless', address: L, port: sp.vlessHu, uuid: UUID, network: 'httpupgrade', path: '/up' },
    { name: 'vless kcp seed', protocol: 'vless', address: L, port: sp.vlessKcp, uuid: UUID, network: 'kcp', path: 'myseed' },
    { name: 'vless kcp wechat', protocol: 'vless', address: L, port: sp.vlessKcpHdr, uuid: UUID, network: 'mkcp', headerType: 'wechat-video' },
    { name: 'dead', protocol: 'vless', address: L, port: deadPort, uuid: UUID },
    { name: 'wrong uuid', protocol: 'vless', address: L, port: sp.vlessTcp, uuid: '11111111-2222-3333-4444-555555555555' }
  ]
  const expectAlive = p => !['dead', 'wrong uuid'].includes(p.name)

  const procs = []
  try {
    const srv = startXray(server, 'e2e-server')
    procs.push(srv)
    for (const port of Object.values(sp)) {
      if (port === sp.vlessKcp || port === sp.vlessKcpHdr) continue // UDP
      if (!await waitPort(port, 8000)) throw new Error('server did not start: ' + srv.log())
    }

    const basePort = await freeRange(profiles.length)
    const { config, ports } = buildDelayTestConfig(profiles, basePort)
    check('delay test skips non-xray profile', () => assert.ok(!ports.some(p => p.index === 6) && ports.length === profiles.length - 1))
    const cli = startXray(config, 'e2e-delay')
    procs.push(cli)
    for (const { port } of ports) if (!await waitPort(port, 8000)) throw new Error('delay instance did not start: ' + cli.log())

    const url = 'http://www.gstatic.com/generate_204' // redirected by the server's freedom outbound
    const results = await Promise.all(ports.map(async ({ index, port }) => ({ index, port, ms: await measureDelay(port, { url, timeoutMs: 3000 }) })))
    for (const r of results) {
      const p = profiles[r.index]
      console.log(`  delay ${p.name.padEnd(18)} port ${r.port}: ${r.ms} ms`)
      check(`delay ${p.name}`, () => expectAlive(p) ? assert.ok(r.ms >= 0, `expected an answer, got ${r.ms}`) : assert.equal(r.ms, -1))
    }
    const vlessPort = ports.find(p => p.index === 0).port
    const non204 = await measureDelay(vlessPort, { url: 'http://www.gstatic.com/other', timeoutMs: 3000 })
    check('bounded delay requires a 204', () => assert.equal(non204, -1))
    const warm = await measureDelay(vlessPort, { url: 'http://www.gstatic.com/other', warm: true })
    check('warm delay accepts any status (MeasureDelay)', () => assert.ok(warm >= 0, String(warm)))
    const warm204 = await measureDelay(vlessPort, { url, warm: true })
    check('warm delay on 204', () => assert.ok(warm204 >= 0, String(warm204)))
    const closed = await measureDelay(await freePort(), { url, timeoutMs: 1000 })
    check('closed SOCKS port gives -1', () => assert.equal(closed, -1))

    // Full runtime config: SOCKS + HTTP inbounds, with routing options on.
    const socksPort = await freePort()
    const httpPort = await freePort()
    const full = buildXrayConfig(profiles[1], { splitRouting: true, sniffing: true, mux: true, noiseSpec: 'light', youtubeDirect: true }, { socksPort, httpPort, logLevel: 'warning' })
    const fullProc = startXray(stripIranOnly(full), 'e2e-full')
    procs.push(fullProc)
    if (!await waitPort(socksPort, 8000) || !await waitPort(httpPort, 8000)) throw new Error('full config did not start: ' + fullProc.log())
    const viaSocks = await measureDelay(socksPort, { url, timeoutMs: 4000 })
    check('full config: SOCKS inbound carries traffic', () => assert.ok(viaSocks >= 0, String(viaSocks)))
    const viaHttp = await viaHttpProxy(httpPort, url)
    check('full config: HTTP inbound carries traffic', () => assert.equal(viaHttp, 204))
    console.log(`  full config (vmess ws, mux, split routing): socks ${viaSocks} ms, http status ${viaHttp}`)

    // Chain to a local SOCKS (the server's socks inbound stands in for Psiphon/Aether).
    const cs = await freePort()
    const ch = await freePort()
    const chain = buildChainToSocks('127.0.0.1', sp.socks, {}, { socksPort: cs, httpPort: ch })
    chain.outbounds[0].settings.servers[0].users = [{ user: 'user', pass: 'pass' }] // the stand-in needs auth
    const chainProc = startXray(chain, 'e2e-chain')
    procs.push(chainProc)
    if (!await waitPort(cs, 8000)) throw new Error('chain config did not start: ' + chainProc.log())
    const viaChain = await measureDelay(cs, { url, timeoutMs: 4000 })
    check('buildChainToSocks carries traffic', () => assert.ok(viaChain >= 0, String(viaChain)))
    console.log(`  chain to local socks: ${viaChain} ms`)
  } catch (e) {
    failed++
    failures.push('end to end: ' + e.message)
  } finally {
    for (const p of procs) p.kill('SIGKILL')
    target.close()
  }
}

// ------------------------------------------------------------------ main

unitChecks()
await coreValidation()
await endToEnd()
try { fs.rmSync(TMP, { recursive: true, force: true }) } catch { /* ignore */ }
console.log(`\n${passed} passed, ${failed} failed`)
if (failed) {
  for (const f of failures) console.log('FAIL ' + f)
  process.exit(1)
}
