import { useEffect, useState } from 'preact/hooks'
import { useStore } from '../lib/store'
import { Icon } from '../components/Icon'
import { Slab, SlabRow, SlabDivider, Rail, ScreenHeader, StatStrip, PillButton, GhostPill, SkinSwitch, TextButton, SlidingSegments } from '../components/Skin'
import { Dialog } from '../components/Overlay'
import { openSettings, SettingsPage, goTab, toast } from '../state/ui'
import { tokenStore, clearAccount } from '../api/account'
import { ownedStore, resetOwned } from '../state/shop'
import { unreadStore, notifyPrefs, setNotifyPrefs, refreshNotices, resetNotices } from '../state/notices'
import { checkout, resetCheckout } from '../state/checkout'
import { lookStore, setLook, resetLook, LookPresets, LookTransitions, LookNavStyles, LookOrbStyles } from '../theme/look'
import { Palettes, ThemeNames, ThemeId, paletteFor } from '../theme/palette'
import { installPrompt, installed, promptInstall, pushState, enablePush, disablePush, testPush, syncPushPrefs, pushSupported, refreshPushState, updateReady, applyUpdate } from '../lib/pwa'
import { detectPlatform, isDesktopApp, isAndroidApp, isIosSafari, iosVersion, isStandalone, inTelegram, PlatformNames } from '../lib/platform'
import { fa, formatPrice } from '../lib/format'
import { APP_VERSION, TELEGRAM_CHANNEL_URL, TELEGRAM_BOT_URL, GITHUB_URL } from '../api/config'

function desktopVersion(): string {
  return String((window as Window & { ghajarDesktop?: { appVersion?: string } }).ghajarDesktop?.appVersion ?? '')
}
import { openExternal } from './Market'
import { ConnectOrb } from './Home'

interface Tile { id: string; title: string; sub?: string; icon: string; badge?: string; active?: boolean; onClick: () => void }

function TileGrid(props: { tiles: Tile[] }) {
  return (
    <div class="tile-grid">
      {props.tiles.map(t => (
        <button class="tile" onClick={t.onClick}>
          <div class="row">
            <span class="tbox"><Icon name={t.icon} size={20} color="var(--primary)" /></span>
            <span class="spacer" />
            {t.active != null ? <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: t.active ? 'var(--good)' : 'color-mix(in srgb, var(--muted) 50%, transparent)' }} /> : null}
            {t.badge ? <span class="tbadge" style={{ marginInlineStart: '6px' }}>{t.badge}</span> : null}
          </div>
          <span class="ttitle clamp2">{t.title}</span>
          {t.sub ? <span class="tsub clamp2">{t.sub}</span> : null}
        </button>
      ))}
    </div>
  )
}

/** SettingsScreen: places to go, as tiles under rails. */
export function SettingsScreen(props: { page: SettingsPage }) {
  switch (props.page) {
    case 'personalize': return <PersonalizeScreen />
    case 'notifications': return <NotificationSettingsScreen />
    case 'about': return <AboutScreen />
    case 'install': return <InstallScreen />
    case 'account': return <AccountScreen />
    default: return <SettingsHome />
  }
}

function SettingsHome() {
  const linked = useStore(tokenStore) !== ''
  const owned = useStore(ownedStore)
  const unread = useStore(unreadStore)
  const ck = useStore(checkout)
  const push = useStore(pushState)
  const inst = useStore(installed)
  const upd = useStore(updateReady)
  return (
    <div class="page pad-lg flow">
      <ScreenHeader title="تنظیمات" context="ظاهر، اعلان‌ها، حساب و نصب برنامه" />
      <StatStrip cells={[
        { label: 'سرویس‌ها', value: fa(owned.length), accent: 'var(--primary)', onClick: () => goTab('shop') },
        { label: 'پیام‌ها', value: fa(unread), accent: 'var(--info)', onClick: () => goTab('shop') },
        { label: 'کیف پول', value: ck.methods ? formatPrice(ck.methods.balance) : '—', accent: 'var(--premium)', onClick: () => goTab('shop') }
      ]} />
      {upd ? (
        <Slab accent="var(--primary)" spacing={8}>
          <span class="bold c-text">نسخهٔ تازهٔ برنامه آماده است</span>
          <PillButton text="به‌روزرسانی و بارگذاری دوباره" icon="refresh" onClick={applyUpdate} />
        </Slab>
      ) : null}
      <Rail label="برنامه" />
      <TileGrid tiles={[
        { id: 'personalize', title: 'شخصی‌سازی و ظاهر', sub: 'تم‌ها، رنگ‌ها و تایپوگرافی', icon: 'palette', onClick: () => openSettings('personalize') },
        { id: 'notifications', title: 'اعلان‌ها', sub: 'مجوز، دسته‌ها و هشدارهای هر دسته', icon: 'notifications', active: push === 'subscribed', onClick: () => openSettings('notifications') },
        { id: 'install', title: inst ? 'برنامه نصب شده' : 'نصب برنامه', sub: inst ? 'روی صفحهٔ اصلی این دستگاه' : 'افزودن به صفحهٔ اصلی، مثل یک اپ', icon: 'install_mobile', active: inst, onClick: () => openSettings('install') },
        { id: 'account', title: 'حساب فروشگاه', sub: linked ? 'متصل؛ خروج یا تعویض حساب' : 'اتصال با ربات تلگرام', icon: 'person', active: linked, onClick: () => (linked ? openSettings('account') : goTab('shop')) },
        { id: 'about', title: 'درباره', sub: 'نسخه، توسعه‌دهنده و حریم خصوصی', icon: 'info', onClick: () => openSettings('about') }
      ]} />
      <Rail label="پشتیبانی" />
      <TileGrid tiles={[
        { id: 'bot', title: 'ربات قاجار', sub: 'خرید، پشتیبانی و حساب', icon: 'support_agent', onClick: () => openExternal(TELEGRAM_BOT_URL) },
        { id: 'channel', title: 'کانال تلگرام', sub: 'اطلاعیه‌ها و خبرها', icon: 'campaign', onClick: () => openExternal(TELEGRAM_CHANNEL_URL) }
      ]} />
    </div>
  )
}

function Section(props: { title: string; children: any }) {
  return <><Rail label={props.title} /><Slab spacing={10}>{props.children}</Slab></>
}

function Choice<T extends string>(props: { options: [T, string][]; value: T; onChange: (v: T) => void }) {
  return (
    <div class="chips">
      {props.options.map(([k, l]) => <button class={'chip' + (props.value === k ? ' active' : '')} onClick={() => props.onChange(k)}><span>{l}</span></button>)}
    </div>
  )
}

function SwitchRow(props: { title: string; sub?: string; checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <div class="row gap-md full" style={{ padding: '4px 0' }}>
      <div class="col grow"><span class="c-text" style={{ fontWeight: 500 }}>{props.title}</span>{props.sub ? <span class="label-small c-text2">{props.sub}</span> : null}</div>
      <SkinSwitch checked={props.checked} onChange={props.onChange} label={props.title} />
    </div>
  )
}

/** PersonalizeScreen: the options of GhajarPersonalize that mean something on the web. */
function PersonalizeScreen() {
  const look = useStore(lookStore)
  const systemDark = (() => { try { return matchMedia('(prefers-color-scheme: dark)').matches } catch { return true } })()
  const themes: ThemeId[] = ['PREMIUM_GREEN_DARK', 'PREMIUM_GREEN_LIGHT', 'MIDNIGHT_BLUE', 'GRAPHITE_GOLD', 'SYSTEM']
  const [confirmReset, setConfirmReset] = useState(false)
  return (
    <div class="page pad-lg flow">
      <ScreenHeader title="شخصی‌سازی و ظاهر" context="تم‌ها، رنگ‌ها و تایپوگرافی" />
      <Section title="تم پایه">
        <div class="swatches">
          {themes.map(id => {
            const p = paletteFor(id, systemDark)
            return (
              <button class={'swatch' + (look.theme === id ? ' on' : '')} style={{ background: p.card }} onClick={() => setLook({ theme: id })}>
                <span class="dots"><i style={{ background: p.background, outline: `1px solid ${p.border}` }} /><i style={{ background: p.primary }} /><i style={{ background: p.highlight }} /></span>
                <span class="label-medium" style={{ color: p.textPrimary }}>{ThemeNames[id]}</span>
              </button>
            )
          })}
        </div>
      </Section>
      <Section title="پریست‌های رنگ">
        <div class="swatches">
          {LookPresets.map(p => (
            <button class={'swatch' + (look.preset === p.key ? ' on' : '')} style={{ background: p.card }} onClick={() => setLook({ preset: p.key })}>
              <span class="dots"><i style={{ background: p.bg, outline: `1px solid ${p.border}` }} /><i style={{ background: p.accent }} /><i style={{ background: p.secondaryCard }} /></span>
              <span class="label-medium" style={{ color: '#E9F4EF' }}>{p.fa}</span>
            </button>
          ))}
        </div>
        <div class="row gap-md">
          <span class="grow c-text">رنگ تأکید دلخواه</span>
          <input class="color-input" type="color" value={look.accent ?? Palettes[0].primary} onInput={e => setLook({ accent: (e.target as HTMLInputElement).value })} aria-label="رنگ تأکید" />
          {look.accent ? <TextButton onClick={() => setLook({ accent: null })}>پیش‌فرض</TextButton> : null}
        </div>
        <SwitchRow title="AMOLED مشکی" sub="پس‌زمینهٔ کاملاً سیاه در تم‌های تیره" checked={look.amoled} onChange={v => setLook({ amoled: v })} />
      </Section>
      <Section title="دکمهٔ اتصال">
        <div class="row" style={{ justifyContent: 'center', overflow: 'hidden' }}><ConnectOrb style={look.orbStyle} enabled working={false} label="اتصال" onClick={() => undefined} diameter={170} /></div>
        <Choice options={LookOrbStyles as [string, string][]} value={look.orbStyle} onChange={v => setLook({ orbStyle: v })} />
      </Section>
      <Section title="نوار پایین">
        <Choice options={LookNavStyles as [string, string][]} value={look.navStyle} onChange={v => setLook({ navStyle: v })} />
        <Choice options={[['rounded', 'گرد'], ['filled', 'پر'], ['outline', 'خطی'], ['dot', 'نقطه']]} value={look.navIndicator} onChange={v => setLook({ navIndicator: v })} />
        <SwitchRow title="نمایش برچسب‌ها" checked={look.navLabels} onChange={v => setLook({ navLabels: v })} />
        <label class="col gap-xs"><span class="label-medium c-text2">اندازهٔ آیکن: {fa(look.navIconSize)}</span>
          <input class="range" type="range" min={18} max={30} value={look.navIconSize} onInput={e => setLook({ navIconSize: +(e.target as HTMLInputElement).value })} /></label>
        <label class="col gap-xs"><span class="label-medium c-text2">گردی گوشه‌ها: {fa(look.navRadius)}</span>
          <input class="range" type="range" min={0} max={40} value={look.navRadius} onInput={e => setLook({ navRadius: +(e.target as HTMLInputElement).value })} /></label>
      </Section>
      <Section title="کارت‌ها و چیدمان">
        <label class="col gap-xs"><span class="label-medium c-text2">گردی کارت‌ها: {fa(look.cardRadius)}</span>
          <input class="range" type="range" min={4} max={36} value={look.cardRadius} onInput={e => setLook({ cardRadius: +(e.target as HTMLInputElement).value })} /></label>
        <label class="col gap-xs"><span class="label-medium c-text2">سایه: {fa(look.elevation)}</span>
          <input class="range" type="range" min={0} max={12} value={look.elevation} onInput={e => setLook({ elevation: +(e.target as HTMLInputElement).value })} /></label>
        <Choice options={[['compact', 'فشرده'], ['comfortable', 'راحت'], ['spacious', 'باز']]} value={look.density} onChange={v => setLook({ density: v })} />
        <span class="label-medium c-text2">سبک تب‌های فروشگاه</span>
        <Choice options={[['pill', 'قرصی'], ['underline', 'زیرخط'], ['boxed', 'جعبه‌ای']]} value={look.storeTabStyle} onChange={v => setLook({ storeTabStyle: v })} />
      </Section>
      <Section title="تایپوگرافی">
        <label class="col gap-xs"><span class="label-medium c-text2">اندازهٔ متن: {fa(Math.round(look.fontScale * 100))}٪</span>
          <input class="range" type="range" min={85} max={125} value={Math.round(look.fontScale * 100)} onInput={e => setLook({ fontScale: +(e.target as HTMLInputElement).value / 100 })} /></label>
        <SwitchRow title="عنوان‌های پررنگ" checked={look.boldTitles} onChange={v => setLook({ boldTitles: v })} />
      </Section>
      <Section title="حرکت">
        <Choice options={LookTransitions as [string, string][]} value={look.transition} onChange={v => setLook({ transition: v })} />
        <SlidingSegments labels={['آهسته', 'عادی', 'سریع']} selected={look.speed === 'slow' ? 0 : look.speed === 'fast' ? 2 : 1}
          onSelect={i => setLook({ speed: i === 0 ? 'slow' : i === 2 ? 'fast' : 'normal' })} />
        <SwitchRow title="کاهش حرکت" sub="انیمیشن‌ها کوتاه و آرام می‌شوند" checked={look.reduceMotion} onChange={v => setLook({ reduceMotion: v })} />
      </Section>
      <GhostPill text="بازگشت همهٔ تنظیمات ظاهر به پیش‌فرض" icon="refresh" accent="var(--error)" onClick={() => setConfirmReset(true)} />
      {confirmReset ? <Dialog title="بازگشت به پیش‌فرض" onDismiss={() => setConfirmReset(false)}
        actions={<><TextButton onClick={() => setConfirmReset(false)}>انصراف</TextButton><TextButton onClick={() => { resetLook(); setConfirmReset(false); toast('ظاهر به پیش‌فرض برگشت') }}>بازگشت</TextButton></>}>
        همهٔ رنگ‌ها، چیدمان و حرکت‌ها به حالت پیش‌فرض قاجار برمی‌گردند.
      </Dialog> : null}
    </div>
  )
}

/** Notifications: permission, push on this device, and the three categories. */
function NotificationSettingsScreen() {
  const push = useStore(pushState)
  const prefs = useStore(notifyPrefs)
  const inst = useStore(installed)
  const [busy, setBusy] = useState(false)
  const platform = detectPlatform()
  const iosNeedsInstall = platform === 'ios' && !isStandalone()
  const iosTooOld = platform === 'ios' && (iosVersion() ?? 99) < 16.4
  useEffect(() => { void refreshPushState() }, [])
  const desktop = isDesktopApp()
  const android = isAndroidApp()
  const status = android ? 'اعلان‌ها روی این گوشی فعال است؛ وقتی برنامه بسته است هم هر چند دقیقه بررسی می‌شوند و روی نوار اعلان می‌آیند.' : desktop ? 'اعلان‌ها روی این کامپیوتر فعال است؛ تا وقتی قاجار باز است یا در سینی سیستم (کنار ساعت) است، می‌آیند.' : push === 'subscribed' ? 'اعلان‌های این دستگاه فعال هستند' : push === 'denied' ? 'اعلان برای این برنامه در تنظیمات دستگاه یا مرورگر بسته شده است.'
    : push === 'unsupported' ? (iosNeedsInstall ? 'در آیفون، اعلان فقط بعد از «افزودن به صفحهٔ اصلی» کار می‌کند.' : 'این مرورگر اعلان پس‌زمینه را پشتیبانی نمی‌کند.')
      : 'برای دریافت پیام‌ها خارج از برنامه، اعلان‌های دستگاه را فعال کن.'
  async function enable() {
    setBusy(true)
    const r = await enablePush(prefs as unknown as Record<string, boolean>)
    setBusy(false)
    if (r === 'subscribed') { toast('اعلان‌ها فعال شد'); void refreshNotices() }
    else if (r === 'local') { toast('اعلان‌ها فعال شد؛ وقتی برنامه باز است نمایش داده می‌شوند'); void refreshNotices() }
    else if (r === 'denied') toast('اجازهٔ اعلان داده نشد؛ از تنظیمات دستگاه آن را باز کن')
    else toast('این مرورگر اعلان را پشتیبانی نمی‌کند')
  }
  const setPref = (k: keyof typeof prefs, v: boolean) => {
    const next = { ...prefs, [k]: v }
    setNotifyPrefs(next)
    void syncPushPrefs(next as unknown as Record<string, boolean>)
  }
  return (
    <div class="page pad-lg flow">
      <ScreenHeader title="اعلان‌ها" context="مجوز، دسته‌ها و هشدارهای هر دسته" />
      <Slab spacing={10}>
        <span class="c-text">{status}</span>
        <span class="body-small">اعلان‌های جدید و شناور اینجا، در بنر بالای برنامه و در نوار اعلان دستگاه نمایش داده می‌شوند: هشدار حجم و زمان سرویس، پیام‌های فروشگاه، کد تخفیف، وضعیت پرداخت و پاسخ تیکت.</span>
        {iosNeedsInstall ? <PillButton text="راهنمای افزودن به صفحهٔ اصلی" icon="ios_share" onClick={() => openSettings('install')} /> : null}
        {iosTooOld ? <span class="label-small c-warning">برای اعلان در آیفون، iOS نسخهٔ ۱۶٫۴ یا بالاتر لازم است.</span> : null}
        {push !== 'subscribed' && pushSupported() && push !== 'denied'
          ? <PillButton text={busy ? 'در حال فعال‌سازی…' : 'فعال‌سازی اعلان دستگاه'} icon="notifications_active" enabled={!busy} onClick={enable} /> : null}
        {push === 'subscribed' ? <>
          <GhostPill text="ارسال اعلان آزمایشی" icon="send" onClick={async () => toast((await testPush()) ? 'اعلان آزمایشی فرستاده شد' : 'سرور اعلان هنوز نصب نشده؛ اعلان‌ها هنگام باز بودن برنامه می‌آیند')} />
          <GhostPill text="خاموش کردن اعلان این دستگاه" icon="notifications_off" accent="var(--error)" onClick={async () => { await disablePush(); toast('اعلان این دستگاه خاموش شد') }} />
        </> : null}
        {!inst && !desktop && !android && platform !== 'ios' ? <span class="label-small c-text2">برای دریافت مطمئن‌تر در پس‌زمینه، برنامه را نصب کن.</span> : null}
      </Slab>
      <Rail label="دسته‌های اعلان" />
      <Slab spacing={4}>
        <SwitchRow title="اعلان‌های عمومی" sub="پیام‌های فروشگاه و اطلاعیه‌ها" checked={prefs.general} onChange={v => setPref('general', v)} />
        <SlabDivider />
        <SwitchRow title="هشدار حجم و زمان سرویس" sub="پیش از تمام شدن حجم یا زمان" checked={prefs.service} onChange={v => setPref('service', v)} />
        <SlabDivider />
        <SwitchRow title="اعلان‌های مهم و شناور" sub="وضعیت فروشگاه و هشدارهای فوری" checked={prefs.important} onChange={v => setPref('important', v)} />
      </Slab>
    </div>
  )
}

/** Installing the PWA, per device: a real home-screen app, not a browser tab. */
function InstallScreen() {
  const prompt = useStore(installPrompt)
  const inst = useStore(installed)
  const platform = detectPlatform()
  const steps: Record<string, string[]> = {
    ios: isIosSafari() ? ['در پایین Safari دکمهٔ «اشتراک‌گذاری» (مربع با فلش رو به بالا) را بزن.', 'گزینهٔ «Add to Home Screen» یا «افزودن به صفحهٔ اصلی» را انتخاب کن.', '«Add» را بزن. آیکن قاجار روی صفحهٔ اصلی می‌نشیند و مثل یک اپ، تمام‌صفحه باز می‌شود.']
      : ['این صفحه را در Safari باز کن (آیفون فقط از Safari نصب می‌کند).', 'در Safari، «اشتراک‌گذاری» ← «Add to Home Screen» را بزن.', '«Add» را بزن تا قاجار روی صفحهٔ اصلی بیاید.'],
    android: ['منوی ⋮ مرورگر را باز کن.', '«نصب برنامه» یا «Install app» / «افزودن به صفحهٔ اصلی» را بزن.', '«نصب» را تأیید کن؛ قاجار در فهرست برنامه‌ها و صفحهٔ اصلی می‌آید.'],
    windows: ['در نوار آدرس Chrome یا Edge، آیکن نصب (صفحه با فلش) را بزن.', 'یا از منوی ⋮ گزینهٔ «نصب قاجار وی پی ان» را انتخاب کن.', 'قاجار در منوی Start و نوار وظیفه، مثل یک برنامه باز می‌شود.'],
    mac: ['در Chrome یا Edge آیکن نصب نوار آدرس را بزن؛ در Safari ۱۷ به بعد منوی File ← «Add to Dock».', 'نصب را تأیید کن.', 'قاجار در Launchpad و Dock، مثل یک برنامه باز می‌شود.'],
    linux: ['در Chrome یا Edge آیکن نصب نوار آدرس را بزن.', 'نصب را تأیید کن.', 'قاجار در فهرست برنامه‌ها می‌آید.'],
    other: ['از منوی مرورگر، «نصب برنامه» یا «افزودن به صفحهٔ اصلی» را بزن.']
  }
  return (
    <div class="page pad-lg flow">
      <ScreenHeader title={inst ? 'برنامه نصب شده' : 'نصب برنامه'} context={`روی ${PlatformNames[platform]}، مثل یک اپ واقعی`} />
      {inst ? (
        <Slab accent="var(--good)" spacing={8}>
          <div class="row gap-sm"><Icon name="check_circle" color="var(--good)" /><span class="bold c-text">قاجار روی این دستگاه نصب است</span></div>
          <span class="body-small c-text2">از آیکن روی صفحهٔ اصلی باز کن؛ تمام‌صفحه، بدون نوار آدرس و با اعلان روی نوار اعلانات دستگاه.</span>
        </Slab>
      ) : <>
        {inTelegram() ? <Slab accent="var(--warning)" spacing={6}><span class="bold c-text">داخل تلگرام نمی‌شود نصب کرد</span><span class="body-small c-text2">این صفحه را در مرورگر دستگاه (Safari، Chrome یا Edge) باز کن.</span></Slab> : null}
        {prompt ? <PillButton text="نصب قاجار روی این دستگاه" icon="install_mobile" onClick={async () => { if (await promptInstall()) toast('قاجار نصب شد') }} /> : null}
        <Slab spacing={10}>
          {(steps[platform] ?? steps.other).map((s, i) => (
            <div class="row gap-md" style={{ alignItems: 'flex-start' }}>
              <span class="label-large bold c-primary" style={{ width: '22px', height: '22px', borderRadius: '50%', background: 'var(--primary-14)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', flex: 'none' }}>{fa(i + 1)}</span>
              <span class="c-text grow">{s}</span>
            </div>
          ))}
        </Slab>
        <Slab spacing={6}>
          <span class="bold c-text">بعد از نصب</span>
          <span class="body-small c-text2">• تمام‌صفحه و بدون نوار آدرس باز می‌شود.</span>
          <span class="body-small c-text2">• اعلان حجم، زمان، پیام‌ها و کد تخفیف روی نوار اعلانات دستگاه می‌آید{platform === 'ios' ? ' (iOS ۱۶٫۴ به بعد)' : ''}.</span>
          <span class="body-small c-text2">• خرید، پرداخت و افزودن سرویس به اپ‌های VPN همین‌جا انجام می‌شود.</span>
        </Slab>
      </>}
    </div>
  )
}

function AccountScreen() {
  const linked = useStore(tokenStore) !== ''
  const [confirm, setConfirm] = useState(false)
  return (
    <div class="page pad-lg flow">
      <ScreenHeader title="حساب فروشگاه" context={linked ? 'این دستگاه به حساب تلگرام تو متصل است' : 'متصل نیست'} />
      <Slab spacing={0} padding={8}>
        <SlabRow title={linked ? 'حساب متصل و همگام است' : 'اتصال با ربات تلگرام'} subtitle={linked ? 'خرید، تمدید، کیف پول و اعلان‌ها فعال است' : 'برای خرید و دیدن سرویس‌ها'}
          icon={linked ? 'verified' : 'link'} chevron onClick={() => goTab('shop')} />
      </Slab>
      {linked ? <GhostPill text="خروج از حساب فروشگاه" icon="link_off" accent="var(--error)" onClick={() => setConfirm(true)} /> : null}
      {confirm ? <Dialog title="خروج از حساب فروشگاه" onDismiss={() => setConfirm(false)}
        actions={<><TextButton onClick={() => setConfirm(false)}>انصراف</TextButton><TextButton onClick={() => {
          setConfirm(false); void disablePush(); clearAccount(); resetOwned(); resetNotices(); resetCheckout(); toast('از حساب فروشگاه خارج شدی'); goTab('shop')
        }}>خروج</TextButton></>}>
        این دستگاه از حساب فعلی جدا می‌شود و اعلان‌های این حساب روی آن خاموش می‌شود. سرویس‌هایی که در اپ‌های VPN اضافه کرده‌ای پاک نمی‌شوند. برای ورود دوباره به یک کد تازه از ربات نیاز داری.
      </Dialog> : null}
    </div>
  )
}

function AboutScreen() {
  return (
    <div class="page pad-lg flow">
      <ScreenHeader title="درباره" context="نسخه، توسعه‌دهنده و حریم خصوصی" />
      <Slab spacing={8}>
        <span class="title-medium bold c-text">قاجار وی پی ان</span>
        <span class="label-medium c-text2">{desktopVersion() ? `نسخهٔ کامپیوتر · ${fa(desktopVersion())}` : `نسخهٔ وب و قابل نصب · ${fa(APP_VERSION)}`}</span>
        <span class="body-small c-text2">همان فروشگاه، همان حساب و همان سرویس‌های برنامهٔ اندروید؛ روی آیفون، اندروید، ویندوز، مک و هر مرورگر. سرویس‌ها با یک لمس به اپ VPN همان دستگاه اضافه می‌شوند.</span>
      </Slab>
      <Rail label="حریم خصوصی" />
      <Slab spacing={6}>
        <span class="body-small c-text2">• نشانی سرورها و دامنه‌ها داخل برنامه نوشته نشده و همهٔ درخواست‌ها از مسیر خود سایت می‌روند.</span>
        <span class="body-small c-text2">• توکن حساب فقط روی همین دستگاه نگه داشته می‌شود؛ رسید پرداخت فقط برای بررسی همان سفارش فرستاده می‌شود.</span>
        <span class="body-small c-text2">• لینک اشتراک و QR خصوصی‌اند؛ فقط با فرد مورد اعتماد به اشتراک بگذار.</span>
      </Slab>
      <Slab spacing={0} padding={8}>
        <SlabRow title="کانال تلگرام" icon="campaign" chevron onClick={() => openExternal(TELEGRAM_CHANNEL_URL)} />
        <SlabDivider />
        <SlabRow title="ربات و پشتیبانی" icon="support_agent" chevron onClick={() => openExternal(TELEGRAM_BOT_URL)} />
        <SlabDivider />
        <SlabRow title="سورس و نسخه‌های اندروید" icon="open_in_new" chevron onClick={() => openExternal(GITHUB_URL)} />
      </Slab>
    </div>
  )
}
