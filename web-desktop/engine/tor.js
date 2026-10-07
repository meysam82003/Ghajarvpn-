// Tor for the desktop app: a port of the Android TorController / TorBridges
// (app/.../Torcontroller.kt) around the official `tor` executable of the Tor
// Expert Bundle and `lyrebird` (obfs4, meek_lite, webtunnel, snowflake).
//
// As on Android, Tor serves a local SOCKS port and Xray sits in front of it
// (ConfigBuilder.buildTor: a socks outbound to Tor), so the routing rules and
// the TUN / system-proxy modes are the same as for every other server. A Tor
// profile's settings are the phone's:
//   torCountry      exit country (ExitNodes {cc}, StrictNodes 0, needs the geoip files)
//   torThroughVpn   Tor dials its relays through another server (torBaseId):
//                   Socks5Proxy pointing at that server's local port
//   extra.pt / extra.bridges   bridge lines (TorBridges.from)
//
// Layout in the core directory (scripts/fetch-tor.sh): <core>/tor/tor[.exe]
// with its libraries, <core>/tor/pluggable_transports/lyrebird[.exe] and
// <core>/tor/geoip, geoip6. A flat <core>/tor[.exe] + <core>/lyrebird[.exe]
// is accepted too (a system tor in development).
//
// No dependencies beyond Node's standard library.
'use strict'

const fs = require('fs')
const path = require('path')

const READY_TIMEOUT_MS = 120000
const TRANSPORTS = ['obfs4', 'meek_lite', 'webtunnel', 'snowflake', 'vanilla']

/** Snowflake's own default bridges, as TorBridges.SNOWFLAKE_DEFAULT (snowflake v2.14.1 client/torrc). */
const SNOWFLAKE_DEFAULT = [
  'snowflake 192.0.2.3:80 2B280B23E1107BB62ABFC40DDCC8824814F80A72 fingerprint=2B280B23E1107BB62ABFC40DDCC8824814F80A72 url=https://1098762253.rsc.cdn77.org/ fronts=www.cdn77.com,www.phpmyadmin.net ice=stun:stun.antisip.com:3478,stun:stun.epygi.com:3478,stun:stun.uls.co.za:3478,stun:stun.voipgate.com:3478,stun:stun.mixvoip.com:3478,stun:stun.nextcloud.com:3478,stun:stun.bethesda.net:3478,stun:stun.nextcloud.com:443 utls-imitate=hellorandomizedalpn',
  'snowflake 192.0.2.4:80 8838024498816A039FCBBAB14E6F40A0843051FA fingerprint=8838024498816A039FCBBAB14E6F40A0843051FA url=https://1098762253.rsc.cdn77.org/ fronts=www.cdn77.com,www.phpmyadmin.net ice=stun:stun.antisip.com:3478,stun:stun.epygi.com:3478,stun:stun.uls.co.za:3478,stun:stun.voipgate.com:3478,stun:stun.mixvoip.com:3478,stun:stun.nextcloud.com:3478,stun:stun.bethesda.net:3478,stun:stun.nextcloud.com:443 utls-imitate=hellorandomizedalpn'
]

/** TorController.Countries: exit countries the app offers ('' = automatic). */
const COUNTRIES = [
  ['', 'Automatic'], ['us', 'United States'], ['de', 'Germany'], ['nl', 'Netherlands'], ['fr', 'France'],
  ['gb', 'United Kingdom'], ['se', 'Sweden'], ['ch', 'Switzerland'], ['fi', 'Finland'], ['ro', 'Romania'],
  ['at', 'Austria'], ['ca', 'Canada'], ['jp', 'Japan'], ['sg', 'Singapore'], ['au', 'Australia'], ['in', 'India'],
  ['br', 'Brazil'], ['id', 'Indonesia'], ['th', 'Thailand'], ['ua', 'Ukraine'], ['es', 'Spain'], ['it', 'Italy'],
  ['pl', 'Poland'], ['cz', 'Czechia'], ['no', 'Norway'], ['dk', 'Denmark'], ['be', 'Belgium'], ['ie', 'Ireland'],
  ['tr', 'Turkey'], ['za', 'South Africa'], ['ru', 'Russia'], ['kr', 'South Korea'], ['az', 'Azerbaijan'],
  ['mx', 'Mexico'], ['cn', 'China'], ['eg', 'Egypt'], ['il', 'Israel']
]

const exe = (name, platform) => (platform === 'win32' ? name + '.exe' : name)
const isFile = f => { try { return fs.statSync(f).isFile() } catch { return false } }

/**
 * Where tor, its pluggable transports and geoip files are in binDir, or
 * null when there is no tor. { tor, dir, ptDir, lyrebird, snowflake, geoip, geoip6 }
 * (a missing optional file is '').
 */
function torLayout(binDir, platform = process.platform) {
  const first = list => list.find(isFile) || ''
  const tor = first([path.join(binDir, 'tor', exe('tor', platform)), path.join(binDir, exe('tor', platform))])
  if (!tor) return null
  const dir = path.dirname(tor)
  const ptDirs = [path.join(dir, 'pluggable_transports'), path.join(dir, 'PluggableTransports'), dir, binDir]
  const lyrebird = first(ptDirs.map(d => path.join(d, exe('lyrebird', platform))))
  // Older bundles ship snowflake as its own program; newer lyrebird carries it.
  const snowflake = first(ptDirs.map(d => path.join(d, exe('snowflake-client', platform))))
  const geoDirs = [dir, path.join(dir, 'data'), path.join(path.dirname(dir), 'data'), binDir]
  return {
    tor, dir, lyrebird, snowflake,
    geoip: first(geoDirs.map(d => path.join(d, 'geoip'))),
    geoip6: first(geoDirs.map(d => path.join(d, 'geoip6')))
  }
}

/** TorBridges.from: { transport, lines } of a Tor profile; transport '' when no bridge is used. */
function torBridges(config) {
  let x = {}
  try { x = typeof config.extra === 'string' ? JSON.parse(config.extra || '{}') : (config.extra || {}) } catch { x = {} }
  const lines = String(x.bridges || '').split(/\r\n|\n|\r/).map(l => l.trim().replace(/^Bridge /, '').trim()).filter(l => l && !l.startsWith('#'))
  const declared = String(x.pt || '').toLowerCase()
  const firstPt = lines.length ? lines[0].split(' ')[0].toLowerCase() : ''
  const transport = declared || (TRANSPORTS.includes(firstPt) ? firstPt : lines.length ? 'vanilla' : '')
  if (transport === 'snowflake' && !lines.length) return { transport: 'snowflake', lines: [...SNOWFLAKE_DEFAULT] }
  return lines.length ? { transport, lines } : { transport: '', lines: [] }
}

/** A torrc value: quoted (C escapes) when it has blanks, quotes or backslashes. */
function torValue(v) {
  const s = String(v)
  return /[\s"\\#]/.test(s) ? '"' + s.replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"' : s
}

/**
 * TorController.writeTorrc for the desktop. Options:
 *   socksPort      Tor's SOCKS port (a free one, not the phone's fixed 9150)
 *   dataDir        DataDirectory (CacheDirectory is <dataDir>/cache)
 *   country        exit country (two letters), used when geoip + geoip6 are given
 *   geoip, geoip6  Tor's geoip files
 *   upstreamPort   Socks5Proxy 127.0.0.1:<port> (torThroughVpn)
 *   bridges        torBridges(config)
 *   ptExec         { lyrebird, snowflake }: the exec commands, relative to tor's
 *                  working directory (see torPlan: torrc cannot quote them)
 *   extra          more torrc lines (advanced setting torrcExtra)
 */
function buildTorrc({ socksPort, dataDir, country = '', geoip = '', geoip6 = '', upstreamPort = 0, bridges = { transport: '', lines: [] }, ptExec = {}, extra = '' }) {
  const lines = [
    `SocksPort 127.0.0.1:${socksPort}`,
    `DataDirectory ${torValue(dataDir)}`,
    `CacheDirectory ${torValue(path.join(dataDir, 'cache'))}`,
    'AvoidDiskWrites 1',
    'Log notice stdout',
    'ClientOnly 1'
  ]
  const cc = String(country || '').trim().toLowerCase()
  if (/^[a-z]{2}$/.test(cc) && geoip && geoip6) {
    lines.push(`GeoIPFile ${torValue(geoip)}`, `GeoIPv6File ${torValue(geoip6)}`, `ExitNodes {${cc}}`, 'StrictNodes 0')
  } else if (geoip && geoip6) {
    lines.push(`GeoIPFile ${torValue(geoip)}`, `GeoIPv6File ${torValue(geoip6)}`)
  }
  if (upstreamPort) lines.push(`Socks5Proxy 127.0.0.1:${upstreamPort}`)
  if (bridges.transport && bridges.lines.length) {
    lines.push('UseBridges 1')
    if (bridges.transport !== 'vanilla') {
      if (ptExec.snowflake) {
        lines.push(`ClientTransportPlugin obfs4,meek_lite,webtunnel exec ${ptExec.lyrebird}`)
        lines.push(`ClientTransportPlugin snowflake exec ${ptExec.snowflake}`)
      } else {
        lines.push(`ClientTransportPlugin obfs4,meek_lite,webtunnel,snowflake exec ${ptExec.lyrebird}`)
      }
    }
    for (const b of bridges.lines) lines.push('Bridge ' + b.replace(/^Bridge /, '').trim())
  }
  for (const l of String(extra || '').split(/\r\n|\n|\r/).map(s => s.trim()).filter(s => s && !s.startsWith('#'))) lines.push(l)
  return lines.join('\n') + '\n'
}

/**
 * Everything needed to start tor for a profile: { binary, args, cwd, env,
 * torrc, torrcPath }. Throws (in Persian, like the engine) when a bridge
 * needs lyrebird and it is missing. ClientTransportPlugin cannot quote a
 * path with spaces ("Application Support", "Program Files"), so tor runs in
 * the transports' directory and execs them by relative path, the way Tor
 * Browser's own torrc-defaults does.
 */
function torPlan(config, { layout, socksPort, dataDir, upstreamPort = 0, extra = '', platform = process.platform }) {
  if (!layout) throw new Error('Tor در این نسخه نیست')
  const bridges = torBridges(config)
  const needsPt = bridges.transport && bridges.transport !== 'vanilla'
  const usesSnowflake = needsPt && bridges.lines.some(l => /^snowflake\s/i.test(l))
  if (needsPt && !layout.lyrebird && !(usesSnowflake && layout.snowflake && bridges.lines.every(l => /^snowflake\s/i.test(l)))) {
    throw new Error('پل‌های Tor به lyrebird نیاز دارند که در این نسخه نیست')
  }
  const ptDir = path.dirname(layout.lyrebird || layout.snowflake || layout.tor)
  const rel = f => (platform === 'win32' ? '.\\' : './') + path.basename(f)
  const ptExec = needsPt ? { lyrebird: rel(layout.lyrebird || layout.snowflake), snowflake: layout.snowflake ? rel(layout.snowflake) : '' } : {}
  const torrc = buildTorrc({
    socksPort, dataDir, country: config.torCountry, geoip: layout.geoip, geoip6: layout.geoip6,
    upstreamPort, bridges, ptExec, extra
  })
  const torrcPath = path.join(dataDir, 'torrc')
  const env = { HOME: dataDir }
  // The Linux bundle's tor sits next to its libevent / OpenSSL; macOS's next to libevent.
  if (platform === 'linux') env.LD_LIBRARY_PATH = [layout.dir, process.env.LD_LIBRARY_PATH].filter(Boolean).join(':')
  if (platform === 'darwin') env.DYLD_LIBRARY_PATH = [layout.dir, process.env.DYLD_LIBRARY_PATH].filter(Boolean).join(':')
  return { binary: layout.tor, args: ['-f', torrcPath], cwd: ptDir, env, torrc, torrcPath, bridges }
}

/** "… Bootstrapped 45% (requesting_descriptors): …" → 45, else null. */
function bootstrapPercent(line) {
  const m = /Bootstrapped (\d{1,3})%/.exec(String(line))
  return m ? parseInt(m[1], 10) : null
}

/** A tor log line that means it cannot go on (bad torrc, port taken, …). */
function fatalLine(line) {
  const s = String(line)
  return /\[(err)\]/.test(s) || /Failed to parse\/validate config/.test(s) ? s.replace(/^.*\[(err|warn)\]\s*/, '') : ''
}

module.exports = { READY_TIMEOUT_MS, TRANSPORTS, SNOWFLAKE_DEFAULT, COUNTRIES, torLayout, torBridges, buildTorrc, torPlan, torValue, bootstrapPercent, fatalLine }
