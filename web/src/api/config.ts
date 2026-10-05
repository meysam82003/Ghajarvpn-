/**
 * Where the API lives, without ever naming a host.
 *
 * The PWA is deployed next to the API (…/Ghajarvpn/pwa/ beside …/Ghajarvpn/api/)
 * or behind a front domain that proxies the same relative layout, so every
 * endpoint is resolved against the page's own location. The deployed bundle
 * therefore carries no domain at all.
 */
function apiBase(): string {
  const override = (import.meta.env.VITE_API_BASE as string | undefined)?.trim()
  if (override) return override.endsWith('/') ? override : override + '/'
  return new URL('../api/', document.baseURI).toString()
}

export const API_BASE = apiBase()
export const MINIAPP_API = API_BASE + 'miniapp.php'
export const WEBLINK_API = API_BASE + 'weblink.php'
export const NOTICES_API = API_BASE + 'notices.php'
export const MARKET_API = API_BASE + 'market.php'
/** Notification registration ships inside the app's own folder (pwa/push.php). */
export const PUSH_API = new URL('./push.php', document.baseURI).toString()

export const APP_NAME_FA = 'قاجار وی پی ان'
export const APP_NAME_EN = 'Ghajarvpn'
export const BOT_USERNAME = 'Ghajar_vpnbot'
export const TELEGRAM_CHANNEL_URL = 'https://t.me/Ghajarvpn'
export const TELEGRAM_BOT_URL = 'https://t.me/Ghajar_vpnbot'
export const GITHUB_URL = 'https://github.com/meysam82003/Ghajarvpn-'

/** BrandConfig.CLIENT_HEADER/CLIENT_ID: the server tells the app apart from the mini app with it. */
export const CLIENT_HEADER = 'X-Ghajar-Client'
export const CLIENT_ID = 'app'

export const APP_VERSION = '1.0.0'

/** BrandConfig.sanitizePublicText. */
export function visible(value: unknown): string {
  if (value == null) return ''
  return String(value)
    .replace(new RegExp('Fao' + 'xima', 'gi'), APP_NAME_EN)
    .replace(new RegExp('GR' + 'oute', 'gi'), APP_NAME_EN)
    .replace(new RegExp('Gozar' + 'Net', 'gi'), APP_NAME_EN)
    .replace(new RegExp('Oracle' + ' ?VPN', 'gi'), APP_NAME_EN)
    .replace(/فاکسیما/g, APP_NAME_FA)
}

const trustedPaymentSuffixes = [
  'zarinpal.com', 'aqayepardakht.ir', 'zarinpey.com', 'shaparak.ir', 'behpardakht.com', 'pec.ir', 'sep.ir',
  'sadadpsp.ir', 'asanpardakht.ir', 'sepehrpay.com', 'idpay.ir', 'nextpay.org', 'plisio.net', 'nowpayments.io',
  'bluepal.ir', 'blupal.net', 'uniquepay.ir'
]

/**
 * GhajarPaymentPolicy.allows: HTTPS only, no credentials in the URL, default
 * port, and either this site's own host or a known payment provider.
 */
export function isTrustedPaymentUrl(value: string): boolean {
  let u: URL
  try { u = new URL(value) } catch { return false }
  if (u.protocol !== 'https:' || u.username || u.password || (u.port && u.port !== '443')) return false
  const host = u.hostname.toLowerCase().replace(/\.$/, '')
  if (host === location.hostname.toLowerCase()) return true
  try { if (host === new URL(API_BASE).hostname.toLowerCase()) return true } catch { /* ignore */ }
  return trustedPaymentSuffixes.some(s => host === s || host.endsWith('.' + s))
}

export function isHttps(value: string | null | undefined): boolean {
  if (!value) return false
  try { const u = new URL(value); return u.protocol === 'https:' && !!u.hostname } catch { return false }
}
