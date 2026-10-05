import * as api from '../api/client'
import { Notice, NoticeFeed } from '../api/models'
import { accountKey, isLinked, tokenStore } from '../api/account'
import { createStore, safeStorage } from '../lib/store'
import { idbGet, idbSet } from '../lib/idb'
import { fa } from '../lib/format'

/**
 * GhajarNoticeBus + GhajarNotificationMonitor + GhajarShopStatus.
 *
 * The feed is the same one the Android app polls (notices.php?action=feed).
 * What is due floats as the in-app banner and goes to the device's
 * notification shade through the service worker; what was shown is reported
 * back with `shown`, so the server's repeat clock runs exactly as for the app.
 */

export const noticeStore = createStore<Notice | null>(null)
export const feedStore = createStore<NoticeFeed | null>(null)
export const shopStatus = createStore<{ enabled: boolean; message: string }>({ enabled: true, message: '' })
export const unreadStore = createStore<number>(0)

export type NoticeChannel = 'general' | 'service' | 'important'
export interface NotifyPrefs { general: boolean; service: boolean; important: boolean }
const PREFS_KEY = 'ghajar.notify.prefs.v1'
export const notifyPrefs = createStore<NotifyPrefs>(safeStorage.json<NotifyPrefs>(PREFS_KEY, { general: true, service: true, important: true }))
export function setNotifyPrefs(p: NotifyPrefs): void {
  notifyPrefs.set(p)
  safeStorage.set(PREFS_KEY, JSON.stringify(p))
  void idbSet('notifyPrefs', p)
}
void idbSet('notifyPrefs', notifyPrefs.get())

export function channelOf(n: Notice): NoticeChannel {
  return n.important ? 'important' : n.serviceAlert ? 'service' : 'general'
}

let pending: Notice[] = []
let busAccount = ''
const dismissedInSession = new Set<string>()

function publishBus(key: string, values: Notice[], acknowledged: Set<string>): void {
  if (busAccount !== key) { pending = []; busAccount = key; dismissedInSession.clear() }
  const seen = new Set<string>()
  pending = values.filter(n => (seen.has(n.id) ? false : (seen.add(n.id), true)))
    .filter(n => !acknowledged.has(n.id) && !dismissedInSession.has(n.id))
    .sort((a, b) => rank(b) - rank(a))
  noticeStore.set(pending[0] ?? null)
}
function rank(n: Notice): number { return n.important ? 2 : n.serviceAlert ? 1 : 0 }

function busDismiss(id: string): void {
  dismissedInSession.add(id)
  pending = pending.filter(n => n.id !== id)
  noticeStore.set(pending[0] ?? null)
}

export function resetNotices(): void {
  pending = []; busAccount = ''; dismissedInSession.clear()
  noticeStore.set(null); feedStore.set(null); unreadStore.set(0)
}

async function ackKey(): Promise<string | null> {
  const k = await accountKey()
  return k ? `ghajar.notices.${k}.ack` : null
}

function readSet(key: string): Set<string> { return new Set(safeStorage.json<string[]>(key, [])) }
function writeSet(key: string, s: Set<string>): void { safeStorage.set(key, JSON.stringify([...s].slice(-500))) }

/** "خوندم": locally (the banner stops), and to the shop (every client stops). */
export async function acknowledge(id: string): Promise<void> {
  const k = await ackKey()
  if (k) { const s = readSet(k); s.add(id); writeSet(k, s) }
  busDismiss(id)
  try {
    const reg = await navigator.serviceWorker?.getRegistration()
    const shown = await reg?.getNotifications({ tag: id })
    shown?.forEach(n => n.close())
  } catch { /* no SW */ }
  if (!k) return
  try { await api.dismissNotice(id) } catch { /* best effort */ }
}

/** withUsageSummary: the panel's own remaining figures appended to the text. */
export function withUsageSummary(n: Notice): Notice {
  const m = n.meta
  if (!m) return n
  const parts: string[] = []
  if (m.remainingBytes != null) parts.push(`حجم باقی‌مانده: ${(m.remainingBytes / 1073741824).toFixed(2)} گیگابایت`)
  if (m.daysRemaining != null) parts.push(`زمان باقی‌مانده: ${m.daysRemaining} روز`)
  const suffix = parts.join(' • ')
  if (!suffix || n.message.includes(suffix)) return n
  return { ...n, message: `${n.message}\n${suffix}` }
}

/** Which deep link a notice's action opens. */
export function noticeRoute(n: Notice): string {
  if (n.action === 'market_shop') {
    const [id, code] = n.actionRef.split('|', 2)
    if (/^\d+$/.test(id ?? '')) return `#/shop?shop=${id}${code ? `&code=${encodeURIComponent(code.replace(/[^\w-]/g, ''))}` : ''}`
  }
  if (n.serviceUsername) return `#/shop?renew=${encodeURIComponent(n.serviceUsername)}`
  return '#/notices'
}

export async function fingerprint(n: Notice): Promise<string> {
  const data = new TextEncoder().encode(n.title + '\n' + n.message)
  try {
    const d = await crypto.subtle.digest('SHA-256', data)
    return n.id + ':' + [...new Uint8Array(d)].slice(0, 12).map(b => b.toString(16).padStart(2, '0')).join('')
  } catch {
    let h = 0; for (const b of data) h = (h * 31 + b) | 0
    return n.id + ':' + (h >>> 0).toString(16)
  }
}

/** Posts one notice to the device's notification shade, through the service worker. */
export async function postSystemNotification(n: Notice): Promise<boolean> {
  if (typeof Notification === 'undefined' || Notification.permission !== 'granted') return false
  const prefs = notifyPrefs.get()
  if (!prefs[channelOf(n)]) return false
  try {
    const reg = await navigator.serviceWorker?.ready
    if (!reg) return false
    const actions: { action: string; title: string }[] = []
    if (n.action === 'market_shop') actions.push({ action: 'open', title: n.actionRef.includes('|') && n.actionRef.split('|')[1] ? 'استفاده از کد تخفیف' : 'رفتن به فروشگاه' })
    else if (n.serviceUsername) actions.push({ action: 'open', title: 'تمدید همین سرویس' })
    actions.push({ action: 'ack', title: 'خوندم' })
    const options: NotificationOptions & Record<string, unknown> = {
      body: n.message,
      tag: n.id,
      icon: new URL('icons/icon-192.png', document.baseURI).toString(),
      badge: new URL('icons/badge-96.png', document.baseURI).toString(),
      dir: 'rtl',
      lang: 'fa',
      requireInteraction: n.important,
      data: { id: n.id, url: noticeRoute(n) },
      actions
    }
    await reg.showNotification(n.title, options)
    return true
  } catch {
    return false
  }
}

let refreshing = false

/** GhajarNotificationMonitor.refresh, for the page. */
export async function refreshNotices(): Promise<boolean> {
  if (refreshing) return true
  refreshing = true
  try {
    const key = await accountKey()
    if (!key || !isLinked()) { resetNotices(); return true }
    let feed: NoticeFeed
    try { feed = await api.noticeFeed() } catch { return false }
    if ((await accountKey()) !== key) return true
    feedStore.set(feed)
    shopStatus.set({ enabled: feed.shopEnabled, message: feed.shopMessage })
    unreadStore.set(feed.unseen || feed.notices.filter(n => !n.seen).length)
    const ack = readSet(`ghajar.notices.${key}.ack`)
    const due = feed.notices.filter(n => n.shouldFloat).map(withUsageSummary)
    publishBus(key, due, ack)

    const notifiedKey = `notified:${key}`
    const notified = new Set<string>((await idbGet<string[]>(notifiedKey)) ?? [])
    const posted: string[] = []
    for (const n of due) {
      const repeating = n.id.startsWith('notice:')
      const fp = await fingerprint(n)
      if (!repeating && notified.has(fp)) continue
      if (await postSystemNotification(n)) {
        notified.add(fp)
        if (repeating) posted.push(n.id)
      }
    }
    await idbSet(notifiedKey, [...notified].slice(-500))
    if (posted.length) await api.markNoticesShown(posted)
    return true
  } finally {
    refreshing = false
  }
}

let timer: ReturnType<typeof setInterval> | undefined

/** Polls while the app is open; the service worker covers the time it is closed. */
export function startNoticeMonitor(): void {
  const tick = () => { if (document.visibilityState === 'visible') void refreshNotices() }
  clearInterval(timer)
  timer = setInterval(tick, 60_000)
  document.addEventListener('visibilitychange', tick)
  window.addEventListener('focus', tick)
  window.addEventListener('online', tick)
  tokenStore.subscribe(() => { resetNotices(); void refreshNotices() })
  void refreshNotices()
}

export function unreadLabel(n: number): string { return n > 99 ? '+۹۹' : fa(n) }
