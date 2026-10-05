import { createStore, safeStorage } from '../lib/store'
import { idbSet, idbDelete } from '../lib/idb'

/**
 * GhajarAccountStore: the bearer token and the pending link session.
 *
 * The token is mirrored into IndexedDB because the service worker - which
 * shows notifications while the app is closed - cannot read localStorage.
 */
export interface LinkSession {
  code: string
  sessionToken: string
  botUsername: string
  expiresInSeconds: number
  expiresAtMillis: number
}

const TOKEN_KEY = 'ghajar.token.v1'
const LINK_KEY = 'ghajar.link.v1'

export const tokenStore = createStore<string>(safeStorage.get(TOKEN_KEY) ?? '')

export function token(): string { return tokenStore.get() }
export function isLinked(): boolean { return token().trim() !== '' }

export function saveToken(value: string): boolean {
  safeStorage.set(TOKEN_KEY, value)
  tokenStore.set(value)
  void idbSet('token', value)
  return safeStorage.get(TOKEN_KEY) === value || tokenStore.get() === value
}

export function clearAccount(): void {
  safeStorage.remove(TOKEN_KEY)
  safeStorage.remove(LINK_KEY)
  tokenStore.set('')
  void idbDelete('token')
}

export function pendingLink(): LinkSession | null {
  const s = safeStorage.json<LinkSession | null>(LINK_KEY, null)
  if (!s || !s.code || !s.sessionToken) return null
  return s
}

export function savePendingLink(s: LinkSession): boolean {
  safeStorage.set(LINK_KEY, JSON.stringify(s))
  return pendingLink()?.sessionToken === s.sessionToken
}

export function clearPendingLink(): void { safeStorage.remove(LINK_KEY) }

/** A short, stable key for this account, for per-account local state (never the token itself). */
export async function accountKey(): Promise<string | null> {
  const t = token()
  if (!t) return null
  try {
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(t))
    return [...new Uint8Array(digest)].slice(0, 12).map(b => b.toString(16).padStart(2, '0')).join('')
  } catch {
    let h = 0
    for (let i = 0; i < t.length; i++) h = (h * 31 + t.charCodeAt(i)) | 0
    return 'h' + (h >>> 0).toString(16)
  }
}

// Mirror the current token for the service worker on start.
if (token()) void idbSet('token', token())
