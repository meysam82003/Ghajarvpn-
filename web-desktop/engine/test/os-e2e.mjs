// The engine on a real operating system, every connection mode, with the
// cores a release ships: run in CI on Windows/macOS/Linux runners.
//   CORE_DIR=core/win-x64 node engine/test/os-e2e.mjs
// A local VLESS server sends every connection it carries to a local web page
// that answers "via-ghajar", so a page fetched by IP shows whether the
// traffic really went through the VPN (proxy, system proxy, TUN, chosen apps).
// TUN needs administrator rights: GitHub's Windows runners have them without
// a prompt; elsewhere it runs only with E2E_TUN=1.
import { createRequire } from 'node:module'
import { spawn, execFileSync } from 'node:child_process'
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import http from 'node:http'
import net from 'node:net'

const require = createRequire(import.meta.url)
const { Engine } = require('../manager.js')

const CORE_DIR = resolve(process.env.CORE_DIR || '')
const EXE = process.platform === 'win32' ? '.exe' : ''
const TUN = process.env.E2E_TUN === '1' || (process.platform === 'win32' && process.env.CI === 'true')
const PUBLIC_IP = '93.184.215.14' // any public address: it never leaves the VPN here

let failures = 0
const ok = m => console.log('ok  ', m)
const fail = m => { failures++; console.log('FAIL', m) }
const children = []
process.on('exit', () => { for (const c of children) { try { c.kill() } catch { /* gone */ } } })

const free = () => new Promise(r => { const s = net.createServer(); s.listen(0, '127.0.0.1', () => { const p = s.address().port; s.close(() => r(p)) }) })
const waitPort = (port, ms = 10000) => new Promise(res => {
  const until = Date.now() + ms
  const t = () => { const s = net.connect(port, '127.0.0.1'); s.once('connect', () => { s.destroy(); res(true) }); s.once('error', () => { s.destroy(); Date.now() < until ? setTimeout(t, 150) : res(false) }) }
  t()
})
/** GET http://<ip>/ directly, or through an HTTP proxy port. */
const get = (proxyPort) => new Promise(res => {
  const opts = proxyPort
    ? { host: '127.0.0.1', port: proxyPort, path: `http://${PUBLIC_IP}/`, headers: { Host: PUBLIC_IP } }
    : { host: PUBLIC_IP, port: 80, path: '/', headers: { Host: PUBLIC_IP } }
  const r = http.get({ ...opts, timeout: 8000 }, x => { let d = ''; x.on('data', c => { d += c }); x.on('end', () => res(d)) })
  r.on('timeout', () => r.destroy(new Error('timeout')))
  r.on('error', e => res('ERR ' + e.message))
})

const web = http.createServer((q, r) => { if (q.url === '/generate_204') { r.writeHead(204); return r.end() } r.end('via-ghajar') })
const webPort = await free()
await new Promise(r => web.listen(webPort, '127.0.0.1', r))
const work = mkdtempSync(join(tmpdir(), 'ghajar-os-e2e-'))
const id = '0f8b2c55-6d1e-4c3a-9b7a-112233445566'
const vport = await free()
writeFileSync(join(work, 'server.json'), JSON.stringify({
  log: { loglevel: 'warning' },
  inbounds: [{ listen: '127.0.0.1', port: vport, protocol: 'vless', settings: { clients: [{ id }], decryption: 'none' } }],
  outbounds: [{ protocol: 'freedom', settings: { redirect: `127.0.0.1:${webPort}` } }]
}))
const srv = spawn(join(CORE_DIR, 'xray' + EXE), ['run', '-c', join(work, 'server.json')], { stdio: 'ignore', env: { ...process.env, XRAY_LOCATION_ASSET: CORE_DIR } })
children.push(srv)
if (!(await waitPort(vport))) { console.log('FAIL test server did not start'); process.exit(1) }

const engine = new Engine({ dataDir: join(work, 'data'), binDir: CORE_DIR, probeUrl: 'http://www.gstatic.com/generate_204', onChange: () => {} })
console.log('platform', process.platform, 'capabilities', JSON.stringify(engine.capabilities()))
const added = await engine.store.addFromText(`vless://${id}@127.0.0.1:${vport}?type=tcp&security=none#e2e`)
const cfg = engine.store.data.configs[0]
if (added.added === 1) ok('server added'); else fail('add ' + JSON.stringify(added))

async function mode(name, check) {
  engine.setSettings({ mode: name })
  try {
    const st = await engine.connect(cfg.id)
    if (st.mode !== name) fail(`${name}: fell back to ${st.mode} (${st.note})`)
    else ok(`${name}: connected (${st.engine})`)
    await check(st)
  } catch (e) { fail(`${name}: ${e.message}`) }
  await engine.disconnect()
  if (engine.children.length === 0 && !engine.tun) ok(`${name}: disconnected cleanly`); else fail(`${name}: processes left`)
}

try {
  const d = await engine.testDelays([cfg.id])
  if (d[cfg.id] > 0) ok(`real delay ${d[cfg.id]} ms`); else fail('delay ' + d[cfg.id])

  await mode('proxy', async st => {
    const body = await get(st.http.port)
    if (body === 'via-ghajar') ok('proxy: traffic through the VPN'); else fail('proxy: ' + body.slice(0, 80))
  })

  if (process.platform === 'win32') {
    await mode('system', async () => {
      const q = execFileSync('reg', ['query', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings', '/v', 'ProxyEnable']).toString()
      if (/0x1/.test(q)) ok('system: Windows proxy switched on'); else fail('system: ProxyEnable ' + q.trim())
    })
    const q = execFileSync('reg', ['query', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings', '/v', 'ProxyEnable']).toString()
    if (/0x0/.test(q)) ok('system: Windows proxy switched back off'); else fail('system: left on ' + q.trim())
  }

  if (TUN) {
    await mode('tun', async () => {
      await new Promise(r => setTimeout(r, 1500))
      const body = await get()
      if (body === 'via-ghajar') ok('tun: a plain connection went through the VPN'); else fail('tun: ' + body.slice(0, 120))
    })
    engine.setSettings({ apps: [process.platform === 'win32' ? 'node.exe' : 'node'] })
    await mode('apps', async () => {
      await new Promise(r => setTimeout(r, 1500))
      const body = await get()
      if (body === 'via-ghajar') ok('apps: the chosen program went through the VPN'); else fail('apps: ' + body.slice(0, 120))
    })
  } else console.log('skip tun/apps (no administrator rights here; E2E_TUN=1 to run)')
} catch (e) {
  fail('exception ' + (e.stack || e))
} finally {
  await engine.disconnect(true).catch(() => undefined)
  srv.kill(); web.close()
  try { rmSync(work, { recursive: true, force: true }) } catch { /* a core still closing */ }
}
console.log(failures ? `\n${failures} failure(s)` : '\nos-e2e: all checks passed')
process.exit(failures ? 1 : 0)
