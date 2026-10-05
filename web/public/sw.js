/* Ghajar VPN service worker.
 *
 *  - Keeps the app shell available offline (never the API, payments or links).
 *  - Shows Web Push notifications in the device's notification shade, with the
 *    same actions the Android app offers ("خوندم", renew, go to the shop).
 *  - On Chromium, refreshes the notice feed in the background (periodic sync),
 *    exactly like the app's 15-minute JobScheduler job.
 */
const VERSION = 'ghajar-pwa-v2'
const SHELL = VERSION + '-shell'
const ASSETS = VERSION + '-assets'
const SCOPE = self.registration.scope
const API = new URL('../api/', SCOPE).toString()
const ICON = new URL('icons/icon-192.png', SCOPE).toString()
const BADGE = new URL('icons/badge-96.png', SCOPE).toString()

self.addEventListener('install', event => {
  event.waitUntil(caches.open(SHELL).then(c => c.addAll(['./', './index.html', './manifest.webmanifest', './icons/icon-192.png', './icons/badge-96.png']).catch(() => undefined)))
})

self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    const keys = await caches.keys()
    await Promise.all(keys.filter(k => !k.startsWith(VERSION)).map(k => caches.delete(k)))
    await self.clients.claim()
  })())
})

self.addEventListener('message', event => {
  if (event.data && event.data.type === 'skipWaiting') self.skipWaiting()
})

self.addEventListener('fetch', event => {
  const req = event.request
  if (req.method !== 'GET') return
  const url = new URL(req.url)
  if (url.origin !== location.origin) return
  // The API, payments and subscription links are never cached.
  if (url.href.startsWith(API) || !url.href.startsWith(SCOPE) || url.pathname.endsWith('.php')) return
  if (req.mode === 'navigate') {
    event.respondWith((async () => {
      try {
        const fresh = await fetch(req)
        const cache = await caches.open(SHELL)
        cache.put('./index.html', fresh.clone()).catch(() => undefined)
        return fresh
      } catch {
        return (await caches.match('./index.html')) || (await caches.match('./')) || new Response('<!doctype html><meta charset="utf-8"><body style="background:#050807;color:#E9F4EF;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0" dir="rtl">اتصال اینترنت برقرار نیست؛ دوباره تلاش کن.</body>', { headers: { 'Content-Type': 'text/html; charset=utf-8' } })
      }
    })())
    return
  }
  // Hashed build files and icons: cache first, they never change in place.
  if (/\/assets\/|\/icons\/|\/splash\//.test(url.pathname)) {
    event.respondWith((async () => {
      const hit = await caches.match(req)
      if (hit) return hit
      const res = await fetch(req)
      if (res.ok) (await caches.open(ASSETS)).put(req, res.clone()).catch(() => undefined)
      return res
    })())
  }
})

// ---------------------------------------------------------------- IndexedDB (shared with the page)

function idb(mode, f) {
  return new Promise(resolve => {
    const open = indexedDB.open('ghajar-pwa', 1)
    open.onupgradeneeded = () => open.result.createObjectStore('kv')
    open.onerror = () => resolve(undefined)
    open.onsuccess = () => {
      const db = open.result
      const tx = db.transaction('kv', mode)
      const r = f(tx.objectStore('kv'))
      r.onsuccess = () => resolve(r.result)
      r.onerror = () => resolve(undefined)
      tx.oncomplete = () => db.close()
    }
  })
}
const idbGet = key => idb('readonly', s => s.get(key))
const idbSet = (key, value) => idb('readwrite', s => s.put(value, key))

// ---------------------------------------------------------------- notices

const KIND_TITLES = { service_time: 'مهلت سرویس رو به پایان است', service_volume: 'حجم سرویس رو به پایان است', shop_status: 'وضعیت فروشگاه' }

function routeFor(n) {
  if (n.action === 'market_shop') {
    const [id, code] = String(n.action_ref || '').split('|')
    if (/^\d+$/.test(id || '')) return `#/shop?shop=${id}${code ? '&code=' + encodeURIComponent(code.replace(/[^\w-]/g, '')) : ''}`
  }
  if (n.action === 'renew' && n.action_ref) return `#/shop?renew=${encodeURIComponent(n.action_ref)}`
  return '#/notices'
}

function channelOf(kind) {
  if (kind === 'shop_status' || kind === 'service_time' || kind === 'service_volume') return kind === 'shop_status' ? 'important' : 'service'
  return 'general'
}

async function show(n) {
  const prefs = (await idbGet('notifyPrefs')) || { general: true, service: true, important: true }
  const channel = n.channel || channelOf(n.kind)
  if (prefs[channel] === false) return false
  const actions = []
  if (n.action === 'market_shop') actions.push({ action: 'open', title: String(n.action_ref || '').split('|')[1] ? 'استفاده از کد تخفیف' : 'رفتن به فروشگاه' })
  else if (n.action === 'renew' && n.action_ref) actions.push({ action: 'open', title: 'تمدید همین سرویس' })
  if (n.id) actions.push({ action: 'ack', title: 'خوندم' })
  await self.registration.showNotification(n.title || KIND_TITLES[n.kind] || 'اعلان قاجار وی پی ان', {
    body: n.body || '',
    tag: n.id || undefined,
    icon: ICON,
    badge: BADGE,
    dir: 'rtl',
    lang: 'fa',
    requireInteraction: channel === 'important',
    renotify: false,
    data: { id: n.id || '', url: n.url || routeFor(n) },
    actions
  })
  return true
}

async function api(path, init, base = API) {
  const token = await idbGet('token')
  if (!token) return null
  const res = await fetch(base + path, {
    ...init,
    headers: { Accept: 'application/json', Authorization: 'Bearer ' + token, 'X-Ghajar-Client': 'app', ...(init && init.body ? { 'Content-Type': 'application/json' } : {}) },
    cache: 'no-store'
  })
  return res.json().catch(() => null)
}
const pushApi = (path, init) => api(path, init, SCOPE)

async function sha(text) {
  const d = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text))
  return [...new Uint8Array(d)].slice(0, 12).map(b => b.toString(16).padStart(2, '0')).join('')
}

/** The app's refresh(): due notices to the shade, then "shown" back to the server. */
async function refreshFeed() {
  const feed = await api('notices.php?action=feed&client=app')
  if (!feed || feed.status !== true || !Array.isArray(feed.notices)) return
  const token = await idbGet('token')
  const key = 'notified:sw:' + (await sha(String(token))).slice(0, 12)
  const notified = new Set((await idbGet(key)) || [])
  const posted = []
  for (const row of feed.notices) {
    if (!row || row.should_float === false) continue
    const body = String(row.body || '').trim()
    if (!body) continue
    const id = 'notice:' + Number(row.id)
    const open = await self.registration.getNotifications({ tag: id })
    if (open.length) continue
    const fp = id + ':' + (await sha((row.title || '') + '\n' + body))
    if (notified.has(fp) && !(Number(row.repeat_after) > 0)) continue
    if (await show({ id, title: row.title || KIND_TITLES[row.kind], body, kind: row.kind, action: row.action, action_ref: row.action_ref })) {
      notified.add(fp)
      posted.push(Number(row.id))
    }
  }
  await idbSet(key, [...notified].slice(-500))
  if (posted.length) await api('notices.php?action=shown&client=app', { method: 'POST', body: JSON.stringify({ ids: posted }) }).catch(() => null)
  if (self.navigator.setAppBadge && typeof feed.unseen === 'number') self.navigator.setAppBadge(feed.unseen).catch(() => undefined)
}

self.addEventListener('push', event => {
  event.waitUntil((async () => {
    let data = null
    try { data = event.data ? event.data.json() : null } catch { data = event.data ? { body: event.data.text() } : null }
    if (data && (data.body || data.title)) {
      const shown = await show(data)
      if (!shown) {
        // Categories switched off: a push must still end with a notification on
        // some browsers, so the quietest one is left and closed at once.
        await self.registration.showNotification('قاجار وی پی ان', { body: 'پیام تازه در برنامه', tag: 'ghajar-quiet', icon: ICON, badge: BADGE, silent: true })
        const list = await self.registration.getNotifications({ tag: 'ghajar-quiet' })
        list.forEach(n => n.close())
      }
      if (typeof data.unseen === 'number' && self.navigator.setAppBadge) self.navigator.setAppBadge(data.unseen).catch(() => undefined)
    } else {
      try { await refreshFeed() } catch { /* offline */ }
      const open = await self.registration.getNotifications()
      if (!open.length) await self.registration.showNotification('قاجار وی پی ان', { body: 'پیام تازه‌ای در برنامه داری', icon: ICON, badge: BADGE, data: { url: '#/notices' } })
    }
  })())
})

self.addEventListener('periodicsync', event => {
  if (event.tag === 'ghajar-notices') event.waitUntil(refreshFeed().catch(() => undefined))
})

self.addEventListener('notificationclick', event => {
  const n = event.notification
  const data = n.data || {}
  n.close()
  event.waitUntil((async () => {
    if (event.action === 'ack') {
      const id = String(data.id || '')
      if (id.startsWith('notice:')) await api('notices.php?action=dismiss&client=app', { method: 'POST', body: JSON.stringify({ ids: [Number(id.slice(7))] }) }).catch(() => null)
      return
    }
    const target = new URL('./' + (data.url || '#/notices'), SCOPE).toString()
    const all = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    for (const c of all) {
      if (c.url.startsWith(SCOPE)) {
        c.postMessage({ type: 'navigate', url: data.url || '#/notices' })
        return c.focus()
      }
    }
    return self.clients.openWindow(target)
  })())
})

self.addEventListener('pushsubscriptionchange', event => {
  event.waitUntil((async () => {
    try {
      const keyRes = await fetch(SCOPE + 'push.php?action=key', { cache: 'no-store' }).then(r => r.json())
      const raw = atob(keyRes.key.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((keyRes.key.length + 3) % 4))
      const sub = await self.registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: Uint8Array.from(raw, c => c.charCodeAt(0)) })
      await pushApi('push.php?action=subscribe', { method: 'POST', body: JSON.stringify({ subscription: sub.toJSON(), old_endpoint: event.oldSubscription && event.oldSubscription.endpoint }) })
    } catch { /* the page re-subscribes on next open */ }
  })())
})
