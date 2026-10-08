import { desktopCore } from '../lib/platform'
import * as api from '../api/client'
import * as M from '../api/models'
import { ownedStore } from './shop'

/**
 * The desktop app's server list carries every purchased service as its own
 * subscription group (like the phone app imports them), kept up to date
 * without a button: on start, whenever the account's services change, and
 * when the server screen opens.
 */
let running: Promise<void> | null = null
let lastKey = ''
let lastAt = 0

export function syncOwnedServices(force = false): Promise<void> {
  const core = desktopCore()
  if (!core?.groups) return Promise.resolve()
  const owned = ownedStore.get().filter(s => !M.isEnded(s))
  const key = owned.map(s => s.username).join(',')
  if (!force && key === lastKey && Date.now() - lastAt < 10 * 60_000) return running ?? Promise.resolve()
  if (running) return running
  running = (async () => {
    for (const s of owned) {
      try {
        const d = await api.service(s.username)
        if (!d.subscriptionUrl && !d.outputs.length) continue
        await core.setService({ name: d.productName || s.productName, configs: d.outputs, subscriptionUrl: d.subscriptionUrl ?? '' })
      } catch { /* this service stays as it was; the next sync tries again */ }
    }
    lastKey = key
    lastAt = Date.now()
  })().finally(() => { running = null })
  return running
}

/** Starts the automatic sync: now, and each time the account's services change. */
export function startDesktopSync(): void {
  if (!desktopCore()?.groups) return
  void syncOwnedServices(true)
  ownedStore.subscribe(() => { void syncOwnedServices() })
}
