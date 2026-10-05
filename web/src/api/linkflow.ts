/** GhajarLinkFlow.kt: pure interpretation of the bot's WebLink contract. */

export type LinkState = 'PENDING' | 'LINKED' | 'EXPIRED' | 'NOT_FOUND' | 'FORCE_JOIN' | 'PHONE_REQUIRED' |
  'SERVER_ERROR' | 'NETWORK_ERROR' | 'STORAGE_ERROR' | 'SUPERSEDED'

export function bearerOf(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const v = value.trim()
  if (!v || v.toLowerCase() === 'null' || v.toLowerCase() === 'undefined') return null
  if (/[\s\x00-\x1f\x7f]/.test(v)) return null
  return v
}

export function responseState(status: unknown, gate: unknown, token: string | null): LinkState {
  switch (String(status ?? '').toLowerCase()) {
    case 'pending': return 'PENDING'
    case 'expired': return 'EXPIRED'
    case 'not_found': return 'NOT_FOUND'
    case 'linked':
      if (gate === 'force_join') return 'FORCE_JOIN'
      if (gate === 'phone_required') return 'PHONE_REQUIRED'
      if (gate == null || gate === '') return bearerOf(token) != null ? 'LINKED' : 'SERVER_ERROR'
      return 'SERVER_ERROR'
    default: return 'SERVER_ERROR'
  }
}

export function remainingSeconds(expiresAtMillis: number, nowMillis: number): number {
  return Math.floor((Math.min(Math.max(expiresAtMillis - nowMillis, 0), 900_000) + 999) / 1000)
}

export function retryDelayMillis(failures: number): number {
  if (failures <= 0) return 2000
  if (failures === 1) return 4000
  if (failures === 2) return 8000
  if (failures === 3) return 16000
  return 30000
}

export function invalidatesAccount(authenticated: boolean, httpCode: number): boolean {
  return authenticated && (httpCode === 401 || httpCode === 403)
}

export function verificationGate(previous: LinkState | null, current: LinkState): LinkState | null {
  if (current === 'FORCE_JOIN' || current === 'PHONE_REQUIRED') return current
  if (current === 'NETWORK_ERROR' || current === 'SERVER_ERROR' || current === 'STORAGE_ERROR') return previous
  return null
}

export function linkMessage(state: LinkState): string {
  switch (state) {
    case 'PENDING': return 'منتظر تأیید ربات هستیم؛ در تلگرام «Start / شروع» را بزن و برگرد.'
    case 'LINKED': return 'حساب با موفقیت و به‌صورت امن متصل شد'
    case 'EXPIRED': return 'مهلت کد اتصال تمام شد؛ یک کد تازه بگیر.'
    case 'NOT_FOUND': return 'این درخواست اتصال در سرور پیدا نشد؛ یک کد تازه بگیر.'
    case 'FORCE_JOIN': return 'ربات منتظر عضویت کانال است؛ مراحل عضویت را در ربات کامل کن و سپس «بررسی دوباره» را بزن.'
    case 'PHONE_REQUIRED': return 'ربات منتظر تأیید شماره است؛ تأیید را فقط داخل ربات انجام بده و سپس «بررسی دوباره» را بزن.'
    case 'NETWORK_ERROR': return 'ارتباط با سرور برقرار نشد؛ اینترنت را بررسی کن. تا پایان مهلت، دوباره تلاش می‌کنیم.'
    case 'SERVER_ERROR': return 'سرور هنوز ورود را کامل نکرده است؛ کد را نگه داشته‌ایم و دوباره بررسی می‌کنیم.'
    case 'STORAGE_ERROR': return 'ذخیرهٔ امن حساب در گوشی انجام نشد؛ دوباره بررسی کن.'
    case 'SUPERSEDED': return 'این درخواست اتصال دیگر فعال نیست؛ یک کد تازه بگیر.'
  }
}

const LINK_CODE = /^[A-Za-z0-9]{4,12}$/

function botName(username: string | null | undefined): string {
  const u = (username ?? '').trim().replace(/^@/, '')
  return /^[A-Za-z0-9_]{5,32}$/.test(u) ? u : 'Ghajar_vpnbot'
}

/** GhajarUiRules.botLoginUrls: the Telegram deep link that carries the pairing code. */
export function botLoginUrls(username: string | null | undefined, code: string | null | undefined): string[] {
  if (!code || !LINK_CODE.test(code)) return []
  const bot = botName(username)
  return [`tg://resolve?domain=${bot}&start=applink_${code}`, `https://t.me/${bot}?start=applink_${code}`]
}

export function botVerificationUrls(username: string | null | undefined): string[] {
  const bot = botName(username)
  return [`tg://resolve?domain=${bot}&start=start`, `https://t.me/${bot}?start=start`]
}

export function validPendingLink(code: string, token: string, expiresAtMillis: number, nowMillis: number): boolean {
  return LINK_CODE.test(code) && /^[a-fA-F0-9]{32,128}$/.test(token) &&
    expiresAtMillis > nowMillis && expiresAtMillis - nowMillis <= 15 * 60 * 1000
}
