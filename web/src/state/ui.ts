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

/*
 * The address bar is the single source of truth: every move writes the hash,
 * and `hashchange` turns the hash into the screen. The back gesture is then
 * just the browser's own history - one entry above home for a tab, one more
 * for a settings page - so nothing ever replays an old address on its own.
 */
interface NavEntry { ghajarDepth?: number; belowHash?: string }
const entry = (): NavEntry => (history.state as NavEntry | null) ?? {}
const depthOf = (n: Pick<Nav, 'tab' | 'settings'>): number => n.tab === 'home' ? 0 : n.tab === 'settings' && n.settings ? 2 : 1
const here = (): string => location.hash || '#/'

/**
 * Goes to `target`. When the entry right under this one is exactly the
 * target (home under a tab, a settings list under its page) this is a real
 * step back; deeper goes on a new entry; anything else replaces this entry.
 */
function navigate(target: Pick<Nav, 'tab' | 'settings'>): void {
  const hash = navHash({ ...target, forward: true })
  if (here() === hash) return
  const cur = entry(), want = depthOf(target), depthHere = cur.ghajarDepth ?? depthOf(navStore.get())
  const onDialog = (cur as { ghajarModal?: number }).ghajarModal != null
  if (want < depthHere && cur.belowHash === hash && !onDialog) { history.back(); return }
  if (want > depthHere) history.pushState({ ghajarDepth: want, belowHash: here() }, '', hash)
  else { const { ghajarModal: _m, ...rest } = cur as NavEntry & { ghajarModal?: number }; history.replaceState({ ...rest, ghajarDepth: want }, '', hash) }
  applyHash(hash)
}

export function goTab(tab: Tab): void {
  navigate({ tab, settings: '' })
}

export function openSettings(page: SettingsPage): void {
  navigate({ tab: 'settings', settings: page })
}

export function back(): boolean {
  const cur = navStore.get()
  if (cur.tab === 'settings' && cur.settings) { navigate({ tab: 'settings', settings: '' }); return true }
  if (cur.tab !== 'home') { navigate({ tab: 'home', settings: '' }); return true }
  return false
}

function order(t: Tab): number { return t === 'home' ? 0 : t === 'shop' ? 1 : 2 }

/** The address the current place in the app is written as. */
export function navHash(n: Nav = navStore.get()): string {
  return n.tab === 'home' ? '#/' : n.tab === 'shop' ? '#/shop' : n.settings ? `#/settings/${n.settings}` : '#/settings'
}
/** Drops one-shot parameters (?renew=, ?code=) from the entry, so going back never repeats them. */
function cleanHash(): void {
  const hash = navHash()
  const cur = entry()
  if (location.hash !== hash || cur.ghajarDepth == null) history.replaceState({ ...cur, ghajarDepth: depthOf(navStore.get()) }, '', hash)
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
  const before = navStore.get()
  try {
    const raw = hash.replace(/^#\/?/, '')
    const [path, qs] = raw.split('?')
    const q = new URLSearchParams(qs ?? '')
    const parts = path.split('/').filter(Boolean)
    if (parts[0] === 'shop' || parts[0] === 'notices' || parts[0] === 'pay') {
      navStore.set({ tab: 'shop', settings: '', forward: order('shop') >= order(before.tab) })
      const renew = q.get('renew')
      if (renew) renewRequest.set(renew)
      const shop = q.get('shop')
      if (shop != null && /^\d+$/.test(shop)) shopOpenRequest.set({ shopId: +shop, code: (q.get('code') ?? '').replace(/[^\w-]/g, '') })
      if (parts[0] === 'notices') openNoticesRequest.set(Date.now())
      if (parts[0] === 'pay') paymentReturn.set(Date.now())
    } else if (parts[0] === 'settings') {
      const page = (parts[1] ?? '') as SettingsPage
      const settings = ['personalize', 'notifications', 'about', 'install', 'account'].includes(page) ? page : ''
      navStore.set({ tab: 'settings', settings, forward: before.tab !== 'settings' || (!!settings && !before.settings) })
    } else if (parts.length === 0) {
      navStore.set({ tab: 'home', settings: '', forward: false })
    }
  } finally {
    cleanHash()
  }
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
