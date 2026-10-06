import { CLIENT_HEADER, CLIENT_ID, MARKET_API, MINIAPP_API, NOTICES_API, WEBLINK_API, SUB_RELAY_API, APP_VERSION, isHttps, visible } from './config'
import { clearAccount, token, savePendingLink, pendingLink, LinkSession, clearPendingLink, saveToken } from './account'
import * as M from './models'
import { LinkState, bearerOf, responseState, invalidatesAccount } from './linkflow'
import { timeBucketMatches } from './rules'

/** GhajarApiException: a message already written for the user, and the HTTP code. */
export class ApiError extends Error {
  constructor(message: string, public httpCode = 0, public network = false) { super(message) }
}

type Json = Record<string, any>

const READ_TIMEOUT = 32_000
const UPLOAD_TIMEOUT = 60_000
export const MAX_RECEIPT_BYTES = 8 * 1024 * 1024

interface RequestOpts {
  method?: string
  bearer?: string | null
  body?: Json | null
  allowPaymentRequired?: boolean
  allowLinkGate?: boolean
  timeout?: number
}

async function performRequest(url: string, o: RequestOpts): Promise<Json> {
  const ctrl = new AbortController()
  const timer = setTimeout(() => ctrl.abort(), o.timeout ?? READ_TIMEOUT)
  const headers: Record<string, string> = { Accept: 'application/json', [CLIENT_HEADER]: CLIENT_ID, 'X-Ghajar-Pwa': APP_VERSION }
  if (o.bearer) headers.Authorization = `Bearer ${o.bearer}`
  if (o.body) headers['Content-Type'] = 'application/json; charset=utf-8'
  let res: Response
  try {
    res = await fetch(url, {
      method: o.method ?? 'GET',
      headers,
      body: o.body ? JSON.stringify(o.body) : undefined,
      signal: ctrl.signal,
      credentials: 'same-origin',
      cache: 'no-store',
      referrerPolicy: 'no-referrer'
    })
  } catch (e) {
    clearTimeout(timer)
    const aborted = (e as Error)?.name === 'AbortError'
    throw new ApiError(aborted
      ? 'سرور به‌موقع پاسخ نداد؛ اتصال اینترنت را بررسی کن و دوباره تلاش کن.'
      : 'اتصال اینترنت برقرار نیست؛ اتصال شبکه یا VPN را بررسی کن و دوباره تلاش کن.', 0, true)
  }
  let raw = ''
  try { raw = await res.text() } finally { clearTimeout(timer) }
  return readResponse(res.status, raw, o)
}

function readResponse(code: number, raw: string, o: RequestOpts): Json {
  let parsed: Json | null = null
  try { parsed = JSON.parse(raw) } catch { parsed = null }
  if (code === 404 && (!parsed || !('msg' in parsed))) {
    throw new ApiError('مسیر ورود یا فروشگاه روی سرور موجود نیست (۴۰۴)؛ نصب ربات باید بروزرسانی شود.', code)
  }
  if (!parsed || typeof parsed !== 'object') throw new ApiError('پاسخ فروشگاه قابل خواندن نیست', code)
  const envelope = parsed
  const paymentRequired = envelope.requires_payment === true || envelope.obj?.requires_payment === true
  if (o.allowPaymentRequired && paymentRequired) return envelope
  if (invalidatesAccount(!!o.bearer, code)) clearAccount()
  if (o.allowLinkGate && code >= 200 && code < 300 && envelope.link_status === 'linked' &&
    ['force_join', 'phone_required'].includes(envelope.gate)) return envelope
  if (code < 200 || code > 299 || envelope.status === false) {
    throw new ApiError(visible(envelope.msg ?? `خطای فروشگاه (${code})`), code)
  }
  return envelope
}

/** One quiet retry on a network failure, as the app's beginLink and shop load do. */
async function requestJson(url: string, o: RequestOpts): Promise<Json> {
  try {
    return await performRequest(url, o)
  } catch (e) {
    if (e instanceof ApiError && e.network && (o.method ?? 'GET') === 'GET') {
      await new Promise(r => setTimeout(r, 900))
      return performRequest(url, o)
    }
    throw e
  }
}

function requireToken(): string {
  const t = token()
  if (!t) throw new ApiError('حساب قاجار وی پی ان هنوز متصل نشده است', 401)
  return t
}

function query(params: Record<string, string>): string {
  return Object.entries(params).map(([k, v]) => encodeURIComponent(k) + '=' + encodeURIComponent(v)).join('&')
}

async function action(name: string, opts: { method?: string; params?: Record<string, string>; body?: Json | null; allowPaymentRequired?: boolean } = {}): Promise<Json> {
  const method = opts.method ?? 'GET'
  const q = query({ actions: name, ...(opts.params ?? {}) })
  const body = method === 'GET' ? null : { ...(opts.body ?? {}), actions: name }
  return requestJson(`${MINIAPP_API}?${q}`, { method, bearer: requireToken(), body, allowPaymentRequired: opts.allowPaymentRequired })
}

async function marketAction(name: string, opts: { method?: string; params?: Record<string, string>; body?: Json | null; allowAnonymous?: boolean } = {}): Promise<Json> {
  const method = opts.method ?? 'GET'
  const q = query({ actions: name, ...(opts.params ?? {}) })
  const body = method === 'GET' ? null : { ...(opts.body ?? {}), actions: name }
  const bearer = opts.allowAnonymous ? (token() || null) : requireToken()
  return requestJson(`${MARKET_API}?${q}`, { method, bearer, body })
}

// ---------------------------------------------------------------- json helpers

function payload(o: Json): any {
  if (o.obj != null) return o.obj
  if (o.data != null) return o.data
  return o
}
function payloadObject(o: Json): Json { const p = payload(o); return p && typeof p === 'object' && !Array.isArray(p) ? p : {} }
function payloadArray(o: Json): Json[] {
  const p = payload(o)
  if (Array.isArray(p)) return p.filter(x => x && typeof x === 'object')
  if (p && typeof p === 'object' && Array.isArray(p.items)) return p.items.filter((x: any) => x && typeof x === 'object')
  return []
}
function arr(v: any): Json[] { return Array.isArray(v) ? v.filter(x => x && typeof x === 'object') : [] }
function str(v: any, def = ''): string { return v == null ? def : String(v) }
function num(v: any): number | null {
  if (v == null || v === '' || (typeof v === 'string' && v.trim() === '')) return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}
function int(v: any, def = 0): number { const n = num(v); return n == null ? def : Math.trunc(n) }
function bool(v: any, def = false): boolean {
  if (v == null) return def
  if (typeof v === 'boolean') return v
  if (typeof v === 'number') return v !== 0
  const s = String(v).toLowerCase()
  if (s === 'true' || s === '1') return true
  if (s === 'false' || s === '0' || s === '') return false
  return def
}

// ---------------------------------------------------------------- account link

export async function beginLink(): Promise<LinkSession> {
  let root: Json
  try {
    root = await performRequest(`${WEBLINK_API}?action=generate`, { method: 'POST', bearer: null })
  } catch (e) {
    if (e instanceof ApiError && e.network) root = await performRequest(`${WEBLINK_API}?action=generate`, { method: 'POST', bearer: null })
    else throw e
  }
  const code = str(root.code), session = str(root.session_token)
  if (!code || !session) throw new ApiError('سرور کد اتصال صادر نکرد')
  const expiresIn = int(root.expires_in, 300)
  const link: LinkSession = {
    code, sessionToken: session, botUsername: str(root.bot_username, 'Ghajar_vpnbot'),
    expiresInSeconds: expiresIn, expiresAtMillis: Date.now() + Math.min(Math.max(expiresIn, 0), 900) * 1000
  }
  if (!savePendingLink(link)) throw new ApiError('ذخیرهٔ امن کد اتصال ناموفق بود؛ دوباره تلاش کن')
  return link
}

export async function pollLink(sessionToken: string): Promise<LinkState> {
  if (pendingLink()?.sessionToken !== sessionToken) return 'SUPERSEDED'
  const root = await requestJson(`${WEBLINK_API}?action=status&session_token=${encodeURIComponent(sessionToken)}`,
    { method: 'GET', bearer: null, allowLinkGate: true })
  const issued = bearerOf(root.token)
  const state = responseState(root.link_status, root.gate, issued)
  if (state !== 'LINKED') return state
  if (pendingLink()?.sessionToken !== sessionToken) return 'SUPERSEDED'
  if (!saveToken(issued!)) return 'STORAGE_ERROR'
  clearPendingLink()
  return 'LINKED'
}

/** Spends a one-time ticket from the Android app ("open the full panel") for this same session. */
export async function redeemTicket(ticket: string): Promise<boolean> {
  const root = await performRequest(`${WEBLINK_API}?action=redeem&ticket=${encodeURIComponent(ticket)}`, { method: 'POST', bearer: null })
  const t = bearerOf(root.token)
  if (!t) return false
  return saveToken(t)
}

// ---------------------------------------------------------------- catalogue

export async function countries(): Promise<M.Panel[]> {
  return payloadArray(await action('countries')).map(row => ({
    id: str(row.id), name: visible(row.name ?? 'سرور قاجار'), custom: bool(row.is_custom),
    customUsername: bool(row.is_username), usernameRequired: bool(row.is_username_required), noteEnabled: bool(row.is_note)
  })).filter(p => p.id !== '')
}

export async function categories(countryId: string): Promise<M.Category[]> {
  return payloadArray(await action('categories', { params: { country_id: countryId } }))
    .map(row => ({ id: str(row.id), name: visible(row.name) })).filter(c => c.id !== '')
}

export async function timeRanges(countryId: string): Promise<M.TimeRange[]> {
  return payloadArray(await action('time_ranges', { params: { country_id: countryId } }))
    .map(row => ({ days: int(row.day), name: visible(row.name) }))
}

export async function products(countryId: string, categoryId: string | null = null, timeDays: number | null = null): Promise<M.Product[]> {
  const params: Record<string, string> = { country_id: countryId }
  if (categoryId) params.category_id = categoryId
  const all = payloadArray(await action('services', { params })).map(row => {
    const price = num(row.price)
    const traffic = num(row.traffic_gb)
    const days = int(row.time_days, -1)
    return {
      id: str(row.id), name: visible(row.name ?? 'سرویس قاجار'),
      price: price != null && price >= 0 ? Math.trunc(price) : null,
      trafficGb: traffic != null && traffic >= 0 ? traffic : null,
      days: days >= 0 ? days : null,
      description: visible(row.description),
      countryId: str(row.country_id, countryId)
    }
  }).filter(p => p.id !== '')
  if (timeDays == null) return all
  return all.filter(p => timeBucketMatches(timeDays, p.days))
}

export async function customQuote(countryId: string, trafficGb: number, timeDays: number): Promise<M.CustomQuote> {
  const p = payloadObject(await action('custom_price', { params: { country_id: countryId, traffic_gb: String(trafficGb), time_days: String(timeDays) } }))
  const price = num(p.price)
  return { price: price == null ? null : Math.trunc(price), trafficMin: int(p.traffic_min), trafficMax: int(p.traffic_max), timeMin: int(p.time_min), timeMax: int(p.time_max) }
}

export async function ownedServices(maxPages = 10): Promise<M.OwnedService[]> {
  const collected: M.OwnedService[] = []
  let page = 1, pages = 1
  do {
    const p = payloadObject(await action('invoices', { params: { page: String(page), limit: '10' } }))
    for (const row of arr(p.items)) {
      const username = str(row.username)
      if (!username) continue
      let info: Json | null = null
      const rawInfo = row.user_info
      if (rawInfo && typeof rawInfo === 'object') info = rawInfo
      else if (typeof rawInfo === 'string' && rawInfo.trim() && rawInfo !== 'null') { try { info = JSON.parse(rawInfo) } catch { info = null } }
      collected.push({
        username,
        productName: visible(row.name_product ?? row.product_name ?? 'سرویس قاجار'),
        status: visible(row.status ?? row.Status ?? 'unknown'),
        location: visible(row.Service_location),
        invoiceId: str(row.id_invoice),
        dataLimitBytes: info ? num(info.data_limit) : null,
        usedBytes: info ? num(info.used_traffic) : null,
        expireTimestamp: info ? num(info.expire) : null,
        planGb: num(row.Volume),
        planDays: num(row.Service_time) == null ? null : Math.trunc(num(row.Service_time)!),
        soldAt: num(row.time_sell)
      })
    }
    pages = Math.min(Math.max(int(p.total_pages, 1), 1), maxPages)
    page++
  } while (page <= pages)
  const seen = new Set<string>()
  return collected.filter(s => (seen.has(s.username) ? false : (seen.add(s.username), true)))
}

export function serviceFrom(row: Json, fallbackUsername: string): M.ServiceDetails {
  const outputs: string[] = []
  for (const value of Array.isArray(row.service_output) ? row.service_output : []) {
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      const entry = value.value
      if (Array.isArray(entry)) entry.forEach((x: any) => x != null && outputs.push(String(x)))
      else if (entry != null) outputs.push(String(entry))
    } else if (Array.isArray(value)) value.forEach((x: any) => x != null && outputs.push(String(x)))
    else if (value != null) outputs.push(String(value))
  }
  for (const c of Array.isArray(row.configs) ? row.configs : []) if (c != null) outputs.push(String(c))
  const sub = str(row.subscription_url)
  return {
    username: str(row.username, fallbackUsername) || fallbackUsername,
    productName: visible(row.product_name ?? row.name_product ?? 'سرویس قاجار'),
    status: visible(row.status ?? 'active'),
    usedGb: num(row.used_traffic_gb), totalGb: num(row.total_traffic_gb), remainingGb: num(row.remaining_traffic_gb),
    expiresAt: visible(row.expiration_time),
    subscriptionUrl: isHttps(sub) ? sub : null,
    outputs: [...new Set(outputs.filter(s => s.trim() !== ''))]
  }
}

export async function service(username: string): Promise<M.ServiceDetails> {
  return serviceFrom(payloadObject(await action('service', { params: { username } })), username)
}

export async function renewOptions(username: string): Promise<M.RenewOptions> {
  const p = payloadObject(await action('service_renew_options', { params: { username } }))
  const currentCode = str(p.current_plan?.code) || null
  const custom = p.custom ?? {}
  return {
    username: str(p.username, username),
    panelName: visible(p.panel?.name ?? ''),
    products: arr(p.products).filter(r => str(r.code)).map(r => ({
      code: str(r.code), name: visible(r.name ?? 'پلن قاجار'), volumeGb: int(r.volume_gb), timeDays: int(r.time_days),
      price: Math.trunc(num(r.price) ?? 0), showPrice: bool(r.show_price, true), note: visible(r.note), isCurrentPlan: str(r.code) === currentCode
    })),
    currentPlanCode: currentCode,
    showPrice: bool(p.show_price, true),
    discountPercent: int(p.discount),
    balance: Math.trunc(num(p.balance) ?? 0),
    custom: {
      enabled: bool(custom.enabled), forced: bool(custom.force), pricePerGb: Math.trunc(num(custom.price_per_gb) ?? 0),
      pricePerDay: Math.trunc(num(custom.price_per_day) ?? 0), minVolumeGb: int(custom.min_volume_gb), maxVolumeGb: int(custom.max_volume_gb),
      minTimeDays: int(custom.min_time_days), maxTimeDays: int(custom.max_time_days)
    }
  }
}

export async function confirmRenew(username: string, productCode: string | null, customVolumeGb?: number | null, customTimeDays?: number | null, discountCode?: string | null): Promise<M.RenewResult> {
  const body: Json = { username }
  if (productCode != null) body.product_code = productCode
  else body.custom = { traffic_gb: customVolumeGb ?? 0, time_days: customTimeDays ?? 0 }
  if (discountCode) body.discount_code = discountCode
  const root = await action('service_renew_confirm', { method: 'POST', body, allowPaymentRequired: true })
  const p = payloadObject(root)
  const po = root.requires_payment === true ? root : (p.kind === 'requires_payment' ? p : null)
  if (po) {
    return {
      completed: false, requiresPayment: true, username: str(po.username, username),
      amountDue: Math.trunc(num(po.amount_due) ?? 0), balance: Math.trunc(num(po.balance) ?? 0),
      price: Math.trunc(num(po.price) ?? 0), orderId: str(po.order_id) || null
    }
  }
  return {
    completed: bool(root.status, true) && bool(p.success, true), requiresPayment: false, username,
    amountDue: 0, balance: Math.trunc(num(p.balance) ?? 0), price: 0, orderId: null
  }
}

// ---------------------------------------------------------------- notices

function feedNotice(row: Json): M.Notice | null {
  const body = visible(row.body).trim()
  if (!body) return null
  const kind = str(row.kind)
  const serverId = int(row.id)
  const titleByKind = kind === 'service_time' ? 'مهلت سرویس رو به پایان است'
    : kind === 'service_volume' ? 'حجم سرویس رو به پایان است'
      : kind === 'shop_status' ? 'وضعیت فروشگاه' : 'اعلان قاجار وی پی ان'
  const act = str(row.action, 'none')
  const ref = str(row.action_ref)
  return {
    id: `notice:${serverId}`,
    title: visible(row.title) || titleByKind,
    message: body,
    important: ['shop_status', 'service_time', 'service_volume'].includes(kind),
    serviceAlert: kind === 'service_time' || kind === 'service_volume',
    serviceUsername: ref && act === 'renew' ? ref : null,
    meta: null,
    action: act,
    actionRef: ref,
    shouldFloat: bool(row.should_float, true),
    repeatAfterSec: int(row.repeat_after),
    seen: bool(row.seen)
  }
}

function noticeFrom(row: Json, source: string, personal: boolean): M.Notice | null {
  const message = visible(row.body || row.message || row.text || '').trim()
  if (!message) return null
  const type = str(row.type)
  const m = row.meta && typeof row.meta === 'object' ? row.meta : null
  return {
    id: `${source}:${str(row.id, str(row.created_at, String(hash(message))))}`,
    title: visible(row.title) || (personal ? 'پیام سرویس قاجار' : 'اعلان قاجار وی پی ان'),
    message,
    important: source === 'floating' || bool(row.important) || int(row.priority) > 0 || ['volume', 'time'].includes(type),
    serviceAlert: personal || ['volume', 'time', 'service'].includes(type),
    serviceUsername: str(row.service) || null,
    meta: m ? { dataLimitBytes: num(m.data_limit), usedBytes: num(m.used_traffic), remainingBytes: num(m.remaining_bytes), daysRemaining: num(m.days_remaining), expireTimestamp: num(m.expire_ts) } : null,
    action: 'none', actionRef: '', shouldFloat: true, repeatAfterSec: 0, seen: bool(row.seen)
  }
}

function hash(s: string): number { let h = 0; for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0; return h >>> 0 }

async function legacyFeed(): Promise<M.NoticeFeed> {
  let failure: unknown = null
  let fetched = 0
  async function fetchNotice(name: string): Promise<Json | null> {
    try { const o = payloadObject(await action(name)); fetched++; return o } catch (e) { failure = e; return null }
  }
  const recent = arr((await fetchNotice('notification_recent'))?.notifications)
  const active = (await fetchNotice('notification_info'))?.notification
  if (fetched === 0) throw failure ?? new ApiError('دریافت اعلان ناموفق بود')
  const now = Date.now() / 1000
  const deliverable = (r: Json) => !bool(r.seen) && (int(r.expires_at) <= 0 || int(r.expires_at) > now)
  const list: M.Notice[] = []
  if (active && typeof active === 'object' && deliverable(active)) {
    const n = noticeFrom(active, 'notification', false)
    if (n) list.push({ ...n, important: true })
  }
  for (const r of recent.filter(deliverable)) { const n = noticeFrom(r, 'notification', false); if (n) list.push(n) }
  const seen = new Set<string>()
  const uniq = list.filter(n => (seen.has(n.id) ? false : (seen.add(n.id), true)))
  return { notices: uniq, unseen: uniq.filter(n => !n.seen).length, shopEnabled: true, shopMessage: '', gate: '', gateMessage: '' }
}

export async function noticeFeed(): Promise<M.NoticeFeed> {
  let raw: Json
  try {
    raw = await requestJson(`${NOTICES_API}?action=feed&client=${CLIENT_ID}`, { method: 'GET', bearer: requireToken() })
  } catch (e) {
    if (e instanceof ApiError && e.httpCode === 401) throw e
    return legacyFeed()
  }
  if (raw.status !== true) return legacyFeed()
  const shop = raw.shop ?? {}
  return {
    notices: arr(raw.notices).map(feedNotice).filter((n): n is M.Notice => n != null),
    unseen: int(raw.unseen),
    shopEnabled: bool(shop.enabled, true),
    shopMessage: str(shop.message),
    gate: str(raw.gate),
    gateMessage: str(raw.gate_message)
  }
}

async function stampNotices(what: string, ids: string[]): Promise<void> {
  const serverIds = ids.map(id => parseInt(id.replace('notice:', ''), 10)).filter(n => Number.isFinite(n))
  if (!serverIds.length) return
  try {
    await performRequest(`${NOTICES_API}?action=${what}&client=${CLIENT_ID}`, { method: 'POST', bearer: requireToken(), body: { ids: serverIds } })
  } catch { /* best effort, as the app */ }
}

export const markNoticesShown = (ids: string[]) => stampNotices('shown', ids)

/** A signed relay address for a subscription (flags first, for desktop clients); null when unavailable. */
export async function relaySubscription(url: string): Promise<string | null> {
  try {
    const res = await fetch(`${SUB_RELAY_API}?action=sign`, {
      method: 'POST', cache: 'no-store',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json', Authorization: `Bearer ${requireToken()}`, [CLIENT_HEADER]: CLIENT_ID },
      body: JSON.stringify({ url })
    })
    const json = await res.json().catch(() => null)
    return res.ok && json?.status === true && typeof json.url === 'string' && /^https?:\/\//.test(json.url) ? json.url : null
  } catch { return null }
}

export async function dismissNotice(id: string): Promise<void> {
  if (id.startsWith('notice:')) return stampNotices('dismiss', [id])
  const serverId = Number(id.replace('notification:', ''))
  if (!Number.isFinite(serverId)) return
  await action('notification_dismiss', { method: 'POST', body: { id: serverId } })
}

// ---------------------------------------------------------------- purchase & payment

export async function purchase(r: M.PurchaseRequest): Promise<M.PurchaseResult> {
  const body: Json = { country_id: r.countryId }
  if (r.serviceId != null) body.service_id = r.serviceId
  else body.custom_service = { traffic_gb: r.customTrafficGb ?? 0, time_days: r.customTimeDays ?? 0 }
  if (r.customUsername?.trim()) body.custom_username = r.customUsername
  if (r.note?.trim()) body.custom_note = r.note
  if (r.discountCode?.trim()) body.discount_code = r.discountCode
  const root = await action('purchase', { method: 'POST', body, allowPaymentRequired: true })
  const p = payloadObject(root)
  const po = root.requires_payment === true ? root : (p.requires_payment === true ? p : null)
  if (po) {
    return {
      completed: false, requiresPayment: true, username: str(po.username) || null,
      amountDue: Math.trunc(num(po.amount_due) ?? 0), balance: Math.trunc(num(po.balance) ?? 0), price: Math.trunc(num(po.price) ?? 0), service: null
    }
  }
  const so = (p.service && typeof p.service === 'object') ? p.service : (root.service && typeof root.service === 'object' ? root.service : null)
  return {
    completed: bool(root.status, true) && bool(p.success, true), requiresPayment: false,
    username: (so && str(so.username)) || str(p.username) || null,
    amountDue: 0, balance: Math.trunc(num(p.balance) ?? 0), price: 0,
    service: so ? serviceFrom(so, str(so.username)) : null
  }
}

export async function paymentOptions(): Promise<M.PaymentOptions> {
  const p = payloadObject(await action('payment_methods'))
  return {
    methods: arr(p.methods).filter(r => str(r.id)).map(r => ({
      id: str(r.id), label: visible(r.label ?? r.id), kind: str(r.kind, 'form'),
      directUrl: isHttps(str(r.url)) ? str(r.url) : null, minimum: int(r.min), maximum: int(r.max)
    })),
    balance: Math.trunc(num(p.balance) ?? 0),
    currency: visible(p.currency ?? 'تومان')
  }
}

export async function beginPayment(method: string, amount: number, purchaseUsername: string | null): Promise<M.PaymentInit> {
  const body: Json = { method, amount }
  if (purchaseUsername) body.purchase_username = purchaseUsername
  const p = payloadObject(await action('payment_init', { method: 'POST', body }))
  const url = str(p.url)
  return {
    kind: str(p.kind, 'manual'), orderId: str(p.order_id), url: isHttps(url) ? url : null,
    cardNumber: str(p.card_number) || null, cardHolder: str(p.name_card) || null,
    amount: Math.trunc(num(p.amount) ?? amount), amountRial: Math.trunc(num(p.amount_rial) ?? amount * 10),
    message: visible(p.message), method, methodLabel: '', expiresAt: int(p.expires_at)
  }
}

export async function pendingPayments(): Promise<M.PendingPayment[]> {
  const p = payloadObject(await action('pending_payments'))
  return arr(p.pending).map(o => ({
    orderId: str(o.order_id), method: str(o.method), label: visible(o.method_label),
    amount: int(o.amount), expiresAt: int(o.expires_at), status: str(o.status)
  }))
}

export async function cancelPayment(orderId: string): Promise<Json> {
  return payloadObject(await action('crypto_cancel_invoice', { method: 'POST', body: { order_id: orderId } }))
}

export async function transactions(page: number): Promise<Json> {
  return payloadObject(await action('transactions', { params: { page: String(page), limit: '20' } }))
}

export async function paymentStatus(orderId: string): Promise<Json> {
  return payloadObject(await action('payment_status', { params: { order_id: orderId } }))
}

export async function requestWalletFallback(orderId: string): Promise<boolean> {
  const p = payloadObject(await action('payment_fallback_wallet', { method: 'POST', body: { order_id: orderId } }))
  return bool(p.wallet_credited, bool(p.credited)) || (bool(p.success) && str(p.stage) === 'wallet_refunded')
}

export async function uploadReceipt(orderId: string, file: File): Promise<string> {
  if (file.size > MAX_RECEIPT_BYTES) throw new ApiError('حجم رسید نباید بیشتر از ۸ مگابایت باشد')
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) throw new ApiError('رسید باید تصویر JPEG، PNG یا WebP باشد')
  const form = new FormData()
  form.append('order_id', orderId)
  form.append('photo', file, file.name.replace(/[^A-Za-z0-9._-]/g, '_') || 'receipt.jpg')
  const ctrl = new AbortController()
  const timer = setTimeout(() => ctrl.abort(), UPLOAD_TIMEOUT)
  let res: Response
  try {
    res = await fetch(`${MINIAPP_API}?actions=payment_receipt`, {
      method: 'POST', body: form, signal: ctrl.signal, credentials: 'same-origin', referrerPolicy: 'no-referrer',
      headers: { Accept: 'application/json', Authorization: `Bearer ${requireToken()}`, [CLIENT_HEADER]: CLIENT_ID }
    })
  } catch {
    clearTimeout(timer)
    throw new ApiError('ارسال رسید انجام نشد؛ اتصال اینترنت را بررسی کن.', 0, true)
  }
  const raw = await res.text().finally(() => clearTimeout(timer))
  const env = readResponse(res.status, raw, { bearer: token() })
  return visible(payloadObject(env).message ?? 'رسید ارسال شد') || 'رسید ارسال شد'
}

export async function trialOptions(): Promise<M.TrialOptions> {
  const p = payloadObject(await action('test_account_info'))
  return {
    panels: arr(p.panels).filter(r => str(r.id)).map(r => ({ code: str(r.id), name: visible(r.name), remaining: r.limit_left == null ? null : num(r.limit_left) })),
    remaining: p.limit_left == null ? null : num(p.limit_left),
    canRequest: bool(p.available)
  }
}

export async function createTrial(panelCode: string, username: string | null): Promise<M.ServiceDetails> {
  const body: Json = { country_id: panelCode }
  if (username?.trim()) body.custom_username = username
  const p = payloadObject(await action('test_account_create', { method: 'POST', body }))
  const issued = p.service && typeof p.service === 'object' ? p.service : p
  return serviceFrom(issued, str(issued.username))
}

export async function support(name: 'tickets' | 'ticket_thread' | 'ticket_departments' | 'ticket_create' | 'ticket_reply' | 'ticket_close', body: Json | null = null, params: Record<string, string> = {}): Promise<Json> {
  return payloadObject(await action(name, { method: body == null ? 'GET' : 'POST', params, body }))
}

// ---------------------------------------------------------------- marketplace

function marketShopFrom(row: Json): M.MarketShop {
  const t = row.tick && typeof row.tick === 'object' ? row.tick : null
  return {
    id: int(row.id), name: visible(row.name ?? 'فروشگاه'), description: visible(row.description),
    telegramBot: str(row.telegram_bot), telegramChannel: str(row.telegram_channel), supportContact: str(row.support_contact),
    verified: bool(row.verified), status: str(row.status), canSell: bool(row.can_sell, true), closedReason: visible(row.closed_reason),
    stars: num(row.stars) ?? 0, reviewCount: int(row.reviews), satisfaction: int(row.satisfaction),
    minPrice: Math.trunc(num(row.min_price) ?? 0), maxPrice: Math.trunc(num(row.max_price) ?? 0),
    productCount: int(row.product_count), discountCount: int(row.discount_count), sales30d: int(row.sales_30d), reviews: [],
    tagline: visible(row.tagline), logoVersion: int(row.logo_version), gbPrice: Math.trunc(num(row.gb_price) ?? 0),
    dayPrice: Math.trunc(num(row.day_price) ?? 0), panelMinPrice: Math.trunc(num(row.panel_min_price) ?? 0),
    panelMaxPrice: Math.trunc(num(row.panel_max_price) ?? 0), panelCount: int(row.panel_count), testAvailable: bool(row.test_available),
    tick: t ? { earned: bool(t.earned), label: visible(t.label), met: int(t.met), total: int(t.total),
      criteria: arr(t.criteria).map(c => ({ label: visible(c.label), value: int(c.value), target: int(c.target), ok: bool(c.ok) })) } : null,
    myServices: int(row.my_services)
  }
}

function reviewsFrom(a: any): M.MarketReview[] {
  return arr(a).map(r => ({ stars: int(r.stars), body: visible(r.body), createdAt: Math.trunc(num(r.created_at) ?? 0) }))
}

function marketCatalogFrom(p: Json): M.MarketCatalog {
  return {
    panels: arr(p.panels).map(r => ({
      code: str(r.code), name: visible(r.name), country: visible(r.country), flag: visible(r.flag), custom: bool(r.custom),
      gbPrice: Math.trunc(num(r.gb_price) ?? 0), dayPrice: Math.trunc(num(r.day_price) ?? 0), gbPriceBefore: Math.trunc(num(r.gb_price_before) ?? 0),
      minGb: int(r.min_gb), maxGb: int(r.max_gb), minDays: int(r.min_days), maxDays: int(r.max_days),
      test: bool(r.test), testHours: int(r.test_hours), testMb: int(r.test_mb)
    })).filter(x => x.code),
    products: arr(p.products).map(r => ({
      code: str(r.code), name: visible(r.name), price: Math.trunc(num(r.price) ?? 0), volumeGb: int(r.volume_gb), timeDays: int(r.time_days),
      note: visible(r.note), location: visible(r.location), category: visible(r.category), priceBefore: Math.trunc(num(r.price_before) ?? 0)
    })).filter(x => x.code),
    methods: arr(p.payment).map(marketMethodFrom),
    categories: arr(p.categories).map(r => ({ id: str(r.id), name: visible(r.name) })).filter(c => c.name)
  }
}

function marketMethodFrom(r: Json): M.MarketMethod {
  return { id: str(r.id), label: visible(r.label), needs: str(r.needs), note: visible(r.note), cardNumber: str(r.card_number), cardHolder: visible(r.card_holder), contact: str(r.contact) }
}

function announcementsFrom(a: any): M.MarketAnnouncement[] {
  return arr(a).map(r => ({ id: int(r.id), title: visible(r.title), body: visible(r.body), discountCode: str(r.discount_code), publishedAt: Math.trunc(num(r.published_at) ?? 0) }))
}

export async function marketShops(): Promise<M.MarketFeed> {
  const p = payloadObject(await marketAction('shops', { allowAnonymous: true }))
  return { enabled: bool(p.enabled, true), registerFee: Math.trunc(num(p.terms_fee) ?? 0), shops: arr(p.shops).map(marketShopFrom), ghajarLogoVersion: int(p.ghajar_logo_version) }
}

export async function marketHome(shopId: number, code = ''): Promise<M.MarketHome> {
  const params: Record<string, string> = { shop_id: String(shopId) }
  if (code) params.code = code
  const p = payloadObject(await marketAction('shop_home', { params, allowAnonymous: true }))
  const applied = p.applied_code ?? null
  const catalog = p.catalog ?? {}
  const test = catalog.test ?? {}
  const me = p.me && typeof p.me === 'object' ? p.me : null
  return {
    shop: { ...marketShopFrom(p), reviews: reviewsFrom(p.reviews_list) },
    reachable: bool(catalog.reachable, true),
    catalog: marketCatalogFrom(catalog),
    testEnabled: bool(test.enabled), testUsed: bool(test.used),
    wallet: me ? Math.trunc(num(me.wallet) ?? 0) : null,
    unread: me ? int(me.unread) : 0, blocked: me ? bool(me.blocked) : false, mine: me ? bool(me.mine) : false,
    appliedCode: str(applied?.code), appliedOk: bool(applied?.ok), appliedMsg: visible(applied?.msg ?? ''),
    announcements: announcementsFrom(p.announcements),
    discountCodes: arr(p.discount_codes).map(r => ({ code: str(r.code), percent: num(r.percent) ?? 0, expiresAt: Math.trunc(num(r.expires_at) ?? 0), left: int(r.left, -1) }))
  }
}

export async function marketAnnouncements(shopId: number): Promise<M.MarketAnnouncement[]> {
  return announcementsFrom(payloadObject(await marketAction('shop_announcements', { params: { shop_id: String(shopId) }, allowAnonymous: true })).announcements)
}

export async function marketReport(shopId: number, reason: string, body: string): Promise<string> {
  const res = await marketAction('shop_report', { method: 'POST', params: { shop_id: String(shopId) }, body: { shop_id: shopId, reason, body } })
  return visible(res.msg) || 'گزارش ثبت شد.'
}

export async function marketMyShops(): Promise<M.MarketMyShop[]> {
  return arr(payloadObject(await marketAction('my_shops')).shops).map(r => ({ shopId: int(r.shop_id), name: visible(r.name), services: int(r.services), lastAt: Math.trunc(num(r.last_at) ?? 0) }))
}

export async function marketOrderStart(o: {
  shopId: number; productCode: string; panelCode: string; method: string; kind?: string; plan?: string
  volumeGb?: number; timeDays?: number; invoiceId?: string; username?: string; amount?: number; discountCode?: string
}): Promise<M.MarketOrder> {
  const env = await marketAction('order_start', {
    method: 'POST', body: {
      shop_id: o.shopId, kind: o.kind ?? 'purchase', product_code: o.productCode, panel_code: o.panelCode, method: o.method,
      plan: o.plan ?? '', volume_gb: o.volumeGb ?? 0, time_days: o.timeDays ?? 0, invoice_id: o.invoiceId ?? '',
      username: o.username ?? '', amount: o.amount ?? 0, discount_code: o.discountCode ?? ''
    }
  })
  const p = payloadObject(env)
  return {
    id: int(p.id), method: str(p.method), methodLabel: visible(p.method_label), needs: str(p.needs), amount: Math.trunc(num(p.amount) ?? 0),
    cardNumber: str(p.card_number), cardHolder: visible(p.card_holder), contact: str(p.contact), gatewayUrl: str(p.gateway_url),
    productName: visible(p.product_name), status: str(p.status), message: visible(env.msg)
  }
}

/** Shrinks a receipt to 1600px JPEG and returns base64, as encodeReceipt does. */
export async function encodeReceipt(file: File): Promise<string> {
  const bitmap = await createImageBitmap(file).catch(() => null)
  if (!bitmap) throw new ApiError('این فایل تصویر خوانده نشد.')
  const scale = Math.min(1, 1600 / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(bitmap.width * scale))
  canvas.height = Math.max(1, Math.round(bitmap.height * scale))
  canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  const dataUrl = canvas.toDataURL('image/jpeg', 0.82)
  const b64 = dataUrl.split(',')[1] ?? ''
  if (b64.length * 0.75 > MAX_RECEIPT_BYTES) throw new ApiError('حجم رسید نباید بیشتر از ۸ مگابایت باشد')
  return b64
}

export async function marketSubmitReceipt(paymentId: number, file: File, note: string): Promise<string> {
  const encoded = await encodeReceipt(file)
  const p = await marketAction('order_receipt', { method: 'POST', body: { payment_id: paymentId, receipt: encoded, note } })
  return visible(p.msg ?? 'رسید ارسال شد.') || 'رسید ارسال شد.'
}

export async function marketOrderStatus(paymentId: number): Promise<M.MarketOrderStatus> {
  const p = payloadObject(await marketAction('order_status', { params: { payment_id: String(paymentId) } }))
  return {
    id: int(p.id), shopId: int(p.shop_id), status: str(p.status), rejectReason: visible(p.reject_reason), amount: Math.trunc(num(p.amount) ?? 0),
    username: str(p.username), configs: (Array.isArray(p.configs) ? p.configs : []).map(String).filter((s: string) => s.trim()), subscription: str(p.subscription)
  }
}

export async function marketReview(shopId: number, stars: number, body: string): Promise<string> {
  return visible((await marketAction('review_submit', { method: 'POST', body: { shop_id: shopId, stars, body } })).msg)
}

export async function marketDiscountCheck(shopId: number, code: string, amount: number): Promise<[number, string]> {
  const env = await marketAction('discount_check', { params: { shop_id: String(shopId), code, amount: String(amount) }, allowAnonymous: true })
  return [Math.trunc(num(payloadObject(env).amount) ?? amount), visible(env.msg)]
}

export async function marketGiftRedeem(shopId: number, code: string): Promise<[boolean, string]> {
  const env = await marketAction('gift_redeem', { method: 'POST', body: { shop_id: shopId, code } })
  return [bool(env.status), visible(env.msg)]
}

export async function marketServices(shopId: number): Promise<M.MarketService[]> {
  return arr(payloadObject(await marketAction('my_services', { params: { shop_id: String(shopId) } })).services).map(r => {
    const u = r.usage ?? {}
    return {
      invoiceId: str(r.invoice_id), username: str(r.username), productName: visible(r.product_name), panelName: visible(r.panel_name),
      isTest: bool(r.is_test), boughtAt: Math.trunc(num(r.bought_at) ?? 0), reachable: bool(r.reachable), status: str(u.status),
      dataLimit: Math.trunc(num(u.data_limit) ?? 0), used: Math.trunc(num(u.used) ?? 0), expire: Math.trunc(num(u.expire) ?? 0),
      volumeGb: int(u.volume_gb), timeDays: int(u.time_days), subscription: str(r.subscription), configCount: int(r.config_count)
    }
  })
}

export async function marketServiceDelivery(shopId: number, invoiceId: string, username: string): Promise<M.MarketOrderStatus> {
  const p = payloadObject(await marketAction('service_detail', { params: { shop_id: String(shopId), invoice_id: invoiceId, username } }))
  return {
    id: 0, shopId, status: 'paid', rejectReason: '', amount: 0, username: str(p.username, username),
    configs: (Array.isArray(p.configs) ? p.configs : []).map(String).filter((s: string) => s.trim()), subscription: str(p.subscription)
  }
}

export async function marketMessages(shopId: number): Promise<M.MarketMessage[]> {
  return arr(payloadObject(await marketAction('messages', { params: { shop_id: String(shopId) } })).messages).map(r => ({
    id: int(r.id), kind: str(r.kind), title: visible(r.title), body: visible(r.body), createdAt: Math.trunc(num(r.created_at) ?? 0), read: bool(r.read)
  }))
}

export async function marketMessagesRead(shopId: number): Promise<void> {
  await marketAction('messages_read', { method: 'POST', body: { shop_id: shopId } })
}

export async function marketWallet(shopId: number): Promise<M.MarketWallet> {
  const p = payloadObject(await marketAction('wallet', { params: { shop_id: String(shopId) } }))
  return {
    balance: Math.trunc(num(p.balance) ?? 0),
    history: arr(p.history).map(r => ({ amount: Math.trunc(num(r.amount) ?? 0), kind: str(r.kind), title: visible(r.title), balanceAfter: Math.trunc(num(r.balance_after) ?? 0), createdAt: Math.trunc(num(r.created_at) ?? 0) }))
  }
}

export async function marketTransactions(shopId: number): Promise<M.MarketTransaction[]> {
  return arr(payloadObject(await marketAction('transactions', { params: { shop_id: String(shopId) } })).transactions).map(r => ({
    id: int(r.id), kind: str(r.kind), status: str(r.status), amount: Math.trunc(num(r.amount) ?? 0), method: str(r.method),
    productName: visible(r.product_name), rejectReason: visible(r.reject_reason), createdAt: Math.trunc(num(r.created_at) ?? 0)
  }))
}

export async function marketTickets(shopId: number): Promise<M.MarketTicket[]> {
  return arr(payloadObject(await marketAction('tickets', { params: { shop_id: String(shopId) } })).tickets).map(r => ({
    id: int(r.id), subject: visible(r.subject), status: str(r.status), updatedAt: Math.trunc(num(r.updated_at) ?? 0)
  }))
}

function threadFrom(p: Json): M.MarketTicketThread {
  return { id: int(p.id), subject: visible(p.subject), status: str(p.status), messages: arr(p.messages).map(r => ({ fromSeller: str(r.sender) === 'seller', body: visible(r.body), createdAt: Math.trunc(num(r.created_at) ?? 0) })) }
}

export async function marketTicketThread(ticketId: number) { return threadFrom(payloadObject(await marketAction('ticket_thread', { params: { ticket_id: String(ticketId) } }))) }
export async function marketTicketReply(ticketId: number, body: string) { return threadFrom(payloadObject(await marketAction('ticket_reply', { method: 'POST', body: { ticket_id: ticketId, body } }))) }
export async function marketTicketClose(ticketId: number) { return threadFrom(payloadObject(await marketAction('ticket_close', { method: 'POST', body: { ticket_id: ticketId } }))) }
export async function marketTicketCreate(shopId: number, subject: string, body: string): Promise<number> {
  return int(payloadObject(await marketAction('ticket_create', { method: 'POST', body: { shop_id: shopId, subject, body } })).id)
}

/** A shop's logo, by shop and version; the URL itself is the cache key. */
export function marketLogoUrl(shopId: number, version: number): string | null {
  if (shopId < 0 || version <= 0) return null
  return `${MARKET_API}?actions=shop_logo&shop_id=${shopId}&v=${version}`
}

/** The public error text for a failure (GhajarCommerceRules.publicMessage). */
export function publicMessage(e: unknown): string {
  const msg = e instanceof Error ? e.message : String(e ?? '')
  if (/[؀-ۿ]/.test(msg)) return visible(msg).slice(0, 500)
  return 'عملیات کامل نشد؛ اتصال اینترنت و وضعیت سفارش را بررسی کن.'
}

export interface MarketCode { source: string; id: number; code: string; value: number; maxUses: number; used: number; expiresAt: number; active: boolean; perUser: number; firstOnly: boolean; product: string; panel: string }

/** The owner's codes at their shop: shop_codes, code_add, code_toggle or code_delete. */
export async function marketCodes(shopId: number, act: 'shop_codes' | 'code_add' | 'code_toggle' | 'code_delete' = 'shop_codes', fields: Record<string, string> = {}): Promise<[{ discounts: MarketCode[]; gifts: MarketCode[] }, string]> {
  const env = await marketAction(act, { method: 'POST', body: { shop_id: shopId, ...fields } })
  const p = env.obj && typeof env.obj === 'object' ? env.obj : {}
  const list = (key: string, gift: boolean): MarketCode[] => arr(p[key]).map(r => ({
    source: str(r.source), id: int(r.id), code: str(r.code), value: num(gift ? r.amount : r.percent) ?? 0,
    maxUses: int(r.max_uses), used: int(r.used), expiresAt: Math.trunc(num(r.expires_at) ?? 0), active: bool(r.active, true),
    perUser: int(r.per_user), firstOnly: bool(r.first_only), product: str(r.product), panel: str(r.panel)
  }))
  const msg = visible(env.msg)
  if (env.status === false && msg) throw new ApiError(msg)
  return [{ discounts: list('discounts', false), gifts: list('gifts', true) }, msg]
}

export interface MarketTerms { terms: string; fee: number; commissionPercent: number; cycleDays: number; penaltyPerDay: number; graceDays: number; myShops: { id: number; name: string; status: string; lastError: string }[] }

export async function marketTerms(): Promise<MarketTerms> {
  const p = payloadObject(await marketAction('register_terms', { allowAnonymous: true }))
  return {
    terms: visible(p.terms), fee: Math.trunc(num(p.fee) ?? 0), commissionPercent: num(p.commission_percent) ?? 0,
    cycleDays: int(p.cycle_days, 30), penaltyPerDay: Math.trunc(num(p.penalty_day) ?? 0), graceDays: int(p.grace_days, 15),
    myShops: arr(p.my_shops).map(r => ({ id: int(r.id), name: visible(r.name), status: str(r.status), lastError: visible(r.last_error) }))
  }
}
