import { useEffect, useState } from 'preact/hooks'
import { DesktopCore, CoreStatus, CoreGroup, CoreSettings, CoreMode, CoreConfig, coreError } from '../lib/platform'
import { Slab, SlabRow, GhostPill, PillButton, SkinField, SkinSwitch, SlidingSegments, LinearProgress, TextButton } from './Skin'
import { Sheet, Dialog } from './Overlay'
import { Icon } from './Icon'
import { copyText, toast } from '../state/ui'
import { fa, formatBytes } from '../lib/format'

const MODES: { mode: CoreMode; label: string; hint: string }[] = [
  { mode: 'tun', label: 'کل دستگاه', hint: 'همهٔ برنامه‌های کامپیوتر از VPN رد می‌شوند (هر اتصال یک‌بار اجازهٔ مدیر سیستم می‌خواهد)' },
  { mode: 'apps', label: 'برنامه‌های انتخابی', hint: 'فقط برنامه‌هایی که انتخاب می‌کنی (مثلاً فقط Chrome یا Firefox) از VPN رد می‌شوند؛ بقیه مستقیم' },
  { mode: 'system', label: 'پراکسی سیستم', hint: 'مرورگرها و برنامه‌هایی که پراکسی ویندوز/مک/لینوکس را می‌خوانند وصل می‌شوند؛ بدون اجازهٔ مدیر' },
  { mode: 'proxy', label: 'پراکسی محلی', hint: 'فقط برنامه‌ای که دستی روی پراکسی 127.0.0.1 تنظیم شود وصل می‌شود' }
]

/** Common programs, by the process names each OS gives them. */
const APP_PRESETS: { label: string; names: string[] }[] = [
  { label: 'Google Chrome', names: ['chrome.exe', 'Google Chrome', 'chrome', 'google-chrome'] },
  { label: 'Firefox', names: ['firefox.exe', 'Firefox', 'firefox', 'firefox-bin'] },
  { label: 'Microsoft Edge', names: ['msedge.exe', 'Microsoft Edge', 'msedge'] },
  { label: 'Brave', names: ['brave.exe', 'Brave Browser', 'brave'] },
  { label: 'Opera', names: ['opera.exe', 'Opera', 'opera'] },
  { label: 'Telegram', names: ['Telegram.exe', 'Telegram', 'telegram-desktop', 'Telegram Desktop'] },
  { label: 'Discord', names: ['Discord.exe', 'Discord', 'discord'] },
  { label: 'Spotify', names: ['Spotify.exe', 'Spotify', 'spotify'] },
  { label: 'WhatsApp', names: ['WhatsApp.exe', 'WhatsApp'] },
  { label: 'Steam', names: ['steam.exe', 'steamwebhelper.exe', 'Steam', 'steam'] }
]

export function modeLabel(mode?: CoreMode): string {
  return MODES.find(m => m.mode === mode)?.label ?? 'متصل'
}

function delayText(ms: number | null | undefined): string {
  if (ms == null) return ''
  return ms < 0 ? 'قطع' : `${fa(ms)} ms`
}
function delayColor(ms: number | null | undefined): string {
  if (ms == null) return 'var(--muted)'
  return ms < 0 ? 'var(--error)' : ms < 700 ? 'var(--glow)' : ms < 1500 ? 'var(--warning)' : 'var(--error)'
}

/**
 * The desktop server screen, like the Android app's: every subscription and
 * manual config, real-delay test, the fastest server, the free sources
 * (Telegram configs, WARP, Psiphon) and how the computer is connected.
 */
export function DesktopServersSheet(props: { core: DesktopCore; status: CoreStatus | null; onDismiss: () => void; onError: (m: string) => void }) {
  const core = props.core
  const [groups, setGroups] = useState<CoreGroup[]>([])
  const [settings, setSettings] = useState<CoreSettings | null>(null)
  const [busy, setBusy] = useState<string>('')
  const [adding, setAdding] = useState(false)
  const [open, setOpen] = useState<Record<string, boolean>>({})
  const [menu, setMenu] = useState<CoreConfig | null>(null)
  const [picking, setPicking] = useState(false)
  const [creds, setCreds] = useState<CoreConfig | null>(null)
  const [torOpen, setTorOpen] = useState(false)
  const [aetherOpen, setAetherOpen] = useState(false)
  const [codeOpen, setCodeOpen] = useState(false)

  /** Connects, asking first for the username/password an OpenVPN profile needs. */
  function connectTo(c: CoreConfig) {
    if (c.needsCredentials) { setCreds(c); return }
    props.onDismiss()
    void core.connect(c.id).catch(e => props.onError(coreError(e)))
  }
  const selectedId = props.status?.selectedId ?? ''

  async function reload() {
    if (core.groups) setGroups(await core.groups())
    if (core.settings) setSettings(await core.settings())
  }
  useEffect(() => { void reload(); return core.onStatus(() => void reload()) }, [])

  async function run(label: string, fn: () => Promise<unknown>, done?: string) {
    setBusy(label)
    try { await fn(); if (done) toast(done) } catch (e) { props.onError(coreError(e)) } finally { setBusy(''); await reload() }
  }

  async function setMode(mode: CoreMode) {
    if (!core.setSettings) return
    setSettings(await core.setSettings({ mode }))
    if (props.status?.connected) await run('اتصال دوباره', () => core.connect())
  }

  const modeIndex = Math.max(0, MODES.findIndex(m => m.mode === (settings?.mode ?? 'tun')))
  const total = groups.reduce((n, g) => n + g.configs.length, 0)

  return (
    <Sheet title="سرورها و نوع اتصال" onDismiss={props.onDismiss}>
      <Slab spacing={8} padding={10}>
        <span class="label-large bold">نوع اتصال</span>
        <SlidingSegments labels={MODES.map(m => m.label)} selected={modeIndex} onSelect={i => void setMode(MODES[i].mode)} />
        <span class="label-small c-muted">{MODES[modeIndex].hint}</span>
        {MODES[modeIndex].mode === 'apps' ? (
          <SlabRow title="برنامه‌هایی که وصل می‌شوند" icon="apps" chevron onClick={() => setPicking(true)}
            subtitle={(settings?.apps ?? []).length ? appSummary(settings?.apps ?? []) : 'هنوز برنامه‌ای انتخاب نشده'} />
        ) : null}
      </Slab>

      <div class="row gap-sm wrap">
        <GhostPill text="افزودن کانفیگ یا اشتراک" icon="add" fillWidth={false} onClick={() => setAdding(true)} />
        <GhostPill text="تست همه" icon="speed" fillWidth={false} enabled={!busy && total > 0} onClick={() => run('در حال تست', () => core.test!(), 'تست تمام شد')} />
        <GhostPill text="سریع‌ترین" icon="bolt" fillWidth={false} enabled={!busy && total > 0}
          onClick={() => run('یافتن سریع‌ترین', async () => { const id = await core.fastest!(); if (!id) throw new Error('هیچ سروری جواب نداد'); await core.connect(id) }, 'به سریع‌ترین سرور وصل شد')} />
        <GhostPill text="بروزرسانی اشتراک‌ها" icon="refresh" fillWidth={false} enabled={!busy}
          onClick={() => run('بروزرسانی', async () => { const r = await core.refreshSubscriptions!(); const bad = r.filter(x => x.error); if (bad.length) throw new Error(bad[0].error) }, 'اشتراک‌ها بروز شد')} />
      </div>

      <Slab spacing={0} padding={6}>
        <SlabRow title="کانفیگ‌های رایگان" subtitle="از کانال‌های تلگرام، تست‌شده؛ فقط آن‌هایی که کار می‌کنند می‌مانند" icon="public" accent="var(--glow)"
          enabled={!busy} onClick={() => run('دریافت کانفیگ رایگان', async () => { const n = await core.refreshFree!(); if (!n) throw new Error('فعلاً کانفیگ رایگان سالمی پیدا نشد') }, 'کانفیگ‌های رایگان آمد')} />
        <SlabRow title="WARP رایگان کلادفلر" subtitle="WireGuard، با ثبت‌نام خودکار" icon="shield" enabled={!busy}
          onClick={() => run('ساخت WARP', async () => { const id = await core.addWarp!(); await core.select!(id) }, 'WARP اضافه شد')} />
        <SlabRow title="Psiphon رایگان" subtitle="بدون نیاز به سرور؛ خودش راه عبور پیدا می‌کند" icon="vpn_key" enabled={!busy}
          onClick={() => run('افزودن Psiphon', async () => { const id = await core.addPsiphon!(''); await core.select!(id) }, 'Psiphon اضافه شد')} />
        {core.addTor ? <SlabRow title="Tor" subtitle="شبکهٔ تور، با پل‌های obfs4 / snowflake / webtunnel و انتخاب کشور خروجی" icon="security" enabled={!busy} onClick={() => setTorOpen(true)} /> : null}
        {core.addAether ? <SlabRow title="Aether (WARP)" subtitle="WARP کلادفلر با MASQUE / WireGuard و اسکن خودکار" icon="bolt" enabled={!busy} onClick={() => setAetherOpen(true)} /> : null}
      </Slab>

      {busy ? <><span class="label-small c-muted">{busy}…</span><LinearProgress /></> : null}

      {groups.length === 0 ? <span class="c-muted">هنوز سروری نیست. سرویس خریداری‌شده خودش اینجا می‌آید؛ یا کانفیگ/اشتراک اضافه کن یا از رایگان‌ها بگیر.</span> : null}

      {groups.map(g => {
        const expanded = open[g.id] ?? (g.configs.some(c => c.id === selectedId) || groups.length === 1)
        const usage = g.total ? `${formatBytes(g.used ?? 0)} از ${formatBytes(g.total)}` : ''
        const days = g.expire ? Math.max(0, Math.ceil((g.expire * 1000 - Date.now()) / 86400000)) : null
        return (
          <Slab spacing={4} padding={6} accent={g.configs.some(c => c.id === selectedId) ? 'var(--primary)' : undefined}>
            <SlabRow title={g.name} subtitle={[`${fa(g.configs.length)} سرور`, usage, days != null ? `${fa(days)} روز` : ''].filter(Boolean).join(' · ')}
              icon={g.kind === 'free' ? 'public' : g.kind === 'manual' ? 'edit' : 'layers'}
              onClick={() => setOpen({ ...open, [g.id]: !expanded })}
              trailing={<span class="row gap-sm">
                {g.kind !== 'manual' ? <button class="icon-btn" aria-label="حذف گروه" onClick={e => { e.stopPropagation(); void run('حذف', () => core.removeSubscription!(g.id)) }}><Icon name="delete" size={20} color="var(--muted)" /></button> : null}
                <Icon name={expanded ? 'expand_less' : 'expand_more'} size={20} color="var(--muted)" />
              </span>} />
            {expanded ? g.configs.map(c => (
              <SlabRow title={c.name || c.address} mixed
                subtitle={<span class="row gap-sm"><span dir="ltr">{c.protocol.toUpperCase()}</span>{c.delay != null ? <span style={{ color: delayColor(c.delay) }}>{delayText(c.delay)}</span> : null}</span>}
                icon={c.id === selectedId ? 'check_circle' : 'dns'} accent={c.id === selectedId ? 'var(--glow)' : 'var(--primary)'}
                onClick={() => connectTo(c)}
                trailing={<span class="row gap-sm">
                  <button class="icon-btn" aria-label="ستاره" onClick={e => { e.stopPropagation(); void core.favorite!(c.id).then(setGroups) }}><Icon name={c.favorite ? 'star' : 'star_border'} size={20} color={c.favorite ? 'var(--warning)' : 'var(--muted)'} /></button>
                  <button class="icon-btn" aria-label="بیشتر" onClick={e => { e.stopPropagation(); setMenu(c) }}><Icon name="more_vert" size={20} color="var(--muted)" /></button>
                </span>} />
            )) : null}
          </Slab>
        )
      })}

      <Slab spacing={0} padding={6}>
        <SlabRow title="سایت‌های ایرانی بدون VPN" subtitle="سریع‌تر؛ سایت‌هایی که IP خارجی را نمی‌پذیرند هم باز می‌شوند" icon="language"
          trailing={<SkinSwitch checked={settings?.iranDirect !== false} onChange={async v => { setSettings(await core.setSettings!({ iranDirect: v })) }} />} />
        <SlabRow title="اتصال خودکار به سریع‌ترین" subtitle="پیش از هر اتصال، سرورها تست می‌شوند" icon="speed"
          trailing={<SkinSwitch checked={!!settings?.autoFastest} onChange={async v => { setSettings(await core.setSettings!({ autoFastest: v })) }} />} />
      </Slab>

      {picking ? <AppsDialog core={core} selected={settings?.apps ?? []} onDone={async apps => {
        setPicking(false)
        if (apps && core.setSettings) {
          setSettings(await core.setSettings({ apps }))
          if (props.status?.connected && props.status.mode === 'apps') await run('اتصال دوباره', () => core.connect())
        }
      }} /> : null}
      {creds ? <CredentialsDialog config={creds} onDone={async (u, p) => {
        const c = creds; setCreds(null)
        if (u == null) return
        try { await core.setCredentials!(c.id, u, p ?? ''); props.onDismiss(); await core.connect(c.id) } catch (e) { props.onError(coreError(e)) }
      }} /> : null}
      {torOpen ? <TorDialog core={core} onDone={async opts => {
        setTorOpen(false)
        if (opts) await run('افزودن Tor', async () => { const id = await core.addTor!(opts); await core.select!(id) }, 'Tor اضافه شد')
      }} /> : null}
      {aetherOpen ? <AetherDialog onDone={async opts => {
        setAetherOpen(false)
        if (opts) await run('افزودن Aether', async () => { const id = await core.addAether!(opts); await core.select!(id) }, 'Aether اضافه شد')
      }} /> : null}
      {codeOpen ? <CodeDialog onDone={async code => {
        setCodeOpen(false)
        if (code) { try { const ok = await core.submitAetherCode!(code); toast(ok ? 'کد فرستاده شد' : 'اول به Aether وصل شو، بعد کد را بزن') } catch (e) { props.onError(coreError(e)) } }
      }} /> : null}
      {adding ? <AddDialog core={core} onDone={async msg => { setAdding(false); if (msg) toast(msg); await reload() }} onError={props.onError} /> : null}
      {menu ? (
        <Dialog title={menu.name} onDismiss={() => setMenu(null)} actions={<TextButton onClick={() => setMenu(null)}>بستن</TextButton>}>
          <div class="col gap-sm">
            <GhostPill text="اتصال" icon="bolt" onClick={() => { const c = menu; setMenu(null); connectTo(c) }} />
            {menu.protocol === 'openvpn' || menu.protocol === 'ikev2' ? <GhostPill text="نام کاربری و رمز" icon="vpn_key" onClick={() => { const c = menu; setMenu(null); setCreds(c) }} /> : null}
            {menu.protocol === 'aether' ? <GhostPill text="کد ایمیل Zero Trust" icon="lock" onClick={() => { setMenu(null); setCodeOpen(true) }} /> : null}
            <GhostPill text="تست همین سرور" icon="speed" onClick={() => { const id = menu.id; setMenu(null); void run('در حال تست', () => core.test!([id])) }} />
            <GhostPill text="کپی لینک" icon="content_copy" onClick={async () => { const l = await core.shareLink!(menu.id); setMenu(null); if (l) void copyText(l, 'لینک'); else toast('این کانفیگ لینک اشتراک ندارد') }} />
            <GhostPill text="حذف" icon="delete" accent="var(--error)" onClick={() => { const id = menu.id; setMenu(null); void run('حذف', () => core.removeConfig!(id)) }} />
          </div>
        </Dialog>
      ) : null}
    </Sheet>
  )
}

function AddDialog(props: { core: DesktopCore; onDone: (msg?: string) => void; onError: (m: string) => void }) {
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  async function paste() { try { setText(await navigator.clipboard.readText()) } catch { toast('دسترسی به کلیپ‌بورد نیست؛ دستی بچسبان') } }
  async function file(f: File | undefined) { if (f) setText(await f.text()) }
  async function add() {
    setBusy(true)
    try {
      const r = await props.core.add!(text)
      props.onDone(r.subscription ? 'اشتراک اضافه شد' : `${fa(r.added ?? 0)} کانفیگ اضافه شد`)
    } catch (e) { props.onError(coreError(e)); props.onDone() } finally { setBusy(false) }
  }
  return (
    <Dialog title="افزودن کانفیگ یا اشتراک" onDismiss={() => props.onDone()}
      actions={<><TextButton onClick={() => props.onDone()}>انصراف</TextButton><PillButton text={busy ? 'در حال افزودن…' : 'افزودن'} fillWidth={false} enabled={!busy && !!text.trim()} onClick={add} /></>}>
      <div class="col gap-sm">
        <SkinField label="لینک‌ها، لینک اشتراک یا متن فایل" value={text} onInput={setText} multiline minLines={5} dir="ltr"
          placeholder="vless:// vmess:// trojan:// ss:// hy2:// tuic:// wireguard:// … یا https://…/sub" />
        <div class="row gap-sm">
          <GhostPill text="چسباندن" icon="content_copy" fillWidth={false} onClick={paste} />
          <label class="ghost auto" style={{ cursor: 'pointer' }}>
            <Icon name="attach_file" size={18} /><span class="txt">باز کردن فایل</span>
            <input type="file" hidden accept=".txt,.conf,.json,.yaml,.yml,.ovpn,text/*,application/json" onChange={e => void file((e.target as HTMLInputElement).files?.[0])} />
          </label>
        </div>
        <span class="label-small c-muted">همهٔ پروتکل‌های اپ اندروید: VLESS، VMess، Trojan، Shadowsocks، Hysteria، TUIC، WireGuard، AmneziaWG، SSH، AnyTLS، Juicity، Mieru و… ؛ JSON ایکس‌ری و سینگ‌باکس، Clash، فایل WireGuard.</span>
      </div>
    </Dialog>
  )
}

function appSummary(names: string[]): string {
  const labels = APP_PRESETS.filter(p => p.names.some(n => names.includes(n))).map(p => p.label)
  const custom = names.filter(n => !APP_PRESETS.some(p => p.names.includes(n)))
  return [...labels, ...custom].join('، ')
}

/** Chooses the programs that use the VPN in "chosen apps" mode. */
function AppsDialog(props: { core: DesktopCore; selected: string[]; onDone: (apps?: string[]) => void }) {
  const [chosen, setChosen] = useState<string[]>(props.selected)
  const [running, setRunning] = useState<string[]>([])
  const [custom, setCustom] = useState('')
  useEffect(() => { props.core.runningApps?.().then(r => setRunning(r.map(x => x.name))).catch(() => undefined) }, [])
  const has = (names: string[]) => names.some(n => chosen.includes(n))
  const toggle = (names: string[]) => setChosen(has(names) ? chosen.filter(n => !names.includes(n)) : [...new Set([...chosen, ...names])])
  const presetNames = new Set(APP_PRESETS.flatMap(p => p.names))
  const others = [...new Set([...chosen.filter(n => !presetNames.has(n)), ...running.filter(n => !presetNames.has(n))])]
  return (
    <Dialog title="برنامه‌هایی که از VPN رد می‌شوند" onDismiss={() => props.onDone()}
      actions={<><TextButton onClick={() => props.onDone()}>انصراف</TextButton><PillButton text="ذخیره" fillWidth={false} onClick={() => props.onDone(chosen)} /></>}>
      <div class="col gap-sm">
        {APP_PRESETS.map(p => (
          <SlabRow title={p.label} icon={p.label === 'Telegram' || p.label === 'Discord' || p.label === 'WhatsApp' ? 'chat' : 'public'}
            trailing={<SkinSwitch checked={has(p.names)} onChange={() => toggle(p.names)} />} />
        ))}
        {others.length ? <span class="label-large bold" style={{ marginTop: '6px' }}>برنامه‌های در حال اجرا</span> : null}
        {others.map(n => (
          <SlabRow title={<span dir="ltr">{n}</span>} icon="laptop" trailing={<SkinSwitch checked={chosen.includes(n)} onChange={() => toggle([n])} />} />
        ))}
        <div class="row gap-sm">
          <SkinField label="برنامهٔ دیگر (نام فایل اجرایی)" value={custom} onInput={setCustom} dir="ltr" placeholder="مثلاً vlc.exe یا Visual Studio Code" />
          <GhostPill text="افزودن" icon="add" fillWidth={false} enabled={!!custom.trim()} onClick={() => { setChosen([...new Set([...chosen, custom.trim()])]); setCustom('') }} />
        </div>
      </div>
    </Dialog>
  )
}

function CredentialsDialog(props: { config: CoreConfig; onDone: (user?: string, pass?: string) => void }) {
  const [user, setUser] = useState('')
  const [pass, setPass] = useState('')
  return (
    <Dialog title={`ورود به ${props.config.name}`} onDismiss={() => props.onDone()}
      actions={<><TextButton onClick={() => props.onDone()}>انصراف</TextButton><PillButton text="ذخیره و اتصال" fillWidth={false} enabled={!!user.trim()} onClick={() => props.onDone(user.trim(), pass)} /></>}>
      <div class="col gap-sm">
        <span class="label-small c-muted">این پروفایل {props.config.protocol === 'ikev2' ? 'IKEv2' : 'OpenVPN'} نام کاربری و رمز می‌خواهد؛ یک‌بار ذخیره می‌شود.</span>
        <SkinField label="نام کاربری" value={user} onInput={setUser} dir="ltr" autoFocus />
        <SkinField label="رمز" value={pass} onInput={setPass} dir="ltr" type="password" />
      </div>
    </Dialog>
  )
}

function TorDialog(props: { core: DesktopCore; onDone: (opts?: { country: string; throughVpn: boolean; bridges: string }) => void }) {
  const [countries, setCountries] = useState<[string, string][]>([['', 'Automatic']])
  const [country, setCountry] = useState('')
  const [throughVpn, setThroughVpn] = useState(false)
  const [bridges, setBridges] = useState('')
  useEffect(() => { props.core.torCountries?.().then(setCountries).catch(() => undefined) }, [])
  return (
    <Dialog title="افزودن Tor" onDismiss={() => props.onDone()}
      actions={<><TextButton onClick={() => props.onDone()}>انصراف</TextButton><PillButton text="افزودن" fillWidth={false} onClick={() => props.onDone({ country, throughVpn, bridges })} /></>}>
      <div class="col gap-sm">
        <label class="field"><span class="flabel">کشور خروجی</span>
          <span class="fbox"><select value={country} onChange={e => setCountry((e.target as HTMLSelectElement).value)} style={{ width: '100%', background: 'transparent', color: 'inherit', border: 0, font: 'inherit' }}>
            {countries.map(([code, name]) => <option value={code}>{code ? `${name} (${code.toUpperCase()})` : 'خودکار'}</option>)}
          </select></span></label>
        <SlabRow title="Tor از راه VPN" subtitle="اول به سرور انتخاب‌شده وصل می‌شود، بعد Tor از داخل آن" icon="layers"
          trailing={<SkinSwitch checked={throughVpn} onChange={setThroughVpn} />} />
        <SkinField label="پل‌ها (اختیاری)" value={bridges} onInput={setBridges} multiline minLines={3} dir="ltr"
          placeholder="obfs4 1.2.3.4:443 FINGERPRINT cert=... iat-mode=0" helper="خالی = اتصال مستقیم به تور؛ «snowflake» = پل‌های پیش‌فرض اسنوفلیک" />
      </div>
    </Dialog>
  )
}

function AetherDialog(props: { onDone: (opts?: { mode: string; exitLoc: string; http2: boolean; fragment: boolean }) => void }) {
  const modes = [['masque', 'MASQUE'], ['wg', 'WireGuard'], ['gool', 'WARP-in-WARP'], ['mim', 'MIM']]
  const [mode, setMode] = useState(0)
  const [exitLoc, setExitLoc] = useState('')
  const [http2, setHttp2] = useState(false)
  return (
    <Dialog title="افزودن Aether (WARP)" onDismiss={() => props.onDone()}
      actions={<><TextButton onClick={() => props.onDone()}>انصراف</TextButton><PillButton text="افزودن" fillWidth={false} onClick={() => props.onDone({ mode: modes[mode][0], exitLoc: exitLoc.trim().toUpperCase(), http2, fragment: http2 })} /></>}>
      <div class="col gap-sm">
        <SlidingSegments labels={modes.map(m => m[1])} selected={mode} onSelect={setMode} />
        <SkinField label="کشور خروجی (اختیاری، مثلاً DE)" value={exitLoc} onInput={setExitLoc} dir="ltr" maxLength={2} />
        <SlabRow title="HTTP/2 با فرگمنت" subtitle="برای شبکه‌هایی که QUIC را می‌بندند" icon="tune"
          trailing={<SkinSwitch checked={http2} onChange={setHttp2} />} />
      </div>
    </Dialog>
  )
}

function CodeDialog(props: { onDone: (code?: string) => void }) {
  const [code, setCode] = useState('')
  return (
    <Dialog title="کد ایمیل Zero Trust" onDismiss={() => props.onDone()}
      actions={<><TextButton onClick={() => props.onDone()}>انصراف</TextButton><PillButton text="فرستادن" fillWidth={false} enabled={/^[0-9]{6}$/.test(code)} onClick={() => props.onDone(code)} /></>}>
      <SkinField label="کد ۶ رقمی" value={code} onInput={v => setCode(v.replace(/[^0-9]/g, '').slice(0, 6))} dir="ltr" inputMode="numeric" autoFocus />
    </Dialog>
  )
}
