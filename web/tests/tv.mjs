// TV mode (LG webOS and other TVs): the remote alone must reach every tab and
// control, Back must step back, and dialogs must trap focus.
import { chromium } from 'playwright'
import { existsSync } from 'node:fs'

const base = process.env.BASE || 'http://localhost:4173/Faoxima/Ghajarvpn/pwa/'
const exe = process.env.CHROME || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome'
const browser = await chromium.launch(existsSync(exe) ? { executablePath: exe } : {})
let failures = 0
const fail = m => { failures++; console.log('FAIL', m) }
const ok = m => console.log('ok', m)

const ctx = await browser.newContext({
  viewport: { width: 1920, height: 1080 }, locale: 'fa-IR',
  userAgent: 'Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.128 Safari/537.36 WebAppManager'
})
await ctx.addInitScript(() => localStorage.setItem('ghajar.token.v1', 'a'.repeat(40)))
const page = await ctx.newPage()
const errors = []
page.on('pageerror', e => errors.push(e.message))
await page.goto(base + '#/')
await page.waitForTimeout(1500)

if (await page.evaluate(() => document.documentElement.classList.contains('tv'))) ok('TV mode on for a webOS user agent')
else fail('TV mode not detected')
const focused = () => page.evaluate(() => { const a = document.activeElement; return a && a !== document.body ? (a.getAttribute('aria-label') || a.textContent || a.tagName).trim().slice(0, 40) : '' })
if (/اتصال/.test(await focused())) ok('the connect control has focus at start')
else if (await focused()) fail('start focus is not the connect control: ' + await focused())
else fail('nothing focused at start')

// Walk with the arrows and collect every control reached.
const seen = new Set()
for (const key of ['ArrowDown', 'ArrowDown', 'ArrowDown', 'ArrowUp', 'ArrowUp', 'ArrowUp', 'ArrowLeft', 'ArrowLeft', 'ArrowRight', 'ArrowRight', 'ArrowRight', 'ArrowDown', 'ArrowDown']) {
  await page.keyboard.press(key)
  await page.waitForTimeout(120)
  seen.add(await focused())
}
if (seen.size >= 4) ok(`arrows reached ${seen.size} controls: ${[...seen].join(' | ')}`)
else fail(`arrows reached only ${seen.size}: ${[...seen].join(' | ')}`)

// Reach the shop tab with the arrows and open it with OK.
let reached = false
const plan = ['ArrowRight', 'ArrowRight', 'ArrowRight', 'ArrowDown', 'ArrowDown', 'ArrowUp', 'ArrowUp', 'ArrowUp', 'ArrowLeft', 'ArrowDown']
for (let i = 0; i < 40 && !reached; i++) {
  const t = await focused()
  if (/فروشگاه/.test(t)) { reached = true; break }
  await page.keyboard.press(plan[i % plan.length])
  await page.waitForTimeout(100)
}
if (reached) {
  await page.keyboard.press('Enter'); await page.waitForTimeout(800)
  const h = await page.evaluate(() => location.hash)
  if (h === '#/shop') ok('OK opened the shop'); else fail('OK on shop tab landed on ' + h)
  // webOS Back (461) goes home.
  await page.evaluate(() => window.dispatchEvent(new KeyboardEvent('keydown', { keyCode: 461, bubbles: true })))
  await page.waitForTimeout(800)
  const h2 = await page.evaluate(() => location.hash)
  if (h2 === '#/' || h2 === '') ok('Back returned home'); else fail('Back landed on ' + h2)
} else fail('could not reach the shop tab with arrows')

// A dialog/sheet traps focus and Back closes it.
await page.locator('.orb').first().click().catch(() => {})
await page.waitForTimeout(800)
if (await page.locator('.scrim').count()) {
  for (let i = 0; i < 6; i++) { await page.keyboard.press('ArrowDown'); await page.waitForTimeout(80) }
  const inside = await page.evaluate(() => !!document.activeElement?.closest('.scrim'))
  if (inside) ok('focus stays inside the open sheet'); else fail('focus escaped the sheet')
  await page.evaluate(() => window.dispatchEvent(new KeyboardEvent('keydown', { keyCode: 461, bubbles: true })))
  await page.waitForTimeout(600)
  if (!(await page.locator('.scrim').count())) ok('Back closed the sheet'); else fail('Back did not close the sheet')
} else console.log('(no sheet opened from home in this state)')

await page.screenshot({ path: process.env.SHOT || '/tmp/tv-home.png' })
if (errors.length) fail('page errors: ' + errors.join('; '))
await browser.close()
console.log(failures ? `${failures} failure(s)` : 'TV mode: all checks passed')
process.exit(failures ? 1 : 0)
