// A stand-in for the Ghajar store API, shaped exactly like the responses
// GhajarStoreApi.kt parses, serving the built PWA beside it at the same paths
// the real deployment uses (…/Ghajarvpn/pwa/ and …/Ghajarvpn/api/).
import http from 'node:http'
import { readFile, stat } from 'node:fs/promises'
import { extname, join, normalize } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../../backend/Faoxima-1.0.0/pwa/', import.meta.url))
const PORT = Number(process.env.PORT || 4173)
const PREFIX = '/Faoxima/Ghajarvpn'
const TOKEN = 'a'.repeat(40)
const now = () => Math.floor(Date.now() / 1000)

const state = { polls: 0, linked: false, paid: false, receipts: 0, dismissed: new Set(), shown: [] }

const services = [
  { username: 'ghajar_7731', name_product: 'آلمان ویژه ۵۰ گیگ', status: 'active', Service_location: 'آلمان ویژه', id_invoice: '9001',
    user_info: JSON.stringify({ data_limit: 50 * 1073741824, used_traffic: 41 * 1073741824, expire: now() + 2 * 86400 }), Volume: 50, Service_time: 30, time_sell: now() - 28 * 86400 },
  { username: 'ghajar_8120', name_product: 'مولتی لوکیشن ۱۰۰ گیگ', status: 'active', Service_location: 'مولتی لوکیشن', id_invoice: '9002',
    user_info: JSON.stringify({ data_limit: 100 * 1073741824, used_traffic: 12 * 1073741824, expire: now() + 25 * 86400 }), Volume: 100, Service_time: 30, time_sell: now() - 5 * 86400 },
  { username: 'ghajar_5003', name_product: 'تست رایگان', status: 'expired', Service_location: 'تست', id_invoice: '9003',
    user_info: JSON.stringify({ data_limit: 1073741824, used_traffic: 1073741824, expire: now() - 86400 }), Volume: 1, Service_time: 1, time_sell: now() - 40 * 86400 }
]

const serviceDetail = u => ({
  username: u, product_name: services.find(s => s.username === u)?.name_product ?? 'سرویس قاجار', status: 'active',
  used_traffic_gb: 41, total_traffic_gb: 50, remaining_traffic_gb: 9, expiration_time: '1405/07/20',
  subscription_url: `https://sub.example.com/sub/${u}/AbCdEf123456`,
  service_output: [{ value: [`vless://11111111-2222-3333-4444-555555555555@de1.example.com:443?security=reality&type=tcp#Ghajar-DE`, `trojan://secret@nl.example.com:443#Ghajar-NL`] }]
})

const notices = () => [
  { id: 41, kind: 'service_volume', title: '', body: 'حجم سرویس ghajar_7731 رو به پایان است؛ ۹ گیگ باقی مانده.', action: 'renew', action_ref: 'ghajar_7731', should_float: true, repeat_after: 10800, seen: false },
  { id: 42, kind: 'broadcast', title: '🎁 کد تخفیف نوروزی', body: 'با کد NOROOZ روی همه پلن‌ها ۲۰٪ تخفیف بگیر.', action: 'market_shop', action_ref: '0|NOROOZ', should_float: true, repeat_after: 0, seen: false },
  { id: 43, kind: 'payment', title: 'پرداخت تأیید شد', body: 'شارژ کیف پول به مبلغ ۲۰۰٬۰۰۰ تومان تأیید شد.', action: 'none', action_ref: '', should_float: false, repeat_after: 0, seen: true }
].filter(n => !state.dismissed.has(n.id))

const shops = [
  { id: 3, name: 'فروشگاه آسمان', tagline: 'سرورهای پرسرعت اروپا', verified: true, stars: 4.6, reviews: 128, satisfaction: 91, min_price: 45000, max_price: 390000,
    product_count: 12, discount_count: 2, sales_30d: 300, logo_version: 0, gb_price: 2500, panel_min_price: 45000, panel_max_price: 390000, panel_count: 3,
    test_available: true, can_sell: true, tick: { earned: true, label: 'قابل اعتماد از نظر خریداران', met: 4, total: 4, criteria: [{ label: 'فروش ۳۰ روز', value: 300, target: 50, ok: true }] }, my_services: 1 },
  { id: 5, name: 'تک‌سرور', tagline: '', verified: false, stars: 0, reviews: 0, satisfaction: 0, min_price: 0, max_price: 0, product_count: 0, can_sell: false, closed_reason: 'فروشگاه در حال بروزرسانی است.' }
]

function json(res, code, body) {
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' })
  res.end(JSON.stringify(body))
}

async function body(req) {
  const chunks = []
  for await (const c of req) chunks.push(c)
  const raw = Buffer.concat(chunks).toString('utf8')
  try { return JSON.parse(raw) } catch { return {} }
}

function authed(req) { return (req.headers.authorization || '') === `Bearer ${TOKEN}` }

async function api(req, res, file, q) {
  if (file === 'weblink.php') {
    const action = q.get('action')
    if (action === 'generate') return json(res, 200, { status: true, code: 'K7Q2MX', session_token: 'f'.repeat(64), expires_in: 300, bot_username: 'Ghajar_vpnbot' })
    if (action === 'status') {
      state.polls++
      if (state.polls < 2 && !process.env.INSTANT_LINK) return json(res, 200, { status: true, link_status: 'pending' })
      state.linked = true
      return json(res, 200, { status: true, link_status: 'linked', token: TOKEN })
    }
    if (action === 'redeem') return json(res, 200, { status: true, token: TOKEN })
    return json(res, 400, { status: false, msg: 'Unknown action' })
  }
  if (file === 'push.php') {
    const action = q.get('action')
    if (action === 'key') return json(res, 200, { status: true, key: 'BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U' })
    if (!authed(req)) return json(res, 401, { status: false, msg: 'Unauthorized' })
    await body(req)
    return json(res, 200, { status: true })
  }
  if (file === 'notices.php') {
    if (!authed(req)) return json(res, 401, { status: false, msg: 'Unauthorized' })
    const action = q.get('action')
    if (action === 'feed') return json(res, 200, { status: true, notices: notices(), unseen: notices().filter(n => !n.seen).length, shop: { enabled: true, message: '' }, gate: '', gate_message: '' })
    const b = await body(req)
    if (action === 'dismiss') (b.ids || []).forEach(id => state.dismissed.add(id))
    if (action === 'shown') state.shown.push(...(b.ids || []))
    return json(res, 200, { status: true })
  }
  if (file === 'market.php') {
    const a = q.get('actions')
    if (a === 'shops') return json(res, 200, { status: true, obj: { enabled: true, terms_fee: 500000, ghajar_logo_version: 0, shops } })
    if (a === 'shop_announcements') return json(res, 200, { status: true, obj: { announcements: [{ id: 1, title: 'تخفیف ویژه', body: 'تا پایان هفته ۲۰٪ تخفیف روی همه پلن‌ها.', discount_code: 'NOROOZ', published_at: now() - 3600 }] } })
    if (a === 'shop_home') {
      const s = shops.find(x => String(x.id) === q.get('shop_id')) || shops[0]
      return json(res, 200, { status: true, obj: { ...s, description: 'فروشگاه نمونه برای آزمون.', telegram_bot: 'aseman_bot', support_contact: 'aseman_support', reviews_list: [{ stars: 5, body: 'عالی و سریع', created_at: now() - 86400 }],
        catalog: { reachable: true, test: { enabled: true, used: false }, panels: [{ code: 'de', name: 'آلمان', flag: '🇩🇪', custom: true, gb_price: 2500, day_price: 300, min_gb: 5, max_gb: 500, min_days: 1, max_days: 90, test: true, test_hours: 24, test_mb: 1024 }],
          products: [{ code: 'p30', name: 'یک ماهه ۵۰ گیگ', price: 120000, volume_gb: 50, time_days: 30, note: '', location: '/all', category: '' }, { code: 'p90', name: 'سه ماهه ۲۰۰ گیگ', price: 390000, volume_gb: 200, time_days: 90, note: '', location: '/all', category: '' }],
          payment: [{ id: 'card', label: 'کارت به کارت', needs: 'card', note: 'رسید برای فروشنده', card_number: '6037991234567890', card_holder: 'آسمان' }], categories: [] },
        me: { wallet: 50000, unread: 1, blocked: false, mine: false }, announcements: [], discount_codes: [{ code: 'ASEMAN10', percent: 10, expires_at: now() + 5 * 86400, left: 20 }] } })
    }
    if (a === 'my_services') return json(res, 200, { status: true, obj: { services: [] } })
    if (a === 'messages') return json(res, 200, { status: true, obj: { messages: [] } })
    return json(res, 200, { status: true, obj: {} })
  }
  if (file === 'miniapp.php') {
    const a = q.get('actions')
    if (!authed(req)) return json(res, 401, { status: false, msg: 'Unauthorized' })
    switch (a) {
      case 'countries': return json(res, 200, { status: true, obj: [{ id: '1', name: 'آلمان ویژه', is_custom: true, is_username: true, is_note: true }, { id: '2', name: 'مولتی لوکیشن' }, { id: '3', name: 'ایرانسل اقتصادی' }] })
      case 'categories': return json(res, 200, { status: true, obj: [{ id: '10', name: 'گیمینگ' }, { id: '11', name: 'اقتصادی' }] })
      case 'time_ranges': return json(res, 200, { status: true, obj: [{ day: 30, name: 'یک ماهه' }, { day: 90, name: 'سه ماهه' }] })
      case 'services': return json(res, 200, { status: true, obj: [
        { id: '101', name: 'یک ماهه ۵۰ گیگ', price: 150000, traffic_gb: 50, time_days: 30, description: 'مناسب استفادهٔ روزمره.\nسرعت بالا', country_id: q.get('country_id') },
        { id: '102', name: 'یک ماهه ۱۰۰ گیگ', price: 250000, traffic_gb: 100, time_days: 31, description: '', country_id: q.get('country_id') },
        { id: '103', name: 'سه ماهه ۳۰۰ گیگ', price: 600000, traffic_gb: 300, time_days: 90, description: '', country_id: q.get('country_id') }] })
      case 'custom_price': return json(res, 200, { status: true, obj: { price: Number(q.get('traffic_gb')) * 3000 + Number(q.get('time_days')) * 500, traffic_min: 5, traffic_max: 500, time_min: 1, time_max: 90 } })
      case 'invoices': return json(res, 200, { status: true, obj: { items: services, total_pages: 1 } })
      case 'service': return json(res, 200, { status: true, obj: serviceDetail(q.get('username')) })
      case 'service_renew_options': return json(res, 200, { status: true, obj: { username: q.get('username'), panel: { name: 'آلمان ویژه' }, current_plan: { code: 'r1' }, show_price: true, discount: 0, balance: 200000,
        products: [{ code: 'r1', name: 'تمدید یک ماهه ۵۰ گیگ', volume_gb: 50, time_days: 30, price: 150000 }, { code: 'r2', name: 'تمدید سه ماهه', volume_gb: 150, time_days: 90, price: 400000 }],
        custom: { enabled: true, force: false, price_per_gb: 3000, price_per_day: 500, min_volume_gb: 5, max_volume_gb: 500, min_time_days: 1, max_time_days: 90 } } })
      case 'service_renew_confirm': { await body(req); return json(res, 200, { status: true, obj: { success: true, balance: 50000 } }) }
      case 'purchase': { const b = await body(req); return json(res, 200, b.discount_code === 'FREE' ? { status: true, obj: { success: true, service: serviceDetail('ghajar_9999') } } : { status: true, obj: { requires_payment: true, amount_due: 150000, balance: 0, price: 150000, username: 'ghajar_9999' } }) }
      case 'payment_methods': return json(res, 200, { status: true, obj: { balance: 200000, currency: 'تومان', methods: [{ id: 'cart', label: 'کارت به کارت', kind: 'carttocart', min: 10000, max: 0 }, { id: 'zarin', label: 'درگاه بانکی', kind: 'url', min: 10000, max: 50000000 }] } })
      case 'payment_init': { const b = await body(req); return json(res, 200, { status: true, obj: b.method === 'cart'
        ? { kind: 'carttocart', order_id: 'GH-20931', card_number: '6037991234567890', name_card: 'قاجار', amount: 150013, amount_rial: 1500130, message: 'مبلغ دقیق را واریز کن', expires_at: now() + 1800 }
        : { kind: 'url', order_id: 'GH-20932', url: 'https://sandbox.zarinpal.com/pg/StartPay/TEST', amount: 150000, expires_at: now() + 900 } }) }
      case 'pending_payments': return json(res, 200, { status: true, obj: { pending: [] } })
      case 'payment_status': return json(res, 200, { status: true, obj: state.paid ? { payment_status: 'paid', is_service_ready: true, service: serviceDetail('ghajar_9999') } : { payment_status: 'pending', expires_at: now() + 1700 } })
      case 'payment_receipt': state.receipts++; return json(res, 200, { status: true, obj: { message: 'رسید ارسال شد و در صف بررسی است.' } })
      case 'transactions': return json(res, 200, { status: true, obj: { total_pages: 1, items: [{ direction: 'credit', amount: 200000, category_label: 'شارژ کیف پول', balance_after: 200000, created_at: '1405/07/10 12:30', order_id: 'GH-1' }] } })
      case 'test_account_info': return json(res, 200, { status: true, obj: { available: true, limit_left: 1, panels: [{ id: '1', name: 'آلمان ویژه', limit_left: 1 }] } })
      case 'test_account_create': return json(res, 200, { status: true, obj: { service: serviceDetail('ghajar_test1') } })
      case 'tickets': return json(res, 200, { status: true, obj: { items: [], total_pages: 1 } })
      case 'ticket_departments': return json(res, 200, { status: true, obj: { items: [{ id: 1, name: 'فنی' }, { id: 2, name: 'مالی' }] } })
      default: return json(res, 200, { status: true, obj: {} })
    }
  }
  return json(res, 404, { status: false, msg: 'not found' })
}

const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.webmanifest': 'application/manifest+json', '.png': 'image/png', '.webp': 'image/webp', '.woff2': 'font/woff2', '.json': 'application/json', '.svg': 'image/svg+xml' }

http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`)
  if (url.pathname === '/__state') return json(res, 200, { ...state, dismissed: [...state.dismissed] })
  if (url.pathname === '/__pay') { state.paid = true; return json(res, 200, { ok: true }) }
  if (url.pathname === PREFIX + '/pwa/push.php') return api(req, res, 'push.php', url.searchParams)
  if (url.pathname === PREFIX + '/pwa/sub.php') { const b = await body(req); return json(res, 200, { status: true, url: b.url }) }
  if (url.pathname.startsWith(PREFIX + '/api/')) return api(req, res, url.pathname.slice((PREFIX + '/api/').length), url.searchParams)
  if (url.pathname === '/' || url.pathname === PREFIX || url.pathname === PREFIX + '/') { res.writeHead(302, { Location: PREFIX + '/pwa/' }); return res.end() }
  if (!url.pathname.startsWith(PREFIX + '/pwa/')) { res.writeHead(404); return res.end() }
  let rel = normalize(decodeURIComponent(url.pathname.slice((PREFIX + '/pwa/').length))).replace(/^(\.\.[/\\])+/, '')
  if (!rel || rel === '.' || rel.endsWith('/')) rel = (rel === '.' ? '' : rel) + 'index.html'
  const path = join(root, rel)
  try {
    const st = await stat(path)
    if (!st.isFile()) throw new Error('dir')
    res.writeHead(200, { 'Content-Type': TYPES[extname(path)] || 'application/octet-stream', 'Service-Worker-Allowed': PREFIX + '/pwa/' })
    res.end(await readFile(path))
  } catch {
    res.writeHead(404); res.end('not found')
  }
}).listen(PORT, () => console.log(`mock on http://localhost:${PORT}${PREFIX}/pwa/`))
