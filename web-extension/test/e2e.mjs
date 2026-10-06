// End to end: Chromium with the extension, the desktop app's core serving its
// control port, and a VLESS server that hands every connection to a local web
// page. Checks that only the browser is routed, and that a web page cannot
// drive the control port.
//   XRAY_DIR=<dir with xray, geoip.dat, geosite.dat> xvfb-run -a node test/e2e.mjs
import { chromium } from '../../web/node_modules/playwright/index.mjs'
import { createRequire } from 'node:module'
import { spawn } from 'node:child_process'
import { mkdtempSync, writeFileSync, existsSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import http from 'node:http'

const here = dirname(fileURLToPath(import.meta.url))
const require = createRequire(import.meta.url)
const { Core } = require('../../web-desktop/core.js')
const X = process.env.XRAY_DIR
const exe = process.env.CHROME || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome'
const extDir = join(here, '..', 'dist', 'chrome')
let failures = 0
const check = (ok, msg) => { console.log(ok ? 'ok' : 'FAIL', msg); if (!ok) failures++ }

const tmp = mkdtempSync(join(tmpdir(), 'ghajar-ext-'))
const uuid = 'b831381d-6324-4d53-ad4f-8cda48b30811'
const web = http.createServer((q, r) => { r.setHeader('Content-Type', 'text/html; charset=utf-8'); r.end(`<h1 id="t">hello through ghajar ${q.headers.host}</h1>`) }).listen(18181, '127.0.0.1')
writeFileSync(join(tmp, 'server.json'), JSON.stringify({ log: { loglevel: 'warning' },
  inbounds: [{ listen: '127.0.0.1', port: 23457, protocol: 'vless', settings: { clients: [{ id: uuid }], decryption: 'none' }, streamSettings: { network: 'tcp' } }],
  outbounds: [{ protocol: 'freedom', settings: { redirect: '127.0.0.1:18181' } }] }))
const srv = spawn(join(X, 'xray'), ['run', '-c', join(tmp, 'server.json')], { env: { ...process.env, XRAY_LOCATION_ASSET: X } })

const core = new Core({ dataDir: tmp, binDir: X })
core.serve()
await core.setService({ name: 'سرویس آزمایشی', configs: [`vless://${uuid}@127.0.0.1:23457?type=tcp&security=none#${encodeURIComponent('🇩🇪 Local test')}`] })
await new Promise(r => setTimeout(r, 800))

const ctx = await chromium.launchPersistentContext(join(tmp, 'profile'), {
  headless: false, executablePath: existsSync(exe) ? exe : undefined,
  args: [`--disable-extensions-except=${extDir}`, `--load-extension=${extDir}`]
})
let [sw] = ctx.serviceWorkers()
if (!sw) sw = await ctx.waitForEvent('serviceworker')
const id = sw.url().split('/')[2]
check(!!id, 'extension loaded')

const popup = await ctx.newPage()
await popup.goto(`chrome-extension://${id}/popup.html`)
await popup.waitForTimeout(800)
check(await popup.locator('#main').isVisible(), 'popup finds the desktop app')
check((await popup.locator('.servers li').count()) === 1, 'popup lists the servers of the chosen service')
await popup.click('#orb')
await popup.waitForFunction(() => document.getElementById('orbLabel').textContent === 'متصل', null, { timeout: 15000 }).catch(() => undefined)
check((await popup.locator('#orbLabel').textContent()) === 'متصل', 'connect from the popup')
const proxy = await sw.evaluate(() => new Promise(r => chrome.proxy.settings.get({}, r)))
if (process.env.SHOT) { await popup.setViewportSize({ width: 360, height: 560 }); await popup.screenshot({ path: process.env.SHOT + '/popup-on.png' }) }
check(proxy.value.mode === 'fixed_servers' && proxy.value.rules.singleProxy.scheme === 'socks5', 'browser routed to the local core (socks5)')

const page = await ctx.newPage()
await page.goto('http://site.example.org/', { timeout: 15000 }).catch(e => console.log('nav error', e.message))
const body = await page.locator('#t').textContent().catch(() => '')
check(body.includes('hello through ghajar site.example.org'), 'a web page loads through the VPN')

// A web page must not be able to drive the control port.
const blocked = await page.evaluate(async () => {
  const a = await fetch('http://127.0.0.1:47823/status').then(r => r.status).catch(() => 'blocked')
  const b = await fetch('http://127.0.0.1:47823/disconnect', { method: 'POST', headers: { 'X-Ghajar-Client': 'extension' } }).then(r => r.status).catch(() => 'blocked')
  return [a, b]
})
check(blocked[0] !== 200 && blocked[1] !== 200 && core.status().connected, `web pages cannot use the control port (${blocked.join(',')})`)

await popup.bringToFront()
await popup.click('#orb')
await popup.waitForFunction(() => document.getElementById('orbLabel').textContent === 'اتصال', null, { timeout: 10000 }).catch(() => undefined)
const cleared = await sw.evaluate(() => new Promise(r => chrome.proxy.settings.get({}, r)))
check(cleared.value.mode !== 'fixed_servers' && !core.status().connected, 'disconnect restores direct browsing')

// The desktop app stops while connected: the browser must not stay pointed at a dead proxy.
await popup.click('#orb')
await popup.waitForFunction(() => document.getElementById('orbLabel').textContent === 'متصل', null, { timeout: 15000 }).catch(() => undefined)
await core.disconnect()
core.server.close()
await popup.reload(); await popup.waitForTimeout(1200)
const after = await sw.evaluate(() => new Promise(r => chrome.proxy.settings.get({}, r)))
check(after.value.mode !== 'fixed_servers', 'a closed desktop app releases the browser')
if (process.env.SHOT) await popup.screenshot({ path: process.env.SHOT + '/popup-missing.png' })
check(await popup.locator('#missing').isVisible(), 'popup explains the desktop app is needed')

await ctx.close()
srv.kill(); web.close()
console.log(failures ? `${failures} failures` : 'extension e2e passed')
process.exit(failures ? 1 : 0)
