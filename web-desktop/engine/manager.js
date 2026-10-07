// The desktop VPN engine: picks the core a config needs (Xray, sing-box with
// its sidecars, Psiphon), runs it on 127.0.0.1, and connects one of three ways:
//
//   proxy   only what is pointed at the local ports (the browser extension)
//   system  the operating system's proxy settings point at the local ports
//   tun     a sing-box TUN (run with administrator rights) captures the
//           whole device and forwards it to the core: every app, every port
//
// It mirrors the Android CoreManager: one connection at a time, real-delay
// tests through the core itself, "fastest server" selection, free servers.
const { spawn } = require('child_process')
const fs = require('fs')
const net = require('net')
const path = require('path')

const parser = require('./parser.js')
const xray = require('./xray.js')
const singbox = require('./singbox.js')
const free = require('./free.js')
const { Store } = require('./store.js')
const { setSystemProxy } = require('./sysproxy.js')
const { runElevated } = require('./elevate.js')

const DEFAULT_SOCKS = 10808
const DEFAULT_HTTP = 10809
const EXE = process.platform === 'win32' ? '.exe' : ''

// ------------------------------------------------------------------ helpers

function freePort(preferred, span = 60) {
  return new Promise(resolve => {
    const tryPort = p => {
      const s = net.createServer()
      s.once('error', () => (p < preferred + span ? tryPort(p + 1) : resolve(0)))
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

/** Real delay through a local SOCKS port, as the app's MeasureDelayBounded: -1 on failure. */
function socksDelay(port, timeoutMs = 5000, url = xray.PROBE_URL) {
  return xray.measureDelay(port, { timeoutMs, url })
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

function tail(text, n = 4) { return String(text || '').trim().split(/\r?\n/).slice(-n).join('\n').slice(0, 400) }

/** A child process with its output kept for error messages. */
function runChild(binary, args, { env, cwd } = {}) {
  const proc = spawn(binary, args, { env: { ...process.env, ...env }, cwd, windowsHide: true })
  proc.log = ''
  const keep = d => { proc.log = (proc.log + d).slice(-4000) }
  proc.stdout.on('data', keep)
  proc.stderr.on('data', keep)
  proc.exited = new Promise(r => proc.once('exit', code => r(code)))
  proc.once('error', e => { proc.log += '\n' + e.message })
  return proc
}

async function killChild(proc) {
  if (!proc || proc.exitCode !== null) return
  proc.kill()
  await Promise.race([proc.exited, new Promise(r => setTimeout(r, 1500))])
  if (proc.exitCode === null) { try { proc.kill('SIGKILL') } catch { /* gone */ } }
}

// ------------------------------------------------------------------ engine

class Engine {
  /**
   * binDir: where the cores live (xray, sing-box, psiphon, ghajar-helper,
   * juicity, the DNS tunnel clients, geoip/geosite data).
   */
  constructor({ dataDir, binDir, onChange, probeUrl }) {
    this.probeUrl = probeUrl || xray.PROBE_URL
    this.dataDir = dataDir
    this.binDir = binDir
    this.runDir = path.join(dataDir, 'run')
    fs.mkdirSync(this.runDir, { recursive: true })
    this.store = new Store({ dataDir, parser })
    this.onChange = onChange || (() => {})
    this.children = []
    this.tun = null
    this.systemProxy = false
    this.state = { connected: false, connecting: false, error: '', mode: '', socksPort: 0, httpPort: 0, server: null, engine: '', since: 0 }
    this.store.onChange(() => this.emit())
  }

  // -------------------------------------------------------------- settings

  settings() {
    const s = {
      ...xray.DEFAULT_SETTINGS,
      mode: 'tun',                // tun | apps | system | proxy (the whole device, like the phone app)
      apps: [],                   // mode 'apps': only these programs use the VPN
      bypassApps: [],             // mode 'tun': these programs stay off the VPN
      iranDirect: true,
      autoFastest: false,
      sniffing: true,
      ...this.store.settings
    }
    // Iranian sites direct: the Xray builder calls it splitRouting (split_routing_enabled).
    s.splitRouting = s.iranDirect !== false
    return s
  }
  setSettings(patch) { this.store.setSettings(patch); return this.settings() }

  bin(name) { return path.join(this.binDir, name + EXE) }
  has(name) { return fs.existsSync(this.bin(name)) }
  available() { return this.has('xray') }

  /** What this build can run, for the UI to grey out the rest. */
  capabilities() {
    const helpers = ['ghajar-helper', 'juicity', 'dnstt', 'vaydns', 'noizdns', 'slipstream', 'masterdns', 'stormdns', 'cottendns']
    return {
      xray: this.has('xray'), singbox: this.has('sing-box'), psiphon: this.has('psiphon'), tor: this.has('tor'),
      tun: this.has('sing-box'), apps: this.has('sing-box'), helpers: Object.fromEntries(helpers.map(h => [h, this.has(h)]))
    }
  }

  // -------------------------------------------------------------- status

  status() {
    const s = this.state
    const sel = this.store.selected()
    return {
      available: this.available(), connected: s.connected, connecting: s.connecting, error: s.error,
      mode: s.mode || this.settings().mode, engine: s.engine, since: s.since,
      socks: s.connected ? { host: '127.0.0.1', port: s.socksPort } : null,
      http: s.connected ? { host: '127.0.0.1', port: s.httpPort } : null,
      server: s.server ? { id: s.server.id, name: s.server.name, protocol: s.server.protocol } : null,
      selectedId: sel ? sel.id : '', service: this.serviceName(), directIran: this.settings().iranDirect !== false,
      // The extension still addresses servers by index in the active group.
      selected: this.activeIndex()
    }
  }

  emit() { try { this.onChange(this.status()) } catch { /* window gone */ } }

  // The group the selected server belongs to: what the extension lists.
  activeGroup() {
    const sel = this.store.selected()
    const groups = this.store.groups()
    return (sel && groups.find(g => g.id === sel.subId)) || groups[0] || { name: '', configs: [] }
  }
  serviceName() { return this.activeGroup().name || '' }
  activeIndex() {
    const sel = this.store.selected()
    return Math.max(0, this.activeGroup().configs.findIndex(c => sel && c.id === sel.id))
  }

  servers() {
    return this.activeGroup().configs.map((c, index) => ({ index, id: c.id, name: c.name, protocol: c.protocol, host: c.address, port: c.port }))
  }

  groups() {
    const delays = this.store.delays()
    return this.store.groups().map(g => ({
      ...g,
      configs: g.configs.map(c => ({ id: c.id, name: c.name, protocol: c.protocol, address: c.address, port: c.port, favorite: !!c.favorite, delay: delays[c.id] ?? null, family: parser.protocolFamily(c) }))
    }))
  }

  // -------------------------------------------------------------- routing per config

  /** How a config is run: which core, and which sidecar (if any) in front of it. */
  planFor(config) {
    const family = parser.protocolFamily(config)
    if (family === 'psiphon') return { engine: 'psiphon' }
    if (family === 'xray') {
      if (xray.supportsXray(config)) return { engine: 'xray' }
      throw new Error('این ترنسپورت (h2/quic قدیمی) در هستهٔ جدید Xray حذف شده؛ کانفیگ تازه از پنل بگیر')
    }
    if ((family === 'singbox' || family === 'dnstunnel') && singbox.handles(config)) return { engine: 'singbox' }
    throw new Error(`پروتکل ${String(config.protocol).toUpperCase()} در نسخهٔ دسکتاپ هنوز فعال نیست`)
  }

  // -------------------------------------------------------------- connect

  async connect(target) {
    if (!this.available()) throw new Error('هستهٔ اتصال در این نسخه نیست؛ نسخهٔ تازهٔ برنامه را نصب کن')
    let config = null
    if (typeof target === 'string') config = this.store.config(target)
    else if (Number.isInteger(target)) config = this.activeGroup().configs[target] || null
    if (!config && this.settings().autoFastest) config = await this.fastest()
    if (!config) config = this.store.selected()
    if (!config) throw new Error('سروری انتخاب نشده؛ اول یک سرویس یا کانفیگ اضافه کن')
    this.store.select(config.id)

    await this.disconnect(true)
    const settings = this.settings()
    const mode = settings.mode
    this.state = { ...this.state, connecting: true, error: '', mode, server: config }
    this.emit()
    try {
      const socksPort = await freePort(DEFAULT_SOCKS)
      const httpPort = await freePort(socksPort === DEFAULT_SOCKS ? DEFAULT_HTTP : socksPort + 1)
      const plan = this.planFor(config)
      await this.startCore(plan.engine, config, settings, socksPort, httpPort)
      if (mode === 'tun' || mode === 'apps') await this.startTun(socksPort, settings, config, mode)
      else if (mode === 'system') { await setSystemProxy(true, { httpPort, socksPort }); this.systemProxy = true }
      this.state = { connected: true, connecting: false, error: '', mode, socksPort, httpPort, server: config, engine: plan.engine, since: Date.now() }
      this.emit()
      return this.status()
    } catch (e) {
      await this.disconnect(true)
      this.state = { ...this.state, connected: false, connecting: false, error: tail(e.message || e, 3) }
      this.emit()
      throw new Error(this.state.error)
    }
  }

  async startCore(engine, config, settings, socksPort, httpPort) {
    const cfgPath = path.join(this.runDir, `${engine}.json`)
    let proc
    if (engine === 'xray') {
      fs.writeFileSync(cfgPath, JSON.stringify(xray.buildXrayConfig(config, settings, { socksPort, httpPort, logLevel: 'warning' })))
      proc = runChild(this.bin('xray'), ['run', '-c', cfgPath], { env: { XRAY_LOCATION_ASSET: this.binDir } })
    } else if (engine === 'singbox') {
      if (!this.has('sing-box')) throw new Error('هستهٔ sing-box در این نسخه نیست')
      const outbound = await this.sidecarFor(config)
      fs.writeFileSync(cfgPath, JSON.stringify(singbox.buildProxyOnlyConfig({ outbound, socksPort, httpPort, settings: { iranDirect: settings.iranDirect, iranRuleSet: this.iranRuleSet(), remoteDns: settings.remoteDns } })))
      proc = runChild(this.bin('sing-box'), ['run', '-c', cfgPath, '-D', this.runDir])
    } else if (engine === 'psiphon') {
      if (!this.has('psiphon')) throw new Error('هستهٔ Psiphon در این نسخه نیست')
      // Psiphon opens its own local SOCKS; Xray in front keeps the routing rules
      // (Iran direct, ads) the same as every other server, like buildPsiphon.
      const upstream = await freePort(socksPort + 20)
      const pDir = path.join(this.dataDir, 'psiphon')
      fs.mkdirSync(pDir, { recursive: true })
      const pCfg = path.join(this.runDir, 'psiphon.json')
      fs.writeFileSync(pCfg, JSON.stringify(free.psiphonConfig({
        mode: config.psiphonMode || 'auto', country: config.psiphonCountry || '', cdnIps: config.psiphonCdnIps || '',
        cdnSni: config.psiphonCdnSni || '', dataDir: pDir, socksPort: upstream
      })))
      const ps = runChild(this.bin('psiphon'), free.psiphonArgs(pCfg, { dataDir: pDir }), { cwd: pDir })
      this.children.push(ps)
      this.watch(ps)
      // Like the Android controller: up only once the SOCKS port AND a first tunnel exist (90 s).
      await new Promise((resolve, reject) => {
        let port = false, tunnel = false, buf = ''
        const timer = setTimeout(() => done(new Error('Psiphon در ۹۰ ثانیه راهی پیدا نکرد')), free.PSIPHON.READY_TIMEOUT_MS || 90000)
        const done = e => { clearTimeout(timer); ps.stderr.off('data', onData); ps.stdout.off('data', onData); e ? reject(e) : resolve() }
        const onData = d => {
          buf += d
          let i
          while ((i = buf.indexOf('\n')) >= 0) {
            const ev = free.psiphonNoticeEvent(buf.slice(0, i)); buf = buf.slice(i + 1)
            if (!ev) continue
            if (ev.fatal) return done(new Error('Psiphon: ' + ev.message))
            if (ev.type === 'socksPort') port = true
            if (ev.type === 'connected') tunnel = true
            if (port && tunnel) return done()
          }
        }
        ps.stderr.on('data', onData)
        ps.stdout.on('data', onData)
        ps.exited.then(() => done(new Error('Psiphon بسته شد: ' + tail(ps.log))))
      })
      fs.writeFileSync(cfgPath, JSON.stringify(xray.buildChainToSocks('127.0.0.1', upstream, settings, { socksPort, httpPort })))
      proc = runChild(this.bin('xray'), ['run', '-c', cfgPath], { env: { XRAY_LOCATION_ASSET: this.binDir } })
    }
    this.children.push(proc)
    this.watch(proc)
    if (!(await waitForPort(socksPort, 10000)) || proc.exitCode !== null) {
      throw new Error(tail(proc.log) || 'هسته اجرا نشد')
    }
  }

  /**
   * Starts the sidecar a protocol needs (AWG, mieru, brook, SSH transports,
   * SSTP, SoftEther, juicity, DNS tunnels…) and returns what sing-box should
   * dial: the sidecar plan (pointing at its local port) or the plain spec.
   */
  async sidecarFor(config) {
    const localPort = await freePort(21000, 400)
    const rpcPort = await freePort(localPort + 1, 400)
    const dir = path.join(this.runDir, 'sidecar')
    fs.rmSync(dir, { recursive: true, force: true })
    fs.mkdirSync(dir, { recursive: true })
    const plan = singbox.sidecarPlan(config, { localPort, rpcPort, dir })
    if (!plan) return singbox.buildSingboxSpec(config)
    const name = plan.binary
    if (!this.has(name)) throw new Error(`ابزار ${name} در این نسخه نیست`)
    for (const [file, content] of Object.entries(plan.files || {})) fs.writeFileSync(path.join(dir, file), content, { mode: 0o600 })
    const side = runChild(this.bin(name), plan.args, { env: plan.env, cwd: plan.cwd || dir })
    this.children.push(side)
    this.watch(side)
    const up = await waitForPort(plan.localPort, plan.readyTimeoutMs || 15000)
    // Passwords and keys live on disk only until the helper has read them.
    for (const f of plan.secretFiles || []) fs.rm(path.isAbsolute(f) ? f : path.join(dir, f), { force: true }, () => {})
    if (!up || side.exitCode !== null) throw new Error(`${name} اجرا نشد: ` + tail(side.log))
    return plan
  }

  iranRuleSet() {
    const f = path.join(this.binDir, 'geoip-ir.srs')
    return fs.existsSync(f) ? f : ''
  }

  /**
   * The TUN: every app on the computer (mode 'tun'), or only the chosen apps
   * (mode 'apps', e.g. just Chrome or Firefox), forwarded to the running core.
   */
  async startTun(socksPort, settings, config, mode) {
    if (!this.has('sing-box')) throw new Error('تونل به هستهٔ sing-box نیاز دارد')
    const apps = (settings.apps || []).map(String).filter(Boolean)
    if (mode === 'apps' && !apps.length) throw new Error('هیچ برنامه‌ای برای اتصال انتخاب نشده')
    const cfg = singbox.buildTunConfig({
      socksPort,
      platform: process.platform,
      settings: {
        iranDirect: settings.iranDirect, iranRuleSet: this.iranRuleSet(), remoteDns: settings.remoteDns,
        serverHosts: singbox.remoteHosts(config),
        perApp: mode === 'apps' ? { mode: 'include', processes: apps } : (settings.bypassApps || []).length ? { mode: 'exclude', processes: settings.bypassApps } : undefined
      }
    })
    const tunDir = path.join(this.runDir, 'tun')
    fs.mkdirSync(tunDir, { recursive: true })
    const cfgPath = path.join(tunDir, 'tun.json')
    fs.writeFileSync(cfgPath, JSON.stringify(cfg))
    this.tun = runElevated({ binary: this.bin('sing-box'), args: ['run', '-c', cfgPath, '-D', tunDir], workDir: tunDir })
    await this.tun.ready
  }

  watch(proc) {
    proc.exited.then(() => {
      if (!this.children.includes(proc) || !this.state.connected) return
      this.disconnect(true).then(() => { this.state = { ...this.state, error: 'اتصال قطع شد' }; this.emit() })
    })
  }

  async disconnect(quiet) {
    const children = this.children
    this.children = []
    if (this.tun) { const t = this.tun; this.tun = null; await t.stop() }
    if (this.systemProxy) { this.systemProxy = false; await setSystemProxy(false).catch(() => undefined) }
    await Promise.all(children.map(killChild))
    this.state = { ...this.state, connected: false, connecting: false, socksPort: 0, httpPort: 0, engine: '', since: 0 }
    if (!quiet) this.emit()
    return this.status()
  }

  // -------------------------------------------------------------- tests

  /**
   * Real delay of many configs at once: Xray ones through one test instance
   * (buildDelayTestConfig), the rest by TCP reachability. Results are stored.
   */
  async testDelays(ids) {
    const configs = (ids && ids.length ? ids.map(id => this.store.config(id)).filter(Boolean) : this.activeGroup().configs)
    const results = {}
    const engineOf = c => { try { return this.planFor(c).engine } catch { return '' } }
    const xs = configs.filter(c => engineOf(c) === 'xray')
    // sing-box protocols that need no helper are tested through sing-box itself.
    const sbs = this.has('sing-box') ? configs.filter(c => engineOf(c) === 'singbox' && !singbox.sidecarSpec(c)) : []
    const rest = configs.filter(c => !xs.includes(c) && !sbs.includes(c))
    if (xs.length && this.available()) {
      for (let i = 0; i < xs.length; i += 64) Object.assign(results, await this.xrayBatch(xs.slice(i, i + 64)))
    }
    for (let i = 0; i < sbs.length; i += 64) Object.assign(results, await this.singboxBatch(sbs.slice(i, i + 64)))
    await Promise.all(rest.map(async c => { results[c.id] = c.address && c.port ? await tcpPing(c.address, c.port) : -1 }))
    this.store.setDelays(results)
    return results
  }

  async xrayBatch(configs) {
    const base = await freePort(30000, 2000)
    const { config, ports } = xray.buildDelayTestConfig(configs, base)
    return this.runBatch(config, ports, configs, () => [this.bin('xray'), ['run', '-c']], { XRAY_LOCATION_ASSET: this.binDir })
  }

  /** Runs one test core with a port per config and measures each port in parallel. */
  async runBatch(config, ports, configs, command, env) {
    const cfgPath = path.join(this.runDir, `delay-${process.hrtime.bigint()}.json`)
    fs.writeFileSync(cfgPath, JSON.stringify(config))
    const [bin, args] = command()
    const proc = runChild(bin, [...args, cfgPath], { env, cwd: this.runDir })
    const out = Object.fromEntries(configs.map(c => [c.id, -1]))
    try {
      if (!ports.length || !(await waitForPort(ports[0].port, 8000))) return out
      const queue = [...ports]
      const worker = async () => {
        for (let p = queue.shift(); p; p = queue.shift()) {
          // Two tries like the app: the first request also pays the handshake.
          let ms = await socksDelay(p.port, 5000, this.probeUrl)
          if (ms < 0) ms = await socksDelay(p.port, 5000, this.probeUrl)
          out[configs[p.index].id] = ms
        }
      }
      await Promise.all(Array.from({ length: Math.min(16, ports.length) }, worker))
    } finally {
      await killChild(proc)
      fs.rm(cfgPath, { force: true }, () => {})
    }
    return out
  }

  /** Same as xrayBatch, for sing-box protocols: one sing-box, one mixed inbound per config. */
  async singboxBatch(configs) {
    const base = await freePort(33000, 2000)
    const inbounds = [], outbounds = [], endpoints = [], rules = [], ports = []
    configs.forEach((c, i) => {
      let spec
      try { spec = singbox.buildSingboxSpec(c) } catch { return }
      if (!spec) return
      // Give every config's objects their own tags (proxy → out-i, a detour x → x-i).
      const rename = t => (t === 'proxy' ? `out-${i}` : `${t}-${i}`)
      const retag = o => { const n = { ...o, tag: rename(o.tag || 'proxy') }; if (n.detour) n.detour = rename(n.detour); return n }
      if (spec.endpoint) endpoints.push(retag(spec.endpoint)); else outbounds.push(retag(spec.outbound))
      for (const x of spec.extraOutbounds || []) outbounds.push(retag(x))
      inbounds.push({ type: 'mixed', tag: `in-${i}`, listen: '127.0.0.1', listen_port: base + i })
      rules.push({ inbound: `in-${i}`, outbound: `out-${i}` })
      ports.push({ index: i, port: base + i })
    })
    if (!ports.length) return Object.fromEntries(configs.map(c => [c.id, -1]))
    outbounds.push({ type: 'direct', tag: 'direct' })
    const config = { log: { level: 'error' }, dns: { servers: [{ type: 'local', tag: 'local' }] }, inbounds, outbounds, route: { rules, final: 'direct', default_domain_resolver: { server: 'local' } } }
    if (endpoints.length) config.endpoints = endpoints
    return this.runBatch(config, ports, configs, () => [this.bin('sing-box'), ['run', '-c']])
  }

  async fastest() {
    const res = await this.testDelays()
    const best = Object.entries(res).filter(([, v]) => v > 0).sort((a, b) => a[1] - b[1])[0]
    return best ? this.store.config(best[0]) : null
  }

  async ping() {
    const res = await this.testDelays()
    return this.servers().map(s => ({ index: s.index, ms: res[s.id] ?? -1 }))
  }

  // -------------------------------------------------------------- free servers

  /**
   * The app's free configs: Telegram channels scraped, every link tested
   * through the cores, the healthy ones (≤ 2.5 s) kept, best first, at most 35.
   * fetchImpl should go through the VPN when connected (Telegram is filtered).
   */
  async refreshFree({ fetchImpl = fetch } = {}) {
    const crypto = require('crypto')
    const res = await free.fetchFreeLinks({ fetch: fetchImpl, parseBundle: t => parser.parseBundle(t) })
    if (res.status === 'unreachable') throw new Error('کانال‌های رایگان در دسترس نیستند؛ اول به یک سرور وصل شو و دوباره بزن')
    const seen = new Set()
    const configs = []
    const add = c => {
      if (!c) return
      const key = free.configSignature ? free.configSignature(c) : JSON.stringify([c.protocol, c.address, c.port, c.uuid, c.password])
      if (seen.has(key)) return
      seen.add(key)
      configs.push({ ...c, id: crypto.randomUUID(), source: 'COMMUNITY' })
    }
    for (const l of res.links || []) add(parser.parseLink(l.link))
    for (const b of res.bundles || []) for (const c of parser.parseBundle(typeof b === 'string' ? b : (b.body || b.text || ''))) add(c)
    const delays = await this.testConfigs(configs)
    const F = free.FREE || {}
    const good = configs.filter(c => delays[c.id] > 0 && delays[c.id] <= (F.MAX_LATENCY_MS || 2500))
      .sort((a, b) => delays[a.id] - delays[b.id]).slice(0, F.MAX_MANAGED_CONFIGS || 35)
    good.forEach((c, i) => { if (!c.name) c.name = `Ghajarvpn ${i + 1}` })
    this.store.setFree(good)
    this.store.setDelays(Object.fromEntries(good.map(c => [c.id, delays[c.id]])))
    return good.length
  }

  /** Delay of configs that are not (yet) in the store, through the matching core. */
  async testConfigs(configs) {
    const out = {}
    const engineOf = c => { try { return this.planFor(c).engine } catch { return '' } }
    const xs = configs.filter(c => engineOf(c) === 'xray')
    const sbs = this.has('sing-box') ? configs.filter(c => engineOf(c) === 'singbox' && !singbox.sidecarSpec(c)) : []
    for (let i = 0; i < xs.length; i += 64) Object.assign(out, await this.xrayBatch(xs.slice(i, i + 64)))
    for (let i = 0; i < sbs.length; i += 64) Object.assign(out, await this.singboxBatch(sbs.slice(i, i + 64)))
    return out
  }

  /** Free Cloudflare WARP: the account is registered once and kept, its 8 endpoints become a group. */
  async addWarp({ fetchImpl = fetch } = {}) {
    let account = this.store.data.warp
    if (!account) {
      const r = await free.registerWarp({ fetch: fetchImpl })
      if (!r.ok) throw new Error('ثبت WARP نشد: ' + r.message)
      account = r.account
      this.store.data.warp = account
    }
    const old = this.store.data.configs.filter(c => c.subId === 'warp').map(c => c.id)
    const configs = free.warpConfigsFromAccount(account, { ids: old.length ? old : undefined })
    let sub = this.store.data.subscriptions.find(s => s.id === 'warp')
    if (!sub) { sub = { id: 'warp', name: 'WARP رایگان کلادفلر', url: '', kind: 'free' }; this.store.data.subscriptions.push(sub) }
    this.store.data.configs = [...this.store.data.configs.filter(c => c.subId !== 'warp'), ...configs.map(c => ({ ...c, subId: 'warp', source: 'WARP' }))]
    this.store.save()
    return this.store.data.configs.find(c => c.subId === 'warp')
  }

  /** Programs running now, for the per-app picker: [{ name }] sorted, without system noise. */
  async runningApps() {
    const { execFile } = require('child_process')
    const run = (cmd, args) => new Promise(r => execFile(cmd, args, { windowsHide: true, timeout: 10000, maxBuffer: 8 << 20 }, (e, out) => r(e ? '' : String(out))))
    let names = []
    if (process.platform === 'win32') {
      names = (await run('tasklist', ['/fo', 'csv', '/nh'])).split(/\r?\n/).map(l => (l.match(/^"([^"]+)"/) || [])[1]).filter(Boolean)
    } else if (process.platform === 'darwin') {
      // App bundles: "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" → "Google Chrome"
      names = (await run('/bin/ps', ['-axo', 'comm='])).split('\n').map(l => (l.match(/\/([^/]+)\.app\//) || [])[1]).filter(Boolean)
    } else {
      // Kernel threads show as [name]; keep real programs, by executable name.
      names = (await run('ps', ['-eo', 'args='])).split('\n').map(l => l.trim()).filter(l => l && !l.startsWith('['))
        .map(l => path.basename(l.split(/\s+/)[0])).filter(n => n && !/^(systemd.*|dbus.*|sh|bash|zsh|ps|login|agetty|sshd|cron|init|node)$/.test(n))
    }
    const own = new Set([...singbox.CORE_PROCESSES].map(x => String(x).toLowerCase()))
    return [...new Set(names)].filter(n => !own.has(n.toLowerCase().replace(/\.exe$/, ''))).sort((a, b) => a.localeCompare(b)).map(name => ({ name }))
  }

  /** Psiphon as a server entry, like the app's built-in one. */
  addPsiphon(country = '') {
    const existing = this.store.data.configs.find(c => c.protocol === 'psiphon' && (c.psiphonCountry || '') === country)
    if (existing) return existing
    const c = { ...parser.defaults(), protocol: 'psiphon', name: country ? `Psiphon ${country}` : 'Psiphon رایگان', psiphonMode: 'auto', psiphonCountry: country, id: require('crypto').randomUUID(), subId: 'manual', source: 'PSIPHON' }
    this.store.data.configs.push(c)
    this.store.save()
    return c
  }
}

module.exports = { Engine, socksDelay, freePort, waitForPort }
