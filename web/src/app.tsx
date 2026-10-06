import { useEffect, useState } from 'preact/hooks'
import { useStore } from './lib/store'
import { navStore, goTab, back, applyHash } from './state/ui'
import { lookStore } from './theme/look'
import { TopBar, NavBar, NoticeBanner } from './components/Shell'
import { ToastHost } from './components/Overlay'
import { HomeScreen } from './screens/Home'
import { ShopScreen } from './screens/Shop'
import { SettingsScreen } from './screens/Settings'
import { unreadStore } from './state/notices'
import { setBadge, installed, installPrompt, promptInstall } from './lib/pwa'
import { detectPlatform, isStandalone, inTelegram } from './lib/platform'
import { Icon } from './components/Icon'
import { TextButton } from './components/Skin'
import { safeStorage } from './lib/store'
import { openSettings } from './state/ui'

const TITLES: Record<string, string> = {
  personalize: 'شخصی‌سازی و ظاهر', notifications: 'اعلان‌ها', about: 'درباره', install: 'نصب برنامه', account: 'حساب فروشگاه'
}

/**
 * The window: the transparent top bar, the notice banner, the page, and the
 * floating bottom bar - GozarApp's Scaffold, minus the VPN-only screens.
 */
export function App() {
  const nav = useStore(navStore)
  const look = useStore(lookStore)
  const unread = useStore(unreadStore)

  useEffect(() => { setBadge(unread) }, [unread])
  useEffect(() => {
    const onHash = () => applyHash()
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [])

  useEffect(() => { window.scrollTo({ top: 0 }) }, [nav.tab, nav.settings])

  const title = nav.tab === 'home' ? null : nav.tab === 'shop' ? 'فروشگاه' : nav.settings ? TITLES[nav.settings] : 'تنظیمات'
  const onBack = nav.tab === 'settings' && nav.settings ? () => back() : null
  const pageKey = nav.tab + ':' + nav.settings

  return (
    <div class="app" data-transition={look.transition} data-speed={look.speed}>
      <TopBar title={title} onBack={onBack} />
      <NoticeBanner />
      <InstallHint />
      <main class="pages">
        {/* Each tab stays mounted, like the pager's neighbouring pages, so state survives switching. */}
        <section hidden={nav.tab !== 'home'} class={nav.tab === 'home' ? 'anim-enter' : ''} key={nav.tab === 'home' ? pageKey : 'home'}><HomeScreen /></section>
        <section hidden={nav.tab !== 'shop'} class={nav.tab === 'shop' ? 'anim-enter' : ''}><ShopScreen active={nav.tab === 'shop'} /></section>
        <section hidden={nav.tab !== 'settings'} class={nav.tab === 'settings' ? (nav.forward ? 'push-enter' : 'pop-enter') : ''} key={'settings:' + nav.settings}><SettingsScreen page={nav.settings} /></section>
      </main>
      <NavBar selected={nav.tab === 'home' ? 0 : nav.tab === 'shop' ? 1 : 2} items={[
        { icon: 'home', label: 'خانه', onSelect: () => goTab('home') },
        { icon: 'shop', label: 'فروشگاه', badge: unread > 0, onSelect: () => goTab('shop') },
        { icon: 'settings', label: 'تنظیمات', onSelect: () => goTab('settings') }
      ]} />
      <ToastHost />
    </div>
  )
}

/** A one-line nudge to install, until it is installed or dismissed. */
function InstallHint() {
  const inst = useStore(installed)
  const prompt = useStore(installPrompt)
  const [hidden, setHidden] = useState(safeStorage.get('ghajar.install.hint') === 'off')
  if (inst || isStandalone() || hidden || inTelegram()) return null
  const ios = detectPlatform() === 'ios'
  if (!ios && !prompt) return null
  return (
    <div class="notice-banner" style={{ borderColor: 'var(--primary-40)' }}>
      <div class="inner">
        <button class="main" onClick={async () => { if (prompt) await promptInstall(); else openSettings('install') }}>
          <Icon name={ios ? 'ios_share' : 'install_mobile'} size={22} color="var(--primary)" />
          <span class="col grow">
            <span class="semibold">قاجار را مثل یک اپ نصب کن</span>
            <span class="body-small c-text2">{ios ? 'در Safari: اشتراک‌گذاری ← افزودن به صفحهٔ اصلی؛ اعلان‌ها هم فعال می‌شوند.' : 'تمام‌صفحه، روی صفحهٔ اصلی و با اعلان روی نوار اعلانات.'}</span>
          </span>
        </button>
        <TextButton onClick={() => { safeStorage.set('ghajar.install.hint', 'off'); setHidden(true) }}>بعداً</TextButton>
      </div>
    </div>
  )
}
