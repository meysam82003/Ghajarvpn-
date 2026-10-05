/**
 * A one-table key/value store in IndexedDB, shared with the service worker
 * (public/sw.js opens the same database and store by name).
 */
const DB = 'ghajar-pwa'
const STORE = 'kv'

function open(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB, 1)
    req.onupgradeneeded = () => { req.result.createObjectStore(STORE) }
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}

async function run<T>(mode: IDBTransactionMode, f: (s: IDBObjectStore) => IDBRequest<T>): Promise<T | undefined> {
  try {
    const db = await open()
    return await new Promise<T | undefined>((resolve) => {
      const tx = db.transaction(STORE, mode)
      const req = f(tx.objectStore(STORE))
      req.onsuccess = () => resolve(req.result)
      req.onerror = () => resolve(undefined)
      tx.oncomplete = () => db.close()
    })
  } catch {
    return undefined
  }
}

export function idbGet<T>(key: string): Promise<T | undefined> {
  return run<T>('readonly', s => s.get(key) as IDBRequest<T>)
}

export async function idbSet(key: string, value: unknown): Promise<void> {
  await run('readwrite', s => s.put(value, key))
}

export async function idbDelete(key: string): Promise<void> {
  await run('readwrite', s => s.delete(key))
}
