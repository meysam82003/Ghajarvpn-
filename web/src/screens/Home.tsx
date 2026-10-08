import { useEffect, useState } from 'preact/hooks'
import { desktopCore, coreError, CoreStatus, CoreServer } from '../lib/platform'
import * as api from '../api/client'
import * as M from '../api/models'
import { useStore } from '../lib/store'
import { tokenStore } from '../api/account'
import { lookStore } from '../theme/look'
import { Icon } from '../components/Icon'
import { Slab, SlabRow, StatStrip, GhostPill, LinearProgress } from '../components/Skin'
import { Sheet } from '../components/Overlay'
import { AddToAppSheet } from '../components/AddToApp'
import { ownedStore, selectedServiceStore, selectService, refreshOwned } from '../state/shop'
import { goTab, renewRequest } from '../state/ui'
import { brandedTitle, fa, formatBytes } from '../lib/format'
import { statusLabel } from '../components/ShopParts'
import { DesktopServersSheet, modeLabel } from '../components/DesktopVpn'

/**
 * The home screen, on the Slab skin: the connect control dead centre, the
 * service it will hand over under it, and what is left of that service.
 *
 * A browser has no tunnel of its own, so the control does the one thing it
 * can: it gives the selected service to a VPN app on this device.
 */
export function HomeScreen() {
  const linked = useStore(tokenStore) !== ''
  const owned = useStore(ownedStore)
  const selected = useStore(selectedServiceStore)
  const look = useStore(lookStore)
  const [picker, setPicker] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [details, setDetails] = useState<M.ServiceDetails | null>(null)
  const service = owned.find(s => s.username === selected) ?? null
  const canAct = linked && service != null

  // In the desktop app the control connects for real: its core is a local
  // proxy, and the Ghajar browser extension sends the browser through it.
  const core = desktopCore()
  const [coreState, setCoreState] = useState<CoreStatus | null>(null)
  const [servers, setServers] = useState<CoreServer[]>([])
  const [serverSheet, setServerSheet] = useState(false)
  const [pings, setPings] = useState<Record<number, number>>({})
  useEffect(() => {
    if (!core) return
    core.status().then(setCoreState).catch(() => undefined)
    core.servers().then(setServers).catch(() => undefined)
    return core.onStatus(st => { setCoreState(st); core.servers().then(setServers).catch(() => undefined) })
  }, [])
  const connected = !!coreState?.connected
  // Engine v2: every protocol, free servers, system proxy and full-device modes.
  const full = !!core?.groups
  const hasServer = full && !!coreState?.selectedId

  async function loadServers(): Promise<boolean> {
    if (!core || !service) return false
    const d = await api.service(service.username)
    const list = await core.setService({ name: d.productName || service.productName, configs: d.outputs, subscriptionUrl: d.subscriptionUrl ?? '' })
    setServers(list)
    return list.length > 0
  }

  async function act() {
    if (core && (connected || (hasServer && !service))) {
      // Free or manually added servers connect without an account.
      setBusy(true); setError(null)
      try { if (connected) await core.disconnect(); else await core.connect() } catch (e) { setError(coreError(e)) } finally { setBusy(false) }
      return
    }
    if (!linked) { if (full) { setServerSheet(true); return } goTab('shop'); return }
    if (!service) { if (owned.length) setPicker(true); else goTab('shop'); return }
    if (core) {
      setBusy(true); setError(null)
      try {
        // The selected service's servers come in first; a server picked on the
        // server screen from another group stays the one to connect.
        await loadServers().catch(e => { if (!hasServer) throw e })
        await core.connect()
      } catch (e) { setError(coreError(e)) } finally { setBusy(false) }
      return
    }
    setBusy(true); setError(null)
    try { setDetails(await api.service(service.username)) } catch (e) { setError(api.publicMessage(e)) } finally { setBusy(false) }
  }

  const gap = look.density === 'compact' ? 8 : look.density === 'spacious' ? 24 : 12
  return (
    <div class="page pad-lg centered home" style={{ gap: `${gap}px`, ['--home-gap' as any]: `${gap}px` }}>
      <div class="home-hero">
      <ConnectOrb style={look.orbStyle} enabled={canAct || !linked || owned.length > 0 || hasServer} working={busy || !!coreState?.connecting || connected} onClick={act}
        label={core && connected ? 'متصل' : core && hasServer && !service ? 'اتصال' : !linked ? (full ? 'اتصال رایگان' : 'ورود و خرید') : service ? 'اتصال' : owned.length ? 'انتخاب سرویس' : 'خرید سرویس'}
        hint={core && connected ? `${modeLabel(coreState?.mode)} · برای قطع بزن` : !linked && !hasServer ? (full ? 'سرور رایگان یا حساب قاجار' : 'برای شروع، حساب را وصل کن') : !service && !owned.length && !hasServer ? 'سرویسی انتخاب نشده' : null} />
      <span class="label-small c-muted center">{core && full && (canAct || hasServer)
        ? (connected ? `${modeLabel(coreState?.mode)} · ${coreState?.server?.name ?? ''}${coreState?.engine ? ' · ' + coreState.engine : ''}` : 'نوع اتصال و سرور را از «سرورها» انتخاب کن')
        : core && canAct
        ? (connected ? `مرورگر با افزونهٔ قاجار از این اتصال استفاده می‌کند · فقط مرورگر، نه کل سیستم` : 'با اتصال، مرورگری که افزونهٔ قاجار دارد از VPN استفاده می‌کند؛ بقیهٔ سیستم دست نمی‌خورد')
        : canAct ? 'با یک لمس، همین سرویس به اپ VPN این دستگاه اضافه می‌شود' : linked ? 'از فروشگاه سرویس بخر؛ همین‌جا به اپ VPN اضافه می‌شود' : 'حساب تلگرام را یک‌بار متصل کن تا سرویس‌هایت اینجا بیایند'}</span>
      {core && coreState && !coreState.available ? <span class="label-small c-warning center">هستهٔ اتصال در این نسخه نیست؛ نسخهٔ تازهٔ برنامه را نصب کن.</span> : null}
      {core && coreState?.connected && coreState.note ? <span class="label-small c-warning center">{coreState.note}</span> : null}
      </div>

      <div class="home-side">
      <div class="full" style={{ maxWidth: '560px' }}>
        <Slab spacing={0} padding={8}>
          <SlabRow title={service ? service.productName : linked ? 'سرویسی انتخاب نشده' : 'حساب متصل نیست'}
            subtitle={service ? `${service.location ? service.location + ' · ' : ''}⁦${service.username}⁩` : linked ? (owned.length ? `${fa(owned.length)} سرویس در حساب` : 'از فروشگاه یک پلن یا تست بگیر') : 'ورود با ربات تلگرام'}
            icon="shield" accent={service && !M.isEnded(service) ? 'var(--glow)' : 'var(--primary)'} chevron
            onClick={() => (linked && owned.length ? setPicker(true) : goTab('shop'))} />
        </Slab>
      </div>

      {core && (canAct || full) ? (
        <div class="full" style={{ maxWidth: '560px' }}>
          <Slab spacing={0} padding={8}>
            <SlabRow title={coreState?.server?.name || servers[coreState?.selected ?? 0]?.name || (full ? 'سرورها' : 'سرور خودکار')}
              subtitle={full ? `${modeLabel(coreState?.mode)} · ${connected && coreState?.socks ? `پراکسی محلی ${coreState.socks.host}:${coreState.socks.port}` : 'سرورها، رایگان‌ها و نوع اتصال'}` : connected && coreState?.socks ? `پروکسی محلی ${coreState.socks.host}:${coreState.socks.port} · فقط مرورگر` : 'انتخاب سرور برای مرورگر'}
              icon="dns" chevron onClick={async () => { setServerSheet(true); if (!servers.length && service) { try { await loadServers() } catch (e) { setError(coreError(e)) } } }} />
          </Slab>
          {canAct ? <div class="row gap-sm" style={{ marginTop: '8px' }}>
            <GhostPill text="افزودن به اپ دیگر" icon="add_to_home_screen" onClick={async () => { setBusy(true); try { setDetails(await api.service(service!.username)) } catch (e) { setError(api.publicMessage(e)) } finally { setBusy(false) } }} />
          </div> : null}
        </div>
      ) : null}

      {service ? <div class="full" style={{ maxWidth: '560px' }}><QuotaCard service={service} /></div> : null}
      {error ? <div class="full" style={{ maxWidth: '560px' }}><Slab accent="var(--error)" spacing={12}><span class="label-large c-error">{error}</span><GhostPill text="تلاش دوباره" accent="var(--error)" onClick={act} /></Slab></div> : null}
      {busy ? <div class="full" style={{ maxWidth: '560px' }}><LinearProgress /></div> : null}
      </div>

      {picker ? (
        <Sheet title="انتخاب سرویس" onDismiss={() => setPicker(false)}>
          {owned.map(s => {
            const [label, active] = statusLabel(s.status)
            return (
              <Slab spacing={0} padding={8} accent={s.username === selected ? 'var(--primary)' : undefined}>
                <SlabRow title={s.productName} subtitle={`⁦${s.username}⁩ · ${label}`} icon={s.username === selected ? 'check_circle' : 'dns'}
                  accent={active ? 'var(--primary)' : 'var(--error)'} onClick={() => { selectService(s.username); setPicker(false) }}
                  trailing={<button class="icon-btn" aria-label="تمدید" onClick={e => { e.stopPropagation(); setPicker(false); renewRequest.set(s.username); goTab('shop') }}><Icon name="autorenew" color="var(--primary)" /></button>} />
              </Slab>
            )
          })}
          <GhostPill text="بروزرسانی فهرست" icon="refresh" onClick={() => void refreshOwned()} />
          <GhostPill text="خرید سرویس تازه" icon="shopping_cart" onClick={() => { setPicker(false); goTab('shop') }} />
        </Sheet>
      ) : null}

      {serverSheet && core && full ? (
        <DesktopServersSheet core={core} status={coreState} onDismiss={() => setServerSheet(false)} onError={m => setError(m)} />
      ) : serverSheet && core ? (
        <Sheet title="سرور مرورگر" onDismiss={() => setServerSheet(false)}>
          {servers.length === 0 ? <span class="c-muted">سروری برای این سرویس دریافت نشد.</span> : null}
          {servers.map(s => (
            <Slab spacing={0} padding={8} accent={s.index === coreState?.selected ? 'var(--primary)' : undefined}>
              <SlabRow title={s.name} subtitle={`${s.protocol.toUpperCase()}${pings[s.index] != null ? ' · ' + (pings[s.index] < 0 ? 'در دسترس نیست' : fa(pings[s.index]) + ' ms') : ''}`}
                icon={s.index === coreState?.selected ? 'check_circle' : 'dns'}
                onClick={async () => { setServerSheet(false); setBusy(true); setError(null); try { await core.connect(s.index) } catch (e) { setError(coreError(e)) } finally { setBusy(false) } }} />
            </Slab>
          ))}
          <GhostPill text="سنجش سرعت سرورها" icon="speed" onClick={async () => { try { const r = await core.ping(); setPings(Object.fromEntries(r.map(x => [x.index, x.ms]))) } catch { /* offline */ } }} />
          <GhostPill text="بروزرسانی سرورها" icon="refresh" onClick={async () => { try { await loadServers() } catch (e) { setError(coreError(e)) } }} />
          <SlabRow title="سایت‌های ایرانی بدون VPN" subtitle="سریع‌تر؛ سایت‌هایی که IP خارجی را نمی‌پذیرند هم باز می‌شوند" icon="language"
            value={coreState?.directIran ? 'روشن' : 'خاموش'} onClick={async () => setCoreState(await core.setDirectIran(!coreState?.directIran))} />
        </Sheet>
      ) : null}

      {details ? (
        <AddToAppSheet productName={details.productName} username={details.username} subscriptionUrl={details.subscriptionUrl}
          configs={details.outputs} synced={!!(details.subscriptionUrl || details.outputs.length)} onDismiss={() => setDetails(null)} />
      ) : null}
    </div>
  )
}

/** SubscriptionQuotaCard: data and days left, from the panel's own figures. */
function QuotaCard(props: { service: M.OwnedService }) {
  const s = props.service
  const total = s.dataLimitBytes && s.dataLimitBytes > 0 ? s.dataLimitBytes : 0
  const used = s.usedBytes ?? 0
  const remaining = total > 0 ? Math.max(0, total - used) : 0
  const daysLeft = M.daysRemaining(s)
  const volumeLevel = total > 0 ? remaining / total : 1
  const timeLevel = daysLeft == null ? 1 : daysLeft <= 1 ? 0 : daysLeft <= 3 ? 0.2 : daysLeft <= 7 ? 0.5 : 1
  const worst = Math.min(volumeLevel, timeLevel)
  const accent = worst <= 0.1 ? 'var(--error)' : worst <= 0.3 ? 'var(--warning)' : 'var(--premium)'
  const frac = total > 0 ? Math.min(1, Math.max(0, remaining / total)) : 0
  const levelColor = frac <= 0.1 ? 'var(--error)' : frac <= 0.3 ? 'var(--warning)' : 'var(--primary)'
  return (
    <Slab accent={accent} padding={12} spacing={4}>
      <SlabRow title={brandedTitle(total, s.productName)} subtitle="سرویس تو" icon="data_usage" accent={accent} />
      <StatStrip padding={8} cells={[
        { label: 'حجم باقی‌مانده', value: total > 0 ? formatBytes(remaining) : 'نامحدود', accent: total > 0 ? levelColor : 'var(--premium)', sub: total > 0 ? `مجموع ${formatBytes(total)}` : undefined },
        { label: 'زمان باقی‌مانده', value: daysLeft == null ? 'نامحدود' : daysLeft <= 0 ? 'تمام شده' : `${fa(daysLeft)} روز`,
          accent: daysLeft == null ? 'var(--premium)' : daysLeft <= 3 ? 'var(--error)' : daysLeft <= 7 ? 'var(--warning)' : 'var(--info)' }
      ]} />
      {total > 0 ? (
        <div class="meter" style={{ height: '6px', background: 'var(--card)' }}>{frac > 0 ? <i style={{ width: `${frac * 100}%`, background: levelColor }} /> : null}</div>
      ) : <span class="label-small c-muted">پنل برای این سرویس محدودیتی اعلام نکرده است.</span>}
    </Slab>
  )
}

/**
 * ConnectOrb: one control, every style the Personalization page offers.
 * Drawn with SVG the way the Compose version draws with Canvas: the disc, a
 * lifted wash of the state colour, the ring, the glyph and the label.
 */
export function ConnectOrb(props: { style: string; enabled: boolean; working: boolean; label: string; hint?: string | null; onClick: () => void; diameter?: number }) {
  const D = props.diameter ?? 236
  const style = props.style
  const wide = style === 'pill' || style === 'capsule_glow'
  const w = wide ? D * 1.08 : style === 'soft_square' ? D * 0.82 : D
  const h = wide ? D * 0.44 : style === 'soft_square' ? D * 0.82 : D
  const tint = props.working ? 'var(--highlight)' : 'var(--primary)'
  const stroke = Math.min(w, h) * 0.028
  const inset = stroke * 2.6
  const r = Math.min(w, h) / 2 - inset
  const radius = wide ? h / 2 : style === 'soft_square' ? 52 * (D / 236) : style === 'shield' ? 0 : D / 2
  const filled = style === 'pill'
  const ink = filled ? 'var(--on-primary)' : tint
  const textInk = filled ? 'var(--on-primary)' : props.enabled ? 'var(--text)' : 'var(--on-disabled)'
  const glow = style === 'capsule_glow' || style === 'neon'
  const k = D / 236
  return (
    <div style={{ width: `${Math.max(D, w)}px`, height: `${D}px`, maxWidth: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative' }}>
      {glow ? <span aria-hidden="true" style={{ position: 'absolute', width: `${w + 20}px`, height: `${h + 20}px`, borderRadius: wide ? '999px' : '50%', background: 'radial-gradient(closest-side, color-mix(in srgb, var(--primary) 14%, transparent), transparent)' }} /> : null}
      <button class="orb" disabled={!props.enabled} onClick={props.onClick} aria-label={props.label}
        style={{ width: `${w}px`, height: `${h}px`, maxWidth: '100%', borderRadius: style === 'shield' ? '0' : `${radius}px`, position: 'relative', overflow: 'hidden',
          clipPath: style === 'shield' ? 'polygon(50% 0, 100% 18%, 100% 60%, 78% 88%, 50% 100%, 22% 88%, 0 60%, 0 18%)' : undefined,
          border: style === 'capsule_glow' || style === 'soft_square' || style === 'shield' ? `1.5px solid color-mix(in srgb, var(--primary) ${props.enabled ? 80 : 30}%, transparent)` : undefined,
          background: filled ? `color-mix(in srgb, var(--primary) ${props.enabled ? 100 : 35}%, transparent)` : wide || style === 'soft_square' || style === 'shield' ? 'var(--card2)' : 'transparent' }}>
        {wide || style === 'soft_square' || style === 'shield' ? (
          <span aria-hidden="true" style={{ position: 'absolute', inset: 0, background: filled ? 'linear-gradient(180deg, rgba(255,255,255,.16), transparent)' : 'linear-gradient(180deg, color-mix(in srgb, var(--primary) 12%, transparent), transparent)' }} />
        ) : (
          <svg aria-hidden="true" width={w} height={h} viewBox={`0 0 ${w} ${h}`} style={{ position: 'absolute', inset: 0 }}>
            <defs>
              <radialGradient id="orbwash"><stop offset="0" stop-color="var(--primary)" stop-opacity="0.10" /><stop offset="1" stop-color="var(--primary)" stop-opacity="0" /></radialGradient>
            </defs>
            {style === 'neon' ? <>
              <circle cx={w / 2} cy={h / 2} r={r} fill="var(--bg)" />
              {[3, 2, 1].map(i => <circle cx={w / 2} cy={h / 2} r={r} fill="none" stroke="var(--primary)" stroke-opacity={0.08 * i} stroke-width={stroke * (1 + i * 1.3)} />)}
              <circle cx={w / 2} cy={h / 2} r={r} fill="none" stroke={tint} stroke-opacity={props.enabled ? 1 : 0.4} stroke-width={stroke} />
            </> : style === 'minimal' ? (
              <circle cx={w / 2} cy={h / 2} r={r} fill="var(--primary)" fill-opacity="0.12" />
            ) : style === 'segmented' ? <>
              <circle cx={w / 2} cy={h / 2} r={r} fill="var(--card2)" />
              {Array.from({ length: 36 }, (_, i) => {
                const a0 = (-90 + i * 10 + 1.5) * Math.PI / 180, a1 = (-90 + i * 10 + 8.5) * Math.PI / 180
                const x0 = w / 2 + r * Math.cos(a0), y0 = h / 2 + r * Math.sin(a0), x1 = w / 2 + r * Math.cos(a1), y1 = h / 2 + r * Math.sin(a1)
                return <path d={`M${x0},${y0} A${r},${r} 0 0 1 ${x1},${y1}`} fill="none" stroke="var(--border)" stroke-width={stroke * 1.6} />
              })}
            </> : style === 'double_ring' ? <>
              <circle cx={w / 2} cy={h / 2} r={r} fill="var(--card2)" />
              <circle cx={w / 2} cy={h / 2} r={r} fill="none" stroke="var(--border)" stroke-width={stroke} />
              <circle cx={w / 2} cy={h / 2} r={r - stroke * 3.2} fill="none" stroke="var(--border)" stroke-width={stroke * 0.7} />
            </> : style === 'power' ? <>
              <circle cx={w / 2} cy={h / 2} r={r} fill="var(--card2)" />
              <path d={(() => { const g = 40 * Math.PI / 180, a0 = -Math.PI / 2 + g, a1 = -Math.PI / 2 - g + 2 * Math.PI; return `M${w / 2 + r * Math.cos(a0)},${h / 2 + r * Math.sin(a0)} A${r},${r} 0 1 1 ${w / 2 + r * Math.cos(a1)},${h / 2 + r * Math.sin(a1)}` })()}
                fill="none" stroke="var(--border)" stroke-width={stroke * 1.4} stroke-linecap="round" />
              <line x1={w / 2} y1={inset - stroke} x2={w / 2} y2={h * 0.3} stroke={props.enabled ? tint : 'var(--on-disabled)'} stroke-width={stroke * 1.6} stroke-linecap="round" />
            </> : <>
              <circle cx={w / 2} cy={h / 2} r={r} fill="var(--card2)" />
              <circle cx={w / 2} cy={h / 2} r={r} fill="url(#orbwash)" />
              <circle cx={w / 2} cy={h / 2} r={r} fill="none" stroke="var(--border)" stroke-width={stroke} />
            </>}
            {props.working ? <circle class="orb-sweep" cx={w / 2} cy={h / 2} r={r} fill="none" stroke={tint} stroke-width={stroke * 1.6} stroke-linecap="round"
              stroke-dasharray={`${2 * Math.PI * r * 0.22} ${2 * Math.PI * r}`} style={{ transformOrigin: `${w / 2}px ${h / 2}px` }} /> : null}
          </svg>
        )}
        {wide ? (
          <span class="row" style={{ position: 'relative', gap: `${10 * k}px`, padding: `0 ${18 * k}px`, justifyContent: 'center', height: '100%' }}>
            <Icon name="bolt" size={30 * k} color={ink} />
            <span class="title-medium bold ellipsis" style={{ color: textInk, fontSize: `${17 * k}px` }}>{props.label}</span>
          </span>
        ) : (
          <span class="col" style={{ position: 'relative', alignItems: 'center', justifyContent: 'center', height: '100%', gap: `${8 * k}px`, maxWidth: `${156 * k}px`, margin: '0 auto' }}>
            <Icon name="bolt" size={44 * k} color={props.enabled ? tint : 'var(--on-disabled)'} />
            <span class="title-medium bold center clamp2" style={{ color: textInk, fontSize: `${16 * k}px` }}>{props.label}</span>
            {props.hint ? <span class="label-small c-muted ellipsis" style={{ maxWidth: '100%' }}>{props.hint}</span> : null}
          </span>
        )}
      </button>
    </div>
  )
}
