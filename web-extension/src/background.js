// Ghajar VPN for the browser.
//
// Only this browser goes through the VPN: the Ghajar desktop app runs the
// connection core (Xray) as a local proxy on 127.0.0.1, and this extension
// points the browser - and nothing else on the computer - at it. While
// connected, WebRTC is kept from leaking the real address.
//
// Works in Chrome/Edge (chrome.proxy.settings) and Firefox (proxy.onRequest).

const API = 'http://127.0.0.1:47823'
const ext = typeof browser !== 'undefined' ? browser : chrome
const isFirefox = typeof browser !== 'undefined' && !!(browser.proxy && browser.proxy.onRequest)

let route = null // { host, port } while connected

async function api(path, body) {
  const res = await fetch(API + path, {
    method: body ? 'POST' : 'GET',
    headers: { 'X-Ghajar-Client': 'extension', ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
    cache: 'no-store'
  })
  return res.json()
}

async function companion() {
  try { return await api('/status') } catch { return null }
}

// ------------------------------------------------------------------ routing

if (isFirefox) {
  // Firefox asks per request; everything but this computer goes to the core.
  browser.proxy.onRequest.addListener(async details => {
    if (!route) {
      const saved = await ext.storage.local.get('route')
      route = saved.route || null
    }
    if (!route) return { type: 'direct' }
    try {
      const host = new URL(details.url).hostname
      if (host === 'localhost' || host === '127.0.0.1' || host === '[::1]') return { type: 'direct' }
    } catch { /* not a URL with a host */ }
    return { type: 'socks', host: route.host, port: route.port, proxyDNS: true }
  }, { urls: ['<all_urls>'] })
}

async function applyRoute(next) {
  route = next
  await ext.storage.local.set({ route: next })
  if (!isFirefox) {
    if (next) {
      await chrome.proxy.settings.set({
        value: {
          mode: 'fixed_servers',
          rules: { singleProxy: { scheme: 'socks5', host: next.host, port: next.port }, bypassList: ['localhost', '127.0.0.1', '[::1]', '<local>'] }
        },
        scope: 'regular'
      })
    } else {
      await chrome.proxy.settings.clear({ scope: 'regular' })
    }
  }
  // WebRTC would otherwise reveal the real address while connected.
  try {
    const policy = ext.privacy && ext.privacy.network && ext.privacy.network.webRTCIPHandlingPolicy
    if (policy) {
      if (next) await policy.set({ value: 'disable_non_proxied_udp' })
      else await policy.clear({})
    }
  } catch { /* not allowed in this browser */ }
  await badge(!!next)
}

async function badge(on) {
  const action = ext.action || ext.browserAction
  try {
    await action.setBadgeText({ text: on ? 'ON' : '' })
    await action.setBadgeBackgroundColor({ color: on ? '#00A86B' : '#6B807A' })
    await action.setIcon({ path: on ? { 16: 'icons/icon-16.png', 32: 'icons/icon-32.png' } : { 16: 'icons/icon-off-16.png', 32: 'icons/icon-off-32.png' } })
    await action.setTitle({ title: on ? 'قاجار وی پی ان · مرورگر متصل' : 'قاجار وی پی ان' })
  } catch { /* older browser */ }
}

/** Keeps the browser's route equal to what the desktop app really has: a stopped core never leaves the browser offline. */
async function sync() {
  const st = await companion()
  if (st && st.ok && st.connected && st.socks) {
    if (!route || route.port !== st.socks.port || route.host !== st.socks.host) await applyRoute({ host: st.socks.host, port: st.socks.port })
  } else if (route) {
    await applyRoute(null)
  }
  return st
}

// ------------------------------------------------------------------ popup

ext.runtime.onMessage.addListener((msg, _sender, reply) => {
  (async () => {
    try {
      switch (msg && msg.type) {
        case 'status': {
          const st = await sync()
          return st ? { ok: true, companion: true, ...st } : { ok: true, companion: false }
        }
        case 'servers': return await api('/servers')
        case 'connect': {
          const st = await api('/connect', { index: msg.index })
          if (st.ok && st.connected && st.socks) await applyRoute({ host: st.socks.host, port: st.socks.port })
          return { companion: true, ...st }
        }
        case 'disconnect': {
          await applyRoute(null)
          return { companion: true, ...(await api('/disconnect', {})) }
        }
        case 'ping': return await api('/ping', {})
        case 'refresh': return await api('/refresh', {})
        case 'directIran': return await api('/direct-iran', { on: !!msg.on })
        default: return { ok: false, error: 'unknown' }
      }
    } catch (e) {
      // The desktop app is closed: never leave the browser pointed at a dead proxy.
      if (route) await applyRoute(null)
      return { ok: false, companion: false, error: String(e && e.message || e) }
    }
  })().then(reply)
  return true
})

ext.alarms.create('ghajar-sync', { periodInMinutes: 1 })
ext.alarms.onAlarm.addListener(a => { if (a.name === 'ghajar-sync') sync() })
ext.runtime.onStartup.addListener(() => { sync() })
ext.runtime.onInstalled.addListener(() => { sync() })
