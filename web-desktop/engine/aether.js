// Aether for the desktop app: a port of the Android AetherController /
// AetherSpec (app/.../Aethercontroller.kt) and OblivionOptions.kt around the
// `aether` executable (CluvexStudio/Aether 2.1.0, native/Aether, AGPL-3.0,
// run unmodified as a separate program; scripts/build-aether-desktop.sh).
//
// Aether finds a reachable Cloudflare WARP gateway (MASQUE over HTTP/3 or
// HTTP/2, WireGuard, WARP-in-WARP "gool", MASQUE-in-MASQUE "mim"), opens the
// tunnel and serves it as a local SOCKS5 proxy. As on Android, Xray sits in
// front of that SOCKS port (ConfigBuilder.buildAether) so routing and the
// TUN / system-proxy modes work as for every other server.
//
// Which profiles run Aether (EngineRouting.engineFor): protocol "aether",
// and a Psiphon profile whose oblivionJson says core "aether" or "chain"
// (Aether, then Psiphon dialled through it: manager.js starts both).
//
// No dependencies beyond Node's standard library.
'use strict'

const path = require('path')

const READY_TIMEOUT_MS = 180000

/** OblivionOptions.defaults. */
const OBLIVION_DEFAULTS = Object.freeze({
  core: 'psiphon', protocol: 'masque', transport: 'h3', scanMode: 'balanced',
  obfuscation: 'balanced', ipVersion: 'v4', logLevel: 'info', perfProfile: '', echMode: '',
  socksPort: '1819', allowLan: 'false', routingMode: 'vpn', mtu: '1500', overrideDns: 'true',
  dnsPrimary: '1.1.1.1', dnsSecondary: '1.0.0.1', fragment: 'false', fragmentSize: '16-32',
  fragmentDelay: '2-10', quickReconnect: 'true', dataCheck: 'true', validateSeconds: '10',
  reconnectSeconds: '2', wgKeepalive: '5', wgProfileRetry: 'true', gatewayProxy: 'false', bypassSelected: 'false'
})

/** OblivionOptions(raw): text / flag / number with the Kotlin defaults. */
function oblivion(raw) {
  let data = {}
  try { data = raw && String(raw).trim() ? JSON.parse(raw) : {} } catch { data = {} }
  if (!data || typeof data !== 'object' || Array.isArray(data)) data = {}
  const text = k => (data[k] !== undefined && data[k] !== null ? String(data[k]) : (OBLIVION_DEFAULTS[k] || ''))
  const flag = k => text(k) === 'true'
  const number = k => { const n = parseInt(text(k), 10); return Number.isFinite(n) && /^-?\d+$/.test(text(k)) ? n : (parseInt(OBLIVION_DEFAULTS[k], 10) || 0) }
  const core = text('core')
  return { text, flag, number, core, aether: core === 'aether' || core === 'chain', psiphon: core !== 'aether', chain: core === 'chain' }
}

/** AetherSpec.from: the Aether settings of a profile, or null when it does not run Aether. */
function aetherSpec(config) {
  const c = config || {}
  const ob = oblivion(c.oblivionJson)
  if (c.protocol !== 'aether' && !(c.protocol === 'psiphon' && ob.aether)) return null
  return {
    mode: String(c.aetherMode || '').trim() || 'masque',
    scan: String(c.aetherScan || '').trim() || 'balanced',
    noise: String(c.aetherNoise || ''),
    http2: c.aetherHttp2 === true || c.aetherHttp2 === 'true',
    ipv6: c.aetherIpv6 === true || c.aetherIpv6 === 'true',
    oblivionJson: String(c.oblivionJson || ''),
    exitLoc: String(c.aetherExitLoc || ''),
    fragment: c.aetherFragment === true || c.aetherFragment === 'true'
  }
}

/**
 * OblivionOptions.aetherArgs, with the listen address given by the desktop
 * engine (a free local port; the HTTP proxy on the next one, as on the phone).
 * allowLan is not honoured: Aether's port is internal here, the one the user
 * sees is Xray's.
 */
function oblivionArgs(ob, port) {
  const host = '127.0.0.1'
  const out = ['--bind', `${host}:${port}`, '--http-proxy', `${host}:${port + 1}`,
    '--scan', ob.text('scanMode'), '--noize', ob.text('obfuscation'), '--log-level', ob.text('logLevel'),
    '--ip', ob.text('ipVersion'), '--validate-secs', ob.text('validateSeconds'), '--reconnect-secs', ob.text('reconnectSeconds')]
  const protocol = ob.text('protocol')
  out.push(protocol === 'wg' ? '--wg' : protocol === 'gool' ? '--gool' : '--masque')
  const option = (key, arg) => { if (ob.text(key).trim()) out.push(arg, ob.text(key).trim()) }
  if (protocol === 'masque' && ob.text('transport') === 'h2') {
    out.push('--h2'); option('h2Endpoint', '--h2-peer')
    if (ob.flag('fragment')) { out.push('--fragment'); option('fragmentSize', '--fragment-size'); option('fragmentDelay', '--fragment-delay') }
  }
  if (protocol === 'masque') option('echMode', '--ech')
  if (protocol === 'wg' || protocol === 'gool') {
    option('wgKeepalive', '--keepalive'); if (!ob.flag('wgProfileRetry')) out.push('--no-profile-retry')
    if (protocol === 'wg') option('wgEndpoint', '--wg-peer')
  }
  if (protocol === 'gool') {
    option('wiwOuter', '--wiw-outer'); option('wiwInner', '--wiw-inner')
    if (!ob.text('wiwOuter').trim() && !ob.text('wiwInner').trim()) out.push('--wiw-scan')
  } else option('endpoint', '--peer')
  if (ob.flag('overrideDns')) {
    const dns = [ob.text('dnsPrimary'), ob.text('dnsSecondary')].filter(s => s.trim())
    if (dns.length) out.push('--dns', dns.join(','))
  }
  option('tlsGroups', '--tls-groups'); option('perfProfile', '--perf')
  if (!ob.flag('dataCheck')) out.push('--no-data-check')
  out.push(ob.flag('quickReconnect') ? '--quick-reconnect' : '--no-quick-reconnect')
  for (const [key, arg] of [['routeBlock', '--route-block'], ['routeDirect', '--route-direct']]) {
    const rules = ob.text(key).split(/[\s,;]+/).filter(Boolean)
    if (rules.length) out.push(arg, rules.join(','))
  }
  if (ob.text('team').trim()) {
    option('team', '--team')
    if (ob.text('accessToken').trim()) option('accessToken', '--access-token')
    else if (ob.text('accessId').trim() && ob.text('accessSecret').trim()) { option('accessId', '--access-id'); option('accessSecret', '--access-secret') }
    else option('accessEmail', '--access-email')
    if (ob.flag('gatewayProxy')) out.push('--gateway')
  }
  return out
}

/** AetherController.args: the command line for a profile, listening on 127.0.0.1:port. */
function aetherArgs(spec, port) {
  if (spec.oblivionJson.trim()) return oblivionArgs(oblivion(spec.oblivionJson), port)
  const out = ['--bind', `127.0.0.1:${port}`]
  out.push(spec.mode === 'wg' ? '--wg' : spec.mode === 'gool' ? '--gool' : spec.mode === 'mim' ? '--mim' : '--masque')
  if (spec.http2) out.push('--h2')
  if (spec.fragment && spec.http2) out.push('--fragment')
  if (spec.exitLoc.trim()) out.push('--exit-loc', spec.exitLoc.trim())
  out.push('--scan', spec.scan || 'balanced')
  if (spec.noise.trim()) out.push('--noize', spec.noise)
  out.push('--quick-reconnect')
  out.push(spec.ipv6 ? '-6' : '-4')
  return out
}

/**
 * AetherController.env: where Aether keeps its WARP identities (workDir, kept
 * between runs like the phone's filesDir/aether) and, without Oblivion
 * options, the same choices as variables.
 */
function aetherEnv(spec, { workDir, tmpDir, port }) {
  const env = {
    AETHER_SOCKS: `127.0.0.1:${port}`,
    AETHER_PROTOCOL: spec.mode || 'masque',
    AETHER_SCAN: spec.scan || 'balanced',
    AETHER_IP: spec.ipv6 ? 'both' : '4',
    AETHER_QUICK_RECONNECT: '1',
    AETHER_CONFIG: path.join(workDir, 'aether.toml'),
    AETHER_MASQUE_CONFIG: path.join(workDir, 'aether-masque.toml'),
    AETHER_WG_CONFIG: path.join(workDir, 'aether-wg.toml'),
    HOME: workDir,
    TMPDIR: tmpDir
  }
  if (spec.oblivionJson.trim()) {
    for (const k of ['AETHER_PROTOCOL', 'AETHER_SCAN', 'AETHER_IP', 'AETHER_QUICK_RECONNECT']) delete env[k]
    return env
  }
  if (spec.http2) env.AETHER_MASQUE_HTTP2 = '1'
  if (spec.fragment && spec.http2) env.AETHER_MASQUE_H2_FRAGMENT = '1'
  if (spec.exitLoc.trim()) env.AETHER_EXIT_LOC = spec.exitLoc.trim()
  if (spec.noise.trim()) env.AETHER_NOIZE = spec.noise
  return env
}

module.exports = { READY_TIMEOUT_MS, OBLIVION_DEFAULTS, oblivion, aetherSpec, aetherArgs, aetherEnv, oblivionArgs }
