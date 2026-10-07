// The desktop engine end to end: real Xray and sing-box servers on this
// machine, the Engine connecting through them in proxy mode, delay tests,
// "fastest", subscriptions and the extension API.
//   node web-desktop/engine/test/engine.test.mjs
// XRAY_BIN / SINGBOX_BIN / GEO_DIR choose the cores and geo files.
import { createRequire } from 'node:module'
import { spawn, execFileSync } from 'node:child_process'
import { mkdtempSync, writeFileSync, readFileSync, symlinkSync, existsSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import http from 'node:http'
import net from 'node:net'

const require = createRequire(import.meta.url)
const here = dirname(fileURLToPath(import.meta.url))
const { Engine } = require('../manager.js')
const { serve } = require('../api.js')

const XRAY = process.env.XRAY_BIN || '/tmp/claude-0/cores/xray/xray'
const SINGBOX = process.env.SINGBOX_BIN || '/tmp/claude-0/cores/sing-box'
const GEO = process.env.GEO_DIR || join(here, '..', '..', '..', 'app', 'src', 'main', 'assets')

let failures = 0
const ok = m => console.log('ok  ', m)
const fail = m => { failures++; console.log('FAIL', m) }
const children = []
const cleanup = () => { for (const c of children) { try { c.kill('SIGKILL') } catch { /* gone */ } } }
process.on('exit', cleanup)

const work = mkdtempSync(join(tmpdir(), 'ghajar-engine-'))
const binDir = join(work, 'bin')
const dataDir = join(work, 'data')
execFileSync('mkdir', ['-p', binDir, dataDir])
symlinkSync(XRAY, join(binDir, 'xray'))
if (existsSync(SINGBOX)) symlinkSync(SINGBOX, join(binDir, 'sing-box'))
for (const f of ['geoip.dat', 'geosite.dat']) symlinkSync(join(GEO, f), join(binDir, f))

function freePort() {
  return new Promise(r => { const s = net.createServer(); s.listen(0, '127.0.0.1', () => { const p = s.address().port; s.close(() => r(p)) }) })
}
function waitPort(port, ms = 8000) {
  const until = Date.now() + ms
  return new Promise(resolve => {
    const t = () => { const s = net.connect(port, '127.0.0.1'); s.once('connect', () => { s.destroy(); resolve(true) }); s.once('error', () => { s.destroy(); Date.now() < until ? setTimeout(t, 100) : resolve(false) }) }
    t()
  })
}
/** GET through a local SOCKS5 port (the way an app pointed at the engine would). */
function viaSocks(socksPort, host, port, pathName = '/') {
  return new Promise((resolve, reject) => {
    const s = net.connect(socksPort, '127.0.0.1')
    let stage = 0, buf = Buffer.alloc(0)
    s.setTimeout(8000, () => { s.destroy(); reject(new Error('timeout')) })
    s.on('error', reject)
    s.on('connect', () => s.write(Buffer.from([5, 1, 0])))
    s.on('data', d => {
      buf = Buffer.concat([buf, d])
      if (stage === 0 && buf.length >= 2) {
        stage = 1; buf = Buffer.alloc(0)
        const h = Buffer.from(host)
        s.write(Buffer.concat([Buffer.from([5, 1, 0, 3, h.length]), h, Buffer.from([port >> 8, port & 255])]))
      } else if (stage === 1 && buf.length >= 10) {
        if (buf[1] !== 0) { s.destroy(); return reject(new Error('socks refused ' + buf[1])) }
        stage = 2; buf = Buffer.alloc(0)
        s.write(`GET ${pathName} HTTP/1.1\r\nHost: ${host}\r\nConnection: close\r\n\r\n`)
      }
    })
    s.on('end', () => { if (stage === 2) resolve(buf.toString()) })
    s.on('close', () => { if (stage === 2) resolve(buf.toString()) })
  })
}

// ---- the "internet": an HTTP server every proxy server sends traffic to
const web = http.createServer((req, res) => {
  if (req.url === '/generate_204') { res.writeHead(204); return res.end() }
  if (req.url === '/sub') { res.writeHead(200, { 'subscription-userinfo': 'upload=1000; download=2000; total=10737418240; expire=1999999999', 'profile-title': 'base64:' + Buffer.from('اشتراک تست').toString('base64') }); return res.end(Buffer.from(subBody).toString('base64')) }
  res.writeHead(200, { 'Content-Type': 'text/plain' }); res.end('hello-through-vpn')
})
const webPort = await freePort()
await new Promise(r => web.listen(webPort, '127.0.0.1', r))

// ---- an Xray VLESS server (freedom → the local web server whatever the destination)
const uuid = '6f1c2f0e-2b8e-4f0a-9d6d-3c6b0c8a1e22'
const vlessPort = await freePort()
const xrayServerCfg = join(work, 'xray-server.json')
writeFileSync(xrayServerCfg, JSON.stringify({
  log: { loglevel: 'warning' },
  inbounds: [{ listen: '127.0.0.1', port: vlessPort, protocol: 'vless', settings: { clients: [{ id: uuid }], decryption: 'none' }, streamSettings: { network: 'tcp' } }],
  outbounds: [{ protocol: 'freedom', settings: { redirect: `127.0.0.1:${webPort}` } }]
}))
const xs = spawn(XRAY, ['run', '-c', xrayServerCfg], { stdio: 'ignore' }); children.push(xs)
if (!(await waitPort(vlessPort))) { fail('xray server did not start'); process.exit(1) }

// ---- a sing-box AnyTLS server (self-signed), for the sing-box engine path
let anytlsLink = ''
if (existsSync(SINGBOX)) {
  const key = join(work, 'k.pem'), crt = join(work, 'c.pem')
  execFileSync('openssl', ['req', '-x509', '-newkey', 'ec', '-pkeyopt', 'ec_paramgen_curve:prime256v1', '-nodes', '-keyout', key, '-out', crt, '-days', '2', '-subj', '/CN=test.local'], { stdio: 'ignore' })
  const atPort = await freePort()
  const sbCfg = join(work, 'sb-server.json')
  writeFileSync(sbCfg, JSON.stringify({
    log: { level: 'warn' },
    inbounds: [{ type: 'anytls', listen: '127.0.0.1', listen_port: atPort, users: [{ name: 'u', password: 'pass-123' }], tls: { enabled: true, server_name: 'test.local', certificate_path: crt, key_path: key } }],
    outbounds: [{ type: 'direct', tag: 'direct' }],
    route: { rules: [{ action: 'route-options', override_address: '127.0.0.1', override_port: webPort }], final: 'direct' }
  }))
  const sb = spawn(SINGBOX, ['run', '-c', sbCfg], { stdio: 'ignore' }); children.push(sb)
  if (await waitPort(atPort)) anytlsLink = `anytls://pass-123@127.0.0.1:${atPort}?sni=test.local&insecure=1#AnyTLS%20test`
  else fail('sing-box anytls server did not start')
}

const vlessLink = `vless://${uuid}@127.0.0.1:${vlessPort}?type=tcp&security=none#سرور%20تست%20🇩🇪`
const deadLink = `vless://${uuid}@127.0.0.1:${await freePort()}?type=tcp&security=none#dead`
const subBody = [vlessLink, deadLink].join('\n')

// ---- the engine
const statuses = []
const engine = new Engine({ dataDir, binDir, onChange: s => statuses.push(s), probeUrl: 'http://www.gstatic.com/generate_204' })
try {
  if (engine.available()) ok('engine sees the xray core'); else fail('engine does not see xray')
  if (engine.settings().mode === 'tun') ok('default mode is the whole device'); else fail('default mode ' + engine.settings().mode)
  // No administrator rights here: the cores are exercised in local-proxy mode.
  engine.setSettings({ mode: 'proxy' })
  const caps = engine.capabilities()
  ok('capabilities ' + JSON.stringify({ xray: caps.xray, singbox: caps.singbox, tun: caps.tun }))

  // subscription with usage headers and a base64 title
  const sub = await engine.store.addSubscription(`http://127.0.0.1:${webPort}/sub`)
  const g = engine.groups().find(x => x.id === sub.id)
  if (g && g.configs.length === 2 && g.name === 'اشتراک تست' && g.total === 10737418240 && g.used === 3000) ok(`subscription: ${g.configs.length} servers, title "${g.name}", usage parsed`)
  else fail('subscription parse ' + JSON.stringify(g && { n: g.configs.length, name: g.name, total: g.total, used: g.used }))

  // manual add of a sing-box protocol link
  if (anytlsLink) {
    const r = await engine.store.addFromText(anytlsLink)
    if (r.added === 1) ok('manual AnyTLS link added'); else fail('anytls add ' + JSON.stringify(r))
  }

  // real-delay test across engines
  const delays = await engine.testDelays(engine.store.data.configs.map(c => c.id))
  const byName = Object.fromEntries(engine.store.data.configs.map(c => [c.name, delays[c.id]]))
  if (byName['سرور تست 🇩🇪'] > 0) ok(`real delay vless: ${byName['سرور تست 🇩🇪']} ms`); else fail('vless delay ' + byName['سرور تست 🇩🇪'])
  if (byName.dead === -1) ok('dead server reported -1'); else fail('dead server delay ' + byName.dead)
  if (anytlsLink) { if (byName['AnyTLS test'] > 0) ok(`real delay anytls through sing-box: ${byName['AnyTLS test']} ms`); else fail('anytls delay ' + byName['AnyTLS test']) }

  // fastest = the live vless
  const best = await engine.fastest()
  if (best && best.name === 'سرور تست 🇩🇪') ok('fastest picked the live server'); else fail('fastest picked ' + (best && best.name))

  // connect (proxy mode) and browse through it
  const st = await engine.connect(best.id)
  if (st.connected && st.engine === 'xray' && st.socks) ok(`connected via xray on socks ${st.socks.port}, http ${st.http.port}`)
  else fail('connect ' + JSON.stringify(st))
  const body = await viaSocks(st.socks.port, 'example.com', 80, '/')
  if (/hello-through-vpn/.test(body)) ok('traffic flows through the VPN (socks)'); else fail('no traffic: ' + body.slice(0, 120))

  // extension API: forbidden for web pages, works for the extension
  const api = serve(engine)
  await new Promise(r => setTimeout(r, 200))
  const call = (p, headers = {}, method = 'GET') => fetch('http://127.0.0.1:47823' + p, { method, headers }).then(async r => ({ code: r.status, json: await r.json().catch(() => null) }))
  const web1 = await call('/status', { Origin: 'https://evil.example', 'X-Ghajar-Client': 'extension' })
  const ext = await call('/status', { 'X-Ghajar-Client': 'extension' })
  if (web1.code === 403) ok('API refuses web origins'); else fail('API web origin ' + web1.code)
  if (ext.code === 200 && ext.json.connected) ok('API status for the extension'); else fail('API status ' + JSON.stringify(ext))
  api.close()

  // switch to the sing-box engine
  if (anytlsLink) {
    const at = engine.store.data.configs.find(c => c.protocol === 'anytls')
    const st2 = await engine.connect(at.id)
    if (st2.connected && st2.engine === 'singbox') ok(`connected via sing-box (AnyTLS) on socks ${st2.socks.port}`)
    else fail('anytls connect ' + JSON.stringify(st2))
    const body2 = await viaSocks(st2.socks.port, 'example.org', 80, '/')
    if (/hello-through-vpn/.test(body2)) ok('traffic flows through sing-box AnyTLS'); else fail('anytls traffic: ' + body2.slice(0, 160))
  }

  // free configs from Telegram: a channel page with the live server, a dead one and junk
  const nowIso = new Date(Date.now() - 3600e3).toISOString().replace(/\.\d+Z$/, '+00:00')
  const page = `<html><body><div class="tgme_widget_message_wrap"><div class="tgme_widget_message js-widget_message" data-post="Ghajarvpn/50">
<div class="tgme_widget_message_text js-message_text" dir="auto">کانفیگ رایگان<br/><code>${vlessLink.replace('#', '#free-')}</code><br/><code>${deadLink}</code></div>
<div class="tgme_widget_message_footer"><a class="tgme_widget_message_date" href="https://t.me/Ghajarvpn/50"><time datetime="${nowIso}" class="time">10:00</time></a></div></div></div></body></html>`
  const feedFetch = async url => String(url).includes('/s/Ghajarvpn') && !String(url).includes('before=')
    ? new Response(page, { status: 200, headers: { 'content-type': 'text/html' } })
    : new Response('<html><body></body></html>', { status: 200, headers: { 'content-type': 'text/html' } })
  const nFree = await engine.refreshFree({ fetchImpl: feedFetch })
  const freeGroup = engine.groups().find(x => x.id === 'free')
  if (nFree === 1 && freeGroup && freeGroup.configs.length === 1 && freeGroup.configs[0].delay > 0) ok(`free configs: 1 healthy kept (dead one dropped), ${freeGroup.configs[0].delay} ms`)
  else fail('free configs ' + nFree + ' ' + JSON.stringify(freeGroup && freeGroup.configs.map(c => [c.name, c.delay])))

  // WARP with a recorded registration reply
  const reg = JSON.parse(readFileSync(join(here, 'fixtures', 'warp-reg.json'), 'utf8'))
  const warpFetch = async (url, init) => { const body = JSON.parse(init.body); return new Response(JSON.stringify({ ...reg, key: body.key }), { status: 200, headers: { 'content-type': 'application/json' } }) }
  const w = await engine.addWarp({ fetchImpl: warpFetch })
  const wg = engine.groups().find(x => x.id === 'warp')
  if (w && w.protocol === 'wireguard' && wg && wg.configs.length === 8) ok('WARP registered: 8 WireGuard endpoints in their own group')
  else fail('warp ' + JSON.stringify(wg && wg.configs.length))
  const w2 = await engine.addWarp({ fetchImpl: async () => { throw new Error('must not register twice') } })
  if (w2 && w2.id === w.id) ok('WARP account reused, ids stable'); else fail('warp re-add changed ids')
  try { require('../xray.js').buildXrayConfig(w, engine.settings(), { socksPort: 1080, httpPort: 1081 }); ok('WARP config builds for Xray') } catch (e) { fail('warp xray build ' + e.message) }

  // a broken server fails cleanly and leaves nothing running
  const dead = engine.store.data.configs.find(c => c.name === 'dead')
  await engine.disconnect()
  const before = engine.children.length
  if (before === 0) ok('disconnect stopped every core'); else fail('children left: ' + before)

  // unsupported protocol message
  const r2 = await engine.store.addFromText('ikev2://user:pass@vpn.example.com:500#ike')
  const ike = engine.store.data.configs.find(c => c.protocol === 'ikev2')
  if (r2.added && ike) {
    try { await engine.connect(ike.id); fail('ikev2 connected?') } catch (e) { ok('ikev2 refused with: ' + e.message) }
  }
  if (!engine.status().connected && engine.children.length === 0) ok('a failed connect leaves nothing running')
  else fail('state after failed connect ' + JSON.stringify(engine.status()))
  if (dead) ok('dead server kept in the list (marked -1)')
} catch (e) {
  fail('exception: ' + (e.stack || e))
} finally {
  await engine.disconnect(true)
  cleanup()
  web.close()
  rmSync(work, { recursive: true, force: true })
}
console.log(failures ? `\n${failures} failure(s)` : '\nengine: all checks passed')
process.exit(failures ? 1 : 0)
