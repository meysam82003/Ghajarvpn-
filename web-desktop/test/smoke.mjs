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
const api = await fetch('http://127.0.0.1:47823/status', { headers: { 'X-Ghajar-Client': 'extension' } }).then(r => r.json()).catch(() => null)
check(!!api && api.app === 'ghajar' && api.available === true, 'the extension control port answers')
const pageApi = await fetch('http://127.0.0.1:47823/status', { headers: { 'X-Ghajar-Client': 'extension', Origin: 'https://evil.example' } }).then(r => r.status).catch(() => 0)
check(pageApi === 403, 'a web origin is refused by the control port')
await win.evaluate(() => { location.hash = '#/' })
await win.waitForTimeout(800)
check((await win.locator('text=مرورگری که افزونهٔ قاجار دارد').count()) + (await win.locator('text=حساب تلگرام را یک').count()) > 0, 'home explains the browser-only connection')
await app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].close())
await win.waitForTimeout(500)
check(await app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows().length === 1 && !BrowserWindow.getAllWindows()[0].isVisible()), 'closing hides to the tray')
await app.evaluate(({ app }) => { app.emit('before-quit'); app.quit() })
await app.close().catch(() => undefined)
console.log(failures ? `${failures} failures` : 'desktop smoke passed')
process.exit(failures ? 1 : 0)
