// OpenVPN for the desktop app: an .ovpn profile, run by sing-box.
//
// On Android an imported .ovpn goes to the ics-openvpn module
// (GhajarOpenVpnBridge.kt): the raw text is kept, the host and port of its
// first remote name the server ("Ghajarvpn 🇩🇪", GhajarUiRules.ovpnDisplayName),
// and a username / password is asked for when the profile has auth-user-pass
// without inline credentials. The desktop keeps the same profile as a
// ProxyConfig:
//
//   protocol 'openvpn', address/port/network of the first remote,
//   uuid = username, password = password (embedded <auth-user-pass> ones by default),
//   extra = { "ovpn": "<the .ovpn text, referenced files embedded>" }
//
// and carries it with the `openvpn-client` endpoint of the pinned sing-box
// (SagerNet/sing-box 132b38e, option/openvpn.go, protocol/openvpn/client.go,
// built with_openvpn), behind the same local SOCKS/HTTP port as every other
// sing-box protocol. That endpoint has its own userspace network stack, so no
// TUN driver or administrator rights are needed for the OpenVPN part.
//
// buildOpenvpnSpec() maps the OpenVPN client directives onto the sing-box
// fields one by one; a directive sing-box cannot honour (dev tap, PKCS#12, an
// encrypted key, a file that is not embedded) is an error with the reason
// rather than silently dropped. The one exception is an inline <crl-verify>
// (sing-box takes a CRL by path only), which is ignored; crl-verify <file> is
// kept as an absolute path. Directives that only change the local machine's
// routing table (redirect-gateway, route, dhcp-option, block-outside-dns, …)
// do not apply to a proxy endpoint and are ignored, as are the ones that only
// steer the openvpn process itself (verb, persist-*, nobind, resolv-retry, …).
//
// No dependencies beyond Node's standard library.
'use strict'

const fs = require('fs')
const path = require('path')

/** Directives whose argument is a file, and their inline block (same name). */
const FILE_DIRECTIVES = ['ca', 'cert', 'key', 'tls-auth', 'tls-crypt', 'tls-crypt-v2', 'secret', 'pkcs12',
  'extra-certs', 'auth-user-pass', 'http-proxy-user-pass', 'crl-verify', 'peer-fingerprint']

/** OpenVPN 2.6's default data-ciphers, which sing-box also uses when the list is empty. */
const DEFAULT_DATA_CIPHERS = ['AES-256-GCM', 'AES-128-GCM', 'CHACHA20-POLY1305']

const INLINE = /^\[\[?inline\]?\]$/i

// ------------------------------------------------------------------ reading .ovpn text

/**
 * One line split into words the way OpenVPN's parse_line does: blanks
 * separate, "double" and 'single' quotes group, a backslash escapes inside
 * double quotes and outside quotes; '#' or ';' at the start of a word ends the line.
 */
function words(line) {
  const out = []
  let cur = ''
  let has = false
  let quote = ''
  for (let i = 0; i < line.length; i++) {
    const ch = line[i]
    if (quote) {
      if (ch === quote) { quote = ''; continue }
      if (ch === '\\' && quote === '"' && i + 1 < line.length) { cur += line[++i]; continue }
      cur += ch
      continue
    }
    if (ch === '"' || ch === "'") { quote = ch; has = true; continue }
    if (/\s/.test(ch)) { if (has) { out.push(cur); cur = ''; has = false } continue }
    if ((ch === '#' || ch === ';') && !has) break
    if (ch === '\\' && i + 1 < line.length) { cur += line[++i]; has = true; continue }
    cur += ch
    has = true
  }
  if (has) out.push(cur)
  return out
}

/**
 * The .ovpn text as { directives: [{ name, args }], blocks: { tag: text },
 * connections: [{ directives }] } - inline <tag>…</tag> blocks collected by
 * name, <connection> blocks kept apart (each one a remote with its options).
 */
function readOvpn(text) {
  const lines = String(text || '').replace(/^﻿/, '').split(/\r\n|\n|\r/)
  const top = { directives: [], blocks: {}, connections: [] }
  let conn = null
  for (let i = 0; i < lines.length; i++) {
    const raw = lines[i].trim()
    if (!raw) continue
    const open = /^<([A-Za-z0-9_-]+)>$/.exec(raw)
    if (open) {
      const tag = open[1].toLowerCase()
      if (tag === 'connection') { conn = { directives: [], blocks: {} }; continue }
      const body = []
      const close = `</${tag}>`
      for (i++; i < lines.length && lines[i].trim().toLowerCase() !== close; i++) body.push(lines[i].replace(/\s+$/, ''))
      ;(conn || top).blocks[tag] = body.join('\n').trim()
      continue
    }
    if (/^<\/connection>$/i.test(raw)) { if (conn) top.connections.push(conn); conn = null; continue }
    const w = words(raw)
    if (!w.length) continue
    const name = w[0].replace(/^--/, '').toLowerCase()
    ;(conn || top).directives.push({ name, args: w.slice(1) })
  }
  return top
}

/** What the Android import checks first (GhajarOpenVpnBridge.inspect / MainActivity's .ovpn sniffing). */
function isOvpn(text) {
  const t = String(text || '')
  if (!/^\s*(client|remote)\b/im.test(t) && !/<connection>/i.test(t)) return false
  return /remote\s/i.test(t) || /<connection>/i.test(t)
}

/**
 * Rewrites directives that point at files (ca ca.crt, tls-auth ta.key 1, …)
 * into inline blocks, reading the files relative to baseDir, so the stored
 * profile no longer depends on where it was imported from. Files that cannot
 * be read are left as they are (buildOpenvpnSpec then says which).
 */
function embedFiles(text, baseDir, readFile = f => fs.readFileSync(f, 'utf8')) {
  if (!baseDir) return String(text)
  const out = []
  for (const line of String(text).split(/\r\n|\n|\r/)) {
    const w = words(line.trim())
    const name = (w[0] || '').replace(/^--/, '').toLowerCase()
    // sing-box reads a CRL by path only: keep it a path, made absolute.
    if (name === 'crl-verify' && w[1] && !INLINE.test(w[1]) && !path.isAbsolute(w[1])) { out.push(`crl-verify "${path.join(baseDir, w[1]).replace(/\\/g, '\\\\')}"`); continue }
    if (name !== 'crl-verify' && FILE_DIRECTIVES.includes(name) && w[1] && !INLINE.test(w[1]) && !(name === 'peer-fingerprint' && /^[0-9a-f:]{64,}$/i.test(w[1]))) {
      let content = null
      try { content = readFile(path.isAbsolute(w[1]) ? w[1] : path.join(baseDir, w[1])) } catch { content = null }
      if (content != null && String(content).trim()) {
        // The direction argument of tls-auth / secret moves to key-direction.
        if ((name === 'tls-auth' || name === 'secret') && w[2] !== undefined) out.push(`key-direction ${w[2]}`)
        out.push(`<${name}>`, String(content).trim(), `</${name}>`)
        continue
      }
    }
    out.push(line)
  }
  return out.join('\n')
}

// ------------------------------------------------------------------ directives → options

const last = (dirs, name) => { for (let i = dirs.length - 1; i >= 0; i--) if (dirs[i].name === name) return dirs[i]; return null }
const all = (dirs, name) => dirs.filter(d => d.name === name)
const intArg = (v) => (/^\d+$/.test(String(v || '')) ? parseInt(v, 10) : null)

/** OpenVPN proto names → the sing-box network (tcp-client → tcp); null for server-side ones. */
function networkOf(proto) {
  const p = String(proto || '').toLowerCase().replace(/-client$/, '')
  return ['udp', 'udp4', 'udp6', 'tcp', 'tcp4', 'tcp6'].includes(p) ? p : null
}

/** A 32-bit netmask → prefix length, or -1. */
function maskBits(mask) {
  const parts = String(mask).split('.').map(Number)
  if (parts.length !== 4 || parts.some(n => !(n >= 0 && n <= 255))) return -1
  const n = ((parts[0] << 24) | (parts[1] << 16) | (parts[2] << 8) | parts[3]) >>> 0
  const bits = n.toString(2).padStart(32, '0')
  return /^1*0*$/.test(bits) ? bits.indexOf('0') < 0 ? 32 : bits.indexOf('0') : -1
}

const ip4 = s => /^\d{1,3}(\.\d{1,3}){3}$/.test(String(s || ''))
const ip4num = s => s.split('.').reduce((a, b) => ((a << 8) | Number(b)) >>> 0, 0)

/** The material of a file directive: its inline block, or a note that the file is missing. */
function material(top, name, missing) {
  if (top.blocks[name]) return top.blocks[name]
  const d = last(top.directives, name)
  if (d && d.args[0] && !INLINE.test(d.args[0])) missing.push(`${name} ${d.args[0]}`)
  return ''
}

/**
 * The .ovpn profile as a flat object of what matters to a client:
 * remotes, TLS material, data-channel settings, timings, credentials.
 * `missing` lists files the profile references but does not embed.
 */
function parseOvpn(text) {
  const top = readOvpn(text)
  const d = top.directives
  const has = n => !!last(d, n)
  const arg = (n, i = 0) => { const x = last(d, n); return x ? x.args[i] : undefined }
  const missing = []
  const unsupported = []

  // Remotes: `remote host [port] [proto]`, each <connection> block too, with
  // the profile's `port` / `rport` / `proto` as defaults (applied at the end, as OpenVPN does).
  const defPort = intArg(arg('rport') ?? arg('port')) || 1194
  const defProto = arg('proto') || 'udp'
  const remotes = []
  const addRemote = (args, port, proto) => {
    if (!args[0]) return
    const network = networkOf(args[2] || proto)
    if (!network) { unsupported.push(`proto ${args[2] || proto}`); return }
    remotes.push({ host: args[0].replace(/^\[|\]$/g, ''), port: intArg(args[1]) || port, network })
  }
  for (const r of all(d, 'remote')) addRemote(r.args, defPort, defProto)
  for (const c of top.connections) {
    const cd = c.directives
    const cp = intArg((last(cd, 'rport') || last(cd, 'port') || { args: [] }).args[0]) || defPort
    const cproto = (last(cd, 'proto') || { args: [defProto] }).args[0]
    for (const r of all(cd, 'remote')) addRemote(r.args, cp, cproto)
  }

  const devType = String(arg('dev-type') || arg('dev') || 'tun').toLowerCase()
  if (devType.startsWith('tap')) unsupported.push('dev tap (only tun is supported)')
  if (has('pkcs12') || top.blocks.pkcs12) unsupported.push('pkcs12 (export the certificate and key as PEM <cert>/<key>)')
  if (has('tls-server') || has('server') || arg('mode') === 'server') unsupported.push('server mode')

  const o = { remotes, missing, unsupported }
  o.remoteRandom = has('remote-random')

  // Static key (pre-shared, no TLS) or the usual TLS mode.
  o.staticKey = material(top, 'secret', missing)
  o.mode = o.staticKey || has('secret') ? 'static_key' : 'tls'
  const keyDir = arg('key-direction')

  o.ca = material(top, 'ca', missing)
  o.cert = material(top, 'cert', missing)
  o.key = material(top, 'key', missing)
  o.extraCerts = material(top, 'extra-certs', missing)
  if (/ENCRYPTED/.test(o.key)) unsupported.push('an encrypted private key (export it without a passphrase)')

  // Control channel wrapping: tls-crypt-v2 > tls-crypt > tls-auth, as only one applies.
  for (const [name, type] of [['tls-crypt-v2', 'tls_crypt_v2'], ['tls-crypt', 'tls_crypt'], ['tls-auth', 'tls_auth']]) {
    const key = material(top, name, missing)
    if (!key) continue
    const dir = type === 'tls_auth' ? (top.blocks[name] ? keyDir : (arg(name, 1) ?? keyDir)) : undefined
    o.controlWrap = { type, key, direction: dir === '0' ? 'server' : dir === '1' ? 'client' : '' }
    break
  }
  if (o.mode === 'static_key') {
    const dir = top.blocks.secret ? keyDir : (arg('secret', 1) ?? keyDir)
    o.keyDirection = dir === '0' ? 'server' : dir === '1' ? 'client' : ''
  }

  // Data channel.
  o.cipher = arg('cipher') || ''
  const list = arg('data-ciphers') ?? arg('ncp-ciphers')
  o.dataCiphers = list ? list.split(':').map(s => s.trim()).filter(Boolean) : []
  o.dataCiphersFallback = arg('data-ciphers-fallback') || ''
  o.auth = arg('auth') || ''

  // Compression: `compress` alone is framing only (stub); `comp-lzo` alone is adaptive.
  if (has('compress')) {
    const v = String(arg('compress') || 'stub').toLowerCase()
    if (v === 'lzo') o.compressionLzo = 'yes'
    else o.compression = v
  }
  if (has('comp-lzo')) o.compressionLzo = String(arg('comp-lzo') || 'adaptive').toLowerCase()
  if (has('allow-compression')) o.allowCompression = String(arg('allow-compression')).toLowerCase()

  // Credentials.
  o.authUserPass = has('auth-user-pass') || !!top.blocks['auth-user-pass']
  if (top.blocks['auth-user-pass']) {
    const [u, p] = top.blocks['auth-user-pass'].split('\n').map(s => s.trim())
    o.embeddedUser = u || ''
    o.embeddedPass = p || ''
  } else if (has('auth-user-pass') && arg('auth-user-pass') && !INLINE.test(arg('auth-user-pass'))) {
    // A credentials file next to the profile is asked for instead, like the phone does.
    o.credentialsFile = arg('auth-user-pass')
  }
  o.authRetry = arg('auth-retry') || ''
  if (has('static-challenge')) { o.staticChallenge = arg('static-challenge') || ''; o.staticChallengeEcho = arg('static-challenge', 1) === '1' }

  // Server verification.
  if (has('verify-x509-name')) o.verifyX509 = { name: arg('verify-x509-name'), type: String(arg('verify-x509-name', 1) || 'subject').toLowerCase() }
  o.remoteCertTls = arg('remote-cert-tls') || ''
  o.remoteCertKu = (last(d, 'remote-cert-ku') || { args: [] }).args
  o.remoteCertEku = arg('remote-cert-eku') || ''
  o.nsCertType = arg('ns-cert-type') || ''
  o.tlsVersionMin = arg('tls-version-min') || ''
  o.tlsVersionMax = arg('tls-version-max') || ''
  o.tlsCipher = arg('tls-cipher') || ''
  o.tlsGroups = arg('tls-groups') || ''
  o.certProfile = arg('tls-cert-profile') || ''
  const fps = top.blocks['peer-fingerprint'] ? top.blocks['peer-fingerprint'].split('\n') : all(d, 'peer-fingerprint').map(x => x.args[0] || '')
  o.peerFingerprint = fps.map(s => s.trim().replace(/:/g, '').toLowerCase()).filter(s => /^[0-9a-f]{64}$/.test(s))
  if (has('crl-verify') && !INLINE.test(arg('crl-verify') || '')) o.crlPath = arg('crl-verify')

  // Packet sizes and timers.
  o.tunMtu = intArg(arg('tun-mtu')) || 0
  if (has('mssfix')) {
    const n = intArg(arg('mssfix'))
    if (n === 0) o.mssfixDisabled = true
    else if (n) { o.mssfix = n; const m = String(arg('mssfix', 1) || '').toLowerCase(); if (m === 'mtu' || m === 'fixed') o.mssfixMode = m }
  }
  o.fragment = intArg(arg('fragment')) || 0
  if (has('replay-window')) { o.replayWindow = intArg(arg('replay-window')) || 0; o.replayWindowTime = intArg(arg('replay-window', 1)) || 0 }
  const ka = last(d, 'keepalive')
  o.ping = intArg(arg('ping')) ?? (ka ? intArg(ka.args[0]) : null)
  o.pingRestart = intArg(arg('ping-restart')) ?? (ka ? intArg(ka.args[1]) : null)
  o.renegSec = intArg(arg('reneg-sec'))
  o.renegBytes = intArg(arg('reneg-bytes')) || 0
  o.renegPkts = intArg(arg('reneg-pkts')) || 0
  o.tlsTimeout = intArg(arg('tls-timeout')) || 0
  o.handWindow = intArg(arg('hand-window')) || 0
  if (has('explicit-exit-notify')) o.explicitExitNotify = intArg(arg('explicit-exit-notify')) ?? 1

  // Pulled options.
  o.routeNoPull = has('route-nopull')
  o.pullFilters = all(d, 'pull-filter').filter(x => ['accept', 'ignore', 'reject'].includes(String(x.args[0]).toLowerCase()) && x.args[1] !== undefined)
    .map(x => ({ action: x.args[0].toLowerCase(), text: x.args[1] }))
  o.topology = arg('topology') || ''
  o.blockIpv6 = has('block-ipv6')
  const ifc = last(d, 'ifconfig')
  if (ifc && ip4(ifc.args[0]) && ip4(ifc.args[1])) o.ifconfig = { local: ifc.args[0], remote: ifc.args[1] }

  // Reaching the server through a proxy.
  const hp = last(d, 'http-proxy')
  if (hp && hp.args[0]) {
    o.httpProxy = { host: hp.args[0], port: intArg(hp.args[1]) || 8080 }
    const creds = top.blocks['http-proxy-user-pass']
    if (creds) { const [u, p] = creds.split('\n').map(s => s.trim()); o.httpProxy.username = u || ''; o.httpProxy.password = p || '' }
  }
  const sp = last(d, 'socks-proxy')
  if (sp && sp.args[0]) o.socksProxy = { host: sp.args[0], port: intArg(sp.args[1]) || 1080 }
  return o
}

// ------------------------------------------------------------------ ProxyConfig

/** Display name (GhajarUiRules.ovpnDisplayName): "Ghajarvpn" + the flag of the first two-letter country code in the host. */
let regions = null
function ovpnDisplayName(host) {
  if (!regions) { try { regions = new Intl.DisplayNames(['en'], { type: 'region', fallback: 'none' }) } catch { regions = false } }
  const code = String(host || '').toLowerCase().split(/[^a-z]+/)
    .find(p => p.length === 2 && !['eu', 'un', 'ez', 'qo', 'zz'].includes(p) && regions && regions.of(p.toUpperCase()))
  const flag = code ? [...code.toUpperCase()].map(ch => String.fromCodePoint(0x1F1E6 + ch.charCodeAt(0) - 65)).join('') : '🌐'
  return `Ghajarvpn ${flag}`
}

/**
 * An imported .ovpn as a ProxyConfig (null when it is not a client profile
 * with at least one remote). Optional: name, username, password, baseDir
 * (embed the files the profile references), source.
 */
function ovpnToConfig(text, { name = '', username = '', password = '', baseDir = '', source = 'PERSONAL', make } = {}) {
  if (!isOvpn(text)) return null
  const raw = embedFiles(String(text).replace(/^﻿/, ''), baseDir).trim()
  const o = parseOvpn(raw)
  const first = o.remotes[0]
  if (!first) return null
  const fields = {
    name: name || ovpnDisplayName(first.host),
    protocol: 'openvpn',
    address: first.host,
    port: first.port,
    network: first.network.replace(/[46]$/, ''),
    security: 'tls',
    uuid: username || o.embeddedUser || '',
    password: password || o.embeddedPass || '',
    extra: JSON.stringify({ ovpn: raw }),
    source
  }
  return make ? make(fields) : fields
}

/** The raw .ovpn of a config. */
function ovpnOf(config) {
  const x = config && config.extra
  try {
    const o = typeof x === 'string' ? JSON.parse(x || '{}') : (x || {})
    return typeof o.ovpn === 'string' ? o.ovpn : ''
  } catch { return '' }
}

/** Whether the profile asks for a username / password it does not have yet (PendingOpenVpnImport.needsCredentials). */
function needsCredentials(config) {
  const raw = ovpnOf(config)
  if (!raw) return false
  const o = parseOvpn(raw)
  return o.authUserPass && (!String(config.uuid || '').trim() || !String(config.password || '').trim())
}

// ------------------------------------------------------------------ sing-box endpoint

const seconds = n => `${n}s`

/**
 * The sing-box `openvpn-client` endpoint for an OpenVPN ProxyConfig, tagged
 * "proxy", as { endpoint, extraOutbounds } (the second only when the profile
 * reaches its server through an http-proxy / socks-proxy). Throws, with the
 * reason in Persian like the rest of the engine, when the profile cannot run.
 */
function buildOpenvpnSpec(config) {
  const raw = ovpnOf(config)
  if (!raw) throw new Error('متن فایل OVPN در این کانفیگ نیست؛ دوباره ایمپورتش کن')
  const o = parseOvpn(raw)
  if (o.unsupported.length) throw new Error('این فایل OVPN پشتیبانی نمی‌شود: ' + o.unsupported.join('، '))
  if (o.missing.length) throw new Error('فایل‌های این پروفایل داخل OVPN نیستند: ' + o.missing.join('، ') + ' — فایل را همراه پوشه‌اش ایمپورت کن یا گواهی‌ها را داخلش بگذار')
  if (!o.remotes.length) throw new Error('فایل OVPN هیچ remote ندارد')
  if (o.authUserPass && (!config.uuid || !config.password)) throw new Error('این پروفایل OpenVPN نام کاربری و رمز می‌خواهد')

  const e = { type: 'openvpn-client', tag: 'proxy' }
  const net0 = o.remotes[0].network
  e.network = net0
  e.servers = o.remotes.map(r => (r.network === net0 ? { server: r.host, server_port: r.port } : { server: r.host, server_port: r.port, network: r.network }))
  if (o.remoteRandom) e.remote_random = true
  if (o.tunMtu >= 576 && o.tunMtu <= 65535) e.mtu = o.tunMtu
  if (o.topology && ['net30', 'p2p', 'subnet'].includes(o.topology)) e.topology = o.topology

  if (o.mode === 'static_key') {
    e.mode = 'static_key'
    e.static_key = o.staticKey
    if (o.keyDirection) e.key_direction = o.keyDirection
    if (o.cipher) e.cipher = o.cipher.toUpperCase()
    if (!o.ifconfig) throw new Error('پروفایل static-key بدون ifconfig قابل اجرا نیست')
    const { local, remote } = o.ifconfig
    const bits = maskBits(remote)
    if (bits > 0 && remote.startsWith('255.')) {
      e.address = [`${local}/${bits}`]
    } else {
      // p2p: the two ends share a /30 in the usual setups; otherwise a host route.
      e.address = [`${local}/${(ip4num(local) & ~3) === (ip4num(remote) & ~3) ? 30 : 32}`]
      e.peer_address = remote
    }
  } else {
    if (o.authUserPass) { e.username = String(config.uuid); e.password = String(config.password) }
    if (o.authRetry && ['none', 'nointeract', 'interact'].includes(o.authRetry)) e.auth_retry = o.authRetry
    if (o.staticChallenge) { e.static_challenge = o.staticChallenge; if (o.staticChallengeEcho) e.static_challenge_echo = true }

    const tls = {}
    if (!o.ca && !o.peerFingerprint.length) throw new Error('فایل OVPN گواهی CA (<ca>) ندارد')
    if (o.ca) tls.certificate = [o.ca]
    if (o.cert || o.key) {
      if (!o.cert || !o.key) throw new Error('فایل OVPN فقط یکی از <cert> و <key> را دارد')
      tls.client_certificate = [o.extraCerts ? o.cert + '\n' + o.extraCerts : o.cert]
      tls.client_key = [o.key]
    }
    if (o.peerFingerprint.length) tls.peer_fingerprint = o.peerFingerprint
    if (o.crlPath) tls.crl_path = o.crlPath
    if (o.verifyX509 && o.verifyX509.name) {
      tls.server_name = o.verifyX509.name
      tls.server_name_type = ['subject', 'name', 'name-prefix'].includes(o.verifyX509.type) ? o.verifyX509.type : 'subject'
    }
    // OpenVPN checks the server certificate's purpose only when the profile
    // asks for it (remote-cert-tls / remote-cert-eku); sing-box checks it by
    // default, so a profile without either says "none" explicitly.
    if (o.remoteCertEku) tls.remote_certificate_eku = o.remoteCertEku
    else tls.remote_certificate_tls = ['server', 'client'].includes(o.remoteCertTls) ? o.remoteCertTls : 'none'
    if (o.remoteCertKu.length) tls.remote_certificate_ku = o.remoteCertKu
    if (['server', 'client'].includes(o.nsCertType)) tls.ns_certificate_type = o.nsCertType
    const ver = v => (['1.0', '1.1', '1.2', '1.3'].includes(v) ? v : '')
    if (ver(o.tlsVersionMin)) tls.version_min = ver(o.tlsVersionMin)
    if (ver(o.tlsVersionMax)) tls.version_max = ver(o.tlsVersionMax)
    if (o.tlsCipher) tls.cipher = o.tlsCipher
    if (o.tlsGroups) tls.groups = o.tlsGroups
    if (['legacy', 'preferred', 'insecure', 'suiteb'].includes(o.certProfile)) tls.certificate_profile = o.certProfile
    if (o.controlWrap) {
      tls.control_wrap = { type: o.controlWrap.type, key: [o.controlWrap.key] }
      if (o.controlWrap.type === 'tls_auth' && o.controlWrap.direction) tls.control_wrap.direction = o.controlWrap.direction
    }
    e.tls = tls

    // In TLS mode `cipher` only names the fallback for peers without cipher
    // negotiation, and OpenVPN 2.5+ adds it to data-ciphers (sing-box refuses
    // `cipher` outside static-key mode, so it is translated here).
    const ciphers = o.dataCiphers.map(c => c.toUpperCase())
    const cipher = o.cipher.toUpperCase()
    if (cipher && cipher !== 'NONE') {
      const base = ciphers.length ? ciphers : [...DEFAULT_DATA_CIPHERS]
      if (!base.includes(cipher)) base.push(cipher)
      e.data_ciphers = base
      e.data_ciphers_fallback = (o.dataCiphersFallback || cipher).toUpperCase()
    } else {
      if (ciphers.length) e.data_ciphers = ciphers
      if (o.dataCiphersFallback) e.data_ciphers_fallback = o.dataCiphersFallback.toUpperCase()
    }

    if (o.renegSec === 0) e.renegotiate_disabled = true
    else if (o.renegSec) e.renegotiate_interval = seconds(o.renegSec)
    if (o.renegBytes) e.renegotiate_bytes = o.renegBytes
    if (o.renegPkts) e.renegotiate_packets = o.renegPkts
    if (o.tlsTimeout) e.tls_timeout = seconds(o.tlsTimeout)
    if (o.handWindow) e.handshake_window = seconds(o.handWindow)
    if (o.routeNoPull) e.route_no_pull = true
    if (o.pullFilters.length) e.pull_filters = o.pullFilters
  }

  if (o.auth) e.auth = o.auth.toUpperCase()
  const stub = v => !v || ['stub', 'stub-v2', 'none', 'no', 'disabled', 'off'].includes(v)
  if (o.compression) {
    if (!['none', 'no', 'lz4', 'lz4-v2', 'stub', 'stub-v2', 'disabled', 'off'].includes(o.compression)) throw new Error('فشرده‌سازی ' + o.compression + ' پشتیبانی نمی‌شود')
    e.compression = o.compression
  }
  if (o.compressionLzo) e.compression_lzo = ['none', 'no', 'yes', 'adaptive', 'asym', 'disabled', 'off'].includes(o.compressionLzo) ? o.compressionLzo : 'adaptive'
  if (o.allowCompression && ['no', 'asym', 'yes'].includes(o.allowCompression)) e.allow_compression = o.allowCompression
  // A profile that turns compression on cannot start with sing-box's (and
  // OpenVPN 2.6's) default "no": accept what the server compresses, never
  // compress what we send (asym), the safe reading of an old profile.
  if ((!stub(o.compression) || (o.compressionLzo && !stub(o.compressionLzo) && o.compressionLzo !== 'no')) && (!e.allow_compression || e.allow_compression === 'no')) e.allow_compression = 'asym'

  if (o.mssfixDisabled) e.mss_fix_disabled = true
  else if (o.mssfix) { e.mss_fix = o.mssfix; if (o.mssfixMode) e.mss_fix_mode = o.mssfixMode }
  if (o.fragment) {
    if (!o.remotes.every(r => r.network.startsWith('udp'))) throw new Error('fragment فقط با UDP کار می‌کند')
    e.fragment = o.fragment
  }
  if (o.replayWindow) e.replay_window = o.replayWindow
  if (o.replayWindowTime) e.replay_window_time = seconds(o.replayWindowTime)
  if (o.ping) e.ping_interval = seconds(o.ping)
  if (o.pingRestart === 0) e.ping_restart_disabled = true
  else if (o.pingRestart) e.ping_restart = seconds(o.pingRestart)
  if (o.explicitExitNotify) e.explicit_exit_notify = o.explicitExitNotify
  if (o.blockIpv6) e.block_ipv6 = true

  const extraOutbounds = []
  if (o.httpProxy || o.socksProxy) {
    if (o.httpProxy && !o.remotes.every(r => r.network.startsWith('tcp'))) throw new Error('http-proxy فقط با OpenVPN روی TCP کار می‌کند')
    const p = o.httpProxy
      ? { type: 'http', tag: 'ovpn-upstream', server: o.httpProxy.host, server_port: o.httpProxy.port }
      : { type: 'socks', tag: 'ovpn-upstream', server: o.socksProxy.host, server_port: o.socksProxy.port, version: '5' }
    if (o.httpProxy && o.httpProxy.username) { p.username = o.httpProxy.username; p.password = o.httpProxy.password || '' }
    extraOutbounds.push(p)
    e.detour = 'ovpn-upstream'
  }
  return { endpoint: e, extraOutbounds }
}

/** Host names the endpoint dials outside the tunnel (for the TUN's direct DNS rules). */
function remoteHosts(config) {
  try {
    const o = parseOvpn(ovpnOf(config))
    const hosts = o.remotes.map(r => r.host)
    if (o.httpProxy) hosts.push(o.httpProxy.host)
    if (o.socksProxy) hosts.push(o.socksProxy.host)
    return [...new Set(hosts.map(h => h.toLowerCase()))]
  } catch { return [] }
}

module.exports = {
  isOvpn, readOvpn, parseOvpn, embedFiles, ovpnToConfig, ovpnOf, ovpnDisplayName, needsCredentials,
  buildOpenvpnSpec, remoteHosts, words, DEFAULT_DATA_CIPHERS
}
