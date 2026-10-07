'use strict'
/**
 * Free sources for the desktop app, ported from the Android app
 * (app/src/main/java/net/gozar/app/):
 *
 *   1. WARP      - Warp.kt (+ Curve25519.kt key generation)
 *   2. Telegram  - FreeConfigs.kt and freecfg/ (FreeFeedRules, FreeFeedHttp,
 *                  FreeSourceRegistry, FreeConfigStore, FreeConfigPipeline,
 *                  TelegramWebFetcher, PasswordedContainer) plus the
 *                  subscription-body handling of SubscriptionFetcher.parseBody
 *   3. Psiphon   - PsiphonConfig.kt, PsiphonRegions.kt, PsiphonEngine.kt and
 *                  the parts of ca.psiphon.PsiphonTunnel (Android wrapper) that
 *                  the app relies on implicitly, retargeted at
 *                  psiphon-tunnel-core's ConsoleClient.
 *
 * Node 22, CommonJS, no dependencies. Every network call goes through an
 * injected `fetch` (defaults to the global one) so callers can route it
 * through a proxy and tests can stub it.
 */

const crypto = require('node:crypto')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')

// ---------------------------------------------------------------------------
// ProxyConfig (ProxyConfig.kt) - the exact toJson() field set and order.
// ---------------------------------------------------------------------------

const PROXY_CONFIG_DEFAULTS = Object.freeze({
  id: '', name: '', protocol: '', address: '', port: 0, uuid: '', password: '',
  method: '', alterId: 0, encryption: 'none', flow: '', network: 'tcp',
  security: 'none', sni: '', publicKey: '', shortId: '', fingerprint: 'chrome',
  path: '', host: '', serviceName: '', mode: '', alpn: '', source: 'PERSONAL',
  headerType: '', subId: '', privateKey: '', localAddress: '', mtu: 0,
  reserved: '', locked: false, favorite: false, aetherMode: 'masque',
  aetherScan: 'balanced', aetherNoise: '', aetherHttp2: true, aetherExitLoc: '',
  aetherFragment: false, aetherIpv6: false, oblivionJson: '', hyObfs: '',
  hyObfsPassword: '', hyUpMbps: 0, hyDownMbps: 0, allowInsecure: false,
  pinnedCertSha256: '', cipherSuites: '', randomSubdomain: false, maskType: '',
  maskDomain: '', maskPassword: '', echConfigList: '', torCountry: '',
  torThroughVpn: false, torBaseId: '', chainId: '', psiphonMode: 'auto',
  psiphonCountry: '', psiphonCdnIps: '', psiphonCdnSni: '', extra: ''
})

/** A ProxyConfig.toJson()-shaped object; `id` defaults to a fresh UUID as in Kotlin. */
function makeProxyConfig (fields = {}) {
  const out = {}
  for (const key of Object.keys(PROXY_CONFIG_DEFAULTS)) {
    out[key] = fields[key] !== undefined ? fields[key] : PROXY_CONFIG_DEFAULTS[key]
  }
  if (!out.id) out.id = crypto.randomUUID()
  return out
}

/**
 * FreeFeedRules.signature: every toJson() key except identity/display ones,
 * sorted, as `key=value` joined by `|`. Two configs that differ only in name
 * (or id/subscription) are the same server.
 */
function configSignature (config) {
  const full = makeProxyConfig({ ...config, id: config.id || 'x' })
  const skip = new Set(['id', 'name', 'subId', 'source', 'locked'])
  return Object.keys(full).filter(k => !skip.has(k)).sort()
    .map(k => `${k}=${full[k]}`).join('|')
}

// ---------------------------------------------------------------------------
// 1. WARP (Warp.kt)
// ---------------------------------------------------------------------------

/**
 * Registration constants mirror the WARP Android client, exactly as Warp.kt.
 * If registration starts returning HTTP 4xx these three are what to refresh.
 */
const WARP = Object.freeze({
  API_VERSION: 'v0a2158',
  CLIENT_VERSION: 'a-6.10-2158',
  USER_AGENT: 'okhttp/3.12.1',
  API_BASE: 'https://api.cloudflareclient.com',
  ENDPOINT_HOST: '162.159.192.1',
  ENDPOINT_PORT: 2408,
  // One config per endpoint, same order and ports as the app.
  ENDPOINTS: Object.freeze([
    ['162.159.192.1', 2408],
    ['188.114.96.1', 2408],
    ['188.114.97.1', 2408],
    ['188.114.98.1', 2408],
    ['188.114.99.1', 2408],
    ['188.114.96.1', 1701],
    ['188.114.97.1', 500],
    ['188.114.98.1', 4500]
  ].map(e => Object.freeze(e))),
  MTU: 1280,
  // HttpURLConnection connect/read timeouts in Warp.kt.
  TIMEOUT_MS: 15000
})

// DER prefixes for raw 32-byte X25519 keys (RFC 8410).
const X25519_PKCS8_PREFIX = Buffer.from('302e020100300506032b656e04220420', 'hex')
const X25519_SPKI_PREFIX = Buffer.from('302a300506032b656e032100', 'hex')

/**
 * Curve25519.generateKeyPair: 32 random bytes clamped the WireGuard way, the
 * public key from node:crypto's X25519. Both base64 (standard, padded).
 */
function generateWireguardKeyPair () {
  const priv = crypto.randomBytes(32)
  priv[0] &= 248
  priv[31] = (priv[31] & 127) | 64
  return { privateKey: priv.toString('base64'), publicKey: wireguardPublicKey(priv) }
}

/** Public key (base64) for a raw or base64 X25519 private key. */
function wireguardPublicKey (privateKey) {
  const raw = Buffer.isBuffer(privateKey) ? privateKey : Buffer.from(String(privateKey), 'base64')
  if (raw.length !== 32) throw new Error('X25519 private key must be 32 bytes')
  const key = crypto.createPrivateKey({ key: Buffer.concat([X25519_PKCS8_PREFIX, raw]), format: 'der', type: 'pkcs8' })
  const spki = crypto.createPublicKey(key).export({ format: 'der', type: 'spki' })
  return Buffer.from(spki.subarray(spki.length - 32)).toString('base64')
}

/** Shared secret check helper (tests): X25519(privA, pubB). */
function x25519 (privateKeyB64, publicKeyB64) {
  const privateKey = crypto.createPrivateKey({ key: Buffer.concat([X25519_PKCS8_PREFIX, Buffer.from(privateKeyB64, 'base64')]), format: 'der', type: 'pkcs8' })
  const publicKey = crypto.createPublicKey({ key: Buffer.concat([X25519_SPKI_PREFIX, Buffer.from(publicKeyB64, 'base64')]), format: 'der', type: 'spki' })
  return crypto.diffieHellman({ privateKey, publicKey })
}

/** Strict standard-alphabet base64 (android.util.Base64.DEFAULT rejects junk). */
function decodeStrictBase64 (value) {
  const s = String(value).replace(/[\r\n\t ]/g, '')
  if (!s || !/^[A-Za-z0-9+/]*={0,2}$/.test(s) || s.replace(/=+$/, '').length % 4 === 1) return null
  return Buffer.from(s, 'base64')
}

/** Warp.decodeReserved: the first three bytes of client_id as "a,b,c", or "". */
function decodeReserved (clientId) {
  const b = decodeStrictBase64(clientId || '')
  return b && b.length >= 3 ? `${b[0]},${b[1]},${b[2]}` : ''
}

/**
 * The WireGuard configs for a cached account, one per WARP endpoint, named
 * "WARP 1".."WARP 8" like the app. `ids` lets a caller keep stable config ids
 * across restarts (the app stores the configs themselves in ConfigStore).
 */
function warpConfigsFromAccount (account, { ids } = {}) {
  if (!account || !account.privateKey || !account.peerPublicKey) throw new Error('incomplete WARP account')
  const addrs = []
  if (account.v4) addrs.push(`${account.v4}/32`)
  if (account.v6) addrs.push(`${account.v6}/128`)
  const localAddress = account.localAddress || addrs.join(',')
  const reserved = account.reserved != null ? account.reserved : decodeReserved(account.clientId)
  return WARP.ENDPOINTS.map(([host, port], i) => makeProxyConfig({
    id: ids && ids[i],
    name: `WARP ${i + 1}`,
    protocol: 'wireguard',
    address: host,
    port,
    privateKey: account.privateKey,
    publicKey: account.peerPublicKey,
    localAddress,
    mtu: WARP.MTU,
    reserved,
    source: 'PERSONAL'
  }))
}

/**
 * Warp.register(): registers a free WARP device and returns it as WireGuard
 * configs. Mirrors the Kotlin sealed Result instead of throwing:
 *
 *   { ok: true,  config, configs, account }
 *   { ok: false, message }
 *
 * `account` is the persistence-friendly part (JSON-serialisable): cache it
 * and rebuild the configs with warpConfigsFromAccount() instead of
 * re-registering on every start. `id`/`token` are kept from the response
 * (the app ignores them) so a later version can manage the device.
 */
async function registerWarp ({ fetch: fetchImpl = globalThis.fetch, now = Date.now, timeoutMs = WARP.TIMEOUT_MS, signal } = {}) {
  const ctrl = new AbortController()
  const timer = setTimeout(() => ctrl.abort(new Error('WARP registration timed out')), timeoutMs)
  const onAbort = () => ctrl.abort(signal.reason)
  if (signal) signal.addEventListener('abort', onAbort, { once: true })
  try {
    const { privateKey, publicKey } = generateWireguardKeyPair()
    const nowMs = typeof now === 'function' ? now() : now
    // SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") in UTC == toISOString().
    const tos = new Date(nowMs).toISOString()
    const body = JSON.stringify({
      key: publicKey, install_id: '', fcm_token: '', tos, model: 'PC', serial_number: '', locale: 'en_US'
    })
    const res = await fetchImpl(`${WARP.API_BASE}/${WARP.API_VERSION}/reg`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json; charset=UTF-8',
        'User-Agent': WARP.USER_AGENT,
        'CF-Client-Version': WARP.CLIENT_VERSION
      },
      body,
      signal: ctrl.signal
    })
    const text = await res.text()
    if (res.status < 200 || res.status > 299) return { ok: false, message: `HTTP ${res.status}: ${text.slice(0, 200)}` }

    const root = JSON.parse(text)
    const cfg = requireObject(root && root.config, 'config')
    const peers = cfg.peers
    if (!Array.isArray(peers) || !peers.length) throw new Error('JSONObject["peers"] not found.')
    const peerPub = requireString(requireObject(peers[0], 'peers[0]').public_key, 'public_key')
    const addresses = requireObject(requireObject(cfg.interface, 'interface').addresses, 'addresses')
    const v4 = optString(addresses.v4)
    const v6 = optString(addresses.v6)
    const clientId = optString(cfg.client_id)
    const account = {
      version: 1,
      registeredAt: nowMs,
      id: optString(root.id),
      token: optString(root.token),
      license: optString(root.account && root.account.license),
      privateKey,
      publicKey,
      peerPublicKey: peerPub,
      v4,
      v6,
      clientId,
      reserved: decodeReserved(clientId)
    }
    const configs = warpConfigsFromAccount(account)
    return { ok: true, config: configs[0], configs, account }
  } catch (e) {
    return { ok: false, message: (e && e.message) || 'registration error' }
  } finally {
    clearTimeout(timer)
    if (signal) signal.removeEventListener('abort', onAbort)
  }
}

function requireObject (v, name) {
  if (!v || typeof v !== 'object' || Array.isArray(v)) throw new Error(`JSONObject["${name}"] not found.`)
  return v
}
function requireString (v, name) {
  if (v == null) throw new Error(`JSONObject["${name}"] not found.`)
  return String(v)
}
// JSONObject.optString: absent/null -> "", anything else stringified.
function optString (v) { return v == null ? '' : String(v) }

// ---------------------------------------------------------------------------
// 2. Free configs from Telegram (FreeConfigs.kt + freecfg/)
// ---------------------------------------------------------------------------

/** FreeSourceRegistry.DEFAULT_SOURCES, same ids, order and priorities. */
const FREE_SOURCES = Object.freeze([
  ['tg_ghajarvpn', 'Ghajarvpn', 10],
  ['tg_prrofile_purple', 'prrofile_purple', 5],
  ['tg_v2rayngvpn', 'v2rayngvpn', 5],
  ['tg_meliproxyy', 'meliproxyy', 5],
  ['tg_v2ray_tz', 'V2Ray_Tz', 5],
  ['tg_mitivpn', 'mitivpn', 5],
  ['tg_kingoclub', 'kingoclub', 5],
  ['tg_vpn_click', 'vpn_Click', 5],
  ['tg_hiddify_nexttt', 'Hiddify_Nexttt', 5]
].map(([id, channel, priority]) => Object.freeze({
  id, type: 'telegram_channel', endpoint: `https://t.me/s/${channel}`, channel, enabled: true, priority
})))

/** Thresholds of FreeConfigs.refresh / FreeFeedHttp, so the caller can mirror EngineTester use. */
const FREE = Object.freeze({
  CHANNEL: 'Ghajarvpn', // BrandConfig.FREE_CONFIG_CHANNEL
  SOURCE_URL: 'https://t.me/s/Ghajarvpn',
  SUBSCRIPTION_NAME: '@Ghajarvpn',
  CONFIG_NAME: 'Ghajarvpn', // reconcile() names results "Ghajarvpn 1..N"
  MAX_AGE_HOURS: 72, // only posts from the last 72 hours
  FUTURE_SKEW_MS: 300000, // posts up to 5 min "in the future" still count
  FEED_CONCURRENCY: 3, // Semaphore(3) over channels
  EXPANDER_WORKERS: 4, // subscription-link fetchers
  TEST_WORKERS: 8, // EngineTester.realDelay workers
  TEST_DELAY_BOUND_MS: 4000, // realDelay(cfg, boundMs = 4000)
  MAX_LATENCY_MS: 2500, // keep a config only if 0 <= delay <= 2500 ms
  MAX_MANAGED_CONFIGS: 35, // hard cap on the managed free set
  PUBLISH_THROTTLE_MS: 1500, // partial results published at most every 1.5 s
  FEED_TIMEOUT_MS: 12000, // one deadline covering redirects and the whole body
  FEED_MAX_REDIRECTS: 3, // hops 0..3
  FEED_MAX_BYTES: 4 * 1024 * 1024,
  FEED_USER_AGENT: 'Mozilla/5.0 Ghajarvpn/1.0.0',
  // FreeConfigs return codes.
  BUSY: -1,
  UNREACHABLE: -2,
  NO_CONFIGS: -3
})

class FeedHttpError extends Error {}
/** SubscriptionFetcher's SubscriptionError: the body held no configs (not a failure). */
class SubscriptionError extends Error {
  constructor (kind) { super(`subscription: ${kind}`); this.kind = kind }
}

/**
 * FreeFeedHttp.read: GET with manual redirects (max 4 requests), https never
 * downgrades to http, one deadline across redirects and the full body,
 * 4 MiB body cap, UTF-8 decode.
 */
async function feedRead (url, {
  fetch: fetchImpl = globalThis.fetch, signal, timeoutMs = FREE.FEED_TIMEOUT_MS,
  maxBytes = FREE.FEED_MAX_BYTES, userAgent = FREE.FEED_USER_AGENT
} = {}) {
  const deadline = Date.now() + timeoutMs
  const secureStart = new URL(url).protocol === 'https:'
  let current = new URL(url)
  const ctrl = new AbortController()
  const timer = setTimeout(() => ctrl.abort(new FeedHttpError('Feed request timed out')), timeoutMs)
  const onAbort = () => ctrl.abort(signal.reason)
  if (signal) { if (signal.aborted) onAbort(); else signal.addEventListener('abort', onAbort, { once: true }) }
  try {
    for (let hop = 0; hop <= FREE.FEED_MAX_REDIRECTS; hop++) {
      if (!(current.protocol === 'https:' || (!secureStart && current.protocol === 'http:'))) {
        throw new FeedHttpError('Refusing insecure redirect')
      }
      if (Date.now() >= deadline) throw new FeedHttpError('Feed request timed out')
      const res = await fetchImpl(current.href, {
        method: 'GET', redirect: 'manual', headers: { 'User-Agent': userAgent }, signal: ctrl.signal
      })
      if (res.status >= 300 && res.status <= 399) {
        const location = res.headers && res.headers.get('location')
        discardBody(res)
        if (!location) throw new FeedHttpError('Redirect without location')
        current = new URL(location, current)
        continue
      }
      if (res.status < 200 || res.status > 299) { discardBody(res); throw new FeedHttpError(`HTTP ${res.status}`) }
      return await readCapped(res, maxBytes)
    }
    throw new FeedHttpError('Too many redirects')
  } catch (e) {
    if (ctrl.signal.aborted && ctrl.signal.reason instanceof Error) throw ctrl.signal.reason
    throw e
  } finally {
    clearTimeout(timer)
    if (signal) signal.removeEventListener('abort', onAbort)
  }
}

function discardBody (res) {
  try { if (res.body && typeof res.body.cancel === 'function') res.body.cancel().catch(() => {}) } catch (_) {}
}

async function readCapped (res, maxBytes) {
  if (res.body && typeof res.body.getReader === 'function') {
    const reader = res.body.getReader()
    const chunks = []
    let size = 0
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      size += value.byteLength
      if (size > maxBytes) { reader.cancel().catch(() => {}); throw new FeedHttpError('Feed is too large') }
      chunks.push(Buffer.from(value.buffer, value.byteOffset, value.byteLength))
    }
    return Buffer.concat(chunks).toString('utf8')
  }
  const buf = Buffer.from(await res.arrayBuffer())
  if (buf.length > maxBytes) throw new FeedHttpError('Feed is too large')
  return buf.toString('utf8')
}

// --- FreeFeedRules ----------------------------------------------------------

// Java's \s (no UNICODE_CHARACTER_CLASS) is only [ \t\n\x0B\f\r]; JS \s is wider.
const NOT_LINK_CHAR = '[^ \\t\\n\\x0B\\f\\r"\'<>\\\\]'
const DIRECT_LINK = new RegExp(`(?:vless|vmess|trojan|ss|hysteria2|hy2|tuic|wireguard|wg|socks5)://${NOT_LINK_CHAR}+`, 'gi')
const HTTP_LINK = new RegExp(`https?://${NOT_LINK_CHAR}+`, 'gi')
const MESSAGE_TEXT = /<div\s+class=["']tgme_widget_message_text[^"']*["'][^>]*>([\s\S]*?)<\/div>/g
const TELEGRAM_HOSTS = new Set(['t.me', 'telegram.me', 'telegram.org', 'www.t.me'])
const MEDIA_URL = /^.*\.(jpg|jpeg|png|gif|webp|mp4|svg)$/i
const SUBSCRIPTION_LIKE = /\/(sub|subscription|api|s)\/|[?&](token|sub)=/i
const TRAILING_PUNCT = new Set(['.', ',', ')', '،', '؛']) // . , ) ، ؛

/** FreeFeedRules.decode: the HTML entities Telegram uses, plus numeric ones. */
function decodeEntities (value) {
  let r = String(value).replace(/&amp;/g, '&').replace(/&quot;/g, '"').replace(/&apos;/g, "'")
    .replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&nbsp;/g, ' ')
  r = r.replace(/&#(x[0-9a-fA-F]+|[0-9]+);/g, (m, s) => {
    const n = s.startsWith('x') ? parseInt(s.slice(1), 16) : parseInt(s, 10)
    return Number.isFinite(n) && n >= 0 && n <= 0x10ffff ? String.fromCodePoint(n) : m
  })
  return r
}

function trimEndChars (s) {
  let end = s.length
  while (end > 0 && TRAILING_PUNCT.has(s[end - 1])) end--
  return s.slice(0, end)
}

/**
 * The host java.net.URI would report for an http(s) URL, or null when URI
 * would throw or fall back to a registry-based authority (host == null).
 * FreeFeedRules drops such links; WHATWG URL is far more lenient, so this
 * mirrors Java's grammar for the parts that matter here.
 */
function javaUriHost (url) {
  // Characters java.net.URI rejects anywhere (the link regex already excluded
  // whitespace, quotes, angle brackets and backslash).
  if (/[\x00-\x1f\x7f^`{|}]/.test(url)) return { ok: false }
  if (/%(?![0-9a-fA-F]{2})/.test(url)) return { ok: false }
  if ((url.match(/#/g) || []).length > 1) return { ok: false }
  const m = /^https?:\/\/([^/?#]*)/i.exec(url)
  if (!m) return { ok: false }
  if (/[[\]]/.test(url.slice(m[0].length))) return { ok: false }
  const authority = m[1]
  if (!authority) return { ok: true, host: null, userInfo: null }
  let userInfo = null
  let hostPort = authority
  const at = authority.lastIndexOf('@')
  if (at >= 0) { userInfo = authority.slice(0, at); hostPort = authority.slice(at + 1) }
  let host = hostPort
  let port = ''
  if (hostPort.startsWith('[')) {
    const close = hostPort.indexOf(']')
    if (close < 0) return { ok: false }
    host = hostPort.slice(0, close + 1)
    port = hostPort.slice(close + 1)
    if (port && !/^:\d*$/.test(port)) return { ok: true, host: null, userInfo: null }
    return { ok: true, host, userInfo }
  }
  const colon = hostPort.lastIndexOf(':')
  if (colon >= 0) { host = hostPort.slice(0, colon); port = hostPort.slice(colon + 1) }
  if (port && !/^\d+$/.test(port)) return { ok: true, host: null, userInfo: null }
  if (/^\d{1,3}(\.\d{1,3}){3}$/.test(host)) {
    if (host.split('.').every(p => Number(p) <= 255)) return { ok: true, host, userInfo }
  }
  const labels = host.replace(/\.$/, '').split('.')
  const okLabel = l => /^[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?$/.test(l)
  if (!host || !labels.every(okLabel) || !/^[A-Za-z]/.test(labels[labels.length - 1])) {
    return { ok: true, host: null, userInfo: null } // registry-based authority
  }
  return { ok: true, host, userInfo }
}

/**
 * FreeFeedRules.extract over a post's HTML: direct share links plus web links
 * worth trying as subscriptions (Telegram links, media and URLs with
 * credentials dropped; subscription-looking URLs first).
 */
function extractLinks (html) {
  const clean = String(html).replace(/<wbr\s*\/?>/gi, '').replace(/[​‌‍‎‏﻿]/g, '')
  const bodies = []
  for (const m of clean.matchAll(MESSAGE_TEXT)) bodies.push(decodeEntities(m[1]))
  const text = bodies.join('\n')
  const urls = []
  const seen = new Set()
  for (const m of text.matchAll(HTTP_LINK)) {
    const url = trimEndChars(m[0])
    const u = javaUriHost(url)
    const host = u.ok && u.host ? u.host.toLowerCase() : ''
    if (!host || u.userInfo != null || TELEGRAM_HOSTS.has(host)) continue
    if (MEDIA_URL.test(url.split('?')[0])) continue
    if (!seen.has(url)) { seen.add(url); urls.push(url) }
  }
  // Stable sort: opaque /sub/<token> URLs must not lose their slots to ads.
  urls.sort((a, b) => Number(SUBSCRIPTION_LIKE.test(b)) - Number(SUBSCRIPTION_LIKE.test(a)))
  const configs = [...new Set(Array.from(text.matchAll(DIRECT_LINK), m => m[0]))]
  return { configs, subscriptions: urls }
}

const ISO_INSTANT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,9})?)?(Z|[+-]\d{2}:\d{2})$/i

/** java.time.Instant.parse (JDK 12+ accepts offsets, which Telegram uses). */
function parseInstant (s) {
  if (!ISO_INSTANT.test(s)) return null
  const t = Date.parse(s)
  return Number.isFinite(t) ? t : null
}

/**
 * FreeFeedRules.posts: every data-post marker starts a block that runs to the
 * next marker; its <time datetime> is the post time (null when absent).
 */
function parsePosts (html) {
  const src = String(html)
  const markers = Array.from(src.matchAll(/data-post=["'][^/"']+\/(\d+)["']/g))
  return markers.map((marker, i) => {
    const end = i + 1 < markers.length ? markers[i + 1].index : src.length
    const block = src.slice(marker.index, end)
    const stamp = /<time\b[^>]*datetime=["']([^"']+)["']/.exec(block)
    return { id: Number(marker[1]), publishedAt: stamp ? parseInstant(stamp[1]) : null, html: block }
  })
}

/** FreeFeedRules.recentPosts: within maxAge before `now`, at most 5 minutes after it. */
function recentPosts (posts, now, maxAgeHours = FREE.MAX_AGE_HOURS) {
  const cutoff = now - maxAgeHours * 3600 * 1000
  return posts.filter(p => p.publishedAt != null && p.publishedAt >= cutoff && p.publishedAt <= now + FREE.FUTURE_SKEW_MS)
}

// --- SubscriptionFetcher.parseBody ------------------------------------------

const CONFIG_MARKERS = [
  'vless://', 'vmess://', 'trojan://', 'ss://', 'ssr://', 'socks5://', 'socks4://', 'socks://', 'http://',
  'hysteria2://', 'hysteria://', 'hy2://', 'hy://', 'tuic://', 'ikev2://', 'wireguard://', 'wg://', 'warp://',
  'juicity://', 'anytls://', 'mieru://', 'naive+', 'ssh://', 'openconnect://', 'anyconnect://', 'dnstt://',
  'vaydns://', 'noizdns://', 'masterdns://', 'stormdns://', 'cottendns://', 'slipstream://', 'mierus://',
  'brook://', 'amneziawg://', 'naive+https://', 'naive+quic://', 'sstp://', 'softether://'
]

/** Line prefixes ConfigParser.parse turns into a config (Tor bridge lines excluded: no address). */
const SHARE_LINK_PREFIXES = [
  'vless://', 'vmess://', 'trojan://', 'ss://', 'socks5://', 'socks://', 'http://', 'hysteria2://', 'hy2://',
  'tuic://', 'hysteria://', 'anytls://', 'masque://', 'tailscale://', 'tailcat://', 'ssh://', 'openconnect://',
  'anyconnect://', 'dnstt://', 'mieru://', 'mierus://', 'brook://', 'naive+https://', 'naive+quic://',
  'naive://', 'juicity://', 'sstp://', 'softether://', 'amneziawg://', 'awg://', 'vaydns://', 'noizdns://',
  'slipstream://', 'masterdns://', 'stormdns://', 'cottendns://', 'ikev2://', 'wireguard://', 'wg://'
]

function hasConfig (text) {
  const lower = text.toLowerCase()
  if (CONFIG_MARKERS.some(s => lower.includes(s))) return true
  const t = text.trim()
  return (t.startsWith('{') || t.startsWith('[')) && (lower.includes('"outbounds"') || lower.includes('"protocol"'))
}

/** SubscriptionFetcher.decodeMaybeBase64: plain text, or base64/base64url (padded or not). */
function decodeMaybeBase64 (body) {
  const trimmed = String(body).replace(/[﻿​‎‏]/g, '').trim()
  if (hasConfig(trimmed)) return trimmed
  const packed = trimmed.replace(/\s/g, '')
  if (packed && /^[A-Za-z0-9+/_-]+={0,2}$/.test(packed) && packed.replace(/=+$/, '').length % 4 !== 1) {
    const std = packed.replace(/-/g, '+').replace(/_/g, '/')
    const out = Buffer.from(std, 'base64').toString('utf8')
    if (hasConfig(out)) return out
  }
  return trimmed
}

function looksLikeClash (text) {
  const lower = text.toLowerCase()
  return lower.includes('proxy-groups:') || /^\s*proxies:\s*$/m.test(lower)
}

/**
 * SubscriptionFetcher.parseBody / ConfigParser.parseBundle for the desktop:
 * returns { links, bundle }. `links` are the body's share-link lines (what
 * parseBundle's line-by-line fallback parses). `bundle` is set when the body
 * is a whole document parseBundle tries first (WireGuard .conf, Xray/sing-box
 * JSON, Clash YAML) so the caller's parser can handle it; parseBundle falls
 * back to the lines when that yields nothing, and so does fetchFreeLinks.
 * Throws SubscriptionError when there is nothing config-like (an ordinary
 * web page), which the app does not count as a failure.
 */
function parseSubscriptionBody (body) {
  const text = decodeMaybeBase64(body)
  const trimmed = text.trim()
  if (!trimmed) throw new SubscriptionError('EMPTY')
  const lower = trimmed.toLowerCase()
  let bundle = null
  if (lower.includes('[interface]') && lower.includes('[peer]')) bundle = { format: 'wireguard', text: trimmed }
  else if ((trimmed.startsWith('{') || trimmed.startsWith('[')) && hasConfig(trimmed)) bundle = { format: 'json', text: trimmed }
  else if (looksLikeClash(trimmed)) bundle = { format: 'clash', text: trimmed }
  const links = trimmed.split(/[\r\n]/).map(l => l.trim()).filter(Boolean)
    .filter(l => { const ll = l.toLowerCase(); return SHARE_LINK_PREFIXES.some(p => ll.startsWith(p)) })
  if (!links.length && (!bundle || (bundle.format === 'clash' && !/^\s*proxies:/m.test(lower)))) {
    throw new SubscriptionError(looksLikeClash(trimmed) ? 'CLASH' : 'NOT_CONFIG')
  }
  return { links, bundle }
}

// --- Identity ----------------------------------------------------------------

function safeDecodeURIComponent (s) { try { return decodeURIComponent(s) } catch (_) { return s } }

function hostPortOf (authority) {
  const hp = authority.slice(authority.lastIndexOf('@') + 1)
  let host = hp
  let port = ''
  if (hp.startsWith('[')) {
    const close = hp.indexOf(']')
    if (close < 0) return null
    host = hp.slice(1, close)
    port = hp.slice(close + 1).replace(/^:/, '')
  } else {
    const c = hp.lastIndexOf(':')
    if (c >= 0) { host = hp.slice(0, c); port = hp.slice(c + 1) }
  }
  return { host, port }
}

/**
 * Default candidate identity. The app dedupes on configSignature() of the
 * parsed ProxyConfig, i.e. everything but the display name. Without a parser
 * this module approximates that: the share link minus its #name (vmess: its
 * JSON minus "ps", keys sorted) and requires a host plus a valid port when one
 * is given - the app drops configs with a blank address or port outside
 * 1..65535. Returns null to reject. Pass `signature` to fetchFreeLinks to use
 * the real parser (parser.js) instead.
 */
function linkIdentity (link) {
  const raw = String(link).trim()
  const sep = raw.indexOf('://')
  if (sep <= 0) return null
  const scheme = raw.slice(0, sep).toLowerCase()
  let rest = raw.slice(sep + 3)
  if (scheme === 'vmess') {
    const b = decodeMaybeJsonB64(rest.split('#')[0])
    if (b) {
      const { ps, ...keep } = b
      const port = Number(keep.port)
      if (!String(keep.add || '').trim() || !(port >= 1 && port <= 65535)) return null
      return 'vmess://' + JSON.stringify(Object.keys(keep).sort().map(k => [k, String(keep[k])]))
    }
  }
  rest = rest.split('#')[0]
  if (scheme === 'vmess' && !rest.includes('@')) return null // neither JSON nor uuid@host:port
  if (scheme === 'ss' && !rest.split('?')[0].includes('@')) {
    // Legacy ss://base64(method:pass@host:port)
    const dec = decodeLooseB64(rest.split('?')[0].replace(/\/$/, ''))
    if (!dec || !dec.includes('@')) return null
    rest = dec
  }
  const authority = rest.split(/[/?]/)[0]
  const hp = hostPortOf(authority)
  if (!hp || !safeDecodeURIComponent(hp.host).trim()) return null
  if (hp.port !== '') {
    const port = Number(hp.port)
    if (!/^\d+$/.test(hp.port) || port < 1 || port > 65535) return null
  }
  return `${scheme}://${rest}`
}

function decodeLooseB64 (s) {
  const t = String(s).trim()
  if (!t || !/^[A-Za-z0-9+/_-]+={0,2}$/.test(t)) return null
  return Buffer.from(t.replace(/-/g, '+').replace(/_/g, '/'), 'base64').toString('utf8')
}
function decodeMaybeJsonB64 (s) {
  const dec = decodeLooseB64(s)
  if (!dec) return null
  try { const o = JSON.parse(dec); return o && typeof o === 'object' && !Array.isArray(o) ? o : null } catch (_) { return null }
}

// --- FreeConfigs.refresh (collection half) ------------------------------------

function limiter (n) {
  let active = 0
  const queue = []
  const pump = () => {
    while (active < n && queue.length) {
      const { fn, resolve, reject } = queue.shift()
      active++
      Promise.resolve().then(fn).then(resolve, reject).finally(() => { active--; pump() })
    }
  }
  return fn => new Promise((resolve, reject) => { queue.push({ fn, resolve, reject }); pump() })
}

function normalizeSource (s) {
  if (typeof s === 'string') {
    const channel = s.replace(/^https?:\/\/t\.me\/(s\/)?/i, '').replace(/^@/, '').replace(/\/.*$/, '')
    return { id: `tg_${channel.toLowerCase()}`, type: 'telegram_channel', endpoint: `https://t.me/s/${channel}`, channel, enabled: true, priority: 0 }
  }
  const channel = s.channel || String(s.endpoint || '').replace(/^https?:\/\/t\.me\/s\//i, '').replace(/[/?].*$/, '')
  return { enabled: true, priority: 0, type: 'telegram_channel', ...s, channel }
}

/**
 * The collection half of FreeConfigs.refresh: pages every enabled channel's
 * t.me/s preview (?before=<oldest id>) back to `maxAgeHours`, extracts share
 * links from fresh posts, follows web links as subscriptions, and dedupes
 * every candidate across all sources. Latency testing is the caller's job;
 * mirror the app with FREE.TEST_WORKERS / TEST_DELAY_BOUND_MS /
 * MAX_LATENCY_MS / MAX_MANAGED_CONFIGS (sort kept configs fastest first and
 * pass them to reconcile()).
 *
 * Options:
 *   fetch        fetch implementation (route it through the tunnel when one
 *                is up, as the app does with its SOCKS route())
 *   now          ms timestamp or function returning one (the scan's start)
 *   maxAgeHours  default 72
 *   channels     registry entries or bare channel names (default FREE_SOURCES)
 *   signature    (link) => identity string | null (reject); default linkIdentity
 *   parseBundle  (text, format) => share links, for JSON/Clash/WireGuard bodies
 *   onLink       (item) => void, called as each unique candidate is found so
 *                testing can start while collection continues (like the app)
 *   onProgress   ({ candidates, pages, collecting }) => void
 *   signal       AbortSignal
 *
 * Resolves to { links, bundles, pages, reachable, failures, incomplete,
 * status, code } where links are { link, channel, sourceId, priority, postId,
 * publishedAt, via: 'post'|'subscription', subscriptionUrl? }. status is
 * 'unreachable' (code -2) when no feed page could be read, 'no_configs'
 * (code -3) when nothing was found and something failed, else 'ok'
 * (code = number of links).
 */
async function fetchFreeLinks ({
  fetch: fetchImpl = globalThis.fetch,
  now = Date.now,
  maxAgeHours = FREE.MAX_AGE_HOURS,
  channels = FREE_SOURCES,
  signature = linkIdentity,
  parseBundle = null,
  onLink = null,
  onProgress = null,
  signal = undefined,
  feedConcurrency = FREE.FEED_CONCURRENCY,
  expanderWorkers = FREE.EXPANDER_WORKERS
} = {}) {
  const started = typeof now === 'function' ? now() : now
  const maxAgeMs = maxAgeHours * 3600 * 1000
  const candidates = new Map() // identity -> item
  const followed = new Set()
  const links = []
  const bundles = []
  let pages = 0
  let reachable = 0
  let failures = 0
  let collecting = true
  const progress = () => { if (onProgress) try { onProgress({ candidates: candidates.size, pages, collecting }) } catch (_) {} }
  const checkAbort = () => { if (signal && signal.aborted) throw signal.reason || new Error('aborted') }
  const http = url => feedRead(url, { fetch: fetchImpl, signal })

  const offer = (link, meta) => {
    let id
    try { id = signature(link) } catch (_) { id = null }
    if (!id || candidates.has(id)) return
    const item = { link, ...meta }
    candidates.set(id, item)
    links.push(item)
    if (onLink) try { onLink(item) } catch (_) {}
    progress()
  }

  const expand = limiter(expanderWorkers)
  const expansions = []
  const follow = (url, meta) => expansions.push(expand(async () => {
    checkAbort()
    try {
      const body = await http(url)
      const { links: found, bundle } = parseSubscriptionBody(body)
      let all = found
      if (bundle) {
        const parsed = parseBundle ? (await parseBundle(bundle.text, bundle.format)) || [] : []
        if (parsed.length) all = parsed.map(String)
        else if (!parseBundle && !found.length) bundles.push({ ...bundle, url, channel: meta.channel, sourceId: meta.sourceId })
      }
      for (const link of all) offer(link, { ...meta, via: 'subscription', subscriptionUrl: url })
    } catch (e) {
      if (signal && signal.aborted) throw e
      // A message's web link may be an ordinary page: not a failure.
      if (!(e instanceof SubscriptionError)) failures++
    }
  }))

  const feeds = limiter(feedConcurrency)
  const sources = channels.map(normalizeSource).filter(s => s.enabled !== false)
  await Promise.all(sources.map(source => feeds(async () => {
    let before = 0
    try {
      for (;;) {
        checkAbort()
        const url = source.endpoint + (before === 0 ? '' : `?before=${before}`)
        const html = await http(url)
        const posts = parsePosts(html)
        if (!posts.length) { failures++; break }
        reachable++; pages++; progress()
        const meta = { channel: source.channel, sourceId: source.id, priority: source.priority }
        for (const post of recentPosts(posts, started, maxAgeHours)) {
          const found = extractLinks(post.html)
          const postMeta = { ...meta, postId: post.id, publishedAt: post.publishedAt }
          for (const link of found.configs) offer(link, { ...postMeta, via: 'post' })
          for (const sub of found.subscriptions) {
            if (!followed.has(sub)) { followed.add(sub); follow(sub, postMeta) }
          }
        }
        // Undated posts make the age cut-off unreliable: mark incomplete, keep paging.
        if (posts.some(p => p.publishedAt == null)) failures++
        if (posts.every(p => (p.publishedAt == null ? Infinity : p.publishedAt) < started - maxAgeMs)) break
        const oldest = Math.min(...posts.map(p => p.id))
        if (oldest <= 1) break
        if (before !== 0 && oldest >= before) { failures++; break } // paging did not advance
        before = oldest
      }
    } catch (e) {
      if (signal && signal.aborted) throw e
      failures++
    }
  })))
  await Promise.all(expansions)
  collecting = false
  progress()

  let status = 'ok'
  let code = links.length
  if (reachable === 0) { status = 'unreachable'; code = FREE.UNREACHABLE } else if (candidates.size === 0 && failures > 0) { status = 'no_configs'; code = FREE.NO_CONFIGS }
  return { links, bundles, pages, reachable, failures, incomplete: failures > 0, status, code }
}

/**
 * FreeFeedRules.reconcile: healthy (already fastest-first) configs, then -
 * only after an incomplete scan - previous configs that were not re-tested,
 * deduped and capped, renamed "Ghajarvpn 1..N". `tested` holds signatures.
 */
function reconcile (previous, healthy, tested, complete, { limit = FREE.MAX_MANAGED_CONFIGS, signature = configSignature, rename = true } = {}) {
  const testedSet = tested instanceof Set ? tested : new Set(tested)
  const retained = complete ? [] : previous.filter(c => !testedSet.has(signature(c)))
  const seen = new Set()
  const out = []
  for (const c of [...healthy, ...retained]) {
    const s = signature(c)
    if (seen.has(s)) continue
    seen.add(s)
    out.push(c)
    if (out.length >= limit) break
  }
  return rename && out.length && typeof out[0] === 'object'
    ? out.map((c, i) => ({ ...c, name: `${FREE.CONFIG_NAME} ${i + 1}` }))
    : out
}

/** FreeFeedRules.select: round-robin over groups so one big source cannot crowd out the rest. */
function selectRoundRobin (groups, limit = Infinity, signature = configSignature) {
  const unique = new Map()
  const longest = groups.reduce((m, g) => Math.max(m, g.length), 0)
  for (let i = 0; i < longest; i++) {
    for (const g of groups) if (i < g.length) { const s = signature(g[i]); if (!unique.has(s)) unique.set(s, g[i]) }
    if (unique.size >= limit) break
  }
  return [...unique.values()].slice(0, limit)
}

// --- FreeConfigPipeline / TelegramWebFetcher / PasswordedContainer ------------

const PASSWORD_PATTERNS = [
  /password\s*[:=]\s*([^ \t\n\x0B\f\r]+)/i,
  /\bpass\s*[:=]\s*([^ \t\n\x0B\f\r]+)/i,
  /رمز\s*(?:عبور)?\s*[:=]\s*([^ \t\n\x0B\f\r]+)/i,
  /پسورد\s*(?:فایل)?\s*[:=]\s*([^ \t\n\x0B\f\r]+)/i
]

/** FreeConfigPipeline.passwordFromContext: caption first, then message, then grouped texts. */
function passwordFromContext (caption, message, grouped) {
  const find = text => {
    for (const re of PASSWORD_PATTERNS) { const m = re.exec(text); if (m) return m[1] }
    return null
  }
  for (const scope of [caption, message]) {
    if (scope == null) continue
    const p = find(scope)
    if (p && p.trim()) return p
  }
  for (const text of grouped || []) { const p = find(text); if (p && p.trim()) return p }
  return null
}

/** FreeConfigPipeline.countryOf: the first regional-indicator flag pair. */
function countryOf (text) {
  const m = /\uD83C[\uDDE6-\uDDFF]\uD83C[\uDDE6-\uDDFF]/.exec(String(text))
  return m ? m[0] : ''
}

/** FreeConfigPipeline.dedupe / rank. */
function dedupeItems (items) {
  const seen = new Set()
  return items.filter(i => (seen.has(i.hash) ? false : (seen.add(i.hash), true)))
}
function rankItems (items) {
  return [...items].sort((a, b) => (b.fetchedAt - a.fetchedAt) || (a.sourceId < b.sourceId ? -1 : a.sourceId > b.sourceId ? 1 : 0))
}

/**
 * PasswordedContainer.tryDecrypt: [16-byte salt][16-byte IV][AES-CBC body],
 * key = SHA-256(password || salt) truncated to 32 then 16 bytes. Accepted only
 * when the plaintext looks like configs. Returns a Buffer or null.
 */
function tryDecryptContainer (bytes, password) {
  const buf = Buffer.from(bytes)
  if (!password || !String(password).trim() || buf.length < 32) return null
  const salt = buf.subarray(0, 16)
  const iv = buf.subarray(16, 32)
  const body = buf.subarray(32)
  for (const keyLength of [32, 16]) {
    const key = crypto.createHash('sha256').update(Buffer.from(String(password), 'utf8')).update(salt).digest().subarray(0, keyLength)
    let out
    try {
      const d = crypto.createDecipheriv(keyLength === 32 ? 'aes-256-cbc' : 'aes-128-cbc', key, iv)
      out = Buffer.concat([d.update(body), d.final()])
    } catch (_) { continue }
    const text = out.toString('utf8')
    if (text.includes('://') || text.includes('{') || text.includes('proxies:')) return out
  }
  return null
}

/**
 * TelegramWebFetcher.parsePreview (the pipeline's alternative parser; the live
 * refresh path uses parsePosts/extractLinks). Kotlin's `replace("<br/?>", "\n")`
 * is a literal-string replace, so <br> tags are simply stripped; kept as is.
 */
function parsePreview (html) {
  const out = []
  const block = /<div[^>]*class="tgme_widget_message[^"]*"[^>]*data-post="[^"]*?(\d+)"[^>]*>([\s\S]*?)<\/div>\s*(?=<div[^>]*class="tgme_widget_message|$)/g
  const textTag = /<div[^>]*class="tgme_widget_message_text[^"]*"[^>]*>([\s\S]*?)<\/div>/
  for (const m of String(html).matchAll(block)) {
    const id = Number(m[1])
    if (!Number.isFinite(id)) continue
    const t = textTag.exec(m[2])
    const text = stripTags(t ? t[1] : m[2])
    if (text.trim()) out.push({ id, text })
  }
  return out
}
function stripTags (html) {
  return html.split('<br/?>').join('\n').replace(/<[^>]+>/g, '')
    .replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&nbsp;/g, ' ').trim()
}

// --- FreeConfigStore -------------------------------------------------------

/** FreeConfigStore.loadItems: tolerant read; items from unknown sources are dropped. */
function loadFreeStore (file, { sources = FREE_SOURCES } = {}) {
  let root = {}
  try { root = JSON.parse(fs.readFileSync(file, 'utf8')) || {} } catch (_) { root = {} }
  const ids = new Set(sources.map(s => s.id))
  const items = (Array.isArray(root.items) ? root.items : []).filter(o => o && typeof o === 'object').map(o => ({
    link: optString(o.link),
    hash: optString(o.hash),
    protocol: optString(o.protocol),
    country: optString(o.country),
    name: optString(o.name),
    sourceId: optString(o.sourceId),
    fetchedAt: Number(o.fetchedAt) || 0,
    lastChecked: Number(o.lastChecked) || 0,
    ok: o.ok === true,
    pingMs: o.pingMs == null ? -1 : Number(o.pingMs),
    failureStreak: Number(o.failureStreak) || 0,
    favorite: o.favorite === true
  })).filter(i => ids.has(i.sourceId))
  return { sources: sources.map(s => ({ ...s })), items }
}

/** FreeConfigStore.save: one JSON document, written via tmp file + rename. */
function saveFreeStore (file, sources, items) {
  const root = {
    sources: sources.map(s => ({
      id: s.id, type: s.type || 'telegram_channel', endpoint: s.endpoint, enabled: s.enabled !== false,
      priority: s.priority || 0, lastFetch: s.lastFetch || 0, lastSuccess: s.lastSuccess || 0,
      lastFailure: s.lastFailure || 0, failureStreak: s.failureStreak || 0, itemsFound: s.itemsFound || 0,
      validItems: s.validItems || 0, duplicateRate: s.duplicateRate || 0
    })),
    items: items.map(i => ({
      link: i.link, hash: i.hash, protocol: i.protocol, country: i.country, name: i.name, sourceId: i.sourceId,
      fetchedAt: i.fetchedAt, lastChecked: i.lastChecked, ok: i.ok, pingMs: i.pingMs == null ? -1 : i.pingMs,
      failureStreak: i.failureStreak || 0, favorite: !!i.favorite
    }))
  }
  const text = JSON.stringify(root)
  fs.mkdirSync(path.dirname(file), { recursive: true })
  const tmp = file + '.tmp'
  fs.writeFileSync(tmp, text)
  try { fs.renameSync(tmp, file) } catch (_) { fs.writeFileSync(file, text); try { fs.unlinkSync(tmp) } catch (__) {} }
}

// ---------------------------------------------------------------------------
// 3. Psiphon (PsiphonConfig.kt, PsiphonRegions.kt, PsiphonEngine.kt)
// ---------------------------------------------------------------------------

const PSIPHON_MODES = Object.freeze({ AUTO: 'auto', CDN: 'cdn', DIRECT: 'direct' })

const PSIPHON_CDN_PROTOCOLS = Object.freeze([
  'FRONTED-MEEK-CDN-OSSH', 'FRONTED-MEEK-CDN-HTTP-OSSH', 'FRONTED-MEEK-CDN-QUIC-OSSH'
])
const PSIPHON_NON_INPROXY_PROTOCOLS = Object.freeze([
  'SSH', 'OSSH', 'TLS-OSSH', 'UNFRONTED-MEEK-OSSH', 'UNFRONTED-MEEK-HTTPS-OSSH',
  'UNFRONTED-MEEK-SESSION-TICKET-OSSH', 'QUIC-OSSH', 'SHADOWSOCKS-OSSH', 'FRONTED-MEEK-OSSH',
  'FRONTED-MEEK-CDN-OSSH', 'FRONTED-MEEK-HTTP-OSSH', 'FRONTED-MEEK-CDN-HTTP-OSSH',
  'FRONTED-MEEK-QUIC-OSSH', 'FRONTED-MEEK-CDN-QUIC-OSSH'
])

// Psiphon Inc's public propagation/sponsor IDs and remote server list
// location/signature, embedded in client builds by design (not secrets).
const PSIPHON = Object.freeze({
  PROPAGATION_CHANNEL_ID: 'FFFFFFFFFFFFFFFF',
  SPONSOR_ID: 'FFFFFFFFFFFFFFFF',
  CLIENT_VERSION: '1',
  SERVER_LIST_URL: 'https://s3.amazonaws.com//psiphon/web/mjr4-p23r-puwl/server_list_compressed',
  SERVER_LIST_SIGNATURE_KEY:
    'MIICIDANBgkqhkiG9w0BAQEFAAOCAg0AMIICCAKCAgEAt7Ls+/39r+T6zNW7GiVpJfzq/xvL9SBH' +
    '5rIFnk0RXYEYavax3WS6HOD35eTAqn8AniOwiH+DOkvgSKF2caqk/y1dfq47Pdymtwzp9ikpB1C5' +
    'OfAysXzBiwVJlCdajBKvBZDerV1cMvRzCKvKwRmvDmHgphQQ7WfXIGbRbmmk6opMBh3roE42Kcot' +
    'LFtqp0RRwLtcBRNtCdsrVsjiI1Lqz/lH+T61sGjSjQ3CHMuZYSQJZo/KrvzgQXpkaCTdbObxHqb6' +
    '/+i1qaVOfEsvjoiyzTxJADvSytVtcTjijhPEV6XskJVHE1Zgl+7rATr/pDQkw6DPCNBS1+Y6fy7G' +
    'stZALQXwEDN/qhQI9kWkHijT8ns+i1vGg00Mk/6J75arLhqcodWsdeG/M/moWgqQAnlZAGVtJI1O' +
    'geF5fsPpXu4kctOfuZlGjVZXQNW34aOzm8r8S0eVZitPlbhcPiR4gT/aSMz/wd8lZlzZYsje/Jr8' +
    'u/YtlwjjreZrGRmG8KMOzukV3lLmMppXFMvl4bxv6YFEmIuTsOhbLTwFgh7KYNjodLj/LsqRVfwz' +
    '31PgWQFTEPICV7GCvgVlPRxnofqKSjgTWI4mxDhBpVcATvaoBl1L/6WLbFvBsoAUBItWwctO2xal' +
    'KxF5szhGm8lccoc5MZr8kfE0uxMgsxz4er68iCID+rsCAQM=',
  // PsiphonController: give up when no SOCKS port + first tunnel within 90 s.
  READY_TIMEOUT_MS: 90000
})

/**
 * PsiphonRegions.FALLBACK: exit regions offered before the engine reports its
 * own list (AvailableEgressRegions notice). Replace with normalizePsiphonRegions()
 * output once one arrives and persist it, as the app does.
 */
const PSIPHON_REGIONS = Object.freeze([
  'AT', 'AU', 'BE', 'BG', 'CA', 'CH', 'CZ', 'DE', 'DK', 'EE', 'ES',
  'FI', 'FR', 'GB', 'HR', 'HU', 'IE', 'IN', 'IT', 'JP', 'LT', 'LV',
  'MX', 'NL', 'NO', 'PL', 'PT', 'RO', 'RS', 'SE', 'SG', 'SI', 'SK',
  'US'
])

function psiphonMode (raw) {
  const v = String(raw == null ? '' : raw).trim()
  return v === PSIPHON_MODES.CDN || v === PSIPHON_MODES.DIRECT ? v : PSIPHON_MODES.AUTO
}

/** PsiphonConfig.chainedProtocols: protocols usable through an upstream SOCKS proxy (no QUIC/in-proxy). */
function psiphonChainedProtocols (raw) {
  const mode = psiphonMode(raw)
  const list = mode === PSIPHON_MODES.CDN ? PSIPHON_CDN_PROTOCOLS : PSIPHON_NON_INPROXY_PROTOCOLS
  return list.filter(p => !(p.includes('QUIC') || p.startsWith('INPROXY') || (mode === PSIPHON_MODES.DIRECT && p.startsWith('FRONTED'))))
}

const PSIPHON_SEPARATORS = /[ \t\n\r,;]/

function isIpv4 (value) {
  const parts = value.split('.')
  return parts.length === 4 && parts.every(p => p.length > 0 && p.length <= 3 && /^\d+$/.test(p) && Number(p) <= 255)
}
function isIpv4Cidr (value) {
  const parts = value.split('/')
  return parts.length === 2 && isIpv4(parts[0]) && /^\d+$/.test(parts[1]) && Number(parts[1]) <= 32
}

/** PsiphonConfig.ipCandidates: IPv4 addresses / CIDRs from free text, deduped. */
function psiphonIpCandidates (raw) {
  return [...new Set(String(raw || '').split(PSIPHON_SEPARATORS).map(s => s.trim()).filter(s => s && (isIpv4(s) || isIpv4Cidr(s))))]
}

function normalizeHostname (raw) {
  let v = raw.trim().toLowerCase()
  for (const prefix of ['https://', 'http://']) if (v.startsWith(prefix)) v = v.slice(prefix.length)
  v = v.split('/')[0].replace(/^\.+|\.+$/g, '')
  if (!v || !v.includes('.')) return ''
  if (!/^[a-z0-9.-]+$/.test(v)) return ''
  return v
}

/** PsiphonConfig.sniCandidates: ASCII host names from free text, deduped. */
function psiphonSniCandidates (raw) {
  return [...new Set(String(raw || '').split(PSIPHON_SEPARATORS).map(normalizeHostname).filter(Boolean))]
}

/** PsiphonConfig.putCdnFronting. */
function putCdnFronting (config, cdnIps, cdnSni) {
  const addresses = psiphonIpCandidates(cdnIps)
  const serverNames = psiphonSniCandidates(cdnSni)
  if (!addresses.length) {
    config.FrontedMeekCDNScanUseBuiltInSpec = true
  } else {
    const spec = { IPCandidates: addresses }
    if (serverNames.length) spec.SNIServerNames = serverNames
    config.FrontedMeekCDNScanSpec = spec
  }
  const dialAddresses = addresses.length ? addresses : serverNames
  if (!dialAddresses.length || !serverNames.length) return
  config.FrontedMeekDialOverrides = [{
    OverrideID: 'user',
    MatchDialAddressRegexes: ['.*'],
    DialAddresses: dialAddresses,
    SNIServerName: serverNames[0],
    VerifyServerNames: serverNames
  }]
}

function defaultClientPlatform () {
  const name = { win32: 'Windows', darwin: 'Mac', linux: 'Linux' }[process.platform] || process.platform
  // PsiphonTunnel.java builds "Android_<release>_<package>" and sanitises it the same way.
  return `${name}_${os.release()}_net.gozar.app.desktop`.replace(/[^\w\-.]/g, '_')
}

/**
 * PsiphonConfig.build for psiphon-tunnel-core's ConsoleClient (write it as
 * JSON and pass psiphonArgs(path)). Options:
 *
 *   mode          'auto' | 'cdn' | 'direct' (anything else = auto)
 *   country       ISO-3166 exit region, blank = any
 *   cdnIps        free-text IPv4 / CIDR list for the fronted-meek CDN scan
 *   cdnSni        free-text SNI host names for it
 *   dataDir       DataRootDirectory (created by the caller; absolute-ised here)
 *   socksPort     LocalSocksProxyPort, 0 = let tunnel-core pick (read the
 *                 ListeningSocksProxyPort notice, as PsiphonController does)
 *   httpPort      LocalHttpProxyPort (omitted when not given)
 *   allowLan      ListenInterface "any" (Oblivion's allowLan)
 *   upstreamProxyUrl  e.g. socks5://127.0.0.1:1829 - the app's "chain" core:
 *                 Psiphon dials through another local proxy (Aether)
 *   clientPlatform, establishTimeoutSeconds: see below
 *
 * The Android ca.psiphon.PsiphonTunnel wrapper also injects a few keys before
 * handing the JSON to the Go library; the ones that matter on a desktop are
 * added here too: EstablishTunnelTimeoutSeconds = 0 (keep trying until
 * connected) and ClientPlatform. Android-only migration keys are not.
 */
function psiphonConfig ({
  mode = 'auto', country = '', cdnIps = '', cdnSni = '', dataDir, socksPort = 0, httpPort,
  allowLan = false, upstreamProxyUrl = '', clientPlatform = defaultClientPlatform(),
  establishTimeoutSeconds = 0
} = {}) {
  const config = {}
  config.PropagationChannelId = PSIPHON.PROPAGATION_CHANNEL_ID
  config.SponsorId = PSIPHON.SPONSOR_ID
  config.ClientVersion = PSIPHON.CLIENT_VERSION
  if (dataDir) config.DataRootDirectory = path.resolve(dataDir)
  config.LocalSocksProxyPort = Number(socksPort) || 0
  if (httpPort != null && httpPort !== '') config.LocalHttpProxyPort = Number(httpPort) || 0
  if (allowLan) config.ListenInterface = 'any'
  config.EmitDiagnosticNotices = true
  config.EmitDiagnosticNetworkParameters = true
  config.EmitServerAlerts = true

  const region = String(country || '').trim().toUpperCase()
  if (region) config.EgressRegion = region

  config.RemoteServerListURLs = [{ URL: Buffer.from(PSIPHON.SERVER_LIST_URL).toString('base64') }]
  config.RemoteServerListSignaturePublicKey = PSIPHON.SERVER_LIST_SIGNATURE_KEY
  // No ServerEntrySignaturePublicKey in this build, so in-proxy is never chosen.
  config.InproxyTunnelProtocolPreferProbability = 0.0
  config.InproxyTunnelProtocolSelectionProbability = 0.0

  putCdnFronting(config, cdnIps, cdnSni)

  if (upstreamProxyUrl) {
    config.UpstreamProxyURL = upstreamProxyUrl
    config.LimitTunnelProtocols = psiphonChainedProtocols(mode)
    if (psiphonMode(mode) !== PSIPHON_MODES.AUTO) config.DisableTactics = true
  } else {
    switch (psiphonMode(mode)) {
      case PSIPHON_MODES.CDN:
        config.LimitTunnelProtocols = [...PSIPHON_CDN_PROTOCOLS]
        config.DisableTactics = true
        break
      case PSIPHON_MODES.DIRECT:
        // As in the app: the full non-in-proxy list, tactics off.
        config.LimitTunnelProtocols = [...PSIPHON_NON_INPROXY_PROTOCOLS]
        config.DisableTactics = true
        break
      default:
        config.LimitTunnelProtocols = [...PSIPHON_NON_INPROXY_PROTOCOLS]
    }
  }

  // PsiphonTunnel.java additions.
  if (establishTimeoutSeconds != null) config.EstablishTunnelTimeoutSeconds = Number(establishTimeoutSeconds) || 0
  if (clientPlatform) config.ClientPlatform = String(clientPlatform).replace(/[^\w\-.]/g, '_')
  return config
}

/**
 * ConsoleClient command line (native/Psiphon/ConsoleClient/main.go flags):
 *   -config <file>              required
 *   -dataRootDirectory <dir>    overrides DataRootDirectory
 *   -listenInterface <name>     overrides ListenInterface
 *   -serverList <file>          embedded server entry list
 *   -notices <file>             notices to a file instead of stderr
 *   -formatNotices              human-readable notices (leave off to parse JSON)
 * Notices are one JSON object per line on stderr: parse with parsePsiphonNotice().
 */
function psiphonArgs (configPath, { dataDir, listenInterface, serverList, noticesFile, formatNotices = false } = {}) {
  if (!configPath) throw new Error('psiphon config path is required')
  const args = ['-config', String(configPath)]
  if (dataDir) args.push('-dataRootDirectory', String(dataDir))
  if (listenInterface) args.push('-listenInterface', String(listenInterface))
  if (serverList) args.push('-serverList', String(serverList))
  if (noticesFile) args.push('-notices', String(noticesFile))
  if (formatNotices) args.push('-formatNotices')
  return args
}

/** One ConsoleClient notice line -> { noticeType, data, timestamp } or null. */
function parsePsiphonNotice (line) {
  const s = String(line).trim()
  if (!s.startsWith('{')) return null
  try {
    const o = JSON.parse(s)
    return o && typeof o.noticeType === 'string' ? { noticeType: o.noticeType, data: o.data || {}, timestamp: o.timestamp || '' } : null
  } catch (_) { return null }
}

/**
 * The PsiphonTunnel.handlePsiphonNotice -> PsiphonRuntime callback mapping,
 * as plain events. `fatal: true` is where PsiphonRuntime calls fail().
 * PsiphonController treats the engine as up only after BOTH 'socksPort' and
 * 'connected' arrive within PSIPHON.READY_TIMEOUT_MS.
 */
function psiphonNoticeEvent (notice) {
  const n = typeof notice === 'string' ? parsePsiphonNotice(notice) : notice
  if (!n) return null
  const d = n.data || {}
  switch (n.noticeType) {
    case 'Tunnels':
      if (d.count === 0) return { type: 'connecting' }
      if (d.count === 1) return { type: 'connected' }
      return null // additional multi-tunnel establishment, not reported
    case 'ListeningSocksProxyPort': return { type: 'socksPort', port: Number(d.port) }
    case 'ListeningHttpProxyPort': return { type: 'httpPort', port: Number(d.port) }
    case 'SocksProxyPortInUse': return { type: 'socksPortInUse', port: Number(d.port), fatal: true, message: `socks port ${d.port} already in use` }
    case 'HttpProxyPortInUse': return { type: 'httpPortInUse', port: Number(d.port) }
    case 'AvailableEgressRegions': return { type: 'regions', regions: normalizePsiphonRegions(d.regions || []) }
    case 'ConnectedServerRegion': return { type: 'serverRegion', region: String(d.serverRegion || '') }
    case 'ClientRegion': return { type: 'clientRegion', region: String(d.region || '') }
    case 'UpstreamProxyError': return { type: 'upstreamProxyError', fatal: true, message: `upstream proxy error: ${d.message || ''}` }
    case 'InproxyMustUpgrade': return { type: 'inproxyMustUpgrade', fatal: true, message: 'this build is too old for in-proxy mode' }
    case 'Exiting': return { type: 'exiting' }
    default: return { type: 'diagnostic', noticeType: n.noticeType, data: d }
  }
}

/**
 * PsiphonRegions.report's cleaning: upper-case two-letter codes, deduped,
 * sorted. An empty result means "ignore this report" (Psiphon sends early
 * empty ones that must not wipe a good list).
 */
function normalizePsiphonRegions (reported) {
  return [...new Set((reported || []).map(r => String(r).trim().toUpperCase()).filter(r => /^[A-Z]{2}$/.test(r)))].sort()
}

/** PsiphonRegions.displayName: localized country name ('fa' or 'en'), or the bare code. */
function psiphonRegionName (code, lang = 'en') {
  const c = String(code || '').trim().toUpperCase()
  if (c.length !== 2) return c
  try {
    const name = new Intl.DisplayNames([lang === 'fa' ? 'fa' : 'en'], { type: 'region' }).of(c)
    return name && name !== c ? name : c
  } catch (_) { return c }
}

module.exports = {
  // ProxyConfig
  PROXY_CONFIG_DEFAULTS,
  makeProxyConfig,
  configSignature,
  // WARP
  WARP,
  registerWarp,
  warpConfigsFromAccount,
  generateWireguardKeyPair,
  wireguardPublicKey,
  x25519,
  decodeReserved,
  // Free configs
  FREE,
  FREE_SOURCES,
  REGISTRY: FREE_SOURCES,
  fetchFreeLinks,
  feedRead,
  extractLinks,
  parsePosts,
  recentPosts,
  decodeEntities,
  decodeMaybeBase64,
  parseSubscriptionBody,
  linkIdentity,
  reconcile,
  selectRoundRobin,
  passwordFromContext,
  countryOf,
  dedupeItems,
  rankItems,
  tryDecryptContainer,
  parsePreview,
  loadFreeStore,
  saveFreeStore,
  SubscriptionError,
  FeedHttpError,
  // Psiphon
  PSIPHON,
  PSIPHON_MODES,
  PSIPHON_REGIONS,
  PSIPHON_CDN_PROTOCOLS,
  PSIPHON_NON_INPROXY_PROTOCOLS,
  psiphonConfig,
  psiphonArgs,
  psiphonMode,
  psiphonChainedProtocols,
  psiphonIpCandidates,
  psiphonSniCandidates,
  parsePsiphonNotice,
  psiphonNoticeEvent,
  normalizePsiphonRegions,
  psiphonRegionName
}
