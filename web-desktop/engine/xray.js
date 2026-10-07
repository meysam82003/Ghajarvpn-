'use strict'
// Xray configuration builder for the desktop app.
//
// A port of the Android app's ConfigBuilder.kt and NoiseSpec.kt
// (app/src/main/java/net/gozar/app/), so a profile connects the same way on
// both. Configs are plain objects with the field names of ProxyConfig.toJson();
// settings mirror the SharedPreferences ConfigStore.kt keeps (see
// DEFAULT_SETTINGS).
//
// The one structural difference: Android feeds Xray a TUN fd ("tun-in"). The
// desktop core instead always exposes a SOCKS5 inbound ("socks") and an HTTP
// inbound ("http") on 127.0.0.1, and a separate sing-box TUN forwards device
// traffic into the SOCKS inbound. So "socks" inherits tun-in's sniffing rules
// (it carries the device traffic) and "http" inherits socks-in's.
//
// Geo data: the rules reference geoip:private, geoip:ir, geosite:category-ir,
// geosite:youtube, geosite:category-ads-all and geosite:ads. The last one only
// exists in the Iran-specific geosite.dat the app ships and updates
// (Chocolate4U/Iran-v2ray-rules, GeoData.kt); the stock v2fly/Xray release
// geosite.dat lacks it and Xray refuses to start with ad blocking on. See
// REQUIRED_GEO.

const crypto = require('crypto')
const net = require('net')
const tls = require('tls')

// ------------------------------------------------------------------ settings

/**
 * The user settings ConfigBuilder.build() is called with, at the app's
 * defaults. Each key names the ConfigStore.kt SharedPreferences key it mirrors.
 */
const DEFAULT_SETTINGS = Object.freeze({
  fragment: false,              // "fragment_enabled"
  fragmentPackets: 'tlshello',  // "fragment_packets"
  fragmentLength: '10-20',      // "fragment_length"
  fragmentInterval: '10-20',    // "fragment_interval"
  splitRouting: false,          // "split_routing_enabled" (Iran direct: geoip:private+ir, geosite:category-ir)
  sniffing: false,              // "sniffing_enabled"
  sniffTypes: Object.freeze(['http', 'tls', 'quic']), // "sniffing_types" (string set; "fakedns+others" expands)
  mux: false,                   // "mux_enabled"
  muxConcurrency: 8,            // "mux_concurrency" (clamped 1..128)
  adBlock: false,               // "ad_block"
  directOnly: false,            // no pref: MainActivity.startBlockOnly() passes it for "block_when_off"
  fakeDns: false,               // "fake_dns"
  encryptedDns: false,          // "encrypted_dns"
  customDns: '',                // "custom_dns" (the DNS-lab pick from DnsResolverStore: an IP, or a DoH URL)
  youtubeDirect: false,         // "youtube_direct"
  noiseSpec: '',                // "noise_spec" (off|light|standard|aggressive|quic or "type:packet[:delay]" lines)
  coreLogLevel: 'warning',      // "core_log_level"
  shareOnLan: false,            // "vpn_share_enabled"
  shareUser: '',                // "vpn_share_user"
  sharePass: '',                // "vpn_share_pass"
  shareListenAddress: '127.0.0.1' // not a pref: hotspotInterfaceAddress() on Android; must be a concrete LAN address
})

/** Geo assets the generated rules need, and which rules need which file. */
const REQUIRED_GEO = Object.freeze({
  'geoip.dat': ['geoip:private', 'geoip:ir'],
  // geosite:ads is only in the Chocolate4U Iran-v2ray-rules geosite.dat
  // (what the Android app bundles). The rest are also in the stock v2fly one.
  'geosite.dat': ['geosite:category-ir', 'geosite:youtube', 'geosite:category-ads-all', 'geosite:ads']
})

/** The geo rules that only the Iran-specific (Chocolate4U) geosite.dat provides. */
const IRAN_ONLY_GEO_RULES = Object.freeze(['geosite:ads'])

/**
 * The DNS lab's built-in resolvers (GhajarDnsLab.Catalogue). The app stores
 * the chosen one's `address` verbatim in custom_dns, so a UDP/TCP/DoT entry
 * reaches Xray as a bare IP (plain DNS) and a DoH entry as its URL.
 */
const DNS_CATALOGUE = Object.freeze([
  ['Cloudflare', 'UDP', '1.1.1.1'], ['Cloudflare', 'DOH', 'https://1.1.1.1/dns-query'],
  ['Cloudflare', 'DOT', '1.1.1.1'], ['Cloudflare', 'TCP', '1.1.1.1'],
  ['Google', 'UDP', '8.8.8.8'], ['Google', 'DOH', 'https://8.8.8.8/dns-query'], ['Google', 'DOT', '8.8.8.8'],
  ['Quad9', 'UDP', '9.9.9.9'], ['Quad9', 'DOH', 'https://9.9.9.9/dns-query'],
  ['AdGuard', 'UDP', '94.140.14.14'], ['AdGuard', 'DOH', 'https://dns.adguard-dns.com/dns-query'],
  ['OpenDNS', 'UDP', '208.67.222.222'], ['OpenDNS', 'DOH', 'https://doh.opendns.com/dns-query'],
  ['Mullvad', 'DOH', 'https://dns.mullvad.net/dns-query'],
  ['Quad101', 'UDP', '101.101.101.101'],
  ['DNS.SB', 'DOH', 'https://doh.dns.sb/dns-query'],
  ['CleanBrowsing', 'DOH', 'https://doh.cleanbrowsing.org/doh/family-filter/'],
  ['Shecan', 'UDP', '178.22.122.100', true], ['Shecan', 'DOH', 'https://free.shecan.ir/dns-query', true],
  ['Radar', 'UDP', '10.202.10.10', true], ['Electro', 'UDP', '78.157.42.100', true],
  ['Begzar', 'UDP', '185.55.226.26', true], ['403', 'UDP', '10.202.10.202', true],
  ['Pishgaman', 'UDP', '5.202.100.100', true], ['Asiatech', 'UDP', '194.36.174.161', true]
].map(([name, transport, address, local]) => Object.freeze({ name, transport, address, local: !!local })))

// ------------------------------------------------------------------ constants (ConfigBuilder.kt)

// DoH endpoints blocked when the tunnel's own DNS is on, so browsers fall back
// to the system resolver that Xray answers.
const DOH_HOSTS = [
  'chrome.cloudflare-dns.com', 'mozilla.cloudflare-dns.com', 'cloudflare-dns.com', 'dns.google',
  'dns64.dns.google', 'dns.quad9.net', 'doh.opendns.com', 'dns.nextdns.io', 'doh.cleanbrowsing.org',
  'dns.adguard-dns.com'
]

const AD_HOSTS = [
  'doubleclick.net', 'googlesyndication.com', 'googleadservices.com', 'google-analytics.com',
  'googletagmanager.com', 'googletagservices.com', 'adservice.google.com', 'app-measurement.com',
  'amazon-adsystem.com', 'adnxs.com', 'adsrvr.org', 'bidswitch.net', 'rubiconproject.com', 'pubmatic.com',
  'casalemedia.com', 'openx.net', 'smartadserver.com', 'criteo.com', 'criteo.net', 'taboola.com',
  'outbrain.com', 'sharethrough.com', '33across.com', 'teads.tv', 'media.net', 'adform.net',
  'serving-sys.com', 'flashtalking.com', 'revcontent.com', 'mgid.com', 'zedo.com', 'advertising.com',
  'yieldmo.com', 'gumgum.com', 'indexww.com', 'moatads.com', 'adsafeprotected.com', 'doubleverify.com',
  'scorecardresearch.com', 'quantserve.com', 'demdex.net', 'omtrdc.net', 'everesttech.net', '2o7.net',
  'bluekai.com', 'krxd.net', 'agkn.com', 'exelator.com', 'rlcdn.com', 'crwdcntrl.net', 'mc.yandex.ru',
  'clarity.ms', 'hotjar.com', 'fullstory.com', 'mouseflow.com', 'inspectlet.com', 'optimizely.com',
  'mixpanel.com', 'amplitude.com', 'segment.io', 'segment.com', 'branch.io', 'appsflyer.com', 'adjust.com',
  'kochava.com', 'singular.net', 'tenjin.io', 'flurry.com', 'braze.com', 'clevertap.com', 'leanplum.com',
  'applovin.com', 'ironsrc.com', 'supersonicads.com', 'unityads.unity3d.com', 'vungle.com',
  'chartboost.com', 'adcolony.com', 'inmobi.com', 'mopub.com', 'tapjoy.com', 'startappservice.com',
  'smaato.com', 'pubnative.net', 'mobfox.com', 'analytics.tiktok.com', 'ads.tiktok.com',
  'ads.linkedin.com', 'ads.yahoo.com', '3lift.com', 'adition.com', 'adzerk.net', 'bidr.io',
  'contextweb.com', 'districtm.io', 'emxdgt.com', 'lijit.com', 'mathtag.com', 'sitescout.com',
  'spotxchange.com', 'stickyadstv.com', 'tremorhub.com', 'undertone.com', 'zemanta.com', 'sonobi.com',
  'improvedigital.com', 'adsymptotic.com', 'themoneytizer.com', 'adthrive.com', 'mediavine.com',
  'sovrn.com', 'onetag.io', 'adkernel.com', 'adpushup.com', 'yieldlab.net', 'adtelligent.com',
  'loopme.com', 'smartyads.com', 'adhigh.net', 'adriver.ru', 'adspirit.de', 'rtbhouse.com',
  'onaudience.com', 'id5-sync.com', 'adlooxtracking.com', 'adsco.re', 'bttrack.com',
  'tracking-protection.com', 'trackjs.com', 'krux.net', 'parsely.com', 'chartbeat.com', 'chartbeat.net',
  'dpm.demdex.net', 'sc-static.net', 'adsmoloco.com', 'liftoff.io', 'fyber.com', 'digitalturbine.com',
  'pangle.io', 'pangolin-sdk-toutiao.com', 'mintegral.com', 'adsgreat.com', 'zqtk.net',
  'trafficjunky.com', 'exoclick.com', 'juicyads.com', 'popads.net', 'propellerads.com', 'adsterra.com',
  'hilltopads.net', 'clickadu.com', 'adcash.com', 'trafficstars.com', 'ad.gt', 'adnium.com',
  'adsupply.com', 'bidvertiser.com', 'infolinks.com', 'smartlook.com', 'heap.io', 'heapanalytics.com',
  'pendo.io', 'mouseflow.net', 'matomo.cloud', 'plausible.io', 'statcounter.com', 'histats.com',
  'googleads.g.doubleclick.net', 'pagead2.googlesyndication.com', 'securepubads.g.doubleclick.net',
  'adservice.google.co.uk', 'ad.doubleclick.net', 'stats.g.doubleclick.net', 'cdn.branch.io',
  'api2.branch.io', 'metrics.apple.com', 'ads.mopub.com', 'yektanet.com', 'yn-cdn.com', 'sabavision.com',
  'clickyab.com', 'adivery.com', 'tapsell.ir', 'tapsell.com', 'tapsell.net', 'adad.ir', 'adro.co',
  'mediaad.org', 'anetwork.ir', 'netbina.com', 'adnegah.net', 'zarpop.com', 'metrix.ir', 'adtrace.io',
  'pushe.co', 'najva.com', 'chabok.io', 'webgozar.com', 'webgozar.ir', 'ads.aparat.com',
  'biz.varzesh3.com', 'biz-cdn.varzesh3.com', 'adnxs-simple.com', 'adnexus.net', 'advertserve.com',
  'adtech.de', 'adtechus.com', 'atdmt.com', 'bat.bing.com', 'clicktale.net', 'decibelinsights.com',
  'quantcount.com', 'adroll.com', 'adroll.net', 'rfihub.com', 'turn.com', 'mediamath.com',
  'adsymptotic.net', 'simpli.fi', 'eyeota.net', 'semasio.net', 'weborama.fr', 'liadm.com', 'tapad.com',
  'drawbridge.com', 'nexac.com', 'owneriq.net', 'addthis.com', 'sharethis.com', 'po.st',
  'disqusads.com', 'adform.com', 'adotmob.com', 'adux.com', 'smartclip.net', 'spotx.tv', 'spotxcdn.com',
  'vidoomy.com', 'viralize.com', 'connatix.com', 'primis.tech', 'playwire.com', 'adsninja.ca',
  'ezoic.net', 'ezojs.com', 'adsense.com', 'adperium.com', 'trackingsoft.com', 'trkn.us',
  'tremorvideo.com', 'yieldoptimizer.com', 'onesignal.com', 'pushwoosh.com', 'pushengage.com',
  'izooto.com', 'truepush.com', 'webpushr.com', 'sendpulse.com', 'vwo.com', 'crazyegg.com',
  'luckyorange.com', 'usabilla.com', 'qualtrics.com', 'surveymonkey.com', 'typekit.net',
  'kissmetrics.com', 'woopra.com', 'gosquared.com', 'keen.io', 'countly.com'
]

// Warp.kt: the WARP hostname is replaced by a fixed endpoint, which survives
// DNS tampering of engage.cloudflareclient.com.
const WARP_HOST = 'engage.cloudflareclient.com'
const WARP_ENDPOINT_HOST = '162.159.192.1'
const WARP_ENDPOINT_PORT = 2408

const CHROME_UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36'

/** Protocols buildXrayConfig() turns into an Xray outbound by itself. */
const XRAY_PROTOCOLS = Object.freeze(['vless', 'vmess', 'trojan', 'shadowsocks', 'socks', 'http', 'hysteria2', 'wireguard'])

// ------------------------------------------------------------------ ProxyConfig

/**
 * Fills a config object with ProxyConfig.fromJson()'s defaults, so a field
 * the caller left out behaves as it does on Android (e.g. encryption "none",
 * fingerprint "chrome", network "tcp").
 */
function normalizeConfig(c) {
  const o = c || {}
  const str = (k, d = '') => (o[k] === undefined || o[k] === null ? d : String(o[k]))
  const int = (k, d = 0) => { const n = parseInt(o[k], 10); return Number.isFinite(n) ? n : d }
  const bool = (k) => o[k] === true || o[k] === 'true'
  return {
    id: str('id'),
    name: str('name'),
    protocol: str('protocol'),
    address: str('address'),
    port: int('port'),
    uuid: str('uuid'),
    password: str('password'),
    method: str('method'),
    alterId: int('alterId'),
    encryption: str('encryption', 'none'),
    flow: str('flow'),
    network: str('network', 'tcp'),
    security: str('security', 'none'),
    sni: str('sni'),
    publicKey: str('publicKey'),
    shortId: str('shortId'),
    fingerprint: str('fingerprint', 'chrome'),
    path: str('path'),
    host: str('host'),
    serviceName: str('serviceName'),
    mode: str('mode'),
    alpn: Array.isArray(o.alpn) ? o.alpn.join(',') : str('alpn'),
    headerType: str('headerType'),
    privateKey: str('privateKey'),
    localAddress: Array.isArray(o.localAddress) ? o.localAddress.join(',') : str('localAddress'),
    mtu: int('mtu'),
    reserved: Array.isArray(o.reserved) ? o.reserved.join(',') : str('reserved'),
    hyObfs: str('hyObfs'),
    hyObfsPassword: str('hyObfsPassword'),
    hyUpMbps: int('hyUpMbps'),
    hyDownMbps: int('hyDownMbps'),
    allowInsecure: bool('allowInsecure'),
    pinnedCertSha256: str('pinnedCertSha256'),
    cipherSuites: str('cipherSuites'),
    randomSubdomain: bool('randomSubdomain'),
    maskType: str('maskType'),
    maskDomain: str('maskDomain'),
    maskPassword: str('maskPassword'),
    echConfigList: str('echConfigList'),
    oblivionJson: str('oblivionJson'),
    extra: str('extra')
  }
}

/** True when buildXrayConfig() can run this profile in Xray on its own. */
function supportsXray(config) {
  if (!config || typeof config !== 'object') return false
  const p = String(config.protocol || '').trim()
  if (!XRAY_PROTOCOLS.includes(p)) return false
  // h2 and legacy QUIC (e.g. shadowsocks + v2ray-plugin "quic"): the bundled
  // core removed both and refuses the whole config. ConfigBuilder.kt would
  // still emit them; here such a profile is reported as unsupported.
  if (p !== 'hysteria2' && p !== 'wireguard' && REMOVED_TRANSPORTS[normalizeNetwork(config.network)]) return false
  return true
}

// ------------------------------------------------------------------ NoiseSpec.kt

const NOISE_PRESETS = Object.freeze(['off', 'light', 'standard', 'aggressive', 'quic', 'custom'])
const NOISE_TYPES = new Set(['rand', 'str', 'hex', 'base64'])

function noiseItem(type, packet, delay) {
  const o = { type, packet }
  // delay only when asked for; applyTo always "ip" (NoiseSpec.item).
  if (delay) o.delay = delay
  o.applyTo = 'ip'
  return o
}

/**
 * The freedom outbound's "noises" array for a preset or hand-written spec,
 * or null for none. Null rather than [] so an unset option emits no field.
 */
function buildNoises(spec) {
  const text = String(spec || '').trim()
  if (!text || text === 'off') return null
  switch (text) {
    case 'light': return [noiseItem('rand', '10-30', '0-3')]
    case 'standard': return [noiseItem('rand', '50-100', '5-15'), noiseItem('rand', '50-100', '5-15')]
    case 'aggressive': return [1, 2, 3].map(() => noiseItem('rand', '200-400', '10-25'))
    case 'quic': return [noiseItem('hex', 'c00000000108', '0-5')]
  }
  // One noise per line (or ';'), "type:packet[:delay]"; bad lines are dropped.
  const out = []
  for (const raw of text.split(/[\n;]/)) {
    const line = raw.trim()
    if (!line) continue
    const parts = line.split(':')
    if (parts.length < 2) continue
    const type = parts[0].trim().toLowerCase()
    if (!NOISE_TYPES.has(type)) continue
    const packet = parts[1].trim()
    if (!packet) continue
    out.push(noiseItem(type, packet, (parts[2] || '').trim()))
  }
  return out.length ? out : null
}

/**
 * The udp finalmask "noise" shape: lengths go in `rand`, never `packet` (the
 * core rejects an item with both). Hand-written specs get the standard preset.
 */
function buildMaskNoise(spec) {
  const text = String(spec || '').trim()
  if (!text || text === 'off') return null
  let lengths
  if (text === 'light') lengths = [['10-30', '0-3']]
  else if (text === 'aggressive') lengths = [['200-400', '10-25'], ['200-400', '10-25'], ['200-400', '10-25']]
  else lengths = [['50-100', '5-15'], ['50-100', '5-15']]
  return lengths.map(([rand, delay]) => ({ rand, delay }))
}

// ------------------------------------------------------------------ helpers

function csvArray(s) {
  return String(s || '').split(',').map(x => x.trim()).filter(Boolean)
}

/** CertPin.isValid: one or more comma-separated SHA-256 hex digests (colons allowed). */
function isValidPin(pin) {
  const parts = String(pin || '').split(',').map(x => x.trim().replace(/:/g, '')).filter(Boolean)
  return parts.length > 0 && parts.every(p => /^[0-9a-fA-F]{64}$/.test(p))
}

function normalizeNetwork(n) {
  const v = String(n || '').trim().toLowerCase()
  switch (v) {
    case '': case 'raw': return 'tcp'
    case 'mkcp': return 'kcp'
    case 'websocket': return 'ws'
    case 'h2': case 'http2': return 'http'
    case 'splithttp': return 'xhttp'
    default: return v
  }
}

/** ConfigBuilder.randomLabel: a 5-9 char [a-z0-9] label in front of a wildcard-cert host. */
function randomLabel(host) {
  if (!String(host || '').trim()) return host
  const alphabet = 'abcdefghijklmnopqrstuvwxyz0123456789'
  const length = crypto.randomInt(5, 10)
  let label = ''
  for (let i = 0; i < length; i++) label += alphabet[crypto.randomInt(alphabet.length)]
  return label + '.' + host
}

// The core keeps one finalmask registry per side; a name in the wrong one is
// an unknown type, not a no-op.
const UDP_NETWORKS = new Set(['kcp', 'hysteria'])
const TCP_MASKS = new Set(['sudoku'])

function maskSide(network, type) {
  if (UDP_NETWORKS.has(normalizeNetwork(network))) return 'udp'
  return TCP_MASKS.has(type) ? 'tcp' : null
}

/** One finalmask entry, or null when unset or unusable (a half-filled mask fails the connect). */
function maskEntry(c) {
  const type = String(c.maskType || '').trim().toLowerCase()
  if (!type) return null
  let settings
  switch (type) {
    case 'xdns': {
      const domain = String(c.maskDomain || '').trim()
      if (!domain) return null
      settings = { domain }
      break
    }
    case 'sudoku': case 'salamander':
      if (!String(c.maskPassword || '').trim()) return null
      settings = { password: c.maskPassword }
      break
    case 'noise': {
      const items = buildMaskNoise('standard')
      if (!items) return null
      settings = { noise: items }
      break
    }
    default: return null
  }
  return { type, settings }
}

// ------------------------------------------------------------------ outbounds

function buildStream(c, warn) {
  const net = normalizeNetwork(c.network)
  const stream = { network: net }

  switch (net) {
    case 'tcp':
      if (c.headerType.toLowerCase() === 'http') {
        const headers = {}
        if (c.host) headers.Host = csvArray(c.host)
        headers['User-Agent'] = [CHROME_UA]
        headers['Accept-Encoding'] = ['gzip, deflate']
        headers.Connection = ['keep-alive']
        headers.Pragma = 'no-cache'
        stream.tcpSettings = {
          header: {
            type: 'http',
            request: { version: '1.1', method: 'GET', path: csvArray(c.path || '/'), headers }
          }
        }
      }
      break
    case 'kcp':
      // ConfigBuilder.kt writes kcpSettings.header/seed, which the core it
      // bundles (Xray v26.3.27 / v1.260327.0) refuses outright ("mkcp header &
      // seed has been removed"), so every mKCP profile fails on Android. Here
      // they are emitted in the migrated, wire-compatible form instead: an
      // empty kcpSettings plus udp finalmasks (see kcpMasks).
      stream.kcpSettings = {}
      break
    case 'ws': {
      const ws = { path: c.path || '/' }
      if (c.host) ws.headers = { Host: c.host }
      stream.wsSettings = ws
      break
    }
    case 'httpupgrade': {
      const hu = { path: c.path || '/' }
      if (c.host) hu.host = c.host
      stream.httpupgradeSettings = hu
      break
    }
    case 'xhttp': {
      const xh = { path: c.path || '/' }
      if (c.host) xh.host = c.host
      if (c.mode) xh.mode = c.mode
      stream.xhttpSettings = xh
      break
    }
    case 'grpc':
      stream.grpcSettings = { serviceName: c.serviceName, multiMode: c.mode === 'multi' }
      break
    case 'http': {
      const h = { path: c.path || '/' }
      if (c.host) h.host = csvArray(c.host)
      stream.httpSettings = h
      break
    }
  }

  if (c.security === 'reality') {
    stream.security = 'reality'
    stream.realitySettings = {
      serverName: c.randomSubdomain ? randomLabel(c.sni) : c.sni,
      publicKey: c.publicKey,
      shortId: c.shortId,
      fingerprint: c.fingerprint,
      spiderX: '/'
    }
  } else if (c.security === 'tls') {
    const baseSni = c.sni || c.host.split(',')[0].trim() || c.address
    const t = { serverName: c.randomSubdomain ? randomLabel(baseSni) : baseSni, fingerprint: c.fingerprint }
    // Only when set: an empty cipherSuites field is not the same as an absent one.
    if (c.cipherSuites.trim()) t.cipherSuites = c.cipherSuites.trim()
    if (isValidPin(c.pinnedCertSha256)) t.pinnedPeerCertSha256 = c.pinnedCertSha256
    if (c.alpn) {
      const arr = csvArray(c.alpn)
      if (arr.length) t.alpn = arr
    }
    if (c.echConfigList.trim()) t.echConfigList = c.echConfigList.trim()
    stream.security = 'tls'
    stream.tlsSettings = t
  }
  // allowInsecure is deliberately not emitted: ConfigBuilder.kt never does
  // (the bundled core replaced it with pinnedPeerCertSha256).

  const entry = maskEntry(c)
  if (entry) {
    const side = maskSide(net, entry.type)
    if (side) stream.finalmask = { [side]: [entry] }
    else warn(`mask ${entry.type} needs a udp transport, not ${net} - not applied`)
  }
  if (net === 'kcp') {
    // The user's mask stays first (outermost on the wire), then the legacy
    // header, then the legacy obfuscation, matching the old packet layout.
    const legacy = kcpMasks(c, warn)
    stream.finalmask = { udp: [...((stream.finalmask && stream.finalmask.udp) || []), ...legacy] }
  }
  return stream
}

// Legacy mKCP header types -> the finalmask udp masks that replaced them.
const KCP_HEADER_MASKS = {
  srtp: 'header-srtp', utp: 'header-utp', 'wechat-video': 'header-wechat', wechat: 'header-wechat',
  dtls: 'header-dtls', wireguard: 'header-wireguard', dns: 'header-dns'
}

/**
 * The finalmask equivalent of the legacy kcpSettings {header, seed}: the
 * header as "header-*", and the packet obfuscation as "mkcp-aes128gcm" keyed
 * by the seed (ConfigBuilder keeps the seed in `path`) or, with no seed, the
 * legacy default "mkcp-original". The masks are applied first-to-last from
 * the wire inwards, so the header comes before the obfuscation.
 */
function kcpMasks(c, warn = () => {}) {
  const out = []
  const header = String(c.headerType || '').trim().toLowerCase()
  if (header && header !== 'none') {
    const type = KCP_HEADER_MASKS[header]
    if (!type) warn(`mKCP header ${header} has no finalmask equivalent - not applied`)
    else if (type === 'header-dns' && c.host.trim()) out.push({ type, settings: { domain: c.host.split(',')[0].trim() } })
    else out.push({ type })
  }
  out.push(c.path ? { type: 'mkcp-aes128gcm', settings: { password: c.path } } : { type: 'mkcp-original' })
  return out
}

/**
 * Transports ConfigBuilder.kt can still emit but the bundled core removed
 * (it fails the whole config on them). Neither has a wire-compatible
 * replacement, so such profiles are reported unsupported rather than built.
 */
const REMOVED_TRANSPORTS = Object.freeze({
  http: 'HTTP/2 transport (removed from Xray; servers must migrate to XHTTP)',
  quic: 'QUIC transport (removed from Xray; servers must migrate to XHTTP H3)'
})

function buildHysteria2(c) {
  const t = {
    serverName: c.sni || c.host || c.address,
    alpn: csvArray(c.alpn || 'h3')
  }
  if (isValidPin(c.pinnedCertSha256)) t.pinnedPeerCertSha256 = c.pinnedCertSha256

  const hy = { version: 2, auth: c.password, udpIdleTimeout: 60 }
  if (c.hyUpMbps > 0) hy.up = c.hyUpMbps + 'mbps'
  if (c.hyDownMbps > 0) hy.down = c.hyDownMbps + 'mbps'

  const stream = { network: 'hysteria', security: 'tls', tlsSettings: t, hysteriaSettings: hy }
  // Hysteria's obfuscation is a udp finalmask (a top-level "udpmasks" is
  // silently dropped by the core - the bug ConfigBuilder.kt fixed).
  const udp = []
  if (c.hyObfsPassword.trim()) {
    udp.push({ type: c.hyObfs.trim() ? c.hyObfs : 'salamander', settings: { password: c.hyObfsPassword } })
  }
  const entry = maskEntry(c)
  if (entry) udp.push(entry)
  if (udp.length) stream.finalmask = { udp }

  return {
    tag: 'proxy', protocol: 'hysteria',
    settings: { version: 2, address: c.address, port: c.port },
    streamSettings: stream
  }
}

function buildWireguard(c) {
  const isWarp = c.address.toLowerCase() === WARP_HOST
  const epAddress = isWarp ? WARP_ENDPOINT_HOST : c.address
  const epPort = isWarp ? WARP_ENDPOINT_PORT : c.port
  const settings = {
    secretKey: c.privateKey,
    address: csvArray(c.localAddress),
    peers: [{ publicKey: c.publicKey, endpoint: `${epAddress}:${epPort}`, allowedIPs: ['0.0.0.0/0', '::/0'] }]
  }
  if (c.mtu > 0) settings.mtu = c.mtu
  // WARP client id: exactly three integers, otherwise omitted.
  const reserved = c.reserved.split(',').map(x => x.trim()).filter(x => /^[+-]?\d+$/.test(x)).map(Number)
  if (reserved.length === 3) settings.reserved = reserved
  return { tag: 'proxy', protocol: 'wireguard', settings }
}

/** buildPsiphon()/buildAether(): a bare SOCKS outbound to a local engine, no streamSettings. */
function localSocksOutbound(host, port) {
  return { tag: 'proxy', protocol: 'socks', settings: { servers: [{ address: host, port: Number(port) }] } }
}

/** ConfigBuilder.buildOutbound for the protocols Xray carries itself. */
function buildOutbound(config, warn = () => {}) {
  const c = normalizeConfig(config)
  switch (c.protocol) {
    case 'wireguard': return buildWireguard(c)
    case 'hysteria2': return buildHysteria2(c)
  }
  if (!XRAY_PROTOCOLS.includes(c.protocol)) {
    throw new Error(`protocol "${c.protocol}" is not carried by Xray` +
      (c.protocol === 'psiphon' || c.protocol === 'aether' ? ' (use buildChainToSocks)' : ''))
  }
  const removed = REMOVED_TRANSPORTS[normalizeNetwork(c.network)]
  if (removed) throw new Error(`unsupported transport: ${removed}`)
  let settings
  switch (c.protocol) {
    case 'vless':
    case 'vmess': {
      const user = { id: c.uuid }
      if (c.protocol === 'vless') {
        user.encryption = c.encryption
        if (c.flow) user.flow = c.flow
      } else {
        user.alterId = c.alterId
        user.security = c.encryption || 'auto'
      }
      settings = { vnext: [{ address: c.address, port: c.port, users: [user] }] }
      break
    }
    case 'trojan': {
      const server = { address: c.address, port: c.port, password: c.password }
      if (c.flow) server.flow = c.flow
      settings = { servers: [server] }
      break
    }
    case 'shadowsocks':
      settings = { servers: [{ address: c.address, port: c.port, method: c.method, password: c.password }] }
      break
    case 'http':
    case 'socks': {
      const server = { address: c.address, port: c.port }
      // The app keeps the username in uuid.
      if (c.uuid || c.password) server.users = [{ user: c.uuid, pass: c.password }]
      settings = { servers: [server] }
      break
    }
  }
  return { tag: 'proxy', protocol: c.protocol, settings, streamSettings: buildStream(c, warn) }
}

// ------------------------------------------------------------------ full config

function expandSniffTypes(types) {
  const out = new Set()
  for (const t of types || []) {
    if (t === 'fakedns+others') ['fakedns', 'http', 'tls', 'quic'].forEach(x => out.add(x))
    else out.add(t)
  }
  if (!out.size) { out.add('http'); out.add('tls') }
  return [...out]
}

/** `chainBase.id != config.id` in ConfigBuilder: a profile never chains through itself. */
function isDistinctChain(config, chainBase) {
  if (!chainBase || chainBase === config) return false
  const a = String((config && config.id) || '')
  const b = String(chainBase.id || '')
  return !(a && b && a === b)
}

function isWildcard(addr) {
  const a = String(addr || '').trim()
  return !a || a === '0.0.0.0' || a === '::' || a === '[::]'
}

/**
 * The shared body of ConfigBuilder.build(): everything around the proxy
 * outbound. `proxyOut` is the already-built "proxy" outbound; `extraOutbounds`
 * are appended after the fixed ones (the chain base).
 */
function assemble(proxyOut, settings, ports, { chainOut = null } = {}) {
  const s = Object.assign({}, DEFAULT_SETTINGS, settings || {})
  const p = ports || {}
  if (!(p.socksPort > 0) || !(p.httpPort > 0)) throw new Error('socksPort and httpPort are required')

  const fake = !!s.fakeDns // onion routing (which also forces fakedns) is not ported
  const chosenDns = String(s.customDns || '').trim()
  const dnsOn = fake || !!s.encryptedDns || !!chosenDns
  const splitRouting = !!s.splitRouting
  const sniffing = !!s.sniffing
  const adBlock = !!s.adBlock

  const root = { log: { loglevel: String(p.logLevel || s.coreLogLevel || '').trim() || 'warning' } }

  if (fake) root.fakedns = [{ ipPool: '198.18.0.0/15', poolSize: 65535 }]
  if (dnsOn) {
    const servers = []
    if (fake) servers.push('fakedns')
    // The chosen resolver first, the built-ins behind it as a fallback.
    if (chosenDns) servers.push(chosenDns)
    if (s.encryptedDns) servers.push('https://1.1.1.1/dns-query', 'https://8.8.8.8/dns-query')
    servers.push('1.1.1.1', '8.8.8.8')
    root.dns = { servers, queryStrategy: 'UseIPv4' }
  }

  root.stats = {}
  root.policy = { system: { statsOutboundUplink: true, statsOutboundDownlink: true } }
  if (p.apiPort > 0) root.api = { tag: 'api', services: ['StatsService'] }

  // "socks" takes the place of Android's tun-in: the sing-box TUN feeds the
  // device's traffic into it, so it gets tun-in's sniffing (including the
  // routeOnly choice and fakedns).
  const shareAuthed = !!s.shareOnLan && !!String(s.shareUser || '').trim() &&
    !!String(s.sharePass || '').trim() && !isWildcard(s.shareListenAddress)
  const socksSettings = { auth: 'noauth', udp: true }
  if (shareAuthed) {
    // Never an open proxy on the LAN: without both credentials it stays on loopback.
    socksSettings.auth = 'password'
    socksSettings.accounts = [{ user: s.shareUser, pass: s.sharePass }]
  }
  const socksIn = {
    tag: 'socks', listen: shareAuthed ? s.shareListenAddress : '127.0.0.1', port: p.socksPort,
    protocol: 'socks', settings: socksSettings
  }
  if (splitRouting || sniffing || adBlock || fake) {
    const types = sniffing ? expandSniffTypes(s.sniffTypes) : ['http', 'tls', 'quic']
    const destOverride = [...types]
    if (fake && !types.includes('fakedns')) destOverride.push('fakedns')
    if (adBlock) ['http', 'tls', 'quic'].forEach(t => { if (!types.includes(t)) destOverride.push(t) })
    socksIn.sniffing = { enabled: true, destOverride, routeOnly: !adBlock && splitRouting && !sniffing }
  }

  // "http" takes socks-in's role (applications pointed at a proxy). Android's
  // http-share-in listens on the hotspot address while sharing is on.
  const httpListen = s.shareOnLan && !isWildcard(s.shareListenAddress) ? s.shareListenAddress : '127.0.0.1'
  const httpIn = { tag: 'http', listen: httpListen, port: p.httpPort, protocol: 'http', settings: {} }
  if (splitRouting || sniffing || adBlock) {
    httpIn.sniffing = { enabled: true, destOverride: ['http', 'tls', 'quic'], routeOnly: false }
  }

  root.inbounds = [socksIn, httpIn]
  if (p.apiPort > 0) {
    root.inbounds.push({
      tag: 'api', listen: '127.0.0.1', port: p.apiPort, protocol: 'dokodemo-door',
      settings: { address: '127.0.0.1' }
    })
  }

  if (chainOut) {
    // Chained profile: the proxy dials through the chain base, and fragment is
    // not applied to it (the base carries the first hop).
    proxyOut.streamSettings = proxyOut.streamSettings || {}
    proxyOut.streamSettings.sockopt = { dialerProxy: 'chain' }
  } else if (s.fragment && proxyOut.streamSettings) {
    // Only an outbound with streamSettings: the local-SOCKS outbounds for
    // Psiphon/Aether (and hence buildChainToSocks) have none, as on Android.
    proxyOut.streamSettings.sockopt = { dialerProxy: 'fragment' }
  }
  if (s.mux) {
    const n = Math.min(128, Math.max(1, parseInt(s.muxConcurrency, 10) || 8))
    proxyOut.mux = { enabled: true, concurrency: n }
  }

  const outbounds = []
  if (s.directOnly) outbounds.push({ tag: 'proxy', protocol: 'freedom', settings: { domainStrategy: 'UseIP' } })
  else outbounds.push(proxyOut)
  if (s.fragment) {
    outbounds.push({
      tag: 'fragment', protocol: 'freedom',
      settings: {
        fragment: {
          packets: String(s.fragmentPackets || '').trim() ? s.fragmentPackets : 'tlshello',
          length: String(s.fragmentLength || '').trim() ? s.fragmentLength : '10-20',
          interval: String(s.fragmentInterval || '').trim() ? s.fragmentInterval : '10-20'
        }
      }
    })
  }
  // The noises ride on the direct outbound: they go out on the wire to the
  // real destination, ahead of the real traffic.
  const direct = { tag: 'direct', protocol: 'freedom' }
  const noises = buildNoises(s.noiseSpec)
  if (noises) direct.settings = { noises }
  outbounds.push(direct)
  outbounds.push({ tag: 'block', protocol: 'blackhole' })
  if (chainOut) outbounds.push(Object.assign(chainOut, { tag: 'chain' }))
  if (dnsOn) outbounds.push({ tag: 'dns-out', protocol: 'dns' })
  root.outbounds = outbounds

  const rules = []
  if (p.apiPort > 0) rules.push({ type: 'field', inboundTag: ['api'], outboundTag: 'api' })
  if (dnsOn) {
    rules.push({ type: 'field', port: 53, outboundTag: 'dns-out' })
    rules.push({ type: 'field', domain: DOH_HOSTS.map(h => 'domain:' + h), outboundTag: 'block' })
  }
  if (adBlock) {
    rules.push({
      type: 'field',
      domain: ['geosite:category-ads-all', 'geosite:ads', ...AD_HOSTS.map(h => 'domain:' + h)],
      outboundTag: 'block'
    })
    // QUIC blocked so sniffed TLS hosts can be matched.
    rules.push({ type: 'field', network: 'udp', port: '443', outboundTag: 'block' })
  }
  // YouTube Direct: above split routing and the catch-all so it decides; the
  // page and the video come from different hosts, so both lists are needed.
  if (s.youtubeDirect) {
    rules.push({
      type: 'field',
      domain: ['geosite:youtube', 'domain:youtube.com', 'domain:youtu.be', 'domain:ytimg.com',
        'domain:googlevideo.com', 'domain:youtube-nocookie.com', 'domain:yt3.ggpht.com'],
      outboundTag: 'direct'
    })
  }
  if (splitRouting) {
    rules.push({ type: 'field', ip: ['geoip:private', 'geoip:ir'], outboundTag: 'direct' })
    rules.push({ type: 'field', domain: ['geosite:category-ir'], outboundTag: 'direct' })
  }
  rules.push({ type: 'field', inboundTag: ['socks', 'http'], outboundTag: 'proxy' })
  root.routing = { domainStrategy: 'AsIs', rules }
  return root
}

/**
 * The full Xray configuration for one profile, as ConfigBuilder.build() makes
 * it, with the desktop's local SOCKS ("socks") and HTTP ("http") inbounds.
 *
 * @param {object} config   ProxyConfig fields (protocol, address, port, ...)
 * @param {object} settings user settings; missing keys take DEFAULT_SETTINGS
 * @param {object} ports    { socksPort, httpPort, logLevel?, apiPort?, chainBase?, onWarning? }
 *                          chainBase: a second ProxyConfig the proxy dials
 *                          through (the app's chainId); apiPort adds Xray's
 *                          StatsService API (counters outbound>>>proxy>>>traffic>>>uplink/downlink).
 * @returns {object} the Xray JSON config
 */
function buildXrayConfig(config, settings, ports) {
  const p = ports || {}
  const s = Object.assign({}, DEFAULT_SETTINGS, settings || {})
  const warn = typeof p.onWarning === 'function' ? p.onWarning : () => {}
  const cfg = normalizeConfig(config)
  // directOnly never uses the proxy outbound, so any profile will do there.
  const proxyOut = s.directOnly && !supportsXray(cfg)
    ? { tag: 'proxy', protocol: 'freedom', settings: {} }
    : buildOutbound(cfg, warn)
  const chainOut = p.chainBase && isDistinctChain(config, p.chainBase) ? buildOutbound(p.chainBase, warn) : null
  return assemble(proxyOut, s, p, { chainOut })
}

/**
 * The full configuration with a local SOCKS engine (Psiphon, Aether/warp
 * plus) as the upstream, as buildPsiphon()/buildAether() make it: the same
 * inbounds, DNS and routing, and a bare SOCKS outbound to socksHost:socksPort.
 */
function buildChainToSocks(socksHost, socksPort, settings, ports) {
  const port = parseInt(socksPort, 10)
  if (!(port > 0 && port < 65536)) throw new Error('invalid upstream SOCKS port')
  return assemble(localSocksOutbound(String(socksHost || '127.0.0.1'), port), settings, ports)
}

// ------------------------------------------------------------------ delay test

/**
 * Every profile in ONE Xray instance, each behind its own SOCKS inbound on
 * 127.0.0.1 (inbound "in-<i>" routed to outbound "out-<i>"). The outbounds
 * are built like ConfigBuilder.buildForTest(): the bare proxy outbound,
 * without fragment, mux or routing settings, log "none".
 *
 * configs[i] is a ProxyConfig, or { config, chainBase } for a chained profile.
 * Profiles Xray cannot carry (or whose outbound fails to build) are skipped;
 * `ports` lists only the ones included, by their index in `configs`.
 */
function buildDelayTestConfig(configs, basePort) {
  const base = parseInt(basePort, 10)
  if (!(base > 0 && base < 65536)) throw new Error('invalid basePort')
  const inbounds = []
  // Xray's default is the first outbound: a blackhole, so a stray connection
  // is dropped instead of leaving through an arbitrary profile.
  const outbounds = [{ tag: 'block', protocol: 'blackhole' }]
  const rules = []
  const ports = []
  ;(configs || []).forEach((item, index) => {
    const wrapped = item && typeof item === 'object' && item.config && typeof item.config === 'object'
    const cfg = wrapped ? item.config : item
    const chain = wrapped ? item.chainBase : null
    if (!supportsXray(cfg)) return
    let out
    let chainOut = null
    try {
      out = buildOutbound(cfg)
      if (chain && supportsXray(chain) && isDistinctChain(cfg, chain)) chainOut = buildOutbound(chain)
    } catch { return }
    const port = base + ports.length
    if (port > 65535) return
    const inTag = `in-${index}`
    const outTag = `out-${index}`
    out.tag = outTag
    if (chainOut) {
      chainOut.tag = `chain-${index}`
      out.streamSettings = out.streamSettings || {}
      out.streamSettings.sockopt = { dialerProxy: chainOut.tag }
    }
    inbounds.push({ tag: inTag, listen: '127.0.0.1', port, protocol: 'socks', settings: { auth: 'noauth', udp: false } })
    outbounds.push(out)
    if (chainOut) outbounds.push(chainOut)
    rules.push({ type: 'field', inboundTag: [inTag], outboundTag: outTag })
    ports.push({ index, port })
  })
  const config = { log: { loglevel: 'none' }, inbounds, outbounds, routing: { domainStrategy: 'AsIs', rules } }
  return { config, ports }
}

const PROBE_URL = 'https://www.gstatic.com/generate_204'

function socksConnect(socksHost, socksPort, host, port, signal) {
  return new Promise((resolve, reject) => {
    const sock = net.connect({ host: socksHost, port: socksPort })
    let stage = 0
    let buf = Buffer.alloc(0)
    const fail = (e) => { sock.destroy(); reject(e) }
    const onAbort = () => fail(new Error('timeout'))
    if (signal) {
      if (signal.aborted) return onAbort()
      signal.addEventListener('abort', onAbort, { once: true })
    }
    sock.once('error', fail)
    sock.once('connect', () => sock.write(Buffer.from([5, 1, 0])))
    sock.on('data', function onData(chunk) {
      buf = Buffer.concat([buf, chunk])
      if (stage === 0) {
        if (buf.length < 2) return
        if (buf[0] !== 5 || buf[1] !== 0) return fail(new Error('socks: no acceptable auth'))
        buf = buf.subarray(2)
        stage = 1
        const h = Buffer.from(host)
        const req = Buffer.concat([Buffer.from([5, 1, 0, 3, h.length]), h, Buffer.from([port >> 8, port & 255])])
        sock.write(req)
      }
      if (stage === 1) {
        if (buf.length < 5) return
        if (buf[1] !== 0) return fail(new Error('socks: connect failed ' + buf[1]))
        const atyp = buf[3]
        const need = atyp === 1 ? 10 : atyp === 4 ? 22 : 7 + buf[4]
        if (buf.length < need) return
        sock.removeListener('data', onData)
        sock.removeListener('error', fail)
        if (signal) signal.removeEventListener('abort', onAbort)
        const rest = buf.subarray(need)
        if (rest.length) sock.unshift(rest)
        resolve(sock)
      }
    })
  })
}

/**
 * Reads one HTTP/1.1 response from `stream`. Resolves { status, reusable }
 * once the headers and the body are consumed.
 */
function readResponse(stream) {
  return new Promise((resolve, reject) => {
    let buf = Buffer.alloc(0)
    let head = null
    let remaining = -1
    let chunked = false
    let untilClose = false
    const cleanup = () => {
      stream.removeListener('data', onData)
      stream.removeListener('end', onEnd)
      stream.removeListener('error', onErr)
      stream.removeListener('close', onEnd)
    }
    const done = (v) => { cleanup(); resolve(v) }
    const onErr = (e) => { cleanup(); reject(e) }
    const onEnd = () => {
      if (head && untilClose) return done({ status: head.status, reusable: false })
      cleanup(); reject(new Error('connection closed'))
    }
    const consumeBody = () => {
      if (untilClose) return
      if (!chunked) {
        if (buf.length >= remaining) done({ status: head.status, reusable: head.keepAlive })
        return
      }
      // Chunked: drop complete chunks; the last one is "0\r\n" + trailers + "\r\n".
      for (;;) {
        const nl = buf.indexOf('\r\n')
        if (nl < 0) return
        const size = parseInt(buf.subarray(0, nl).toString('latin1').split(';')[0], 16)
        if (!Number.isFinite(size)) return onErr(new Error('bad chunk'))
        if (size === 0) {
          const rest = buf.subarray(nl + 2)
          if (rest.subarray(0, 2).toString('latin1') === '\r\n' || rest.indexOf('\r\n\r\n') >= 0) {
            done({ status: head.status, reusable: head.keepAlive })
          }
          return
        }
        if (buf.length < nl + 2 + size + 2) return
        buf = buf.subarray(nl + 2 + size + 2)
      }
    }
    function onData(chunk) {
      buf = Buffer.concat([buf, chunk])
      if (!head) {
        const end = buf.indexOf('\r\n\r\n')
        if (end < 0) { if (buf.length > 65536) onErr(new Error('header too large')); return }
        const lines = buf.subarray(0, end).toString('latin1').split('\r\n')
        const m = /^HTTP\/1\.[01] (\d{3})/.exec(lines[0])
        if (!m) return onErr(new Error('bad status line'))
        const headers = {}
        for (const l of lines.slice(1)) {
          const i = l.indexOf(':')
          if (i > 0) headers[l.slice(0, i).trim().toLowerCase()] = l.slice(i + 1).trim()
        }
        const status = Number(m[1])
        head = { status, keepAlive: !/close/i.test(headers.connection || '') }
        buf = buf.subarray(end + 4)
        if (status === 204 || status === 304 || (status >= 100 && status < 200)) return done({ status, reusable: head.keepAlive })
        if (/chunked/i.test(headers['transfer-encoding'] || '')) chunked = true
        else if (headers['content-length'] !== undefined) remaining = parseInt(headers['content-length'], 10) || 0
        else { untilClose = true; head.keepAlive = false }
      }
      consumeBody()
    }
    stream.on('data', onData)
    stream.once('end', onEnd)
    stream.once('close', onEnd)
    stream.once('error', onErr)
  })
}

async function openThroughSocks(socksPort, u, signal, socksHost) {
  const port = Number(u.port) || (u.protocol === 'https:' ? 443 : 80)
  const raw = await socksConnect(socksHost, socksPort, u.hostname, port, signal)
  if (u.protocol !== 'https:') return raw
  return new Promise((resolve, reject) => {
    const s = tls.connect({ socket: raw, servername: net.isIP(u.hostname) ? undefined : u.hostname, ALPNProtocols: ['http/1.1'] })
    const onAbort = () => { s.destroy(); reject(new Error('timeout')) }
    if (signal) signal.addEventListener('abort', onAbort, { once: true })
    s.once('secureConnect', () => { if (signal) signal.removeEventListener('abort', onAbort); resolve(s) })
    s.once('error', (e) => { if (signal) signal.removeEventListener('abort', onAbort); reject(e) })
  })
}

function sendGet(stream, u) {
  stream.write(`GET ${u.pathname}${u.search} HTTP/1.1\r\nHost: ${u.host}\r\nUser-Agent: Go-http-client/1.1\r\n` +
    'Accept-Encoding: identity\r\nConnection: keep-alive\r\n\r\n')
}

/**
 * Real delay through a local SOCKS5 port, in milliseconds, or -1.
 *
 * Default mode mirrors gozarcore's MeasureDelayBounded (EngineTester.realDelay
 * with a bound): one GET on a fresh connection, timed from before the dial so
 * the tunnel and TLS handshakes are included, and only a 204 counts. The
 * bound is clamped like the Go code (outside 500..10000 ms -> 4000).
 *
 * `{ warm: true }` mirrors the unbounded MeasureDelay instead: a warm-up GET,
 * then a second GET timed on the kept-alive connection (any status), 10 s cap.
 */
async function measureDelay(socksPort, opts = {}) {
  const u = new URL(opts.url || PROBE_URL)
  const socksHost = opts.socksHost || '127.0.0.1'
  const warm = !!opts.warm
  let timeout = Number(opts.timeoutMs) || 0
  if (warm) timeout = 10000
  else if (timeout < 500 || timeout > 10000) timeout = 4000
  const ac = new AbortController()
  const timer = setTimeout(() => ac.abort(), timeout)
  const streams = []
  try {
    if (!warm) {
      const start = process.hrtime.bigint()
      const s = await openThroughSocks(socksPort, u, ac.signal, socksHost)
      streams.push(s)
      sendGet(s, u)
      const r = await abortable(readResponse(s), ac.signal, s)
      if (r.status !== 204) return -1
      return Number((process.hrtime.bigint() - start) / 1000000n)
    }
    let s = await openThroughSocks(socksPort, u, ac.signal, socksHost)
    streams.push(s)
    sendGet(s, u)
    const first = await abortable(readResponse(s), ac.signal, s)
    const start = process.hrtime.bigint()
    if (!first.reusable) {
      s = await openThroughSocks(socksPort, u, ac.signal, socksHost)
      streams.push(s)
    }
    sendGet(s, u)
    await abortable(readResponse(s), ac.signal, s)
    return Number((process.hrtime.bigint() - start) / 1000000n)
  } catch {
    return -1
  } finally {
    clearTimeout(timer)
    for (const s of streams) s.destroy()
  }
}

function abortable(promise, signal, stream) {
  if (!signal) return promise
  return new Promise((resolve, reject) => {
    if (signal.aborted) { stream.destroy(); return reject(new Error('timeout')) }
    const onAbort = () => { stream.destroy(); reject(new Error('timeout')) }
    signal.addEventListener('abort', onAbort, { once: true })
    promise.then(v => { signal.removeEventListener('abort', onAbort); resolve(v) },
      e => { signal.removeEventListener('abort', onAbort); reject(e) })
  })
}

module.exports = {
  DEFAULT_SETTINGS,
  REQUIRED_GEO,
  IRAN_ONLY_GEO_RULES,
  DNS_CATALOGUE,
  NOISE_PRESETS,
  XRAY_PROTOCOLS,
  PROBE_URL,
  buildXrayConfig,
  buildChainToSocks,
  buildDelayTestConfig,
  measureDelay,
  supportsXray,
  buildOutbound,
  buildNoises,
  buildMaskNoise,
  // exposed for tests
  _internal: { kcpMasks, normalizeConfig, normalizeNetwork, maskEntry, maskSide, randomLabel, isValidPin, expandSniffTypes, csvArray }
}
