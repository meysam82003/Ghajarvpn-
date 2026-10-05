// Screenshots of the main screens on a phone viewport, for visual review.
import { chromium } from 'playwright'

const base = process.env.BASE || 'http://localhost:4173/Faoxima/Ghajarvpn/pwa/'
const out = process.argv[2] || '/tmp/claude-0/shots'
const exe = process.env.CHROME || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome'
const browser = await chromium.launch({ executablePath: exe })
const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true, locale: 'fa-IR' })
const page = await ctx.newPage()
page.on('console', m => { if (m.type() === 'error') console.log('console:', m.text()) })
page.on('pageerror', e => console.log('pageerror:', e.message))
const shot = async name => { await page.waitForTimeout(700); await page.screenshot({ path: `${out}/${name}.png` }) }

await page.goto(base)
await page.waitForTimeout(1200)
await shot('01-home-guest')
await page.locator('nav .cell').nth(1).click()
await shot('02-shop-guest')
await page.getByText('فروشگاه قاجار').first().click()
await shot('03-ghajar-guest')
await page.getByText('اتصال با تلگرام').first().click()
await page.waitForTimeout(5000)
await shot('04-linked')
await page.screenshot({ path: `${out}/04b-linked-full.png`, fullPage: true })
await browser.close()
