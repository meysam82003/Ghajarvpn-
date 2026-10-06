import * as api from '../api/client'
import { ApiError, publicMessage } from '../api/client'
import * as M from '../api/models'
import { accountKey } from '../api/account'
import { OrderFlow, OrderStage, paymentOutcome } from '../api/rules'
import { createStore, safeStorage } from '../lib/store'
import { isTrustedPaymentUrl } from '../api/config'

/**
 * GhajarCheckoutViewModel, for the web.
 *
 * The same order life cycle, the same guards and the same messages. The one
 * difference is delivery: there is no tunnel in a browser, so "delivered"
 * means the service's subscription link and configs are in hand and the
 * add-to-app sheet is open - the user adds them to the VPN app of their choice.
 */

export interface Delivery { service: M.ServiceDetails; imported: number; synced: boolean }

export interface CheckoutState {
  revision: number
  busy: boolean
  error: string | null
  message: string | null
  purchase: M.PurchaseResult | null
  methods: M.PaymentOptions | null
  payment: M.PaymentInit | null
  receipt: File | null
  receiptSent: boolean
  walletTopUp: boolean
  delivery: Delivery | null
  openUrl: string | null
  checkoutVisible: boolean
  pendingPayments: M.PendingPayment[]
  stage: OrderStage | null
}

const initial: CheckoutState = {
  revision: 0, busy: false, error: null, message: null, purchase: null, methods: null, payment: null, receipt: null,
  receiptSent: false, walletTopUp: false, delivery: null, openUrl: null, checkoutVisible: false, pendingPayments: [], stage: null
}

export const checkout = createStore<CheckoutState>({ ...initial })
const set = (patch: Partial<CheckoutState>) => checkout.set({ ...checkout.get(), ...patch })
const get = () => checkout.get()

export function deliveryFailed(): boolean { return get().stage === 'PROVISION_FAILED' }

let owner = ''
let paidWaitingChecks = 0
let fallbackAttemptedOrderId: string | null = null
let locked = false
const INVOICE_KEY = 'ghajar.checkout.v1'

export function resetCheckout(): void {
  checkout.set({ ...initial })
  paidWaitingChecks = 0; fallbackAttemptedOrderId = null; owner = ''
  safeStorage.remove(INVOICE_KEY)
}

async function runOperation(block: () => Promise<void>, silent = false): Promise<void> {
  if (get().busy || (silent && locked)) return
  if (!silent) set({ busy: true, error: null })
  locked = true
  try {
    const current = (await accountKey()) ?? ''
    if (!current) throw new ApiError('ابتدا حساب را به ربات متصل کن.')
    if (owner && owner !== current) {
      set({ purchase: null, payment: null, methods: null, receipt: null, delivery: null, openUrl: null, receiptSent: false,
        walletTopUp: false, stage: null, pendingPayments: [], checkoutVisible: false })
      owner = current
      safeStorage.remove(INVOICE_KEY)
      throw new ApiError('حساب تغییر کرده است؛ سفارش مربوط به حساب قبلی بود.')
    }
    owner = current
    await block()
  } catch (e) {
    if (!silent) set({ error: publicMessage(e) })
  } finally {
    locked = false
    if (!silent) set({ busy: false })
  }
}

export function buy(request: M.PurchaseRequest) {
  return runOperation(async () => {
    if (get().payment) throw new ApiError('ابتدا وضعیت فاکتور فعلی را بررسی کن؛ پرداخت دوباره لازم نیست.')
    set({ walletTopUp: false, checkoutVisible: true })
    const result = await api.purchase(request)
    if (result.requiresPayment) {
      set({ purchase: result, payment: null, receiptSent: false })
      persist()
      set({ methods: await api.paymentOptions(), message: 'برای همین سفارش، روش پرداخت را انتخاب کن.' })
    } else {
      if (!result.completed) throw new ApiError('خرید تأیید نشد؛ وضعیت سرویس را بررسی کن.')
      set({ stage: OrderFlow.initialStage(true, false) })
      const service = result.service ?? (result.username ? await api.service(result.username) : null)
      if (!service) throw new ApiError('سفارش ثبت شد؛ خروجی سرویس هنوز آماده نیست.')
      await deliver(service, true)
    }
  })
}

export function topUp(amount: number) {
  return runOperation(async () => {
    if (!(amount > 0)) throw new ApiError('مبلغ شارژ را به تومان وارد کن.')
    if (get().payment) throw new ApiError('ابتدا وضعیت فاکتور فعلی را بررسی کن.')
    const options = await api.paymentOptions()
    set({
      methods: options, walletTopUp: true, checkoutVisible: true,
      purchase: { completed: false, requiresPayment: true, username: null, amountDue: amount, balance: options.balance, price: amount, service: null }
    })
    persist()
    set({ message: 'روش پرداخت شارژ کیف پول را انتخاب کن.' })
  })
}

export function refreshMethods() { return runOperation(async () => { set({ methods: await api.paymentOptions() }) }) }

export function beginPayment(method: M.PaymentMethod) {
  return runOperation(async () => {
    const target = get().purchase
    if (!target) return
    if (get().payment) { set({ message: 'فاکتور فعلی را تکمیل یا وضعیتش را بررسی کن؛ پرداخت دوباره لازم نیست.' }); return }
    if (!(target.amountDue >= method.minimum && (method.maximum <= 0 || target.amountDue <= method.maximum))) {
      throw new ApiError('مبلغ سفارش خارج از محدودهٔ این روش است.')
    }
    const result = await api.beginPayment(method.id, target.amountDue, target.username)
    if (!result.orderId) throw new ApiError('شناسهٔ فاکتور از سرور دریافت نشد.')
    set({ payment: { ...result, methodLabel: method.label }, checkoutVisible: true, receipt: null, receiptSent: false })
    paidWaitingChecks = 0; fallbackAttemptedOrderId = null
    persist()
    set({ message: result.message || 'فاکتور آماده است.', openUrl: result.url })
    try { set({ pendingPayments: await api.pendingPayments() }) } catch { /* list refreshes later */ }
  })
}

export function setReceipt(file: File | null) { set({ receipt: file, receiptSent: file ? false : get().receiptSent }) }

export function uploadReceipt() {
  return runOperation(async () => {
    if (get().receiptSent) return
    const invoice = get().payment, photo = get().receipt
    if (!invoice || !photo) return
    const msg = await api.uploadReceipt(invoice.orderId, photo)
    set({ message: msg, receiptSent: true })
    persist()
  })
}

export function trial(code: string, username: string | null) {
  return runOperation(async () => { await deliver(await api.createTrial(code, username)) })
}

export function importOwned(username: string) {
  return runOperation(async () => { await deliver(await api.service(username)) })
}

/** All the ways this service can be added to a VPN app: its subscription link, then each config. */
export function payloadsOf(service: M.ServiceDetails): string[] {
  return [...new Set([service.subscriptionUrl, ...service.outputs].filter((s): s is string => !!s && s.trim() !== ''))]
}

async function deliver(service: M.ServiceDetails, finishCheckout = false): Promise<void> {
  const st = get().stage
  const paidStage = st ? OrderFlow.onDeliveryAttempt(st) : null
  if (paidStage) set({ stage: paidStage })
  set({ delivery: { service, imported: 0, synced: false } })
  const count = payloadsOf(service).length
  if (count === 0) {
    if (paidStage) { set({ stage: OrderFlow.onDeliveryFailed(paidStage) }); persist() }
    throw new ApiError('سرویس صادر شد، اما ساب هنوز کانفیگ ندارد؛ از «سرویس‌های من» دوباره دریافت کن.')
  }
  if (paidStage) set({ stage: OrderFlow.onDelivered(paidStage, count) })
  set({
    delivery: { service, imported: count, synced: true },
    message: 'سرویس آماده است؛ با «افزودن به اپ» آن را به برنامهٔ VPN خودت اضافه کن.',
    revision: get().revision + 1
  })
  if (finishCheckout) {
    set({ purchase: null, payment: null, receipt: null, receiptSent: false, walletTopUp: false, stage: null })
    safeStorage.remove(INVOICE_KEY)
  }
}

/** Only authenticated server state confirms a payment, never a redirect. */
export function checkPayment() {
  return runOperation(async () => {
    const invoice = get().payment
    if (!invoice) return
    const status = await api.paymentStatus(invoice.orderId)
    const value = String(status.payment_status ?? '')
    set({ payment: { ...invoice, expiresAt: Number(status.expires_at ?? invoice.expiresAt) || invoice.expiresAt } })
    const service = status.service && typeof status.service === 'object' ? status.service : null
    const serviceReady = status.is_service_ready === true || status.is_service_ready === 1
    const walletTopUp = get().walletTopUp
    const outcome = paymentOutcome(value, walletTopUp, status.wallet_credited_only === true, serviceReady, service != null)
    switch (outcome) {
      case 'WALLET_CREDITED': {
        set({ stage: null })
        set({ methods: await api.paymentOptions().catch(() => get().methods) })
        set({
          message: walletTopUp ? 'شارژ کیف پول تأیید شد و موجودی بروزرسانی شد.' : 'پرداخت تأیید شد و مبلغ به کیف پول برگشت؛ خرید را از محصولات ادامه بده.',
          purchase: null, payment: null, receipt: null, receiptSent: false, walletTopUp: false
        })
        safeStorage.remove(INVOICE_KEY)
        break
      }
      case 'SERVICE_READY': {
        if (!get().stage) set({ stage: OrderFlow.initialStage(true, walletTopUp) })
        await deliver(api.serviceFrom(service!, get().purchase?.username ?? ''), true)
        break
      }
      case 'PAID_WAITING': {
        if (!get().stage) set({ stage: OrderFlow.initialStage(true, walletTopUp) ?? 'PAYMENT_CONFIRMED' })
        paidWaitingChecks++
        persist()
        const stage = get().stage!
        const mayFallback = OrderFlow.refundEligible(value, walletTopUp, serviceReady, service != null) &&
          OrderFlow.walletFallbackAllowed(stage) && fallbackAttemptedOrderId !== invoice.orderId &&
          (paidWaitingChecks >= 2 || stage === 'PROVISION_FAILED')
        if (mayFallback) {
          fallbackAttemptedOrderId = invoice.orderId
          const credited = await api.requestWalletFallback(invoice.orderId).catch(() => false)
          if (credited) {
            set({ methods: await api.paymentOptions().catch(() => get().methods) })
            set({
              message: 'پرداخت تأیید شد ولی سرویس تحویل نشد؛ مبلغ به‌صورت خودکار به کیف پول برگشت.',
              purchase: null, payment: null, receipt: null, receiptSent: false, walletTopUp: false, stage: null
            })
            safeStorage.remove(INVOICE_KEY)
          } else {
            set({ stage: OrderFlow.onDeliveryFailed(stage), message: 'پرداخت تأیید شد؛ سرویس در حال آماده‌سازی است. دوباره پرداخت نکن.' })
            persist()
          }
        } else set({ message: 'پرداخت تأیید شد؛ سرویس در حال آماده‌سازی است. دوباره پرداخت نکن.' })
        break
      }
      case 'NOT_APPROVED': {
        const reason = String(status.reason ?? '')
        set({ message: reason && reason !== 'null' ? reason : 'این فاکتور تأیید نشده یا منقضی است.' })
        break
      }
      case 'PENDING':
        set({ message: get().receiptSent ? 'رسید ارسال شده و در انتظار بررسی ادمین است.' : 'فاکتور در انتظار پرداخت یا تأیید سرور است؛ پس از پرداخت، بررسی وضعیت را بزن.' })
    }
  }, true)
}

export function refreshPending() {
  return runOperation(async () => { set({ pendingPayments: await api.pendingPayments() }) }, true)
}

export function resumePayment(item: M.PendingPayment) {
  return runOperation(async () => {
    const status = await api.paymentStatus(item.orderId)
    const previous = get().payment?.orderId === item.orderId ? get().payment : null
    const walletTopUp = status.flow === 'recharge'
    let purchase = previous ? get().purchase : null
    const username = [status.service?.username, status.username, status.service_username]
      .map(v => (v == null ? '' : String(v))).find(v => v && v !== 'null') ?? null
    if (!purchase) purchase = { completed: false, requiresPayment: true, username, amountDue: item.amount, balance: 0, price: item.amount, service: null }
    else if (!purchase.username && username) purchase = { ...purchase, username }
    const gateway = String(status.gateway_url ?? '')
    set({
      walletTopUp, purchase,
      payment: previous ? { ...previous, expiresAt: Number(status.expires_at ?? item.expiresAt) || item.expiresAt }
        : { kind: 'url', orderId: item.orderId, url: gateway.startsWith('https://') ? gateway : null, cardNumber: null, cardHolder: null,
          amount: item.amount, amountRial: item.amount * 10, message: '', method: item.method, methodLabel: item.label, expiresAt: item.expiresAt },
      checkoutVisible: true
    })
    persist()
    set({ message: 'همان فاکتور برای پیگیری باز شد؛ پرداخت تازه‌ای ساخته نشد.' })
  })
}

export function cancelPayment(orderId: string) {
  return runOperation(async () => {
    await api.cancelPayment(orderId)
    const status = String((await api.paymentStatus(orderId)).payment_status ?? '').toLowerCase()
    if (['cancelled', 'canceled', 'reject'].includes(status)) {
      if (get().payment?.orderId === orderId) clearInvoice()
      set({ pendingPayments: await api.pendingPayments(), message: 'فاکتور لغو شد.' })
    } else set({ message: 'لغو تأیید نشد؛ وضعیت پرداخت را دوباره بررسی کن.' })
  })
}

function clearInvoice(): void {
  set({ purchase: null, payment: null, methods: null, receipt: null, receiptSent: false, walletTopUp: false, stage: null, checkoutVisible: false })
  paidWaitingChecks = 0; fallbackAttemptedOrderId = null
  safeStorage.remove(INVOICE_KEY)
}

export function leaveInvoice(): void {
  if (get().busy) return
  if (!get().payment) clearInvoice()
  else set({ checkoutVisible: false })
  set({ message: 'برای ادامهٔ فاکتور از «پرداخت در انتظار تأیید» استفاده کن.' })
}

export function setCheckoutMessage(message: string | null) { set({ message }) }
export function setCheckoutError(error: string | null) { set({ error }) }
export function clearOpenUrl() { set({ openUrl: null }) }
export function clearDelivery() { set({ delivery: null }) }

function persist(): void {
  const s = get()
  const p = s.purchase
  if (!p) return
  const root = {
    owner, username: p.username, due: p.amountDue, balance: p.balance, price: p.price,
    receipt_sent: s.receiptSent, wallet_top_up: s.walletTopUp, stage: s.stage, payment: s.payment
  }
  safeStorage.set(INVOICE_KEY, JSON.stringify(root))
}

/** Restores the unpaid invoice of this same account (GhajarCheckoutViewModel.restore). */
export async function restoreCheckout(): Promise<void> {
  owner = (await accountKey()) ?? ''
  const root = safeStorage.json<any>(INVOICE_KEY, null)
  if (!root || !owner || root.owner !== owner) return
  set({
    purchase: { completed: false, requiresPayment: true, username: root.username || null, amountDue: +root.due || 0, balance: +root.balance || 0, price: +root.price || 0, service: null },
    receiptSent: !!root.receipt_sent, walletTopUp: !!root.wallet_top_up, stage: OrderFlow.fromStorage(root.stage),
    payment: root.payment ?? null
  })
  if (get().purchase) void runOperation(async () => { set({ methods: await api.paymentOptions() }) })
}

/**
 * Opens a payment page. The PWA stays where it is (a new browsing context),
 * and coming back to it is what triggers the status check - the same
 * "check on resume" the app does when its payment activity closes.
 */
export function openCheckout(url: string): boolean {
  if (!isTrustedPaymentUrl(url)) {
    set({ error: 'صفحهٔ پرداخت امن باز نشد؛ «ادامهٔ همین پرداخت» را دوباره بزن.' })
    return false
  }
  // Inside the desktop or Android app: the app's own payment window, no address bar.
  const shell = (window as Window & { ghajarDesktop?: { openPayment?: (u: string) => void }; ghajarNative?: { openPayment?: (u: string) => void } })
  // Called on the object itself: an Android bridge method cannot be called detached.
  try {
    if (shell.ghajarNative?.openPayment) { shell.ghajarNative.openPayment(url); return true }
    if (shell.ghajarDesktop?.openPayment) { shell.ghajarDesktop.openPayment(url); return true }
  } catch { /* fall back to the browser */ }
  // Not 'noopener' in the features string: with it window.open always returns
  // null, and a blocked popup could not be told apart from an opened one.
  const w = window.open(url, '_blank')
  if (w) { try { w.opener = null } catch { /* cross-origin already */ } }
  else location.href = url
  return true
}
