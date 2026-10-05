// End-to-end checks against the mock API: the main flows, and - on every
// screen, at every viewport - that nothing sticks out sideways.
import { chromium } from 'playwright'
import { existsSync } from 'node:fs'

const base = process.env.BASE || 'http://localhost:4173/Faoxima/Ghajarvpn/pwa/'
const exe = process.env.CHROME || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome'
const viewports = [[320, 568], [360, 640], [375, 667], [390, 844], [393, 873], [412, 915], [430, 932], [568, 320], [844, 390], [932, 430],
  [768, 1024], [820, 1180], [1024, 1366], [1280, 800], [1440, 900], [1920, 1080]]
const screens = ['#/', '#/shop', '#/settings', '#/settings/personalize', '#/settings/notifications', '#/settings/install', '#/settings/about', '#/settings/account']
let failures = 0
const fail = (msg) => { failures++; console.log('FAIL', msg) }

const browser = await chromium.launch(existsSync(exe) ? { executablePath: exe } : {})

async function overflowCheck(page, label) {
  const problems = await page.evaluate(() => {
    const vw = document.documentElement.clientWidth
    const out = []
    if (document.documentElement.scrollWidth > vw + 1) out.push(`document scrollWidth ${document.documentElement.scrollWidth} > ${vw}`)
    for (const el of document.querySelectorAll('body *')) {
      if (!(el instanceof HTMLElement) && !(el instanceof SVGElement)) continue
      if (el.closest('.tab-rail, [hidden], .scrim, .toast-host')) continue
      const r = el.getBoundingClientRect()
      if (r.width === 0 || r.height === 0) continue
      const style = getComputedStyle(el)
      if (style.position === 'fixed' && el.closest('.navbar')) continue
      if (r.right > vw + 1.5 || r.left < -1.5) out.push(`${el.tagName.toLowerCase()}.${(el.className && el.className.baseVal !== undefined ? el.className.baseVal : el.className) || ''} [${Math.round(r.left)},${Math.round(r.right)}] vw=${vw}`)
      if (out.length > 5) break
    }
    return out
  })
  for (const p of problems) fail(`${label}: ${p}`)
}

// 1. Overflow sweep, signed in, every screen at every size.
for (const [w, h] of viewports) {
  const ctx = await browser.newContext({ viewport: { width: w, height: h }, isMobile: w < 900, hasTouch: w < 900 })
  await ctx.addInitScript(() => { localStorage.setItem('ghajar.token.v1', 'a'.repeat(40)); localStorage.setItem('ghajar.look.v1', JSON.stringify({ fontScale: 1.25 })) })
  const page = await ctx.newPage()
  page.on('pageerror', e => fail(`pageerror ${w}x${h}: ${e.message}`))
  for (const s of screens) {
    await page.goto(base + s)
    await page.waitForTimeout(s === '#/shop' ? 900 : 450)
    await overflowCheck(page, `${w}x${h} ${s}`)
  }
  // Inside the Ghajar shop, every section.
  await page.goto(base + '#/shop')
  await page.waitForTimeout(800)
  await page.locator('section:not([hidden]) .slab.clickable', { hasText: 'فروشگاه قاجار' }).first().click()
  await page.waitForTimeout(600)
  for (const [i, name] of ['خرید', 'سرویس‌ها', 'پیام‌ها', 'کیف پول', 'پشتیبانی', 'تراکنش‌ها'].entries()) {
    await page.locator('section:not([hidden]) [role=tab]', { hasText: name }).first().click()
    await page.waitForTimeout(500)
    await overflowCheck(page, `${w}x${h} ghajar section ${i}`)
  }
  // The add-to-app sheet and a dialog.
  await page.goto(base + '#/')
  await page.waitForTimeout(700)
  await page.locator('button.orb').click()
  await page.waitForTimeout(700)
  await overflowCheck(page, `${w}x${h} add-to-app sheet`)
  const sheetOk = await page.evaluate(() => { const s = document.querySelector('.sheet'); if (!s) return false; const r = s.getBoundingClientRect(); return r.left >= -1 && r.right <= innerWidth + 1 && r.top >= -1 })
  if (!sheetOk) fail(`${w}x${h} sheet outside the viewport`)
  await ctx.close()
}

// 2. Flows.
{
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })
  const page = await ctx.newPage()
  page.on('pageerror', e => fail('pageerror flow: ' + e.message))
  await page.goto(base)
  await page.waitForTimeout(800)
  // Sign in with the bot code.
  await page.locator('nav .cell').nth(1).click()
  await page.locator('section:not([hidden]) .slab.clickable', { hasText: 'فروشگاه قاجار' }).first().click()
  await page.getByText('اتصال با تلگرام').first().click()
  await page.waitForFunction(() => !!localStorage.getItem('ghajar.token.v1'), null, { timeout: 15000 }).catch(() => fail('link flow did not store a token'))
  await page.waitForTimeout(1200)
  // The banner shows the due notice; "خوندم" acknowledges it on the server.
  const banner = page.locator('.notice-banner').first()
  if (!(await banner.isVisible())) fail('notice banner not shown')
  await banner.getByText('خوندم').click()
  await page.waitForTimeout(800)
  const st = await (await fetch(base.replace(/\/Faoxima.*/, '') + '/__state')).json()
  if (!st.dismissed.includes(41) && !st.dismissed.includes(42)) fail('acknowledge did not reach the server')
  // Buy: confirm, card-to-card, receipt upload.
  await page.getByRole('tab', { name: /خرید/ }).click()
  await page.getByText('خرید این پلن').first().click()
  await page.getByText('تأیید و ادامه').click()
  await page.waitForTimeout(900)
  await page.locator('button.mcard', { hasText: 'کارت به کارت' }).click()
  await page.waitForTimeout(900)
  if (!(await page.getByText('6037 9912 3456 7890').isVisible())) fail('card number not shown')
  await page.locator('input[type=file]').first().setInputFiles({ name: 'r.png', mimeType: 'image/png', buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==', 'base64') })
  await page.getByText('ارسال رسید برای بررسی').click()
  await page.waitForTimeout(900)
  const st2 = await (await fetch(base.replace(/\/Faoxima.*/, '') + '/__state')).json()
  if (st2.receipts < 1) fail('receipt not uploaded')
  // The payment is approved: the next status check delivers the service.
  await fetch(base.replace(/\/Faoxima.*/, '') + '/__pay')
  await page.getByText('پرداخت کردم؛ بررسی و دریافت سرویس').click()
  await page.waitForSelector('.sheet', { timeout: 8000 }).catch(() => fail('delivery sheet did not open after payment'))
  if (!(await page.locator('.qr-box').isVisible())) fail('delivery QR missing')
  await ctx.close()
}

await browser.close()
console.log(failures ? `${failures} failure(s)` : 'all checks passed')
process.exit(failures ? 1 : 0)
