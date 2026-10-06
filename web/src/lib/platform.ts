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
}
export function nativeBridge(): NativeBridge | null {
  return (window as Window & { ghajarNative?: NativeBridge }).ghajarNative ?? null
}
/** Inside the Ghajar Android app. */
export function isAndroidApp(): boolean { return !!nativeBridge() }

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
