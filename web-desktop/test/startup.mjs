// Startup check of a packaged build: the window must paint the bundled app
// screens within a few seconds, with no network, and show the app's version.
//   ELECTRON_BIN=dist/linux-unpacked/ghajar-app xvfb-run node test/startup.mjs
import { _electron as electron } from '../../web/node_modules/playwright/index.mjs'

const t0 = Date.now()
const app = await electron.launch({ args: ['--no-sandbox'], executablePath: process.env.ELECTRON_BIN, env: { ...process.env, GHAJAR_URL: '' } })
const win = await app.firstWindow()
let failures = 0
const check = (ok, msg) => { console.log(ok ? 'ok' : 'FAIL', msg); if (!ok) failures++ }
await win.waitForSelector('.orb', { timeout: 15000 }).catch(() => undefined)
const ms = Date.now() - t0
check(await win.locator('.orb').count() > 0, `home screen painted in ${ms} ms`)
check(ms < 8000, 'starts in under 8 s')
check(await win.evaluate(() => !!window.ghajarDesktop?.core?.groups), 'the bundled screens have the desktop VPN screen')
const ver = await win.evaluate(() => window.ghajarDesktop.appVersion)
check(/^\d+\.\d+\.\d+$/.test(ver), 'app version ' + ver)
await win.evaluate(() => { location.hash = '#/settings/about' })
await win.waitForTimeout(800)
check(await win.locator(`text=نسخهٔ کامپیوتر`).count() > 0, 'about page shows the desktop version')
if (process.env.SHOT) await win.screenshot({ path: process.env.SHOT })
await app.evaluate(({ app }) => { app.emit('before-quit'); app.quit() })
await app.close().catch(() => undefined)
process.exit(failures ? 1 : 0)
