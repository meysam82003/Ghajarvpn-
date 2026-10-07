// The server list, as the Android app keeps it (ConfigStore.kt): subscriptions
// with their usage/expiry, the configs each one delivered, a group of manual
// configs, favourites, the selected server and the connection settings.
// Persisted as one JSON file in the app's data folder.
const fs = require('fs')
const path = require('path')
const crypto = require('crypto')

const MANUAL = 'manual'
const FREE = 'free'

function parseUserInfo(header) {
  const out = {}
  for (const part of String(header || '').split(';')) {
    const [k, v] = part.split('=').map(x => (x || '').trim())
    if (k && v && /^\d+$/.test(v)) out[k.toLowerCase()] = Number(v)
  }
  return { used: (out.upload || 0) + (out.download || 0), total: out.total || 0, expire: out.expire || 0 }
}

function titleFrom(header) {
  const v = String(header || '').trim()
  if (!v) return ''
  if (v.startsWith('base64:')) { try { return Buffer.from(v.slice(7), 'base64').toString('utf8').trim() } catch { return '' } }
  return v
}

class Store {
  constructor({ dataDir, parser }) {
    this.file = path.join(dataDir, 'vpn-store.json')
    this.parser = parser
    this.data = { subscriptions: [], configs: [], selectedId: '', settings: {}, warp: null, freeUpdated: 0, delays: {} }
    try { Object.assign(this.data, JSON.parse(fs.readFileSync(this.file, 'utf8'))) } catch { /* first run */ }
    this.listeners = new Set()
  }

  save() {
    try { fs.writeFileSync(this.file + '.tmp', JSON.stringify(this.data)); fs.renameSync(this.file + '.tmp', this.file) } catch { /* read-only */ }
    for (const l of this.listeners) { try { l() } catch { /* listener gone */ } }
  }

  onChange(fn) { this.listeners.add(fn); return () => this.listeners.delete(fn) }

  get settings() { return this.data.settings }
  setSettings(patch) { this.data.settings = { ...this.data.settings, ...patch }; this.save(); return this.data.settings }

  config(id) { return this.data.configs.find(c => c.id === id) || null }
  selected() { return this.config(this.data.selectedId) || this.data.configs[0] || null }
  select(id) { if (this.config(id)) { this.data.selectedId = id; this.save() } }

  /** Groups for the server screen: each subscription, then manual configs, then free ones. */
  groups() {
    const by = id => this.data.configs.filter(c => c.subId === id)
    const subs = this.data.subscriptions.map(s => ({ id: s.id, name: s.name, url: s.url, kind: s.kind || 'subscription', used: s.used || 0, total: s.total || 0, expire: s.expire || 0, lastUpdated: s.lastUpdated || 0, configs: by(s.id) }))
    const manual = by(MANUAL)
    return [...subs, ...(manual.length ? [{ id: MANUAL, name: 'کانفیگ‌های دستی', kind: 'manual', configs: manual }] : [])]
  }

  /** Text pasted or a file opened: share links, a subscription URL, JSON, YAML, WireGuard .conf… */
  async addFromText(text, { fetchImpl = fetch } = {}) {
    const t = String(text || '').trim()
    // Like the phone's paste path: a lone http(s) URL is a subscription, not an http proxy.
    if (/^https?:\/\/\S+$/i.test(t)) {
      return { subscription: await this.addSubscription(t, '', { fetchImpl }) }
    }
    const configs = this.parser.parseBundle(t)
    if (!configs.length) throw new Error('کانفیگ قابل شناسایی پیدا نشد')
    const fresh = configs.map(c => ({ ...c, id: c.id || crypto.randomUUID(), subId: MANUAL }))
    this.data.configs = [...this.data.configs, ...fresh]
    if (!this.data.selectedId) this.data.selectedId = fresh[0].id
    this.save()
    return { added: fresh.length }
  }

  async addSubscription(url, name = '', { fetchImpl = fetch, kind = 'subscription', id } = {}) {
    const existing = this.data.subscriptions.find(s => s.url === url)
    const sub = existing || { id: id || crypto.randomUUID(), name: name || 'اشتراک', url, kind }
    if (!existing) this.data.subscriptions.push(sub)
    if (name) sub.name = name
    await this.refreshSubscription(sub.id, { fetchImpl })
    return sub
  }

  async refreshSubscription(id, { fetchImpl = fetch } = {}) {
    const sub = this.data.subscriptions.find(s => s.id === id)
    if (!sub || !sub.url) return 0
    const res = await fetchImpl(sub.url, { headers: { 'User-Agent': 'v2rayN/7.0', Accept: '*/*' }, redirect: 'follow' })
    if (!res.ok) throw new Error(`اشتراک پاسخ نداد (${res.status})`)
    const body = await res.text()
    const configs = this.parser.parseBundle(body)
    if (!configs.length) throw new Error('اشتراک کانفیگی برنگرداند')
    const info = parseUserInfo(res.headers.get('subscription-userinfo'))
    const title = titleFrom(res.headers.get('profile-title'))
    Object.assign(sub, info, { lastUpdated: Date.now() }, title && !sub.nameLocked ? { name: title } : {})
    // Keep favourites and the selection across a refresh when the same server comes back.
    const key = c => `${c.protocol}|${c.address}|${c.port}|${c.uuid || c.password}|${c.name}`
    const old = new Map(this.data.configs.filter(c => c.subId === id).map(c => [key(c), c]))
    const next = configs.map(c => { const prev = old.get(key(c)); return { ...c, id: prev ? prev.id : crypto.randomUUID(), favorite: prev ? !!prev.favorite : false, subId: id } })
    this.data.configs = [...this.data.configs.filter(c => c.subId !== id), ...next]
    if (!this.config(this.data.selectedId)) this.data.selectedId = next[0] ? next[0].id : ''
    this.save()
    return next.length
  }

  async refreshAll({ fetchImpl = fetch } = {}) {
    const results = []
    for (const s of this.data.subscriptions) {
      if (s.kind === FREE) continue
      try { results.push({ id: s.id, count: await this.refreshSubscription(s.id, { fetchImpl }) }) }
      catch (e) { results.push({ id: s.id, error: String(e.message || e) }) }
    }
    return results
  }

  /** Replaces the free group with freshly tested configs (best first). */
  setFree(configs, name = 'رایگان قاجار') {
    let sub = this.data.subscriptions.find(s => s.id === FREE)
    if (!sub) { sub = { id: FREE, name, url: '', kind: FREE }; this.data.subscriptions.push(sub) }
    sub.lastUpdated = Date.now()
    this.data.configs = [...this.data.configs.filter(c => c.subId !== FREE), ...configs.map(c => ({ ...c, id: c.id || crypto.randomUUID(), subId: FREE, source: 'COMMUNITY' }))]
    this.data.freeUpdated = Date.now()
    this.save()
  }

  /** Changes fields of one config (e.g. the username / password an .ovpn asks for). */
  updateConfig(id, patch) {
    const c = this.config(id)
    if (!c) return null
    const { id: _id, subId: _sub, ...rest } = patch || {}
    Object.assign(c, rest)
    this.save()
    return c
  }

  removeConfig(id) { this.data.configs = this.data.configs.filter(c => c.id !== id); this.save() }
  removeSubscription(id) {
    this.data.subscriptions = this.data.subscriptions.filter(s => s.id !== id)
    this.data.configs = this.data.configs.filter(c => c.subId !== id)
    this.save()
  }
  renameSubscription(id, name) { const s = this.data.subscriptions.find(x => x.id === id); if (s) { s.name = name; s.nameLocked = true; this.save() } }
  toggleFavorite(id) { const c = this.config(id); if (c) { c.favorite = !c.favorite; this.save() } }

  setDelays(map) { this.data.delays = { ...this.data.delays, ...map }; this.save() }
  delays() { return this.data.delays || {} }

  /** Upserts one service delivered by the shop (subscription first, configs as a fallback). */
  async setService({ name, configs = [], subscriptionUrl = '' }, { fetchImpl = fetch } = {}) {
    if (subscriptionUrl) {
      try { return await this.addSubscription(subscriptionUrl, name, { fetchImpl }) } catch (e) { if (!configs.length) throw e }
    }
    const parsed = configs.flatMap(l => this.parser.parseBundle(l))
    if (!parsed.length) throw new Error('این سرویس هنوز کانفیگ قابل اتصال ندارد')
    const id = 'svc-' + crypto.createHash('sha1').update(name + configs.join('\n')).digest('hex').slice(0, 12)
    let sub = this.data.subscriptions.find(s => s.id === id)
    if (!sub) { sub = { id, name, url: '', kind: 'service' }; this.data.subscriptions.push(sub) }
    this.data.configs = [...this.data.configs.filter(c => c.subId !== id), ...parsed.map(c => ({ ...c, id: crypto.randomUUID(), subId: id }))]
    this.data.selectedId = this.data.configs.find(c => c.subId === id).id
    this.save()
    return sub
  }
}

module.exports = { Store, parseUserInfo, titleFrom, MANUAL, FREE }
