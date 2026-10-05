import { useEffect, useState } from 'preact/hooks'

/**
 * A minimal observable value, the web counterpart of the StateFlow objects the
 * Android app shares between screens (GhajarNoticeBus, GhajarShopStatus …).
 */
export interface Store<T> {
  get(): T
  set(value: T): void
  update(f: (value: T) => T): void
  subscribe(listener: (value: T) => void): () => void
}

export function createStore<T>(initial: T): Store<T> {
  let value = initial
  const listeners = new Set<(value: T) => void>()
  return {
    get: () => value,
    set(next) {
      if (Object.is(next, value)) return
      value = next
      listeners.forEach(l => l(value))
    },
    update(f) { this.set(f(value)) },
    subscribe(listener) {
      listeners.add(listener)
      return () => { listeners.delete(listener) }
    }
  }
}

export function useStore<T>(store: Store<T>): T {
  const [value, setValue] = useState(store.get())
  useEffect(() => {
    setValue(store.get())
    return store.subscribe(setValue)
  }, [store])
  return value
}

/** localStorage that never throws: private windows and blocked storage just forget. */
export const safeStorage = {
  get(key: string): string | null {
    try { return localStorage.getItem(key) } catch { return null }
  },
  set(key: string, value: string): void {
    try { localStorage.setItem(key, value) } catch { /* storage unavailable */ }
  },
  remove(key: string): void {
    try { localStorage.removeItem(key) } catch { /* storage unavailable */ }
  },
  json<T>(key: string, fallback: T): T {
    const raw = this.get(key)
    if (!raw) return fallback
    try { return JSON.parse(raw) as T } catch { return fallback }
  }
}
