import { createStore } from '../lib/store'

/**
 * Where the user is: the three destinations of the bottom bar and the page
 * open inside each, plus the cross-tab requests the app's singletons carry
 * (GhajarRenewRequest, GhajarShopOpenRequest, GhajarPaymentReturn).
 */
export type Tab = 'home' | 'shop' | 'settings'
export type SettingsPage = '' | 'personalize' | 'notifications' | 'about' | 'install' | 'account'

export interface Nav { tab: Tab; settings: SettingsPage; forward: boolean }

export const navStore = createStore<Nav>({ tab: 'home', settings: '', forward: true })

export function goTab(tab: Tab): void {
  const cur = navStore.get()
  navStore.set({ tab, settings: tab === 'settings' ? '' : cur.settings, forward: order(tab) >= order(cur.tab) })
  syncHash()
}

export function openSettings(page: SettingsPage): void {
  navStore.set({ tab: 'settings', settings: page, forward: true })
  syncHash()
}

export function back(): boolean {
  const cur = navStore.get()
  if (cur.tab === 'settings' && cur.settings) { navStore.set({ ...cur, settings: '', forward: false }); syncHash(); return true }
  if (cur.tab !== 'home') { navStore.set({ tab: 'home', settings: '', forward: false }); syncHash(); return true }
  return false
}

function order(t: Tab): number { return t === 'home' ? 0 : t === 'shop' ? 1 : 2 }

let applyingHash = false
/** The address the current place in the app is written as. */
export function navHash(n: Nav = navStore.get()): string {
  return n.tab === 'home' ? '#/' : n.tab === 'shop' ? '#/shop' : n.settings ? `#/settings/${n.settings}` : '#/settings'
}
function syncHash(): void {
  if (applyingHash) return
  const hash = navHash()
  if (location.hash !== hash) history.replaceState(history.state, '', hash)
}

/** A renewal asked for from outside the shop: a notification or the banner. */
export const renewRequest = createStore<string | null>(null)
/** "Go to this shop" (0 = Ghajar) with an optional discount code. */
export const shopOpenRequest = createStore<{ shopId: number; code: string; renew?: string } | null>(null)
/** Ticks when the user comes back from a payment page. */
export const paymentReturn = createStore<number>(0)
/** Opens the shop on its messages section. */
export const openNoticesRequest = createStore<number>(0)

/** Deep links: #/shop?renew=u, #/shop?shop=3&code=X, #/notices, #/settings/notifications. */
export function applyHash(hash = location.hash): void {
  applyingHash = true
  try {
    const raw = hash.replace(/^#\/?/, '')
    const [path, qs] = raw.split('?')
    const q = new URLSearchParams(qs ?? '')
    const parts = path.split('/').filter(Boolean)
    if (parts[0] === 'shop' || parts[0] === 'notices' || parts[0] === 'pay') {
      navStore.set({ tab: 'shop', settings: '', forward: true })
      const renew = q.get('renew')
      if (renew) renewRequest.set(renew)
      const shop = q.get('shop')
      if (shop != null && /^\d+$/.test(shop)) shopOpenRequest.set({ shopId: +shop, code: (q.get('code') ?? '').replace(/[^\w-]/g, '') })
      if (parts[0] === 'notices') openNoticesRequest.set(Date.now())
      if (parts[0] === 'pay') paymentReturn.set(Date.now())
    } else if (parts[0] === 'settings') {
      const page = (parts[1] ?? '') as SettingsPage
      navStore.set({ tab: 'settings', settings: ['personalize', 'notifications', 'about', 'install', 'account'].includes(page) ? page : '', forward: true })
    } else if (parts.length === 0) {
      navStore.set({ tab: 'home', settings: '', forward: false })
    }
  } finally {
    applyingHash = false
  }
  syncHash()
}

// ---------------------------------------------------------------- toast

export const toastStore = createStore<{ id: number; text: string } | null>(null)
let toastTimer: ReturnType<typeof setTimeout> | undefined
export function toast(text: string, ms = 2600): void {
  clearTimeout(toastTimer)
  toastStore.set({ id: Date.now(), text })
  toastTimer = setTimeout(() => toastStore.set(null), ms)
}

/** navigator.clipboard with a textarea fallback for older WebViews. */
export async function copyText(text: string, label?: string): Promise<void> {
  let ok = false
  try { await navigator.clipboard.writeText(text); ok = true } catch {
    try {
      const ta = document.createElement('textarea')
      ta.value = text; ta.setAttribute('readonly', ''); ta.style.position = 'fixed'; ta.style.opacity = '0'
      document.body.appendChild(ta); ta.select(); ok = document.execCommand('copy'); ta.remove()
    } catch { ok = false }
  }
  toast(ok ? `${label ?? 'متن'} کپی شد` : 'کپی انجام نشد؛ متن را نگه دار و دستی کپی کن')
}
