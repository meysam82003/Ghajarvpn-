import { createStore } from './store'
import { PUSH_API, CLIENT_HEADER, CLIENT_ID } from '../api/config'
import { token } from '../api/account'
import { idbSet } from './idb'
import { isStandalone } from './platform'

/**
 * Installation, the service worker, and Web Push.
 *
 * Push is the part that makes a closed app still put "your service ends
 * tomorrow" on the lock screen: the server runs the same feed for every
 * subscribed device and pushes what is due (pwa/push.php + pwa/push-cron.php).
 * While the app is open the page posts the same notifications itself.
 */

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

export const installPrompt = createStore<BeforeInstallPromptEvent | null>(null)
export const installed = createStore<boolean>(isStandalone())
export const swReady = createStore<boolean>(false)
export const updateReady = createStore<ServiceWorker | null>(null)
export const pushState = createStore<'unsupported' | 'default' | 'denied' | 'subscribed' | 'unsubscribed'>('default')

window.addEventListener('beforeinstallprompt', e => {
  e.preventDefault()
  installPrompt.set(e as BeforeInstallPromptEvent)
})
window.addEventListener('appinstalled', () => { installed.set(true); installPrompt.set(null) })
try {
  window.matchMedia('(display-mode: standalone)').addEventListener('change', ev => installed.set(ev.matches || isStandalone()))
} catch { /* old browsers */ }

export async function promptInstall(): Promise<boolean> {
  const p = installPrompt.get()
  if (!p) return false
  await p.prompt()
  const choice = await p.userChoice.catch(() => ({ outcome: 'dismissed' as const }))
  installPrompt.set(null)
  return choice.outcome === 'accepted'
}

export async function registerServiceWorker(): Promise<void> {
  if (!('serviceWorker' in navigator)) return
  try {
    const reg = await navigator.serviceWorker.register('./sw.js', { scope: './' })
    swReady.set(true)
    const watch = (w: ServiceWorker | null) => {
      if (!w) return
      w.addEventListener('statechange', () => {
        if (w.state === 'installed' && navigator.serviceWorker.controller) updateReady.set(w)
      })
    }
    watch(reg.installing)
    reg.addEventListener('updatefound', () => watch(reg.installing))
    if (reg.waiting && navigator.serviceWorker.controller) updateReady.set(reg.waiting)
    // Android/desktop Chromium: a periodic background refresh of the feed
    // while the installed app is closed, on top of push.
    const anyReg = reg as ServiceWorkerRegistration & { periodicSync?: { register(tag: string, o: { minInterval: number }): Promise<void> } }
    if (anyReg.periodicSync && isStandalone()) {
      try {
        const status = await (navigator.permissions as Permissions).query({ name: 'periodic-background-sync' as PermissionName })
        if (status.state === 'granted') await anyReg.periodicSync.register('ghajar-notices', { minInterval: 15 * 60 * 1000 })
      } catch { /* not allowed here */ }
    }
    navigator.serviceWorker.addEventListener('message', ev => {
      if (ev.data?.type === 'navigate' && typeof ev.data.url === 'string') {
        location.hash = ev.data.url.replace(/^[^#]*/, '')
      }
    })
    await refreshPushState()
  } catch {
    swReady.set(false)
  }
}

export function applyUpdate(): void {
  const w = updateReady.get()
  if (!w) return
  navigator.serviceWorker.addEventListener('controllerchange', () => location.reload(), { once: true })
  w.postMessage({ type: 'skipWaiting' })
}

export function pushSupported(): boolean {
  // The desktop app shows notices itself while it runs in the tray.
  if ((window as Window & { ghajarDesktop?: unknown; ghajarNative?: unknown }).ghajarDesktop || (window as Window & { ghajarNative?: unknown }).ghajarNative) return false
  return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window
}

export async function refreshPushState(): Promise<void> {
  if (!pushSupported()) { pushState.set('unsupported'); return }
  if (Notification.permission === 'denied') { pushState.set('denied'); return }
  try {
    const reg = await navigator.serviceWorker.getRegistration()
    const sub = await reg?.pushManager.getSubscription()
    pushState.set(sub ? 'subscribed' : Notification.permission === 'granted' ? 'unsubscribed' : 'default')
  } catch { pushState.set('default') }
}

function b64urlToBytes(s: string): Uint8Array {
  const pad = '='.repeat((4 - (s.length % 4)) % 4)
  const raw = atob((s + pad).replace(/-/g, '+').replace(/_/g, '/'))
  return Uint8Array.from(raw, c => c.charCodeAt(0))
}

async function pushCall(action: string, body?: unknown): Promise<any> {
  const res = await fetch(`${PUSH_API}?action=${action}`, {
    method: body ? 'POST' : 'GET',
    headers: {
      Accept: 'application/json', [CLIENT_HEADER]: CLIENT_ID,
      ...(token() ? { Authorization: `Bearer ${token()}` } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {})
    },
    body: body ? JSON.stringify(body) : undefined,
    credentials: 'same-origin',
    cache: 'no-store'
  })
  const json = await res.json().catch(() => ({}))
  if (!res.ok || json.status === false) throw new Error(json.msg || 'push.php unavailable')
  return json
}

/** Asks for permission (from a tap) and registers this device for push. */
export async function enablePush(prefs?: Record<string, boolean>): Promise<'subscribed' | 'denied' | 'unsupported' | 'local'> {
  if (!pushSupported()) return 'unsupported'
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') { await refreshPushState(); return 'denied' }
  const reg = await navigator.serviceWorker.ready
  try {
    const { key } = await pushCall('key')
    let sub = await reg.pushManager.getSubscription()
    if (!sub) sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: b64urlToBytes(key) as BufferSource })
    await pushCall('subscribe', { subscription: sub.toJSON(), prefs: prefs ?? {}, ua: navigator.userAgent.slice(0, 250) })
    await idbSet('pushEndpoint', sub.endpoint)
    pushState.set('subscribed')
    return 'subscribed'
  } catch {
    // The server side is not installed yet: notifications still work while
    // the app is open (and via periodic sync on Chromium).
    await refreshPushState()
    return 'local'
  }
}

export async function syncPushPrefs(prefs: Record<string, boolean>): Promise<void> {
  try {
    const reg = await navigator.serviceWorker.getRegistration()
    const sub = await reg?.pushManager.getSubscription()
    if (sub) await pushCall('subscribe', { subscription: sub.toJSON(), prefs, ua: navigator.userAgent.slice(0, 250) })
  } catch { /* best effort */ }
}

export async function disablePush(): Promise<void> {
  try {
    const reg = await navigator.serviceWorker.getRegistration()
    const sub = await reg?.pushManager.getSubscription()
    if (sub) {
      await pushCall('unsubscribe', { endpoint: sub.endpoint }).catch(() => undefined)
      await sub.unsubscribe()
    }
  } finally {
    await refreshPushState()
  }
}

export async function testPush(): Promise<boolean> {
  try { await pushCall('test', {}); return true } catch { return false }
}

/** Re-binds the push subscription to whichever account is signed in now. */
export async function rebindPush(): Promise<void> {
  if (pushState.get() !== 'subscribed' || !token()) return
  try {
    const reg = await navigator.serviceWorker.getRegistration()
    const sub = await reg?.pushManager.getSubscription()
    if (sub) await pushCall('subscribe', { subscription: sub.toJSON(), ua: navigator.userAgent.slice(0, 250) })
  } catch { /* later */ }
}

/** The app badge on the home-screen icon (iOS 16.4+, Chromium). */
export function setBadge(count: number): void {
  const nav = navigator as Navigator & { setAppBadge?: (n?: number) => Promise<void>; clearAppBadge?: () => Promise<void> }
  try {
    if (count > 0) void nav.setAppBadge?.(count)
    else void nav.clearAppBadge?.()
  } catch { /* unsupported */ }
}
