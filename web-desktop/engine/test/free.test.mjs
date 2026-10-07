// Tests for engine/free.js. Run: node web-desktop/engine/test/free.test.mjs
// Offline only: every network call goes through a stubbed fetch serving the
// recorded fixtures in ./fixtures.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { readFileSync, mkdtempSync, rmSync, existsSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import crypto from 'node:crypto'

const require = createRequire(import.meta.url)
const here = dirname(fileURLToPath(import.meta.url))
const fx = name => readFileSync(join(here, 'fixtures', name), 'utf8')
const repo = join(here, '..', '..', '..')
const free = require('../free.js')

const tests = []
const test = (name, fn) => tests.push({ name, fn })

const NOW = Date.parse('2026-10-07T12:00:00Z')
const A1 = 'vless://11111111-1111-1111-1111-111111111111@a.example.com:443?security=tls&type=ws&path=%2Fws#🇩🇪'

/** Stub fetch: route table url -> Response factory; records every request. */
function stubFetch (routes) {
  const calls = []
  const fn = async (url, init = {}) => {
    calls.push({ url, init })
    if (init.signal && init.signal.aborted) throw init.signal.reason
    const r = routes[url]
    if (!r) return new Response('not found', { status: 404 })
    return typeof r === 'function' ? r(url, init) : r()
  }
  fn.calls = calls
  return fn
}
const html = name => () => new Response(fx(name), { status: 200, headers: { 'content-type': 'text/html; charset=utf-8' } })
const text = body => () => new Response(body, { status: 200 })
const redirect = location => () => new Response(null, { status: 302, headers: { location } })

const feedRoutes = () => ({
  'https://t.me/s/Ghajarvpn': html('tg-ghajarvpn-1.html'),
  'https://t.me/s/Ghajarvpn?before=101': html('tg-ghajarvpn-2.html'),
  'https://t.me/s/Ghajarvpn?before=98': html('tg-ghajarvpn-3.html'),
  'https://t.me/s/prrofile_purple': html('tg-purple-1.html'),
  // https://t.me/s/v2rayngvpn -> 404 (unreachable channel)
  'https://sub.example.com/sub/TOKEN123': text(fx('sub-token.txt')),
  'https://example.org/landing': html('landing.html'),
  'https://short.example.net/s/abc': redirect('/final'),
  'https://short.example.net/final': text('hy2://pw@j.example.com:443#j\n'),
  'https://down.example.net/sub/y': redirect('http://down.example.net/plain'),
  'http://down.example.net/plain': text('vless://77777777-7777-7777-7777-777777777777@k.example.com:443#must-not-load')
})

// ---------------------------------------------------------------------------
// Telegram free configs
// ---------------------------------------------------------------------------

test('registry mirrors FreeSourceRegistry.DEFAULT_SOURCES', () => {
  assert.equal(free.FREE_SOURCES.length, 9)
  assert.deepEqual(free.FREE_SOURCES.map(s => s.channel), ['Ghajarvpn', 'prrofile_purple', 'v2rayngvpn', 'meliproxyy', 'V2Ray_Tz', 'mitivpn', 'kingoclub', 'vpn_Click', 'Hiddify_Nexttt'])
  assert.equal(free.FREE_SOURCES[0].priority, 10)
  assert.ok(free.FREE_SOURCES.slice(1).every(s => s.priority === 5 && s.enabled && s.endpoint === `https://t.me/s/${s.channel}`))
  assert.equal(free.REGISTRY, free.FREE_SOURCES)
})

test('thresholds mirror FreeConfigs / EngineTester use', () => {
  assert.equal(free.FREE.TEST_WORKERS, 8)
  assert.equal(free.FREE.TEST_DELAY_BOUND_MS, 4000)
  assert.equal(free.FREE.MAX_LATENCY_MS, 2500)
  assert.equal(free.FREE.MAX_MANAGED_CONFIGS, 35)
  assert.equal(free.FREE.MAX_AGE_HOURS, 72)
  assert.equal(free.FREE.FEED_CONCURRENCY, 3)
  assert.equal(free.FREE.EXPANDER_WORKERS, 4)
})

test('parsePosts reads ids and ISO times, null when undated', () => {
  const posts = free.parsePosts(fx('tg-purple-1.html'))
  assert.deepEqual(posts.map(p => p.id), [1, 2, 3])
  assert.equal(posts[0].publishedAt, null)
  assert.equal(posts[1].publishedAt, Date.parse('2026-10-07T11:30:00Z'))
  const recent = free.recentPosts(posts, NOW)
  assert.deepEqual(recent.map(p => p.id), [2], 'undated and >5 min future posts are dropped')
})

test('extractLinks: entities, <wbr>, dedupe, subscription filtering and order', () => {
  const posts = free.parsePosts(fx('tg-ghajarvpn-1.html'))
  const p104 = free.extractLinks(posts.find(p => p.id === 104).html)
  assert.deepEqual(p104.configs, [
    'vless://11111111-1111-1111-1111-111111111111@a.example.com:443?security=tls&type=ws&path=%2Fws#🇩🇪',
    'vless://11111111-1111-1111-1111-111111111111@a.example.com:443?security=tls&type=ws&path=%2Fws#copy-2'
  ])
  const p103 = free.extractLinks(posts.find(p => p.id === 103).html)
  assert.ok(p103.configs.includes('trojan://pass@c.example.com:443#t1'), '<wbr> removed inside the link')
  assert.equal(p103.subscriptions.length, 0, 'telegram.org emoji URLs are not followed')
  const p102 = free.extractLinks(posts.find(p => p.id === 102).html)
  // /sub/ link first (stable), landing page (trailing "." trimmed) after; t.me, images, user:pw@ dropped.
  assert.deepEqual(p102.subscriptions, ['https://sub.example.com/sub/TOKEN123', 'https://example.org/landing'])
  assert.equal(p102.configs.length, 1)
  assert.ok(p102.configs[0].startsWith('ss://'))
})

test('extractLinks trims Persian punctuation and rejects what java.net.URI would', () => {
  const body = '<div class="tgme_widget_message_text js-message_text">' +
    'https://ok.example.com/sub/a، https://ok.example.com/b؛ https://bad_host.example.com/x ' +
    'https://x.example.com/a|b https://www.t.me/x https://t.me/s/ch https://img.example.com/a.webp?x=1</div>'
  const { subscriptions } = free.extractLinks(body)
  assert.deepEqual(subscriptions, ['https://ok.example.com/sub/a', 'https://ok.example.com/b'])
})

test('decodeMaybeBase64 / parseSubscriptionBody', () => {
  const { links, bundle } = free.parseSubscriptionBody(fx('sub-token.txt'))
  assert.equal(bundle, null)
  assert.equal(links.length, 3)
  assert.ok(links[1].startsWith('vless://66666666'))
  assert.throws(() => free.parseSubscriptionBody(fx('landing.html')), free.SubscriptionError)
  assert.throws(() => free.parseSubscriptionBody('   '), free.SubscriptionError)
  // base64url without padding
  const b64url = Buffer.from('trojan://p@q.example.com:443#ü?').toString('base64url')
  assert.deepEqual(free.parseSubscriptionBody(b64url).links, ['trojan://p@q.example.com:443#ü?'])
  const json = free.parseSubscriptionBody('{"outbounds":[{"protocol":"vless"}]}')
  assert.equal(json.bundle.format, 'json')
  const wg = free.parseSubscriptionBody('[Interface]\nPrivateKey = x\n[Peer]\nEndpoint = 1.2.3.4:51820')
  assert.equal(wg.bundle.format, 'wireguard')
})

test('linkIdentity ignores names, rejects hostless/bad-port links', () => {
  const id = free.linkIdentity
  assert.equal(id(A1), id(A1.replace('#🇩🇪', '#another')))
  const vm = n => 'vmess://' + Buffer.from(JSON.stringify({ v: '2', ps: n, add: 'b.example.com', port: '8080', id: 'u' })).toString('base64')
  assert.equal(id(vm('one')), id(vm('دو')))
  assert.equal(id('vless://u@:443'), null)
  assert.equal(id('vless://u@h.example.com:70000'), null)
  assert.equal(id('vmess://bm90IGpzb24'), null)
  assert.ok(id('ss://' + Buffer.from('aes-256-gcm:pw@d.example.com:8388').toString('base64') + '#x'))
  assert.equal(id('ss://example.com/path'), null)
})

test('fetchFreeLinks: paging, 72h window, dedupe, subscriptions, failures', async () => {
  const fetch = stubFetch(feedRoutes())
  const streamed = []
  const res = await free.fetchFreeLinks({ fetch, now: NOW, channels: free.FREE_SOURCES.slice(0, 3), onLink: i => streamed.push(i.link) })
  const hosts = res.links.map(l => (/@([^:/?#]+)/.exec(l.link) || [])[1] || (l.link.startsWith('vmess') ? 'b.example.com' : l.link.startsWith('ss://') ? 'd.example.com' : '?'))
  assert.deepEqual([...hosts].sort(), ['a.example.com', 'b.example.com', 'c.example.com', 'd.example.com', 'e.example.com', 'f.example.com', 'g.example.com', 'h.example.com', 'i.example.com', 'j.example.com'])
  assert.equal(res.status, 'ok')
  assert.equal(res.code, 10)
  assert.deepEqual(streamed, res.links.map(l => l.link))
  // Paging used ?before=<oldest id> and stopped once a page was entirely older than 72 h.
  const urls = fetch.calls.map(c => c.url)
  assert.ok(urls.includes('https://t.me/s/Ghajarvpn?before=101'))
  assert.ok(urls.includes('https://t.me/s/Ghajarvpn?before=98'))
  assert.ok(!urls.includes('https://t.me/s/Ghajarvpn?before=96'))
  assert.ok(!urls.some(u => u.startsWith('https://t.me/s/prrofile_purple?')), 'oldest id 1 ends paging')
  assert.ok(!urls.includes('http://down.example.net/plain'), 'https -> http redirect is refused')
  assert.ok(!urls.includes('https://evil.example.com/sub/x'))
  for (const c of fetch.calls) {
    assert.equal(c.init.redirect, 'manual')
    assert.equal(c.init.headers['User-Agent'], 'Mozilla/5.0 Ghajarvpn/1.0.0')
  }
  // Expired, future and undated posts contribute nothing.
  assert.ok(!res.links.some(l => /expired|future|undated|ancient|k\.example/.test(l.link)))
  // Duplicates across posts, channels and subscriptions collapse to the first.
  const a = res.links.filter(l => l.link.includes('@a.example.com'))
  assert.equal(a.length, 1)
  assert.equal(a[0].channel, 'Ghajarvpn')
  assert.equal(a[0].via, 'post')
  assert.equal(res.links.filter(l => l.link.startsWith('vmess://')).length, 1)
  const h = res.links.find(l => l.link.includes('@h.example.com'))
  assert.equal(h.via, 'subscription')
  assert.equal(h.subscriptionUrl, 'https://sub.example.com/sub/TOKEN123')
  assert.equal(h.channel, 'Ghajarvpn')
  assert.equal(h.postId, 102)
  assert.equal(res.links.find(l => l.link.includes('@j.example.com')).subscriptionUrl, 'https://short.example.net/s/abc')
  const g = res.links.find(l => l.link.includes('@g.example.com'))
  assert.equal(g.channel, 'prrofile_purple')
  assert.equal(g.sourceId, 'tg_prrofile_purple')
  // pages: 3 of Ghajarvpn + 1 of prrofile_purple; failures: v2rayngvpn 404,
  // an undated post, the refused redirect (the ordinary landing page is not one).
  assert.equal(res.pages, 4)
  assert.equal(res.reachable, 4)
  assert.equal(res.failures, 3)
  assert.equal(res.incomplete, true)
})

test('fetchFreeLinks: maxAgeHours and channel names', async () => {
  const fetch = stubFetch(feedRoutes())
  const res = await free.fetchFreeLinks({ fetch, now: NOW, maxAgeHours: 24, channels: ['Ghajarvpn'] })
  // Only post 104 (and 103 is 27 h old): a single unique link.
  assert.deepEqual(res.links.map(l => l.postId), [104])
  assert.equal(res.links[0].channel, 'Ghajarvpn')
})

test('fetchFreeLinks: unreachable and no-configs codes', async () => {
  const none = await free.fetchFreeLinks({ fetch: stubFetch({}), now: NOW })
  assert.equal(none.status, 'unreachable')
  assert.equal(none.code, free.FREE.UNREACHABLE)
  assert.equal(none.failures, 9)
  const undated = '<div data-post="x/5"><div class="tgme_widget_message_text">vless://u@h.example.com:443</div></div>'
  const res = await free.fetchFreeLinks({ fetch: stubFetch({ 'https://t.me/s/x': text(undated) }), now: NOW, channels: ['x'] })
  assert.equal(res.status, 'no_configs')
  assert.equal(res.code, free.FREE.NO_CONFIGS)
})

test('fetchFreeLinks: custom signature and parseBundle hooks', async () => {
  const routes = {
    'https://t.me/s/c': text(`<div data-post="c/9"><div class="tgme_widget_message_text">https://j.example.com/sub/json vless://bad</div><time datetime="2026-10-07T10:00:00+00:00"></time></div>`),
    'https://j.example.com/sub/json': text('{"outbounds":[{"protocol":"trojan","settings":{}}]}')
  }
  const seen = []
  const res = await free.fetchFreeLinks({
    fetch: stubFetch(routes),
    now: NOW,
    channels: ['c'],
    signature: l => { seen.push(l); return l.includes('bad') ? null : l },
    parseBundle: (t, format) => (format === 'json' ? ['trojan://p@from-json.example.com:443'] : [])
  })
  assert.deepEqual(res.links.map(l => l.link), ['trojan://p@from-json.example.com:443'])
  assert.ok(seen.includes('vless://bad'))
  const raw = await free.fetchFreeLinks({ fetch: stubFetch(routes), now: NOW, channels: ['c'] })
  assert.equal(raw.bundles.length, 1, 'without a parser the JSON document is handed back')
  assert.equal(raw.bundles[0].format, 'json')
})

test('feedRead: size cap, redirect limit, timeout', async () => {
  await assert.rejects(free.feedRead('https://big.example.com/', { fetch: stubFetch({ 'https://big.example.com/': text('x'.repeat(2000)) }), maxBytes: 1000 }), /too large/)
  const loop = stubFetch({ 'https://l.example.com/a': redirect('/a') })
  await assert.rejects(free.feedRead('https://l.example.com/a', { fetch: loop }), /Too many redirects/)
  assert.equal(loop.calls.length, 4)
  const slow = async (url, init) => new Promise((resolve, reject) => init.signal.addEventListener('abort', () => reject(init.signal.reason)))
  await assert.rejects(free.feedRead('https://s.example.com/', { fetch: slow, timeoutMs: 50 }), /timed out/)
  assert.equal(await free.feedRead('http://p.example.com/', { fetch: stubFetch({ 'http://p.example.com/': redirect('https://p.example.com/x'), 'https://p.example.com/x': text('ok') }) }), 'ok')
})

test('reconcile / selectRoundRobin / configSignature', () => {
  const mk = (name, address) => free.makeProxyConfig({ name, protocol: 'vless', address, port: 443, uuid: 'u' })
  const a = mk('A', 'a'); const a2 = mk('A again', 'a'); const b = mk('B', 'b'); const c = mk('C', 'c')
  assert.equal(free.configSignature(a), free.configSignature(a2))
  assert.notEqual(free.configSignature(a), free.configSignature(b))
  const sig = free.configSignature
  const partial = free.reconcile([c, b], [a, a2], new Set([sig(b)]), false)
  assert.deepEqual(partial.map(x => [x.address, x.name]), [['a', 'Ghajarvpn 1'], ['c', 'Ghajarvpn 2']])
  assert.deepEqual(free.reconcile([c], [b], new Set(), true).map(x => x.address), ['b'])
  assert.equal(free.reconcile([], Array.from({ length: 50 }, (_, i) => mk('x', `h${i}`)), new Set(), true).length, 35)
  assert.deepEqual(free.selectRoundRobin([[a, b], [c], [a2]]).map(x => x.address), ['a', 'c', 'b'])
})

test('pipeline helpers: Persian password, flags, container decrypt, preview, store', () => {
  assert.equal(free.passwordFromContext('رمز عبور: ۱۲۳abc', null), '۱۲۳abc')
  assert.equal(free.passwordFromContext(null, 'پسورد فایل = s3cret'), 's3cret')
  assert.equal(free.passwordFromContext('no', 'Password: p@ss'), 'p@ss')
  assert.equal(free.passwordFromContext('none', 'none', ['pass=grp']), 'grp')
  assert.equal(free.countryOf('Germany 🇩🇪 fast'), '🇩🇪')

  const plain = Buffer.from('vless://u@h.example.com:443#x')
  const salt = crypto.randomBytes(16); const iv = crypto.randomBytes(16)
  const key = crypto.createHash('sha256').update('pw').update(salt).digest()
  const c = crypto.createCipheriv('aes-256-cbc', key, iv)
  const blob = Buffer.concat([salt, iv, c.update(plain), c.final()])
  assert.deepEqual(free.tryDecryptContainer(blob, 'pw'), plain)
  assert.equal(free.tryDecryptContainer(blob, 'wrong'), null)

  // Faithful to TelegramWebFetcher.parsePreview: its lazy block regex ends at the
  // first nested </div>, so on real t.me/s markup it yields nothing (the app's
  // refresh uses FreeFeedRules instead); on flat markup it works.
  assert.equal(free.parsePreview(fx('tg-purple-1.html')).length, 0)
  const msgs = free.parsePreview('<div class="tgme_widget_message" data-post="c/7"><div class="tgme_widget_message_text">hi &amp; vless://x<br/>y</div></div>')
  assert.deepEqual(msgs, [{ id: 7, text: 'hi & vless://xy' }])

  const dir = mkdtempSync(join(tmpdir(), 'free-store-'))
  try {
    const file = join(dir, 'ghajar_free_configs.json')
    free.saveFreeStore(file, free.FREE_SOURCES, [
      { link: A1, hash: 'h1', protocol: 'vless', country: '🇩🇪', name: 'n', sourceId: 'tg_ghajarvpn', fetchedAt: 1, lastChecked: 2, ok: true, pingMs: 120 },
      { link: 'x', hash: 'h2', protocol: 'vless', country: '', name: '', sourceId: 'tg_removed', fetchedAt: 1, lastChecked: 1, ok: false }
    ])
    assert.ok(!existsSync(file + '.tmp'))
    const loaded = free.loadFreeStore(file)
    assert.equal(loaded.items.length, 1, 'items of unknown sources are dropped')
    assert.equal(loaded.items[0].pingMs, 120)
    assert.equal(free.loadFreeStore(join(dir, 'missing.json')).items.length, 0)
  } finally { rmSync(dir, { recursive: true, force: true }) }
})

// ---------------------------------------------------------------------------
// WARP
// ---------------------------------------------------------------------------

const PROXY_CONFIG_KEYS = ['id', 'name', 'protocol', 'address', 'port', 'uuid', 'password', 'method', 'alterId', 'encryption', 'flow', 'network', 'security', 'sni', 'publicKey', 'shortId', 'fingerprint', 'path', 'host', 'serviceName', 'mode', 'alpn', 'source', 'headerType', 'subId', 'privateKey', 'localAddress', 'mtu', 'reserved', 'locked', 'favorite', 'aetherMode', 'aetherScan', 'aetherNoise', 'aetherHttp2', 'aetherExitLoc', 'aetherFragment', 'aetherIpv6', 'oblivionJson', 'hyObfs', 'hyObfsPassword', 'hyUpMbps', 'hyDownMbps', 'allowInsecure', 'pinnedCertSha256', 'cipherSuites', 'randomSubdomain', 'maskType', 'maskDomain', 'maskPassword', 'echConfigList', 'torCountry', 'torThroughVpn', 'torBaseId', 'chainId', 'psiphonMode', 'psiphonCountry', 'psiphonCdnIps', 'psiphonCdnSni', 'extra']

test('ProxyConfig keys match ProxyConfig.toJson()', () => {
  const src = readFileSync(join(repo, 'app/src/main/java/net/gozar/app/ProxyConfig.kt'), 'utf8')
  const toJson = src.slice(src.indexOf('fun toJson()'), src.indexOf('fun extraJson'))
  const kotlinKeys = Array.from(toJson.matchAll(/\.put\("([A-Za-z0-9]+)"/g), m => m[1])
  assert.deepEqual([...kotlinKeys].sort(), [...PROXY_CONFIG_KEYS].sort())
  assert.deepEqual(Object.keys(free.makeProxyConfig({})), PROXY_CONFIG_KEYS)
})

test('generateWireguardKeyPair: valid clamped X25519 keys', () => {
  for (let i = 0; i < 20; i++) {
    const kp = free.generateWireguardKeyPair()
    const priv = Buffer.from(kp.privateKey, 'base64'); const pub = Buffer.from(kp.publicKey, 'base64')
    assert.equal(priv.length, 32); assert.equal(pub.length, 32)
    assert.match(kp.privateKey, /^[A-Za-z0-9+/]{43}=$/); assert.match(kp.publicKey, /^[A-Za-z0-9+/]{43}=$/)
    assert.equal(priv[0] & 7, 0); assert.equal(priv[31] & 128, 0); assert.equal(priv[31] & 64, 64)
    assert.equal(free.wireguardPublicKey(kp.privateKey), kp.publicKey)
    const other = free.generateWireguardKeyPair()
    assert.deepEqual(free.x25519(kp.privateKey, other.publicKey), free.x25519(other.privateKey, kp.publicKey))
  }
  // RFC 7748 6.1 test vector (Alice).
  const alicePriv = Buffer.from('77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a', 'hex')
  assert.equal(Buffer.from(free.wireguardPublicKey(alicePriv), 'base64').toString('hex'), '8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a')
})

test('registerWarp: request shape and WireGuard configs', async () => {
  let request
  const fetch = async (url, init) => { request = { url, init }; return new Response(fx('warp-reg.json'), { status: 200 }) }
  const res = await free.registerWarp({ fetch, now: NOW })
  assert.equal(res.ok, true, res.message)
  assert.equal(request.url, 'https://api.cloudflareclient.com/v0a2158/reg')
  assert.equal(request.init.method, 'POST')
  assert.equal(request.init.headers['CF-Client-Version'], 'a-6.10-2158')
  assert.equal(request.init.headers['User-Agent'], 'okhttp/3.12.1')
  assert.equal(request.init.headers['Content-Type'], 'application/json; charset=UTF-8')
  const body = JSON.parse(request.init.body)
  assert.deepEqual(Object.keys(body), ['key', 'install_id', 'fcm_token', 'tos', 'model', 'serial_number', 'locale'])
  assert.equal(body.tos, '2026-10-07T12:00:00.000Z')
  assert.equal(body.model, 'PC'); assert.equal(body.locale, 'en_US')
  assert.equal(body.key, res.account.publicKey)
  assert.equal(free.wireguardPublicKey(res.account.privateKey), body.key)

  assert.equal(res.configs.length, 8)
  assert.equal(res.config, res.configs[0])
  const c = res.configs[0]
  assert.deepEqual(Object.keys(c), PROXY_CONFIG_KEYS)
  assert.equal(c.protocol, 'wireguard'); assert.equal(c.name, 'WARP 1')
  assert.equal(c.address, '162.159.192.1'); assert.equal(c.port, 2408)
  assert.equal(c.publicKey, 'bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=')
  assert.equal(c.privateKey, res.account.privateKey)
  assert.equal(c.localAddress, '172.16.0.2/32,2606:4700:110:8a36:df92:102a:9602:fa18/128')
  assert.equal(c.mtu, 1280); assert.equal(c.reserved, '1,2,3'); assert.equal(c.source, 'PERSONAL')
  assert.deepEqual(res.configs.map(x => `${x.address}:${x.port}`), ['162.159.192.1:2408', '188.114.96.1:2408', '188.114.97.1:2408', '188.114.98.1:2408', '188.114.99.1:2408', '188.114.96.1:1701', '188.114.97.1:500', '188.114.98.1:4500'])
  assert.equal(new Set(res.configs.map(x => x.id)).size, 8)
  assert.equal(res.account.token, 'tok-abc'); assert.equal(res.account.license, 'a1B2c3D4-e5F6g7H8-i9J0k1L2')

  // Persist and rebuild (the desktop's cache), keeping ids.
  const cached = JSON.parse(JSON.stringify(res.account))
  const rebuilt = free.warpConfigsFromAccount(cached, { ids: res.configs.map(x => x.id) })
  assert.deepEqual(rebuilt, res.configs)
})

test('registerWarp: failures become { ok: false }', async () => {
  const denied = await free.registerWarp({ fetch: async () => new Response('{"success":false,"errors":["blocked"]}', { status: 403 }) })
  assert.equal(denied.ok, false)
  assert.match(denied.message, /^HTTP 403: \{"success":false/)
  const broken = await free.registerWarp({ fetch: async () => new Response('{"config":{"interface":{"addresses":{}}}}', { status: 200 }) })
  assert.equal(broken.ok, false)
  const offline = await free.registerWarp({ fetch: async () => { throw new Error('ECONNREFUSED') } })
  assert.deepEqual(offline, { ok: false, message: 'ECONNREFUSED' })
  assert.equal(free.decodeReserved('not base64!'), '')
  assert.equal(free.decodeReserved('AQ=='), '')
})

// ---------------------------------------------------------------------------
// Psiphon
// ---------------------------------------------------------------------------

/** Field names of a Go struct in native/Psiphon (json:",omitempty" keeps the Go name). */
function goStructFields (file, struct) {
  const src = readFileSync(join(repo, 'native/Psiphon', file), 'utf8')
  const start = src.indexOf(`type ${struct} struct {`)
  assert.ok(start >= 0, `${struct} not found in ${file}`)
  let depth = 0; let end = start
  for (let i = src.indexOf('{', start); i < src.length; i++) {
    if (src[i] === '{') depth++
    if (src[i] === '}' && --depth === 0) { end = i; break }
  }
  const fields = new Set()
  for (const line of src.slice(start, end).split('\n')) {
    const m = /^\t([A-Z][A-Za-z0-9]*)\s+[^\s]/.exec(line)
    if (m && !/json:"-"/.test(line)) fields.add(m[1])
  }
  return fields
}

test('psiphonConfig: every key is a psiphon.Config field ConsoleClient loads', () => {
  const fields = goStructFields('psiphon/config.go', 'Config')
  const scan = goStructFields('psiphon/common/parameters/frontingSpec.go', 'FrontedMeekCDNScanSpec')
  const override = goStructFields('psiphon/common/parameters/frontingSpec.go', 'FrontedMeekDialOverride')
  const transfer = goStructFields('psiphon/common/parameters/transferURLs.go', 'TransferURL')
  const variants = [
    free.psiphonConfig({ mode: 'auto', dataDir: '/tmp/p', socksPort: 1819, httpPort: 1820, allowLan: true }),
    free.psiphonConfig({ mode: 'cdn', country: 'de', cdnIps: '104.16.0.0/13, 1.1.1.1;bad 300.1.1.1', cdnSni: 'https://www.Example.com/x a.b.c', dataDir: '/tmp/p' }),
    free.psiphonConfig({ mode: 'direct', upstreamProxyUrl: 'socks5://127.0.0.1:1829' })
  ]
  for (const cfg of variants) {
    for (const key of Object.keys(cfg)) assert.ok(fields.has(key), `psiphon.Config has no ${key}`)
    for (const k of Object.keys(cfg.FrontedMeekCDNScanSpec || {})) assert.ok(scan.has(k), k)
    for (const o of cfg.FrontedMeekDialOverrides || []) for (const k of Object.keys(o)) assert.ok(override.has(k), k)
    for (const u of cfg.RemoteServerListURLs) for (const k of Object.keys(u)) assert.ok(transfer.has(k), k)
    assert.equal(cfg.PropagationChannelId, 'FFFFFFFFFFFFFFFF'); assert.equal(cfg.SponsorId, 'FFFFFFFFFFFFFFFF')
    assert.equal(Buffer.from(cfg.RemoteServerListURLs[0].URL, 'base64').toString(), free.PSIPHON.SERVER_LIST_URL)
    assert.equal(cfg.InproxyTunnelProtocolSelectionProbability, 0)
    assert.equal(cfg.EstablishTunnelTimeoutSeconds, 0)
    JSON.parse(JSON.stringify(cfg))
  }
  const [auto, cdn, chain] = variants
  assert.equal(auto.LocalSocksProxyPort, 1819); assert.equal(auto.LocalHttpProxyPort, 1820)
  assert.equal(auto.ListenInterface, 'any'); assert.equal(auto.DataRootDirectory, '/tmp/p')
  assert.equal(auto.DisableTactics, undefined); assert.equal(auto.FrontedMeekCDNScanUseBuiltInSpec, true)
  assert.equal(auto.LimitTunnelProtocols.length, 14); assert.equal(auto.EgressRegion, undefined)
  assert.equal(cdn.EgressRegion, 'DE'); assert.equal(cdn.DisableTactics, true)
  assert.deepEqual(cdn.LimitTunnelProtocols, ['FRONTED-MEEK-CDN-OSSH', 'FRONTED-MEEK-CDN-HTTP-OSSH', 'FRONTED-MEEK-CDN-QUIC-OSSH'])
  assert.deepEqual(cdn.FrontedMeekCDNScanSpec, { IPCandidates: ['104.16.0.0/13', '1.1.1.1'], SNIServerNames: ['www.example.com', 'a.b.c'] })
  assert.deepEqual(cdn.FrontedMeekDialOverrides, [{ OverrideID: 'user', MatchDialAddressRegexes: ['.*'], DialAddresses: ['104.16.0.0/13', '1.1.1.1'], SNIServerName: 'www.example.com', VerifyServerNames: ['www.example.com', 'a.b.c'] }])
  assert.equal(cdn.LocalSocksProxyPort, 0, '0 lets tunnel-core pick; read ListeningSocksProxyPort')
  assert.equal(chain.UpstreamProxyURL, 'socks5://127.0.0.1:1829'); assert.equal(chain.DisableTactics, true)
  assert.ok(chain.LimitTunnelProtocols.every(p => !p.includes('QUIC') && !p.startsWith('FRONTED')))
  assert.equal(free.psiphonMode('weird'), 'auto')
  assert.deepEqual(free.psiphonSniCandidates('nodot, ünï.com, ok.example.org.'), ['ok.example.org'])
})

test('psiphonArgs matches ConsoleClient flags', () => {
  const src = readFileSync(join(repo, 'native/Psiphon/ConsoleClient/main.go'), 'utf8')
  const flags = new Set(Array.from(src.matchAll(/flag\.(?:String|Bool|Int)Var\(&\w+, "(\w+)"/g), m => m[1]))
  const args = free.psiphonArgs('/x/psiphon.json', { dataDir: '/x/data', listenInterface: 'lo', serverList: '/x/s', noticesFile: '/x/n', formatNotices: true })
  assert.deepEqual(args.slice(0, 2), ['-config', '/x/psiphon.json'])
  for (const a of args.filter(a => a.startsWith('-'))) assert.ok(flags.has(a.slice(1)), a)
  assert.deepEqual(free.psiphonArgs('c.json'), ['-config', 'c.json'])
})

test('psiphon notices -> PsiphonRuntime events; regions', () => {
  const ev = l => free.psiphonNoticeEvent(l)
  assert.deepEqual(ev('{"data":{"port":34109},"noticeType":"ListeningSocksProxyPort","timestamp":"t"}'), { type: 'socksPort', port: 34109 })
  assert.deepEqual(ev('{"data":{"count":0},"noticeType":"Tunnels"}'), { type: 'connecting' })
  assert.deepEqual(ev('{"data":{"count":1},"noticeType":"Tunnels"}'), { type: 'connected' })
  assert.equal(ev('{"data":{"count":2},"noticeType":"Tunnels"}'), null)
  assert.deepEqual(ev('{"data":{"regions":["us","DE","x","de"]},"noticeType":"AvailableEgressRegions"}'), { type: 'regions', regions: ['DE', 'US'] })
  assert.equal(ev('{"data":{"port":1},"noticeType":"SocksProxyPortInUse"}').fatal, true)
  assert.equal(ev('not json'), null)
  assert.equal(free.PSIPHON.READY_TIMEOUT_MS, 90000)
  assert.equal(free.PSIPHON_REGIONS.length, 34)
  assert.ok(free.PSIPHON_REGIONS.includes('DE') && free.PSIPHON_REGIONS.includes('US'))
  assert.equal(free.psiphonRegionName('de', 'en'), 'Germany')
  assert.equal(free.psiphonRegionName('XYZ'), 'XYZ')
})

// ---------------------------------------------------------------------------

let failed = 0
for (const t of tests) {
  try { await t.fn(); console.log(`ok   ${t.name}`) } catch (e) { failed++; console.log(`FAIL ${t.name}\n     ${e && e.stack ? e.stack.split('\n').slice(0, 4).join('\n     ') : e}`) }
}
console.log(`\n${tests.length - failed}/${tests.length} passed`)
process.exit(failed ? 1 : 0)
