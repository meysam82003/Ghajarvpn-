import * as api from '../api/client'
import * as M from '../api/models'
import { createStore, safeStorage } from '../lib/store'
import { isLinked } from '../api/account'

/**
 * The account's services, shared by the shop and the home screen - the
 * home screen's "route" and quota card read the selected one, the way the
 * app's home reads the selected server and its subscription.
 */
export const ownedStore = createStore<M.OwnedService[]>([])
export const ownedLoaded = createStore<boolean>(false)
export const selectedServiceStore = createStore<string | null>(safeStorage.get('ghajar.selected.service'))

export function selectService(username: string | null): void {
  selectedServiceStore.set(username)
  if (username) safeStorage.set('ghajar.selected.service', username)
  else safeStorage.remove('ghajar.selected.service')
}

let inflight: Promise<void> | null = null

export function refreshOwned(): Promise<void> {
  if (!isLinked()) { ownedStore.set([]); ownedLoaded.set(false); return Promise.resolve() }
  if (inflight) return inflight
  inflight = (async () => {
    try {
      const list = await api.ownedServices()
      ownedStore.set(list)
      ownedLoaded.set(true)
      const sel = selectedServiceStore.get()
      if (!sel || !list.some(s => s.username === sel)) {
        const first = M.sortedFor(list, 'ACTIVE')[0] ?? M.sortedFor(list, 'NEWEST')[0]
        selectService(first?.username ?? null)
      }
    } finally {
      inflight = null
    }
  })()
  return inflight
}

export function resetOwned(): void {
  ownedStore.set([]); ownedLoaded.set(false)
}
