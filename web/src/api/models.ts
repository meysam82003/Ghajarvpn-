/** The data the store screens work with - GhajarStoreApi.kt's data classes. */

export interface Panel { id: string; name: string; custom: boolean; customUsername: boolean; usernameRequired: boolean; noteEnabled: boolean }
export interface Category { id: string; name: string }
export interface TimeRange { days: number; name: string }
export interface Product { id: string; name: string; price: number | null; trafficGb: number | null; days: number | null; description: string; countryId: string }
export interface CustomQuote { price: number | null; trafficMin: number; trafficMax: number; timeMin: number; timeMax: number }

export interface OwnedService {
  username: string
  productName: string
  status: string
  location: string
  invoiceId: string
  dataLimitBytes: number | null
  usedBytes: number | null
  expireTimestamp: number | null
  planGb: number | null
  planDays: number | null
  soldAt: number | null
}

export interface ServiceDetails {
  username: string
  productName: string
  status: string
  usedGb: number | null
  totalGb: number | null
  remainingGb: number | null
  expiresAt: string
  subscriptionUrl: string | null
  outputs: string[]
}

export interface NoticeMeta { dataLimitBytes: number | null; usedBytes: number | null; remainingBytes: number | null; daysRemaining: number | null; expireTimestamp: number | null }

export interface Notice {
  id: string
  title: string
  message: string
  important: boolean
  serviceAlert: boolean
  serviceUsername: string | null
  meta: NoticeMeta | null
  action: string
  actionRef: string
  shouldFloat: boolean
  repeatAfterSec: number
  seen: boolean
}

export interface NoticeFeed { notices: Notice[]; unseen: number; shopEnabled: boolean; shopMessage: string; gate: string; gateMessage: string }

export interface PaymentMethod { id: string; label: string; kind: string; directUrl: string | null; minimum: number; maximum: number }
export interface PaymentOptions { methods: PaymentMethod[]; balance: number; currency: string }
export interface PaymentInit {
  kind: string; orderId: string; url: string | null; cardNumber: string | null; cardHolder: string | null
  amount: number; amountRial: number; message: string; method: string; methodLabel: string; expiresAt: number
}
export interface PurchaseRequest {
  countryId: string; serviceId?: string | null; customTrafficGb?: number | null; customTimeDays?: number | null
  customUsername?: string | null; note?: string | null; discountCode?: string | null
}
export interface PurchaseResult {
  completed: boolean; requiresPayment: boolean; username: string | null; amountDue: number; balance: number; price: number
  service: ServiceDetails | null
}
export interface TrialPanel { code: string; name: string; remaining: number | null }
export interface TrialOptions { panels: TrialPanel[]; remaining: number | null; canRequest: boolean }
export interface RenewProduct { code: string; name: string; volumeGb: number; timeDays: number; price: number; showPrice: boolean; note: string; isCurrentPlan: boolean }
export interface RenewCustom { enabled: boolean; forced: boolean; pricePerGb: number; pricePerDay: number; minVolumeGb: number; maxVolumeGb: number; minTimeDays: number; maxTimeDays: number }
export interface RenewOptions { username: string; panelName: string; products: RenewProduct[]; currentPlanCode: string | null; showPrice: boolean; discountPercent: number; balance: number; custom: RenewCustom }
export interface RenewResult { completed: boolean; requiresPayment: boolean; username: string; amountDue: number; balance: number; price: number; orderId: string | null }
export interface PendingPayment { orderId: string; method: string; label: string; amount: number; expiresAt: number; status: string }

// ---------------------------------------------------------------- marketplace

export interface MarketTickCriterion { label: string; value: number; target: number; ok: boolean }
export interface MarketTick { earned: boolean; label: string; met: number; total: number; criteria: MarketTickCriterion[] }
export interface MarketReview { stars: number; body: string; createdAt: number }
export interface MarketShop {
  id: number; name: string; description: string; telegramBot: string; telegramChannel: string; supportContact: string
  verified: boolean; status: string; canSell: boolean; closedReason: string; stars: number; reviewCount: number; satisfaction: number
  minPrice: number; maxPrice: number; productCount: number; discountCount: number; sales30d: number; reviews: MarketReview[]
  tagline: string; logoVersion: number; gbPrice: number; dayPrice: number; panelMinPrice: number; panelMaxPrice: number
  panelCount: number; testAvailable: boolean; tick: MarketTick | null; myServices: number
}
export interface MarketFeed { enabled: boolean; registerFee: number; shops: MarketShop[]; ghajarLogoVersion: number }
export interface MarketPanel {
  code: string; name: string; country: string; flag: string; custom: boolean; gbPrice: number; dayPrice: number
  minGb: number; maxGb: number; minDays: number; maxDays: number; gbPriceBefore: number; test: boolean; testHours: number; testMb: number
}
export interface MarketProduct { code: string; name: string; price: number; volumeGb: number; timeDays: number; note: string; location: string; category: string; priceBefore: number }
export interface MarketMethod { id: string; label: string; needs: string; note: string; cardNumber: string; cardHolder: string; contact: string }
export interface MarketCatalog { panels: MarketPanel[]; products: MarketProduct[]; methods: MarketMethod[]; categories: Category[] }
export interface MarketAnnouncement { id: number; title: string; body: string; discountCode: string; publishedAt: number }
export interface MarketPublicCode { code: string; percent: number; expiresAt: number; left: number }
export interface MarketHome {
  shop: MarketShop; reachable: boolean; catalog: MarketCatalog; testEnabled: boolean; testUsed: boolean
  wallet: number | null; unread: number; blocked: boolean; mine: boolean
  appliedCode: string; appliedOk: boolean; appliedMsg: string
  announcements: MarketAnnouncement[]; discountCodes: MarketPublicCode[]
}
export interface MarketOrder {
  id: number; method: string; methodLabel: string; needs: string; amount: number; cardNumber: string; cardHolder: string
  contact: string; gatewayUrl: string; productName: string; status: string; message: string
}
export interface MarketOrderStatus { id: number; shopId: number; status: string; rejectReason: string; amount: number; username: string; configs: string[]; subscription: string }
export interface MarketService {
  invoiceId: string; username: string; productName: string; panelName: string; isTest: boolean; boughtAt: number
  reachable: boolean; status: string; dataLimit: number; used: number; expire: number; volumeGb: number; timeDays: number
  subscription: string; configCount: number
}
export interface MarketMessage { id: number; kind: string; title: string; body: string; createdAt: number; read: boolean }
export interface MarketWalletEntry { amount: number; kind: string; title: string; balanceAfter: number; createdAt: number }
export interface MarketWallet { balance: number; history: MarketWalletEntry[] }
export interface MarketTransaction { id: number; kind: string; status: string; amount: number; method: string; productName: string; rejectReason: string; createdAt: number }
export interface MarketTicket { id: number; subject: string; status: string; updatedAt: number }
export interface MarketTicketMessage { fromSeller: boolean; body: string; createdAt: number }
export interface MarketTicketThread { id: number; subject: string; status: string; messages: MarketTicketMessage[] }
export interface MarketMyShop { shopId: number; name: string; services: number; lastAt: number }

// ---------------------------------------------------------------- derived

const GIB = 1073741824

export function remainingBytes(s: OwnedService): number | null {
  const limit = s.dataLimitBytes && s.dataLimitBytes > 0 ? s.dataLimitBytes : null
  if (limit == null) return null
  return Math.max(0, limit - (s.usedBytes ?? 0))
}

export function volumeFraction(s: OwnedService): number | null {
  const limit = s.dataLimitBytes && s.dataLimitBytes > 0 ? s.dataLimitBytes : null
  if (limit == null) return null
  return Math.min(1, Math.max(0, (s.usedBytes ?? 0) / limit))
}

export function daysRemaining(s: OwnedService, nowSec = Date.now() / 1000): number | null {
  let expiry = s.expireTimestamp && s.expireTimestamp > 0 ? s.expireTimestamp : null
  if (expiry == null && s.soldAt && s.soldAt > 0 && s.planDays && s.planDays > 0) expiry = s.soldAt + s.planDays * 86400
  if (expiry == null) return null
  return Math.max(0, Math.floor(((expiry - nowSec) + 86399) / 86400))
}

export function timeFraction(s: OwnedService): number | null {
  const total = s.planDays && s.planDays > 0 ? s.planDays : null
  const left = daysRemaining(s)
  if (total == null || left == null) return null
  return Math.min(1, Math.max(0, (total - left) / total))
}

export function isEnded(s: OwnedService): boolean {
  const st = s.status.toLowerCase()
  return ['expired', 'limited', 'disabled', 'inactive', 'منقضی'].includes(st) || daysRemaining(s) === 0 || remainingBytes(s) === 0
}

export function needsRenewSoon(s: OwnedService): boolean {
  return !isEnded(s) && ((daysRemaining(s) ?? Number.MAX_SAFE_INTEGER) <= 3 || (volumeFraction(s) ?? 0) >= 0.85)
}

export type ServiceSort = 'NEWEST' | 'RENEW_SOON' | 'ENDED' | 'ACTIVE'
export const ServiceSortLabels: [ServiceSort, string][] = [['NEWEST', 'جدیدترین'], ['RENEW_SOON', 'نیاز به تمدید'], ['ENDED', 'تمام‌شده'], ['ACTIVE', 'فعال']]

export function sortedFor(list: OwnedService[], sort: ServiceSort): OwnedService[] {
  const newest = [...list].sort((a, b) => (b.soldAt ?? 0) - (a.soldAt ?? 0))
  switch (sort) {
    case 'NEWEST': return newest
    case 'RENEW_SOON': return newest.filter(needsRenewSoon).sort((a, b) =>
      ((daysRemaining(a) ?? Number.MAX_SAFE_INTEGER) - (daysRemaining(b) ?? Number.MAX_SAFE_INTEGER)) ||
      ((volumeFraction(b) ?? 0) - (volumeFraction(a) ?? 0)))
    case 'ENDED': return newest.filter(isEnded)
    case 'ACTIVE': return newest.filter(s => !isEnded(s))
  }
}

export { GIB }
