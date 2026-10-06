// Touch scrolling on a phone, and that tab taps land where they were aimed.
import { chromium, devices } from 'playwright'
import { existsSync } from 'node:fs'

const base = process.env.BASE || 'http://localhost:4173/Faoxima/Ghajarvpn/pwa/'
const exe = process.env.CHROME || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome'
const browser = await chromium.launch(existsSync(exe) ? { executablePath: exe } : {})
let failures = 0
const fail = m => { failures++; console.log('FAIL', m) }

for (const dev of ['Pixel 7', 'iPhone 13']) {
  const ctx = await browser.newContext({ ...devices[dev], locale: 'fa-IR' })
  await ctx.addInitScript(() => localStorage.setItem('ghajar.token.v1', 'a'.repeat(40)))
  const page = await ctx.newPage()
  const cdp = await ctx.newCDPSession(page)
  // A real finger: touchStart, a run of touchMoves, touchEnd.
  const swipeUp = async () => {
    const vp = page.viewportSize(), x = vp.width / 2
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x, y: vp.height * 0.75 }] })
    for (let y = vp.height * 0.75; y >= vp.height * 0.3; y -= 15) {
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x, y }] })
      await page.waitForTimeout(8)
    }
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] })
    await page.waitForTimeout(600)
  }
  const at = () => page.evaluate(() => location.hash)
  const tab = name => page.locator('.navbar .cell', { hasText: name }).first()

  await page.goto(base + '#/')
  await page.waitForTimeout(1200)
  for (const [hash, label] of [['#/settings', 'تنظیمات'], ['#/shop', 'فروشگاه']]) {
    await tab(label).tap()
    await page.waitForTimeout(700)
    const h = await page.evaluate(() => document.documentElement.scrollHeight - innerHeight)
    if (h > 40) {
      await swipeUp()
      const y = await page.evaluate(() => scrollY || document.scrollingElement.scrollTop)
      if (y < 20) fail(`${dev} ${hash}: touch scroll did not move (scrollable ${h}px)`)
      else console.log(`ok ${dev} ${hash} scrolled ${Math.round(y)}px`)
    } else console.log(`${dev} ${hash}: page fits (${h}px)`)
  }

  // Taps: home / settings / home / shop / home, each must stay put.
  for (const label of ['خانه', 'تنظیمات', 'خانه', 'فروشگاه', 'خانه', 'تنظیمات', 'فروشگاه', 'خانه']) {
    await tab(label).tap()
    await page.waitForTimeout(900)
    const expect = label === 'خانه' ? '#/' : label === 'فروشگاه' ? '#/shop' : '#/settings'
    const h = await at()
    const sel = await page.locator('.navbar .cell.active, .navbar .cell[aria-current="page"]').first().textContent().catch(() => '')
    if (h !== expect) fail(`${dev}: tapped ${label}, landed on ${h}`)
    if (sel && !sel.includes(label)) fail(`${dev}: tapped ${label}, bar shows ${sel}`)
  }

  // Shop sections: tap each tab of the Ghajar shop, it must stay selected.
  await tab('فروشگاه').tap(); await page.waitForTimeout(800)
  await page.locator('section:not([hidden]) .slab.clickable', { hasText: 'فروشگاه قاجار' }).first().tap().catch(() => {})
  await page.waitForTimeout(1200)
  const tabs = page.locator('section:not([hidden]) .tab-rail .tab')
  const n = await tabs.count()
  for (let i = 0; i < n; i++) {
    const name = (await tabs.nth(i).textContent()).trim()
    await tabs.nth(i).tap()
    await page.waitForTimeout(900)
    const active = (await page.locator('section:not([hidden]) .tab-rail .tab.active').first().textContent()).trim()
    if (active !== name) fail(`${dev}: shop tab ${name} jumped to ${active}`)
  }
  // The service picker keeps every card visible when the list is longer than the sheet.
  await tab('خانه').tap(); await page.waitForTimeout(700)
  await page.locator('section:not([hidden]) .home-side button.slab-row').first().tap(); await page.waitForTimeout(700)
  const cards = await page.evaluate(() => {
    const body = document.querySelector('.sheet .sheet-body')
    if (!body) return null
    const first = body.querySelector('.slab')
    for (let i = 0; i < 12 && first; i++) body.insertBefore(first.cloneNode(true), first)
    document.querySelector('.sheet').style.maxHeight = '360px'
    return [...body.querySelectorAll('.slab')].map(e => Math.round(e.getBoundingClientRect().height))
  })
  if (!cards) fail(`${dev}: service picker did not open`)
  else if (cards.some(h => h < 50)) fail(`${dev}: service cards squeezed in a long list (${cards.join(',')})`)
  await page.goBack(); await page.waitForTimeout(500)

  // Back gesture from a tab goes home; from home the app is left alone.
  await tab('تنظیمات').tap(); await page.waitForTimeout(600)
  await page.goBack(); await page.waitForTimeout(800)
  if ((await at()) !== '#/') fail(`${dev}: back from settings landed on ${await at()}`)
  console.log(`${dev}: nav checked (${n} shop tabs)`)
  await ctx.close()
}
await browser.close()
console.log(failures ? `${failures} failures` : 'all checks passed')
process.exit(failures ? 1 : 0)
