// Tests for engine/parser.js. Run: node web-desktop/engine/test/parser.test.mjs
// Sample inputs and expected values marked "Kotlin:" come from the Android
// app's unit tests (app/src/test/...), so both apps are held to the same answers.
import { createRequire } from 'node:module'
import assert from 'node:assert/strict'

const require = createRequire(import.meta.url)
const P = require('../parser.js')

let passed = 0
const failures = []
function test(name, fn) {
  try { fn(); passed++ } catch (e) { failures.push([name, e]) }
}

const noId = c => { const { id, ...rest } = c; return rest }
const link = l => { const c = P.parseLink(l); assert.ok(c, `did not parse: ${l}`); return c }
/** parse(toLink(c)) == c, ids aside (the Kotlin tests' copy(id = "x")). */
function roundTrip(c, patch = {}) {
  const l = P.toShareLink(c)
  assert.ok(l, `no share link for ${c.protocol}`)
  const back = P.parseLink(l)
  assert.ok(back, `share link did not parse back: ${l}`)
  assert.deepEqual(noId({ ...back, ...patch }), noId({ ...c, ...patch }), `round trip of ${l}`)
  return back
}
const x = c => JSON.parse(c.extra)
const KEY = '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
const UUID = '11111111-2222-3333-4444-555555555555'
const WG_KEY = 'aGVsbG8taGVsbG8taGVsbG8taGVsbG8taGVsbG8tMTI='
const b64 = s => Buffer.from(s, 'utf8').toString('base64')
const b64url = s => Buffer.from(s, 'utf8').toString('base64url')

// ------------------------------------------------------------ ProxyConfig

test('defaults() has every ProxyConfig field and a fresh id', () => {
  const a = P.defaults(); const b = P.defaults()
  assert.notEqual(a.id, b.id)
  assert.match(a.id, /^[0-9a-f-]{36}$/)
  for (const k of ['name', 'protocol', 'address', 'port', 'uuid', 'password', 'method', 'alterId', 'encryption', 'flow',
    'network', 'security', 'sni', 'publicKey', 'shortId', 'fingerprint', 'path', 'host', 'serviceName', 'mode', 'alpn',
    'headerType', 'privateKey', 'localAddress', 'mtu', 'reserved', 'hyObfs', 'hyObfsPassword', 'hyUpMbps', 'hyDownMbps',
    'allowInsecure', 'pinnedCertSha256', 'cipherSuites', 'randomSubdomain', 'maskType', 'maskDomain', 'maskPassword',
    'echConfigList', 'torCountry', 'torThroughVpn', 'torBaseId', 'chainId', 'psiphonMode', 'psiphonCountry',
    'psiphonCdnIps', 'psiphonCdnSni', 'aetherMode', 'aetherScan', 'aetherNoise', 'aetherHttp2', 'aetherExitLoc',
    'aetherFragment', 'aetherIpv6', 'oblivionJson', 'extra', 'source', 'locked', 'favorite', 'id', 'subId']) {
    assert.ok(k in a, k)
  }
  assert.equal(a.encryption, 'none'); assert.equal(a.network, 'tcp'); assert.equal(a.fingerprint, 'chrome')
  assert.equal(a.psiphonMode, 'auto'); assert.equal(a.aetherMode, 'masque'); assert.equal(a.aetherHttp2, true)
  assert.equal(a.source, 'PERSONAL')
})

test('toJson / fromJson round trip, key order and Kotlin defaults', () => {
  const c = link('vless://' + UUID + '@1.2.3.4:443?security=reality&pbk=PK&sid=ab#R')
  const j = P.toJson(c)
  assert.equal(Object.keys(j)[0], 'id'); assert.equal(Object.keys(j).at(-1), 'extra')
  assert.deepEqual(P.fromJson(JSON.stringify(j)), c)
  const sparse = P.fromJson({ name: 'n', protocol: 'vmess', address: 'a', port: '443', source: 'NOPE', allowInsecure: 'TRUE' })
  assert.equal(sparse.port, 443); assert.equal(sparse.source, 'PERSONAL'); assert.equal(sparse.allowInsecure, true)
  assert.equal(sparse.aetherHttp2, false) // fromJson's default differs from the constructor's, as in Kotlin
  assert.equal(P.fromJson('not json'), null)
})

// ------------------------------------------------------------ Xray links

test('vless reality with a Persian + emoji name round-trips', () => {
  const name = 'سرور آلمان 🇩🇪'
  const c = link(`vless://${UUID}@203.0.113.5:443?type=tcp&security=reality&pbk=PUBKEY&sid=ab12&fp=firefox&sni=www.apple.com&flow=xtls-rprx-vision&encryption=none#${encodeURIComponent(name)}`)
  assert.equal(c.name, name); assert.equal(c.protocol, 'vless'); assert.equal(c.uuid, UUID)
  assert.equal(c.security, 'reality'); assert.equal(c.publicKey, 'PUBKEY'); assert.equal(c.shortId, 'ab12')
  assert.equal(c.fingerprint, 'firefox'); assert.equal(c.flow, 'xtls-rprx-vision'); assert.equal(c.sni, 'www.apple.com')
  roundTrip(c)
})

test('vless grpc / xhttp / ech / mask fields', () => {
  const g = link(`vless://${UUID}@g.example.com:443?type=grpc&security=tls&path=svc&mode=multi#G`)
  assert.equal(g.network, 'grpc'); assert.equal(g.serviceName, 'svc'); assert.equal(g.mode, 'multi')
  const h = link(`vless://${UUID}@h.example.com:443?type=splithttp&security=tls&path=%2Fx&host=cdn.example.com&ech=AEX&mask=xdns&maskDomain=t.example.com&maskPass=pw&allowInsecure=1#H`)
  assert.equal(h.network, 'xhttp'); assert.equal(h.path, '/x'); assert.equal(h.echConfigList, 'AEX')
  assert.equal(h.maskType, 'xdns'); assert.equal(h.maskDomain, 't.example.com'); assert.equal(h.maskPassword, 'pw')
  assert.equal(h.allowInsecure, true)
  // ConfigShare.userLink never writes allowInsecure, so it does not survive the share link.
  assert.equal(P.parseLink(P.toShareLink(h)).allowInsecure, false)
  roundTrip(h, { allowInsecure: false })
  // Kotlin: SingBoxConfigTest xrayProtocolsAreNotRoutedToSingBox
  assert.equal(P.protocolFamily(link('vless://11111111-1111-1111-1111-111111111111@9.9.9.9:443?security=tls&type=ws#V')), 'xray')
})

test('vmess base64 JSON', () => {
  const j = { v: '2', ps: 'وی‌مس', add: 'v.example.com', port: '443', id: UUID, aid: '0', scy: 'auto', net: 'ws', type: 'none', host: 'cdn.example.com', path: '/ray', tls: 'tls', sni: 'v.example.com', fp: 'chrome' }
  const c = link('vmess://' + b64(JSON.stringify(j)))
  assert.equal(c.name, 'وی‌مس'); assert.equal(c.address, 'v.example.com'); assert.equal(c.port, 443)
  assert.equal(c.network, 'ws'); assert.equal(c.security, 'tls'); assert.equal(c.path, '/ray'); assert.equal(c.headerType, '')
  roundTrip(c)
  // URL-safe, unpadded, with a numeric port.
  const u = link('vmess://' + b64url(JSON.stringify({ ...j, port: 8443, net: 'grpc', path: 'gs' })))
  assert.equal(u.port, 8443); assert.equal(u.serviceName, 'gs')
})

test('trojan with an IPv6 host round-trips', () => {
  const c = link('trojan://p%40ss@[2001:db8::1]:443?type=ws&path=%2Fws&host=cdn.example.com&sni=cdn.example.com#T6')
  assert.equal(c.address, '2001:db8::1'); assert.equal(c.port, 443); assert.equal(c.password, 'p@ss')
  assert.equal(c.security, 'tls') // trojan's default
  roundTrip(c)
})

test('shadowsocks: SIP002 base64, plain 2022 userinfo, legacy base64', () => {
  const a = link(`ss://${b64url('aes-256-gcm:pa:ss')}@198.51.100.2:8388#${encodeURIComponent('شادو ساکس')}`)
  assert.equal(a.protocol, 'shadowsocks'); assert.equal(a.method, 'aes-256-gcm'); assert.equal(a.password, 'pa:ss')
  assert.equal(a.name, 'شادو ساکس')
  roundTrip(a)
  const b = link('ss://2022-blake3-aes-128-gcm:a2V5a2V5a2V5a2V5a2V5aw%3D%3D@[2001:db8::2]:443#plain')
  assert.equal(b.address, '2001:db8::2')
  assert.equal(b.method, '2022-blake3-aes-128-gcm'); assert.equal(b.password, 'a2V5a2V5a2V5a2V5a2V5aw==')
  const c = link('ss://' + b64('chacha20-ietf-poly1305:pw@10.0.0.1:8389') + '#legacy')
  assert.equal(c.method, 'chacha20-ietf-poly1305'); assert.equal(c.address, '10.0.0.1'); assert.equal(c.port, 8389)
  assert.equal(link('ss://' + b64('aes-128-gcm:x@h.example.com:1')).name, 'Shadowsocks')
})

test('shadowsocks plugins: obfs-local, v2ray-plugin, shadow-tls', () => {
  const user = b64url('aes-128-gcm:pw')
  const o = link(`ss://${user}@s.example.com:80?plugin=${encodeURIComponent('obfs-local;obfs=http;obfs-host=bing.com')}#o`)
  assert.equal(o.headerType, 'http'); assert.equal(o.host, 'bing.com'); assert.equal(o.network, 'tcp')
  const v = link(`ss://${user}@s.example.com:443?plugin=${encodeURIComponent('v2ray-plugin;mode=websocket;host=cdn.example.com;path=/v;tls=1')}#v`)
  assert.equal(v.network, 'ws'); assert.equal(v.host, 'cdn.example.com'); assert.equal(v.path, '/v')
  assert.equal(v.security, 'tls'); assert.equal(v.sni, 'cdn.example.com')
  // Kotlin quirk: a bare ";tls" after the plugin name is dropped.
  assert.equal(link(`ss://${user}@s.example.com:443?plugin=${encodeURIComponent('v2ray-plugin;tls;host=a')}#v`).security, 'none')
  // Kotlin: MoreEnginesTest shadowTlsPluginBecomesTwoOutbounds
  const st = link(`ss://${b64url('2022-blake3-aes-128-gcm:k')}@1.2.3.4:443?plugin=${encodeURIComponent('shadow-tls;host=www.apple.com;password=pw;version=3')}#s`)
  assert.equal(st.protocol, 'shadowtls'); assert.equal(st.sni, 'www.apple.com'); assert.equal(st.password, 'pw'); assert.equal(st.alterId, 3)
  assert.deepEqual(x(st), { ss: { method: '2022-blake3-aes-128-gcm', password: 'k' } })
  assert.equal(P.protocolFamily(st), 'singbox')
  roundTrip(st)
})

test('socks / socks5 / http proxies', () => {
  const s = link('socks5://us%20er:p%40ss@10.0.0.9:1080#S5')
  assert.equal(s.protocol, 'socks'); assert.equal(s.uuid, 'us er'); assert.equal(s.password, 'p@ss'); assert.equal(s.port, 1080)
  const s4 = link('socks://10.0.0.9:1081')
  assert.equal(s4.name, '10.0.0.9:1081')
  const h = link('http://user:pw@proxy.example.com:3128/#H')
  assert.equal(h.protocol, 'http'); assert.equal(h.address, 'proxy.example.com'); assert.equal(h.port, 3128)
  assert.equal(link('socks5://[::1]:1080').address, '[::1]') // brackets kept, as in Kotlin
  assert.equal(P.parseLink('http://example.com/sub'), null)
  assert.equal(P.toShareLink(s), null) // ConfigShare has no socks link
})

test('hysteria2 / hy2', () => {
  const c = link('hysteria2://p%2Bw@h.example.com:8443?sni=h.example.com&obfs=salamander&obfs-password=salt&insecure=1&upmbps=20&downmbps=100&alpn=h3#H2')
  assert.equal(c.password, 'p+w'); assert.equal(c.hyObfs, 'salamander'); assert.equal(c.hyObfsPassword, 'salt')
  assert.equal(c.allowInsecure, true); assert.equal(c.hyUpMbps, 20); assert.equal(c.hyDownMbps, 100)
  roundTrip(c)
  const hy = link('hy2://pw@[2001:db8::3]:443?peer=p.example.com#x')
  assert.equal(hy.address, '2001:db8::3'); assert.equal(hy.sni, 'p.example.com')
  assert.equal(P.protocolFamily(hy), 'xray') // Kotlin: SingBoxConfigTest
})

test('wireguard:// and wg:// URIs', () => {
  const c = link(`wireguard://${encodeURIComponent(WG_KEY)}@198.51.100.7:51820?publickey=${encodeURIComponent(WG_KEY)}&address=10.0.0.2%2F32&mtu=1280&reserved=1,2,3#WG`)
  assert.equal(c.protocol, 'wireguard'); assert.equal(c.privateKey, WG_KEY); assert.equal(c.publicKey, WG_KEY)
  assert.equal(c.localAddress, '10.0.0.2/32'); assert.equal(c.mtu, 1280); assert.equal(c.reserved, '1,2,3')
  const w = link(`wg://${WG_KEY}@[2001:db8::7]:51820?pubkey=${WG_KEY}`)
  assert.equal(w.address, '2001:db8::7'); assert.equal(w.name, 'WireGuard 2001:db8::7')
  assert.equal(P.protocolFamily(w), 'xray')
})

// ------------------------------------------------------------ sing-box family

test('tuic (Kotlin: SingBoxConfigTest tuicLinkBecomesATuicOutbound)', () => {
  const c = link(`tuic://${UUID}:pw@tuic.example.com:8443?sni=cdn.example.com&alpn=h3&congestion_control=bbr&udp_relay_mode=native&allow_insecure=1#T`)
  assert.equal(c.uuid, UUID); assert.equal(c.password, 'pw'); assert.equal(c.method, 'bbr'); assert.equal(c.mode, 'native')
  assert.equal(c.allowInsecure, true); assert.equal(c.sni, 'cdn.example.com'); assert.equal(c.alpn, 'h3')
  assert.equal(P.protocolFamily(c), 'singbox')
  roundTrip(c)
})

test('hysteria v1 (Kotlin: hysteriaV1KeepsAuthAndObfs)', () => {
  const c = link('hysteria://h.example.com:36712?auth=tok&peer=h.example.com&upmbps=20&downmbps=100&obfs=xplus&obfsParam=salt#H')
  assert.equal(c.password, 'tok'); assert.equal(c.hyUpMbps, 20); assert.equal(c.hyDownMbps, 100); assert.equal(c.hyObfsPassword, 'salt')
  roundTrip(c)
})

test('Kotlin: SingBoxConfigTest shareLinksRoundTrip', () => {
  for (const l of [
    `tuic://${UUID}:pw@tuic.example.com:8443?sni=cdn.example.com&congestion_control=bbr#T`,
    'hysteria://h.example.com:36712?auth=tok&peer=h.example.com&upmbps=20#H',
    'anytls://secret@a.example.com:443?sni=a.example.com&fp=chrome#A',
    'ssh://bob:pw@ssh.example.com:2222#S',
    'openconnect://u:p@vpn.example.com:8443?flavor=fortinet#O'
  ]) roundTrip(link(l))
})

test('anytls', () => {
  const c = link('anytls://pw@a.example.com:8443?sni=cdn.example.com#a')
  assert.equal(c.port, 8443); assert.equal(c.sni, 'cdn.example.com'); assert.equal(c.fingerprint, '')
  assert.equal(P.parseLink('anytls://@a.example.com:443#nopass'), null)
})

test('masque (Kotlin: masqueDefaultsToHttp3…, masqueFormBuildsAConnectIpEndpoint)', () => {
  const d = link('masque://mq.example.com#Plain')
  assert.equal(d.port, 443); assert.equal(d.mode, '3'); assert.equal(d.uuid, '')
  const c = link(`masque://u:p@mq.example.com:8443?version=2&path=${encodeURIComponent('/ip/{target}/{ipproto}/')}&sni=cdn.example.com&fp=chrome&pin=${'a'.repeat(64)}&mtu=1350#M`)
  assert.equal(c.mode, '2'); assert.equal(c.mtu, 1350); assert.equal(c.path, '/ip/{target}/{ipproto}/'); assert.equal(c.uuid, 'u')
  roundTrip(c)
})

test('tailscale and tailcat', () => {
  const t = link('tailscale://tskey-auth-abc@hs.example.com?control=https%3A%2F%2Fhs.example.com&exit=100.64.0.9&flags=ephemeral,routes,bogus#TS')
  assert.equal(t.password, 'tskey-auth-abc'); assert.equal(t.address, 'hs.example.com'); assert.equal(t.port, 443)
  assert.equal(t.host, 'https://hs.example.com'); assert.equal(t.path, '100.64.0.9'); assert.equal(t.headerType, 'ephemeral,routes')
  const share = P.toShareLink(t)
  assert.ok(!share.includes('tskey'), 'the auth key never leaves in a share link')
  assert.equal(link('tailscale://#x').address, 'controlplane.tailscale.com')
  const cat = link('tailcat://?pub=nodekey%3Aaa&disco=discokey%3Abb&region=7&derp=https%3A%2F%2Fderp.example.org%2Fmap#cat')
  assert.equal(cat.publicKey, 'nodekey:aa'); assert.equal(cat.uuid, 'discokey:bb'); assert.equal(cat.mode, '7')
  assert.equal(cat.address, 'derp.example.org')
  assert.equal(P.parseLink('tailcat://x?pub=a#no-disco'), null)
})

test('ssh: default port, transports, payload (Kotlin: HelperEnginesTest)', () => {
  const s = link('ssh://bob:pa%40ss@ssh.example.com#S')
  assert.equal(s.port, 22); assert.equal(s.uuid, 'bob'); assert.equal(s.password, 'pa@ss'); assert.equal(s.extra, '')
  const w = link('ssh://bob:pw@ssh.example.com:22?mode=wss&proxy=cdn.example.com:443&sni=front.example.com&wspath=%2Fssh#s')
  assert.deepEqual(x(w).transport, { mode: 'wss', proxyHost: 'cdn.example.com', proxyPort: 443, sni: 'front.example.com', wsPath: '/ssh' })
  const payload = b64url('GET / HTTP/1.1[crlf]Host: [host][crlf][crlf]')
  const p = link(`ssh://bob:pw@ssh.example.com:2222?mode=payload&proxy=10.0.0.1:8080&payload=${payload}#p`)
  assert.equal(x(p).transport.payload, 'GET / HTTP/1.1[crlf]Host: [host][crlf][crlf]')
  roundTrip(p)
  const pem = '-----BEGIN OPENSSH PRIVATE KEY-----\nabc\n-----END OPENSSH PRIVATE KEY-----'
  assert.equal(link(`ssh://u@h.example.com?pk=${b64url(pem)}`).privateKey, pem)
  assert.ok(!P.toShareLink(link(`ssh://u@h.example.com?pk=${b64url(pem)}`)).includes('pk='))
})

test('openconnect / anyconnect', () => {
  const c = link('openconnect://u:p@vpn.example.com?flavor=gp&authgroup=Staff&os=win&ua=AnyConnect&reconnect=60&nodtls=1&noipv6=1&mtu=1300#O')
  assert.equal(c.mode, 'gp'); assert.equal(c.port, 443); assert.equal(c.mtu, 1300)
  assert.deepEqual(x(c), { authGroup: 'Staff', reportedOs: 'win', userAgent: 'AnyConnect', reconnect: 60, noUdp: true, ipv6Off: true })
  roundTrip(c)
  const a = link('anyconnect://vpn.example.com:4443#A')
  assert.equal(a.protocol, 'openconnect'); assert.equal(a.mode, 'anyconnect'); assert.equal(a.port, 4443); assert.equal(a.extra, '')
})

test('mieru / mierus and brook (Kotlin: mieruAndBrookLinks)', () => {
  const m = link('mierus://u:p@1.2.3.4?port=6666&protocol=TCP&profile=default#m')
  assert.equal(m.protocol, 'mieru'); assert.equal(m.address, '1.2.3.4'); assert.equal(m.port, 6666); assert.equal(m.uuid, 'u')
  assert.equal(x(m).url, 'mierus://u:p@1.2.3.4?port=6666&protocol=TCP&profile=default')
  assert.equal(P.toShareLink(m), 'mierus://u:p@1.2.3.4?port=6666&protocol=TCP&profile=default#m')
  const full = link('mieru://CgdkZWZhdWx0#full')
  assert.equal(full.address, ''); assert.equal(full.port, 0); assert.equal(full.name, 'full')
  const b = link('brook://wsserver?password=pw&wsserver=ws%3A%2F%2Fb.example.com%3A8080#b')
  assert.equal(b.protocol, 'brook'); assert.equal(b.address, 'b.example.com'); assert.equal(b.port, 8080); assert.equal(b.mode, 'wsserver')
  assert.equal(P.toShareLink(b), 'brook://wsserver?password=pw&wsserver=ws%3A%2F%2Fb.example.com%3A8080#b')
})

test('naive+https / naive+quic / naive (Kotlin: naiveLink)', () => {
  const c = link('naive+https://u:p@n.example.com:8443#n')
  assert.equal(c.sni, 'n.example.com'); assert.equal(c.mode, 'https')
  roundTrip(c)
  const q = link('naive+quic://u:p@n.example.com?sni=front.example.com#q')
  assert.equal(q.mode, 'quic'); assert.equal(q.port, 443); assert.equal(q.sni, 'front.example.com')
  roundTrip(q)
  assert.equal(link('naive://n.example.com').mode, 'https')
})

test('juicity (Kotlin: juicityLinkAndConfig)', () => {
  const c = link(`juicity://${UUID}:pw@j.example.com:443?congestion_control=bbr&sni=j.example.com#j`)
  assert.equal(c.uuid, UUID); assert.equal(c.method, 'bbr')
  roundTrip(c)
  assert.equal(P.parseLink('juicity://nocolon@j.example.com:443#j'), null) // Kotlin quirk: uuid needs a ':'
})

test('sstp and softether (Kotlin: MoreEnginesTest)', () => {
  const pin = '6ecdebdb14974ab295edaea132cd91c1cfbfa7812c386093b0d7eecc3b66ebac'
  const s = link(`sstp://ghtest:p%40ss@vpn.example.com?auth=mschapv2&pin=${pin}&mtu=1350#S`)
  assert.equal(s.port, 443); assert.equal(s.password, 'p@ss'); assert.equal(s.method, 'mschapv2'); assert.equal(s.mtu, 1350)
  assert.equal(P.engineFor(s), 'SINGBOX')
  roundTrip(s)
  const e = link('softether://bob:pw@se.example.com:5555?hub=VPN&ip=10.0.0.9%2F24&gw=10.0.0.1&dns=1.1.1.1&auth=plain#SE')
  assert.equal(e.port, 5555); assert.deepEqual(x(e), { hub: 'VPN', plain: true, ip: '10.0.0.9/24', gw: '10.0.0.1', dns: '1.1.1.1' })
  roundTrip(e)
  const d = link('softether://u:p@h.example.com#d')
  assert.equal(d.port, 443); assert.equal(x(d).hub, 'DEFAULT')
  assert.equal(link('sstp://u:p@[2001:db8::9]#v6').address, '2001:db8::9')
})

const AWG_CONF = '[Interface]\nPrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=\nAddress = 10.8.0.2/32\nJc = 4\nH1 = 1234\n\n' +
  '[Peer]\nPublicKey = xTIBA5rboUvnH4htodjb6e697QjLERt1NAB4mZqp8Dg=\nEndpoint = vpn.example.com:51820\nAllowedIPs = 0.0.0.0/0\n'

test('AmneziaWG conf, amneziawg:// and awg:// (Kotlin: amneziaConfIsDetectedAndRunsOnTheHelper)', () => {
  const c = P.parseWireguardConf(AWG_CONF)
  assert.equal(c.protocol, 'amneziawg'); assert.equal(P.protocolFamily(c), 'singbox')
  assert.equal(x(c).conf, AWG_CONF.trim())
  const back = roundTrip(c)
  assert.equal(back.name, c.name)
  const named = link('awg://' + b64(AWG_CONF).replace(/=+$/, '') + '#' + encodeURIComponent('امنزیا'))
  assert.equal(named.name, 'امنزیا'); assert.equal(named.address, 'vpn.example.com')
  const wg = P.parseWireguardConf(AWG_CONF.replace('Jc = 4\nH1 = 1234\n', ''))
  assert.equal(wg.protocol, 'wireguard'); assert.equal(P.protocolFamily(wg), 'xray'); assert.equal(P.toShareLink(wg), null)
})

// ------------------------------------------------------------ DNS tunnels

test('dnstt (Kotlin: DnsttProfileTest)', () => {
  const c = link(`dnstt://t.example.com?pubkey=${KEY}&transport=udp&resolver=8.8.8.8#D`)
  assert.equal(c.protocol, 'dnstt'); assert.equal(c.address, '8.8.8.8'); assert.equal(c.port, 53); assert.equal(c.host, 't.example.com')
  assert.equal(c.method, 'socks'); assert.equal(P.engineFor(c), 'SINGBOX'); assert.equal(P.protocolFamily(c), 'dnstunnel')
  const s = link(`dnstt://bob:pw@t.example.com?pubkey=${KEY}&doh=https%3A%2F%2Fdns.example%2Fdns-query&upstream=ssh#D`)
  assert.equal(s.mode, 'doh'); assert.equal(s.address, 'dns.example'); assert.equal(s.port, 443); assert.equal(s.method, 'ssh')
  assert.equal(s.path, 'https://dns.example/dns-query'); assert.equal(s.uuid, 'bob')
  roundTrip(s)
  assert.equal(link(`dnstt://t.example.com?pubkey=${KEY}&transport=dot&resolver=1.1.1.1#D`).port, 853)
  assert.equal(P.parseLink('dnstt://t.example.com?resolver=1.1.1.1#nokey'), null)
})

test('vaydns / noizdns / slipstream (Kotlin: DnsFamilyTest)', () => {
  for (const l of [
    `vaydns://v.example.com?pubkey=${KEY}&transport=udp&resolver=1.1.1.1:53&upstream=ssh&record=null&compat=1#v`,
    `noizdns://u:p@n.example.com?pubkey=${KEY}&transport=doh&doh=https%3A%2F%2Fdns.example%2Fdns-query&upstream=socks&noiz=1&stealth=0#n`
  ]) roundTrip(link(l))
  const v = link(`vaydns://v.example.com?pubkey=${KEY}&resolver=1.1.1.1&record=null&qname=120#v`)
  assert.deepEqual(x(v), { recordType: 'null', maxQnameLen: 120 })
  const sl = link('slipstream://s.example.com?resolver=9.9.9.9#s')
  assert.equal(sl.protocol, 'slipstream'); assert.equal(sl.publicKey, '') // slipstream needs no key
})

test('masterdns / stormdns / cottendns (Kotlin: masterFamilyWritesTomlAndResolvers)', () => {
  for (const p of ['masterdns', 'stormdns', 'cottendns']) {
    const c = link(`${p}://s3cr%22et@m.example.com?resolver=8.8.8.8,1.1.1.1:5353&enc=3#m`)
    assert.equal(c.protocol, p); assert.equal(c.password, 's3cr"et'); assert.equal(c.host, 'm.example.com')
    assert.equal(c.address, '8.8.8.8'); assert.equal(c.port, 53); assert.equal(c.mode, 'udp')
    assert.deepEqual(x(c), { enc: 3, resolvers: '1.1.1.1:5353' })
    assert.equal(P.protocolFamily(c), 'dnstunnel')
    roundTrip(c)
  }
  assert.equal(P.parseLink('masterdns://m.example.com?resolver=8.8.8.8#nokey'), null)
})

// ------------------------------------------------------------ IKEv2, Tor

test('ikev2', () => {
  const c = link('ikev2://user:p%40ss@vpn.example.com/remote.example.com#' + encodeURIComponent('دفتر'))
  assert.equal(c.protocol, 'ikev2'); assert.equal(c.port, 500); assert.equal(c.sni, 'remote.example.com')
  assert.equal(c.uuid, 'user'); assert.equal(c.password, 'p@ss'); assert.equal(c.name, 'دفتر'); assert.equal(c.network, 'ikev2')
  const q = link('ikev2://u:p@vpn.example.com:4500?remote_id=id.example.com')
  assert.equal(q.port, 4500); assert.equal(q.sni, 'id.example.com'); assert.equal(q.name, 'vpn.example.com')
  assert.equal(P.protocolFamily(q), 'ikev2')
  assert.equal(P.parseLink('ikev2://nocreds@vpn.example.com'), null)
})

test('Tor bridge lines (Kotlin: torBridgeLinesImportAndReachTorrc)', () => {
  const lines = 'obfs4 192.0.2.10:443 0123456789ABCDEF0123456789ABCDEF01234567 cert=AAAA iat-mode=0\nobfs4 192.0.2.11:443 0123456789ABCDEF0123456789ABCDEF01234568 cert=BBBB iat-mode=0'
  const c = link(lines)
  assert.equal(c.protocol, 'tor'); assert.equal(c.name, 'Tor (obfs4)'); assert.equal(P.protocolFamily(c), 'tor')
  assert.equal(x(c).pt, 'obfs4'); assert.equal(x(c).bridges.split('\n').length, 2)
  assert.equal(link('Bridge webtunnel 192.0.2.3:443 url=https://x.example/y').extra.includes('"pt":"webtunnel"'), true)
  // parseBundle splits lines first (as ConfigParser.parseBundle does): one config per bridge.
  assert.equal(P.parseBundle(lines).length, 2)
})

// ------------------------------------------------------------ files and bundles

test('WireGuard .conf via parseBundle', () => {
  const conf = `[Interface]\nPrivateKey = ${WG_KEY}\nAddress = 10.0.0.2/32 # me\nMTU = 1420\n[Peer]\nPublicKey = ${WG_KEY}\nPresharedKey = PSK\nEndpoint = [2001:db8::5]:51820\n`
  const [c] = P.parseBundle(conf)
  assert.equal(c.protocol, 'wireguard'); assert.equal(c.address, '2001:db8::5'); assert.equal(c.port, 51820)
  assert.equal(c.mtu, 1420); assert.equal(c.password, 'PSK'); assert.equal(c.localAddress, '10.0.0.2/32')
})

test('Xray JSON array (Kotlin: jsonSubscriptionArrayReadsAllNineProfilesAndSkipsDirectOutbounds)', () => {
  const json = '[' + Array.from({ length: 9 }, (_, k) => `
    {"remarks":"source ${k + 1}","log":{},"inbounds":[],"outbounds":[
      {"protocol":"vless","settings":{"vnext":[{"address":"server${k + 1}.example","port":443,
        "users":[{"id":"00000000-0000-0000-0000-000000000001","encryption":"none"}]}]},
        "streamSettings":{"network":"ws","security":"tls","tlsSettings":{"serverName":"sni.example"},
          "wsSettings":{"path":"/subpath","headers":{"Host":"host.example"}}}},
      {"protocol":"freedom"},{"protocol":"blackhole"}]}`).join(',') + ']'
  const configs = P.parseBundle(json, 'COMMUNITY')
  assert.equal(configs.length, 9)
  assert.ok(configs.every(c => c.protocol === 'vless' && c.network === 'ws' && c.sni === 'sni.example' && c.path === '/subpath' && c.host === 'host.example' && c.source === 'COMMUNITY'))
  assert.equal(configs[0].name, 'source 1')
})

test('Xray JSON single outbound: reality, trojan, shadowsocks', () => {
  const r = P.parseBundle(JSON.stringify({ outbounds: [{ tag: 'proxy', protocol: 'vless', settings: { vnext: [{ address: 'r.example.com', port: 443, users: [{ id: UUID, flow: 'xtls-rprx-vision' }] }] },
    streamSettings: { network: 'raw', security: 'reality', realitySettings: { serverName: 'www.apple.com', publicKey: 'PK', shortId: 'ab', fingerprint: 'safari' } } },
  { protocol: 'trojan', tag: 'tj', settings: { servers: [{ address: 't.example.com', port: 443, password: 'pw' }] }, streamSettings: { network: 'grpc', security: 'tls', grpcSettings: { serviceName: 'gs' } } },
  { protocol: 'shadowsocks', settings: { servers: [{ address: 's.example.com', port: '8388', method: 'aes-128-gcm', password: 'k' }] } }] }))
  assert.equal(r.length, 3)
  assert.equal(r[0].security, 'reality'); assert.equal(r[0].publicKey, 'PK'); assert.equal(r[0].fingerprint, 'safari'); assert.equal(r[0].name, 'www.apple.com')
  assert.equal(r[1].name, 'tj'); assert.equal(r[1].serviceName, 'gs')
  assert.equal(r[2].port, 8388); assert.equal(r[2].name, 's.example.com:8388')
})

test('sing-box JSON with ShadowTLS detour (Kotlin: singBoxOutboundsAndShadowTlsDetour)', () => {
  const sb = `{"outbounds":[
    {"type":"vless","tag":"v","server":"a.example.com","server_port":443,"uuid":"${UUID}",
     "tls":{"enabled":true,"server_name":"a.example.com","utls":{"enabled":true,"fingerprint":"chrome"}},
     "transport":{"type":"grpc","service_name":"gs"}},
    {"type":"shadowsocks","tag":"ss","method":"2022-blake3-aes-128-gcm","password":"k","detour":"st"},
    {"type":"shadowtls","tag":"st","server":"1.2.3.4","server_port":443,"version":3,"password":"pw","tls":{"enabled":true,"server_name":"www.apple.com"}},
    {"type":"tuic","tag":"t","server":"t.example.com","server_port":8443,"uuid":"${UUID}","password":"p",
     "congestion_control":"bbr","tls":{"enabled":true,"server_name":"t.example.com","alpn":["h3"]}},
    {"type":"direct","tag":"direct"},{"type":"selector","tag":"sel","outbounds":["v"]}
  ],
  "endpoints":[{"type":"wireguard","tag":"wg","address":["10.0.0.2/32"],"private_key":"${WG_KEY}",
     "peers":[{"address":"198.51.100.7","port":51820,"public_key":"${WG_KEY}"}]}]}`
  const by = Object.fromEntries(P.parseJsonOutbounds(sb).map(c => [c.name, c]))
  assert.deepEqual(Object.keys(by).sort(), ['ss', 't', 'v', 'wg'])
  assert.equal(by.v.network, 'grpc'); assert.equal(by.v.serviceName, 'gs'); assert.equal(by.v.fingerprint, 'chrome')
  assert.equal(by.ss.protocol, 'shadowtls'); assert.equal(by.ss.address, '1.2.3.4'); assert.equal(by.ss.sni, 'www.apple.com')
  assert.equal(by.t.protocol, 'tuic'); assert.equal(by.t.alpn, 'h3')
  assert.equal(by.wg.address, '198.51.100.7')
  assert.equal(P.parseBundle(sb).length, 4)
})

test('sing-box masque / tailscale / tailcat import (Kotlin: ProtocolFormsTest)', () => {
  const r = P.importSingBox({ endpoints: [{ type: 'masque-client', tag: 'mq', server: 'h.example.com', server_port: 443, username: 'a', password: 'b', version: 2, tls: { enabled: true, server_name: 's.example.com' } }] })
  assert.equal(r.configs.length, 1)
  const m = r.configs[0]
  assert.equal(m.protocol, 'masque'); assert.equal(m.mode, '2'); assert.equal(m.uuid, 'a'); assert.equal(m.sni, 's.example.com')
  const t = P.importSingBox('{"endpoints":[{"type":"tailscale","tag":"home","auth_key":"tskey-x","exit_node":"exit1","ephemeral":true}],"outbounds":[{"type":"tailcat","tag":"cat","server_public_key":"p","server_disco_key":"d"}]}')
  assert.equal(t.configs.length, 2)
  const ts = t.configs.find(c => c.protocol === 'tailscale')
  assert.equal(ts.password, 'tskey-x'); assert.equal(ts.path, 'exit1'); assert.equal(ts.headerType, 'ephemeral')
  assert.equal(t.configs.find(c => c.protocol === 'tailcat').uuid, 'd')
  const w = P.importSingBox({ outbounds: [{ type: 'shadowsocksr', tag: 'old' }] })
  assert.equal(w.configs.length, 0); assert.match(w.warnings[0], /shadowsocksr/)
})

const CLASH = `
# Mihomo profile
mixed-port: 7890
proxies:
  - name: "ss-plain"
    type: ss
    server: 203.0.113.1
    port: 8388
    cipher: aes-128-gcm
    password: "p#ss"
  - {name: vless-reality, type: vless, server: r.example.com, port: 443, uuid: 11111111-2222-3333-4444-555555555555,
     network: tcp, tls: true, flow: xtls-rprx-vision, servername: www.apple.com, client-fingerprint: chrome,
     reality-opts: {public-key: PUBKEY123, short-id: ab12}}
  - name: vmess-ws
    type: vmess
    server: v.example.com
    port: 443
    uuid: 11111111-2222-3333-4444-555555555555
    alterId: 0
    cipher: auto
    tls: true
    network: ws
    ws-opts:
      path: /ray
      headers:
        Host: cdn.example.com
  - name: hy2
    type: hysteria2
    server: h.example.com
    port: 8443
    password: pw
    sni: h.example.com
    skip-cert-verify: true
  - name: wg
    type: wireguard
    server: 198.51.100.7
    port: 51820
    ip: 10.0.0.2
    private-key: aGVsbG8taGVsbG8taGVsbG8taGVsbG8taGVsbG8tMTI=
    public-key: d29ybGQtd29ybGQtd29ybGQtd29ybGQtd29ybGQtMTI=
    mtu: 1280
  - name: legacy
    type: ssr
    server: x.example.com
    port: 1
proxy-groups:
  - name: auto
    type: url-test
    proxies: [ss-plain, hy2]
`.trim()

test('MiniYaml reads block and flow styles (Kotlin: miniYamlReadsBlockAndFlowStyles)', () => {
  const root = P.MiniYaml.parse(CLASH)
  const proxies = root.get('proxies')
  assert.equal(proxies.length, 6)
  assert.equal(proxies[0].get('password'), 'p#ss')
  assert.equal(proxies[1].get('reality-opts').get('short-id'), 'ab12')
  assert.equal(proxies[2].get('ws-opts').get('headers').get('Host'), 'cdn.example.com')
  assert.deepEqual(root.get('proxy-groups')[0].get('proxies'), ['ss-plain', 'hy2'])
})

test('Clash proxies become profiles (Kotlin: clashProxiesBecomeProfiles)', () => {
  const r = P.importClash(CLASH)
  const by = Object.fromEntries(r.configs.map(c => [c.name, c]))
  assert.deepEqual(Object.keys(by).sort(), ['hy2', 'ss-plain', 'vless-reality', 'vmess-ws', 'wg'])
  assert.equal(by['ss-plain'].protocol, 'shadowsocks'); assert.equal(by['ss-plain'].password, 'p#ss')
  const v = by['vless-reality']
  assert.equal(v.security, 'reality'); assert.equal(v.publicKey, 'PUBKEY123'); assert.equal(v.shortId, 'ab12')
  assert.equal(v.sni, 'www.apple.com'); assert.equal(v.flow, 'xtls-rprx-vision')
  const m = by['vmess-ws']
  assert.equal(m.network, 'ws'); assert.equal(m.path, '/ray'); assert.equal(m.host, 'cdn.example.com')
  assert.equal(by.hy2.allowInsecure, true)
  assert.equal(by.wg.mtu, 1280)
  assert.equal(r.warnings.length, 1); assert.match(r.warnings[0], /ssr/)
  assert.equal(P.parseBundle(CLASH).length, 5)
  assert.equal(P.looksLikeClash('{"proxies": []}'), false)
})

test('Clash: tuic, trojan grpc, socks5, mieru, snell, AmneziaWG', () => {
  const y = `proxies:
  - {name: "تویک", type: tuic, server: t.example.com, port: 443, uuid: ${UUID}, password: "p w", congestion-controller: bbr, alpn: [h3]}
  - {name: tj, type: trojan, server: "2001:db8::4", port: 443, password: pw, network: grpc, grpc-opts: {grpc-service-name: gs}}
  - {name: s5, type: socks5, server: s.example.com, port: 1080, username: u, password: p}
  - {name: mi, type: mieru, server: m.example.com, port: 2999, username: u, password: p, transport: TCP}
  - {name: sn, type: snell, server: sn.example.com, port: 44046, psk: k, version: 3}
  - name: awg
    type: wireguard
    server: a.example.com
    port: 51820
    private-key: ${WG_KEY}
    public-key: ${WG_KEY}
    amnezia-wg-option: {jc: 4, jmin: 40}
`
  const by = Object.fromEntries(P.importClash(y).configs.map(c => [c.name, c]))
  assert.equal(by['تویک'].protocol, 'tuic'); assert.equal(by['تویک'].password, 'p w'); assert.equal(by['تویک'].alpn, 'h3')
  assert.equal(by.tj.address, '2001:db8::4'); assert.equal(by.tj.serviceName, 'gs')
  assert.equal(by.s5.protocol, 'socks'); assert.equal(by.s5.uuid, 'u')
  assert.equal(by.mi.protocol, 'mieru'); assert.equal(by.mi.port, 2999)
  assert.equal(by.sn.protocol, 'snell'); assert.equal(by.sn.alterId, 3); assert.equal(P.protocolFamily(by.sn), 'singbox')
  assert.equal(by.awg.protocol, 'amneziawg'); assert.match(x(by.awg).conf, /Jc = 4/)
})

// ------------------------------------------------------------ subscriptions

const LIST = [
  `vless://${UUID}@1.1.1.1:443?security=tls#a`,
  `trojan://pw@2.2.2.2:443#${encodeURIComponent('ب')}`,
  'hy2://pw@3.3.3.3:443#c'
].join('\n')

test('subscription bodies: plain, base64, URL-safe unpadded, wrapped', () => {
  assert.equal(P.parseBundle(LIST).length, 3)
  assert.equal(P.parseBundle(b64(LIST)).length, 3)
  assert.equal(P.parseBundle(b64url(LIST)).length, 3)
  assert.equal(P.parseBundle('﻿' + b64(LIST).replace(/(.{60})/g, '$1\n')).length, 3)
  assert.equal(P.parseBundle(LIST.replace(/\n/g, '\r\n') + '\n\n# comment\n').length, 3)
  assert.equal(P.parseSubscriptionBody(b64(LIST)).length, 3)
  assert.equal(P.decodeSubscriptionBody(b64(LIST)), LIST)
})

test('subscription errors are classified like SubscriptionError.Kind', () => {
  const kind = body => { try { P.parseSubscriptionBody(body); return 'OK' } catch (e) { assert.ok(e instanceof P.SubscriptionError); return e.kind } }
  assert.equal(kind('   '), 'EMPTY')
  assert.equal(kind('proxies:\nproxy-groups:\n  - name: x'), 'CLASH')
  assert.equal(kind('<html>blocked</html>'), 'NOT_CONFIG')
  assert.deepEqual(P.parseUserInfo('upload=10; download=20; total=100; expire=1700000000'),
    { upload: 10, download: 20, total: 100, expire: 1700000000, used: 30, hasData: true })
  assert.equal(P.parseUserInfo(''), null)
})

// ------------------------------------------------------------ engines

test('protocolFamily follows EngineRouting.engineFor', () => {
  const f = (protocol, extra = {}) => P.protocolFamily({ ...P.defaults(), protocol, ...extra })
  assert.equal(f('vless'), 'xray'); assert.equal(f('shadowsocks'), 'xray'); assert.equal(f('wireguard'), 'xray')
  assert.equal(f('ikev2'), 'ikev2'); assert.equal(f('openvpn'), 'openvpn'); assert.equal(f('tor'), 'tor')
  assert.equal(f('psiphon'), 'psiphon'); assert.equal(f('aether'), 'aether')
  assert.equal(f('psiphon', { oblivionJson: '{"core":"chain"}' }), 'aether')
  assert.equal(f('psiphon', { oblivionJson: '{bad' }), 'psiphon')
  for (const p of P.SINGBOX_PROTOCOLS) assert.equal(f(p), [...P.DNSTT_FAMILY, ...P.MASTERDNS_FAMILY].includes(p) ? 'dnstunnel' : 'singbox', p)
  assert.equal(P.engineFor({ protocol: 'dnstt' }), 'SINGBOX')
  assert.equal(P.protocolFamily(null), 'xray')
})

test('SCHEMES: every listed scheme has a parser branch', () => {
  assert.ok(P.SCHEMES.includes('vless') && P.SCHEMES.includes('cottendns') && P.SCHEMES.includes('awg'))
  // A junk body under each scheme returns null or a config, never throws.
  for (const s of P.SCHEMES) for (const body of ['', 'x', '@', '%zz@[::1', '#']) P.parseLink(`${s}://${body}`)
})

// ------------------------------------------------------------ robustness

test('malformed input returns null / [] and never throws', () => {
  const junk = ['', ' ', 'hello', 'vless://', 'vless://nohost', 'vless://u@h:notaport', 'vless://u@[::1', 'vmess://!!!!',
    'vmess://' + b64('[1,2]'), 'vmess://' + b64('{"add":'), 'ss://', 'ss://%%%', 'ss://bm9jb2xvbg@h:1', 'trojan://@:',
    'hysteria2://pw@h', 'tuic://:p@h:1', 'wireguard://k@h', 'wg://@h:1', 'ikev2://', 'dnstt://?pubkey=x',
    'masque://[bad', 'amneziawg://!!!', 'awg://', 'ssh://u@h:99999999999', 'brook://', 'tailcat://', 'sstp://@',
    '%E0%A4%A', '{"outbounds":[{"protocol":"vless"}]}', '[', '{', 'proxies:\n  - {name: x, type: ss, server: [', '\u0000',
    '\uD800', 'obfs4', 'Bridge', 'vless://' + 'a'.repeat(100000)]
  for (const j of junk) {
    assert.doesNotThrow(() => P.parseLink(j), j.slice(0, 40))
    assert.doesNotThrow(() => P.parseBundle(j), j.slice(0, 40))
  }
  for (const j of ['', 'hello', 'vless://', 'vless://nohost', 'vmess://!!!!', 'ss://', 'trojan://@:', 'hysteria2://pw@h', 'ikev2://', 'sstp://@']) {
    assert.equal(P.parseLink(j), null, j)
    assert.deepEqual(P.parseBundle(j), [], j)
  }
  for (const v of [null, undefined, 42, {}, []]) {
    assert.equal(P.parseLink(v), null); assert.deepEqual(P.parseBundle(v), [])
    assert.doesNotThrow(() => P.toShareLink(v)); assert.doesNotThrow(() => P.protocolFamily(v))
  }
  assert.equal(P.toShareLink({ protocol: 'vless', port: 'x', name: null, extra: '{bad' }) !== undefined, true)
  assert.equal(P.toShareLink({ protocol: 'mieru', extra: '{bad' }), null)
  // A flow mapping that loops forever in the Kotlin MiniYaml is rejected here.
  assert.deepEqual(P.importClash('proxies:\n  - {a: ]}').configs, [])
})

test('JVM codec quirks: URLDecoder, Integer.parseInt, base64', () => {
  const I = P._internal
  assert.equal(I.formDecode('a+b%20c'), 'a b c'); assert.equal(I.pctDecode('a+b'), 'a+b')
  assert.equal(I.formDecode('100%'), '100%') // bad escape: input unchanged
  assert.equal(I.formDecode('%F0%9F%87%A9%F0%9F%87%AA'), '🇩🇪')
  assert.equal(I.javaUrlEncode("a b*~'é"), 'a+b*%7E%27%C3%A9')
  assert.equal(I.toIntOrNull('۴۴۳'), 443); assert.equal(I.toIntOrNull('+80'), 80); assert.equal(I.toIntOrNull('2147483648'), null)
  assert.throws(() => I.javaB64Decode('QQ=', false)); assert.equal(I.javaB64Decode('QQ', false).toString(), 'A')
  assert.equal(I.androidB64Decode('Q!Q==', false).toString(), 'A') // Android skips foreign characters
  assert.equal(link('vless://u@h.example.com:۴۴۳#p').port, 443)
})

// ------------------------------------------------------------ summary

const total = passed + failures.length
for (const [name, e] of failures) {
  console.error(`FAIL ${name}\n  ${(e && e.stack) || e}\n`)
}
console.log(`parser.test: ${passed}/${total} passed${failures.length ? `, ${failures.length} failed` : ''}`)
process.exitCode = failures.length ? 1 : 0
