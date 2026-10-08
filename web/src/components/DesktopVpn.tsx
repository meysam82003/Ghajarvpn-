import { useEffect, useRef, useState } from 'preact/hooks'
import { DesktopCore, CoreStatus, CoreGroup, CoreSettings, CoreMode, CoreConfig, coreError } from '../lib/platform'
import { Slab, SlabRow, SlabDivider, Rail, GhostPill, PillButton, SkinField, SkinSwitch, SlidingSegments, TextButton, SkinEmpty } from './Skin'
import { FullPage, Popover, MenuItem, MenuDivider, Dialog } from './Overlay'
import { Icon } from './Icon'
import { copyText, toast, goTab, renewRequest } from '../state/ui'
import { fa, formatBytes, jalali } from '../lib/format'
import { safeStorage, useStore } from '../lib/store'
import { ownedStore } from '../state/shop'
import { syncOwnedServices } from '../state/desktopSync'
import * as M from '../api/models'

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

const MODE_ICONS: Record<CoreMode, string> = { tun: 'desktop_windows', apps: 'apps', system: 'language', proxy: 'link' }

export function modeLabel(mode?: CoreMode): string {
  return MODES.find(m => m.mode === mode)?.label ?? 'متصل'
}

// ---------------------------------------------------------------- flags

/** FlagColors.kt: the one colour each flag is remembered by. */
const FLAG_COLORS: Record<string, string> = {
  ae: '#C8102E', al: '#E41E20', aq: '#3D9BE9', ar: '#74ACDF', at: '#ED2939', au: '#E4002B', ba: '#FECB00', be: '#FDDA24', bg: '#00966E', br: '#009C3B',
  ca: '#D80621', ch: '#DA291C', cl: '#D52B1E', co: '#FCD116', cy: '#D57800', cz: '#D7141A', de: '#FFCC00', dk: '#C8102E', ec: '#FFDD00', ee: '#4891D9',
  es: '#FFC400', fi: '#005EB8', fr: '#E1000F', gb: '#C8102E', ge: '#FF0000', gr: '#3D7BC8', gt: '#4997D0', hk: '#DE2910', hr: '#FF0000', hu: '#CD2A3E',
  id: '#CE1126', ie: '#169B62', il: '#4A7DE8', in: '#FF671F', is: '#DC1E35', it: '#008C45', jp: '#BC002D', ke: '#BB0000', kr: '#CD2E3A', lt: '#FDB913',
  lu: '#00A1DE', lv: '#B44F55', md: '#FFD200', mk: '#D20000', mx: '#00A05A', my: '#CC0001', ng: '#008751', nl: '#FF9B00', no: '#BA0C2F', nz: '#C8102E',
  pa: '#D21034', pe: '#D91023', ph: '#FCD116', pl: '#DC143C', pt: '#00A550', py: '#D52B1E', ro: '#FCD116', rs: '#C6363C', ru: '#4C7FD6', se: '#FECC02',
  sg: '#ED2939', si: '#3D7BC8', sk: '#EE1C25', sv: '#4A7DE8', th: '#A51931', tr: '#E30A17', tw: '#FE0000', ua: '#FFD700', us: '#B31942', uy: '#5B8AD9',
  vn: '#DA251D', za: '#007A4D', ir: '#239F40', am: '#D90012', az: '#00B5E2', kz: '#00AFCA', qa: '#8A1538', sa: '#006C35', om: '#DB161B', iq: '#CE1126'
}
const FLAG_PAIR = /([\u{1F1E6}-\u{1F1FF}])([\u{1F1E6}-\u{1F1FF}])/u
const FLAGS_ALL = /[\u{1F1E6}-\u{1F1FF}]/gu

/** A name's first flag as its country code, and the name without flag glyphs (Windows draws them as two letters). */
export function splitFlag(name: string): { cc: string; text: string } {
  const m = name.match(FLAG_PAIR)
  const cc = m ? String.fromCharCode(65 + m[1].codePointAt(0)! - 0x1F1E6, 65 + m[2].codePointAt(0)! - 0x1F1E6) : ''
  const text = name.replace(FLAGS_ALL, '').replace(/\s{2,}/g, ' ').replace(/^[\s|·\-–—]+/, '').trim()
  return { cc, text: text || name }
}

export function FlagBadge(props: { cc: string }) {
  return <span class="flag-badge" style={{ ['--flag' as any]: FLAG_COLORS[props.cc.toLowerCase()] ?? 'var(--primary)' }}><bdi dir="ltr">{props.cc}</bdi></span>
}

/** A server or group name, its flag drawn as a badge (flagRuns / flagInlineContent). */
export function FlagName(props: { name: string }) {
  const { cc, text } = splitFlag(props.name)
  return <span class="flag-name">{cc ? <FlagBadge cc={cc} /> : null}<span class="bidi">{text}</span></span>
}

// ---------------------------------------------------------------- pings

type Ping = number | null | undefined | 'testing'

/** pingColor(): good under 250 ms, warning under 600, error above; a timeout reads muted. */
export function pingColor(p: Ping): string {
  if (p === 'testing' || p == null) return 'var(--text2)'
  if (p < 0) return 'var(--muted)'
  return p <= 250 ? 'var(--good)' : p <= 600 ? 'var(--warning)' : 'var(--error)'
}
function pingText(p: Ping): string {
  if (p === 'testing') return 'در حال تست…'
  if (p == null) return ''
  return p < 0 ? 'تایم‌اوت' : `${fa(p)} میلی‌ثانیه`
}
/** pingRank: answered (fastest first), then untested, then timed out. */
function pingRank(p: Ping): number {
  if (p === 'testing' || p == null) return 1e9
  return p < 0 ? 2e9 : p
}

function PingChip(props: { ping: Ping }) {
  if (props.ping == null) return null
  return <span class="ping-chip" style={{ ['--pc' as any]: pingColor(props.ping) }}>{pingText(props.ping)}</span>
}

// ---------------------------------------------------------------- services ↔ groups

/** The shop service a group was delivered for (the sync names it after the product), when there is one. */
export function serviceOf(g: CoreGroup | null | undefined, owned: M.OwnedService[]): M.OwnedService | null {
  if (!g || g.kind === 'free' || g.kind === 'manual') return null
  return owned.find(s => !!g.url && !!s.username && g.url.includes(s.username)) ??
    owned.find(s => !!s.productName && s.productName.trim() === g.name.trim()) ?? null
}

/** HH:MM:SS in Persian digits, as SessionLine prints it. */
export function sessionClock(ms: number): string {
  const t = Math.max(0, Math.floor(ms / 1000))
  const p2 = (n: number) => String(n).padStart(2, '0')
  return fa(`${p2(Math.floor(t / 3600))}:${p2(Math.floor((t % 3600) / 60))}:${p2(t % 60)}`)
}

/** rememberSecondTick: a clock that ticks once a second while [active]. */
export function useSecondTick(active: boolean): number {
  const [now, setNow] = useState(Date.now())
  useEffect(() => {
    if (!active) return
    setNow(Date.now())
    const t = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(t)
  }, [active])
  return now
}

function daysLeft(expire?: number): number | null {
  return expire && expire > 0 ? Math.floor((expire * 1000 - Date.now()) / 86400000) : null
}
function levelColor(remaining: number, total: number): string {
  const f = total > 0 ? Math.min(1, Math.max(0, remaining / total)) : 0
  return f <= 0.1 ? 'var(--error)' : f <= 0.3 ? 'var(--warning)' : 'var(--primary)'
}

/** SubscriptionQuotaCard, for a group whose panel reported a quota or an expiry. */
export function GroupQuotaCard(props: { group: CoreGroup }) {
  const g = props.group
  const total = g.total ?? 0, used = g.used ?? 0
  const remaining = total > 0 ? Math.max(0, total - used) : 0
  const days = daysLeft(g.expire)
  const volumeLevel = total > 0 ? remaining / total : 1
  const timeLevel = days == null ? 1 : days <= 1 ? 0 : days <= 3 ? 0.2 : days <= 7 ? 0.5 : 1
  const worst = Math.min(volumeLevel, timeLevel)
  const accent = worst <= 0.1 ? 'var(--error)' : worst <= 0.3 ? 'var(--warning)' : 'var(--premium)'
  return (
    <Slab accent={accent} padding={12} spacing={4}>
      <SlabRow title={<FlagName name={g.name} />} subtitle="سرویس تو" icon="data_usage" accent={accent} />
      <Slab padding={8} spacing={0}>
        <div class="stat-strip">
          <span class="cell"><span class="k">حجم باقی‌مانده</span>
            <span class="v" style={{ color: total > 0 ? levelColor(remaining, total) : 'var(--premium)' }}>{total > 0 ? formatBytes(remaining) : 'نامحدود'}</span>
            {total > 0 ? <span class="s">مجموع {formatBytes(total)}</span> : null}</span>
          <span class="sep" />
          <span class="cell"><span class="k">زمان باقی‌مانده</span>
            <span class="v" style={{ color: days == null ? 'var(--premium)' : days <= 3 ? 'var(--error)' : days <= 7 ? 'var(--warning)' : 'var(--info)' }}>
              {days == null ? 'نامحدود' : days < 0 ? 'تمام شده' : `${fa(days)} روز`}</span></span>
        </div>
      </Slab>
      {total > 0 ? <UsageBar used={used} total={total} /> : <span class="label-small c-muted">پنل برای این سرویس محدودیتی اعلام نکرده است.</span>}
    </Slab>
  )
}

function UsageBar(props: { used: number; total: number }) {
  const remaining = Math.max(0, props.total - props.used)
  const frac = props.total > 0 ? Math.min(1, Math.max(0, remaining / props.total)) : 0
  return <div class="meter usage-bar">{frac > 0 ? <i style={{ width: `${frac * 100}%`, background: levelColor(remaining, props.total) }} /> : null}</div>
}

// ---------------------------------------------------------------- the server screen

type Sort = 'added' | 'alpha' | 'fastest'
const SORTS: [Sort, string][] = [['alpha', 'الفبایی'], ['fastest', 'سریع‌ترین'], ['added', 'زمان افزودن']]

function readJson<T>(key: string, fallback: T): T {
  try { const v = safeStorage.get(key); return v ? JSON.parse(v) as T : fallback } catch { return fallback }
}

interface Confirm { title: string; text: string; action: string; run: () => Promise<unknown> | void }

/**
 * The desktop server screen, the Android app's "انتخاب سرور" on a whole
 * window: the add panel and the tools, how the computer is connected and the
 * free sources down the side; every subscription with its own ping, update,
 * usage and menu, and its servers, filling the rest.
 */
export function DesktopServersPage(props: { core: DesktopCore; status: CoreStatus | null; onDismiss: () => void; onError: (m: string) => void }) {
  const core = props.core
  const owned = useStore(ownedStore)
  const [groups, setGroups] = useState<CoreGroup[]>([])
  const [settings, setSettings] = useState<CoreSettings | null>(null)
  const [busy, setBusy] = useState('')
  const [statusLine, setStatusLine] = useState<{ text: string; bad: boolean } | null>(null)
  const [delays, setDelays] = useState<Record<string, number>>({})
  const [testing, setTesting] = useState<Set<string>>(new Set())
  const [pingingGroups, setPingingGroups] = useState<Set<string>>(new Set())
  const [refreshing, setRefreshing] = useState<Set<string>>(new Set())
  const [testAllState, setTestAllState] = useState(0)
  const [updatingAll, setUpdatingAll] = useState(false)
  const [pickingFastest, setPickingFastest] = useState(false)
  const [open, setOpen] = useState<Record<string, boolean>>(() => readJson('ghajar.desktop.groups.open', {}))
  const [actionsFor, setActionsFor] = useState<string | null>(null)
  const [addOpen, setAddOpen] = useState(false)
  const [searchOpen, setSearchOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [favOnly, setFavOnly] = useState(false)
  const [sort, setSortState] = useState<Sort>(() => (safeStorage.get('ghajar.desktop.sort') as Sort) || 'added')
  const [newest, setNewestState] = useState(() => safeStorage.get('ghajar.desktop.newest') === '1')
  const [menu, setMenu] = useState<{ kind: 'sort' | 'purge' | 'group'; anchor: HTMLElement; group?: CoreGroup } | null>(null)
  const [confirm, setConfirm] = useState<Confirm | null>(null)
  const [renaming, setRenaming] = useState<CoreGroup | null>(null)
  const [adding, setAdding] = useState(false)
  const [subDialog, setSubDialog] = useState(false)
  const [picking, setPicking] = useState(false)
  const [creds, setCreds] = useState<CoreConfig | null>(null)
  const [torOpen, setTorOpen] = useState(false)
  const [aetherOpen, setAetherOpen] = useState(false)
  const [codeOpen, setCodeOpen] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)

  const st = props.status
  const connected = !!st?.connected
  const connecting = !!st?.connecting && !connected
  const selectedId = st?.selectedId ?? ''
  const activeId = connected || connecting ? (st?.server?.id || selectedId) : ''

  async function reload() {
    try {
      if (core.groups) setGroups(await core.groups())
      if (core.settings) setSettings(await core.settings())
    } catch { /* engine restarting: the next status brings the list */ }
  }
  useEffect(() => {
    void reload()
    // The purchased services come in (or are brought up to date) as their own groups.
    void syncOwnedServices(true).then(reload)
    return core.onStatus(() => void reload())
  }, [])
  useEffect(() => { if (!statusLine) return; const t = setTimeout(() => setStatusLine(null), 3500); return () => clearTimeout(t) }, [statusLine])

  function say(text: string, bad = false) { setStatusLine({ text, bad }) }
  function fail(e: unknown) { const m = coreError(e); say(m, true); props.onError(m) }

  async function run(label: string, fn: () => Promise<unknown>, done?: string) {
    setBusy(label)
    try { await fn(); if (done) say(done) } catch (e) { fail(e) } finally { setBusy(''); await reload() }
  }

  const setIn = (set: Set<string>, ids: string[], on: boolean) => { const n = new Set(set); ids.forEach(id => on ? n.add(id) : n.delete(id)); return n }

  /** Real-delay test of these servers, through the core (EngineTester.realDelay). */
  async function testIds(ids: string[]): Promise<Record<string, number>> {
    if (!core.test || !ids.length) return {}
    setTesting(t => setIn(t, ids, true))
    try {
      const r = await core.test(ids)
      setDelays(d => ({ ...d, ...r }))
      return r
    } catch (e) { fail(e); return {} } finally { setTesting(t => setIn(t, ids, false)); void reload() }
  }

  async function testGroup(g: CoreGroup) {
    if (pingingGroups.has(g.id) || !g.configs.length) return
    setPingingGroups(s => setIn(s, [g.id], true))
    try { await testIds(g.configs.map(c => c.id)) } finally { setPingingGroups(s => setIn(s, [g.id], false)) }
  }

  async function testAll() {
    const ids = groups.flatMap(g => g.configs.map(c => c.id))
    if (testAllState === 1 || !ids.length) return
    setTestAllState(1)
    const r = await testIds(ids)
    setTestAllState(2)
    setTimeout(() => setTestAllState(0), 2500)
    // The results are in; order by them (Android switches to "fastest" too).
    if (Object.values(r).some(ms => ms > 0)) setSort('fastest')
  }

  function canRefresh(g: CoreGroup): boolean {
    return g.kind !== 'manual' && (!!g.url || g.kind === 'free' || g.kind === 'service' || !!serviceOf(g, owned))
  }

  async function refreshGroup(g: CoreGroup) {
    if (refreshing.has(g.id)) return
    setRefreshing(s => setIn(s, [g.id], true))
    say('در حال دریافت ساب…')
    try {
      if (g.id === 'free' && core.refreshFree) {
        const n = await core.refreshFree()
        if (!n) throw new Error('فعلاً کانفیگ رایگان سالمی پیدا نشد')
        say(`${fa(n)} کانفیگ سالم اضافه شد`)
      } else if (g.id === 'warp' && core.addWarp) {
        await core.addWarp()
        say('WARP بروز شد')
      } else if (g.url) {
        if (core.refreshSubscription) await core.refreshSubscription(g.id)
        else {
          const r = await core.refreshSubscriptions!()
          const mine = r.find(x => x.id === g.id)
          if (mine?.error) throw new Error(mine.error)
        }
        const fresh = (await core.groups!()).find(x => x.id === g.id)
        say(`${g.name}: ${fa(fresh?.configs.length ?? 0)}`)
      } else {
        await syncOwnedServices(true)
        say('سرویس بروز شد')
      }
    } catch (e) { fail(e) } finally { setRefreshing(s => setIn(s, [g.id], false)); await reload() }
  }

  async function updateAll() {
    if (updatingAll) return
    setUpdatingAll(true)
    try {
      const subs = groups.filter(g => g.url && g.kind !== 'free')
      await syncOwnedServices(true)
      const r = core.refreshSubscriptions ? await core.refreshSubscriptions() : []
      const ok = r.filter(x => !x.error).length
      if (!subs.length && !r.length) say('ساب اینترنتی برای بروزرسانی وجود ندارد', true)
      else if (ok >= r.length) say(`همهٔ ساب‌ها بروزرسانی شد (${fa(ok)})`)
      else if (ok > 0) say(`${fa(ok)} از ${fa(r.length)} ساب بروزرسانی شد`)
      else say(`دریافت ناموفق بود: هیچ سابی بروزرسانی نشد${r[0]?.error ? ' — ' + r[0].error : ''}`, true)
    } catch (e) { fail(e) } finally { setUpdatingAll(false); await reload() }
  }

  async function connectFastest() {
    if (pickingFastest || !core.fastest) return
    setPickingFastest(true)
    try {
      const id = await core.fastest()
      if (!id) { say('هیچ سروری به تست پاسخ نداد؛ اتصالت را بررسی کن یا سرور اضافه کن.', true); return }
      await core.connect(id)
    } catch (e) { fail(e) } finally { setPickingFastest(false); await reload() }
  }

  /** A tap on a row: this server is the one (and the live tunnel moves to it), then home. */
  async function choose(c: CoreConfig) {
    if (c.needsCredentials) { setCreds(c); return }
    try {
      if (core.select) await core.select(c.id)
      if ((connected || connecting) && c.id !== activeId) void core.connect(c.id).catch(e => props.onError(coreError(e)))
      props.onDismiss()
    } catch (e) { fail(e) }
  }

  /** The row's play/stop: connect here, or drop the tunnel this row carries. */
  async function toggleConnection(c: CoreConfig) {
    if (c.id === activeId && (connected || connecting)) { await run('قطع اتصال', () => core.disconnect()); return }
    if (c.needsCredentials) { setCreds(c); return }
    await run('در حال اتصال', () => core.connect(c.id))
  }

  function setSort(s: Sort) { setSortState(s); safeStorage.set('ghajar.desktop.sort', s) }
  function setNewest(v: boolean) { setNewestState(v); safeStorage.set('ghajar.desktop.newest', v ? '1' : '0') }
  function toggleGroup(id: string, now: boolean) {
    const next = { ...open, [id]: !now }
    setOpen(next)
    safeStorage.set('ghajar.desktop.groups.open', JSON.stringify(next))
  }

  async function setMode(mode: CoreMode) {
    if (!core.setSettings) return
    setSettings(await core.setSettings({ mode }))
    if (connected) await run('اتصال دوباره', () => core.connect())
  }

  async function addText(text: string) {
    if (!text.trim()) { say('کلیپ‌بورد خالی است', true); return }
    await run('در حال افزودن…', async () => {
      const r = await core.add!(text)
      say(r.subscription ? 'ساب افزوده شد' : `${fa(r.added ?? 0)} کانفیگ افزوده شد`)
    })
  }

  async function pasteClipboard() {
    try { await addText(await navigator.clipboard.readText()) } catch { setAdding(true) }
  }

  function removeIds(ids: string[], label: string) {
    return run(label, async () => { for (const id of ids) await core.removeConfig!(id) }, `${fa(ids.length)} کانفیگ حذف شد`)
  }

  const delayOf = (c: CoreConfig): Ping => testing.has(c.id) ? 'testing' : (delays[c.id] ?? c.delay)
  const total = groups.reduce((n, g) => n + g.configs.length, 0)
  const favourites = groups.reduce((n, g) => n + g.configs.filter(c => c.favorite).length, 0)
  const timedOut = (list: CoreConfig[]) => list.filter(c => { const d = delays[c.id] ?? c.delay; return d != null && d < 0 })
  const q = query.trim().toLowerCase()
  const matches = (c: CoreConfig) => !q || c.name.toLowerCase().includes(q) || c.address.toLowerCase().includes(q) || c.protocol.toLowerCase().includes(q)
  const sorted = (list: CoreConfig[]) => sort === 'fastest' ? [...list].sort((a, b) => pingRank(delayOf(a)) - pingRank(delayOf(b)))
    : sort === 'alpha' ? [...list].sort((a, b) => splitFlag(a.name).text.localeCompare(splitFlag(b.name).text, 'fa'))
    : newest ? [...list].reverse() : list
  const shown = groups.map(g => {
    const pool = g.configs.filter(c => !favOnly || c.favorite)
    const list = sorted(q && !g.name.toLowerCase().includes(q) ? pool.filter(matches) : pool)
    return { g, list }
  }).filter(({ g, list }) => (!q && !favOnly) || list.length > 0 || (!!q && g.name.toLowerCase().includes(q)))
  const modeIndex = Math.max(0, MODES.findIndex(m => m.mode === (settings?.mode ?? 'tun')))

  const statusChip = connected
    ? <span class="conn-chip on"><span class="dot" />متصل · <FlagName name={st?.server?.name ?? ''} /></span>
    : connecting ? <span class="conn-chip busy"><span class="dot" />در حال اتصال…</span>
    : <span class="conn-chip"><span class="dot" />قطع</span>

  return (
    <FullPage title="انتخاب سرور" subtitle={`${fa(total)} سرور · ${fa(groups.length)} گروه`} onDismiss={props.onDismiss} class="servers-page" trailing={statusChip}>
      <div class="srv-layout">
        <aside class="srv-side">
          {/* AddServerPanel */}
          <Slab spacing={0} padding={10}>
            <button class="add-head" aria-expanded={addOpen} onClick={() => setAddOpen(!addOpen)}>
              <span class={'add-plus' + (addOpen ? ' open' : '')}><Icon name="add" size={18} /></span>
              <span class="col grow"><span class="title-small bold c-text">افزودن سرور</span>
                <span class="label-small c-muted ellipsis">الصاق، دستی، فایل، سابسکریپشن</span></span>
              <Icon name={addOpen ? 'expand_less' : 'expand_more'} size={20} color="var(--muted)" />
            </button>
            {addOpen ? (
              <div class="col gap-sm" style={{ paddingTop: '10px' }}>
                <Rail label="کانفیگی که از قبل داری" />
                <div class="glyph-grid">
                  <GlyphButton icon="content_paste" label="وارد کردن از کلیپ‌بورد" enabled={!busy} onClick={() => void pasteClipboard()} />
                  <GlyphButton icon="upload_file" label="ورود از فایل" enabled={!busy} onClick={() => fileRef.current?.click()} />
                  <GlyphButton icon="add" label="افزودن دستی" enabled={!busy} onClick={() => setAdding(true)} />
                  <GlyphButton icon="hub" label="افزودن سابسکریپشن" enabled={!busy} onClick={() => setSubDialog(true)} />
                </div>
                <input ref={fileRef} type="file" hidden accept=".txt,.conf,.json,.yaml,.yml,.ovpn,text/*,application/json"
                  onChange={async e => { const f = (e.target as HTMLInputElement).files?.[0]; (e.target as HTMLInputElement).value = ''; if (f) await addText(await f.text()) }} />
                <span class="label-small c-muted">لینک‌های VLESS، VMess، Trojan، Shadowsocks، Hysteria، TUIC، WireGuard، SSH و… ؛ JSON ایکس‌ری و سینگ‌باکس، Clash، فایل WireGuard و پروفایل OpenVPN (‎.ovpn‎).</span>
              </div>
            ) : null}
          </Slab>

          {/* The toolbar: two labelled jobs, the fastest, then the glyph rail. */}
          <Slab padding={10} spacing={8}>
            <div class="row gap-sm">
              <GhostPill text={testAllState === 1 ? 'در حال تست…' : testAllState === 2 ? 'تست کامل شد' : 'تست همه'} icon="speed" minHeight={40}
                enabled={total > 0 && testAllState !== 1} onClick={() => void testAll()} />
              <GhostPill text={updatingAll ? 'در حال دریافت ساب…' : 'آپدیت ساب‌ها'} icon="autorenew" minHeight={40}
                enabled={!updatingAll} onClick={() => void updateAll()} />
            </div>
            <PillButton text={pickingFastest ? 'در حال یافتن سریع‌ترین سرور' : 'اتصال به سریع‌ترین'} icon="bolt" minHeight={42}
              enabled={total > 0 && !pickingFastest} onClick={() => void connectFastest()} />
            <div class="tool-rail">
              <PickerTool icon={searchOpen ? 'close' : 'search'} label="جستجو" active={searchOpen}
                onClick={() => { setSearchOpen(!searchOpen); if (searchOpen) setQuery('') }} />
              <PickerTool icon={favOnly ? 'star' : 'star_border'} label={favourites ? `مورد علاقه (${fa(favourites)})` : 'مورد علاقه'} active={favOnly}
                enabled={favourites > 0 || favOnly} onClick={() => setFavOnly(!favOnly)} />
              <PickerTool icon="arrow_upward" label="جدیدترین در بالا" short="جدیدترین" active={newest} onClick={() => setNewest(!newest)} />
              <PickerTool icon="swap_vert" label="مرتب‌سازی" active={sort !== 'added'} onClick={e => setMenu({ kind: 'sort', anchor: e })} />
              <PickerTool icon="delete_sweep" label="حذف" destructive onClick={e => setMenu({ kind: 'purge', anchor: e })} />
            </div>
          </Slab>

          {/* How the computer is connected (desktop only). */}
          <Slab spacing={8} padding={12}>
            <span class="label-large bold c-text">نوع اتصال</span>
            <div class="mode-grid" role="radiogroup" aria-label="نوع اتصال">
              {MODES.map((m, i) => (
                <button role="radio" aria-checked={i === modeIndex} class={'mode-opt' + (i === modeIndex ? ' on' : '')} onClick={() => void setMode(m.mode)}>
                  <Icon name={MODE_ICONS[m.mode]} size={18} /><span>{m.label}</span>
                </button>
              ))}
            </div>
            <span class="label-small c-muted">{MODES[modeIndex].hint}</span>
            {MODES[modeIndex].mode === 'apps' ? (
              <SlabRow title="برنامه‌هایی که وصل می‌شوند" icon="apps" chevron onClick={() => setPicking(true)}
                subtitle={(settings?.apps ?? []).length ? appSummary(settings?.apps ?? []) : 'هنوز برنامه‌ای انتخاب نشده'} />
            ) : null}
          </Slab>

          {/* The free sources. */}
          <Slab spacing={0} padding={10}>
            <Rail label="سرورهای رایگان" />
            <SlabRow title="کانفیگ‌های رایگان" subtitle="از کانال‌های تلگرام، تست‌شده؛ فقط سالم‌ها می‌مانند" icon="card_giftcard" accent="var(--premium)" chevron
              enabled={!busy} onClick={() => run('دریافت کانفیگ رایگان', async () => { const n = await core.refreshFree!(); if (!n) throw new Error('فعلاً کانفیگ رایگان سالمی پیدا نشد'); say(`${fa(n)} کانفیگ سالم اضافه شد`) })} />
            <SlabDivider />
            <SlabRow title="WARP رایگان کلادفلر" subtitle="WireGuard، با ثبت‌نام خودکار" icon="shield" accent="var(--info)" chevron enabled={!busy}
              onClick={() => run('ساخت WARP', async () => { const id = await core.addWarp!(); await core.select!(id) }, 'WARP اضافه شد')} />
            <SlabDivider />
            <SlabRow title="Psiphon" subtitle="موتور داخلی، بدون نیاز به کانفیگ" icon="public" accent="var(--good)" chevron enabled={!busy}
              onClick={() => run('افزودن Psiphon', async () => { const id = await core.addPsiphon!(''); await core.select!(id) }, 'Psiphon اضافه شد')} />
            {core.addTor ? <><SlabDivider /><SlabRow title="Tor" subtitle="موتور داخلی، با پل‌ها و انتخاب کشور خروجی" icon="security" accent="var(--premium)" chevron enabled={!busy} onClick={() => setTorOpen(true)} /></> : null}
            {core.addAether ? <><SlabDivider /><SlabRow title="Aether (WARP)" subtitle="WARP کلادفلر با MASQUE / WireGuard و اسکن خودکار" icon="bolt" accent="var(--warning)" chevron enabled={!busy} onClick={() => setAetherOpen(true)} /></> : null}
          </Slab>

          <Slab spacing={0} padding={10}>
            <SlabRow title="سایت‌های ایرانی بدون VPN" subtitle="سریع‌تر؛ سایت‌هایی که IP خارجی را نمی‌پذیرند هم باز می‌شوند" icon="language"
              trailing={<SkinSwitch checked={settings?.iranDirect !== false} label="سایت‌های ایرانی بدون VPN" onChange={async v => { setSettings(await core.setSettings!({ iranDirect: v })) }} />} />
            <SlabDivider />
            <SlabRow title="اتصال خودکار به سریع‌ترین" subtitle="پیش از هر اتصال، سرورها تست می‌شوند" icon="speed"
              trailing={<SkinSwitch checked={!!settings?.autoFastest} label="اتصال خودکار به سریع‌ترین" onChange={async v => { setSettings(await core.setSettings!({ autoFastest: v })) }} />} />
          </Slab>
        </aside>

        <main class="srv-main">
          {searchOpen ? (
            <SkinField label="جستجوی سرورها" value={query} onInput={setQuery} autoFocus placeholder="نام، آدرس یا پروتکل (مثلاً vless)" />
          ) : null}

          {busy || statusLine ? (
            <div class="status-line-wrap">
              <span class={'status-line' + (statusLine?.bad && !busy ? ' bad' : '')}>
                {busy ? <span class="spinner xs" /> : <Icon name={statusLine?.bad ? 'error' : 'check_circle'} size={16} />}
                <span>{busy ? `${busy}…` : statusLine?.text}</span>
              </span>
            </div>
          ) : null}

          {groups.length === 0 ? (
            <SkinEmpty icon="dns" title="هنوز سروری نیست"
              hint="سرویس خریداری‌شده خودش اینجا می‌آید؛ یا کانفیگ و سابسکریپشن اضافه کن، یا از سرورهای رایگان بگیر."
              actionText="افزودن سرور" onAction={() => setAddOpen(true)} />
          ) : shown.length === 0 ? (
            <SkinEmpty icon="search" title="سروری با این جستجو پیدا نشد" />
          ) : null}

          {shown.map(({ g, list }) => {
            const hasSelected = g.configs.some(c => c.id === selectedId)
            const expanded = q || favOnly ? true : open[g.id] ?? (hasSelected || groups.length === 1)
            const service = serviceOf(g, owned)
            return (
              <section class="grp" key={g.id}>
                <GroupHeader group={g} count={g.configs.length} expanded={expanded} selected={hasSelected} service={service}
                  pinging={pingingGroups.has(g.id)} refreshing={refreshing.has(g.id)} canRefresh={canRefresh(g)}
                  onToggle={() => toggleGroup(g.id, expanded)}
                  onPing={() => void testGroup(g)}
                  onRefresh={() => void refreshGroup(g)}
                  onDelete={() => setConfirm(g.kind === 'manual'
                    ? { title: 'حذف کانفیگ‌های دستی', text: `${fa(g.configs.length)} کانفیگ دستی حذف شود؟`, action: 'حذف', run: () => removeIds(g.configs.map(c => c.id), 'حذف') }
                    : { title: 'حذف همه کانفیگ‌ها', text: `«${splitFlag(g.name).text}» و ${fa(g.configs.length)} کانفیگش حذف شود؟`, action: 'حذف', run: () => run('حذف', () => core.removeSubscription!(g.id)) })}
                  onMore={anchor => setMenu({ kind: 'group', anchor, group: g })}
                  onRenew={() => { if (!service) return; props.onDismiss(); renewRequest.set(service.username); goTab('shop') }} />
                {expanded && list.length ? (
                  <div class="cfg-grid">
                    {list.map(c => (
                      <ConfigRow key={c.id} config={c} ping={delayOf(c)} selected={c.id === selectedId} active={c.id === activeId}
                        live={c.id === activeId ? (connected ? 'on' : 'connecting') : 'off'} actionsOpen={actionsFor === c.id}
                        onSelect={() => void choose(c)} onToggleConnection={() => void toggleConnection(c)}
                        onToggleActions={() => setActionsFor(actionsFor === c.id ? null : c.id)}
                        onFavorite={() => void core.favorite!(c.id).then(setGroups).catch(fail)}
                        onTest={() => void testIds([c.id])}
                        onCopy={async () => { const l = await core.shareLink!(c.id); if (l) void copyText(l, 'لینک'); else toast('این کانفیگ لینک اشتراک ندارد') }}
                        onCredentials={c.protocol === 'openvpn' || c.protocol === 'ikev2' ? () => setCreds(c) : undefined}
                        onCode={c.protocol === 'aether' ? () => setCodeOpen(true) : undefined}
                        onDelete={() => void run('حذف', () => core.removeConfig!(c.id))} />
                    ))}
                  </div>
                ) : expanded && !list.length ? <span class="label-medium c-muted grp-empty">این گروه هنوز کانفیگی ندارد؛ «بروزرسانی» را بزن.</span> : null}
              </section>
            )
          })}
        </main>
      </div>

      {menu?.kind === 'sort' ? (
        <Popover anchor={menu.anchor} onDismiss={() => setMenu(null)} width={200}>
          {SORTS.map(([s, label]) => <MenuItem label={label} checked={sort === s} onClick={() => { setSort(s); setMenu(null) }} />)}
        </Popover>
      ) : null}
      {menu?.kind === 'purge' ? (
        <Popover anchor={menu.anchor} onDismiss={() => setMenu(null)} width={250}>
          <MenuItem icon="edit_off" label="حذف کانفیگ‌های دستی" onClick={() => {
            setMenu(null)
            const manual = groups.filter(g => g.kind === 'manual').flatMap(g => g.configs)
            if (!manual.length) { say('کانفیگ دستی‌ای نیست'); return }
            setConfirm({ title: 'حذف کانفیگ‌های دستی', text: `${fa(manual.length)} کانفیگ دستی حذف شود؟`, action: 'حذف', run: () => removeIds(manual.map(c => c.id), 'حذف') })
          }} />
          <MenuItem icon="timer_off" label="حذف تایم‌اوت‌ها" onClick={() => {
            setMenu(null)
            const dead = timedOut(groups.flatMap(g => g.configs))
            if (!dead.length) { say('کانفیگ تایم‌اوتی نیست؛ اول «تست همه» را بزن'); return }
            setConfirm({ title: 'حذف تایم‌اوت‌ها', text: `${fa(dead.length)} کانفیگی که به تست جواب نداد حذف شود؟`, action: 'حذف', run: () => removeIds(dead.map(c => c.id), 'حذف') })
          }} />
          <MenuDivider />
          <MenuItem icon="delete_forever" danger label="حذف همه" onClick={() => {
            setMenu(null)
            setConfirm({ title: 'حذف همه', text: `همهٔ ${fa(total)} کانفیگ و ${fa(groups.length)} گروه حذف شود؟`, action: 'حذف', run: () => run('حذف', async () => {
              for (const g of groups) {
                if (g.kind === 'manual') { for (const c of g.configs) await core.removeConfig!(c.id) }
                else await core.removeSubscription!(g.id)
              }
            }, 'همه حذف شد') })
          }} />
        </Popover>
      ) : null}
      {menu?.kind === 'group' && menu.group ? (() => {
        const g = menu.group
        const dead = timedOut(g.configs)
        const close = () => setMenu(null)
        return (
          <Popover anchor={menu.anchor} onDismiss={close} width={250}>
            {g.url ? <MenuItem icon="content_copy" label="اشتراک در کلیپ‌بورد" onClick={() => { close(); void copyText(g.url!, 'لینک ساب') }} /> : null}
            {core.renameSubscription && g.kind !== 'manual' ? <MenuItem icon="edit" label="ویرایش نام" onClick={() => { close(); setRenaming(g) }} /> : null}
            <MenuItem icon="speed" label="تست تأخیر واقعی" enabled={g.configs.length > 0} onClick={() => { close(); void testGroup(g) }} />
            {canRefresh(g) ? <MenuItem icon="refresh" label="بروزرسانی" onClick={() => { close(); void refreshGroup(g) }} /> : null}
            {serviceOf(g, owned) ? <MenuItem icon="autorenew" label="تمدید" onClick={() => { close(); props.onDismiss(); renewRequest.set(serviceOf(g, owned)!.username); goTab('shop') }} /> : null}
            <MenuDivider />
            <MenuItem icon="timer_off" label={dead.length ? `حذف تایم‌اوت‌ها (${fa(dead.length)})` : 'حذف تایم‌اوت‌ها'} enabled={dead.length > 0}
              onClick={() => { close(); void removeIds(dead.map(c => c.id), 'حذف') }} />
            <MenuItem icon="delete_forever" danger label="حذف همه کانفیگ‌ها" onClick={() => {
              close()
              setConfirm(g.kind === 'manual'
                ? { title: 'حذف کانفیگ‌های دستی', text: `${fa(g.configs.length)} کانفیگ دستی حذف شود؟`, action: 'حذف', run: () => removeIds(g.configs.map(c => c.id), 'حذف') }
                : { title: 'حذف همه کانفیگ‌ها', text: `«${splitFlag(g.name).text}» و ${fa(g.configs.length)} کانفیگش حذف شود؟`, action: 'حذف', run: () => run('حذف', () => core.removeSubscription!(g.id)) })
            }} />
          </Popover>
        )
      })() : null}

      {confirm ? (
        <Dialog title={confirm.title} onDismiss={() => setConfirm(null)}
          actions={<><TextButton onClick={() => setConfirm(null)}>انصراف</TextButton>
            <TextButton color="var(--error)" onClick={() => { const c = confirm; setConfirm(null); void c.run() }}>{confirm.action}</TextButton></>}>
          <span class="body-medium c-text">{confirm.text}</span>
        </Dialog>
      ) : null}
      {renaming ? <RenameDialog name={renaming.name} onDone={async name => {
        const g = renaming; setRenaming(null)
        if (name && name !== g.name) await run('ویرایش نام', async () => { setGroups(await core.renameSubscription!(g.id, name)) })
      }} /> : null}
      {subDialog ? <SubscriptionDialog onDone={async (url, name) => {
        setSubDialog(false)
        if (url) await run('در حال دریافت ساب…', async () => { await core.addSubscription!(url, name || undefined); say('ساب افزوده شد') })
      }} /> : null}
      {picking ? <AppsDialog core={core} selected={settings?.apps ?? []} onDone={async apps => {
        setPicking(false)
        if (apps && core.setSettings) {
          setSettings(await core.setSettings({ apps }))
          if (connected && st?.mode === 'apps') await run('اتصال دوباره', () => core.connect())
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
      {adding ? <AddDialog core={core} onDone={async msg => { setAdding(false); if (msg) say(msg); await reload() }} onError={props.onError} /> : null}
    </FullPage>
  )
}

/** PickerTool: one square tinted tile on the toolbar's rail, filled while on. */
function PickerTool(props: { icon: string; label: string; short?: string; active?: boolean; enabled?: boolean; destructive?: boolean; onClick: (anchor: HTMLElement) => void }) {
  return (
    <button class={'picker-tool' + (props.active ? ' active' : '') + (props.destructive ? ' danger' : '')} disabled={props.enabled === false}
      aria-label={props.label} aria-pressed={props.active} title={props.label} onClick={e => props.onClick(e.currentTarget as HTMLElement)}>
      <span class="pt-tile"><Icon name={props.icon} size={18} /></span>
      <span class="lbl">{props.short ?? props.label}</span>
    </button>
  )
}

function GlyphButton(props: { icon: string; label: string; onClick: () => void; enabled?: boolean }) {
  return (
    <button class="glyph-action" disabled={props.enabled === false} onClick={props.onClick}>
      <span class="glyph-tile" style={{ width: '36px', height: '36px', borderRadius: '12px', background: 'var(--primary-14)' }}><Icon name={props.icon} size={19} color="var(--primary)" /></span>
      <span class="label-medium c-text">{props.label}</span>
    </button>
  )
}

/** SubHeaderGlyph, with its word beside it: the window has the room the phone did not. */
function HeadAction(props: { icon: string; label: string; onClick: (anchor: HTMLElement) => void; enabled?: boolean; danger?: boolean; busy?: boolean; iconOnly?: boolean }) {
  return (
    <button class={'head-act' + (props.danger ? ' danger' : '') + (props.iconOnly ? ' icon-only' : '')} disabled={props.enabled === false || props.busy}
      aria-label={props.label} title={props.label} onClick={e => { e.stopPropagation(); props.onClick(e.currentTarget as HTMLElement) }}>
      {props.busy ? <span class="spinner xs" /> : <Icon name={props.icon} size={17} />}
      {props.iconOnly ? null : <span>{props.label}</span>}
    </button>
  )
}

/** SubscriptionHeader: the name, its own ping / update / delete / menu, the usage bar, the chips, renew, last update. */
function GroupHeader(props: {
  group: CoreGroup; count: number; expanded: boolean; selected: boolean; service: M.OwnedService | null
  pinging: boolean; refreshing: boolean; canRefresh: boolean
  onToggle: () => void; onPing: () => void; onRefresh: () => void; onDelete: () => void; onMore: (anchor: HTMLElement) => void; onRenew: () => void
}) {
  const g = props.group
  const total = g.total ?? 0, used = g.used ?? 0
  const remaining = Math.max(0, total - used)
  const percent = total > 0 ? Math.round(Math.min(100, Math.max(0, remaining / total * 100))) : 0
  const days = daysLeft(g.expire)
  const chips: [string, number][] = []
  if (total > 0) { const f = remaining / total; chips.push([`${formatBytes(remaining)} از ${formatBytes(total)} باقی‌مانده`, f <= 0.1 ? 2 : f <= 0.3 ? 1 : 0]) }
  if (days != null && days >= 0) chips.push([`انقضا در ${fa(days)} روز`, days <= 1 ? 2 : days <= 3 ? 1 : 0])
  if (days != null && days < 0) chips.push(['تمام شده', 2])
  const nearlyOut = (total > 0 && remaining / total <= 0.15) || (days != null && days <= 3)
  const kindLabel = g.kind === 'manual' ? 'دستی' : g.kind === 'free' ? 'رایگان' : props.service ? 'سرویس خریداری‌شده' : g.url ? 'سابسکریپشن' : ''
  return (
    <div class={'grp-head' + (props.selected ? ' sel' : '')}>
      <div class="grp-top">
        <button class="grp-toggle" aria-expanded={props.expanded} onClick={props.onToggle}>
          <span class="chev-tile"><Icon name="expand_more" size={18} class={props.expanded ? '' : 'turned'} /></span>
          <span class="col grow gap-2">
            <span class="title-medium bold c-text clamp2"><FlagName name={g.name} /></span>
            <span class="label-medium c-text2">{fa(props.count)} کانفیگ{kindLabel ? ` · ${kindLabel}` : ''}</span>
          </span>
        </button>
        <div class="grp-acts">
          <HeadAction icon="speed" label="پینگ" busy={props.pinging} enabled={props.count > 0} onClick={props.onPing} />
          {props.canRefresh ? <HeadAction icon="refresh" label="بروزرسانی" busy={props.refreshing} onClick={props.onRefresh} /> : null}
          <HeadAction icon="delete" label="حذف" danger onClick={props.onDelete} />
          <HeadAction icon="more_vert" label="بیشتر" iconOnly onClick={props.onMore} />
        </div>
      </div>
      {total > 0 ? (
        <div class="row gap-sm">
          <div class="grow"><UsageBar used={used} total={total} /></div>
          <span class="label-medium bold" style={{ color: levelColor(remaining, total) }}>{fa(percent)}٪</span>
        </div>
      ) : null}
      {chips.length || props.service || g.kind !== 'manual' || g.error ? (
        <div class="grp-meta">
          {chips.map(([label, level]) => (
            <span class="quota-chip" style={{ ['--qc' as any]: level === 2 ? 'var(--error)' : level === 1 ? 'var(--warning)' : 'var(--primary)' }}>{label}</span>
          ))}
          {g.error ? <span class="label-small c-error wrap-any">{g.error}</span> : null}
          <span class="spacer" />
          {g.kind !== 'manual' ? (
            <span class="label-small c-muted">{g.lastUpdated ? `آخرین به‌روزرسانی ${jalali(Math.floor(g.lastUpdated / 1000))}` : 'هنوز به‌روزرسانی نشده'}</span>
          ) : null}
          {props.service ? (nearlyOut
            ? <PillButton text="تمدید" icon="autorenew" minHeight={34} fillWidth={false} onClick={props.onRenew} />
            : <GhostPill text="تمدید" icon="autorenew" minHeight={34} fillWidth={false} onClick={props.onRenew} />) : null}
        </div>
      ) : null}
    </div>
  )
}

/** ConfigRow: ping dot, flag and name, protocol and endpoint, the ping chip; play/stop and its action rail. */
function ConfigRow(props: {
  config: CoreConfig; ping: Ping; selected: boolean; active: boolean; live: 'on' | 'connecting' | 'off'; actionsOpen: boolean
  onSelect: () => void; onToggleConnection: () => void; onToggleActions: () => void
  onFavorite: () => void; onTest: () => void; onCopy: () => void; onDelete: () => void
  onCredentials?: () => void; onCode?: () => void
}) {
  const c = props.config
  const color = pingColor(props.ping)
  const builtin = !c.address || ['psiphon', 'tor', 'aether'].includes(c.protocol)
  const proto = c.protocol.toUpperCase()
  return (
    <div class={'cfg' + (props.selected ? ' sel' : '') + (props.active ? ' active' : '') + (props.actionsOpen ? ' open' : '')}>
      <button class="cfg-main" onClick={props.onSelect} title={c.name}>
        <span class="ping-tile" style={{ ['--pc' as any]: color }}><span class={'ping-dot' + (props.ping === 'testing' ? ' live' : '')} /></span>
        <span class="cfg-text">
          <span class="cfg-name">
            {c.favorite ? <Icon name="star" size={14} color="var(--premium)" /> : null}
            <FlagName name={c.name || c.address} />
          </span>
          <span class="cfg-sub">
            <span class="proto-tag">{proto}</span>
            {builtin ? <span class="addr ellipsis">موتور داخلی</span> : <span class="addr ellipsis" dir="ltr">{c.address}:{c.port}</span>}
            <span class="spacer" />
            <PingChip ping={props.ping} />
          </span>
        </span>
      </button>
      <div class="cfg-acts">
        <button class={'row-act' + (props.live === 'on' ? ' stop' : '')} disabled={props.live === 'connecting'}
          aria-label={props.live === 'on' ? 'قطع اتصال' : 'اتصال'} title={props.live === 'on' ? 'قطع اتصال' : 'اتصال'} onClick={props.onToggleConnection}>
          <Icon name={props.live === 'on' ? 'stop' : props.live === 'connecting' ? 'autorenew' : 'play_arrow'} size={20} />
        </button>
        <button class={'row-act' + (props.actionsOpen ? ' on' : '')} aria-label="بیشتر" aria-expanded={props.actionsOpen} title="بیشتر" onClick={props.onToggleActions}>
          <Icon name="more_vert" size={20} />
        </button>
      </div>
      {props.actionsOpen ? (
        <div class="cfg-rail">
          <RowAction icon={c.favorite ? 'star' : 'star_border'} label="مورد علاقه" tint={c.favorite ? 'var(--premium)' : undefined} onClick={props.onFavorite} />
          <RowAction icon="speed" label="تست تأخیر" onClick={props.onTest} />
          <RowAction icon="content_copy" label="کپی لینک" onClick={props.onCopy} />
          {props.onCredentials ? <RowAction icon="vpn_key" label="نام کاربری و رمز" onClick={props.onCredentials} /> : null}
          {props.onCode ? <RowAction icon="lock" label="کد ایمیل" onClick={props.onCode} /> : null}
          <RowAction icon="delete" label="حذف" tint="var(--error)" onClick={props.onDelete} />
        </div>
      ) : null}
    </div>
  )
}

function RowAction(props: { icon: string; label: string; onClick: () => void; tint?: string }) {
  return (
    <button class="rail-act" onClick={props.onClick} style={props.tint ? { color: props.tint } : undefined}>
      <Icon name={props.icon} size={18} /><span>{props.label}</span>
    </button>
  )
}

function SubscriptionDialog(props: { onDone: (url?: string, name?: string) => void }) {
  const [url, setUrl] = useState('')
  const [name, setName] = useState('')
  const ok = /^https?:\/\/\S+$/i.test(url.trim())
  return (
    <Dialog title="افزودن سابسکریپشن" onDismiss={() => props.onDone()}
      actions={<><TextButton onClick={() => props.onDone()}>انصراف</TextButton><PillButton text="افزودن" fillWidth={false} enabled={ok} onClick={() => props.onDone(url.trim(), name.trim())} /></>}>
      <div class="col gap-sm">
        <span class="label-small c-muted">یک لینک که فهرست سرورهایش را خودش به‌روز نگه می‌دارد</span>
        <SkinField label="لینک سابسکریپشن" value={url} onInput={setUrl} dir="ltr" placeholder="https://" autoFocus />
        <SkinField label="نام (اختیاری)" value={name} onInput={setName} />
      </div>
    </Dialog>
  )
}

function RenameDialog(props: { name: string; onDone: (name?: string) => void }) {
  const [name, setName] = useState(props.name)
  return (
    <Dialog title="ویرایش نام" onDismiss={() => props.onDone()}
      actions={<><TextButton onClick={() => props.onDone()}>انصراف</TextButton><PillButton text="ذخیره" fillWidth={false} enabled={!!name.trim()} onClick={() => props.onDone(name.trim())} /></>}>
      <SkinField label="نام" value={name} onInput={setName} autoFocus />
    </Dialog>
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
