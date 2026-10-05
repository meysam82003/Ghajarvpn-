import { Platform } from './platform'

/**
 * "Add to app": one tap hands a purchased service to a VPN client installed on
 * this device. One entry per app; adding an app is one line here.
 *
 * Formats, and where each was checked:
 *  - v2rayNG     v2rayng://install-sub?url=   (2dust/v2rayNG AndroidManifest + UrlSchemeActivity)
 *  - Hiddify     hiddify://install-config?url=&name=  (hiddify-app lib/utils/link_parsers.dart: any host + url=)
 *  - sing-box    sing-box://import-remote-profile?url=#name (sing-box docs, SFA/SFI)
 *  - Ghajar      intent with scheme happ → com.ghajarvpn.app, configs in data= as base64
 *                (GhajarCompatibilityImport.parseDeepLink reads url/config/data/fragment, base64 or plain)
 *  - the rest follow each app's published import link; see web/APP_LINKS.md.
 */

export interface AppLink {
  id: string
  name: string
  platforms: Platform[]
  /** Android package, for an intent:// link that falls back to the store when the app is missing. */
  androidPackage?: string
  store: Partial<Record<Platform, string>>
  /** Builds the link; null when this app cannot take this kind of service. */
  build: (sub: Sub) => string | null
  /** Only offered when the service has plain configs (not just a subscription). */
  needsConfigs?: boolean
  note?: string
}

export interface Sub { url: string | null; name: string; configs: string[] }

const e = encodeURIComponent

function b64(s: string): string {
  const bytes = new TextEncoder().encode(s)
  let bin = ''
  bytes.forEach(b => { bin += String.fromCharCode(b) })
  return btoa(bin)
}
function b64url(s: string): string { return b64(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '') }

const PLAY = (id: string) => `https://play.google.com/store/apps/details?id=${id}`
/** App Store search for the app's name: never a guessed product id. */
const APPSTORE = (name: string) => `itms-apps://search.itunes.apple.com/WebObjects/MZSearch.woa/wa/search?media=software&term=${encodeURIComponent(name)}`
const GHAJAR_RELEASES = 'https://github.com/meysam82003/Ghajarvpn-/releases/latest'

export const AppLinks: AppLink[] = [
  {
    id: 'ghajar-android', name: 'قاجار وی پی ان', platforms: ['android'], androidPackage: 'com.ghajarvpn.app',
    store: { android: GHAJAR_RELEASES }, needsConfigs: true,
    build: s => s.configs.length ? `happ://add?data=${e(b64(s.configs.join('\n')))}#${e(s.name)}` : null
  },
  { id: 'streisand', name: 'Streisand', platforms: ['ios', 'mac'], store: { ios: APPSTORE('Streisand'), mac: APPSTORE('Streisand') },
    build: s => s.url ? `streisand://import/${s.url}#${e(s.name)}` : null },
  { id: 'v2box', name: 'V2Box', platforms: ['ios', 'mac'], store: { ios: APPSTORE('V2Box'), mac: APPSTORE('V2Box') },
    build: s => s.url ? `v2box://install-sub?url=${e(s.url)}&name=${e(s.name)}` : null },
  { id: 'happ', name: 'Happ', platforms: ['ios', 'android', 'mac', 'windows'], androidPackage: 'com.happproxy',
    store: { ios: APPSTORE('Happ Proxy Utility'), android: PLAY('com.happproxy'), mac: APPSTORE('Happ Proxy Utility'), windows: 'https://www.happ.su/' },
    build: s => s.url ? `happ://add/${s.url}` : null },
  { id: 'hiddify', name: 'Hiddify', platforms: ['ios', 'android', 'windows', 'mac', 'linux'], androidPackage: 'app.hiddify.com',
    store: { ios: APPSTORE('Hiddify Proxy VPN'), android: PLAY('app.hiddify.com'), windows: 'https://github.com/hiddify/hiddify-app/releases/latest',
      mac: 'https://github.com/hiddify/hiddify-app/releases/latest', linux: 'https://github.com/hiddify/hiddify-app/releases/latest' },
    build: s => s.url ? `hiddify://install-config?url=${e(s.url)}&name=${e(s.name)}` : null },
  { id: 'v2raytun', name: 'v2RayTun', platforms: ['ios', 'android'], androidPackage: 'com.v2raytun.android',
    store: { ios: APPSTORE('v2RayTun'), android: PLAY('com.v2raytun.android') },
    build: s => s.url ? `v2raytun://import/${s.url}` : null },
  { id: 'foxray', name: 'FoXray', platforms: ['ios', 'mac'], store: { ios: APPSTORE('FoXray'), mac: APPSTORE('FoXray') },
    build: s => s.url ? `foxray://yiguo.dev/sub/add/?url=${e(s.url)}#${e(s.name)}` : null },
  { id: 'shadowrocket', name: 'Shadowrocket', platforms: ['ios', 'mac'], store: { ios: 'https://apps.apple.com/app/shadowrocket/id932747118', mac: 'https://apps.apple.com/app/shadowrocket/id932747118' },
    build: s => s.url ? `sub://${b64url(s.url)}#${e(s.name)}` : null },
  { id: 'v2rayng', name: 'v2rayNG', platforms: ['android'], androidPackage: 'com.v2ray.ang',
    store: { android: PLAY('com.v2ray.ang') },
    build: s => s.url ? `v2rayng://install-sub?url=${e(s.url)}&name=${e(s.name)}` : null },
  { id: 'singbox', name: 'sing-box', platforms: ['ios', 'android', 'mac'], androidPackage: 'io.nekohasekai.sfa',
    store: { ios: APPSTORE('sing-box VT'), android: PLAY('io.nekohasekai.sfa'), mac: APPSTORE('sing-box VT') },
    build: s => s.url ? `sing-box://import-remote-profile?url=${e(s.url)}#${e(s.name)}` : null },
  { id: 'karing', name: 'Karing', platforms: ['ios', 'android', 'windows', 'mac'], androidPackage: 'com.nebula.karing',
    store: { ios: APPSTORE('Karing'), android: PLAY('com.nebula.karing'), windows: 'https://github.com/KaringX/karing/releases/latest', mac: APPSTORE('Karing') },
    build: s => s.url ? `karing://install-config?url=${e(s.url)}&name=${e(s.name)}` : null },
  { id: 'nekobox', name: 'NekoBox', platforms: ['android'], androidPackage: 'moe.nb4a',
    store: { android: 'https://github.com/MatsuriDayo/NekoBoxForAndroid/releases/latest' },
    build: s => s.url ? `sn://subscription?url=${e(s.url)}&name=${e(s.name)}` : null },
  { id: 'clash-verge', name: 'Clash Verge', platforms: ['windows', 'mac', 'linux'],
    store: { windows: 'https://github.com/clash-verge-rev/clash-verge-rev/releases/latest', mac: 'https://github.com/clash-verge-rev/clash-verge-rev/releases/latest', linux: 'https://github.com/clash-verge-rev/clash-verge-rev/releases/latest' },
    build: s => s.url ? `clash://install-config?url=${e(s.url)}&name=${e(s.name)}` : null },
  { id: 'ghajar-desktop', name: 'قاجار وی پی ان (دسکتاپ)', platforms: ['windows', 'mac', 'linux'],
    store: { windows: GHAJAR_RELEASES, mac: GHAJAR_RELEASES, linux: GHAJAR_RELEASES },
    build: s => s.url ? `ghajarvpn://import?url=${e(s.url)}&name=${e(s.name)}` : null }
]

/** Apps for this device first, the rest under "all apps". */
export function appsFor(platform: Platform): AppLink[] {
  return AppLinks.filter(a => a.platforms.includes(platform))
}

/**
 * The link to actually open. On Android an intent:// link names the package,
 * so the right app opens even when several claim the scheme, and the Play
 * Store page opens when it is not installed.
 */
export function launchUrl(app: AppLink, sub: Sub, platform: Platform): string | null {
  const raw = app.build(sub)
  if (!raw) return null
  if (platform === 'android' && app.androidPackage) {
    const i = raw.indexOf(':')
    const scheme = raw.slice(0, i)
    // The intent syntax owns the fragment, so a "#name" suffix is dropped here;
    // every app above also reads the name from the query or the subscription.
    const rest = raw.slice(i + 1).replace(/^\/\//, '').split('#')[0]
    const fallback = app.store.android ? `;S.browser_fallback_url=${e(app.store.android)}` : ''
    return `intent://${rest}#Intent;scheme=${scheme};package=${app.androidPackage}${fallback};end`
  }
  return raw
}

/**
 * Opens a custom scheme from a user tap. Returns false when, after a moment,
 * the page is still in front - which on iOS and the desktop is the only sign
 * that no app took the link.
 */
export function openScheme(url: string): Promise<boolean> {
  return new Promise(resolve => {
    let left = false
    const onHide = () => { if (document.visibilityState === 'hidden') left = true }
    const onBlur = () => { left = true }
    document.addEventListener('visibilitychange', onHide)
    window.addEventListener('blur', onBlur)
    try { location.href = url } catch { /* blocked */ }
    setTimeout(() => {
      document.removeEventListener('visibilitychange', onHide)
      window.removeEventListener('blur', onBlur)
      resolve(left)
    }, 1600)
  })
}
