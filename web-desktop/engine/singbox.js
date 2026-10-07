// sing-box for the desktop app.
//
// Three jobs:
//
//  1. A port of the Android builder (app/.../engine/SingBoxConfig.kt): one
//     ProxyConfig (as ProxyConfig.toJson() field names) to the sing-box
//     outbound or endpoint the app would build for it, for every protocol the
//     app routes to sing-box rather than Xray.
//  2. A port of the sidecar launcher (engine/SidecarRunner.kt, object
//     Sidecars): the helper process some of those protocols need in front of
//     sing-box (ghajar-helper, juicity, the DNS tunnel clients), with the exact
//     command lines the app uses. On Android these are lib<name>.so in
//     nativeLibraryDir; on desktop they are plain executables (".exe" on
//     Windows), see executableName().
//  3. Whole sing-box configurations for the desktop: a full-device TUN
//     (buildTunConfig) and a local proxy for proxy / system-proxy mode
//     (buildProxyOnlyConfig).
//
// Schema: sing-box 1.12+ (rule actions, "address" list in tun, typed DNS
// servers), checked against the pinned source the Android app builds -
// SagerNet/sing-box 132b38e (v1.15.0-alpha.9), scripts/build-singbox.sh.
//
// No dependencies beyond Node's standard library.
'use strict'

const crypto = require('crypto')
const path = require('path')

// ------------------------------------------------------------------ constants

/** Protocols the app sends to sing-box (SingBoxConfig.PROTOCOLS). ShadowsocksR is not here: the pinned source registers it only as a removed stub. */
const PROTOCOLS = new Set(['tuic', 'hysteria', 'anytls', 'ssh', 'snell', 'openconnect', 'masque',
  'dnstt', 'vaydns', 'noizdns', 'masterdns', 'stormdns', 'cottendns', 'slipstream',
  'amneziawg', 'mieru', 'brook', 'juicity', 'naive', 'shadowtls', 'sstp', 'softether', 'tailscale', 'tailcat'])

/** DNS tunnels whose server forwards to a SOCKS5 or SSH upstream. */
const DNSTT_FAMILY = new Set(['dnstt', 'vaydns', 'noizdns', 'slipstream'])

/** DNS tunnels whose client serves SOCKS5 itself. */
const MASTERDNS_FAMILY = new Set(['masterdns', 'stormdns', 'cottendns'])

/** Protocols carried as sing-box endpoints rather than outbounds (by profile protocol). */
const ENDPOINT_PROTOCOLS = new Set(['openconnect', 'masque', 'tailscale'])

/** The same, by sing-box type: how a bare object is sorted into "endpoints". */
const ENDPOINT_TYPES = new Set(['openconnect', 'masque-client', 'tailscale', 'wireguard'])

/** SSH transport modes the helper implements; "direct" needs no helper at all (Sidecars.SSH_MODES). */
const SSH_MODES = new Set(['payload', 'http-proxy', 'https-proxy', 'tls', 'payload-tls', 'ws', 'wss'])

/** Placeholders in sidecar arguments, env and files (SidecarLaunch.PORT / PORT2 / DIR). */
const PORT = '{port}'
const PORT2 = '{port2}'
const DIR = '{dir}'

/** The in-repo helper (native/ghajar-helper, GPL-3.0); libghajarhelper.so on Android. */
const HELPER = 'ghajar-helper'

/**
 * Executables that must never be captured by the TUN: the cores and helpers
 * this app runs. Their own connections to the servers would otherwise loop
 * back into the tunnel they are carrying (on Android the app excludes its own
 * UID from the VpnService for the same reason). Base names; ".exe" is added on
 * Windows.
 */
const CORE_PROCESSES = ['xray', 'sing-box', HELPER, 'juicity',
  'dnstt', 'vaydns', 'noizdns', 'slipstream', 'masterdns', 'stormdns', 'cottendns',
  'psiphon', 'psiphon-tunnel-core', 'tor', 'lyrebird', 'obfs4proxy', 'snowflake-client', 'aether']

/** TUN addresses (the sing-box documented defaults, also the ones the task fixes). */
const TUN_INET4 = '172.19.0.1/30'
const TUN_INET6 = 'fdfe:dcba:9876::1/126'
/** The tun's own DNS address: the next address after each tun address (protocol/tun: Inet4DNSAddress). */
const TUN_DNS = ['172.19.0.2', 'fdfe:dcba:9876::2']
const TUN_MTU = 9000

// ------------------------------------------------------------------ ProxyConfig access

/**
 * Normalizes a ProxyConfig.toJson() object with the same defaults as
 * ProxyConfig.fromJson(), so missing fields read as the app would read them.
 */
function profile(raw) {
  const o = raw && typeof raw === 'object' ? raw : {}
  const str = (k, d = '') => (o[k] === undefined || o[k] === null ? d : String(o[k]))
  const int = (k, d = 0) => optIntValue(o[k], d)
  const bool = (k, d = false) => optBoolValue(o[k], d)
  return {
    name: str('name'), protocol: str('protocol'), address: str('address'), port: int('port'),
    uuid: str('uuid'), password: str('password'), method: str('method'), alterId: int('alterId'),
    encryption: str('encryption', 'none'), flow: str('flow'), network: str('network', 'tcp'),
    security: str('security', 'none'), sni: str('sni'), publicKey: str('publicKey'),
    shortId: str('shortId'), fingerprint: str('fingerprint', 'chrome'), path: str('path'),
    host: str('host'), serviceName: str('serviceName'), mode: str('mode'), alpn: str('alpn'),
    headerType: str('headerType'), privateKey: str('privateKey'), mtu: int('mtu'),
    hyObfs: str('hyObfs'), hyObfsPassword: str('hyObfsPassword'),
    hyUpMbps: int('hyUpMbps'), hyDownMbps: int('hyDownMbps'),
    allowInsecure: bool('allowInsecure'), pinnedCertSha256: str('pinnedCertSha256'),
    extra: o.extra
  }
}

/** ProxyConfig.extraJson(): [extra] as an object; empty when blank or unreadable. Also accepts an already-parsed object. */
function extraJson(c) {
  const x = c.extra
  if (x && typeof x === 'object' && !Array.isArray(x)) return x
  if (typeof x !== 'string' || !x.trim()) return {}
  try {
    const v = JSON.parse(x)
    return v && typeof v === 'object' && !Array.isArray(v) ? v : {}
  } catch { return {} }
}

// org.json opt* semantics: missing -> default, numbers and numeric strings coerce.
function optIntValue(v, d) {
  if (typeof v === 'number' && Number.isFinite(v)) return Math.trunc(v)
  if (typeof v === 'string' && /^\s*-?\d+(\.\d+)?\s*$/.test(v)) return Math.trunc(Number(v))
  return d
}
function optBoolValue(v, d) {
  if (typeof v === 'boolean') return v
  if (typeof v === 'string') { if (v.toLowerCase() === 'true') return true; if (v.toLowerCase() === 'false') return false }
  return d
}
const optString = (o, k, d = '') => (o && o[k] !== undefined && o[k] !== null ? String(o[k]) : d)
const optInt = (o, k, d = 0) => optIntValue(o ? o[k] : undefined, d)
const optBool = (o, k, d = false) => optBoolValue(o ? o[k] : undefined, d)
const has = (o, k) => !!o && Object.prototype.hasOwnProperty.call(o, k)

/** Kotlin String.toIntOrNull(). */
function toIntOrNull(s) {
  return /^[+-]?\d+$/.test(String(s)) ? parseInt(s, 10) : null
}

const blank = (s) => s === undefined || s === null || String(s).trim() === ''
const ifBlank = (s, d) => (blank(s) ? d : s)
const inRange = (n, lo, hi) => n >= lo && n <= hi
const coerceIn = (n, lo, hi) => Math.min(hi, Math.max(lo, n))
const splitList = (s, seps) => String(s || '').split(seps).map((it) => it.trim()).filter(Boolean)

/** JSONObject.putIf: skips null and blank strings. */
function putIf(o, key, value) {
  if (value === undefined || value === null) return o
  if (typeof value === 'string' && value.trim() === '') return o
  o[key] = value
  return o
}

/** host:port with IPv6 in brackets, as the Kotlin builders join them. */
function hostPortString(host, port) {
  return (host.includes(':') ? `[${host}]` : host) + ':' + port
}

// ------------------------------------------------------------------ outbounds (SingBoxConfig.kt)

/** Whether the app routes this profile to sing-box (SingBoxConfig.handles). */
function handles(config) {
  return PROTOCOLS.has(profile(config).protocol)
}

function tlsOf(c, forceOn) {
  const t = { enabled: forceOn || c.security === 'tls' }
  putIf(t, 'server_name', ifBlank(c.sni, c.host))
  if (c.allowInsecure) t.insecure = true
  const alpn = splitList(c.alpn, ',')
  if (alpn.length) t.alpn = alpn
  if (c.protocol === 'anytls' && !blank(c.fingerprint)) t.utls = { enabled: true, fingerprint: c.fingerprint }
  return t
}

/** sing-box wants certificate hashes in base64; profiles may carry hex (SingBoxConfig.pinBase64). */
function pinBase64(pin) {
  const p = String(pin || '').trim().replace(/:/g, '')
  if (!p) return null
  if (/^[0-9a-fA-F]{64}$/.test(p)) return Buffer.from(p, 'hex').toString('base64')
  return String(pin).trim()
}

/** A stable directory name per Tailscale profile, so two tailnets never share a node key (SingBoxConfig.stateId). */
function stateId(config) {
  const c = profile(config)
  return crypto.createHash('sha256').update(c.host + '|' + c.sni + '|' + c.name, 'utf8').digest().subarray(0, 6).toString('hex')
}

/** The SSH disguise, or null for plain SSH (SingBoxConfig.sshTransport). */
function sshTransportOf(c) {
  const t = extraJson(c).transport
  return t && typeof t === 'object' && SSH_MODES.has(optString(t, 'mode')) ? t : null
}

/**
 * The proxy outbound (or endpoint) for one profile, tagged "proxy" - a port of
 * SingBoxConfig.proxy(). Profiles carried by a sidecar point at
 * 127.0.0.1:0 here; sidecarPlan() returns the same object with the sidecar's
 * port filled in. ShadowTLS needs a second outbound: see buildSingboxSpec().
 */
function buildSingboxOutbound(config) {
  const c = profile(config)
  const o = { tag: 'proxy' }
  const server = (obj) => { obj.server = c.address; obj.server_port = c.port; return obj }
  switch (c.protocol) {
    case 'tuic':
      o.type = 'tuic'; server(o)
      o.uuid = c.uuid
      putIf(o, 'password', c.password)
      putIf(o, 'congestion_control', ['cubic', 'new_reno', 'bbr'].includes(c.method) ? c.method : null)
      putIf(o, 'udp_relay_mode', ['native', 'quic'].includes(c.mode) ? c.mode : null)
      o.tls = tlsOf(c, true)
      break
    case 'hysteria':
      o.type = 'hysteria'; server(o)
      putIf(o, 'auth_str', c.password)
      o.up_mbps = c.hyUpMbps > 0 ? c.hyUpMbps : 10
      o.down_mbps = c.hyDownMbps > 0 ? c.hyDownMbps : 50
      putIf(o, 'obfs', ifBlank(c.hyObfsPassword, c.hyObfs !== 'xplus' ? c.hyObfs : ''))
      o.tls = tlsOf(c, true)
      break
    case 'anytls':
      o.type = 'anytls'; server(o)
      o.password = c.password
      o.tls = tlsOf(c, true)
      break
    case 'naive':
      // Needs sing-box built with with_naive_outbound (Cronet); see scripts/build-singbox.sh.
      o.type = 'naive'; server(o)
      putIf(o, 'username', c.uuid)
      putIf(o, 'password', c.password)
      o.tls = { enabled: true, server_name: ifBlank(c.sni, c.address) }
      if (c.mode === 'quic') o.quic = true
      break
    case 'shadowtls': {
      // The Shadowsocks stream rides inside ShadowTLS: the proxy outbound is
      // Shadowsocks with the ShadowTLS outbound as its detour.
      const ss = extraJson(c).ss && typeof extraJson(c).ss === 'object' ? extraJson(c).ss : {}
      o.type = 'shadowsocks'
      o.method = optString(ss, 'method')
      o.password = optString(ss, 'password')
      o.detour = 'shadowtls-out'
      break
    }
    case 'amneziawg': case 'mieru': case 'brook': case 'juicity': case 'sstp': case 'softether':
    case 'masterdns': case 'stormdns': case 'cottendns':
      // The sidecar serves SOCKS5; sing-box only relays to it.
      Object.assign(o, { type: 'socks', server: '127.0.0.1', server_port: 0, version: '5' })
      break
    case 'ssh':
      o.type = 'ssh'; server(o)
      o.user = ifBlank(c.uuid, 'root')
      putIf(o, 'password', c.password)
      // Through a disguise the helper carries the bytes; sing-box still
      // speaks SSH (and checks the host key) end to end.
      if (sshTransportOf(c)) { o.server = '127.0.0.1'; o.server_port = 0 }
      if (!blank(c.privateKey)) o.private_key = [c.privateKey]
      if (!blank(c.publicKey)) o.host_key = [c.publicKey]
      break
    case 'snell': {
      o.type = 'snell'; server(o)
      o.psk = c.password
      // option/snell.go: a client is version 4 (with obfs) or 6; no other value parses.
      const version = c.alterId === 6 ? 6 : 4
      o.version = version
      if (version === 4) {
        putIf(o, 'obfs_mode', c.hyObfs === 'http' || c.hyObfs === 'tls' ? c.hyObfs : null)
        putIf(o, 'obfs_host', c.host)
      }
      break
    }
    case 'openconnect': {
      const x = extraJson(c)
      o.type = 'openconnect'
      o.server = c.port === 443 || c.port <= 0 ? c.address : `${c.address}:${c.port}`
      o.flavor = ['anyconnect', 'gp', 'fortinet', 'f5', 'pulse', 'nc'].includes(c.mode) ? c.mode : 'anyconnect'
      putIf(o, 'username', c.uuid)
      putIf(o, 'password', c.password)
      putIf(o, 'auth_group', optString(x, 'authGroup'))
      const os = optString(x, 'reportedOs')
      putIf(o, 'reported_os', ['linux', 'linux-64', 'win', 'mac-intel', 'android', 'apple-ios'].includes(os) ? os : null)
      putIf(o, 'user_agent', optString(x, 'userAgent'))
      if (inRange(c.mtu, 576, 9000)) o.mtu = c.mtu
      const reconnect = optInt(x, 'reconnect', 0)
      if (reconnect > 0) o.reconnect_timeout = `${reconnect}s`
      if (optBool(x, 'noUdp')) o.no_udp = true
      if (optBool(x, 'ipv6Off')) o.ipv6_disabled = true
      const t = {}
      if (c.allowInsecure) t.insecure = true
      if (!blank(c.sni)) t.server_name = c.sni
      if (!blank(c.pinnedCertSha256)) t.peer_fingerprint = [c.pinnedCertSha256]
      const cert = optString(x, 'clientCert')
      if (cert.includes('BEGIN CERTIFICATE')) t.client_certificate = [cert]
      const key = optString(x, 'clientKey')
      if (key.includes('PRIVATE KEY')) t.client_key = [key]
      if (Object.keys(t).length) o.tls = t
      break
    }
    case 'masque': {
      // sing-box 1.15.0-alpha.7+: masque-client endpoint (CONNECT-IP,
      // RFC 9484) on its internal network stack; HTTP/3 by default, falling
      // back to 2 and 1 unless the profile fixes a version.
      o.type = 'masque-client'; server(o)
      putIf(o, 'username', c.uuid)
      putIf(o, 'password', c.password)
      putIf(o, 'path', c.path)
      const v = toIntOrNull(c.mode)
      if (v !== null && inRange(v, 1, 3)) o.version = v
      if (inRange(c.mtu, 1280, 9000)) o.mtu = c.mtu
      const t = { enabled: true, server_name: ifBlank(c.sni, c.address) }
      if (c.allowInsecure) t.insecure = true
      const alpn = splitList(c.alpn, ',')
      if (alpn.length) t.alpn = alpn
      if (!blank(c.fingerprint)) t.utls = { enabled: true, fingerprint: c.fingerprint }
      const pin = pinBase64(c.pinnedCertSha256)
      if (pin) t.certificate_sha256 = [pin]
      o.tls = t
      break
    }
    case 'tailscale': {
      // Userspace Tailscale node (option/tailscale.go). Its state lives under
      // the sing-box working directory (-D), so the node keeps its identity
      // between connects unless it is ephemeral.
      const flags = new Set(String(c.headerType).split(',').map((s) => s.trim()))
      o.type = 'tailscale'
      o.state_directory = 'tailscale-' + stateId(config)
      putIf(o, 'auth_key', c.password)
      putIf(o, 'control_url', /^https?:\/\//.test(c.host) ? c.host : null)
      // The app names the node "ghajar-android"; the desktop names itself.
      o.hostname = ifBlank(c.sni, 'ghajar-desktop')
      putIf(o, 'exit_node', c.path)
      if (flags.has('ephemeral')) o.ephemeral = true
      if (flags.has('routes')) o.accept_routes = true
      if (flags.has('lan') && !blank(c.path)) o.exit_node_allow_lan_access = true
      break
    }
    case 'tailcat': {
      // DERP-only Tailscale-protocol client (option/tailcat.go).
      o.type = 'tailcat'
      putIf(o, 'private_key', c.privateKey)
      o.server_public_key = c.publicKey
      o.server_disco_key = c.uuid
      putIf(o, 'pre_shared_key', c.password)
      putIf(o, 'derp_map_url', /^https?:\/\//.test(c.host) ? c.host : null)
      const region = toIntOrNull(c.mode)
      if (region !== null && region > 0) o.derp_region = region
      break
    }
    case 'dnstt': case 'vaydns': case 'noizdns': case 'slipstream':
      // What the tunnel server forwards to: an SSH server (the common setup)
      // or a SOCKS5 proxy.
      if (c.method === 'ssh') {
        Object.assign(o, { type: 'ssh', server: '127.0.0.1', server_port: 0, user: ifBlank(c.uuid, 'root') })
        putIf(o, 'password', c.password)
        if (!blank(c.privateKey)) o.private_key = [c.privateKey]
      } else {
        Object.assign(o, { type: 'socks', server: '127.0.0.1', server_port: 0, version: '5' })
        putIf(o, 'username', c.uuid)
        putIf(o, 'password', c.password)
      }
      break
    default:
      throw new Error(`not a sing-box protocol: ${c.protocol}`)
  }
  return o
}

/** The ShadowTLS outbound the Shadowsocks proxy detours through (SingBoxConfig.shadowTlsOut). */
function shadowTlsOut(c) {
  const t = { enabled: true, server_name: ifBlank(c.sni, c.address) }
  if (c.allowInsecure) t.insecure = true
  if (!blank(c.fingerprint)) t.utls = { enabled: true, fingerprint: c.fingerprint }
  const o = { type: 'shadowtls', tag: 'shadowtls-out', server: c.address, server_port: c.port,
    version: inRange(c.alterId, 1, 3) ? c.alterId : 3 }
  putIf(o, 'password', c.password)
  o.tls = t
  return o
}

/**
 * Port of SingBoxConfig.spec(): `{ outbound }` or `{ endpoint }`, plus
 * `extraOutbounds` (ShadowTLS) and `sidecar` (the raw sidecar spec, see
 * sidecarSpec()). Null when the profile is not a sing-box protocol.
 */
function buildSingboxSpec(config) {
  const c = profile(config)
  if (!PROTOCOLS.has(c.protocol)) return null
  const proxy = buildSingboxOutbound(config)
  const out = ENDPOINT_PROTOCOLS.has(c.protocol) ? { endpoint: proxy } : { outbound: proxy }
  if (c.protocol === 'shadowtls') out.extraOutbounds = [shadowTlsOut(c)]
  const side = sidecarSpec(config)
  if (side) out.sidecar = side
  return out
}

/** Every sing-box object a profile needs, in order: the proxy first, then any it detours through. */
function singboxOutbounds(config) {
  const spec = buildSingboxSpec(config)
  if (!spec) return []
  return [spec.outbound || spec.endpoint, ...(spec.extraOutbounds || [])]
}

// ------------------------------------------------------------------ sidecars (SingBoxConfig.sidecar + Sidecars)

/** DNS tunnel fields. For DNS tunnel profiles, host is the tunnel domain (SingBoxConfig.dnstt). */
function dnsttFields(c) {
  const transport = c.mode === 'doh' || c.mode === 'dot' ? c.mode : 'udp'
  let resolver
  if (transport === 'doh') {
    resolver = ifBlank(c.path, `https://${c.address}/dns-query`)
  } else {
    const port = inRange(c.port, 1, 65535) ? c.port : transport === 'dot' ? 853 : 53
    resolver = hostPortString(c.address, port)
  }
  return { transport, resolver, domain: c.host, pubkey: c.publicKey }
}

/** The helper a profile needs in front of sing-box, as the app's raw sidecar spec, or null (SingBoxConfig.sidecar). */
function sidecarSpec(config) {
  const c = profile(config)
  const x = extraJson(c)
  const p = c.protocol
  if (DNSTT_FAMILY.has(p)) {
    const s = { ...dnsttFields(c), kind: p }
    for (const k of ['recordType', 'dnsttCompat', 'maxQnameLen', 'clientIdSize', 'noiz', 'stealth', 'authoritative', 'cc', 'cert']) {
      if (has(x, k)) s[k] = x[k]
    }
    return s
  }
  if (MASTERDNS_FAMILY.has(p)) {
    const first = blank(c.address) ? '' : hostPortString(c.address, inRange(c.port, 1, 65535) ? c.port : 53)
    const resolvers = [...new Set([first, ...String(optString(x, 'resolvers')).split(/[,\n ]/)].map((s) => s.trim()).filter(Boolean))]
    return { kind: p, domain: c.host, key: c.password, enc: optInt(x, 'enc', 1), transport: c.mode, resolvers: resolvers.join(',') }
  }
  switch (p) {
    case 'ssh': {
      const t = sshTransportOf(c)
      return t ? { ...JSON.parse(JSON.stringify(t)), kind: 'sshtransport', host: c.address, port: c.port } : null
    }
    case 'amneziawg': return { kind: 'awg', conf: optString(x, 'conf') }
    case 'mieru': case 'brook': return { kind: p, url: optString(x, 'url') }
    case 'softether':
      return { kind: 'softether', server: hostPortString(c.address, c.port), hub: ifBlank(optString(x, 'hub'), 'DEFAULT'),
        user: c.uuid, password: c.password, plain: optBool(x, 'plain'), sni: c.sni, allowInsecure: c.allowInsecure,
        pin: c.pinnedCertSha256, ip: optString(x, 'ip'), gw: optString(x, 'gw'), dns: optString(x, 'dns'),
        mtu: inRange(c.mtu, 576, 1500) ? c.mtu : 1400 }
    case 'sstp':
      return { kind: 'sstp', server: hostPortString(c.address, c.port), user: c.uuid, password: c.password, sni: c.sni,
        auth: ifBlank(c.method, 'auto'), allowInsecure: c.allowInsecure, pin: c.pinnedCertSha256,
        mtu: inRange(c.mtu, 576, 1500) ? c.mtu : 1400 }
    case 'juicity':
      return { kind: 'juicity', server: hostPortString(c.address, c.port), uuid: c.uuid, password: c.password, sni: c.sni,
        allowInsecure: c.allowInsecure, cc: c.method, pin: c.pinnedCertSha256 }
    default: return null
  }
}

/** Desktop executable base name for a sidecar kind (Sidecars.binaryFor, without lib*.so). */
function binaryFor(kind) {
  switch (kind) {
    case 'dnstt': case 'vaydns': case 'noizdns': case 'masterdns': case 'stormdns': case 'cottendns': case 'slipstream': case 'juicity':
      return kind
    case 'sshtransport': case 'awg': case 'mieru': case 'brook': case 'sstp': case 'softether':
      return HELPER
    default: throw new Error(`unknown engine: ${kind}`)
  }
}

/** The file name of an executable on [platform] (Node's process.platform). */
function executableName(binary, platform = process.platform) {
  return platform === 'win32' ? binary + '.exe' : binary
}

/** Kotlin require(): throws with the app's own user-facing message. */
function need(ok, message) {
  if (!ok) throw new Error(message)
}

/**
 * Global DNS-protocol settings applied on top of a sidecar spec
 * (DnsTunnelPrefs.applyTo). [prefs]: { overrideResolver, overrideTransport,
 * pool, workers, duplication, keepSlowResolvers }; empty means the profile's own.
 */
function applyDnsPrefs(kind, spec, prefs) {
  const v = dnsPrefsOf(prefs)
  const out = { ...spec }
  if (DNSTT_FAMILY.has(kind) && v.overrideResolver) {
    // Slipstream speaks plain UDP DNS only; it keeps its own resolver when the override is DoT/DoH.
    if (kind !== 'slipstream' || v.overrideTransport === 'udp') {
      out.resolver = v.overrideResolver
      out.transport = v.overrideTransport
    }
  }
  if (MASTERDNS_FAMILY.has(kind)) {
    if (v.pool.length) out.resolvers = v.pool.join(',')
    else if (v.overrideResolver && v.overrideTransport === 'udp') out.resolvers = v.overrideResolver
  }
  return out
}

function dnsPrefsOf(p) {
  const o = p && typeof p === 'object' ? p : {}
  return {
    overrideResolver: String(o.overrideResolver || '').trim(),
    overrideTransport: ifBlank(o.overrideTransport, 'udp'),
    pool: Array.isArray(o.pool) ? o.pool.map(String).filter((s) => s.trim()) : [],
    workers: coerceIn(optIntValue(o.workers, 0), 0, 32),
    duplication: coerceIn(optIntValue(o.duplication, 0), 0, 10),
    keepSlowResolvers: !!o.keepSlowResolvers
  }
}

/** Extra client.toml lines for the MasterDNS family (DnsTunnelPrefs.masterTomlLines). */
function masterTomlLines(kind, prefs) {
  const v = dnsPrefsOf(prefs)
  const lines = []
  if (v.workers > 0) lines.push(`RX_TX_WORKERS = ${v.workers}`, `TUNNEL_PROCESS_WORKERS = ${v.workers}`)
  if (v.duplication > 0) {
    lines.push(kind === 'masterdns' ? `PACKET_DUPLICATION_COUNT = ${v.duplication}` : `UPLOAD_PACKET_DUPLICATION_COUNT = ${v.duplication}`)
  }
  if (v.keepSlowResolvers) lines.push('AUTO_DISABLE_TIMEOUT_SERVERS = false')
  return lines
}

const HEX_KEY = /^[0-9a-fA-F]{64}$/

function resolverFlag(spec) {
  const resolver = optString(spec, 'resolver').trim()
  need(resolver, 'DNS tunnel: no resolver')
  const t = optString(spec, 'transport', 'udp')
  return [t === 'doh' ? '-doh' : t === 'dot' ? '-dot' : '-udp', resolver]
}

function tunnelDomain(spec) {
  const d = optString(spec, 'domain').trim().replace(/^\.+|\.+$/g, '')
  need(d, 'DNS tunnel: no domain')
  return d
}

function tunnelPubkey(spec) {
  const k = optString(spec, 'pubkey').trim()
  need(HEX_KEY.test(k), 'DNS tunnel: the server key must be 64 hex digits')
  return k.toLowerCase()
}

const tomlString = (s) => '"' + String(s).replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"'

/**
 * The launch for one raw sidecar spec (Sidecars.launchWith): { binary, args,
 * socks, files, env, readyTimeoutMs, secretFiles }, with {port}, {port2} and
 * {dir} placeholders still in place.
 */
function sidecarLaunch(raw, prefs) {
  const kind = optString(raw, 'kind')
  const spec = applyDnsPrefs(kind, raw, prefs)
  const L = (binary, args, socks, more = {}) => {
    const files = more.files || {}
    return { binary, args, socks, files, env: more.env || {}, readyTimeoutMs: more.readyTimeoutMs || 15000,
      secretFiles: more.secretFiles || Object.keys(files) }
  }
  switch (kind) {
    case 'dnstt': {
      // dnstt-client (v1.20260501.0): (-udp ADDR | -dot ADDR | -doh URL) -pubkey HEX DOMAIN 127.0.0.1:PORT
      const [flag, resolver] = resolverFlag(spec)
      const domain = tunnelDomain(spec)
      return L('dnstt', [flag, resolver, '-pubkey', tunnelPubkey(spec), domain, `127.0.0.1:${PORT}`], false)
    }
    case 'vaydns': {
      // vaydns-client (net2share/vaydns v0.2.8): (-udp|-dot|-doh) R -pubkey HEX -domain D -listen 127.0.0.1:PORT
      //   [-record-type T] [-dnstt-compat] [-max-qname-len N] [-clientid-size N]
      const [flag, resolver] = resolverFlag(spec)
      const args = [flag, resolver, '-pubkey', tunnelPubkey(spec), '-domain', tunnelDomain(spec), '-listen', `127.0.0.1:${PORT}`]
      const rt = optString(spec, 'recordType')
      if (['txt', 'null', 'cname', 'a', 'aaaa', 'mx', 'ns', 'srv', 'caa'].includes(rt)) args.push('-record-type', rt)
      if (optBool(spec, 'dnsttCompat')) args.push('-dnstt-compat')
      const qn = optInt(spec, 'maxQnameLen', 0)
      if (qn > 0) args.push('-max-qname-len', String(qn))
      const cid = optInt(spec, 'clientIdSize', 0)
      if (cid > 0 && !optBool(spec, 'dnsttCompat')) args.push('-clientid-size', String(cid))
      return L('vaydns', args, false)
    }
    case 'noizdns': {
      // noizdns-client (anonvector/noizdns 7289a56): (-udp|-dot|-doh) R -pubkey HEX [-noiz] [-stealth] DOMAIN 127.0.0.1:PORT.
      // Without -noiz it speaks plain dnstt, so it also reaches dnstt servers.
      const [flag, resolver] = resolverFlag(spec)
      const args = [flag, resolver, '-pubkey', tunnelPubkey(spec)]
      const noiz = optBool(spec, 'noiz', true)
      if (noiz) args.push('-noiz')
      if (noiz && optBool(spec, 'stealth')) args.push('-stealth')
      args.push(tunnelDomain(spec), `127.0.0.1:${PORT}`)
      return L('noizdns', args, false)
    }
    case 'masterdns': case 'stormdns': case 'cottendns': {
      // MasterDnsVPN and its forks StormDNS / CottenDNS: client -config FILE -resolvers FILE,
      // TOML configuration; the client itself serves SOCKS5 on LISTEN_PORT.
      const domains = splitList(optString(spec, 'domain'), /[, \n]/).map((d) => d.replace(/^\.+|\.+$/g, '')).filter(Boolean)
      need(domains.length, 'DNS tunnel: no domain')
      const key = optString(spec, 'key')
      need(!blank(key), 'DNS tunnel: no encryption key')
      const resolvers = splitList(optString(spec, 'resolvers'), /[,\n ]/)
      need(resolvers.length, 'DNS tunnel: no resolver')
      const enc = coerceIn(optInt(spec, 'enc', 1), 0, 5)
      const lines = [
        'DOMAINS = [' + domains.map(tomlString).join(', ') + ']',
        `DATA_ENCRYPTION_METHOD = ${enc}`,
        'ENCRYPTION_KEY = ' + tomlString(key),
        'PROTOCOL_TYPE = "SOCKS5"',
        'LISTEN_IP = "127.0.0.1"',
        `LISTEN_PORT = ${PORT}`,
        'LOCAL_DNS_ENABLED = false',
        ...masterTomlLines(kind, prefs)
      ]
      if (kind === 'cottendns') {
        const t = optString(spec, 'transport')
        if (t === 'dot') lines.push('RESOLVER_TRANSPORT = "dot"')
        else if (t === 'doh') lines.push('RESOLVER_TRANSPORT = "doh"')
      }
      return L(binaryFor(kind), ['-config', `${DIR}/client.toml`, '-resolvers', `${DIR}/resolvers.txt`], true, {
        files: { 'client.toml': lines.map((l) => l + '\n').join(''), 'resolvers.txt': resolvers.join('\n') + '\n' },
        // These clients measure resolver MTUs before they listen.
        readyTimeoutMs: 90000,
        secretFiles: ['client.toml']
      })
    }
    case 'slipstream': {
      // slipstream-client (Mygod/slipstream-rust 7de506b, QUIC over DNS): a raw forward like dnstt.
      const [flag, resolver] = resolverFlag(spec)
      need(flag === '-udp', 'Slipstream carries plain UDP DNS only')
      const args = ['--tcp-listen-host', '127.0.0.1', '--tcp-listen-port', PORT, '--resolver', resolver, '--domain', tunnelDomain(spec)]
      const auth = optString(spec, 'authoritative')
      if (!blank(auth)) args.push('--authoritative', auth)
      const cc = optString(spec, 'cc')
      if (cc === 'bbr' || cc === 'dcubic') args.push('-c', cc)
      const files = {}
      const cert = optString(spec, 'cert')
      if (cert.includes('BEGIN CERTIFICATE')) { files['server.pem'] = cert; args.push('--cert', `${DIR}/server.pem`) }
      return L('slipstream', args, false, { files, secretFiles: [] })
    }
    case 'sshtransport': return sshTransportLaunch(spec, L)
    case 'awg': {
      const conf = optString(spec, 'conf')
      need(/\[Interface\]/i.test(conf) && /\[Peer\]/i.test(conf), 'AmneziaWG: the profile needs an [Interface] and a [Peer]')
      return L(HELPER, ['awg', '-listen', PORT, '-config', `${DIR}/awg.conf`], true, { files: { 'awg.conf': conf } })
    }
    case 'mieru': {
      const url = optString(spec, 'url')
      need(url.startsWith('mieru://') || url.startsWith('mierus://'), 'Mieru: a mieru:// or mierus:// link is needed')
      return L(HELPER, ['mieru', '-listen', PORT, '-rpc', PORT2, '-url', url, '-dir', DIR], true, { readyTimeoutMs: 20000 })
    }
    case 'brook': {
      const url = optString(spec, 'url')
      need(url.startsWith('brook://'), 'Brook: a brook:// link is needed')
      return L(HELPER, ['brook', '-listen', PORT, '-url', url], true)
    }
    case 'juicity': {
      // juicity-client (juicity/juicity v0.5.0, AGPL-3.0, separate program): run -c FILE; "listen" serves SOCKS5.
      const server = optString(spec, 'server')
      need(!blank(server), 'Juicity: no server')
      const uuid = optString(spec, 'uuid')
      need(!blank(uuid), 'Juicity: no UUID')
      const cfg = { listen: `127.0.0.1:${PORT}`, server, uuid, password: optString(spec, 'password'), sni: optString(spec, 'sni'),
        allow_insecure: optBool(spec, 'allowInsecure'), congestion_control: ifBlank(optString(spec, 'cc'), 'bbr'), log_level: 'info' }
      const pin = optString(spec, 'pin')
      if (!blank(pin)) cfg.pinned_certchain_sha256 = pin
      return L('juicity', ['run', '-c', `${DIR}/juicity.json`], true, { files: { 'juicity.json': JSON.stringify(cfg) } })
    }
    case 'sstp': {
      // ghajar-helper sstp: SSTP + PPP in userspace, served as SOCKS5. The
      // password goes through a file deleted once the helper listens, never on the command line.
      const server = optString(spec, 'server')
      need(!blank(server) && !server.startsWith(':'), 'SSTP: no server')
      const user = optString(spec, 'user')
      need(!blank(user), 'SSTP: no user name')
      const args = ['sstp', '-listen', PORT, '-server', server, '-user', user, '-auth', ifBlank(optString(spec, 'auth'), 'auto'),
        '-mtu', String(coerceIn(optInt(spec, 'mtu', 1400), 576, 1500))]
      const sni = optString(spec, 'sni')
      if (!blank(sni)) args.push('-sni', sni)
      const pin = optString(spec, 'pin')
      if (!blank(pin)) {
        need(/^[0-9a-fA-F:]{64,95}$/.test(pin), 'SSTP: the certificate pin must be a SHA-256 in hex')
        args.push('-pin', pin)
      }
      if (optBool(spec, 'allowInsecure')) args.push('-insecure')
      return L(HELPER, args, true, { files: { 'sstp.pass': optString(spec, 'password') },
        env: { SSTP_PASSWORD_FILE: `${DIR}/sstp.pass` }, readyTimeoutMs: 30000 })
    }
    case 'softether': {
      // ghajar-helper softether: SoftEther's own protocol to a Virtual Hub,
      // DHCP (or a static address) and ARP in userspace, served as SOCKS5.
      const server = optString(spec, 'server')
      need(!blank(server) && !server.startsWith(':'), 'SoftEther: no server')
      const user = optString(spec, 'user')
      need(!blank(user), 'SoftEther: no user name')
      const args = ['softether', '-listen', PORT, '-server', server, '-hub', ifBlank(optString(spec, 'hub'), 'DEFAULT'),
        '-user', user, '-mtu', String(coerceIn(optInt(spec, 'mtu', 1400), 576, 1500))]
      if (optBool(spec, 'plain')) args.push('-plain')
      const sni = optString(spec, 'sni')
      if (!blank(sni)) args.push('-sni', sni)
      const pin = optString(spec, 'pin')
      if (!blank(pin)) {
        need(/^[0-9a-fA-F:]{64,95}$/.test(pin), 'SoftEther: the certificate pin must be a SHA-256 in hex')
        args.push('-pin', pin)
      }
      if (optBool(spec, 'allowInsecure')) args.push('-insecure')
      const ip = optString(spec, 'ip')
      if (!blank(ip)) {
        need(/^\d{1,3}(\.\d{1,3}){3}\/\d{1,2}$/.test(ip), 'SoftEther: static address must look like 10.0.0.2/24')
        args.push('-ip', ip)
      }
      const gw = optString(spec, 'gw')
      if (!blank(gw)) args.push('-gw', gw)
      const dns = optString(spec, 'dns')
      if (!blank(dns)) args.push('-dns', dns)
      return L(HELPER, args, true, { files: { 'se.pass': optString(spec, 'password') },
        env: { SE_PASSWORD_FILE: `${DIR}/se.pass` }, readyTimeoutMs: 30000 })
    }
    default: throw new Error(`unknown engine: ${kind}`)
  }
}

/**
 * ghajar-helper sshtransport: a raw forward to the SSH server through the
 * chosen disguise; sing-box's SSH client (with its host-key check) runs on top.
 */
function sshTransportLaunch(spec, L) {
  const mode = optString(spec, 'mode')
  need(SSH_MODES.has(mode), `SSH: unknown transport mode ${mode}`)
  const host = optString(spec, 'host')
  need(!blank(host), 'SSH: no server')
  const args = ['sshtransport', '-listen', PORT, '-mode', mode, '-host', host, '-port', String(optInt(spec, 'port', 22))]
  const proxyHost = optString(spec, 'proxyHost')
  if (!blank(proxyHost)) {
    const def = mode === 'https-proxy' || mode.includes('tls') || mode === 'wss' ? 443 : 80
    args.push('-proxy', proxyHost + ':' + optInt(spec, 'proxyPort', def))
  } else {
    need(mode !== 'http-proxy' && mode !== 'https-proxy', 'SSH: this mode needs a proxy address')
  }
  const sni = optString(spec, 'sni')
  if (!blank(sni)) args.push('-sni', sni)
  const payload = optString(spec, 'payload')
  if (!blank(payload)) args.push('-payload', Buffer.from(payload, 'utf8').toString('base64'))
  const wsPath = optString(spec, 'wsPath')
  if (!blank(wsPath)) args.push('-ws-path', wsPath)
  const wsHost = optString(spec, 'wsHost')
  if (!blank(wsHost)) args.push('-ws-host', wsHost)
  const ua = optString(spec, 'ua')
  if (!blank(ua)) args.push('-ua', ua)
  if (optBool(spec, 'wsFraming')) args.push('-ws-framing')
  if (optBool(spec, 'verify')) args.push('-verify')
  need(!(mode === 'payload' || mode === 'payload-tls') || !blank(payload), 'SSH: this mode needs a payload')
  return L(HELPER, args, false)
}

/**
 * What to run in front of sing-box for [config], or null when it needs no
 * helper. Mirrors SingBoxRunner.startInner + SidecarRunner.start.
 *
 * ports: a number (the helper's local port) or { localPort, rpcPort, dir }.
 *   rpcPort is the second free port Mieru needs; dir is the private working
 *   directory (files are written there, it is the process cwd and HOME). When
 *   dir is omitted the {dir} placeholder stays in args/env/files.
 * prefs: optional DNS-protocol settings (see applyDnsPrefs).
 *
 * Returns { kind, binary, args, env, files, cwd, localPort, socks,
 * readyTimeoutMs, secretFiles, outbound }: `binary` is the executable base
 * name (use executableName() for ".exe"); `files` are written into dir before
 * the start and `secretFiles` deleted once localPort accepts a connection;
 * `outbound` is the sing-box proxy outbound pointed at 127.0.0.1:localPort.
 * Throws with the app's message when the profile is incomplete.
 */
function sidecarPlan(config, ports, prefs) {
  const raw = sidecarSpec(config)
  if (!raw) return null
  const p = typeof ports === 'number' ? { localPort: ports } : (ports || {})
  const localPort = optIntValue(p.localPort, 0)
  need(inRange(localPort, 1, 65535), 'sidecarPlan: a local port is needed')
  const rpcPort = optIntValue(p.rpcPort, 0)
  const dir = p.dir ? String(p.dir) : null
  const launch = sidecarLaunch(raw, prefs)
  if (launch.args.includes(PORT2)) need(inRange(rpcPort, 1, 65535) && rpcPort !== localPort, 'sidecarPlan: Mieru needs a second free local port (rpcPort)')
  const fill = (s) => {
    let v = String(s).split(PORT2).join(String(rpcPort)).split(PORT).join(String(localPort))
    if (dir) v = v.split(DIR + '/').join(dir + path.sep).split(DIR).join(dir)
    return v
  }
  const files = {}
  for (const [name, content] of Object.entries(launch.files)) files[name] = fill(content)
  const env = {}
  if (dir) env.HOME = dir
  for (const [k, v] of Object.entries(launch.env)) env[k] = fill(v)
  const spec = buildSingboxSpec(config)
  const outbound = { ...(spec.outbound || spec.endpoint), server_port: localPort }
  return {
    kind: raw.kind, binary: launch.binary, args: launch.args.map(fill), env, files, cwd: dir,
    localPort, socks: launch.socks, readyTimeoutMs: launch.readyTimeoutMs, secretFiles: launch.secretFiles, outbound
  }
}

/**
 * Host names a profile's processes connect to outside the tunnel: the server,
 * and for sidecars the resolver / front they dial. Used by buildTunConfig to
 * resolve them directly, so a core waiting on its own server name never waits
 * on the tunnel it is about to build.
 */
function remoteHosts(config) {
  const c = profile(config)
  const x = extraJson(c)
  const out = []
  const add = (h) => {
    const s = String(h || '').trim().replace(/^\[|\]$/g, '')
    if (s && !isIp(s)) out.push(s.toLowerCase())
  }
  if (DNSTT_FAMILY.has(c.protocol)) {
    const f = dnsttFields(c)
    if (f.transport === 'doh') { try { add(new URL(f.resolver).hostname) } catch {} } else add(c.address)
  } else if (MASTERDNS_FAMILY.has(c.protocol)) {
    add(c.address)
    for (const r of splitList(optString(x, 'resolvers'), /[,\n ]/)) add(r.replace(/:\d+$/, ''))
  } else if (c.protocol !== 'tailscale' && c.protocol !== 'tailcat') {
    add(c.address)
  }
  if (c.protocol === 'ssh') { const t = sshTransportOf(c); if (t) add(optString(t, 'proxyHost')) }
  if (c.protocol === 'tailscale' || c.protocol === 'tailcat') { try { add(new URL(c.host).hostname) } catch {} }
  if (c.protocol === 'mieru' || c.protocol === 'brook') { try { add(new URL(optString(x, 'url')).hostname) } catch {} }
  if (c.protocol === 'amneziawg') {
    const m = /^\s*Endpoint\s*=\s*(\S+)/im.exec(optString(x, 'conf'))
    if (m) add(m[1].replace(/:\d+$/, ''))
  }
  return [...new Set(out)]
}

function isIp(s) {
  return /^\d{1,3}(\.\d{1,3}){3}$/.test(s) || (s.includes(':') && /^[0-9a-fA-F:.]+$/.test(s))
}

// ------------------------------------------------------------------ whole configurations

/**
 * Sorts what the caller passed as `outbound` into sing-box outbounds and
 * endpoints. Accepts a single outbound/endpoint object, an array of them,
 * a spec from buildSingboxSpec(), or a sidecarPlan() result.
 */
function proxyObjects(outbound) {
  const outbounds = []
  const endpoints = []
  const put = (o) => {
    if (!o || typeof o !== 'object') return
    ;(ENDPOINT_TYPES.has(o.type) ? endpoints : outbounds).push(JSON.parse(JSON.stringify(o)))
  }
  if (Array.isArray(outbound)) outbound.forEach(put)
  else if (outbound && !outbound.type && (outbound.outbound || outbound.endpoint)) {
    put(outbound.outbound)
    put(outbound.endpoint)
    ;(outbound.extraOutbounds || []).forEach(put)
  } else put(outbound)
  const all = [...outbounds, ...endpoints]
  need(all.length, 'no proxy outbound')
  if (!all.some((o) => o.tag === 'proxy')) all[0].tag = 'proxy'
  // A ShadowTLS proxy given without its detour cannot work; say so here rather than at connect time.
  for (const o of all) {
    if (o.detour && !all.some((d) => d.tag === o.detour)) throw new Error(`outbound "${o.tag}" detours through "${o.detour}", which was not given; pass buildSingboxSpec(config) or singboxOutbounds(config)`)
  }
  return { outbounds, endpoints }
}

/** The Xray core's local SOCKS5 inbound, as the sing-box outbound the TUN forwards to. */
function socksOutbound(socksPort) {
  return { type: 'socks', tag: 'proxy', server: '127.0.0.1', server_port: socksPort, version: '5' }
}

/**
 * The resolver lookups through the tunnel go to. Accepts what the app's
 * Settings -> DNS protocols -> remote DNS accepts (a bare IP, sent as UDP
 * through the proxy - SingBoxConfig.remoteDns) and also https:// and tls://
 * URLs. Default: DoH to 1.1.1.1, which is TCP so it also survives a SOCKS
 * upstream without UDP associate.
 */
function remoteDnsServer(value) {
  const s = String(value || '').trim()
  const base = { tag: 'remote', detour: 'proxy' }
  if (/^\d{1,3}(\.\d{1,3}){3}$/.test(s) || (s.includes(':') && /^[0-9a-zA-Z:]+$/.test(s) && !s.includes('//'))) {
    return { type: 'udp', ...base, server: s }
  }
  let u = null
  try { u = s ? new URL(s) : null } catch { u = null }
  if (u && (u.protocol === 'https:' || u.protocol === 'tls:') && u.hostname) {
    const server = { type: u.protocol === 'https:' ? 'https' : 'tls', ...base, server: u.hostname.replace(/^\[|\]$/g, '') }
    if (u.port) server.server_port = parseInt(u.port, 10)
    if (u.protocol === 'https:' && u.pathname && u.pathname !== '/' && u.pathname !== '/dns-query') server.path = u.pathname
    // A server given by name is itself resolved directly (no loop through the tunnel).
    if (!isIp(server.server)) server.domain_resolver = 'local'
    return server
  }
  return { type: 'https', ...base, server: '1.1.1.1' }
}

/** process_name values for the cores, with ".exe" on Windows (sing-box matches the executable's base name). */
function processNames(extra, platform) {
  const names = [...new Set([...CORE_PROCESSES, ...(Array.isArray(extra) ? extra.map(String) : [])])]
  return platform === 'win32' ? names.map((n) => (/\.exe$/i.test(n) ? n : n + '.exe')) : names
}

const reEscape = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

/**
 * Route-rule matchers for a user's per-app list. sing-box ANDs different
 * fields inside one rule, so each kind of match is its own rule:
 *  - a full path (contains / or \) -> process_path;
 *  - a name -> process_name (".exe" added on Windows when it has no extension);
 *  - on macOS a name also matches its app bundle by path ("Google Chrome" ->
 *    (?i)/Google Chrome\.app/), because the traffic of a macOS app usually comes
 *    from its helper processes ("Google Chrome Helper") inside the bundle.
 */
function perAppMatchers(list, platform) {
  const names = new Set()
  const paths = new Set()
  const regex = new Set()
  for (const raw of Array.isArray(list) ? list : []) {
    const v = String(raw || '').trim()
    if (!v) continue
    if (/[\\/]/.test(v)) { paths.add(v); continue }
    if (platform === 'darwin') {
      const app = v.replace(/\.app$/i, '')
      names.add(app)
      regex.add('(?i)/' + reEscape(app) + '\\.app/')
    } else if (platform === 'win32') {
      names.add(/\.[A-Za-z0-9]{1,4}$/.test(v) ? v : v + '.exe')
    } else {
      names.add(v)
    }
  }
  const out = []
  if (names.size) out.push({ process_name: [...names] })
  if (paths.size) out.push({ process_path: [...paths] })
  if (regex.size) out.push({ process_path_regex: [...regex] })
  return out
}

/** The Iran rule-set when settings.iranRuleSet names a local file (.srs binary, or .json source). */
function iranRuleSet(settings) {
  const p = String(settings.iranRuleSet || '').trim()
  if (!p) return null
  return { type: 'local', tag: 'geoip-ir', format: /\.json$/i.test(p) ? 'source' : 'binary', path: p }
}

/** Route rules shared by both modes: private addresses and (optionally) Iran stay direct. */
function directRules(settings, ruleSet) {
  const rules = [{ ip_is_private: true, action: 'route', outbound: 'direct' }]
  if (settings.iranDirect) {
    // Iranian sites stay direct: faster, and many refuse foreign addresses (as the Xray builder does).
    rules.push({ domain_suffix: ['.ir'], action: 'route', outbound: 'direct' })
    if (ruleSet) rules.push({ rule_set: [ruleSet.tag], action: 'route', outbound: 'direct' })
  }
  return rules
}

function logOf(settings) {
  return { level: settings.logLevel || 'info', timestamp: true }
}

/**
 * A complete sing-box configuration that captures the whole device in a TUN.
 *
 * - socksPort: the Xray core's local SOCKS5 port, used when `outbound` is
 *   not given (Xray protocols, Psiphon, Aether, Tor all end in that SOCKS5).
 * - outbound: a sing-box protocol (buildSingboxSpec(config), an outbound,
 *   an array of them, or a sidecarPlan()); traffic then goes to it directly.
 * - settings: { iranDirect, iranRuleSet, remoteDns, serverHosts,
 *   excludeProcesses, perApp: { mode: 'include'|'exclude', processes },
 *   mtu, stack, logLevel, dnsStrategy, interfaceName }.
 * - platform: 'win32' | 'darwin' | 'linux' (default: this process's).
 */
function buildTunConfig({ socksPort, outbound, settings = {}, platform = process.platform } = {}) {
  const s = settings || {}
  let proxy
  if (outbound) proxy = proxyObjects(outbound)
  else {
    need(inRange(optIntValue(socksPort, 0), 1, 65535), 'buildTunConfig: socksPort or outbound is needed')
    proxy = { outbounds: [socksOutbound(optIntValue(socksPort, 0))], endpoints: [] }
  }
  const all = [...proxy.outbounds, ...proxy.endpoints]

  const tun = { type: 'tun', tag: 'tun-in' }
  // macOS only accepts utunN names (sing-tun tun_darwin.go: "bad tun name"),
  // so the name is left to sing-box there; elsewhere it is ours.
  if (platform !== 'darwin') tun.interface_name = s.interfaceName || 'ghajar-tun'
  tun.address = [TUN_INET4, TUN_INET6]
  tun.mtu = inRange(optIntValue(s.mtu, 0), 1280, 65535) ? optIntValue(s.mtu, 0) : TUN_MTU
  tun.auto_route = true
  tun.strict_route = true
  // No "stack" by default: in the pinned 1.15 it is deprecated, and leaving it
  // out selects sing-tun's own stack. "mixed" and "gvisor" would also need the
  // with_gvisor build tag, which scripts/build-singbox.sh does not set - they
  // pass `check` but fail when the TUN starts. An explicit choice is honoured.
  if (['system', 'gvisor', 'mixed', 'go'].includes(s.stack)) tun.stack = s.stack

  const ruleSet = iranRuleSet(s)
  const cores = processNames(s.excludeProcesses, platform)

  // Names the cores and sidecars resolve for themselves: answered by the
  // local resolver, never through the tunnel those cores are still building.
  const hosts = new Set((Array.isArray(s.serverHosts) ? s.serverHosts : []).map((h) => String(h).trim().toLowerCase()).filter((h) => h && !isIp(h)))
  for (const o of all) {
    if (typeof o.server === 'string' && !isIp(o.server) && o.server !== '127.0.0.1' && o.server !== 'localhost') hosts.add(o.server.replace(/:\d+$/, '').toLowerCase())
  }

  const dnsRules = []
  if (hosts.size) dnsRules.push({ domain: [...hosts], action: 'route', server: 'local' })
  dnsRules.push({ process_name: cores, action: 'route', server: 'local' })
  if (s.iranDirect) dnsRules.push({ domain_suffix: ['.ir'], action: 'route', server: 'local' })

  // Per-app split (settings.perApp). The cores' rule above comes first either
  // way, so they always leave directly. 'include': only the listed apps are
  // proxied, everything else (final) is direct. 'exclude': the listed apps
  // are direct, everything else is proxied. DNS stays hijacked in both modes,
  // since lookups usually arrive from the OS resolver, not from the app.
  const perApp = s.perApp && typeof s.perApp === 'object' ? s.perApp : null
  const matchers = perApp ? perAppMatchers(perApp.processes, platform) : []
  const includeOnly = !!perApp && perApp.mode === 'include' && matchers.length > 0
  const perAppInclude = includeOnly ? matchers.map((m) => ({ ...m, action: 'route', outbound: 'proxy' })) : []
  const perAppExclude = perApp && perApp.mode === 'exclude' ? matchers.map((m) => ({ ...m, action: 'route', outbound: 'direct' })) : []

  const route = {
    rules: [
      { action: 'sniff' },
      // Lookups addressed to the tun's own resolver are always answered by
      // sing-box, whoever sends them (TCP included; UDP is caught earlier by the tun).
      { ip_cidr: TUN_DNS.map((a) => (a.includes(':') ? a + '/128' : a + '/32')), action: 'hijack-dns' },
      // The cores' own connections leave directly - this is what keeps the
      // tunnel from looping into itself. Before the DNS hijack on purpose: the
      // DNS tunnel clients' port-53 queries ARE their tunnel.
      { process_name: cores, action: 'route', outbound: 'direct' },
      { protocol: 'dns', action: 'hijack-dns' },
      { port: 53, action: 'hijack-dns' },
      ...perAppExclude,
      ...directRules(s, ruleSet),
      ...perAppInclude
    ],
    final: includeOnly ? 'direct' : 'proxy',
    // On a desktop the outbounds must bind to the physical interface, or
    // sing-box's own connections would be routed back into the TUN.
    auto_detect_interface: true,
    default_domain_resolver: { server: 'local' }
  }
  if (ruleSet) route.rule_set = [ruleSet]

  const config = {
    log: logOf(s),
    dns: {
      servers: [
        remoteDnsServer(s.remoteDns),
        // The system resolver of the physical interface (sing-box tracks it
        // past the TUN's own DNS), as the Android app uses "local".
        { type: 'local', tag: 'local' }
      ],
      rules: dnsRules,
      final: 'remote',
      strategy: ['prefer_ipv4', 'prefer_ipv6', 'ipv4_only', 'ipv6_only'].includes(s.dnsStrategy) ? s.dnsStrategy : 'prefer_ipv4'
    },
    inbounds: [tun],
    outbounds: [...proxy.outbounds, { type: 'direct', tag: 'direct' }],
    route
  }
  if (proxy.endpoints.length) config.endpoints = proxy.endpoints
  return config
}

/**
 * A sing-box configuration for proxy / system-proxy mode: a "mixed"
 * (SOCKS5 + HTTP) inbound on 127.0.0.1:socksPort, an extra HTTP inbound on
 * httpPort when it differs, and the sing-box protocol outbound behind them.
 * The shape of SingBoxConfig.full() (local resolver, remote DNS hijack when
 * settings.remoteDns is set), plus private / Iran direct like the Xray builder.
 */
function buildProxyOnlyConfig({ outbound, socksPort, httpPort, settings = {} } = {}) {
  const s = settings || {}
  const port = optIntValue(socksPort, 0)
  need(inRange(port, 1, 65535), 'buildProxyOnlyConfig: socksPort is needed')
  const proxy = proxyObjects(outbound)
  const inbounds = [{ type: 'mixed', tag: 'mixed-in', listen: '127.0.0.1', listen_port: port }]
  const hp = optIntValue(httpPort, 0)
  if (inRange(hp, 1, 65535) && hp !== port) inbounds.push({ type: 'http', tag: 'http-in', listen: '127.0.0.1', listen_port: hp })

  const ruleSet = iranRuleSet(s)
  const rules = [{ action: 'sniff' }]
  const dns = {
    // A server given by name needs a resolver in this sing-box version
    // (common/dialer/dialer.go: "missing domain resolver").
    servers: [{ type: 'local', tag: 'local' }]
  }
  if (!blank(s.remoteDns)) {
    // Settings -> DNS protocols -> remote DNS (SingBoxConfig.remoteDns): lookups
    // coming through the proxy are answered by this resolver, reached through it.
    dns.servers.push(remoteDnsServer(s.remoteDns))
    dns.final = 'remote'
    rules.push({ protocol: 'dns', action: 'hijack-dns' })
  }
  rules.push(...directRules(s, ruleSet))
  const route = { rules, final: 'proxy', default_domain_resolver: { server: 'local' } }
  if (ruleSet) route.rule_set = [ruleSet]
  const config = {
    log: logOf(s),
    dns,
    inbounds,
    outbounds: [...proxy.outbounds, { type: 'direct', tag: 'direct' }],
    route
  }
  if (proxy.endpoints.length) config.endpoints = proxy.endpoints
  return config
}

module.exports = {
  PROTOCOLS, DNSTT_FAMILY, MASTERDNS_FAMILY, SSH_MODES, CORE_PROCESSES, HELPER,
  handles,
  buildSingboxOutbound,
  buildSingboxSpec,
  singboxOutbounds,
  sidecarSpec,
  sidecarLaunch,
  sidecarPlan,
  binaryFor,
  executableName,
  remoteHosts,
  socksOutbound,
  buildTunConfig,
  buildProxyOnlyConfig,
  perAppMatchers,
  pinBase64,
  stateId
}
