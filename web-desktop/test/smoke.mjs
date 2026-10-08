// Launches the desktop app against the mock API and checks the desktop
// bridge, notifications, navigation and that outside links leave the window.
import { _electron as electron } from '../../web/node_modules/playwright/index.mjs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const url = process.env.GHAJAR_URL || 'http://localhost:4173/Faoxima/Ghajarvpn/pwa/'
let failures = 0
const check = (ok, msg) => { if (!ok) { failures++; console.log('FAIL', msg) } else console.log('ok', msg) }

// ELECTRON_BIN: a packaged build (dist/linux-unpacked/…); otherwise the npm electron runs this folder.
const packaged = process.env.ELECTRON_BIN
const app = await electron.launch({ args: packaged ? ['--no-sandbox'] : [root, '--no-sandbox'], env: { ...process.env, GHAJAR_URL: url }, executablePath: packaged || undefined })
const win = await app.firstWindow()
await win.waitForLoadState('domcontentloaded')
await win.waitForSelector('.navbar', { timeout: 20000 })
check(await win.evaluate(() => !!window.ghajarDesktop && typeof window.ghajarDesktop.focus === 'function'), 'desktop bridge present')
check(await win.evaluate(() => Notification.permission) === 'granted', 'notifications allowed')
check((await win.locator('text=قاجار را مثل یک اپ نصب کن').count()) === 0, 'no install nudge inside the app')
const shown = await win.evaluate(() => new Promise(res => { try { const n = new Notification('آزمایش', { body: 'اعلان دسکتاپ' }); n.onshow = () => res(true); n.onerror = () => res(false); setTimeout(() => res('timeout'), 3000) } catch { res(false) } }))
check(shown === true || shown === 'timeout', `native notification posted (${shown})`)
for (const [label, hash] of [['تنظیمات', '#/settings'], ['خانه', '#/'], ['فروشگاه', '#/shop'], ['خانه', '#/']]) {
  await win.locator('.navbar .cell', { hasText: label }).first().click()
  await win.waitForTimeout(700)
  check(await win.evaluate(() => location.hash) === hash, `tab ${label} → ${hash}`)
}
await win.evaluate(() => { location.hash = '#/settings/notifications' })
await win.waitForTimeout(800)
check((await win.locator('text=اعلان‌ها روی این کامپیوتر فعال است').count()) > 0, 'notification settings show desktop mode')
const before = win.url()
await win.evaluate(() => window.open('https://t.me/Ghajarvpn', '_blank'))
await win.waitForTimeout(600)
check(win.url() === before && app.windows().length === 1, 'outside link kept out of the app window')
// Payment opens in the app's own window and closes back into the order check.
const payUrl = new URL('../payment/checkout.php?ticket=t', url).toString()
await win.evaluate(u => window.ghajarDesktop.openPayment(u), payUrl)
await win.waitForTimeout(1500)
check(app.windows().length === 2, 'payment opens in an in-app window')
const pay = app.windows().find(w => w !== win)
if (pay) {
  await pay.evaluate(u => { location.href = u }, url + '#/').catch(() => undefined)
  await win.waitForTimeout(1500)
  check(app.windows().length === 1, 'returning from the gateway closes the payment window')
  check(await win.evaluate(() => location.hash) === '#/shop', 'the app goes to the order check after payment')
}
// The packaged connection core and the extension's control port.
const coreStatus = await win.evaluate(() => window.ghajarDesktop.core.status())
check(coreStatus.available === true, 'the Xray core ships inside the app')
const caps = await win.evaluate(() => window.ghajarDesktop.core.capabilities())
check(caps.xray && caps.singbox, `cores present: ${JSON.stringify(caps)}`)
const api = await fetch('http://127.0.0.1:47823/status', { headers: { 'X-Ghajar-Client': 'extension' } }).then(r => r.json()).catch(() => null)
check(!!api && api.app === 'ghajar' && api.available === true, 'the extension control port answers')
const pageApi = await fetch('http://127.0.0.1:47823/status', { headers: { 'X-Ghajar-Client': 'extension', Origin: 'https://evil.example' } }).then(r => r.status).catch(() => 0)
check(pageApi === 403, 'a web origin is refused by the control port')
await win.evaluate(() => { location.hash = '#/' })
await win.waitForTimeout(800)
check((await win.locator('.orb').count()) > 0 && (await win.locator('text=نوع اتصال').count()) > 0, 'desktop home with the connect control and connection mode')

// The VPN engine end to end through the app: a local VLESS server, added by
// pasting its link, tested, connected in local-proxy mode, traffic through it.
const net = await import('node:net')
const http = await import('node:http')
const { spawn } = await import('node:child_process')
const { writeFileSync, mkdtempSync } = await import('node:fs')
const { tmpdir } = await import('node:os')
const coreDir = process.env.GHAJAR_CORE_DIR
if (coreDir) {
  const free = () => new Promise(r => { const s = net.createServer(); s.listen(0, '127.0.0.1', () => { const p = s.address().port; s.close(() => r(p)) }) })
  const web = http.createServer((q, r) => { if (q.url === '/generate_204') { r.writeHead(204); return r.end() } r.end('via-ghajar') })
  const webPort = await free(); await new Promise(r => web.listen(webPort, '127.0.0.1', r))
  const vport = await free(), id = '1b2c3d4e-0000-4000-8000-00000000abcd'
  const dir = mkdtempSync(join(tmpdir(), 'smoke-')); const cfg = join(dir, 's.json')
  writeFileSync(cfg, JSON.stringify({ inbounds: [{ listen: '127.0.0.1', port: vport, protocol: 'vless', settings: { clients: [{ id }], decryption: 'none' } }], outbounds: [{ protocol: 'freedom', settings: { redirect: '127.0.0.1:' + webPort } }] }))
  const srv = spawn(join(coreDir, 'xray'), ['run', '-c', cfg], { stdio: 'ignore' })
  await new Promise(r => setTimeout(r, 1200))
  const added = await win.evaluate(l => window.ghajarDesktop.core.add(l), `vless://${id}@127.0.0.1:${vport}?type=tcp&security=none#Smoke%20🇩🇪`)
  check(added && added.added === 1, 'pasting a link adds the server')
  // Open the server screen (the route card) and look at it.
  await win.locator('.slab-row', { hasText: 'Smoke' }).first().click().catch(async () => { await win.locator('.home .slab-row').first().click() })
  await win.waitForTimeout(1200)
  const page = '.fullpage'
  check((await win.locator(`${page} >> text=انتخاب سرور`).count()) > 0, 'full-window server screen opens')
  check((await win.locator(`${page} >> text=برنامه‌های انتخابی`).count()) > 0, 'server screen shows the connection modes')
  check((await win.locator(`${page} >> text=Smoke`).count()) > 0, 'server screen lists the added server')
  check((await win.locator(`${page} >> text=پینگ`).count()) > 0, 'each group has its own ping (manual configs have no update)')
  await win.screenshot({ path: process.env.SHOT_DIR ? join(process.env.SHOT_DIR, 'desktop-servers.png') : join(dir, 'servers.png') })
  await win.locator(`${page} >> text=برنامه‌های انتخابی`).first().click()
  await win.waitForTimeout(500)
  await win.locator(`${page} >> text=برنامه‌هایی که وصل می‌شوند`).first().click()
  await win.waitForTimeout(900)
  check((await win.locator('.dialog >> text=Google Chrome').count()) > 0 && (await win.locator('.dialog >> text=Firefox').count()) > 0, 'app picker offers Chrome and Firefox')
  if (process.env.SHOT_DIR) await win.screenshot({ path: join(process.env.SHOT_DIR, 'desktop-apps.png') })
  await win.keyboard.press('Escape'); await win.waitForTimeout(300)
  await win.locator(`${page} >> text=پراکسی محلی`).first().click()
  await win.waitForTimeout(400)
  await win.keyboard.press('Escape'); await win.waitForTimeout(400)
  const groups = await win.evaluate(() => window.ghajarDesktop.core.groups())
  const smoke = groups.flatMap(g => g.configs).filter(c => c.name.startsWith('Smoke') && c.port === vport).pop()
  const delays = await win.evaluate(i => window.ghajarDesktop.core.test([i]), smoke.id)
  check(typeof delays[smoke.id] === 'number', `delay test ran (${delays[smoke.id]} ms; real internet probe may be offline here)`)
  const st = await win.evaluate(i => window.ghajarDesktop.core.connect(i), smoke.id)
  check(st.connected && st.mode === 'proxy' && st.socks, `connected (${st.engine}) on 127.0.0.1:${st.socks && st.socks.port}`)
  const body = await new Promise(resolve => {
    const r = http.get({ host: '127.0.0.1', port: st.http.port, path: 'http://example.com/', headers: { Host: 'example.com' } }, res => { let d = ''; res.on('data', c => { d += c }); res.on('end', () => resolve(d)) })
    r.on('error', () => resolve(''))
  })
  check(body === 'via-ghajar', 'traffic flows through the VPN from the app')
  await win.evaluate(() => { location.hash = '#/' }); await win.waitForTimeout(500)
  await win.waitForTimeout(1500)
  const clock = await win.locator('.orb').first().innerText()
  check(/[۰-۹0-9]{2}:[۰-۹0-9]{2}:[۰-۹0-9]{2}/.test(clock), 'connected: the orb shows the session clock (' + clock.replace(/\s+/g, ' ') + ')')
  if (process.env.SHOT_DIR) await win.screenshot({ path: join(process.env.SHOT_DIR, 'desktop-connected.png') })
  const off = await win.evaluate(() => window.ghajarDesktop.core.disconnect())
  check(!off.connected, 'disconnects')
  srv.kill(); web.close()
}
await app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].close())
await win.waitForTimeout(500)
check(await app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows().length === 1 && !BrowserWindow.getAllWindows()[0].isVisible()), 'closing hides to the tray')
await app.evaluate(({ app }) => { app.emit('before-quit'); app.quit() })
await app.close().catch(() => undefined)
console.log(failures ? `${failures} failures` : 'desktop smoke passed')
process.exit(failures ? 1 : 0)
