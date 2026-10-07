'use strict'
/**
 * Config parsing for the desktop app: a port of the Android app's
 *   ConfigParser.kt          share links, Xray JSON, WireGuard .conf, Tor bridges
 *   ForeignImport.kt         sing-box JSON, Clash / Clash Meta / Mihomo YAML (MiniYaml)
 *   SubscriptionFetcher.kt   subscription bodies (base64 or plain lists)
 *   ConfigShare.kt           share links back (toShareLink)
 *   ProxyConfig.kt           the config object, its defaults and JSON form
 *   engine/CoreManager.kt    EngineRouting.engineFor (protocolFamily)
 *
 * The aim is that the desktop app accepts exactly the links and files the
 * phone does, and produces the same fields from them. Where the Kotlin
 * relies on a JVM / Android library behaviour (URLDecoder, java.util.Base64,
 * android.util.Base64, org.json, Integer.parseInt) the helper below
 * reproduces that behaviour instead of using the nearest JS built-in, because
 * the built-ins differ on exactly the malformed input that subscriptions
 * contain. No dependencies; every public entry point returns null / [] on
 * bad input and never throws.
 */

const crypto = require('crypto')
const openvpn = require('./openvpn.js')

// ======================================================================
// ProxyConfig (ProxyConfig.kt)
// ======================================================================

const SOURCES = ['PERSONAL', 'COMMUNITY', 'PREMIUM']

/** Field defaults of the ProxyConfig constructor (not of fromJson: see below). */
const FIELD_DEFAULTS = Object.freeze({
  name: '', protocol: '', address: '', port: 0,
  uuid: '', password: '', method: '', alterId: 0, encryption: 'none', flow: '',
  network: 'tcp', security: 'none', sni: '', publicKey: '', shortId: '', fingerprint: 'chrome',
  path: '', host: '', serviceName: '', mode: '', alpn: '', headerType: '', subId: '',
  privateKey: '', localAddress: '', mtu: 0, reserved: '',
  aetherMode: 'masque', aetherScan: 'balanced', aetherNoise: '', aetherHttp2: true,
  aetherExitLoc: '', aetherFragment: false, aetherIpv6: false,
  hyObfs: '', hyObfsPassword: '', hyUpMbps: 0, hyDownMbps: 0,
  allowInsecure: false, pinnedCertSha256: '', cipherSuites: '', randomSubdomain: false,
  maskType: '', maskDomain: '', maskPassword: '', echConfigList: '',
  torCountry: '', torThroughVpn: false, torBaseId: '', chainId: '',
  psiphonMode: 'auto', psiphonCountry: '', psiphonCdnIps: '', psiphonCdnSni: '',
  oblivionJson: '', extra: '', source: 'PERSONAL', locked: false, favorite: false
})

/** Key order of ProxyConfig.toJson(). */
const JSON_KEYS = [
  'id', 'name', 'protocol', 'address', 'port', 'uuid', 'password', 'method', 'alterId',
  'encryption', 'flow', 'network', 'security', 'sni', 'publicKey', 'shortId', 'fingerprint',
  'path', 'host', 'serviceName', 'mode', 'alpn', 'source', 'headerType', 'subId',
  'privateKey', 'localAddress', 'mtu', 'reserved', 'locked', 'favorite',
  'aetherMode', 'aetherScan', 'aetherNoise', 'aetherHttp2', 'aetherExitLoc', 'aetherFragment',
  'aetherIpv6', 'oblivionJson', 'hyObfs', 'hyObfsPassword', 'hyUpMbps', 'hyDownMbps',
  'allowInsecure', 'pinnedCertSha256', 'cipherSuites', 'randomSubdomain',
  'maskType', 'maskDomain', 'maskPassword', 'echConfigList',
  'torCountry', 'torThroughVpn', 'torBaseId', 'chainId',
  'psiphonMode', 'psiphonCountry', 'psiphonCdnIps', 'psiphonCdnSni', 'extra'
]

/** A fresh config with every field at its constructor default and a new id. */
function defaults() {
  return { ...FIELD_DEFAULTS, id: crypto.randomUUID() }
}

/** ProxyConfig(...) with named arguments: defaults, then the given fields. */
function make(fields) {
  return Object.assign(defaults(), fields)
}

/** ProxyConfig.toJson(): a plain object with the Kotlin key order. */
function toJson(c) {
  const out = {}
  const src = c || {}
  for (const k of JSON_KEYS) {
    out[k] = src[k] !== undefined ? src[k] : (k === 'id' ? crypto.randomUUID() : FIELD_DEFAULTS[k])
  }
  return out
}

/**
 * ProxyConfig.fromJson(). Accepts an object or a JSON string; null when the
 * string is not a JSON object.
 *
 * Kotlin quirk kept: aetherHttp2 defaults to false here although the
 * constructor default is true.
 */
function fromJson(input) {
  let o = input
  if (typeof input === 'string') {
    try { o = parseJsonLenient(input) } catch { return null }
  }
  if (!isObj(o)) return null
  const src = optString(o, 'source', 'PERSONAL')
  return {
    name: optString(o, 'name'),
    protocol: optString(o, 'protocol'),
    address: optString(o, 'address'),
    port: optInt(o, 'port'),
    uuid: optString(o, 'uuid', ''),
    password: optString(o, 'password', ''),
    method: optString(o, 'method', ''),
    alterId: optInt(o, 'alterId', 0),
    encryption: optString(o, 'encryption', 'none'),
    flow: optString(o, 'flow', ''),
    network: optString(o, 'network', 'tcp'),
    security: optString(o, 'security', 'none'),
    sni: optString(o, 'sni', ''),
    publicKey: optString(o, 'publicKey', ''),
    shortId: optString(o, 'shortId', ''),
    fingerprint: optString(o, 'fingerprint', 'chrome'),
    path: optString(o, 'path', ''),
    host: optString(o, 'host', ''),
    serviceName: optString(o, 'serviceName', ''),
    mode: optString(o, 'mode', ''),
    alpn: optString(o, 'alpn', ''),
    headerType: optString(o, 'headerType', ''),
    subId: optString(o, 'subId', ''),
    privateKey: optString(o, 'privateKey', ''),
    localAddress: optString(o, 'localAddress', ''),
    mtu: optInt(o, 'mtu', 0),
    reserved: optString(o, 'reserved', ''),
    aetherMode: optString(o, 'aetherMode', 'masque'),
    aetherScan: optString(o, 'aetherScan', 'balanced'),
    aetherNoise: optString(o, 'aetherNoise', ''),
    aetherHttp2: optBool(o, 'aetherHttp2', false),
    aetherExitLoc: optString(o, 'aetherExitLoc', ''),
    aetherFragment: optBool(o, 'aetherFragment', false),
    aetherIpv6: optBool(o, 'aetherIpv6', false),
    oblivionJson: optString(o, 'oblivionJson', ''),
    hyObfs: optString(o, 'hyObfs', ''),
    hyObfsPassword: optString(o, 'hyObfsPassword', ''),
    hyUpMbps: optInt(o, 'hyUpMbps', 0),
    hyDownMbps: optInt(o, 'hyDownMbps', 0),
    allowInsecure: optBool(o, 'allowInsecure', false),
    pinnedCertSha256: optString(o, 'pinnedCertSha256', ''),
    cipherSuites: optString(o, 'cipherSuites', ''),
    randomSubdomain: optBool(o, 'randomSubdomain', false),
    maskType: optString(o, 'maskType', ''),
    maskDomain: optString(o, 'maskDomain', ''),
    maskPassword: optString(o, 'maskPassword', ''),
    echConfigList: optString(o, 'echConfigList', ''),
    torCountry: optString(o, 'torCountry', ''),
    torThroughVpn: optBool(o, 'torThroughVpn', false),
    torBaseId: optString(o, 'torBaseId', ''),
    chainId: optString(o, 'chainId', ''),
    psiphonMode: optString(o, 'psiphonMode', 'auto'),
    psiphonCountry: optString(o, 'psiphonCountry', ''),
    psiphonCdnIps: optString(o, 'psiphonCdnIps', ''),
    psiphonCdnSni: optString(o, 'psiphonCdnSni', ''),
    extra: optString(o, 'extra', ''),
    // ConfigSource.valueOf falls back to PERSONAL for anything unknown.
    source: SOURCES.includes(src) ? src : 'PERSONAL',
    locked: optBool(o, 'locked', false),
    favorite: optBool(o, 'favorite', false),
    id: optString(o, 'id', crypto.randomUUID())
  }
}

/** ProxyConfig.extraJson(): [extra] as an object; {} when blank or unreadable. */
function extraJson(c) {
  const raw = c && typeof c.extra === 'string' ? c.extra : ''
  if (isBlank(raw)) return {}
  try {
    const v = parseJsonLenient(raw)
    return isObj(v) ? v : {}
  } catch { return {} }
}

// ======================================================================
// Kotlin / JVM string semantics
// ======================================================================

/** Char.isWhitespace on the JVM: Character.isWhitespace || isSpaceChar (no BOM, unlike JS \s). */
function isKtWs(ch) {
  return ch !== '\uFEFF' && (/\s/.test(ch) || (ch >= '\u001C' && ch <= '\u001F'))
}
function ktTrimStart(s) { let i = 0; while (i < s.length && isKtWs(s[i])) i++; return s.substring(i) }
function ktTrimEnd(s) { let j = s.length; while (j > 0 && isKtWs(s[j - 1])) j--; return s.substring(0, j) }
function ktTrim(s) { return ktTrimEnd(ktTrimStart(s)) }
function isBlank(s) { for (const ch of s) if (!isKtWs(ch)) return false; return true }
/** String.trim(vararg chars). */
function trimChars(s, chars) {
  let i = 0; let j = s.length
  while (i < j && chars.includes(s[i])) i++
  while (j > i && chars.includes(s[j - 1])) j--
  return s.substring(i, j)
}
function trimEndChars(s, chars) { let j = s.length; while (j > 0 && chars.includes(s[j - 1])) j--; return s.substring(0, j) }
/** String.lines(): \r\n, \n or \r. */
function ktLines(s) { return s.split(/\r\n|\n|\r/) }

const orEmpty = v => (v == null ? '' : v)
const ifEmpty = (v, d) => (v === '' ? d : v)
const ifBlank = (v, d) => (isBlank(v) ? d : v)
function before(s, d, missing = s) { const i = s.indexOf(d); return i < 0 ? missing : s.substring(0, i) }
function after(s, d, missing = s) { const i = s.indexOf(d); return i < 0 ? missing : s.substring(i + d.length) }
function beforeLast(s, d, missing = s) { const i = s.lastIndexOf(d); return i < 0 ? missing : s.substring(0, i) }
function afterLast(s, d, missing = s) { const i = s.lastIndexOf(d); return i < 0 ? missing : s.substring(i + d.length) }
function removePrefix(s, p) { return s.startsWith(p) ? s.substring(p.length) : s }

/** Kotlin String.substring: throws where JS would silently clamp or swap. */
function sub(s, a, b = s.length) {
  if (a < 0 || b > s.length || a > b) throw new RangeError(`substring(${a}, ${b}) of length ${s.length}`)
  return s.substring(a, b)
}

// Zeros of the Unicode decimal-digit blocks Character.digit() understands;
// Persian / Arabic-Indic digits in a port field parse on the phone too.
const DIGIT_ZEROS = [0x30, 0x660, 0x6F0, 0x7C0, 0x966, 0x9E6, 0xA66, 0xAE6, 0xB66, 0xBE6, 0xC66,
  0xCE6, 0xD66, 0xDE6, 0xE50, 0xED0, 0xF20, 0x1040, 0x1090, 0x17E0, 0x1810, 0x1946, 0x19D0, 0xFF10]
function digitOf(ch) {
  const c = ch.charCodeAt(0)
  for (const z of DIGIT_ZEROS) if (c >= z && c <= z + 9) return c - z
  return -1
}

/** String.toIntOrNull() / Integer.parseInt: optional sign, Unicode digits, Int range. */
function toIntOrNull(s) {
  if (typeof s !== 'string' || s.length === 0) return null
  let i = 0; let neg = false
  if (s[0] === '-' || s[0] === '+') {
    if (s.length === 1) return null
    neg = s[0] === '-'; i = 1
  }
  let v = 0
  for (; i < s.length; i++) {
    const d = digitOf(s[i])
    if (d < 0) return null
    v = v * 10 + d
    if (v > 2147483648) return null
  }
  if (neg) v = -v
  if (v > 2147483647 || v < -2147483648) return null
  return v === 0 ? 0 : v // never -0
}
/** String.toInt(): like toIntOrNull but throws. */
function toInt(s) {
  const v = toIntOrNull(s)
  if (v === null) throw new RangeError(`not an int: ${s}`)
  return v
}

// ======================================================================
// URL encoding (java.net.URLDecoder / URLEncoder)
// ======================================================================

const utf8 = new TextDecoder('utf-8', { ignoreBOM: true })

function hexVal(ch) {
  const c = ch.charCodeAt(0)
  if (c >= 48 && c <= 57) return c - 48
  if (c >= 65 && c <= 70) return c - 55
  if (c >= 97 && c <= 102) return c - 87
  return -1
}

/**
 * URLDecoder.decode(s, "UTF-8"): '+' is a space, runs of %XX are UTF-8
 * (malformed bytes become U+FFFD), and a bad or truncated escape throws.
 */
function javaUrlDecode(s) {
  let out = ''
  let i = 0
  const n = s.length
  while (i < n) {
    let c = s[i]
    if (c === '+') { out += ' '; i++; continue }
    if (c !== '%') { out += c; i++; continue }
    const bytes = []
    while (i + 2 < n && c === '%') {
      // Integer.parseInt(s, i+1, i+3, 16): a sign is accepted, a negative value is not.
      const a = s[i + 1]; const b = s[i + 2]
      let v
      if (a === '+' || a === '-') {
        const d = hexVal(b)
        if (d < 0) throw new Error('URLDecoder: Illegal hex characters in escape (%) pattern')
        v = a === '-' ? -d : d
      } else {
        const h = hexVal(a); const l = hexVal(b)
        if (h < 0 || l < 0) throw new Error('URLDecoder: Illegal hex characters in escape (%) pattern')
        v = h * 16 + l
      }
      if (v < 0) throw new Error('URLDecoder: Illegal hex characters in escape (%) pattern - negative value')
      bytes.push(v)
      i += 3
      if (i < n) c = s[i]
    }
    if (i < n && c === '%') throw new Error('URLDecoder: Incomplete trailing escape (%) pattern')
    out += utf8.decode(Uint8Array.from(bytes))
  }
  return out
}

/** ConfigParser.formDecode: URLDecoder, or the input unchanged when that throws. */
function formDecode(s) { try { return javaUrlDecode(s) } catch { return s } }
/** ConfigParser.pctDecode: like formDecode but a literal '+' stays '+'. */
function pctDecode(s) { try { return javaUrlDecode(s.split('+').join('%2B')) } catch { return s } }

/** URLEncoder.encode(s, "UTF-8"): unreserved A-Za-z0-9 . - * _, space as '+'. */
function javaUrlEncode(s) {
  // A lone surrogate is encoded as '?' by the JVM; encodeURIComponent would throw.
  const safe = String(s).replace(/[\uD800-\uDBFF](?![\uDC00-\uDFFF])|(?<![\uD800-\uDBFF])[\uDC00-\uDFFF]/g, '?')
  return encodeURIComponent(safe)
    .replace(/[!'()~]/g, ch => '%' + ch.charCodeAt(0).toString(16).toUpperCase())
    .replace(/%2A/g, '*')
    .replace(/%20/g, '+')
}

// ======================================================================
// Base64 (java.util.Base64 strict decoders, android.util.Base64 lenient one)
// ======================================================================

const B64_STD = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'
const B64_URL = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_'
function b64Table(alpha) {
  const t = new Int8Array(256).fill(-1)
  for (let i = 0; i < alpha.length; i++) t[alpha.charCodeAt(i)] = i
  t[61] = -2 // '='
  return t
}
const T_STD = b64Table(B64_STD)
const T_URL = b64Table(B64_URL)

/**
 * java.util.Base64.getDecoder() / getUrlDecoder().decode(String): padding
 * optional, but a wrong padding, a stray character or anything after the
 * padding throws.
 */
function javaB64Decode(str, url) {
  const t = url ? T_URL : T_STD
  const out = []
  let bits = 0; let shiftto = 18; let sp = 0
  const sl = str.length
  while (sp < sl) {
    const code = str.charCodeAt(sp++)
    // getBytes(ISO_8859_1) turns anything above U+00FF into '?', which is illegal.
    const b = code > 255 ? -1 : t[code]
    if (b < 0) {
      if (b === -2) {
        if ((shiftto === 6 && (sp === sl || str.charCodeAt(sp++) !== 61)) || shiftto === 18) {
          throw new Error('Input byte array has wrong 4-byte ending unit')
        }
        break
      }
      throw new Error('Illegal base64 character')
    }
    bits |= b << shiftto
    shiftto -= 6
    if (shiftto < 0) {
      out.push((bits >> 16) & 255, (bits >> 8) & 255, bits & 255)
      shiftto = 18; bits = 0
    }
  }
  if (shiftto === 6) out.push((bits >> 16) & 255)
  else if (shiftto === 0) out.push((bits >> 16) & 255, (bits >> 8) & 255)
  else if (shiftto === 12) throw new Error('Last unit does not have enough valid bits')
  if (sp < sl) throw new Error('Input byte array has incorrect ending byte')
  return Buffer.from(out)
}

/**
 * android.util.Base64.decode(String, DEFAULT | URL_SAFE): characters outside
 * the alphabet are skipped, but misplaced padding or a dangling sextet is
 * "bad base-64". The input is taken as UTF-8 bytes, as String.getBytes() does.
 */
function androidB64Decode(str, urlSafe) {
  const t = urlSafe ? T_URL : T_STD
  const input = Buffer.from(str, 'utf8')
  const out = []
  let state = 0; let value = 0
  for (let p = 0; p < input.length; p++) {
    const d = t[input[p]]
    switch (state) {
      case 0:
        if (d >= 0) { value = d; state++ } else if (d !== -1) throw new Error('bad base-64')
        break
      case 1:
        if (d >= 0) { value = (value << 6) | d; state++ } else if (d !== -1) throw new Error('bad base-64')
        break
      case 2:
        if (d >= 0) { value = (value << 6) | d; state++ } else if (d === -2) { out.push((value >> 4) & 255); state = 4 } else if (d !== -1) throw new Error('bad base-64')
        break
      case 3:
        if (d >= 0) {
          value = (value << 6) | d
          out.push((value >> 16) & 255, (value >> 8) & 255, value & 255)
          state = 0
        } else if (d === -2) {
          out.push((value >> 10) & 255, (value >> 2) & 255)
          state = 5
        } else if (d !== -1) throw new Error('bad base-64')
        break
      case 4:
        if (d === -2) state++
        else if (d !== -1) throw new Error('bad base-64')
        break
      case 5:
        if (d !== -1) throw new Error('bad base-64')
        break
    }
  }
  switch (state) {
    case 1: case 4: throw new Error('bad base-64')
    case 2: out.push((value >> 4) & 255); break
    case 3: out.push((value >> 10) & 255, (value >> 2) & 255); break
  }
  return Buffer.from(out)
}

/** ConfigParser.decodeB64: strict JVM decoders on three paddings, then Android's lenient one. */
function decodeB64(input) {
  let s = ''
  for (const ch of ktTrim(input)) if (ch !== '\n' && ch !== '\r' && ch !== ' ' && ch !== '\t') s += ch
  if (s.length === 0) return null
  const bare = trimEndChars(s, '=')
  const padded = bare.length % 4 === 0 ? bare : bare + '='.repeat(4 - (bare.length % 4))
  const candidates = [padded, bare, s]
  for (const cand of candidates) {
    for (const url of [false, true]) {
      try { const r = javaB64Decode(cand, url); if (r.length) return r } catch { /* next */ }
    }
  }
  for (const cand of candidates) {
    for (const url of [false, true]) {
      try { const r = androidB64Decode(cand, url); if (r.length) return r } catch { /* next */ }
    }
  }
  return null
}
function decodeB64Text(input) {
  const b = decodeB64(input)
  return b ? utf8.decode(b) : null
}
/** java.util.Base64.getUrlDecoder() on the "+/ → -_, no padding" form the Kotlin builds first. */
function urlB64ToText(raw) {
  return utf8.decode(javaB64Decode(trimEndChars(raw.split('+').join('-').split('/').join('_'), '='), true))
}
const b64UrlNoPad = s => Buffer.from(s, 'utf8').toString('base64url')
const b64Std = s => Buffer.from(s, 'utf8').toString('base64')

// ======================================================================
// org.json semantics
// ======================================================================

function isObj(v) { return v !== null && typeof v === 'object' && !Array.isArray(v) }
function has(o, k) { return isObj(o) && Object.prototype.hasOwnProperty.call(o, k) }
function jsonToString(v) {
  if (typeof v === 'string') return v
  if (typeof v === 'number' || typeof v === 'boolean') return String(v)
  return JSON.stringify(v)
}
/**
 * optString: numbers and booleans are stringified like org.json does. A JSON
 * null is treated as missing (Android's org.json would return "null").
 */
function optString(o, k, def = '') {
  if (!has(o, k)) return def
  const v = o[k]
  return v == null ? def : jsonToString(v)
}
function javaDouble(s) {
  const t = s.trim()
  if (/^[+-]?(NaN|Infinity)$/.test(t)) return Number(t.replace(/^\+/, ''))
  if (!/^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?[dDfF]?$/.test(t)) return null
  return Number(t.replace(/[dDfF]$/, ''))
}
function toJavaInt(d) {
  if (Number.isNaN(d)) return 0
  const v = Math.trunc(d)
  return v > 2147483647 ? 2147483647 : v < -2147483648 ? -2147483648 : (v === 0 ? 0 : v)
}
function optInt(o, k, def = 0) {
  if (!has(o, k)) return def
  const v = o[k]
  if (typeof v === 'number') return toJavaInt(v)
  if (typeof v === 'string') { const d = javaDouble(v); return d === null ? def : toJavaInt(d) }
  return def
}
function optBool(o, k, def = false) {
  if (!has(o, k)) return def
  const v = o[k]
  if (typeof v === 'boolean') return v
  if (typeof v === 'string') {
    const l = v.toLowerCase()
    if (l === 'true') return true
    if (l === 'false') return false
  }
  return def
}
function optObj(o, k) { return has(o, k) && isObj(o[k]) ? o[k] : null }
function optArr(o, k) { return has(o, k) && Array.isArray(o[k]) ? o[k] : null }
function arrOptString(a, i) { return i < a.length && a[i] != null ? jsonToString(a[i]) : '' }
function arrOptObj(a, i) { return i < a.length && isObj(a[i]) ? a[i] : null }
/** JSONArray.getString: any non-null value as text; missing throws. */
function arrGetString(a, i) {
  if (i >= a.length || a[i] === undefined) throw new Error(`JSONArray[${i}] not found`)
  return a[i] === null ? 'null' : jsonToString(a[i])
}

/** End index (exclusive) of the JSON value starting at s[start] when it is an object or array. */
function jsonValueEnd(s, start) {
  let depth = 0; let inStr = false
  for (let i = start; i < s.length; i++) {
    const c = s[i]
    if (inStr) {
      if (c === '\\') i++
      else if (c === '"') inStr = false
      continue
    }
    if (c === '"') inStr = true
    else if (c === '{' || c === '[') depth++
    else if (c === '}' || c === ']') { depth--; if (depth === 0) return i + 1 }
  }
  return -1
}

/**
 * JSON.parse, but like Android's JSONObject(String) / JSONArray(String) it
 * ignores whatever follows the first complete value (some vmess payloads and
 * pasted files carry trailing junk).
 */
function parseJsonLenient(text) {
  const t = ktTrim(String(text))
  try { return JSON.parse(t) } catch (e) {
    if (t[0] !== '{' && t[0] !== '[') throw e
    const end = jsonValueEnd(t, 0)
    if (end < 0) throw e
    return JSON.parse(t.substring(0, end))
  }
}
function parseJsonObject(text) {
  const v = parseJsonLenient(text)
  if (!isObj(v)) throw new Error('not a JSON object')
  return v
}

// ======================================================================
// java.net.URI host / port (what the Kotlin reads from DoH and control URLs)
// ======================================================================

/**
 * new URI(s).host / .port for a server-based authority. Throws on the
 * characters java.net.URI rejects; host is null when there is no
 * "scheme://authority" or the authority is not a plain host name.
 */
function javaUri(s) {
  if (/[\s"<>\\^`{|}]/.test(s) || /%(?![0-9A-Fa-f]{2})/.test(s)) throw new Error('URISyntaxException')
  const m = /^[A-Za-z][A-Za-z0-9+.-]*:\/\/([^/?#]*)/.exec(s)
  if (!m) return { host: null, port: -1 }
  const auth = afterLast(m[1], '@')
  let host; let rest
  if (auth.startsWith('[')) {
    const end = auth.indexOf(']')
    if (end < 0) throw new Error('URISyntaxException')
    host = auth.substring(0, end + 1); rest = auth.substring(end + 1)
  } else {
    host = before(auth, ':'); rest = auth.substring(host.length)
  }
  let port = -1
  if (rest.startsWith(':') && rest.length > 1) {
    if (!/^\d+$/.test(rest.substring(1))) return { host: null, port: -1 }
    port = Number(rest.substring(1))
  } else if (rest !== '' && rest !== ':') return { host: null, port: -1 }
  if (!host.startsWith('[') && !/^[A-Za-z0-9.-]+$/.test(host)) return { host: null, port: -1 }
  return { host: host || null, port }
}

// ======================================================================
// ConfigParser.kt: shared pieces
// ======================================================================

/** The case-insensitive TreeMap the Kotlin keeps query parameters in. */
class QueryMap {
  constructor() { this.m = new Map() }
  set(k, v) { this.m.set(k.toLowerCase(), v) }
  get(k) { return this.m.get(k.toLowerCase()) }
  has(k) { return this.m.has(k.toLowerCase()) }
  get size() { return this.m.size }
}

function parseQuery(query) {
  const map = new QueryMap()
  if (query === '') return map
  for (const it of query.split('&')) {
    const eq = it.indexOf('=')
    if (eq > 0) {
      const key = ktTrim(it.substring(0, eq))
      if (key !== '') map.set(key, formDecode(it.substring(eq + 1)))
    }
  }
  return map
}

/** [name, user@host:port, query] of "user@host:port?query#name". */
function splitUserUri(body, def) {
  const hash = body.indexOf('#')
  const name = ifEmpty(hash >= 0 ? ktTrim(formDecode(body.substring(hash + 1))) : '', def)
  const main = hash >= 0 ? body.substring(0, hash) : body
  const q = main.indexOf('?')
  const uhp = q >= 0 ? main.substring(0, q) : main
  return [name, ktTrim(uhp), parseQuery(q >= 0 ? main.substring(q + 1) : '')]
}

function splitHostPort(raw) {
  let s = ktTrim(raw)
  const slash = s.indexOf('/')
  if (slash >= 0) s = s.substring(0, slash)
  if (s.startsWith('[')) {
    const end = s.indexOf(']')
    return [sub(s, 1, end), toInt(ktTrim(sub(s, end + 2)))]
  }
  const colon = s.lastIndexOf(':')
  return [sub(s, 0, colon), toInt(ktTrim(sub(s, colon + 1)))]
}

/** Kotlin quirk kept: without an '@' this throws (substring(0, -1)), so the link is rejected. */
function splitUserHostPort(uhp) {
  const at = uhp.lastIndexOf('@')
  const hp = splitHostPort(uhp.substring(at + 1))
  return [sub(uhp, 0, at), hp[0], hp[1]]
}

function splitHostPortOrDefault(raw, def) {
  const s = before(ktTrim(raw), '/')
  const bracket = s.startsWith('[')
  const hasPort = bracket ? s.includes(']:') : s.split(':').length - 1 === 1
  if (hasPort) return splitHostPort(s)
  let h = removePrefix(s, '[')
  if (h.endsWith(']')) h = h.substring(0, h.length - 1)
  return [h, def]
}

function normalizeNetwork(t) {
  const v = ktTrim(orEmpty(t)).toLowerCase()
  switch (v) {
    case '': case 'raw': return 'tcp'
    case 'mkcp': return 'kcp'
    case 'websocket': return 'ws'
    case 'h2': case 'http2': return 'http'
    case 'splithttp': return 'xhttp'
    default: return v
  }
}

function normalizeHeaderType(t) {
  const v = ktTrim(orEmpty(t)).toLowerCase()
  return v === 'none' ? '' : v
}

const truthy = v => v === '1' || v === 'true'
function insecure(p) { return truthy(p.get('insecure') ?? p.get('allowInsecure') ?? p.get('allow_insecure') ?? '') }
const inRange = (v, a, b) => v >= a && v <= b
const validPort = port => inRange(port, 1, 65535)
/** "user[:pass]" → [user, pass] with the Kotlin's pctDecode on each half. */
function userPass(user) {
  const colon = user.indexOf(':')
  return [pctDecode(colon >= 0 ? user.substring(0, colon) : user), pctDecode(colon >= 0 ? user.substring(colon + 1) : '')]
}

// ======================================================================
// ConfigParser.parse: one share link
// ======================================================================

const TAILSCALE_CONTROL = 'controlplane.tailscale.com'
const TAILSCALE_FLAGS = ['ephemeral', 'routes', 'lan']
// Java's \s is [ \t\n\x0B\f\r]; the pattern is anchored to the start of the text only.
const TOR_LINE = /^(bridge[ \t\n\x0B\f\r]+)?(obfs4|webtunnel|snowflake|meek_lite)[ \t\n\x0B\f\r]/i

/** URI schemes parseLink accepts (Tor bridge lines and WireGuard .conf text are accepted too). */
const SCHEMES = Object.freeze([
  'vless', 'vmess', 'trojan', 'ss', 'socks5', 'socks', 'http', 'hysteria2', 'hy2',
  'tuic', 'hysteria', 'anytls', 'masque', 'tailscale', 'tailcat', 'ssh', 'openconnect', 'anyconnect',
  'dnstt', 'mieru', 'mierus', 'brook', 'naive+https', 'naive+quic', 'naive', 'juicity', 'sstp',
  'softether', 'amneziawg', 'awg', 'vaydns', 'noizdns', 'slipstream', 'masterdns', 'stormdns',
  'cottendns', 'ikev2', 'wireguard', 'wg'
])

function parseUri(uri, source) {
  const trimmed = ktTrim(uri)
  const lower = trimmed.toLowerCase()
  const rest = n => trimmed.substring(n)
  const starts = p => lower.startsWith(p)
  if (starts('vless://')) return parseXrayUserLink(rest(8), source, 'vless')
  if (starts('vmess://')) return parseVmess(rest(8), source)
  if (starts('trojan://')) return parseXrayUserLink(rest(9), source, 'trojan')
  if (starts('ss://')) return parseShadowsocks(rest(5), source)
  if (starts('socks5://')) return parseProxyUrl(rest(9), 'socks', source)
  if (starts('socks://')) return parseProxyUrl(rest(8), 'socks', source)
  if (starts('http://')) return parseProxyUrl(rest(7), 'http', source)
  if (starts('hysteria2://')) return parseHysteria2(rest(12), source)
  if (starts('hy2://')) return parseHysteria2(rest(6), source)
  // Carried by sing-box (engine/SingBoxConfig.kt), not Xray.
  if (starts('tuic://')) return parseTuic(rest(7), source)
  if (starts('hysteria://')) return parseHysteria1(rest(11), source)
  if (starts('anytls://')) return parseAnyTls(rest(9), source)
  if (starts('masque://')) return parseMasque(rest(9), source)
  if (starts('tailscale://')) return parseTailscale(rest(12), source)
  if (starts('tailcat://')) return parseTailcat(rest(10), source)
  if (starts('ssh://')) return parseSsh(rest(6), source)
  if (starts('openconnect://')) return parseOpenConnect(rest(14), source)
  if (starts('anyconnect://')) return parseOpenConnect(rest(13), source)
  if (starts('dnstt://')) return parseDnstt(rest(8), source)
  if (starts('mieru://') || starts('mierus://')) return parseMieru(trimmed, source)
  if (starts('brook://')) return parseBrook(trimmed, source)
  // Tor bridge lines as bridges.torproject.org hands them out.
  if (TOR_LINE.test(trimmed)) {
    const lines = ktLines(trimmed)
      .map(l => ktTrim(removePrefix(removePrefix(ktTrim(l), 'Bridge '), 'bridge ')))
      .filter(l => l !== '')
    const pt = before(lines[0], ' ').toLowerCase()
    return make({ name: `Tor (${pt})`, protocol: 'tor', address: '', port: 0, extra: JSON.stringify({ pt, bridges: lines.join('\n') }), source })
  }
  if (starts('naive+https://') || starts('naive+quic://') || starts('naive://')) return parseNaive(trimmed, source)
  if (starts('juicity://')) return parseJuicity(rest(10), source)
  if (starts('sstp://')) return parseSstp(rest(7), source)
  if (starts('softether://')) return parseSoftEther(rest(12), source)
  if (starts('amneziawg://') || starts('awg://')) return parseAmneziaLink(trimmed, source)
  if (starts('vaydns://')) return parseDnstt(rest(9), source, 'vaydns')
  if (starts('noizdns://')) return parseDnstt(rest(10), source, 'noizdns')
  if (starts('slipstream://')) return parseDnstt(rest(13), source, 'slipstream')
  if (starts('masterdns://')) return parseMasterDns(rest(12), source, 'masterdns')
  if (starts('stormdns://')) return parseMasterDns(rest(11), source, 'stormdns')
  if (starts('cottendns://')) return parseMasterDns(rest(12), source, 'cottendns')
  if (starts('ikev2://')) return parseIkev2(rest(8), source)
  if (starts('wireguard://')) return parseWireguardUri(rest(12), source)
  if (starts('wg://')) return parseWireguardUri(rest(5), source)
  if (lower.includes('[interface]') && lower.includes('[peer]')) return parseWireguardConf(trimmed, source)
  return null
}

/** Every per-scheme parser below is wrapped like the Kotlin's try { … } catch { null }. */
function guarded(fn) {
  return (...args) => { try { return fn(...args) } catch { return null } }
}

/** parseVless / parseTrojan: identical apart from the credential field and default security. */
const parseXrayUserLink = guarded((body, source, protocol) => {
  const vless = protocol === 'vless'
  const [name, uhp, p] = splitUserUri(body, vless ? 'VLESS' : 'Trojan')
  const [user, address, port] = splitUserHostPort(uhp)
  const network = normalizeNetwork(p.get('type'))
  const c = {
    name, protocol, address, port,
    flow: orEmpty(p.get('flow')),
    network,
    security: ifEmpty(orEmpty(p.get('security')), vless ? 'none' : 'tls'),
    sni: orEmpty(p.get('sni')), publicKey: orEmpty(p.get('pbk')), shortId: orEmpty(p.get('sid')),
    fingerprint: ifEmpty(orEmpty(p.get('fp')), 'chrome'),
    allowInsecure: truthy(p.get('allowInsecure') ?? p.get('insecure') ?? ''),
    path: ifEmpty(orEmpty(p.get('path')), orEmpty(p.get('seed'))), host: orEmpty(p.get('host')),
    serviceName: ifEmpty(orEmpty(p.get('serviceName')), network === 'grpc' ? orEmpty(p.get('path')) : ''),
    mode: orEmpty(p.get('mode')), alpn: orEmpty(p.get('alpn')),
    headerType: normalizeHeaderType(p.get('headerType')),
    // Written by this app's own share link; absent (and blank) everywhere else.
    maskType: orEmpty(p.get('mask')),
    maskDomain: orEmpty(p.get('maskDomain')),
    maskPassword: orEmpty(p.get('maskPass')),
    echConfigList: orEmpty(p.get('ech')),
    source
  }
  if (vless) {
    c.uuid = pctDecode(user)
    c.encryption = ifEmpty(orEmpty(p.get('encryption')), 'none')
  } else {
    c.password = pctDecode(user)
  }
  return make(c)
})

/** tuic://uuid:password@host:port?sni=&alpn=&congestion_control=&udp_relay_mode=&allow_insecure=#name */
const parseTuic = guarded((body, source) => {
  const [name, uhp, p] = splitUserUri(body, 'TUIC')
  const [user, address, port] = splitUserHostPort(uhp)
  const colon = user.indexOf(':')
  const c = make({
    name, protocol: 'tuic', address, port,
    uuid: pctDecode(colon >= 0 ? user.substring(0, colon) : user),
    password: pctDecode(colon >= 0 ? user.substring(colon + 1) : orEmpty(p.get('password'))),
    sni: orEmpty(p.get('sni')), alpn: orEmpty(p.get('alpn')), security: 'tls',
    method: (p.get('congestion_control') ?? p.get('congestion') ?? '').toLowerCase(),
    mode: (p.get('udp_relay_mode') ?? '').toLowerCase(),
    allowInsecure: insecure(p), source
  })
  return !isBlank(c.uuid) && !isBlank(c.address) && validPort(c.port) ? c : null
})

/** hysteria://host:port?auth=&peer=&insecure=&upmbps=&downmbps=&alpn=&obfs=xplus&obfsParam=#name (v1) */
const parseHysteria1 = guarded((body, source) => {
  const [name, hostPort, p] = splitUserUri(body, 'Hysteria')
  const hp = splitHostPort(afterLast(hostPort, '@'))
  const auth = hostPort.includes('@') ? pctDecode(beforeLast(hostPort, '@')) : orEmpty(p.get('auth'))
  const c = make({
    name, protocol: 'hysteria', address: hp[0], port: hp[1],
    password: auth, sni: ifEmpty(orEmpty(p.get('peer')), orEmpty(p.get('sni'))),
    alpn: orEmpty(p.get('alpn')), security: 'tls',
    hyObfs: orEmpty(p.get('obfs')), hyObfsPassword: orEmpty(p.get('obfsParam')),
    hyUpMbps: toIntOrNull(p.get('upmbps') ?? p.get('up') ?? '') ?? 0,
    hyDownMbps: toIntOrNull(p.get('downmbps') ?? p.get('down') ?? '') ?? 0,
    allowInsecure: insecure(p), source
  })
  return !isBlank(c.address) && validPort(c.port) ? c : null
})

/** anytls://password@host:port?sni=&insecure=&fp=#name */
const parseAnyTls = guarded((body, source) => {
  const [name, uhp, p] = splitUserUri(body, 'AnyTLS')
  const [password, address, port] = splitUserHostPort(uhp)
  const c = make({
    name, protocol: 'anytls', address, port,
    password: pctDecode(password), sni: ifEmpty(orEmpty(p.get('sni')), orEmpty(p.get('peer'))),
    alpn: orEmpty(p.get('alpn')), security: 'tls',
    fingerprint: orEmpty(p.get('fp')), allowInsecure: insecure(p), source
  })
  return !isBlank(c.password) && !isBlank(c.address) && validPort(c.port) ? c : null
})

/** masque://[user:password@]host:port?version=3&path=&sni=&insecure=1&alpn=&fp=&pin=&mtu=#name (Ghajar's own shape) */
const parseMasque = guarded((body, source) => {
  const [name, uhp, p] = splitUserUri(body, 'MASQUE')
  const hasUser = uhp.includes('@')
  const hp = splitHostPortOrDefault(afterLast(uhp, '@'), 443)
  const [uuid, password] = userPass(hasUser ? beforeLast(uhp, '@') : '')
  const v = toIntOrNull(p.get('version'))
  const version = v !== null && inRange(v, 1, 3) ? v : 3
  const mtu = toIntOrNull(p.get('mtu'))
  const c = make({
    name, protocol: 'masque', address: hp[0], port: hp[1], uuid, password,
    // Kotlin quirk kept: path is decoded twice (parseQuery, then pctDecode).
    mode: String(version), path: pctDecode(orEmpty(p.get('path'))),
    sni: orEmpty(p.get('sni')), alpn: orEmpty(p.get('alpn')), fingerprint: orEmpty(p.get('fp')),
    pinnedCertSha256: orEmpty(p.get('pin')), security: 'tls',
    allowInsecure: insecure(p), mtu: mtu !== null && inRange(mtu, 1280, 9000) ? mtu : 0,
    source
  })
  return !isBlank(c.address) && validPort(c.port) ? c : null
})

/** tailscale://[authkey@]controlhost[:port]?control=&exit=&hostname=&flags=ephemeral,routes,lan#name */
const parseTailscale = guarded((body, source) => {
  const [name, uhp, p] = splitUserUri(body, 'Tailscale')
  const hasKey = uhp.includes('@')
  const hp = splitHostPortOrDefault(ifBlank(afterLast(uhp, '@'), TAILSCALE_CONTROL), 443)
  const flags = orEmpty(p.get('flags')).split(',').map(ktTrim).filter(f => TAILSCALE_FLAGS.includes(f))
  return make({
    name, protocol: 'tailscale', address: ifBlank(hp[0], TAILSCALE_CONTROL), port: hp[1],
    password: hasKey ? pctDecode(beforeLast(uhp, '@')) : '',
    host: pctDecode(orEmpty(p.get('control'))), path: pctDecode(orEmpty(p.get('exit'))),
    sni: pctDecode(orEmpty(p.get('hostname'))), headerType: flags.join(','),
    source
  })
})

/** tailcat://derphost?pub=&disco=&psk=&key=&derp=&region=#name */
const parseTailcat = guarded((body, source) => {
  const [name, uhp, p] = splitUserUri(body, 'Tailcat')
  const derp = pctDecode(orEmpty(p.get('derp')))
  let host = before(afterLast(uhp, '@'), ':')
  if (isBlank(host)) {
    let h = ''
    try { h = orEmpty(javaUri(derp).host) } catch { h = '' }
    host = ifBlank(h, 'tailcat.dev')
  }
  const region = toIntOrNull(p.get('region'))
  const c = make({
    name, protocol: 'tailcat', address: host, port: 443,
    publicKey: pctDecode(orEmpty(p.get('pub'))), uuid: pctDecode(orEmpty(p.get('disco'))),
    password: pctDecode(orEmpty(p.get('psk'))), privateKey: pctDecode(orEmpty(p.get('key'))),
    host: derp, mode: region !== null && region > 0 ? String(region) : '',
    source
  })
  return !isBlank(c.publicKey) && !isBlank(c.uuid) ? c : null
})

/** ssh://user:password@host:port?hostkey=&pk=#name (pk = base64 of a PEM private key) */
const parseSsh = guarded((body, source) => {
  const [name, uhp, p] = splitUserUri(body, 'SSH')
  const hasUser = uhp.includes('@')
  const hp = splitHostPortOrDefault(afterLast(uhp, '@'), 22)
  const [uuid, password] = userPass(hasUser ? beforeLast(uhp, '@') : '')
  const raw = orEmpty(p.get('pk'))
  let pk = ''
  if (!isBlank(raw)) { try { pk = urlB64ToText(raw) } catch { pk = '' } }
  const c = make({
    name, protocol: 'ssh', address: hp[0], port: hp[1], uuid, password,
    privateKey: pk, publicKey: orEmpty(p.get('hostkey')), source,
    extra: sshTransportExtra(p)
  })
  return !isBlank(c.address) && validPort(c.port) ? c : null
})

/**
 * The SSH disguise, from Ghajar's ssh:// parameters:
 * mode=payload|http-proxy|https-proxy|tls|payload-tls|ws|wss, proxy=HOST:PORT,
 * sni=, payload=BASE64URL, wspath=, wshost=, wsframing=1, verify=1, ua=.
 */
function sshTransportExtra(p) {
  const mode = orEmpty(p.get('mode')).toLowerCase()
  if (isBlank(mode) || mode === 'direct') return ''
  const t = { mode }
  const proxy = p.get('proxy')
  if (proxy != null && !isBlank(proxy)) {
    const def = mode === 'https-proxy' || mode.includes('tls') || mode === 'wss' ? 443 : 80
    const [h, port] = splitHostPortOrDefault(proxy, def)
    t.proxyHost = h; t.proxyPort = port
  }
  const put = (k, key) => { const v = p.get(k); if (v != null && !isBlank(v)) t[key] = v }
  put('sni', 'sni')
  const payload = p.get('payload')
  if (payload != null && !isBlank(payload)) {
    try { t.payload = urlB64ToText(payload) } catch { /* left out, as runCatching does */ }
  }
  put('wspath', 'wsPath')
  put('wshost', 'wsHost')
  put('ua', 'ua')
  if (p.get('wsframing') === '1') t.wsFraming = true
  if (p.get('verify') === '1') t.verify = true
  return JSON.stringify({ transport: t })
}

/** naive+https://user:pass@host[:port]?padding=…#name and naive+quic://… (NaiveProxy) */
const parseNaive = guarded((link, source) => {
  const quic = link.toLowerCase().startsWith('naive+quic://')
  const [name, uhp, p] = splitUserUri(after(link, '://'), 'NaiveProxy')
  const hp = splitHostPortOrDefault(afterLast(uhp, '@'), 443)
  const [uuid, password] = userPass(uhp.includes('@') ? beforeLast(uhp, '@') : '')
  return make({
    name, protocol: 'naive', address: hp[0], port: hp[1], uuid, password,
    sni: ifEmpty(orEmpty(p.get('sni')), hp[0]), mode: quic ? 'quic' : 'https',
    security: 'tls', source
  })
})

/** juicity://uuid:password@host:port?congestion_control=&sni=&allow_insecure=&pinned_certchain_sha256=#name */
const parseJuicity = guarded((body, source) => {
  const [name, uhp, p] = splitUserUri(body, 'Juicity')
  const [user, address, port] = splitUserHostPort(uhp)
  const colon = user.indexOf(':')
  const c = make({
    name, protocol: 'juicity', address, port,
    // Kotlin quirk kept: without a ':' the uuid is empty (substring(0, 0)), so the link is rejected.
    uuid: pctDecode(user.substring(0, Math.max(colon, 0))), password: pctDecode(colon >= 0 ? user.substring(colon + 1) : ''),
    sni: orEmpty(p.get('sni')), method: ifEmpty(orEmpty(p.get('congestion_control')), 'bbr'),
    allowInsecure: insecure(p), pinnedCertSha256: orEmpty(p.get('pinned_certchain_sha256')), security: 'tls',
    source
  })
  return !isBlank(c.uuid) && !isBlank(c.address) && validPort(c.port) ? c : null
})

/** host[:port] of sstp:// and softether:// links; 443 when no port is given. */
function hostPort443(uhp) {
  const at = uhp.lastIndexOf('@')
  const hostPort = at >= 0 ? uhp.substring(at + 1) : uhp
  const [, address, port] = splitUserHostPort('x@' + (afterLast(hostPort, ']').includes(':') ? hostPort : hostPort + ':443'))
  return { address, port, user: at >= 0 ? uhp.substring(0, at) : '' }
}

/** sstp://user:password@host[:443]?sni=&auth=auto|pap|mschapv2&allow_insecure=1&pin=SHA256HEX&mtu=1400#name */
const parseSstp = guarded((body, source) => {
  const [name, uhp, p] = splitUserUri(body, 'SSTP')
  const { address, port, user } = hostPort443(uhp)
  const [uuid, password] = userPass(user)
  const auth = orEmpty(p.get('auth')).toLowerCase()
  const mtu = toIntOrNull(p.get('mtu'))
  const c = make({
    name, protocol: 'sstp', address, port, uuid, password,
    sni: orEmpty(p.get('sni')), method: auth === 'pap' || auth === 'mschapv2' ? auth : 'auto',
    allowInsecure: insecure(p), pinnedCertSha256: orEmpty(p.get('pin')),
    security: 'tls', mtu: mtu !== null && inRange(mtu, 576, 1500) ? mtu : 0,
    source
  })
  return !isBlank(c.uuid) && !isBlank(c.address) && validPort(c.port) ? c : null
})

/** softether://user:password@host[:443]?hub=DEFAULT&sni=&pin=&allow_insecure=1&auth=plain&ip=a.b.c.d/nn&gw=&dns=&mtu=#name */
const parseSoftEther = guarded((body, source) => {
  const [name, uhp, p] = splitUserUri(body, 'SoftEther')
  const { address, port, user } = hostPort443(uhp)
  const [uuid, password] = userPass(user)
  const extra = { hub: ifBlank(orEmpty(p.get('hub')), 'DEFAULT') }
  if (orEmpty(p.get('auth')).toLowerCase() === 'plain') extra.plain = true
  for (const k of ['ip', 'gw', 'dns']) { const v = p.get(k); if (v != null && !isBlank(v)) extra[k] = v }
  const mtu = toIntOrNull(p.get('mtu'))
  const c = make({
    name, protocol: 'softether', address, port, uuid, password,
    sni: orEmpty(p.get('sni')), allowInsecure: insecure(p), pinnedCertSha256: orEmpty(p.get('pin')), security: 'tls',
    mtu: mtu !== null && inRange(mtu, 576, 1500) ? mtu : 0, extra: JSON.stringify(extra),
    source
  })
  return !isBlank(c.uuid) && !isBlank(c.address) && validPort(c.port) ? c : null
})

/** mieru:// (full configuration) and mierus:// (simple) links: carried by the upstream mieru client. */
const parseMieru = guarded((link, source) => {
  const hash = link.indexOf('#')
  const name = ifEmpty(hash >= 0 ? ktTrim(formDecode(link.substring(hash + 1))) : '', 'Mieru')
  const url = hash >= 0 ? link.substring(0, hash) : link
  let host = ''; let port = 0; let user = ''
  if (url.toLowerCase().startsWith('mierus://')) {
    const [, uhp, p] = splitUserUri(url.substring(9), 'Mieru')
    host = trimChars(before(afterLast(uhp, '@'), '/'), '[]')
    user = pctDecode(before(beforeLast(uhp, '@', ''), ':'))
    const pp = p.get('port')
    port = (pp != null ? toIntOrNull(before(pp, '-')) : null) ?? 0
  }
  return make({ name, protocol: 'mieru', address: host, port, uuid: user, extra: JSON.stringify({ url }), source })
})

/** brook:// links (server, wsserver, wssserver, quicserver, socks5): carried by the upstream brook library. */
const parseBrook = guarded((link, source) => {
  const hash = link.indexOf('#')
  const url = hash >= 0 ? link.substring(0, hash) : link
  const q = parseQuery(after(url, '?', ''))
  const kind = before(before(removePrefix(url, 'brook://'), '?'), '/')
  const server = orEmpty(kind === '' ? undefined : q.get(kind))
  const hostPort = before(after(server, '://'), '/')
  const [host, port] = splitHostPortOrDefault(hostPort, 443)
  const name = ifEmpty(hash >= 0 ? ktTrim(formDecode(link.substring(hash + 1))) : orEmpty(q.get('name')), `Brook ${kind}`)
  const c = make({ name, protocol: 'brook', address: host, port, mode: kind, extra: JSON.stringify({ url }), source })
  return !isBlank(kind) && !isBlank(host) ? c : null
})

/** openconnect://user:password@host[:port]?flavor=anyconnect|gp|fortinet|f5|pulse|nc&insecure=&pin=#name */
const parseOpenConnect = guarded((body, source) => {
  const [name, uhp, p] = splitUserUri(body, 'OpenConnect')
  const hp = splitHostPortOrDefault(afterLast(uhp, '@'), 443)
  const [uuid, password] = userPass(uhp.includes('@') ? beforeLast(uhp, '@') : '')
  const extra = {}
  const put = (k, key) => { const v = p.get(k); if (v != null && !isBlank(v)) extra[key] = v }
  put('authgroup', 'authGroup')
  put('os', 'reportedOs')
  put('ua', 'userAgent')
  const reconnect = toIntOrNull(p.get('reconnect'))
  if (reconnect !== null && reconnect > 0) extra.reconnect = reconnect
  if (p.get('nodtls') === '1') extra.noUdp = true
  if (p.get('noipv6') === '1') extra.ipv6Off = true
  const mtu = toIntOrNull(p.get('mtu'))
  const c = make({
    name, protocol: 'openconnect', address: hp[0], port: hp[1], uuid, password,
    mode: ifEmpty(orEmpty(p.get('flavor')).toLowerCase(), 'anyconnect'),
    sni: orEmpty(p.get('sni')), pinnedCertSha256: orEmpty(p.get('pin')),
    allowInsecure: insecure(p), source,
    mtu: mtu !== null && inRange(mtu, 576, 9000) ? mtu : 0,
    extra: Object.keys(extra).length === 0 ? '' : JSON.stringify(extra)
  })
  return !isBlank(c.address) ? c : null
})

const yes = v => v === '1' || v.toLowerCase() === 'true'

/**
 * dnstt://[user[:pass]@]DOMAIN?pubkey=HEX&transport=udp|dot|doh&resolver=HOST[:PORT]&doh=URL&upstream=socks|ssh#name
 * (also vaydns://, noizdns://, slipstream://). Ghajar's own link for a DNS tunnel.
 */
const parseDnstt = guarded((body, source, protocol = 'dnstt') => {
  const [name, uhp, p] = splitUserUri(body, 'DNS tunnel')
  const domain = trimChars(trimChars(ktTrim(afterLast(uhp, '@')), '/'), '.')
  const [uuid, password] = userPass(uhp.includes('@') ? beforeLast(uhp, '@') : '')
  const t = orEmpty(p.get('transport')).toLowerCase()
  const transport = t === 'doh' || t === 'dot' ? t : (p.get('doh') != null ? 'doh' : 'udp')
  const doh = orEmpty(p.get('doh'))
  let rHost; let rPort
  if (transport === 'doh') {
    const u = javaUri(doh) // a malformed URL rejects the link, as URISyntaxException does
    rHost = orEmpty(u.host); rPort = u.port > 0 ? u.port : 443
  } else {
    [rHost, rPort] = splitHostPortOrDefault(orEmpty(p.get('resolver')), transport === 'dot' ? 853 : 53)
  }
  // Engine options that have no field of their own (see Sidecars), in the Kotlin's put order.
  const extra = {}
  const v = k => p.get(k)
  if (v('record') != null) extra.recordType = v('record').toLowerCase()
  if (v('compat') != null) extra.dnsttCompat = yes(v('compat'))
  if (v('qname') != null && toIntOrNull(v('qname')) !== null) extra.maxQnameLen = toIntOrNull(v('qname'))
  if (v('clientid') != null && toIntOrNull(v('clientid')) !== null) extra.clientIdSize = toIntOrNull(v('clientid'))
  if (v('noiz') != null) extra.noiz = yes(v('noiz'))
  if (v('stealth') != null) extra.stealth = yes(v('stealth'))
  if (v('authoritative') != null && !isBlank(v('authoritative'))) extra.authoritative = v('authoritative')
  if (v('cc') != null && !isBlank(v('cc'))) extra.cc = v('cc').toLowerCase()
  const c = make({
    name, protocol, address: rHost, port: rPort,
    extra: Object.keys(extra).length === 0 ? '' : JSON.stringify(extra),
    host: domain, publicKey: ktTrim(orEmpty(p.get('pubkey'))), mode: transport, path: doh,
    method: orEmpty(p.get('upstream')).toLowerCase() === 'ssh' ? 'ssh' : 'socks',
    uuid, password, source
  })
  return !isBlank(c.host) && !isBlank(c.address) && (!isBlank(c.publicKey) || protocol === 'slipstream') ? c : null
})

/**
 * masterdns://KEY@DOMAIN[,DOMAIN2]?resolver=IP[:PORT][,IP2...]&enc=0..5&transport=udp|dot|doh#NAME
 * (same for stormdns:// and cottendns://).
 */
const parseMasterDns = guarded((body, source, protocol) => {
  const [name, uhp, p] = splitUserUri(body, protocol)
  const key = uhp.includes('@') ? pctDecode(beforeLast(uhp, '@')) : orEmpty(p.get('key'))
  const domain = pctDecode(trimChars(ktTrim(afterLast(uhp, '@')), '/'))
  const resolvers = pctDecode(orEmpty(p.get('resolver'))).split(',').map(ktTrim).filter(r => r !== '')
  const [rHost, rPort] = splitHostPortOrDefault(resolvers.length ? resolvers[0] : '', 53)
  const extra = { enc: toIntOrNull(p.get('enc')) ?? 1 }
  if (resolvers.length > 1) extra.resolvers = resolvers.slice(1).join(',')
  const c = make({
    name, protocol, address: rHost, port: rPort, host: domain,
    password: key, mode: ifEmpty(orEmpty(p.get('transport')).toLowerCase(), 'udp'),
    extra: JSON.stringify(extra), source
  })
  return !isBlank(c.host) && !isBlank(c.address) && !isBlank(c.password) ? c : null
})

const parseHysteria2 = guarded((body, source) => {
  const [name, uhp, p] = splitUserUri(body, 'Hysteria2')
  const [password, address, port] = splitUserHostPort(uhp)
  return make({
    name, protocol: 'hysteria2', address, port,
    password: pctDecode(password),
    sni: ifEmpty(orEmpty(p.get('sni')), orEmpty(p.get('peer'))),
    host: orEmpty(p.get('host')),
    alpn: orEmpty(p.get('alpn')),
    security: 'tls',
    hyObfs: orEmpty(p.get('obfs')),
    hyObfsPassword: ifEmpty(orEmpty(p.get('obfs-password')), orEmpty(p.get('obfsParam'))),
    hyUpMbps: toIntOrNull(p.get('upmbps') ?? p.get('up') ?? '') ?? 0,
    hyDownMbps: toIntOrNull(p.get('downmbps') ?? p.get('down') ?? '') ?? 0,
    allowInsecure: truthy(p.get('insecure') ?? p.get('allowInsecure') ?? ''),
    source
  })
})

const parseVmess = guarded((body, source) => {
  let b = body
  const h = b.indexOf('#'); if (h >= 0) b = b.substring(0, h)
  const q = b.indexOf('?'); if (q >= 0) b = b.substring(0, q)
  const json = decodeB64Text(b)
  if (json === null) return null
  const o = parseJsonObject(json)
  const tls = optString(o, 'tls')
  const net = normalizeNetwork(optString(o, 'net', 'tcp'))
  return make({
    name: ifEmpty(optString(o, 'ps'), 'VMess'), protocol: 'vmess',
    address: optString(o, 'add'), port: toIntOrNull(optString(o, 'port')) ?? 0,
    uuid: optString(o, 'id'), alterId: toIntOrNull(optString(o, 'aid')) ?? 0,
    encryption: ifEmpty(optString(o, 'scy', 'auto'), 'auto'),
    network: net,
    // Kotlin quirk kept: any non-empty tls value but "none" (reality included) means plain TLS.
    security: tls !== '' && tls !== 'none' ? 'tls' : 'none',
    sni: optString(o, 'sni'), fingerprint: ifEmpty(optString(o, 'fp', 'chrome'), 'chrome'),
    path: optString(o, 'path'), host: optString(o, 'host'),
    serviceName: net === 'grpc' ? optString(o, 'path') : '',
    mode: optString(o, 'mode'), alpn: optString(o, 'alpn'),
    headerType: normalizeHeaderType(optString(o, 'type')),
    source
  })
})

/** socks5:// socks:// http:// proxies. */
const parseProxyUrl = guarded((body, proto, source) => {
  const hash = body.indexOf('#')
  const name = hash >= 0 ? formDecode(body.substring(hash + 1)) : ''
  const main = hash >= 0 ? body.substring(0, hash) : body
  const at = main.lastIndexOf('@')
  let user = ''; let pass = ''
  let hostPart
  if (at >= 0) {
    const cred = main.substring(0, at)
    hostPart = main.substring(at + 1)
    const colon = cred.indexOf(':')
    if (colon >= 0) {
      user = formDecode(cred.substring(0, colon))
      pass = formDecode(cred.substring(colon + 1))
    } else user = formDecode(cred)
  } else hostPart = main
  const clean = before(before(hostPart, '/'), '?')
  const colon = clean.lastIndexOf(':')
  if (colon <= 0) return null
  // Kotlin quirk kept: an IPv6 host keeps its brackets here ("[::1]").
  const host = clean.substring(0, colon)
  const port = toIntOrNull(clean.substring(colon + 1))
  if (port === null) return null
  return make({ name: name !== '' ? name : `${host}:${port}`, protocol: proto, address: host, port, uuid: user, password: pass, source })
})

const IKE_ID_KEYS = ['remote_id', 'remoteid', 'sni', 'identity', 'leftid', 'server_id', 'serverid']

/** ikev2://user:pass@host[:port][/identity][?remote_id=…]#name */
const parseIkev2 = guarded((body, source) => {
  const hash = body.indexOf('#')
  const label = hash >= 0 ? formDecode(body.substring(hash + 1)) : ''
  const core = hash >= 0 ? body.substring(0, hash) : body
  const q = core.indexOf('?')
  const params = parseQuery(q >= 0 ? core.substring(q + 1) : '')
  const main = q >= 0 ? core.substring(0, q) : core
  const at = main.lastIndexOf('@')
  if (at <= 0) return null
  const creds = main.substring(0, at)
  const tail = ktTrim(main.substring(at + 1))
  const slash = tail.indexOf('/')
  const raw = ktTrim(slash >= 0 ? tail.substring(0, slash) : tail)
  if (raw === '') return null
  const pathId = slash >= 0 ? trimEndChars(ktTrim(formDecode(tail.substring(slash + 1))), '/') : ''
  let hp = null
  try { hp = splitHostPort(raw) } catch { hp = null }
  // Kotlin quirk kept: a host without a port (IPv6 in brackets included) is used as written, port 500.
  const host = ifEmpty(hp ? ktTrim(hp[0]) : '', raw)
  const port = hp ? hp[1] : 500
  const colon = creds.indexOf(':')
  if (colon <= 0) return null
  let identity = pathId
  if (identity === '') {
    for (const k of IKE_ID_KEYS) {
      const v = params.get(k)
      if (v != null) { const d = ktTrim(formDecode(v)); if (d !== '') { identity = d; break } }
    }
  }
  return make({
    name: ifBlank(label, host),
    protocol: 'ikev2', address: host, port, sni: identity,
    uuid: formDecode(creds.substring(0, colon)),
    password: formDecode(creds.substring(colon + 1)),
    network: 'ikev2', security: 'none', source
  })
})

const AMNEZIA_KEYS = ['jc', 'jmin', 'jmax', 's1', 's2', 's3', 's4', 'h1', 'h2', 'h3', 'h4', 'i1', 'i2', 'i3', 'i4', 'i5']

/** A WireGuard / AmneziaWG [Interface]/[Peer] text. */
function parseWireguardConf(text, source = 'PERSONAL') {
  try {
    let section = ''
    let privateKey = ''; let address = ''; let mtu = 0; let publicKey = ''; let preShared = ''
    let endpoint = ''; let reserved = ''; let label = ''; let amnezia = false
    for (const raw of ktLines(text)) {
      const line = ktTrim(before(raw, '#'))
      if (line === '') continue
      if (line.startsWith('[') && line.endsWith(']')) { section = line.toLowerCase(); continue }
      const eq = line.indexOf('=')
      if (eq <= 0) continue
      const key = ktTrim(line.substring(0, eq)).toLowerCase()
      const value = ktTrim(line.substring(eq + 1))
      if (section === '[interface]') {
        if (key === 'privatekey') privateKey = value
        else if (key === 'address') address = value
        else if (key === 'mtu') mtu = toIntOrNull(value) ?? 0
        else if (key === 'reserved') reserved = value
        else if (key === 'name') label = value
        // AmneziaWG obfuscation keys (1.x and 2.0): any set to non-zero means
        // plain WireGuard (Xray) cannot talk to this server.
        else if (AMNEZIA_KEYS.includes(key)) { if (!isBlank(value) && value !== '0') amnezia = true }
      } else if (section === '[peer]') {
        if (key === 'publickey') publicKey = value
        else if (key === 'presharedkey') preShared = value
        else if (key === 'endpoint') endpoint = value
      }
    }
    if (privateKey === '' || publicKey === '' || endpoint === '') return null
    const colon = endpoint.lastIndexOf(':')
    if (colon <= 0) return null
    const host = trimChars(endpoint.substring(0, colon), '[]')
    const port = toIntOrNull(endpoint.substring(colon + 1))
    if (port === null) return null
    if (amnezia) {
      return make({
        name: label !== '' ? label : `AmneziaWG ${host}`,
        protocol: 'amneziawg', address: host, port,
        privateKey, publicKey, localAddress: address, mtu,
        extra: JSON.stringify({ conf: ktTrim(text) }),
        source
      })
    }
    return make({
      name: label !== '' ? label : `WireGuard ${host}`,
      protocol: 'wireguard', address: host, port,
      privateKey, publicKey, password: preShared, localAddress: address, mtu, reserved,
      source
    })
  } catch { return null }
}

/** amneziawg://BASE64URL(conf)#name and awg://… */
const parseAmneziaLink = guarded((trimmed, source) => {
  const body = after(trimmed, '://')
  const conf = urlB64ToText(before(body, '#'))
  const c = parseWireguardConf(conf, source)
  if (!c) return null
  const frag = after(body, '#', '')
  if (!isBlank(frag)) c.name = formDecode(frag)
  return c
})

/** wireguard://PRIVATEKEY@host:port?publickey=&presharedkey=&address=&mtu=&reserved=#name and wg://… */
const parseWireguardUri = guarded((body, source) => {
  const hash = body.indexOf('#')
  const name = hash >= 0 ? formDecode(body.substring(hash + 1)) : ''
  const main = hash >= 0 ? body.substring(0, hash) : body
  const q = main.indexOf('?')
  const core = q >= 0 ? main.substring(0, q) : main
  const params = new Map()
  if (q >= 0) {
    for (const part of main.substring(q + 1).split('&')) {
      const i = part.indexOf('=')
      if (i > 0) params.set(formDecode(part.substring(0, i)).toLowerCase(), formDecode(part.substring(i + 1)))
    }
  }
  const at = core.lastIndexOf('@')
  if (at <= 0) return null
  const key = formDecode(core.substring(0, at))
  const hostPart = core.substring(at + 1)
  const colon = hostPart.lastIndexOf(':')
  if (colon <= 0) return null
  const host = trimChars(hostPart.substring(0, colon), '[]')
  const port = toIntOrNull(hostPart.substring(colon + 1))
  if (port === null) return null
  return make({
    name: name !== '' ? name : `WireGuard ${host}`,
    protocol: 'wireguard', address: host, port,
    privateKey: key,
    publicKey: params.get('publickey') ?? params.get('pubkey') ?? '',
    password: params.get('presharedkey') ?? '',
    localAddress: params.get('address') ?? params.get('ip') ?? '',
    mtu: toIntOrNull(params.get('mtu')) ?? 0,
    reserved: params.get('reserved') ?? '',
    source
  })
})

/** ss:// in SIP002 (base64 or plain userinfo), legacy all-base64, with SIP003 plugins. */
const parseShadowsocks = guarded((body, source) => {
  const hash = body.indexOf('#')
  const name = ifEmpty(hash >= 0 ? ktTrim(formDecode(body.substring(hash + 1))) : '', 'Shadowsocks')
  let main = hash >= 0 ? body.substring(0, hash) : body
  const q = main.indexOf('?')
  const p = parseQuery(q >= 0 ? main.substring(q + 1) : '')
  if (q >= 0) main = main.substring(0, q)
  main = ktTrim(main)

  let method; let password; let address; let port
  const at = main.lastIndexOf('@')
  if (at >= 0) {
    const rawUser = pctDecode(main.substring(0, at))
    // Kotlin quirk kept: a plain "method:pass" userinfo is first run through
    // the lenient Android base64 decoder; only a result without ':' falls back.
    const decoded = decodeB64Text(rawUser)
    const info = decoded !== null && decoded.includes(':') ? decoded : rawUser
    ;[address, port] = splitHostPort(main.substring(at + 1))
    const mc = info.indexOf(':')
    method = sub(info, 0, mc); password = info.substring(mc + 1)
  } else {
    const dec = decodeB64Text(pctDecode(main))
    if (dec === null) return null
    const da = dec.lastIndexOf('@')
    const mp = sub(dec, 0, da)
    ;[address, port] = splitHostPort(dec.substring(da + 1))
    const mc = mp.indexOf(':')
    method = sub(mp, 0, mc); password = mp.substring(mc + 1)
  }

  const plugin = orEmpty(p.get('plugin'))
  const opts = new QueryMap()
  if (plugin !== '') {
    for (const part of plugin.split(';')) {
      const eq = part.indexOf('=')
      if (eq > 0) opts.set(ktTrim(part.substring(0, eq)), ktTrim(part.substring(eq + 1)))
      // Kotlin quirk kept: a bare flag ("tls", "quic") is only kept when it is
      // the first part (the plugin name); later bare flags are dropped.
      else if (!isBlank(part) && opts.size === 0) opts.set('name', ktTrim(part))
    }
  }
  const pluginName = orEmpty(opts.get('name')).toLowerCase()
  const isSimpleObfs = pluginName.startsWith('obfs-local') || pluginName.startsWith('simple-obfs')
  const isV2rayPlugin = pluginName.startsWith('v2ray-plugin')
  const obfsMode = orEmpty(opts.get('obfs')).toLowerCase()

  let network = normalizeNetwork(p.get('type'))
  let headerType = normalizeHeaderType(p.get('headerType'))
  let host = orEmpty(p.get('host'))
  let path = orEmpty(p.get('path'))
  let security = ifEmpty(orEmpty(p.get('security')), 'none')

  if (isSimpleObfs && obfsMode === 'http') {
    network = 'tcp'
    headerType = 'http'
    if (host === '') host = orEmpty(opts.get('obfs-host'))
    if (path === '') path = orEmpty(opts.get('obfs-uri'))
  } else if (isV2rayPlugin) {
    network = opts.has('quic') ? 'quic' : 'ws'
    if (host === '') host = orEmpty(opts.get('host'))
    if (path === '') path = orEmpty(opts.get('path'))
    if (opts.has('tls')) security = 'tls'
  }

  // shadow-tls plugin ("shadow-tls;host=…;password=…;version=3"): the outer
  // ShadowTLS server wraps the Shadowsocks stream; sing-box carries both.
  if (pluginName.startsWith('shadow-tls') || pluginName.startsWith('shadowtls')) {
    return make({
      name, protocol: 'shadowtls', address, port,
      password: ifEmpty(orEmpty(opts.get('password')), orEmpty(opts.get('passwd'))),
      sni: orEmpty(opts.get('host')),
      alterId: toIntOrNull(opts.get('version')) ?? 3,
      fingerprint: ifEmpty(orEmpty(p.get('fp')), 'chrome'),
      extra: JSON.stringify({ ss: { method, password } }),
      source
    })
  }
  return make({
    name, protocol: 'shadowsocks', address, port, method, password,
    network: ifEmpty(network, 'tcp'), headerType, host, path, security,
    sni: ifEmpty(orEmpty(p.get('sni')), security === 'tls' ? host : ''),
    fingerprint: ifEmpty(orEmpty(p.get('fp')), 'chrome'),
    allowInsecure: truthy(p.get('allowInsecure') ?? p.get('insecure') ?? ''),
    alpn: orEmpty(p.get('alpn')),
    source
  })
})

// ======================================================================
// ConfigParser.parseJsonOutbounds: Xray JSON (and sing-box, via ForeignImport)
// ======================================================================

const XRAY_SKIP = ['freedom', 'blackhole', 'dns', 'tun', 'loopback']

function outboundToConfig(o, source, parentLabel = '') {
  const protocol = optString(o, 'protocol').toLowerCase()
  if (protocol === '' || XRAY_SKIP.includes(protocol)) return null
  const settings = optObj(o, 'settings') || {}
  const stream = optObj(o, 'streamSettings') || {}
  const tag = optString(o, 'tag')

  let address = ''; let port = 0; let uuid = ''; let password = ''; let method = ''
  let encryption = ''; let flow = ''; let alterId = 0
  if (protocol === 'vless' || protocol === 'vmess') {
    const vn = optArr(settings, 'vnext')
    const v = vn ? arrOptObj(vn, 0) : null
    if (!v) return null
    address = optString(v, 'address')
    port = optInt(v, 'port')
    const users = optArr(v, 'users')
    const u = (users && arrOptObj(users, 0)) || {}
    uuid = optString(u, 'id')
    flow = optString(u, 'flow')
    alterId = optInt(u, 'alterId', 0)
    encryption = protocol === 'vless' ? ifEmpty(optString(u, 'encryption'), 'none') : ifEmpty(optString(u, 'security'), 'auto')
  } else if (['trojan', 'shadowsocks', 'socks', 'http'].includes(protocol)) {
    const sv = optArr(settings, 'servers')
    const v = sv ? arrOptObj(sv, 0) : null
    if (!v) return null
    address = optString(v, 'address')
    port = optInt(v, 'port')
    password = optString(v, 'password')
    method = optString(v, 'method')
    flow = optString(v, 'flow')
    const users = optArr(v, 'users')
    const u = users ? arrOptObj(users, 0) : null
    if (u) {
      uuid = optString(u, 'user')
      if (password === '') password = optString(u, 'pass')
    }
  } else return null
  if (address === '' || port <= 0) return null

  const network = normalizeNetwork(ifEmpty(optString(stream, 'network'), 'tcp'))
  const security = ifEmpty(optString(stream, 'security'), 'none')
  const tls = optObj(stream, 'tlsSettings')
  const reality = optObj(stream, 'realitySettings')
  const sec = tls || reality

  let host = ''; let path = ''; let headerType = ''; let serviceName = ''
  const first = a => (a ? arrOptString(a, 0) : '')
  switch (network) {
    case 'tcp': {
      const ts = optObj(stream, 'tcpSettings')
      const header = ts ? optObj(ts, 'header') : null
      headerType = normalizeHeaderType(header ? optString(header, 'type') : null)
      const req = header ? optObj(header, 'request') : null
      path = req ? first(optArr(req, 'path')) : ''
      const hs = req ? optObj(req, 'headers') : null
      host = hs ? first(optArr(hs, 'Host')) : ''
      break
    }
    case 'kcp': {
      const kcp = optObj(stream, 'kcpSettings')
      const header = kcp ? optObj(kcp, 'header') : null
      headerType = normalizeHeaderType(header ? optString(header, 'type') : null)
      path = kcp ? optString(kcp, 'seed') : ''
      break
    }
    case 'ws': {
      const ws = optObj(stream, 'wsSettings')
      path = ws ? optString(ws, 'path') : ''
      const hs = ws ? optObj(ws, 'headers') : null
      host = hs ? optString(hs, 'Host') : ''
      if (host === '') host = ws ? optString(ws, 'host') : ''
      break
    }
    case 'httpupgrade': case 'xhttp': {
      const s = optObj(stream, network === 'xhttp' ? 'xhttpSettings' : 'httpupgradeSettings')
      path = s ? optString(s, 'path') : ''
      host = s ? optString(s, 'host') : ''
      break
    }
    case 'grpc': {
      const g = optObj(stream, 'grpcSettings')
      serviceName = g ? optString(g, 'serviceName') : ''
      host = g ? optString(g, 'authority') : ''
      break
    }
    case 'http': {
      const h = optObj(stream, 'httpSettings')
      path = h ? optString(h, 'path') : ''
      host = h ? first(optArr(h, 'host')) : ''
      break
    }
  }

  const sni = sec ? optString(sec, 'serverName') : ''
  const alpnArr = sec ? optArr(sec, 'alpn') : null
  const alpn = alpnArr ? alpnArr.map((_, i) => arrOptString(alpnArr, i)).join(',') : ''
  const name = parentLabel !== '' ? parentLabel
    : (tag !== '' && tag !== 'proxy') ? tag
      : (sni !== '' ? sni : `${address}:${port}`)

  return make({
    name, protocol, address, port, uuid, password, method, encryption, flow, alterId,
    network,
    security: reality ? 'reality' : security,
    sni, alpn, host, path, headerType, serviceName,
    fingerprint: ifEmpty(sec ? optString(sec, 'fingerprint') : '', 'chrome'),
    publicKey: reality ? optString(reality, 'publicKey') : '',
    shortId: reality ? optString(reality, 'shortId') : '',
    allowInsecure: sec ? optBool(sec, 'allowInsecure', false) : false,
    source
  })
}

/** Xray JSON (object, array of configs, or bare outbounds) and sing-box JSON. */
function parseJsonOutbounds(text, source = 'PERSONAL') {
  try {
    const t = ktTrim(String(text))
    // sing-box configurations: typed outbounds / endpoints.
    let root = null
    try { root = parseJsonObject(t) } catch { root = null }
    if (root && looksLikeSingBox(root)) return singBox(root, source).configs

    const nodes = []
    const collect = o => {
      let label = ''
      for (const k of ['remarks', 'remark', 'name', 'ps', 'tag']) {
        const v = optString(o, k)
        if (v !== '') { label = v; break }
      }
      const arr = optArr(o, 'outbounds')
      if (arr) {
        for (let i = 0; i < arr.length; i++) { const x = arrOptObj(arr, i); if (x) nodes.push([x, label]) }
      } else nodes.push([o, ''])
    }
    try {
      if (t.startsWith('[')) {
        const arr = parseJsonLenient(t)
        if (!Array.isArray(arr)) return []
        for (let i = 0; i < arr.length; i++) { const x = arrOptObj(arr, i); if (x) collect(x) }
      } else {
        collect(parseJsonObject(t))
      }
    } catch { return [] }
    const out = []
    for (const [node, label] of nodes) {
      let c = null
      try { c = outboundToConfig(node, source, label) } catch { c = null }
      if (c) out.push(c)
    }
    return out
  } catch { return [] }
}

// ======================================================================
// ForeignImport.kt: link building shared by Clash and sing-box
// ======================================================================

/** ForeignImport.enc: URLEncoder with %20 for spaces. */
const fiEnc = s => javaUrlEncode(s).replace(/\+/g, '%20')
const fiHost = h => (h.includes(':') && !h.startsWith('[') ? `[${h}]` : h)
function fiQuery(pairs) {
  const q = pairs.filter(([, v]) => v != null && v !== '').map(([k, v]) => k + '=' + fiEnc(v)).join('&')
  return q === '' ? '' : '?' + q
}
function fiLink(scheme, user, server, port, name, pairs = []) {
  return `${scheme}://` + (user == null || user === '' ? '' : `${user}@`) + fiHost(server) + ':' + port + fiQuery(pairs) + '#' + fiEnc(name)
}
const toMap = pairs => new Map(pairs)

// ---------------------------------------------------------------- Clash

function looksLikeClash(text) {
  const t = String(text).replace(/^[\uFEFF \n\r\t]+/, '')
  return !t.startsWith('{') && !t.startsWith('[') && /^[ \t\n\x0B\f\r]*proxies[ \t\n\x0B\f\r]*:/m.test(t)
}

/** Kotlin toString() of a MiniYaml value (Map → "{k=v, …}", List → "[a, b]"). */
function ktToString(v) {
  if (v == null) return 'null'
  if (Array.isArray(v)) return '[' + v.map(ktToString).join(', ') + ']'
  if (v instanceof Map) return '{' + [...v].map(([k, x]) => `${k}=${ktToString(x)}`).join(', ') + '}'
  return String(v)
}
function ys(m, k) {
  if (!(m instanceof Map)) return ''
  const v = m.get(k)
  if (v == null) return ''
  if (Array.isArray(v)) return v.map(ktToString).join(',')
  return ktToString(v)
}
const yb = (m, k) => ['true', '1', 'yes'].includes(ys(m, k).toLowerCase())
const ysub = (m, k) => (m instanceof Map && m.get(k) instanceof Map ? m.get(k) : null)
const digits = s => s.replace(/[^0-9]/g, '') // Char::isDigit, ASCII part
function yUserPass(m, userKey = 'username') {
  const u = ys(m, userKey); const p = ys(m, 'password')
  return u === '' && p === '' ? null : fiEnc(u) + ':' + fiEnc(p)
}

/** Transport query parameters shared by vmess/vless/trojan. */
function clashTransport(m) {
  const net = ifBlank(ys(m, 'network'), 'tcp')
  const ws = ysub(m, 'ws-opts'); const grpc = ysub(m, 'grpc-opts'); const h2 = ysub(m, 'h2-opts'); const http = ysub(m, 'http-opts')
  switch (net) {
    case 'ws': return [['type', 'ws'], ['path', ys(ws, 'path')], ['host', ifBlank(ys(ysub(ws, 'headers'), 'Host'), ys(ysub(ws, 'headers'), 'host'))]]
    case 'grpc': return [['type', 'grpc'], ['serviceName', ys(grpc, 'grpc-service-name')]]
    case 'h2': return [['type', 'http'], ['path', ys(h2, 'path')], ['host', ys(h2, 'host')]]
    case 'http': return [['type', 'tcp'], ['headerType', 'http'], ['path', ys(http, 'path')], ['host', ys(ysub(http, 'headers'), 'Host')]]
    case 'httpupgrade': return [['type', 'httpupgrade'], ['path', ys(ws, 'path')], ['host', ys(ysub(ws, 'headers'), 'Host')]]
    default: return [['type', 'tcp']]
  }
}

function clashProxy(m, type, name, src) {
  const server = ys(m, 'server'); const port = toIntOrNull(ys(m, 'port')) ?? 0
  const sni = ifBlank(ys(m, 'servername'), ys(m, 'sni'))
  const insec = yb(m, 'skip-cert-verify') ? '1' : null
  const fp = ys(m, 'client-fingerprint')
  const alpn = ys(m, 'alpn')
  switch (type) {
    case 'ss': {
      const plugin = ys(m, 'plugin'); const po = ysub(m, 'plugin-opts')
      let pluginStr
      if (plugin === 'obfs') pluginStr = `obfs-local;obfs=${ys(po, 'mode')};obfs-host=${ys(po, 'host')}`
      else if (plugin === 'v2ray-plugin') {
        pluginStr = `v2ray-plugin;mode=${ifBlank(ys(po, 'mode'), 'websocket')}` +
          (yb(po, 'tls') ? ';tls' : '') + `;host=${ys(po, 'host')};path=${ys(po, 'path')}`
      } else if (plugin === 'shadow-tls') pluginStr = `shadow-tls;host=${ys(po, 'host')};password=${ys(po, 'password')};version=${ifBlank(ys(po, 'version'), '3')}`
      else if (plugin === '') pluginStr = null
      else return null
      const user = b64UrlNoPad(`${ys(m, 'cipher')}:${ys(m, 'password')}`)
      return parseLink(fiLink('ss', user, server, port, name, [['plugin', pluginStr]]), src)
    }
    case 'vmess': {
      const t = toMap(clashTransport(m))
      const j = {
        v: '2', ps: name, add: server, port: String(port),
        id: ys(m, 'uuid'), aid: ifBlank(ys(m, 'alterId'), '0'), scy: ifBlank(ys(m, 'cipher'), 'auto'),
        net: t.get('type') ?? 'tcp', type: t.get('headerType') ?? 'none', host: orEmpty(t.get('host')),
        path: t.get('path') ?? orEmpty(t.get('serviceName')), tls: yb(m, 'tls') ? 'tls' : '',
        sni, alpn, fp
      }
      return parseLink('vmess://' + b64Std(JSON.stringify(j)), src)
    }
    case 'vless': {
      const reality = ysub(m, 'reality-opts')
      const security = reality ? 'reality' : yb(m, 'tls') ? 'tls' : 'none'
      return parseLink(fiLink('vless', ys(m, 'uuid'), server, port, name, [...clashTransport(m),
        ['security', security], ['sni', sni], ['fp', fp], ['alpn', alpn],
        ['flow', ys(m, 'flow')], ['pbk', ys(reality, 'public-key')], ['sid', ys(reality, 'short-id')],
        ['allowInsecure', insec], ['encryption', 'none']]), src)
    }
    case 'trojan': {
      const reality = ysub(m, 'reality-opts')
      return parseLink(fiLink('trojan', fiEnc(ys(m, 'password')), server, port, name, [...clashTransport(m),
        ['security', reality ? 'reality' : 'tls'], ['sni', sni], ['fp', fp],
        ['alpn', alpn], ['pbk', ys(reality, 'public-key')], ['sid', ys(reality, 'short-id')],
        ['allowInsecure', insec]]), src)
    }
    case 'hysteria2': case 'hy2':
      return parseLink(fiLink('hysteria2', fiEnc(ys(m, 'password')), server, port, name, [
        ['sni', sni], ['obfs', ys(m, 'obfs')], ['obfs-password', ys(m, 'obfs-password')], ['insecure', insec],
        ['up', digits(ys(m, 'up'))], ['down', digits(ys(m, 'down'))], ['alpn', alpn]]), src)
    case 'hysteria':
      return parseLink(fiLink('hysteria', null, server, port, name, [
        ['auth', ifBlank(ys(m, 'auth-str'), ys(m, 'auth'))], ['peer', sni], ['obfs', ys(m, 'obfs')],
        ['upmbps', digits(ys(m, 'up'))], ['downmbps', digits(ys(m, 'down'))],
        ['alpn', alpn], ['insecure', insec]]), src)
    case 'tuic':
      return parseLink(fiLink('tuic', fiEnc(ys(m, 'uuid')) + ':' + fiEnc(ys(m, 'password')), server, port, name, [
        ['sni', sni], ['alpn', alpn], ['congestion_control', ys(m, 'congestion-controller')],
        ['udp_relay_mode', ys(m, 'udp-relay-mode')], ['allow_insecure', insec]]), src)
    case 'anytls':
      return parseLink(fiLink('anytls', fiEnc(ys(m, 'password')), server, port, name, [['sni', sni], ['fp', fp],
        ['alpn', alpn], ['insecure', insec]]), src)
    case 'socks5':
      return parseLink(fiLink('socks5', yUserPass(m), server, port, name), src)
    case 'http':
      return yb(m, 'tls') ? null : parseLink(fiLink('http', yUserPass(m), server, port, name), src)
    case 'ssh': {
      // Kotlin quirk kept: the PEM goes into pk= unencoded, which parseSsh
      // cannot base64-decode, so the key is dropped on import.
      const pem = ys(m, 'private-key')
      return parseLink(fiLink('ssh', yUserPass(m, 'username'), server, port > 0 ? port : 22, name, [
        ['pk', pem.includes('PRIVATE KEY') ? pem : null], ['hostkey', ys(m, 'host-key')]]), src)
    }
    case 'mieru':
      return parseLink(fiLink('mierus', yUserPass(m), server, 0, name, [
        ['port', ifBlank(ys(m, 'port'), ys(m, 'port-range'))], ['protocol', ifBlank(ys(m, 'transport'), 'TCP')]])
        .split(':0?').join('?'), src)
    case 'snell': {
      const c = make({
        name, protocol: 'snell', address: server, port, password: ys(m, 'psk'),
        alterId: toIntOrNull(ys(m, 'version')) ?? 4, hyObfs: ys(ysub(m, 'obfs-opts'), 'mode'),
        host: ys(ysub(m, 'obfs-opts'), 'host'), source: src
      })
      return !isBlank(server) && port > 0 ? c : null
    }
    case 'wireguard': {
      const awg = ysub(m, 'amnezia-wg-option')
      let conf = '[Interface]\n'
      conf += `PrivateKey = ${ys(m, 'private-key')}\n`
      const addrs = [ys(m, 'ip'), ys(m, 'ipv6')].filter(a => !isBlank(a))
      if (addrs.length) conf += 'Address = ' + addrs.map(a => (a.includes('/') ? a : a.includes(':') ? `${a}/128` : `${a}/32`)).join(', ') + '\n'
      const mtu = ys(m, 'mtu')
      if (!isBlank(mtu)) conf += `MTU = ${mtu}\n`
      if (awg) for (const [k, v] of awg) conf += `${k.charAt(0).toUpperCase()}${k.substring(1)} = ${ktToString(v)}\n`
      conf += '[Peer]\n'
      conf += `PublicKey = ${ys(m, 'public-key')}\n`
      const psk = ys(m, 'pre-shared-key')
      if (!isBlank(psk)) conf += `PresharedKey = ${psk}\n`
      conf += 'AllowedIPs = 0.0.0.0/0, ::/0\n'
      conf += `Endpoint = ${fiHost(server)}:${port}\n`
      const c = parseWireguardConf(conf, src)
      if (c) c.name = name
      return c
    }
    default: return null
  }
}

/** ForeignImport.clash: { configs, warnings } from a Clash / Mihomo YAML. */
function importClash(text, source = 'PERSONAL') {
  let root = null
  try { root = MiniYaml.parse(String(text)) } catch { root = null }
  if (!(root instanceof Map)) return { configs: [], warnings: ['the YAML could not be read'] }
  const proxies = root.get('proxies')
  if (!Array.isArray(proxies)) return { configs: [], warnings: ['no proxies: list'] }
  const configs = []; const warnings = []
  for (const p of proxies) {
    if (!(p instanceof Map)) continue
    const name = ifBlank(ys(p, 'name'), 'Clash')
    const type = ys(p, 'type').toLowerCase()
    let c = null
    try { c = clashProxy(p, type, name, source) } catch { c = null }
    if (c) configs.push(c); else warnings.push(`${name}: type "${type}" skipped (not supported or incomplete)`)
  }
  return { configs, warnings }
}

// ------------------------------------------------------------- sing-box

/** True when a JSON config uses sing-box's shape (typed outbounds/endpoints). */
function looksLikeSingBox(root) {
  if (!isObj(root)) return false
  const arr = optArr(root, 'outbounds') || optArr(root, 'endpoints')
  if (!arr) return false
  return arr.some(o => isObj(o) && has(o, 'type') && !has(o, 'protocol'))
}

function sbTls(o) {
  const t = optObj(o, 'tls')
  if (!t || !optBool(t, 'enabled')) return []
  let reality = optObj(t, 'reality')
  if (reality && !optBool(reality, 'enabled')) reality = null
  const a = optArr(t, 'alpn')
  const alpn = a ? a.map((_, i) => arrGetString(a, i)).join(',') : null
  const utls = optObj(t, 'utls')
  return [['security', reality ? 'reality' : 'tls'], ['sni', optString(t, 'server_name')],
    ['alpn', alpn], ['fp', utls ? optString(utls, 'fingerprint') : null],
    ['allowInsecure', optBool(t, 'insecure') ? '1' : null],
    ['pbk', reality ? optString(reality, 'public_key') : null], ['sid', reality ? optString(reality, 'short_id') : null]]
}

function sbTransport(o) {
  const t = optObj(o, 'transport')
  if (!t) return [['type', 'tcp']]
  switch (optString(t, 'type')) {
    case 'ws': {
      const h = optObj(t, 'headers')
      return [['type', 'ws'], ['path', optString(t, 'path')], ['host', h ? optString(h, 'Host') : null]]
    }
    case 'grpc': return [['type', 'grpc'], ['serviceName', optString(t, 'service_name')]]
    case 'http': {
      const a = optArr(t, 'host')
      return [['type', 'http'], ['path', optString(t, 'path')], ['host', a ? a.map((_, i) => arrGetString(a, i)).join(',') : null]]
    }
    case 'httpupgrade': return [['type', 'httpupgrade'], ['path', optString(t, 'path')], ['host', optString(t, 'host')]]
    default: return [['type', 'tcp']]
  }
}

function sbUser(o, userKey = 'username') {
  const u = optString(o, userKey); const p = optString(o, 'password')
  return u === '' && p === '' ? null : fiEnc(u) + ':' + fiEnc(p)
}
const positive = n => (n > 0 ? String(n) : null)
const nonBlank = s => (isBlank(s) ? null : s)

function singBoxOutbound(o, byTag, src) {
  const name = ifBlank(optString(o, 'tag'), optString(o, 'type'))
  const server = optString(o, 'server'); const port = optInt(o, 'server_port')
  const tls = toMap(sbTls(o))
  switch (optString(o, 'type')) {
    case 'vless':
      return parseLink(fiLink('vless', optString(o, 'uuid'), server, port, name,
        [...sbTransport(o), ...sbTls(o), ['flow', optString(o, 'flow')], ['encryption', 'none']]), src)
    case 'trojan':
      return parseLink(fiLink('trojan', fiEnc(optString(o, 'password')), server, port, name, [...sbTransport(o), ...sbTls(o)]), src)
    case 'vmess': {
      const t = toMap(sbTransport(o))
      const j = {
        v: '2', ps: name, add: server, port: String(port),
        id: optString(o, 'uuid'), aid: String(optInt(o, 'alter_id')), scy: ifBlank(optString(o, 'security'), 'auto'),
        net: t.get('type') ?? 'tcp', host: orEmpty(t.get('host')), path: t.get('path') ?? orEmpty(t.get('serviceName')),
        tls: tls.get('security') === 'tls' ? 'tls' : '', sni: orEmpty(tls.get('sni')), alpn: orEmpty(tls.get('alpn')),
        fp: orEmpty(tls.get('fp'))
      }
      return parseLink('vmess://' + b64Std(JSON.stringify(j)), src)
    }
    case 'shadowsocks': {
      const user = b64UrlNoPad(`${optString(o, 'method')}:${optString(o, 'password')}`)
      const d = byTag.get(optString(o, 'detour'))
      const detour = d && optString(d, 'type') === 'shadowtls' ? d : null
      if (detour) {
        const dt = optObj(detour, 'tls')
        const plugin = `shadow-tls;host=${dt ? optString(dt, 'server_name') : ''};` +
          `password=${optString(detour, 'password')};version=${optInt(detour, 'version', 3)}`
        return parseLink(fiLink('ss', user, optString(detour, 'server'), optInt(detour, 'server_port'), name, [['plugin', plugin]]), src)
      }
      const pl = optString(o, 'plugin')
      const plugin = isBlank(pl) ? null : pl + ';' + optString(o, 'plugin_opts')
      return parseLink(fiLink('ss', user, server, port, name, [['plugin', plugin]]), src)
    }
    case 'hysteria2': {
      const obfs = optObj(o, 'obfs')
      return parseLink(fiLink('hysteria2', fiEnc(optString(o, 'password')), server, port, name, [
        ['sni', tls.get('sni')], ['insecure', tls.get('allowInsecure')], ['alpn', tls.get('alpn')],
        ['obfs', obfs ? optString(obfs, 'type') : null], ['obfs-password', obfs ? optString(obfs, 'password') : null],
        ['up', positive(optInt(o, 'up_mbps'))], ['down', positive(optInt(o, 'down_mbps'))]]), src)
    }
    case 'hysteria':
      return parseLink(fiLink('hysteria', null, server, port, name, [
        ['auth', optString(o, 'auth_str')], ['peer', tls.get('sni')], ['obfs', optString(o, 'obfs')],
        ['upmbps', positive(optInt(o, 'up_mbps'))], ['downmbps', positive(optInt(o, 'down_mbps'))],
        ['alpn', tls.get('alpn')], ['insecure', tls.get('allowInsecure')]]), src)
    case 'tuic':
      return parseLink(fiLink('tuic', fiEnc(optString(o, 'uuid')) + ':' + fiEnc(optString(o, 'password')), server, port, name, [
        ['sni', tls.get('sni')], ['alpn', tls.get('alpn')], ['congestion_control', optString(o, 'congestion_control')],
        ['udp_relay_mode', optString(o, 'udp_relay_mode')], ['allow_insecure', tls.get('allowInsecure')]]), src)
    case 'anytls':
      return parseLink(fiLink('anytls', fiEnc(optString(o, 'password')), server, port, name, [
        ['sni', tls.get('sni')], ['fp', tls.get('fp')], ['alpn', tls.get('alpn')], ['insecure', tls.get('allowInsecure')]]), src)
    case 'tailscale': {
      const control = optString(o, 'control_url')
      let h = ''
      try { h = orEmpty(javaUri(control).host) } catch { h = '' }
      const host = ifBlank(h, TAILSCALE_CONTROL)
      const flags = [optBool(o, 'ephemeral') && 'ephemeral', optBool(o, 'accept_routes') && 'routes',
        optBool(o, 'exit_node_allow_lan_access') && 'lan'].filter(Boolean).join(',')
      const key = fiEnc(optString(o, 'auth_key'))
      return parseLink(fiLink('tailscale', key !== '' ? key : null, host, 443, name, [
        ['control', nonBlank(control)], ['exit', nonBlank(optString(o, 'exit_node'))],
        ['hostname', nonBlank(optString(o, 'hostname'))], ['flags', flags !== '' ? flags : null]]), src)
    }
    case 'tailcat':
      return parseLink(fiLink('tailcat', null, 'tailcat.dev', 443, name, [
        ['pub', optString(o, 'server_public_key')], ['disco', optString(o, 'server_disco_key')],
        ['psk', nonBlank(optString(o, 'pre_shared_key'))], ['key', nonBlank(optString(o, 'private_key'))],
        ['derp', nonBlank(optString(o, 'derp_map_url'))], ['region', positive(optInt(o, 'derp_region', 0))]]), src)
    case 'masque-client': {
      const v = optInt(o, 'version', 0)
      const t = optObj(o, 'tls')
      const pins = t ? optArr(t, 'certificate_sha256') : null
      return parseLink(fiLink('masque', sbUser(o), server, port > 0 ? port : 443, name, [
        ['version', inRange(v, 1, 2) ? String(v) : null], ['path', nonBlank(optString(o, 'path'))],
        ['sni', tls.get('sni')], ['fp', tls.get('fp')], ['alpn', tls.get('alpn')], ['insecure', tls.get('allowInsecure')],
        ['pin', pins ? arrOptString(pins, 0) : null], ['mtu', positive(optInt(o, 'mtu', 0))]]), src)
    }
    case 'socks':
      return parseLink(fiLink('socks5', sbUser(o), server, port, name), src)
    case 'http':
      return tls.size > 0 ? null : parseLink(fiLink('http', sbUser(o), server, port, name), src)
    case 'ssh': {
      // Kotlin quirk kept: a PEM key is put in pk= as is and dropped by parseSsh.
      const pkArr = optArr(o, 'private_key')
      const hk = optArr(o, 'host_key')
      return parseLink(fiLink('ssh', sbUser(o, 'user'), server, port > 0 ? port : 22, name, [
        ['pk', pkArr ? arrOptString(pkArr, 0) : nonBlank(optString(o, 'private_key'))],
        ['hostkey', hk ? arrOptString(hk, 0) : null]]), src)
    }
    case 'naive':
      return parseLink(fiLink(optBool(o, 'quic') ? 'naive+quic' : 'naive+https', sbUser(o), server, port, name, [['sni', tls.get('sni')]]), src)
    case 'snell': {
      const c = make({
        name, protocol: 'snell', address: server, port, password: optString(o, 'psk'),
        alterId: optInt(o, 'version', 4), hyObfs: optString(o, 'obfs_mode'), host: optString(o, 'obfs_host'), source: src
      })
      return !isBlank(server) && port > 0 ? c : null
    }
    case 'wireguard': {
      // 1.11+ endpoint shape, or the legacy outbound.
      const peers = optArr(o, 'peers')
      const peer = peers ? arrOptObj(peers, 0) : null
      const a = optArr(o, 'address') || optArr(o, 'local_address')
      const addr = a ? a.map((_, i) => arrGetString(a, i)).join(', ') : ''
      const pServer = peer ? optString(peer, 'address') : server
      const pPort = peer ? optInt(peer, 'port') : port
      let conf = '[Interface]\n' + `PrivateKey = ${optString(o, 'private_key')}\n`
      if (!isBlank(addr)) conf += `Address = ${addr}\n`
      const mtu = optInt(o, 'mtu')
      if (mtu > 0) conf += `MTU = ${mtu}\n`
      conf += '[Peer]\n'
      conf += `PublicKey = ${peer ? optString(peer, 'public_key') : optString(o, 'peer_public_key')}\n`
      const psk = peer ? optString(peer, 'pre_shared_key') : optString(o, 'pre_shared_key')
      if (!isBlank(psk)) conf += `PresharedKey = ${psk}\n`
      conf += 'AllowedIPs = 0.0.0.0/0, ::/0\n' + `Endpoint = ${fiHost(pServer)}:${pPort}\n`
      const c = parseWireguardConf(conf, src)
      if (c) c.name = name
      return c
    }
    default: return null
  }
}

const SB_SKIP = ['direct', 'block', 'dns', 'selector', 'urltest', 'tun']

/** ForeignImport.singBox: { configs, warnings } from a sing-box config (object or JSON text). */
function singBox(rootOrText, source = 'PERSONAL') {
  let root = rootOrText
  if (typeof rootOrText === 'string') {
    try { root = parseJsonObject(rootOrText) } catch { return { configs: [], warnings: ['the JSON could not be read'] } }
  }
  if (!isObj(root)) return { configs: [], warnings: ['the JSON could not be read'] }
  const all = []
  for (const k of ['outbounds', 'endpoints']) {
    const a = optArr(root, k)
    if (a) for (let i = 0; i < a.length; i++) { const x = arrOptObj(a, i); if (x) all.push(x) }
  }
  const byTag = new Map(all.map(o => [optString(o, 'tag'), o]))
  // ShadowTLS outbounds are only the carriers of a Shadowsocks detour.
  const carriers = new Set(all.filter(o => optString(o, 'type') === 'shadowtls').map(o => optString(o, 'tag')))
  const configs = []; const warnings = []
  for (const o of all) {
    const type = optString(o, 'type')
    if (SB_SKIP.includes(type) || carriers.has(optString(o, 'tag'))) continue
    const name = ifBlank(optString(o, 'tag'), type)
    let c = null
    try { c = singBoxOutbound(o, byTag, source) } catch { c = null }
    if (c) configs.push(c); else warnings.push(`${name}: type "${type}" skipped (not supported or incomplete)`)
  }
  return { configs, warnings }
}

// ======================================================================
// MiniYaml (ForeignImport.kt): the YAML subset Clash / Mihomo files use
// ======================================================================

/**
 * Block mappings and sequences by indentation, flow {…} / […], quoted and
 * plain scalars, comments. Mappings come back as Map, sequences as Array,
 * scalars as strings (or null for ~ / null).
 */
const MiniYaml = (() => {
  const isItem = t => t === '-' || t.startsWith('- ')

  function stripComment(l) {
    let q = null
    for (let i = 0; i < l.length; i++) {
      const c = l[i]
      if (q !== null) { if (c === q) q = null; continue }
      if (c === '"' || c === "'") q = c
      else if (c === '#' && (i === 0 || l[i - 1] === ' ')) return ktTrimEnd(l.substring(0, i))
    }
    return ktTrimEnd(l)
  }

  function balance(l) {
    let q = null; let d = 0
    for (const c of l) {
      if (q !== null) { if (c === q) q = null; continue }
      if (c === '"' || c === "'") q = c
      else if (c === '{' || c === '[') d++
      else if (c === '}' || c === ']') d--
    }
    return d
  }

  /** Joins flow collections that span several lines. */
  function join(lines) {
    const out = []
    let acc = null; let depth = 0
    for (const l of lines) {
      const d = balance(l)
      if (acc !== null) {
        acc += ' ' + ktTrim(l); depth += d
        if (depth <= 0) { out.push(acc); acc = null }
        continue
      }
      if (d > 0) { acc = l; depth = d } else out.push(l)
    }
    if (acc !== null) out.push(acc)
    return out
  }

  function unquote(s) {
    if (s.length >= 2 && s.startsWith('"') && s.endsWith('"')) {
      return s.substring(1, s.length - 1).split('\\n').join('\n').split('\\"').join('"').split('\\\\').join('\\')
    }
    if (s.length >= 2 && s.startsWith("'") && s.endsWith("'")) return s.substring(1, s.length - 1).split("''").join("'")
    return s
  }

  /** "key: value" split outside quotes; null when the line is not a pair. */
  function keyOf(t) {
    let q = null
    for (let j = 0; j < t.length; j++) {
      const c = t[j]
      if (q !== null) { if (c === q) q = null; continue }
      if ((c === '"' || c === "'") && j === 0) { q = c; continue }
      if (c === ':' && (j === t.length - 1 || t[j + 1] === ' ')) return [unquote(ktTrim(t.substring(0, j))), ktTrim(t.substring(j + 1))]
      if (c === '{' || c === '[') return null
    }
    return null
  }

  class Flow {
    constructor(s) { this.s = s; this.i = 0 }
    ws() { while (this.i < this.s.length && this.s[this.i] === ' ') this.i++ }
    value() {
      const s = this.s
      this.ws()
      if (this.i >= s.length) return null
      if (s[this.i] === '{') {
        this.i++
        const m = new Map()
        this.ws()
        while (this.i < s.length && s[this.i] !== '}') {
          const start = this.i
          const k = this.scalar(true); this.ws()
          if (this.i < s.length && s[this.i] === ':') this.i++
          this.ws()
          const v = this.i < s.length && (s[this.i] === ',' || s[this.i] === '}') ? null : this.value()
          m.set(String(k), v); this.ws()
          if (this.i < s.length && s[this.i] === ',') this.i++
          this.ws()
          // The Kotlin loops forever on a stray ']' inside {…}; stop instead.
          if (this.i === start) throw new Error('MiniYaml: malformed flow mapping')
        }
        this.i++
        return m
      }
      if (s[this.i] === '[') {
        this.i++
        const l = []
        this.ws()
        while (this.i < s.length && s[this.i] !== ']') {
          const start = this.i
          l.push(this.value()); this.ws()
          if (this.i < s.length && s[this.i] === ',') this.i++
          this.ws()
          if (this.i === start) throw new Error('MiniYaml: malformed flow sequence')
        }
        this.i++
        return l
      }
      const v = this.scalar(false)
      return v === '~' || v === 'null' ? null : v
    }
    scalar(stopAtColon) {
      const s = this.s
      this.ws()
      if (this.i < s.length && (s[this.i] === '"' || s[this.i] === "'")) {
        const q = s[this.i]; const start = this.i
        this.i++
        while (this.i < s.length) {
          if (s[this.i] === q) {
            if (q === "'" && this.i + 1 < s.length && s[this.i + 1] === "'") { this.i += 2; continue }
            break
          }
          if (s[this.i] === '\\' && q === '"') this.i++
          this.i++
        }
        this.i++
        return unquote(s.substring(start, Math.min(this.i, s.length)))
      }
      const start = this.i
      while (this.i < s.length && s[this.i] !== ',' && s[this.i] !== '}' && s[this.i] !== ']' &&
        !(stopAtColon && s[this.i] === ':' && (this.i + 1 >= s.length || s[this.i + 1] === ' '))) this.i++
      return ktTrim(s.substring(start, this.i))
    }
  }

  function inline(s) {
    const t = ktTrim(s)
    if (t.startsWith('{') || t.startsWith('[')) return new Flow(t).value()
    if (t === '~' || t === 'null') return null
    return unquote(t)
  }

  class P {
    constructor(ls) { this.ls = ls; this.i = 0 }
    node() {
      if (this.i >= this.ls.length) return null
      const l = this.ls[this.i]
      return isItem(l.text) ? this.seq(l.indent) : this.map(l.indent)
    }
    seq(indent) {
      const ls = this.ls
      const out = []
      while (this.i < ls.length && ls[this.i].indent === indent && isItem(ls[this.i].text)) {
        const rest = ktTrimStart(ls[this.i].text.substring(1))
        if (rest === '') {
          this.i++
          out.push(this.i < ls.length && ls[this.i].indent > indent ? this.node() : null)
          continue
        }
        if (keyOf(rest) !== null && !rest.startsWith('{') && !rest.startsWith('[') && !rest.startsWith('"') && !rest.startsWith("'")) {
          // "- key: value" opens a mapping whose keys align with "key".
          const inner = indent + (ls[this.i].text.length - rest.length)
          ls[this.i] = { indent: inner, text: rest }
          out.push(this.map(inner))
        } else { out.push(inline(rest)); this.i++ }
      }
      return out
    }
    map(indent) {
      const ls = this.ls
      const out = new Map()
      while (this.i < ls.length && ls[this.i].indent === indent && !isItem(ls[this.i].text)) {
        const kv = keyOf(ls[this.i].text)
        if (kv === null) break
        const [k, v] = kv
        this.i++
        let val
        if (v !== '') val = inline(v)
        else if (this.i < ls.length && ls[this.i].indent > indent) val = this.node()
        else if (this.i < ls.length && ls[this.i].indent === indent && ls[this.i].text.startsWith('- ')) val = this.seq(indent)
        else val = null
        out.set(k, val)
      }
      return out
    }
  }

  function parse(text) {
    const lines = join(ktLines(String(text).replace(/\t/g, '  ')).map(stripComment))
      .filter(l => !isBlank(l) && ktTrim(l) !== '---' && ktTrim(l) !== '...')
      .map(l => ({ indent: l.length - ktTrimStart(l).length, text: ktTrim(l) }))
    if (lines.length === 0) return null
    return new P(lines).node()
  }

  return { parse, inline }
})()

// ======================================================================
// ConfigParser.parseBundle and SubscriptionFetcher body decoding
// ======================================================================

/** ConfigParser.parseBundle: exactly what the phone does with pasted text or a file. */
function parseConfigBundle(text, source = 'PERSONAL') {
  try {
    const trimmed = ktTrim(String(text))
    if (trimmed === '') return []
    const lower = trimmed.toLowerCase()
    if (lower.includes('[interface]') && lower.includes('[peer]')) {
      const single = parseWireguardConf(trimmed, source)
      if (single) return [single]
    }
    if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
      const fromJson = parseJsonOutbounds(trimmed, source)
      if (fromJson.length) return fromJson
    }
    // Clash / Clash Meta / Mihomo YAML subscriptions and files.
    if (looksLikeClash(trimmed)) {
      const clash = importClash(trimmed, source).configs
      if (clash.length) return clash
    }
    // An .ovpn profile (MainActivity: a .ovpn file, or text with a client /
    // remote line, goes to GhajarOpenVpnBridge): one config, protocol "openvpn".
    if (openvpn.isOvpn(trimmed)) {
      const ovpn = openvpn.ovpnToConfig(trimmed, { source, make })
      if (ovpn) return [ovpn]
    }
    // Kotlin quirk kept: each line is parsed alone, so a pasted block of N Tor
    // bridge lines becomes N single-bridge configs here (parseLink keeps them together).
    return trimmed.split(/[\n\r]/).map(ktTrim).filter(l => l !== '').map(l => parseLink(l, source)).filter(Boolean)
  } catch { return [] }
}

/** Substrings SubscriptionFetcher.hasConfig looks for (duplicates as in the Kotlin list). */
const SUBSCRIPTION_SCHEMES = Object.freeze([
  'vless://', 'vmess://', 'trojan://', 'ss://', 'ssr://',
  'socks5://', 'socks4://', 'socks://', 'http://',
  'hysteria2://', 'hysteria://', 'hy2://', 'hy://', 'tuic://',
  'ikev2://', 'wireguard://', 'wg://', 'warp://', 'juicity://',
  'anytls://', 'mieru://', 'naive+', 'ssh://', 'openconnect://', 'anyconnect://', 'dnstt://', 'vaydns://', 'noizdns://',
  'masterdns://', 'stormdns://', 'cottendns://', 'slipstream://', 'mierus://', 'brook://', 'amneziawg://',
  'naive+https://', 'naive+quic://', 'sstp://', 'softether://'
])

function hasConfig(text) {
  const lower = text.toLowerCase()
  if (SUBSCRIPTION_SCHEMES.some(s => lower.includes(s))) return true
  const t = ktTrim(text)
  return (t.startsWith('{') || t.startsWith('[')) && (lower.includes('"outbounds"') || lower.includes('"protocol"'))
}

function stripInvisible(raw) {
  return ktTrim(String(raw).replace(/[\uFEFF\u200B\u200E\u200F]/g, ''))
}

/** SubscriptionFetcher.decodeMaybeBase64: the readable text of a subscription body. */
function decodeSubscriptionBody(body) {
  const trimmed = stripInvisible(body)
  if (hasConfig(trimmed)) return trimmed
  let packed = ''
  for (const ch of trimmed) if (!isKtWs(ch)) packed += ch
  // Android flags DEFAULT / URL_SAFE (NO_WRAP and NO_PADDING only matter when encoding).
  const padded = packed + '='.repeat((4 - (packed.length % 4)) % 4)
  // Deliberate deviation: the phone always tries DEFAULT first, whose lenient
  // decoder skips '-' and '_' and returns shifted garbage that still contains
  // the first "vless://", so a URL-safe body loses its configs. When the body
  // only uses the URL-safe alphabet, try URL_SAFE first; every body the phone
  // decodes cleanly decodes identically here.
  const urlFirst = /[-_]/.test(packed) && !/[+/]/.test(packed)
  for (const cand of [packed, padded]) {
    for (const url of urlFirst ? [true, false] : [false, true]) {
      let out = null
      try { out = utf8.decode(androidB64Decode(cand, url)) } catch { out = null }
      if (out !== null && hasConfig(out)) return out
    }
  }
  return trimmed
}

class SubscriptionError extends Error {
  /** kind: 'HTTP' | 'EMPTY' | 'NOT_CONFIG' | 'CLASH' (SubscriptionError.Kind). */
  constructor(kind, code = 0) {
    super(`subscription: ${kind}${code ? ' ' + code : ''}`)
    this.name = 'SubscriptionError'
    this.kind = kind
    this.code = code
  }
}

function classify(text) {
  const t = ktTrim(text)
  const lower = t.toLowerCase()
  const isYaml = !t.startsWith('{') && !t.startsWith('[') &&
    (lower.includes('proxy-groups:') || /^[ \t\n\x0B\f\r]*proxies:[ \t\n\x0B\f\r]*$/m.test(lower))
  if (t === '') return new SubscriptionError('EMPTY')
  if (isYaml) return new SubscriptionError('CLASH')
  return new SubscriptionError('NOT_CONFIG')
}

/** SubscriptionFetcher.parseBody: configs, or throws a SubscriptionError saying why there are none. */
function parseSubscriptionBody(body, source = 'PERSONAL') {
  const text = decodeSubscriptionBody(String(body == null ? '' : body))
  const configs = parseConfigBundle(text, source)
  if (configs.length === 0) throw classify(text)
  return configs
}

/** The subscription-userinfo header: { upload, download, total, expire, used, hasData } or null. */
function parseUserInfo(header) {
  if (header == null || isBlank(String(header))) return null
  const map = new Map()
  for (const part of String(header).split(';')) {
    const eq = part.indexOf('=')
    if (eq < 0) continue
    const v = ktTrim(part.substring(eq + 1))
    map.set(ktTrim(part.substring(0, eq)), /^[+-]?\d+$/.test(v) ? Number(v) : 0)
  }
  const info = { upload: map.get('upload') || 0, download: map.get('download') || 0, total: map.get('total') || 0, expire: map.get('expire') || 0 }
  info.used = info.upload + info.download
  info.hasData = info.total > 0 || info.expire > 0
  return info
}

// ======================================================================
// Public entry points
// ======================================================================

/** ConfigParser.parse: one share link (or Tor bridge block / WireGuard .conf) → config, else null. */
function parseLink(text, source = 'PERSONAL') {
  if (typeof text !== 'string') return null
  try { return parseUri(text, source) } catch { return null }
}

/**
 * Everything the app imports: share links (one per line), subscription
 * bodies (base64 or plain), Xray / sing-box JSON, Clash YAML, WireGuard
 * .conf, Tor bridge lines. Text the phone's paste path reads is read the
 * same way; when that finds nothing the subscription decoder gets a turn
 * (the phone runs it on fetched subscription bodies).
 */
function parseBundle(text, source = 'PERSONAL') {
  if (typeof text !== 'string') return []
  try {
    const direct = parseConfigBundle(text, source)
    if (direct.length) return direct
    const decoded = decodeSubscriptionBody(text)
    return decoded === ktTrim(text) ? [] : parseConfigBundle(decoded, source)
  } catch { return [] }
}

// ======================================================================
// ConfigShare.toLink
// ======================================================================

/** ConfigShare.enc: URLEncoder.encode (spaces as '+'). */
const enc = javaUrlEncode
const bracket = a => (a.includes(':') ? `[${a}]` : a)
const flag = b => (b ? '1' : '')
const posStr = n => (n > 0 ? String(n) : '')

function simpleLink(scheme, userInfo, c, params) {
  const query = params.filter(([, v]) => v !== '').map(([k, v]) => k + '=' + enc(v)).join('&')
  const user = userInfo === '' || userInfo === ':' ? '' : `${userInfo}@`
  return `${scheme}://${user}${bracket(c.address)}:${c.port}` + (query === '' ? '' : `?${query}`) + '#' + enc(c.name)
}
const userAndPass = c => enc(c.uuid) + (c.password !== '' ? ':' + enc(c.password) : '')

function userLink(scheme, userInfo, c, includeEncryption) {
  const params = [['type', c.network], ['security', c.security]]
  if (includeEncryption && c.encryption !== '') params.push(['encryption', c.encryption])
  const opt = (k, v) => { if (v !== '') params.push([k, v]) }
  opt('flow', c.flow); opt('sni', c.sni); opt('pbk', c.publicKey); opt('sid', c.shortId)
  opt('fp', c.fingerprint); opt('path', c.path); opt('host', c.host); opt('serviceName', c.serviceName)
  opt('mode', c.mode); opt('alpn', c.alpn); opt('headerType', c.headerType)
  // finalmask and ECH: not standard keys, so other clients ignore them.
  opt('mask', c.maskType); opt('maskDomain', c.maskDomain); opt('maskPass', c.maskPassword); opt('ech', c.echConfigList)
  const query = params.map(([k, v]) => `${k}=${enc(v)}`).join('&')
  // Kotlin quirk kept: the address is not bracketed here, IPv6 included (it still parses back).
  return `${scheme}://${enc(userInfo)}@${c.address}:${c.port}?${query}#${enc(c.name)}`
}

function hysteria2Link(c) {
  const params = []
  if (c.sni !== '') params.push(['sni', c.sni])
  if (c.alpn !== '') params.push(['alpn', c.alpn])
  if (c.allowInsecure) params.push(['insecure', '1'])
  if (c.hyObfsPassword !== '') {
    params.push(['obfs', ifEmpty(c.hyObfs, 'salamander')])
    params.push(['obfs-password', c.hyObfsPassword])
  }
  if (c.hyUpMbps > 0) params.push(['upmbps', String(c.hyUpMbps)])
  if (c.hyDownMbps > 0) params.push(['downmbps', String(c.hyDownMbps)])
  const query = params.map(([k, v]) => k + '=' + enc(v)).join('&')
  return 'hysteria2://' + enc(c.password) + '@' + bracket(c.address) + ':' + c.port + (query === '' ? '' : '?' + query) + '#' + enc(c.name)
}

function vmessLink(c) {
  const o = {
    v: '2', ps: c.name, add: c.address, port: String(c.port), id: c.uuid, aid: String(c.alterId),
    scy: ifEmpty(c.encryption, 'auto'), net: c.network, type: ifEmpty(c.headerType, 'none'),
    host: c.host, path: c.path, tls: c.security === 'tls' ? 'tls' : '', sni: c.sni, fp: c.fingerprint
  }
  return 'vmess://' + b64Std(JSON.stringify(o))
}

function toLinkRaw(c) {
  switch (c.protocol) {
    case 'vless': return userLink('vless', c.uuid, c, true)
    case 'trojan': return userLink('trojan', c.password, c, false)
    case 'vmess': return vmessLink(c)
    case 'shadowsocks': return `ss://${b64UrlNoPad(`${c.method}:${c.password}`)}@${c.address}:${c.port}#${enc(c.name)}`
    case 'hysteria2': return hysteria2Link(c)
    case 'tuic': return simpleLink('tuic', enc(c.uuid) + ':' + enc(c.password), c, [
      ['sni', c.sni], ['alpn', c.alpn], ['congestion_control', c.method],
      ['udp_relay_mode', c.mode], ['allow_insecure', flag(c.allowInsecure)]])
    case 'hysteria': return simpleLink('hysteria', '', c, [
      ['auth', c.password], ['peer', c.sni], ['alpn', c.alpn],
      ['upmbps', posStr(c.hyUpMbps)], ['downmbps', posStr(c.hyDownMbps)],
      ['obfs', c.hyObfs], ['obfsParam', c.hyObfsPassword], ['insecure', flag(c.allowInsecure)]])
    case 'masque': return simpleLink('masque',
      c.uuid === '' && c.password === '' ? '' : userAndPass(c), c, [
        ['version', c.mode === '1' || c.mode === '2' ? c.mode : ''], ['path', c.path], ['sni', c.sni],
        ['alpn', c.alpn], ['fp', c.fingerprint], ['pin', c.pinnedCertSha256],
        ['insecure', flag(c.allowInsecure)], ['mtu', posStr(c.mtu)]])
    // The auth key and the tailcat private key are never put in a share link.
    case 'tailscale': return simpleLink('tailscale', '', c, [
      ['control', c.host], ['exit', c.path], ['hostname', c.sni], ['flags', c.headerType]])
    case 'tailcat': return simpleLink('tailcat', '', c, [
      ['pub', c.publicKey], ['disco', c.uuid], ['psk', c.password], ['derp', c.host], ['region', c.mode]])
    case 'anytls': return simpleLink('anytls', enc(c.password), c, [
      ['sni', c.sni], ['fp', c.fingerprint], ['insecure', flag(c.allowInsecure)]])
    // The private key is never put in a share link.
    case 'ssh': {
      const t = optObj(extraJson(c), 'transport')
      const tp = t === null ? [] : [
        ['mode', optString(t, 'mode')],
        ['proxy', isBlank(optString(t, 'proxyHost')) ? '' : optString(t, 'proxyHost') + ':' + optInt(t, 'proxyPort')],
        ['sni', optString(t, 'sni')],
        ['payload', optString(t, 'payload') !== '' ? b64UrlNoPad(optString(t, 'payload')) : ''],
        ['wspath', optString(t, 'wsPath')], ['wshost', optString(t, 'wsHost')], ['ua', optString(t, 'ua')],
        ['wsframing', flag(optBool(t, 'wsFraming'))], ['verify', flag(optBool(t, 'verify'))]]
      return simpleLink('ssh', userAndPass(c), c, [['hostkey', c.publicKey], ...tp])
    }
    case 'naive': return simpleLink(c.mode === 'quic' ? 'naive+quic' : 'naive+https', userAndPass(c), c,
      [['sni', c.sni !== c.address ? c.sni : '']])
    case 'softether': {
      const x = extraJson(c)
      return simpleLink('softether', enc(c.uuid) + ':' + enc(c.password), c, [
        ['hub', optString(x, 'hub')], ['sni', c.sni], ['pin', c.pinnedCertSha256],
        ['allow_insecure', flag(c.allowInsecure)], ['auth', optBool(x, 'plain') ? 'plain' : ''],
        ['ip', optString(x, 'ip')], ['gw', optString(x, 'gw')], ['dns', optString(x, 'dns')],
        ['mtu', posStr(c.mtu)]])
    }
    case 'sstp': return simpleLink('sstp', enc(c.uuid) + ':' + enc(c.password), c, [
      ['sni', c.sni], ['auth', c.method === 'pap' || c.method === 'mschapv2' ? c.method : ''],
      ['allow_insecure', flag(c.allowInsecure)], ['pin', c.pinnedCertSha256], ['mtu', posStr(c.mtu)]])
    case 'juicity': return simpleLink('juicity', enc(c.uuid) + ':' + enc(c.password), c, [
      ['congestion_control', c.method], ['sni', c.sni], ['allow_insecure', flag(c.allowInsecure)],
      ['pinned_certchain_sha256', c.pinnedCertSha256]])
    case 'shadowtls': {
      const ss = optObj(extraJson(c), 'ss') || {}
      const user = b64UrlNoPad(optString(ss, 'method') + ':' + optString(ss, 'password'))
      const plugin = `shadow-tls;host=${c.sni};password=${c.password};version=${inRange(c.alterId, 1, 3) ? c.alterId : 3}`
      return `ss://${user}@${bracket(c.address)}:${c.port}?plugin=` + enc(plugin) + '#' + enc(c.name)
    }
    case 'mieru': case 'brook': {
      const url = optString(extraJson(c), 'url')
      return isBlank(url) ? '' : url + '#' + enc(c.name)
    }
    case 'amneziawg': {
      const conf = optString(extraJson(c), 'conf')
      return isBlank(conf) ? '' : 'amneziawg://' + b64UrlNoPad(conf) + '#' + enc(c.name)
    }
    case 'openconnect': {
      const x = extraJson(c)
      return simpleLink('openconnect', userAndPass(c), c, [
        ['flavor', c.mode], ['sni', c.sni], ['pin', c.pinnedCertSha256], ['insecure', flag(c.allowInsecure)],
        ['mtu', posStr(c.mtu)],
        ['authgroup', optString(x, 'authGroup')], ['os', optString(x, 'reportedOs')],
        ['ua', optString(x, 'userAgent')], ['reconnect', posStr(optInt(x, 'reconnect', 0))],
        ['nodtls', flag(optBool(x, 'noUdp'))], ['noipv6', flag(optBool(x, 'ipv6Off'))]])
    }
    // The client certificate and key stay on this device.
    case 'masterdns': case 'stormdns': case 'cottendns': {
      const x = extraJson(c)
      const first = bracket(c.address) + ':' + c.port
      const resolvers = [first, ...optString(x, 'resolvers').split(',')].map(ktTrim).filter(r => r !== '')
      const params = [['resolver', resolvers.join(',')], ['enc', String(optInt(x, 'enc', 1))], ['transport', ifEmpty(c.mode, 'udp')]]
      return c.protocol + '://' + enc(c.password) + '@' + c.host + '?' + params.map(([k, v]) => k + '=' + enc(v)).join('&') + '#' + enc(c.name)
    }
    case 'dnstt': case 'vaydns': case 'noizdns': case 'slipstream': {
      const user = userAndPass(c)
      const params = [['pubkey', c.publicKey], ['transport', ifEmpty(c.mode, 'udp')],
        c.mode === 'doh' ? ['doh', c.path] : ['resolver', bracket(c.address) + ':' + c.port],
        ['upstream', ifEmpty(c.method, 'socks')]]
      const x = extraJson(c)
      const opts = []
      if (optString(x, 'recordType') !== '') opts.push(['record', optString(x, 'recordType')])
      if (has(x, 'dnsttCompat')) opts.push(['compat', optBool(x, 'dnsttCompat') ? '1' : '0'])
      if (optInt(x, 'maxQnameLen', 0) > 0) opts.push(['qname', String(optInt(x, 'maxQnameLen', 0))])
      if (optInt(x, 'clientIdSize', 0) > 0) opts.push(['clientid', String(optInt(x, 'clientIdSize', 0))])
      if (has(x, 'noiz')) opts.push(['noiz', optBool(x, 'noiz') ? '1' : '0'])
      if (has(x, 'stealth')) opts.push(['stealth', optBool(x, 'stealth') ? '1' : '0'])
      if (optString(x, 'authoritative') !== '') opts.push(['authoritative', optString(x, 'authoritative')])
      if (optString(x, 'cc') !== '') opts.push(['cc', optString(x, 'cc')])
      const query = [...params, ...opts].filter(([, v]) => v !== '').map(([k, v]) => k + '=' + enc(v)).join('&')
      return c.protocol + '://' + (user === '' || user === ':' ? '' : `${user}@`) + c.host + '?' + query + '#' + enc(c.name)
    }
    default: return ''
  }
}

/**
 * ConfigShare.toLink: the share link for a config, or null where the phone
 * has none (socks, http, wireguard, ikev2, tor, psiphon, snell, …).
 */
function toShareLink(config) {
  if (!config || typeof config !== 'object') return null
  try {
    const c = { ...FIELD_DEFAULTS, ...config }
    for (const k of Object.keys(FIELD_DEFAULTS)) {
      const d = FIELD_DEFAULTS[k]
      if (typeof d === 'string' && typeof c[k] !== 'string') c[k] = c[k] == null ? d : String(c[k])
      if (typeof d === 'number' && typeof c[k] !== 'number') c[k] = Number(c[k]) || 0
    }
    const link = toLinkRaw(c)
    return link === '' ? null : link
  } catch { return null }
}

// ======================================================================
// Engine routing (engine/CoreManager.kt EngineRouting, SingBoxConfig.PROTOCOLS)
// ======================================================================

const SINGBOX_PROTOCOLS = Object.freeze(['tuic', 'hysteria', 'anytls', 'ssh', 'snell', 'openconnect', 'masque',
  'dnstt', 'vaydns', 'noizdns', 'masterdns', 'stormdns', 'cottendns', 'slipstream',
  'amneziawg', 'mieru', 'brook', 'juicity', 'naive', 'shadowtls', 'sstp', 'softether', 'tailscale', 'tailcat'])
const DNSTT_FAMILY = Object.freeze(['dnstt', 'vaydns', 'noizdns', 'slipstream'])
const MASTERDNS_FAMILY = Object.freeze(['masterdns', 'stormdns', 'cottendns'])

/** OblivionOptions(oblivionJson).aether: core is "aether" or "chain" (default "psiphon"). */
function oblivionAether(raw) {
  try {
    if (isBlank(orEmpty(raw))) return false
    const core = optString(parseJsonObject(raw), 'core', 'psiphon')
    return core === 'aether' || core === 'chain'
  } catch { return false }
}

/** EngineRouting.engineFor, exactly: 'XRAY' | 'SINGBOX' | 'IKEV2' | 'OPENVPN' | 'AETHER' | 'PSIPHON' | 'TOR'. */
function engineFor(config) {
  const p = config && typeof config.protocol === 'string' ? config.protocol : ''
  if (p === 'ikev2') return 'IKEV2'
  if (p === 'openvpn') return 'OPENVPN'
  if (SINGBOX_PROTOCOLS.includes(p)) return 'SINGBOX'
  if (p === 'aether') return 'AETHER'
  if (p === 'psiphon' && oblivionAether(config.oblivionJson)) return 'AETHER'
  if (p === 'psiphon') return 'PSIPHON'
  if (p === 'tor') return 'TOR'
  return 'XRAY'
}

/**
 * Which desktop engine carries a config. Same as engineFor, except that the
 * DNS-tunnel protocols, which the phone runs as a helper process in front of
 * sing-box (SingBoxConfig "sidecar"), are reported as 'dnstunnel'.
 */
function protocolFamily(config) {
  const p = config && typeof config.protocol === 'string' ? config.protocol : ''
  if (DNSTT_FAMILY.includes(p) || MASTERDNS_FAMILY.includes(p)) return 'dnstunnel'
  return engineFor(config).toLowerCase()
}

module.exports = {
  // main entry points
  parseLink, parseBundle, toShareLink, protocolFamily, defaults, SCHEMES,
  // ProxyConfig
  fromJson, toJson, extraJson, FIELD_DEFAULTS,
  // finer-grained importers (same names as the Kotlin)
  parseConfigBundle, parseJsonOutbounds, parseWireguardConf,
  importClash, importSingBox: singBox, looksLikeClash, looksLikeSingBox, MiniYaml,
  // subscriptions
  decodeSubscriptionBody, parseSubscriptionBody, parseUserInfo, SubscriptionError, SUBSCRIPTION_SCHEMES,
  // engine routing
  engineFor, SINGBOX_PROTOCOLS, DNSTT_FAMILY, MASTERDNS_FAMILY,
  // JVM-compatible codecs, exported for tests and the other engine modules
  _internal: { javaUrlDecode, javaUrlEncode, formDecode, pctDecode, decodeB64, javaB64Decode, androidB64Decode, toIntOrNull, javaUri }
}
