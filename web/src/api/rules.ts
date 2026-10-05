/** GhajarTimeBuckets, GhajarCommerceRules and GhajarOrderFlow, ported unchanged. */

const ALIASES: Record<number, number[]> = { 1: [1], 7: [7], 30: [30, 31], 60: [60, 61], 90: [90, 91], 120: [120, 121], 180: [180, 181], 365: [365], 0: [0] }

export function timeBucketMatches(bucketDay: number, productDays: number | null): boolean {
  return productDays != null && (ALIASES[bucketDay] ?? [bucketDay]).includes(productDays)
}

export type PaymentOutcome = 'WALLET_CREDITED' | 'SERVICE_READY' | 'PAID_WAITING' | 'NOT_APPROVED' | 'PENDING'
export type OrderStage = 'PAYMENT_CONFIRMED' | 'PROVISIONING' | 'DELIVERED' | 'PROVISION_FAILED' | 'WALLET_REFUNDED'

export const paid = (v: string) => v.trim().toLowerCase() === 'paid'
export const terminal = (v: string) => ['reject', 'rejected', 'expire', 'expired', 'canceled', 'cancelled'].includes(v.trim().toLowerCase())
export const cardPayment = (kind: string, card: string | null) =>
  ['carttocart', 'carttocart_pv', 'card', 'card_to_card'].includes(kind.toLowerCase()) || !!(card && card.trim())

export function paymentOutcome(status: string, walletTopUp: boolean, walletOnly: boolean, serviceReady: boolean, hasService: boolean): PaymentOutcome {
  if (paid(status) && (walletTopUp || walletOnly)) return 'WALLET_CREDITED'
  if (paid(status) && serviceReady && hasService) return 'SERVICE_READY'
  if (paid(status)) return 'PAID_WAITING'
  if (terminal(status)) return 'NOT_APPROVED'
  return 'PENDING'
}

export const OrderFlow = {
  fromStorage(v: string | null | undefined): OrderStage | null {
    return v && ['PAYMENT_CONFIRMED', 'PROVISIONING', 'DELIVERED', 'PROVISION_FAILED', 'WALLET_REFUNDED'].includes(v) ? v as OrderStage : null
  },
  initialStage(paidNow: boolean, walletTopUp: boolean): OrderStage | null { return paidNow && !walletTopUp ? 'PAYMENT_CONFIRMED' : null },
  onDeliveryAttempt(s: OrderStage): OrderStage { return s === 'PAYMENT_CONFIRMED' || s === 'PROVISION_FAILED' ? 'PROVISIONING' : s },
  onDelivered(s: OrderStage, imported: number): OrderStage {
    return imported > 0 && ['PROVISIONING', 'PROVISION_FAILED', 'PAYMENT_CONFIRMED'].includes(s) ? 'DELIVERED' : s
  },
  onDeliveryFailed(s: OrderStage): OrderStage { return ['PAYMENT_CONFIRMED', 'PROVISIONING', 'PROVISION_FAILED'].includes(s) ? 'PROVISION_FAILED' : s },
  walletFallbackAllowed(s: OrderStage): boolean { return ['PAYMENT_CONFIRMED', 'PROVISIONING', 'PROVISION_FAILED'].includes(s) },
  onWalletRefunded(s: OrderStage): OrderStage | null { return OrderFlow.walletFallbackAllowed(s) ? 'WALLET_REFUNDED' : null },
  refundEligible(status: string, walletTopUp: boolean, serviceReady: boolean, hasService: boolean): boolean {
    if (walletTopUp) return false
    return paymentOutcome(status, walletTopUp, false, serviceReady, hasService) === 'PAID_WAITING'
  }
}
