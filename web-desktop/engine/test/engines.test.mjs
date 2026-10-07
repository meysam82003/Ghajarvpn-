// The engines the desktop gained from the Android app: OpenVPN (.ovpn →
// sing-box openvpn-client, every variant through `sing-box check`), Tor
// (torrc, bridges, layout of the expert bundle), Aether (command line and
// environment as AetherController / OblivionOptions build them) and IKEv2
// (the OS VPN client's command lines, with a recording runner).
// End-to-end runs of OpenVPN, Tor and Aether are in engine.test.mjs.
//
//   node web-desktop/engine/test/engines.test.mjs
//   SING_BOX=/path/to/sing-box  AETHER_BIN=/path/to/aether (optional: checks the real CLI accepts the arguments)
import { createRequire } from 'node:module'
import { spawnSync, spawn } from 'node:child_process'
import { mkdtempSync, writeFileSync, mkdirSync, rmSync, existsSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import crypto from 'node:crypto'
import assert from 'node:assert/strict'

const require = createRequire(import.meta.url)
const ovpn = require('../openvpn.js')
const tor = require('../tor.js')
const aether = require('../aether.js')
const ikev2 = require('../ikev2.js')
const sb = require('../singbox.js')
const P = require('../parser.js')

const SING_BOX = process.env.SING_BOX || '/tmp/claude-0/cores/sing-box'
const work = mkdtempSync(join(tmpdir(), 'ghajar-engines-'))
let passed = 0
const failures = []
async function test(name, fn) {
  try { await fn(); passed++; console.log('ok  ', name) } catch (e) { failures.push(name); console.log('FAIL', name, '\n  ', String(e.stack || e).split('\n').slice(0, 4).join('\n   ')) }
}
function sbCheck(config) {
  const f = join(work, `c-${crypto.randomUUID()}.json`)
  writeFileSync(f, JSON.stringify(config))
  const r = spawnSync(SING_BOX, ['check', '-c', f, '-D', work, '--disable-color'], { encoding: 'utf8', timeout: 30000 })
  if (r.status !== 0) throw new Error('sing-box check: ' + (r.stderr || r.stdout).trim().split('\n').slice(-2).join(' '))
}

// ------------------------------------------------------------------ material

const pem = (label, body) => `-----BEGIN ${label}-----\n${body}\n-----END ${label}-----`
const { privateKey } = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' })
const KEY = privateKey.export({ type: 'pkcs8', format: 'pem' }).trim()
// A syntactically valid certificate is enough for `check` (it parses PEM, not trust).
const CERT = (() => {
  const keyFile = join(work, 'k.pem')
  writeFileSync(keyFile, KEY)
  const r = spawnSync('openssl', ['req', '-x509', '-key', keyFile, '-subj', '/CN=t', '-days', '1'], { encoding: 'utf8' })
  if (!r.stdout.includes('BEGIN CERTIFICATE')) throw new Error('openssl could not make a test certificate: ' + r.stderr)
  return r.stdout.trim()
})()
const staticKey = () => '#\n# 2048 bit OpenVPN static key\n#\n' + pem('OpenVPN Static key V1', crypto.randomBytes(256).toString('hex').match(/.{32}/g).join('\n'))
const TA = staticKey()
const base = (extra = '', proto = 'udp') => `client
dev tun
proto ${proto}
remote vpn.de.example.net 1194
nobind
persist-key
persist-tun
remote-cert-tls server
verb 3
${extra}
<ca>
${CERT}
</ca>
<cert>
${CERT}
</cert>
<key>
${KEY}
</key>
`
const cfgOf = (text, creds = {}) => ({ ...P.parseBundle(text)[0], ...creds })
const endpoint = c => sb.buildSingboxSpec(c).endpoint
const proxyOnly = c => sb.buildProxyOnlyConfig({ outbound: sb.buildSingboxSpec(c), socksPort: 10808, httpPort: 10809, settings: { iranDirect: true } })

// ------------------------------------------------------------------ OpenVPN

await test('ovpn: detected by parseBundle as one openvpn config, named like the phone', () => {
  const c = cfgOf(base('auth-user-pass'))
  assert.equal(c.protocol, 'openvpn')
  assert.equal(c.name, 'Ghajarvpn 🇩🇪')
  assert.equal(c.address, 'vpn.de.example.net'); assert.equal(c.port, 1194); assert.equal(c.network, 'udp')
  assert.equal(P.protocolFamily(c), 'openvpn')
  assert.ok(ovpn.needsCredentials(c))
  assert.ok(!ovpn.needsCredentials({ ...c, uuid: 'u', password: 'p' }))
  assert.equal(P.parseBundle('vless://' + crypto.randomUUID() + '@h:1?type=tcp#x')[0].protocol, 'vless')
})

await test('ovpn: inline <auth-user-pass> fills username / password', () => {
  const c = cfgOf(base('auth-user-pass\n<auth-user-pass>\nalice\ns3cret\n</auth-user-pass>'))
  assert.equal(c.uuid, 'alice'); assert.equal(c.password, 's3cret'); assert.ok(!ovpn.needsCredentials(c))
})

await test('ovpn: credentials required before connecting', () => {
  assert.throws(() => sb.buildSingboxSpec(cfgOf(base('auth-user-pass'))), /نام کاربری/)
})

await test('ovpn: tls-crypt + cipher + auth + credentials → sing-box check', () => {
  const c = cfgOf(base(`cipher AES-256-CBC\nauth SHA256\nauth-user-pass\n<tls-crypt>\n${TA}\n</tls-crypt>`), { uuid: 'u', password: 'p' })
  const e = endpoint(c)
  assert.equal(e.type, 'openvpn-client'); assert.equal(e.username, 'u'); assert.equal(e.password, 'p')
  assert.deepEqual(e.servers, [{ server: 'vpn.de.example.net', server_port: 1194 }]); assert.equal(e.network, 'udp')
  assert.equal(e.tls.control_wrap.type, 'tls_crypt')
  assert.deepEqual(e.data_ciphers, ['AES-256-GCM', 'AES-128-GCM', 'CHACHA20-POLY1305', 'AES-256-CBC'])
  assert.equal(e.data_ciphers_fallback, 'AES-256-CBC'); assert.equal(e.auth, 'SHA256'); assert.equal(e.cipher, undefined)
  assert.equal(e.tls.remote_certificate_tls, 'server')
  sbCheck(proxyOnly(c))
  sbCheck(sb.buildTunConfig({ socksPort: 10808, settings: { serverHosts: sb.remoteHosts(c) }, platform: 'linux' }))
  assert.deepEqual(sb.remoteHosts(c), ['vpn.de.example.net'])
})

await test('ovpn: tls-auth with key-direction / file direction, TCP, several remotes and <connection> blocks', () => {
  const t = base(`key-direction 1\n<tls-auth>\n${TA}\n</tls-auth>\nremote 10.0.0.2 443 tcp\nremote-random\ndata-ciphers AES-128-GCM:CHACHA20-POLY1305\n<connection>\nremote alt.example.net 8443 tcp-client\n</connection>`, 'tcp-client')
  const e = endpoint(cfgOf(t))
  assert.equal(e.tls.control_wrap.direction, 'client')
  assert.equal(e.network, 'tcp')
  assert.deepEqual(e.servers.map(s => [s.server, s.server_port, s.network]), [['vpn.de.example.net', 1194, undefined], ['10.0.0.2', 443, undefined], ['alt.example.net', 8443, undefined]])
  assert.equal(e.remote_random, true)
  assert.deepEqual(e.data_ciphers, ['AES-128-GCM', 'CHACHA20-POLY1305'])
  sbCheck(proxyOnly(cfgOf(t)))
  // "tls-auth file 0" embedded from a folder: the direction moves to key-direction.
  const dir = join(work, 'ovpn-files'); mkdirSync(dir, { recursive: true })
  writeFileSync(join(dir, 'ta.key'), TA); writeFileSync(join(dir, 'ca.crt'), CERT)
  const text = base('tls-auth ta.key 0').replace(/<ca>[\s\S]*?<\/ca>/, 'ca ca.crt')
  assert.throws(() => sb.buildSingboxSpec(cfgOf(text)), /ca ca\.crt/)
  const c2 = ovpn.ovpnToConfig(text, { baseDir: dir })
  const e2 = endpoint(c2)
  assert.equal(e2.tls.control_wrap.direction, 'server'); assert.ok(e2.tls.certificate[0].includes('BEGIN CERTIFICATE'))
  sbCheck(proxyOnly(c2))
})

await test('ovpn: tls-crypt-v2, verify-x509-name, tls versions, peer-fingerprint, timers, mssfix, compression → check', () => {
  const v2 = '-----BEGIN OpenVPN tls-crypt-v2 client key-----\n' + crypto.randomBytes(300).toString('base64').match(/.{1,64}/g).join('\n') + '\n-----END OpenVPN tls-crypt-v2 client key-----'
  const fp = crypto.randomBytes(32).toString('hex').match(/../g).join(':').toUpperCase()
  const t = base(`verify-x509-name server.example name\ntls-version-min 1.2 or-highest\ntls-version-max 1.3\nreneg-sec 0\nkeepalive 10 60\nmssfix 1400\nfragment 1300\ntun-mtu 1500\ncomp-lzo\npeer-fingerprint ${fp}\nexplicit-exit-notify\npull-filter ignore "route "\nreplay-window 128 30\n<tls-crypt-v2>\n${v2}\n</tls-crypt-v2>`)
  const e = endpoint(cfgOf(t))
  assert.equal(e.tls.control_wrap.type, 'tls_crypt_v2')
  assert.equal(e.tls.server_name, 'server.example'); assert.equal(e.tls.server_name_type, 'name')
  assert.equal(e.tls.version_min, '1.2'); assert.equal(e.tls.version_max, '1.3')
  assert.equal(e.renegotiate_disabled, true); assert.equal(e.ping_interval, '10s'); assert.equal(e.ping_restart, '60s')
  assert.equal(e.mss_fix, 1400); assert.equal(e.fragment, 1300); assert.equal(e.mtu, 1500)
  assert.equal(e.compression_lzo, 'adaptive'); assert.equal(e.allow_compression, 'asym')
  assert.deepEqual(e.tls.peer_fingerprint, [fp.replace(/:/g, '').toLowerCase()])
  assert.equal(e.explicit_exit_notify, 1); assert.deepEqual(e.pull_filters, [{ action: 'ignore', text: 'route ' }])
  assert.equal(e.replay_window, 128); assert.equal(e.replay_window_time, '30s')
  sbCheck(proxyOnly(cfgOf(t)))
  const lz4 = endpoint(cfgOf(base('compress lz4-v2')))
  assert.equal(lz4.compression, 'lz4-v2'); assert.equal(lz4.allow_compression, 'asym')
  const stub = endpoint(cfgOf(base('compress')))
  assert.equal(stub.compression, 'stub'); assert.equal(stub.allow_compression, undefined)
  sbCheck(proxyOnly(cfgOf(base('compress lz4-v2'))))
})

await test('ovpn: no remote-cert-tls means no purpose check (as OpenVPN); verify-x509-name defaults to subject', () => {
  const t = base('verify-x509-name "C=DE, CN=srv"').replace('remote-cert-tls server\n', '')
  const e = endpoint(cfgOf(t))
  assert.equal(e.tls.remote_certificate_tls, 'none'); assert.equal(e.tls.server_name, 'C=DE, CN=srv'); assert.equal(e.tls.server_name_type, 'subject')
  sbCheck(proxyOnly(cfgOf(t)))
  const eku = endpoint(cfgOf(base('remote-cert-eku "TLS Web Server Authentication"').replace('remote-cert-tls server\n', '')))
  assert.equal(eku.tls.remote_certificate_tls, undefined); assert.equal(eku.tls.remote_certificate_eku, 'TLS Web Server Authentication')
})

await test('ovpn: static-key profile (secret + ifconfig) → check', () => {
  const t = `remote 198.51.100.4 1194\nproto udp\ndev tun\nifconfig 10.8.0.2 10.8.0.1\ncipher AES-256-CBC\nkey-direction 1\n<secret>\n${TA}\n</secret>\n`
  const c = P.parseBundle(t)[0]
  assert.equal(c.protocol, 'openvpn')
  const e = endpoint(c)
  assert.equal(e.mode, 'static_key'); assert.equal(e.key_direction, 'client'); assert.equal(e.cipher, 'AES-256-CBC')
  assert.deepEqual(e.address, ['10.8.0.2/30']); assert.equal(e.peer_address, '10.8.0.1'); assert.equal(e.tls, undefined)
  sbCheck(proxyOnly(c))
})

await test('ovpn: socks-proxy / http-proxy become the endpoint detour → check', () => {
  const s = cfgOf(base('socks-proxy 127.0.0.1 1080'))
  const spec = sb.buildSingboxSpec(s)
  assert.equal(spec.endpoint.detour, 'ovpn-upstream'); assert.equal(spec.extraOutbounds[0].type, 'socks')
  sbCheck(proxyOnly(s))
  const h = cfgOf(base('http-proxy proxy.example 3128\n<http-proxy-user-pass>\nbob\npw\n</http-proxy-user-pass>', 'tcp'))
  const hs = sb.buildSingboxSpec(h)
  assert.equal(hs.extraOutbounds[0].type, 'http'); assert.equal(hs.extraOutbounds[0].username, 'bob')
  sbCheck(proxyOnly(h))
  assert.throws(() => sb.buildSingboxSpec(cfgOf(base('http-proxy proxy.example 3128'))), /TCP/)
})

await test('ovpn: unsupported profiles are refused with the reason', () => {
  assert.throws(() => sb.buildSingboxSpec(cfgOf(base('dev tap'))), /tap/)
  assert.throws(() => sb.buildSingboxSpec(cfgOf(base('pkcs12 client.p12'))), /pkcs12/)
  assert.throws(() => sb.buildSingboxSpec(cfgOf(base().replace(/<ca>[\s\S]*?<\/ca>/, ''))), /CA/)
  assert.throws(() => sb.buildSingboxSpec(cfgOf(base().replace('-----BEGIN PRIVATE KEY-----', '-----BEGIN ENCRYPTED PRIVATE KEY-----'))), /encrypted/)
  assert.equal(ovpn.ovpnToConfig('client\ndev tun\n'), null)
})

await test('ovpn: words() splits like OpenVPN (quotes, escapes, comments)', () => {
  assert.deepEqual(ovpn.words('remote "my host" 1194 # comment'), ['remote', 'my host', '1194'])
  assert.deepEqual(ovpn.words("pull-filter ignore 'route '"), ['pull-filter', 'ignore', 'route '])
  assert.deepEqual(ovpn.words('static-challenge "Enter \\"PIN\\"" 1'), ['static-challenge', 'Enter "PIN"', '1'])
  assert.deepEqual(ovpn.words('; all comment'), [])
})

await test('ovpn: the delay test builds it (singboxBatch path) and the TUN keeps its server direct', () => {
  const c = cfgOf(base(`<tls-crypt>\n${TA}\n</tls-crypt>`))
  assert.ok(sb.buildSingboxSpec(c).endpoint)
  const tun = sb.buildTunConfig({ outbound: sb.buildSingboxSpec(c), settings: { iranDirect: true }, platform: 'win32' })
  assert.equal(tun.endpoints[0].type, 'openvpn-client')
  sbCheck(tun)
})

// ------------------------------------------------------------------ Tor

const torCfg = (extra = {}) => ({ ...P.defaults(), protocol: 'tor', name: 'Tor', address: '127.0.0.1', port: 9150, ...extra })

await test('tor: TorBridges.from - bridge lines, transport, snowflake defaults', () => {
  assert.deepEqual(tor.torBridges(torCfg()), { transport: '', lines: [] })
  const obfs = 'obfs4 192.0.2.1:443 0123456789ABCDEF0123456789ABCDEF01234567 cert=abc iat-mode=0'
  assert.deepEqual(tor.torBridges(torCfg({ extra: JSON.stringify({ bridges: 'Bridge ' + obfs + '\n# note\n' }) })), { transport: 'obfs4', lines: [obfs] })
  assert.equal(tor.torBridges(torCfg({ extra: JSON.stringify({ pt: 'snowflake' }) })).lines.length, 2)
  assert.equal(tor.torBridges(torCfg({ extra: JSON.stringify({ bridges: '192.0.2.9:9001 0123456789ABCDEF0123456789ABCDEF01234567' }) })).transport, 'vanilla')
  // A Tor bridge line pasted into the app becomes such a profile (parser.parseLink).
  const c = P.parseLink('obfs4 192.0.2.1:443 0123456789ABCDEF0123456789ABCDEF01234567 cert=abc iat-mode=0')
  assert.equal(c.protocol, 'tor'); assert.equal(tor.torBridges(c).transport, 'obfs4')
})

await test('tor: torrc like TorController.writeTorrc (country, through-VPN, bridges, quoting)', () => {
  const rc = tor.buildTorrc({ socksPort: 9350, dataDir: '/Users/a b/Library/Application Support/Ghajar/tor', country: 'DE', geoip: '/x/geoip', geoip6: '/x/geoip6', upstreamPort: 10627,
    bridges: { transport: 'obfs4', lines: ['obfs4 192.0.2.1:443 FP cert=x iat-mode=0'] }, ptExec: { lyrebird: './lyrebird' }, extra: 'ConnectionPadding 1\n# c' })
  const lines = rc.trim().split('\n')
  for (const l of ['SocksPort 127.0.0.1:9350', 'DataDirectory "/Users/a b/Library/Application Support/Ghajar/tor"', 'AvoidDiskWrites 1', 'Log notice stdout', 'ClientOnly 1',
    'GeoIPFile /x/geoip', 'GeoIPv6File /x/geoip6', 'ExitNodes {de}', 'StrictNodes 0', 'Socks5Proxy 127.0.0.1:10627', 'UseBridges 1',
    'ClientTransportPlugin obfs4,meek_lite,webtunnel,snowflake exec ./lyrebird', 'Bridge obfs4 192.0.2.1:443 FP cert=x iat-mode=0', 'ConnectionPadding 1']) assert.ok(lines.includes(l), l)
  assert.ok(!lines.some(l => l.startsWith('#')))
  const plain = tor.buildTorrc({ socksPort: 1, dataDir: 'C:\\Users\\x\\tor', country: 'de' })
  assert.ok(plain.includes('DataDirectory "C:\\\\Users\\\\x\\\\tor"')); assert.ok(!plain.includes('ExitNodes'))
  const vanilla = tor.buildTorrc({ socksPort: 1, dataDir: '/d', bridges: { transport: 'vanilla', lines: ['192.0.2.9:9001 FP'] } })
  assert.ok(vanilla.includes('UseBridges 1') && !vanilla.includes('ClientTransportPlugin'))
  const sf = tor.buildTorrc({ socksPort: 1, dataDir: '/d', bridges: { transport: 'snowflake', lines: tor.SNOWFLAKE_DEFAULT }, ptExec: { lyrebird: './lyrebird', snowflake: './snowflake-client' } })
  assert.ok(sf.includes('ClientTransportPlugin snowflake exec ./snowflake-client') && sf.includes('ClientTransportPlugin obfs4,meek_lite,webtunnel exec ./lyrebird'))
})

await test('tor: expert-bundle layout, plan (cwd = transports folder), lyrebird required for bridges', () => {
  const bin = join(work, 'core'); const tdir = join(bin, 'tor'); const pt = join(tdir, 'pluggable_transports')
  mkdirSync(pt, { recursive: true })
  assert.equal(tor.torLayout(bin, 'linux'), null)
  writeFileSync(join(tdir, 'tor'), ''); writeFileSync(join(tdir, 'geoip'), ''); writeFileSync(join(tdir, 'geoip6'), '')
  const noPt = tor.torLayout(bin, 'linux')
  assert.equal(noPt.lyrebird, '')
  const bridged = torCfg({ extra: JSON.stringify({ pt: 'snowflake' }) })
  assert.throws(() => tor.torPlan(bridged, { layout: noPt, socksPort: 1, dataDir: '/d', platform: 'linux' }), /lyrebird/)
  writeFileSync(join(pt, 'lyrebird'), '')
  const L = tor.torLayout(bin, 'linux')
  assert.equal(L.lyrebird, join(pt, 'lyrebird')); assert.equal(L.geoip, join(tdir, 'geoip'))
  const plan = tor.torPlan({ ...bridged, torCountry: 'nl' }, { layout: L, socksPort: 9400, dataDir: join(work, 'data tor'), upstreamPort: 0, platform: 'linux' })
  assert.equal(plan.cwd, pt); assert.equal(plan.binary, join(tdir, 'tor')); assert.deepEqual(plan.args, ['-f', join(work, 'data tor', 'torrc')])
  assert.ok(plan.torrc.includes('exec ./lyrebird')); assert.ok(plan.torrc.includes('ExitNodes {nl}'))
  assert.ok(plan.env.LD_LIBRARY_PATH.startsWith(tdir))
  const win = tor.torPlan(bridged, { layout: { ...L, lyrebird: 'C:\\core\\tor\\pluggable_transports\\lyrebird.exe' }, socksPort: 1, dataDir: '/d', platform: 'win32' })
  assert.ok(win.torrc.includes('exec .\\lyrebird.exe'))
  assert.equal(tor.bootstrapPercent('Oct 07 [notice] Bootstrapped 45% (requesting_descriptors): x'), 45)
  assert.equal(tor.bootstrapPercent('nothing'), null)
  assert.ok(tor.fatalLine('Oct 07 [err] Reading config failed--see warnings above.'))
  assert.equal(tor.fatalLine('[warn] Problem bootstrapping'), '')
})

await test('tor: a real tor accepts the generated torrc (tor --verify-config)', () => {
  const which = spawnSync('sh', ['-c', 'command -v tor'], { encoding: 'utf8' }).stdout.trim()
  if (!which) { console.log('     (no tor on PATH, skipped)'); return }
  const dataDir = join(work, 'tor verify'); mkdirSync(dataDir, { recursive: true, mode: 0o700 })
  const geo = existsSync('/usr/share/tor/geoip') ? '/usr/share/tor/geoip' : ''
  const rc = tor.buildTorrc({ socksPort: 19350, dataDir, country: 'de', geoip: geo, geoip6: geo ? '/usr/share/tor/geoip6' : '', upstreamPort: 10627,
    bridges: { transport: 'obfs4', lines: ['obfs4 192.0.2.1:443 0123456789ABCDEF0123456789ABCDEF01234567 cert=AAAA iat-mode=0'] }, ptExec: { lyrebird: './lyrebird' } })
  writeFileSync(join(dataDir, 'torrc'), rc)
  const r = spawnSync(which, ['--verify-config', '-f', join(dataDir, 'torrc')], { encoding: 'utf8' })
  assert.equal(r.status, 0, r.stdout + r.stderr)
})

// ------------------------------------------------------------------ Aether

await test('aether: AetherSpec.from / args / env as AetherController builds them', () => {
  const c = { ...P.defaults(), protocol: 'aether', aetherMode: 'masque', aetherScan: 'turbo', aetherHttp2: true, aetherFragment: true, aetherExitLoc: ' de ', aetherNoise: 'gfw', aetherIpv6: false }
  const s = aether.aetherSpec(c)
  assert.deepEqual(aether.aetherArgs(s, 18190), ['--bind', '127.0.0.1:18190', '--masque', '--h2', '--fragment', '--exit-loc', 'de', '--scan', 'turbo', '--noize', 'gfw', '--quick-reconnect', '-4'])
  const env = aether.aetherEnv(s, { workDir: '/w', tmpDir: '/t', port: 18190 })
  assert.equal(env.AETHER_SOCKS, '127.0.0.1:18190'); assert.equal(env.AETHER_MASQUE_HTTP2, '1'); assert.equal(env.AETHER_MASQUE_H2_FRAGMENT, '1')
  assert.equal(env.AETHER_EXIT_LOC, 'de'); assert.equal(env.AETHER_NOIZE, 'gfw'); assert.equal(env.AETHER_IP, '4'); assert.equal(env.HOME, '/w')
  assert.equal(env.AETHER_MASQUE_CONFIG, join('/w', 'aether-masque.toml'))
  for (const [mode, flag] of [['wg', '--wg'], ['gool', '--gool'], ['mim', '--mim'], ['', '--masque']]) assert.equal(aether.aetherArgs(aether.aetherSpec({ ...c, aetherMode: mode, aetherHttp2: false }), 1)[2], flag)
  assert.equal(aether.aetherArgs(aether.aetherSpec({ ...c, aetherIpv6: true }), 1).at(-1), '-6')
  assert.equal(aether.aetherSpec({ ...P.defaults(), protocol: 'psiphon' }), null)
  assert.equal(P.protocolFamily(c), 'aether')
})

await test('aether: Oblivion options (core aether / chain) → OblivionOptions.aetherArgs', () => {
  const ob = JSON.stringify({ core: 'chain', protocol: 'gool', wiwOuter: '162.159.192.1:2408', wiwInner: '188.114.96.1:2408', team: 'acme', accessEmail: 'a@b.c', routeDirect: 'example.ir\nprivate', dataCheck: 'false', quickReconnect: 'false', overrideDns: 'true', dnsPrimary: '9.9.9.9', dnsSecondary: '' })
  const c = { ...P.defaults(), protocol: 'psiphon', oblivionJson: ob }
  assert.equal(P.protocolFamily(c), 'aether')
  assert.ok(aether.oblivion(ob).chain)
  const a = aether.aetherArgs(aether.aetherSpec(c), 20000)
  assert.deepEqual(a.slice(0, 4), ['--bind', '127.0.0.1:20000', '--http-proxy', '127.0.0.1:20001'])
  const has = (...xs) => { const i = a.indexOf(xs[0]); assert.ok(i >= 0, xs[0]); xs.slice(1).forEach((x, k) => assert.equal(a[i + 1 + k], x)) }
  has('--gool'); has('--wiw-outer', '162.159.192.1:2408'); has('--wiw-inner', '188.114.96.1:2408'); has('--keepalive', '5')
  has('--team', 'acme'); has('--access-email', 'a@b.c'); has('--route-direct', 'example.ir,private'); has('--no-data-check'); has('--no-quick-reconnect'); has('--dns', '9.9.9.9')
  assert.ok(!a.includes('--wiw-scan')); assert.ok(!a.includes('--peer'))
  const env = aether.aetherEnv(aether.aetherSpec(c), { workDir: '/w', tmpDir: '/t', port: 20000 })
  assert.equal(env.AETHER_PROTOCOL, undefined); assert.equal(env.AETHER_SCAN, undefined)
})

await test('aether: the real CLI accepts the generated arguments (AETHER_BIN)', async () => {
  const bin = process.env.AETHER_BIN || ['/home/user/Ghajarvpn-/web-desktop/core/linux-x64/aether'].find(existsSync)
  if (!bin || !existsSync(bin)) { console.log('     (no aether binary, skipped)'); return }
  const wd = join(work, 'aether'); mkdirSync(wd, { recursive: true })
  const runs = [
    aether.aetherArgs(aether.aetherSpec({ ...P.defaults(), protocol: 'aether', aetherMode: 'mim', aetherHttp2: true, aetherFragment: true, aetherExitLoc: 'DE', aetherNoise: 'gfw' }), 19190),
    aether.aetherArgs(aether.aetherSpec({ ...P.defaults(), protocol: 'psiphon', oblivionJson: JSON.stringify({ core: 'aether', protocol: 'masque', transport: 'h2', fragment: 'true', echMode: 'auto', perfProfile: 'low', tlsGroups: 'X25519' }) }), 19190)
  ]
  for (const args of runs) {
    const out = await new Promise(resolve => {
      const p = spawn(bin, args, { env: { ...process.env, ...aether.aetherEnv(aether.aetherSpec({ protocol: 'aether' }), { workDir: wd, tmpDir: wd, port: 19190 }) }, cwd: wd })
      let log = ''
      p.stdout.on('data', d => { log += d }); p.stderr.on('data', d => { log += d })
      setTimeout(() => { p.kill('SIGKILL'); resolve(log) }, 2500)
    })
    assert.ok(/Aether v2/.test(out), out.slice(0, 300))
    assert.ok(!/unknown option|Error: Other/.test(out), out.slice(0, 300))
  }
})

// ------------------------------------------------------------------ IKEv2

const ikeCfg = { ...P.parseLink('ikev2://alice:p%40ss@203.0.113.7:500?remote_id=vpn.example.com#IKE'), mtu: 1380 }
function recorder(replies = {}) {
  const calls = []
  const run = async (cmd, args) => {
    calls.push([cmd, ...args])
    const key = Object.keys(replies).find(k => [cmd, ...args].join(' ').includes(k))
    const r = key ? replies[key] : {}
    return { code: 0, stdout: '', stderr: '', ...(typeof r === 'function' ? r(calls) : r) }
  }
  return { calls, run }
}

await test('ikev2: profile fields as IkeController.profileFor reads them', () => {
  const p = ikev2.ikeProfile(ikeCfg)
  assert.equal(p.address, '203.0.113.7'); assert.equal(p.identity, 'vpn.example.com'); assert.equal(p.username, 'alice'); assert.equal(p.password, 'p@ss'); assert.equal(p.mtu, 1380)
  assert.equal(ikev2.ikeProfile({ ...ikeCfg, sni: '', mtu: 9000 }).identity, '203.0.113.7')
  assert.equal(ikev2.ikeProfile({ ...ikeCfg, mtu: 9000 }).mtu, 1400)
  assert.notEqual(ikev2.ikeProfile({ ...ikeCfg, password: 'other' }).name, p.name)
  assert.throws(() => ikev2.ikeProfile({ ...ikeCfg, password: '' }), /رمز/)
})

await test('ikev2 windows: Add-VpnConnection (user scope, EAP) + IPsec parameters, rasdial connect / status / hang up', async () => {
  const R = recorder({ 'rasdial.exe': calls => (calls.at(-1).length === 1 ? { stdout: 'Connected to\r\n' + ikev2.ikeProfile(ikeCfg).name + '\r\nCommand completed successfully.\r\n' } : {}) })
  const vpn = ikev2.osVpn(ikeCfg, { platform: 'win32', run: R.run })
  await vpn.connect()
  const ps = R.calls[0]
  assert.equal(ps[0], 'powershell.exe')
  const script = ps.at(-1)
  assert.ok(/Add-VpnConnection -Name \$n -ServerAddress 'vpn\.example\.com' -TunnelType Ikev2 -AuthenticationMethod Eap/.test(script), script)
  assert.ok(!/AllUserConnection/.test(script))
  assert.ok(/Set-VpnConnectionIPsecConfiguration .*-DHGroup Group14/.test(script))
  assert.deepEqual(R.calls[1], ['rasdial.exe', vpn.profile.name, 'alice', 'p@ss'])
  assert.equal(await vpn.connected(), true)
  await vpn.disconnect()
  assert.deepEqual(R.calls.at(-1), ['rasdial.exe', vpn.profile.name, '/disconnect'])
  assert.ok(/'it''s'/.test(ikev2.windowsScript({ ...vpn.profile, address: "it's", identity: "it's" })))
  const bad = recorder({ 'rasdial.exe': { code: 691, stdout: 'Remote Access error 691 - The remote connection was denied' } })
  await assert.rejects(ikev2.osVpn(ikeCfg, { platform: 'win32', run: bad.run }).connect(), /رد شد/)
})

await test('ikev2 macOS: installs a .mobileconfig first, then scutil --nc start / status / stop', async () => {
  const dir = join(work, 'ike-mac')
  const name = ikev2.ikeProfile(ikeCfg).name
  const first = recorder({ '--nc list': { stdout: '* (Disconnected) 1234 PPP --> L2TP "Other VPN" [PPP:L2TP]\n' } })
  await assert.rejects(ikev2.osVpn(ikeCfg, { platform: 'darwin', run: first.run, dir }).connect(), e => e.needsUserAction === true)
  const file = first.calls.find(c => c[0] === '/usr/bin/open')[1]
  const xml = readFileSync(file, 'utf8')
  for (const s of ['<string>IKEv2</string>', '<key>RemoteAddress</key><string>203.0.113.7</string>', '<key>RemoteIdentifier</key><string>vpn.example.com</string>',
    '<key>ExtendedAuthEnabled</key><integer>1</integer>', '<key>AuthName</key><string>alice</string>', '<key>AuthPassword</key><string>p@ss</string>',
    '<key>AuthenticationMethod</key><string>None</string>', `<string>${name}</string>`, '<key>MTU</key><integer>1380</integer>']) assert.ok(xml.includes(s), s)
  const plutil = spawnSync('sh', ['-c', 'command -v plutil || command -v xmllint'], { encoding: 'utf8' }).stdout.trim()
  if (plutil.endsWith('xmllint')) assert.equal(spawnSync(plutil, ['--noout', file]).status, 0)
  let status = 'Connecting'
  const R = recorder({ '--nc list': { stdout: `* (Disconnected) 1 IPSec "${name}" [IPSec]\n` }, '--nc status': () => ({ stdout: status + '\n' }), '--nc start': () => { status = 'Connected'; return {} } })
  const vpn = ikev2.osVpn(ikeCfg, { platform: 'darwin', run: R.run, dir })
  await vpn.connect()
  assert.ok(R.calls.some(c => c.join(' ') === `/usr/sbin/scutil --nc start ${name}`))
  assert.equal(await vpn.connected(), true)
  await vpn.disconnect()
  assert.deepEqual(R.calls.at(-1), ['/usr/sbin/scutil', '--nc', 'stop', name])
})

await test('ikev2 linux: nmcli + NetworkManager-strongswan (EAP), up / state / down; clear error without the plugin', async () => {
  await assert.rejects(ikev2.osVpn(ikeCfg, { platform: 'linux', run: recorder().run, exists: () => false }).connect(), /network-manager-strongswan/)
  assert.equal(ikev2.supported('linux', () => false), false)
  const exists = f => f.endsWith('/usr/lib/NetworkManager/VPN/nm-strongswan-service.name')
  const name = ikev2.ikeProfile(ikeCfg).name
  const R = recorder({ 'connection show id': { stdout: 'GENERAL.STATE:activated\n' }, '-f NAME': { stdout: 'Wired connection 1\nGhajar IKEv2 deadbeef\n' } })
  const vpn = ikev2.osVpn(ikeCfg, { platform: 'linux', run: R.run, exists })
  await vpn.connect()
  assert.ok(R.calls.some(c => c.join(' ') === 'nmcli connection delete id Ghajar IKEv2 deadbeef'))
  const add = R.calls.find(c => c[1] === 'connection' && c[2] === 'add')
  assert.ok(add.includes('strongswan'))
  const data = add[add.indexOf('vpn.data') + 1]
  for (const kv of ['address=203.0.113.7', 'method=eap', 'user=alice', 'virtual=yes', 'remote-identity=vpn.example.com', 'password-flags=0']) assert.ok(data.includes(kv), kv)
  assert.equal(add[add.indexOf('vpn.secrets') + 1], 'password=p@ss')
  assert.ok(R.calls.some(c => c.join(' ').includes(`connection up id ${name}`)))
  assert.equal(await vpn.connected(), true)
  await vpn.disconnect()
  assert.deepEqual(R.calls.at(-1), ['nmcli', 'connection', 'down', 'id', name])
})

rmSync(work, { recursive: true, force: true })
console.log(failures.length ? `\n${failures.length} failure(s): ${failures.join(', ')}` : `\nengines: ${passed} passed`)
process.exit(failures.length ? 1 : 0)
