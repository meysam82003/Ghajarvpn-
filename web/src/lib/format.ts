/** Number, size and date formatting, as the app prints them. */

const FA_DIGITS = '۰۱۲۳۴۵۶۷۸۹'

export function fa(value: string | number): string {
  return String(value).replace(/[0-9]/g, d => FA_DIGITS[+d])
}

/** GhajarUiRules.asciiDigits: Persian/Arabic digits typed in, ASCII digits out. */
export function asciiDigits(value: string): string {
  return value.replace(/[۰-۹]/g, d => String(FA_DIGITS.indexOf(d))).replace(/[٠-٩]/g, d => String('٠١٢٣٤٥٦٧٨٩'.indexOf(d))).replace(/[^0-9]/g, '')
}

const priceFormat = (() => {
  try { return new Intl.NumberFormat('fa-IR', { maximumFractionDigits: 0 }) } catch { return null }
})()

/** NumberFormat.getIntegerInstance(Locale("fa", "IR")). */
export function formatPrice(n: number | null | undefined): string {
  const v = Math.trunc(Number(n ?? 0))
  if (priceFormat) return priceFormat.format(v)
  return fa(String(v).replace(/\B(?=(\d{3})+(?!\d))/g, '٬'))
}

const LRE = '‪', PDF = '‬'

/** formatBytes(bytes, Lang.FA). */
export function formatBytes(bytes: number): string {
  let num: string, unit: string
  if (bytes < 1024) { num = String(bytes); unit = 'بایت' }
  else if (bytes < 1024 * 1024) { num = (bytes / 1024).toFixed(1); unit = 'کیلوبایت' }
  else if (bytes < 1024 * 1024 * 1024) { num = (bytes / (1024 * 1024)).toFixed(1); unit = 'مگابایت' }
  else { num = (bytes / (1024 * 1024 * 1024)).toFixed(2); unit = 'گیگابایت' }
  return `${LRE}${fa(num)}${PDF} ${unit}`
}

export function gb2(bytes: number): string {
  return fa((bytes / 1073741824).toFixed(2))
}

/** A plan size as the app shows it: 10 گیگ, 2.5 گیگ (stripTrailingZeros). */
export function plainNumber(n: number): string {
  return String(Number(n.toFixed(6)))
}

/** marketJalali: unix seconds as ۱۴۰۵/۰۷/۰۴ ۱۲:۳۰ in Tehran time. */
export function jalali(epochSeconds: number, withTime = true): string {
  if (!epochSeconds || epochSeconds <= 0) return '—'
  const d = new Date(epochSeconds * 1000)
  let parts: Record<string, number>
  try {
    const f = new Intl.DateTimeFormat('en-US', { timeZone: 'Asia/Tehran', year: 'numeric', month: 'numeric', day: 'numeric', hour: 'numeric', minute: 'numeric', hourCycle: 'h23' })
    parts = Object.fromEntries(f.formatToParts(d).filter(p => p.type !== 'literal').map(p => [p.type, Number(p.value)]))
  } catch {
    parts = { year: d.getFullYear(), month: d.getMonth() + 1, day: d.getDate(), hour: d.getHours(), minute: d.getMinutes() }
  }
  const gy = parts.year, gm = parts.month, gd = parts.day
  const gdm = [0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334]
  const gy2 = gm > 2 ? gy + 1 : gy
  let days = 355666 + 365 * gy + Math.floor((gy2 + 3) / 4) - Math.floor((gy2 + 99) / 100) + Math.floor((gy2 + 399) / 400) + gd + gdm[gm - 1]
  let jy = -1595 + 33 * Math.floor(days / 12053)
  days %= 12053
  jy += 4 * Math.floor(days / 1461)
  days %= 1461
  if (days > 365) { jy += Math.floor((days - 1) / 365); days = (days - 1) % 365 }
  const jm = days < 186 ? 1 + Math.floor(days / 31) : 7 + Math.floor((days - 186) / 30)
  const jd = 1 + (days < 186 ? days % 31 : (days - 186) % 30)
  const p2 = (n: number) => String(n).padStart(2, '0')
  const date = `${String(jy).padStart(4, '0')}/${p2(jm)}/${p2(jd)}`
  return fa(withTime ? `${date} ${p2(parts.hour % 24)}:${p2(parts.minute)}` : date)
}

/** GhajarUiRules.brandedSubscriptionTitle. */
export function brandedTitle(totalBytes: number, fallbackName: string): string {
  if (totalBytes <= 0) {
    const clean = fallbackName.trim() || 'VPN'
    return /^ghajarvpn/i.test(clean) ? clean : `Ghajarvpn • ${clean}`
  }
  const gib = totalBytes / 1073741824
  const nearest = Math.round(gib)
  const quota = Math.abs(gib - nearest) < 0.005 ? String(nearest) : gib.toFixed(1).replace(/0+$/, '').replace(/\.$/, '')
  return `Ghajarvpn ${quota} GB`
}

/** "isolate" a left-to-right token (username, card number) inside Persian text. */
export function ltr(s: string): string { return `⁦${s}⁩` }
