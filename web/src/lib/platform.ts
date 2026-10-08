/** Which device this is, for choosing the add-to-app list and the install help. */
export type Platform = 'ios' | 'android' | 'windows' | 'mac' | 'linux' | 'other'

export function detectPlatform(): Platform {
  const nav = navigator as Navigator & { userAgentData?: { platform?: string } }
  const p = (nav.userAgentData?.platform ?? '').toLowerCase()
  const ua = navigator.userAgent.toLowerCase()
  if (/iphone|ipad|ipod/.test(ua)) return 'ios'
  // iPadOS reports itself as a Mac with touch.
  if (/macintosh/.test(ua) && navigator.maxTouchPoints > 1) return 'ios'
  if (p === 'android' || /android/.test(ua)) return 'android'
  if (p === 'windows' || /windows/.test(ua)) return 'windows'
  if (p === 'macos' || /mac os x|macintosh/.test(ua)) return 'mac'
  if (p === 'linux' || /linux|cros/.test(ua)) return 'linux'
  return 'other'
}

export const PlatformNames: Record<Platform, string> = {
  ios: 'آیفون و آیپد', android: 'اندروید', windows: 'ویندوز', mac: 'مک', linux: 'لینوکس', other: 'سایر'
}

/** The Android app's bridge (window.ghajarNative), when the page runs inside it. */
export interface NativeBridge {
  platform?: string
  notify?: (id: string, title: string, body: string, route: string, important: boolean) => void
  setToken?: (token: string) => void
  saveFile?: (name: string, mime: string, text: string) => void
  share?: (title: string, text: string) => void
  openPayment?: (url: string) => void
}
export function nativeBridge(): NativeBridge | null {
  return (window as Window & { ghajarNative?: NativeBridge }).ghajarNative ?? null
}
/** Inside the Ghajar Android app. */
export function isAndroidApp(): boolean { return !!nativeBridge() }

/** The desktop app's VPN engine, when the page runs inside it. */
export type CoreMode = 'proxy' | 'system' | 'apps' | 'tun'
export interface CoreStatus {
  available: boolean; connected: boolean; connecting: boolean; error: string; note?: string
  mode?: CoreMode; engine?: string; since?: number; selectedId?: string; osVpn?: boolean; bootstrap?: number
  socks: { host: string; port: number } | null; http: { host: string; port: number } | null
  server: { id?: string; name: string; protocol: string } | null; service: string; selected: number; directIran: boolean
}
export interface CoreServer { index: number; id?: string; name: string; protocol: string; host: string; port: number }
export interface CoreConfig { id: string; name: string; protocol: string; address: string; port: number; favorite: boolean; delay: number | null; family: string; needsCredentials?: boolean }
export interface CoreGroup { id: string; name: string; url?: string; kind: string; used?: number; total?: number; expire?: number; lastUpdated?: number; configs: CoreConfig[] }
export interface CoreSettings { mode: CoreMode; iranDirect: boolean; autoFastest: boolean; apps?: string[]; bypassApps?: string[]; [key: string]: unknown }
export interface CoreCapabilities { xray: boolean; singbox: boolean; psiphon: boolean; tor: boolean; tun: boolean; apps?: boolean; torBridges?: boolean; aether?: boolean; openvpn?: boolean; ikev2?: boolean; helpers: Record<string, boolean> }
export interface DesktopCore {
  version?: number
  status(): Promise<CoreStatus>
  servers(): Promise<CoreServer[]>
  setService(s: { name: string; configs: string[]; subscriptionUrl: string }): Promise<CoreServer[]>
  connect(target?: number | string): Promise<CoreStatus>
  disconnect(): Promise<CoreStatus>
  ping(): Promise<{ index: number; ms: number }[]>
  setDirectIran(on: boolean): Promise<CoreStatus>
  onStatus(cb: (s: CoreStatus) => void): () => void
  // Engine v2: the full server screen.
  capabilities?(): Promise<CoreCapabilities>
  groups?(): Promise<CoreGroup[]>
  settings?(): Promise<CoreSettings>
  setSettings?(patch: Partial<CoreSettings>): Promise<CoreSettings>
  add?(text: string): Promise<{ added?: number; subscription?: { id: string } }>
  addSubscription?(url: string, name?: string): Promise<{ id: string }>
  refreshSubscriptions?(): Promise<{ id: string; count?: number; error?: string }[]>
  renameSubscription?(id: string, name: string): Promise<CoreGroup[]>
  removeSubscription?(id: string): Promise<CoreGroup[]>
  removeConfig?(id: string): Promise<CoreGroup[]>
  favorite?(id: string): Promise<CoreGroup[]>
  select?(id: string): Promise<CoreStatus>
  shareLink?(id: string): Promise<string | null>
  test?(ids?: string[]): Promise<Record<string, number>>
  fastest?(): Promise<string>
  refreshFree?(): Promise<number>
  addWarp?(): Promise<string>
  addPsiphon?(country?: string): Promise<string>
  runningApps?(): Promise<{ name: string }[]>
  setCredentials?(id: string, username: string, password: string): Promise<CoreGroup[]>
  addTor?(opts: { country?: string; throughVpn?: boolean; bridges?: string }): Promise<string>
  torCountries?(): Promise<[string, string][]>
  addAether?(opts: { mode?: string; exitLoc?: string; http2?: boolean; fragment?: boolean }): Promise<string>
  submitAetherCode?(code: string): Promise<boolean>
}
export function desktopCore(): DesktopCore | null {
  return (window as Window & { ghajarDesktop?: { core?: DesktopCore } }).ghajarDesktop?.core ?? null
}
/** An Electron IPC error reads "Error invoking remote method …: Error: <message>"; the message alone. */
export function coreError(e: unknown): string {
  const m = String((e as Error)?.message ?? e ?? '')
  return m.replace(/^Error invoking remote method '[^']*': (Error: )?/, '') || 'اتصال برقرار نشد'
}

/** Running inside the Ghajar desktop app (Windows/macOS/Linux), which sets this from its preload. */
export function isDesktopApp(): boolean {
  return !!(window as Window & { ghajarDesktop?: unknown }).ghajarDesktop
}

export function isStandalone(): boolean {
  if (isDesktopApp() || isAndroidApp()) return true
  try {
    return window.matchMedia('(display-mode: standalone)').matches ||
      window.matchMedia('(display-mode: window-controls-overlay)').matches ||
      (navigator as Navigator & { standalone?: boolean }).standalone === true
  } catch { return false }
}

/** iOS Safari proper (not Chrome/Firefox for iOS, which cannot add a web app to the home screen before iOS 16.4). */
export function isIosSafari(): boolean {
  const ua = navigator.userAgent
  return detectPlatform() === 'ios' && /safari/i.test(ua) && !/crios|fxios|edgios|opios/i.test(ua)
}

export function iosVersion(): number | null {
  const m = navigator.userAgent.match(/OS (\d+)[._](\d+)/)
  return m ? parseFloat(`${m[1]}.${m[2]}`) : null
}

export function inTelegram(): boolean {
  return !!(window as Window & { Telegram?: { WebApp?: unknown } }).Telegram?.WebApp || /telegram/i.test(navigator.userAgent)
}
