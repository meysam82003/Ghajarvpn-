// The connection core of the desktop app: Xray, run as a local proxy only.
//
// Nothing here touches the system's proxy or routes: the core listens on
// 127.0.0.1 (SOCKS5 and HTTP) and only what is pointed at it goes through
// the VPN - the Ghajar browser extension points the browser at it, so the
// browser alone is connected, never the whole device.
//
// It also serves a small control API on 127.0.0.1 for that extension.
const { spawn } = require('child_process')
const fs = require('fs')
const http = require('http')
const https = require('https')
const net = require('net')
const path = require('path')

const API_PORT = 47823
const DEFAULT_SOCKS = 10808
const DEFAULT_HTTP = 10809

// ------------------------------------------------------------------ links

function b64decode(s) {
  const clean = String(s).trim().replace(/-/g, '+').replace(/_/g, '/').replace(/\s+/g, '')
  return Buffer.from(clean + '='.repeat((4 - (clean.length % 4)) % 4), 'base64').toString('utf8')
}

function nameOf(link, fallback) {
  const i = link.indexOf('#')
  if (i < 0) return fallback
  try { return decodeURIComponent(link.slice(i + 1)).trim() || fallback } catch { return link.slice(i + 1).trim() || fallback }
}

/** Splits host:port, IPv6 in brackets included. */
function hostPort(u) {
  const host = u.hostname.replace(/^\[|\]$/g, '')
  const port = parseInt(u.port, 10)
  return { host, port: Number.isFinite(port) ? port : 443 }
}

function streamFrom(q, fallbackSecurity) {
  const network = (q.get('type') || q.get('net') || 'tcp').toLowerCase()
  const security = (q.get('security') || fallbackSecurity || 'none').toLowerCase()
  const s = { network: network === 'h2' ? 'http' : network, security: security === 'none' ? 'none' : security }
  const host = q.get('host') || ''
  const pathV = q.get('path') || '/'
  const sni = q.get('sni') || q.get('peer') || ''
  const fp = q.get('fp') || ''
  const alpn = (q.get('alpn') || '').split(',').map(x => x.trim()).filter(Boolean)
  if (s.security === 'tls') {
    s.tlsSettings = { serverName: sni || host || undefined, fingerprint: fp || undefined, alpn: alpn.length ? alpn : undefined,
      allowInsecure: q.get('allowInsecure') === '1' || q.get('insecure') === '1' }
  } else if (s.security === 'reality') {
    s.realitySettings = { serverName: sni, fingerprint: fp || 'chrome', publicKey: q.get('pbk') || '', shortId: q.get('sid') || '',
      spiderX: q.get('spx') || '' }
  }
  switch (s.network) {
    case 'ws': s.wsSettings = { path: pathV, host: host || undefined }; break
    case 'grpc': s.grpcSettings = { serviceName: q.get('serviceName') || q.get('path') || '', multiMode: (q.get('mode') || '') === 'multi' }; break
    case 'xhttp': case 'splithttp': s.network = 'xhttp'; s.xhttpSettings = { path: pathV, host: host || undefined, mode: q.get('mode') || 'auto' }; break
    case 'httpupgrade': s.httpupgradeSettings = { path: pathV, host: host || undefined }; break
    case 'http': s.httpSettings = { path: pathV, host: host ? host.split(',') : undefined }; break
    case 'tcp':
      if ((q.get('headerType') || '') === 'http') {
        s.tcpSettings = { header: { type: 'http', request: { path: pathV.split(','), headers: host ? { Host: host.split(',') } : {} } } }
      }
      break
  }
  return s
}

/** One share link → { name, protocol, host, port, outbound } or null when the link is not usable. */
function parseLink(raw) {
  const link = String(raw || '').trim()
  const scheme = link.split('://')[0].toLowerCase()
  try {
    if (scheme === 'vmess') {
      const j = JSON.parse(b64decode(link.slice(8).split('#')[0]))
      const q = new URLSearchParams()
      q.set('type', j.net || 'tcp'); q.set('security', j.tls === 'tls' ? 'tls' : 'none')
      if (j.host) q.set('host', j.host); if (j.path) q.set('path', j.path); if (j.sni) q.set('sni', j.sni)
      if (j.alpn) q.set('alpn', j.alpn); if (j.fp) q.set('fp', j.fp); if (j.type) q.set('headerType', j.type)
      if (j.net === 'grpc' && j.path) q.set('serviceName', j.path)
      const port = parseInt(j.port, 10)
      return {
        name: String(j.ps || 'VMess'), protocol: 'vmess', host: String(j.add), port,
        outbound: { protocol: 'vmess', settings: { vnext: [{ address: String(j.add), port, users: [{ id: String(j.id), alterId: parseInt(j.aid || '0', 10) || 0, security: j.scy || 'auto' }] }] }, streamSettings: streamFrom(q) }
      }
    }
    if (scheme === 'vless' || scheme === 'trojan') {
      const u = new URL(link)
      const { host, port } = hostPort(u)
      const q = u.searchParams
      const id = decodeURIComponent(u.username)
      if (!id || !host) return null
      const outbound = scheme === 'vless'
        ? { protocol: 'vless', settings: { vnext: [{ address: host, port, users: [{ id, encryption: q.get('encryption') || 'none', flow: q.get('flow') || undefined }] }] }, streamSettings: streamFrom(q) }
        : { protocol: 'trojan', settings: { servers: [{ address: host, port, password: id }] }, streamSettings: streamFrom(q, 'tls') }
      return { name: nameOf(link, scheme.toUpperCase()), protocol: scheme, host, port, outbound }
    }
    if (scheme === 'ss') {
      let body = link.slice(5).split('#')[0]
      let method, password, host, port
      if (body.includes('@')) {
        const at = body.lastIndexOf('@')
        let user = decodeURIComponent(body.slice(0, at))
        if (!user.includes(':')) user = b64decode(user)
        ;[method, password] = [user.slice(0, user.indexOf(':')), user.slice(user.indexOf(':') + 1)]
        const hp = body.slice(at + 1).split('?')[0].replace(/\/$/, '')
        const u = new URL('ss://x@' + hp)
        ;({ host, port } = hostPort(u))
      } else {
        const dec = b64decode(body)
        const at = dec.lastIndexOf('@')
        ;[method, password] = [dec.slice(0, dec.indexOf(':')), dec.slice(dec.indexOf(':') + 1, at)]
        const u = new URL('ss://x@' + dec.slice(at + 1))
        ;({ host, port } = hostPort(u))
      }
      if (!method || !host) return null
      return { name: nameOf(link, 'Shadowsocks'), protocol: 'ss', host, port,
        outbound: { protocol: 'shadowsocks', settings: { servers: [{ address: host, port, method, password }] } } }
    }
  } catch { return null }
  return null
}

/** The links inside a subscription body (plain lines or base64). */
function linksFrom(body) {
  let text = String(body || '')
  if (!text.includes('://')) { try { text = b64decode(text) } catch { return [] } }
  return text.split(/\r?\n|\r/).map(l => l.trim()).filter(l => /^(vless|vmess|trojan|ss):\/\//i.test(l))
}

function fetchText(url, redirects = 3) {
  return new Promise((resolve, reject) => {
    const lib = url.startsWith('https:') ? https : http
    const req = lib.get(url, { headers: { 'User-Agent': 'v2rayN/7.0', Accept: '*/*' }, timeout: 20000 }, res => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location && redirects > 0) {
        res.resume(); resolve(fetchText(new URL(res.headers.location, url).toString(), redirects - 1)); return
      }
      if (res.statusCode !== 200) { res.resume(); reject(new Error('subscription ' + res.statusCode)); return }
      let data = ''
      res.setEncoding('utf8')
      res.on('data', c => { data += c; if (data.length > 4e6) req.destroy() })
      res.on('end', () => resolve(data))
    })
    req.on('timeout', () => req.destroy(new Error('timeout')))
    req.on('error', reject)
  })
}

// ------------------------------------------------------------------ config

function xrayConfig(server, opts) {
  const rules = [
    { type: 'field', ip: ['geoip:private'], outboundTag: 'direct' },
    { type: 'field', domain: ['geosite:private'], outboundTag: 'direct' }
  ]
  if (opts.directIran) {
    // Iranian sites stay direct: faster, and many refuse foreign addresses.
    rules.push({ type: 'field', domain: ['regexp:\\.ir$'], outboundTag: 'direct' })
    rules.push({ type: 'field', ip: ['geoip:ir'], outboundTag: 'direct' })
  }
  return {
    log: { loglevel: 'warning' },
    inbounds: [
      { tag: 'socks', listen: '127.0.0.1', port: opts.socksPort, protocol: 'socks', settings: { udp: true, auth: 'noauth' },
        sniffing: { enabled: true, destOverride: ['http', 'tls', 'quic'], routeOnly: true } },
      { tag: 'http', listen: '127.0.0.1', port: opts.httpPort, protocol: 'http', settings: {},
        sniffing: { enabled: true, destOverride: ['http', 'tls'], routeOnly: true } }
    ],
    outbounds: [
      { ...server.outbound, tag: 'proxy' },
      { protocol: 'freedom', tag: 'direct' },
      { protocol: 'blackhole', tag: 'block' }
    ],
    // AsIs: domains are never resolved on this computer, so DNS does not leak around the VPN.
    routing: { domainStrategy: 'AsIs', rules }
  }
}

function freePort(preferred) {
  return new Promise(resolve => {
    const tryPort = p => {
      const s = net.createServer()
      s.once('error', () => (p < preferred + 40 ? tryPort(p + 1) : resolve(0)))
      s.listen(p, '127.0.0.1', () => s.close(() => resolve(p)))
    }
    tryPort(preferred)
  })
}

function waitForPort(port, ms) {
  const until = Date.now() + ms
  return new Promise(resolve => {
    const attempt = () => {
      const sock = net.connect(port, '127.0.0.1')
      sock.once('connect', () => { sock.destroy(); resolve(true) })
      sock.once('error', () => { sock.destroy(); Date.now() < until ? setTimeout(attempt, 150) : resolve(false) })
    }
    attempt()
  })
}

function tcpPing(host, port, ms = 3000) {
  return new Promise(resolve => {
    const t0 = Date.now()
    const sock = net.connect({ host, port })
    const done = v => { sock.destroy(); resolve(v) }
    sock.setTimeout(ms, () => done(-1))
    sock.once('connect', () => done(Date.now() - t0))
    sock.once('error', () => done(-1))
  })
}

// ------------------------------------------------------------------ manager

class Core {
  constructor({ dataDir, binDir, onChange }) {
    this.dataDir = dataDir
    this.binDir = binDir
    this.onChange = onChange || (() => {})
    this.proc = null
    this.state = { connected: false, connecting: false, error: '', socksPort: 0, httpPort: 0, server: null }
    this.store = { serviceName: '', subscriptionUrl: '', servers: [], selected: 0, directIran: true }
    try { Object.assign(this.store, JSON.parse(fs.readFileSync(this.file(), 'utf8'))) } catch { /* first run */ }
  }

  file() { return path.join(this.dataDir, 'core-servers.json') }
  save() { try { fs.writeFileSync(this.file(), JSON.stringify(this.store)) } catch { /* read-only */ } }
  binary() { return path.join(this.binDir, process.platform === 'win32' ? 'xray.exe' : 'xray') }
  available() { return fs.existsSync(this.binary()) }

  status() {
    const s = this.state
    return {
      available: this.available(), connected: s.connected, connecting: s.connecting, error: s.error,
      socks: s.connected ? { host: '127.0.0.1', port: s.socksPort } : null,
      http: s.connected ? { host: '127.0.0.1', port: s.httpPort } : null,
      server: s.server ? { name: s.server.name, protocol: s.server.protocol } : null,
      service: this.store.serviceName, selected: this.store.selected, directIran: this.store.directIran
    }
  }

  servers() {
    return this.store.servers.map((l, i) => { const p = parseLink(l); return p ? { index: i, name: p.name, protocol: p.protocol, host: p.host, port: p.port } : null }).filter(Boolean)
  }

  emit() { try { this.onChange(this.status()) } catch { /* window gone */ } }

  /** Sets the servers of the chosen service: its links, or its subscription when there are none. */
  async setService({ name = '', configs = [], subscriptionUrl = '' } = {}) {
    let links = (configs || []).filter(l => parseLink(l))
    if (!links.length && subscriptionUrl) links = linksFrom(await fetchText(subscriptionUrl)).filter(l => parseLink(l))
    if (!links.length) throw new Error('این سرویس هنوز کانفیگ قابل اتصال ندارد')
    const sameService = this.store.subscriptionUrl === subscriptionUrl && this.store.serviceName === name
    this.store = { ...this.store, serviceName: name, subscriptionUrl, servers: links, selected: sameService ? Math.min(this.store.selected, links.length - 1) : 0 }
    this.save()
    this.emit()
    return this.servers()
  }

  async refresh() {
    if (this.store.subscriptionUrl) {
      const links = linksFrom(await fetchText(this.store.subscriptionUrl)).filter(l => parseLink(l))
      if (links.length) { this.store.servers = links; this.store.selected = Math.min(this.store.selected, links.length - 1); this.save() }
    }
    this.emit()
    return this.servers()
  }

  setDirectIran(on) { this.store.directIran = !!on; this.save(); this.emit() }

  async ping() {
    const list = this.servers()
    const out = await Promise.all(list.map(async s => ({ index: s.index, ms: await tcpPing(s.host, s.port) })))
    return out
  }

  async connect(index) {
    if (!this.available()) throw new Error('هستهٔ اتصال در این نسخه نیست؛ نسخهٔ تازهٔ برنامه را نصب کن')
    if (Number.isInteger(index) && index >= 0 && index < this.store.servers.length) { this.store.selected = index; this.save() }
    const server = parseLink(this.store.servers[this.store.selected])
    if (!server) throw new Error('سروری انتخاب نشده؛ اول در برنامه یک سرویس انتخاب کن')
    await this.disconnect(true)
    this.state = { ...this.state, connecting: true, error: '' }
    this.emit()
    const socksPort = await freePort(DEFAULT_SOCKS)
    const httpPort = await freePort(socksPort === DEFAULT_SOCKS ? DEFAULT_HTTP : socksPort + 1)
    const cfgPath = path.join(this.dataDir, 'xray-run.json')
    fs.writeFileSync(cfgPath, JSON.stringify(xrayConfig(server, { socksPort, httpPort, directIran: this.store.directIran })))
    let stderr = ''
    const proc = spawn(this.binary(), ['run', '-c', cfgPath], { env: { ...process.env, XRAY_LOCATION_ASSET: this.binDir }, windowsHide: true })
    this.proc = proc
    proc.stderr.on('data', d => { stderr = (stderr + d).slice(-2000) })
    proc.stdout.on('data', d => { stderr = (stderr + d).slice(-2000) })
    proc.on('exit', () => {
      if (this.proc !== proc) return
      this.proc = null
      const wasUp = this.state.connected
      this.state = { ...this.state, connected: false, connecting: false, error: wasUp ? 'اتصال قطع شد' : this.state.error }
      this.emit()
    })
    const up = await waitForPort(socksPort, 6000)
    if (!up || !this.proc) {
      const reason = (stderr.match(/(failed[^\n]*|invalid[^\n]*)/i) || [])[0] || 'هسته اجرا نشد'
      await this.disconnect(true)
      this.state = { ...this.state, connecting: false, error: reason.slice(0, 200) }
      this.emit()
      throw new Error(this.state.error)
    }
    this.state = { connected: true, connecting: false, error: '', socksPort, httpPort, server }
    this.emit()
    return this.status()
  }

  async disconnect(quiet) {
    const p = this.proc
    this.proc = null
    if (p) {
      p.kill()
      await new Promise(r => { const t = setTimeout(r, 1500); p.once('exit', () => { clearTimeout(t); r() }) })
    }
    this.state = { ...this.state, connected: false, connecting: false, socksPort: 0, httpPort: 0, server: null }
    if (!quiet) this.emit()
    return this.status()
  }

  /**
   * The extension's control API, on 127.0.0.1 only. Web pages cannot use
   * it: every call must carry the X-Ghajar-Client header (which forces a
   * CORS preflight a page cannot pass) and come from an extension origin or
   * none at all.
   */
  serve() {
    const allowed = origin => !origin || /^(chrome|moz)-extension:\/\//.test(origin)
    const server = http.createServer(async (req, res) => {
      const origin = req.headers.origin || ''
      const headers = { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' }
      if (origin && allowed(origin)) {
        headers['Access-Control-Allow-Origin'] = origin
        headers['Access-Control-Allow-Headers'] = 'Content-Type, X-Ghajar-Client'
        headers['Access-Control-Allow-Methods'] = 'GET, POST'
        headers.Vary = 'Origin'
      }
      const reply = (code, body) => { res.writeHead(code, headers); res.end(JSON.stringify(body)) }
      if (!allowed(origin)) return reply(403, { ok: false, error: 'forbidden' })
      if (req.method === 'OPTIONS') return reply(204, {})
      if (req.headers['x-ghajar-client'] !== 'extension') return reply(403, { ok: false, error: 'forbidden' })
      let body = {}
      if (req.method === 'POST') {
        let raw = ''
        for await (const c of req) { raw += c; if (raw.length > 65536) break }
        try { body = JSON.parse(raw || '{}') } catch { body = {} }
      }
      const url = new URL(req.url, 'http://127.0.0.1')
      try {
        switch (`${req.method} ${url.pathname}`) {
          case 'GET /status': return reply(200, { ok: true, app: 'ghajar', version: 1, ...this.status() })
          case 'GET /servers': return reply(200, { ok: true, servers: this.servers(), selected: this.store.selected, service: this.store.serviceName })
          case 'POST /connect': return reply(200, { ok: true, ...(await this.connect(Number.isInteger(body.index) ? body.index : undefined)) })
          case 'POST /disconnect': return reply(200, { ok: true, ...(await this.disconnect()) })
          case 'POST /refresh': return reply(200, { ok: true, servers: await this.refresh(), selected: this.store.selected })
          case 'POST /ping': return reply(200, { ok: true, results: await this.ping() })
          case 'POST /direct-iran': this.setDirectIran(!!body.on); return reply(200, { ok: true, ...this.status() })
          default: return reply(404, { ok: false, error: 'not found' })
        }
      } catch (e) {
        return reply(200, { ok: false, error: String(e && e.message || e), ...this.status() })
      }
    })
    server.on('error', () => { /* another copy already serves the port */ })
    server.listen(API_PORT, '127.0.0.1')
    this.server = server
    return server
  }
}

module.exports = { Core, parseLink, linksFrom, xrayConfig, API_PORT }
