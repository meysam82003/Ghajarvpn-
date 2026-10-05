import { ComponentChildren } from 'preact'
import { useEffect, useMemo, useState } from 'preact/hooks'
import * as M from '../api/models'
import { Icon } from './Icon'
import { Slab, SlabRow, StatStrip, PillButton, GhostPill, TextButton, Rail, SkinField, Radio, LinearProgress, TabRail } from './Skin'
import { Dialog } from './Overlay'
import { formatPrice, fa, plainNumber, gb2, ltr } from '../lib/format'
import { copyText } from '../state/ui'
import { publicMessage } from '../api/client'
import mark from '../assets/images/mark.webp'
import treasury from '../assets/images/treasury.webp'
import wordmark from '../assets/images/wordmark.webp'
import { marketLogoUrl } from '../api/client'

export function SectionTitle(props: { title: string; subtitle: string }) {
  return (
    <div class="col gap-2">
      <Rail label={props.title} />
      <span class="label-medium c-text2">{props.subtitle}</span>
    </div>
  )
}

/** StatusCard: a quiet message or a readable error, with retry and close. */
export function StatusCard(props: { text: string; error: boolean; onDismiss: () => void; onRetry?: () => void; footnote?: string | null }) {
  return (
    <div class={'status-card' + (props.error ? ' err' : '')} role={props.error ? 'alert' : 'status'}>
      <div class="row">
        <span class={'body-small grow wrap-any ' + (props.error ? 'c-error' : 'c-text')}>{props.error ? publicMessage(new Error(props.text)) : props.text}</span>
        {props.onRetry ? <TextButton onClick={props.onRetry}><span class="label-medium bold">تلاش مجدد</span></TextButton> : null}
        <TextButton onClick={props.onDismiss} color="var(--text2)"><span class="label-medium">بستن</span></TextButton>
      </div>
      {props.footnote ? <div class="label-small c-text2" style={{ padding: '0 0 4px 8px' }}>{props.footnote}</div> : null}
    </div>
  )
}

/** GhajarCrestLogo: the Ghajar mark (or the owner's uploaded logo) in a shop-logo frame. */
export function CrestLogo(props: { size?: number; logoVersion?: number }) {
  const size = props.size ?? 48
  const url = props.logoVersion && props.logoVersion > 0 ? marketLogoUrl(0, props.logoVersion) : null
  return <MarketLogoImg url={url} size={size} fallback={mark} verified />
}

export function MarketLogoImg(props: { url: string | null; size: number; fallback?: string; verified?: boolean; name?: string }) {
  const [failed, setFailed] = useState(false)
  const s = props.size
  return (
    <span style={{ width: `${s}px`, height: `${s}px`, borderRadius: '16px', background: props.fallback ? '#101816' : 'var(--card)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden', flex: 'none', position: 'relative' }}>
      {props.url && !failed
        ? <img src={props.url} alt="" width={s} height={s} style={{ width: '100%', height: '100%', objectFit: 'cover' }} onError={() => setFailed(true)} loading="lazy" />
        : props.fallback
          ? <img src={props.fallback} alt="" style={{ width: `${s - 8}px`, height: `${s - 8}px` }} />
          : <Icon name="storefront" size={s / 2} color="var(--muted)" />}
    </span>
  )
}

export function panelIcon(name: string): string {
  const has = (...k: string[]) => k.some(x => name.toLowerCase().includes(x.toLowerCase()))
  if (has('مولتی', 'چند لوکیشن', 'لوکیشن')) return '📍'
  if (has('قبله') || (has('ویژه') && !has('ایرانسل'))) return '👑'
  if (has('ایرانسل', 'اقتصادی')) return '📉'
  if (has('آمریکا', 'usa')) return '🇺🇸'
  if (has('آلمان', 'germany')) return '🇩🇪'
  if (has('سوئد', 'sweden')) return '🇸🇪'
  if (has('ترکیه', 'turkey')) return '🇹🇷'
  if (has('هلند', 'netherlands')) return '🇳🇱'
  if (has('فنلاند', 'finland')) return '🇫🇮'
  if (has('انگلیس', 'uk', 'england')) return '🇬🇧'
  if (has('کانادا', 'canada')) return '🇨🇦'
  if (has('امارات', 'uae')) return '🇦🇪'
  if (has('سنگاپور', 'singapore')) return '🇸🇬'
  if (has('ژاپن', 'japan')) return '🇯🇵'
  if (has('فرانسه', 'france')) return '🇫🇷'
  if (has('روسیه', 'russia')) return '🇷🇺'
  if (has('چین', 'china')) return '🇨🇳'
  if (has('هند', 'india')) return '🇮🇳'
  if (has('تست', 'trial')) return '🎁'
  if (has('گیم', 'بازی')) return '🎮'
  return '🌐'
}

/** ServiceTypeGrid: the service families as chips, all visible at once. */
export function ServiceTypeGrid<T>(props: { items: T[]; selected: T | null; label: (t: T) => string; icon: (t: T) => string; onSelect: (t: T) => void; same?: (a: T, b: T) => boolean }) {
  const same = props.same ?? ((a, b) => a === b)
  return (
    <div class="chips">
      {props.items.map(item => {
        const on = props.selected != null && same(item, props.selected)
        return (
          <button class={'chip big' + (on ? ' active' : '')} onClick={() => props.onSelect(item)}>
            <span class="body-large" style={{ overflow: 'visible' }}>{props.icon(item)}</span>
            <span>{props.label(item)}</span>
          </button>
        )
      })}
    </div>
  )
}

/** ChipFlowRow: "all" plus each option; the chosen chip is filled. */
export function ChipFlowRow<T>(props: { allLabel: string; items: T[]; selected: T | null; label: (t: T) => string; onSelect: (t: T | null) => void; same?: (a: T, b: T) => boolean }) {
  const same = props.same ?? ((a, b) => a === b)
  return (
    <div class="chips">
      <button class={'chip' + (props.selected == null ? ' active' : '')} onClick={() => props.onSelect(null)}><span>{props.allLabel}</span></button>
      {props.items.map(item => {
        const on = props.selected != null && same(item, props.selected)
        return <button class={'chip' + (on ? ' active' : '')} onClick={() => props.onSelect(item)}><span>{props.label(item)}</span></button>
      })}
    </div>
  )
}

/** ProductCard: name, a two/three-cell strip, the price large, its own buy action. */
export function ProductCard(props: { product: M.Product; enabled: boolean; bestValue?: boolean; onBuy: () => void }) {
  const p = props.product
  const [details, setDetails] = useState(false)
  const cells = []
  if (p.trafficGb != null) cells.push({ label: 'حجم', value: `${fa(plainNumber(p.trafficGb))} گیگ`, accent: 'var(--info)' })
  if (p.days != null) cells.push({ label: 'مدت', value: `${fa(p.days)} روز`, accent: 'var(--premium)' })
  if (p.price != null) cells.push({ label: 'قیمت', value: p.price === 0 ? 'رایگان' : `${formatPrice(p.price)} تومان`, accent: props.enabled ? 'var(--highlight)' : 'var(--on-disabled)' })
  return (
    <>
      <Slab accent={props.bestValue ? 'var(--highlight)' : undefined} spacing={12}>
        <div class="row gap-md">
          <span class="glyph-tile" style={{ background: `color-mix(in srgb, ${props.enabled ? 'var(--primary)' : 'var(--on-disabled)'} 14%, transparent)` }}>
            <Icon name="shield" size={20} color={props.enabled ? 'var(--primary)' : 'var(--on-disabled)'} />
          </span>
          <div class="col gap-3 grow">
            <span class="body-large bold clamp2" style={{ color: props.enabled ? 'var(--text)' : 'var(--on-disabled)' }}>{p.name}</span>
            {props.bestValue ? <span class="label-small bold c-highlight">بهترین ارزش در این فهرست</span> : null}
          </div>
          {p.description ? (
            <button class="icon-btn" style={{ width: '34px', height: '34px' }} aria-label="توضیح این پلن" disabled={!props.enabled} onClick={() => setDetails(true)}>
              <Icon name="open_in_new" size={18} color="var(--muted)" />
            </button>
          ) : null}
        </div>
        <StatStrip cells={cells} />
        <PillButton text={p.price == null ? 'قیمت در دسترس نیست' : 'خرید این پلن'} onClick={props.onBuy}
          enabled={props.enabled && p.price != null} icon="shopping_cart" />
      </Slab>
      {details ? (
        <Dialog title={p.name} onDismiss={() => setDetails(false)}
          actions={<>
            <TextButton onClick={() => setDetails(false)}>بازگشت</TextButton>
            <button class="m-btn" disabled={!props.enabled || p.price == null} onClick={() => { setDetails(false); props.onBuy() }}>انتخاب و ادامه</button>
          </>}>
          <span class="bold c-text">{p.price != null ? `${formatPrice(p.price)} تومان` : 'قیمت در دسترس نیست'}</span>
          <span class="c-text wrap-any" style={{ whiteSpace: 'pre-line' }}>{p.description || 'توضیح بیشتری از پنل ارسال نشده است.'}</span>
        </Dialog>
      ) : null}
    </>
  )
}

export function PlanComparisonDialog(props: { products: M.Product[]; onDismiss: () => void }) {
  const rows = useMemo(() => [...props.products].sort((a, b) => {
    const pa = a.price != null && a.trafficGb && a.trafficGb > 0 ? a.price / a.trafficGb : Infinity
    const pb = b.price != null && b.trafficGb && b.trafficGb > 0 ? b.price / b.trafficGb : Infinity
    return pa - pb
  }), [props.products])
  return (
    <Dialog title="مقایسهٔ پلن‌ها" onDismiss={props.onDismiss} actions={<TextButton onClick={props.onDismiss}>بستن</TextButton>}>
      {rows.map(p => {
        const perGb = p.price != null && p.trafficGb && p.trafficGb > 0 ? p.price / p.trafficGb : null
        return (
          <div class="col gap-xs" style={{ background: 'color-mix(in srgb, var(--card2) 70%, transparent)', borderRadius: '12px', padding: '12px' }}>
            <span class="bold c-text">{p.name}</span>
            <span class="body-small">{[p.trafficGb != null ? `${fa(plainNumber(p.trafficGb))} گیگ` : null, p.days != null ? `${fa(p.days)} روز` : null].filter(Boolean).join('  •  ')}</span>
            <span class="bold c-primary">{p.price != null ? (p.price === 0 ? 'رایگان' : `${formatPrice(p.price)} تومان`) : 'قیمت در دسترس نیست'}</span>
            {perGb != null ? <span class="label-small c-text2">هر گیگ: {formatPrice(Math.trunc(perGb))} تومان</span> : null}
          </div>
        )
      })}
    </Dialog>
  )
}

export function CustomServiceCard(props: { traffic: string; days: string; quote: M.CustomQuote | null; onTraffic: (v: string) => void; onDays: (v: string) => void; onQuote: () => void }) {
  return (
    <Slab padding={15} spacing={8}>
      <span class="bold c-text">سرویس سفارشی</span>
      <div class="row gap-10">
        <div class="grow"><SkinField label="حجم (گیگ)" value={props.traffic} onInput={props.onTraffic} inputMode="numeric" dir="ltr" /></div>
        <div class="grow"><SkinField label="مدت (روز)" value={props.days} onInput={props.onDays} inputMode="numeric" dir="ltr" /></div>
      </div>
      {props.quote ? <>
        <span class="bold" style={{ color: props.quote.price != null ? 'var(--highlight)' : 'var(--text2)' }}>
          {props.quote.price != null ? `قیمت لحظه‌ای: ${formatPrice(props.quote.price)} تومان` : 'سرویس سفارشی برای این پنل فعال نیست'}
        </span>
        <span class="body-small">حجم {fa(props.quote.trafficMin)} تا {fa(props.quote.trafficMax)} گیگ · زمان {fa(props.quote.timeMin)} تا {fa(props.quote.timeMax)} روز</span>
      </> : null}
      <button class="m-out full" onClick={props.onQuote}>محاسبه قیمت از پنل</button>
    </Slab>
  )
}

/** ServiceRemaining + MeterRow: what is still yours of a service. */
export function ServiceRemaining(props: { service: M.OwnedService }) {
  const s = props.service
  const spent = M.volumeFraction(s)
  const days = M.daysRemaining(s)
  if (spent == null && days == null) return null
  const pct = (v: number) => fa(`${Math.trunc(v * 100)}٪`)
  const elapsed = M.timeFraction(s)
  return (
    <div class="col gap-6 full">
      {spent != null ? <MeterRow label="حجم باقی‌مانده"
        value={`${gb2(M.remainingBytes(s) ?? 0)} از ${gb2(s.dataLimitBytes ?? 0)} گیگابایت`}
        share={pct(1 - spent)} fraction={1 - spent} tint={spent >= 0.9 ? 'var(--error)' : 'var(--primary)'} /> : null}
      {days != null ? <MeterRow label="روز باقی‌مانده"
        value={`${fa(days)} روز${s.planDays && s.planDays > 0 ? ` از ${fa(s.planDays)}` : ''}`}
        share={elapsed != null ? pct(1 - elapsed) : ''} fraction={elapsed != null ? 1 - elapsed : 1}
        tint={days <= 3 ? 'var(--error)' : 'var(--primary)'} /> : null}
    </div>
  )
}

function MeterRow(props: { label: string; value: string; share: string; fraction: number; tint: string }) {
  return (
    <div class="col gap-3 full">
      <div class="row">
        <span class="label-small c-muted grow">{props.label}</span>
        {props.share ? <span class="label-small bold" style={{ color: props.tint }}>{props.share}</span> : null}
      </div>
      <span class="body-small c-text ellipsis">{props.value}</span>
      <div class="meter">{props.fraction > 0.01 ? <i style={{ width: `${Math.min(1, Math.max(0, props.fraction)) * 100}%`, background: props.tint }} /> : null}</div>
    </div>
  )
}

export function statusLabel(status: string): [string, boolean] {
  const st = status.toLowerCase()
  const active = ['active', 'enabled', 'فعال'].includes(st)
  if (active) return ['فعال', true]
  if (st === 'expired') return ['منقضی', false]
  if (st === 'disabled' || st === 'inactive') return ['غیرفعال', false]
  return [status, false]
}

/** OwnedServiceCard: tap to get the configs, the renew button, and what is left. */
export function OwnedServiceCard(props: { service: M.OwnedService; onImport: () => void; onRenew: () => void }) {
  const s = props.service
  const [label, active] = statusLabel(s.status)
  return (
    <div class="mcard" role="button" tabIndex={0} style={{ cursor: 'pointer' }} onClick={props.onImport}
      onKeyDown={e => { if (e.key === 'Enter') props.onImport() }}>
      <div class="col gap-sm" style={{ padding: '15px' }}>
        <div class="row">
          <div class="col grow">
            <span class="bold c-text clamp2">{s.productName}</span>
            <span class="body-small c-text2 ellipsis">{ltr(s.username)}</span>
            {s.location ? <span class="body-small c-text2 ellipsis">{s.location}</span> : null}
          </div>
          <span class="label-medium" style={{ color: active ? 'var(--primary)' : 'var(--error)', whiteSpace: 'nowrap' }}>{label}</span>
          <button class="icon-btn" aria-label="تمدید سرویس" onClick={e => { e.stopPropagation(); props.onRenew() }}>
            <Icon name="autorenew" color="var(--primary)" />
          </button>
          <Icon name="add_circle" />
        </div>
        <ServiceRemaining service={s} />
      </div>
    </div>
  )
}

export function ServiceSortChips(props: { services: M.OwnedService[]; selected: M.ServiceSort; onSelect: (s: M.ServiceSort) => void }) {
  return (
    <TabRail
      tabs={M.ServiceSortLabels.map(([key, label]) => {
        const count = M.sortedFor(props.services, key).length
        return { label, badge: key !== 'NEWEST' && count > 0 ? count : null }
      })}
      selected={M.ServiceSortLabels.findIndex(([k]) => k === props.selected)}
      onSelect={i => props.onSelect(M.ServiceSortLabels[i][0])}
    />
  )
}

export function NoticeCard(props: { notice: M.Notice; onOpen?: () => void }) {
  const n = props.notice
  const accent = n.important ? 'var(--error)' : 'var(--primary)'
  const m = n.meta
  const details = m ? [m.remainingBytes != null ? `باقی‌مانده ${(m.remainingBytes / 1073741824).toFixed(2)} گیگ` : null, m.daysRemaining != null ? `${m.daysRemaining} روز باقی‌مانده` : null].filter(Boolean).join(' · ') : ''
  return (
    <div class="mcard" role={props.onOpen ? 'button' : undefined} onClick={props.onOpen}
      style={{ background: n.important ? 'var(--error-surface)' : 'var(--card2)', borderColor: `color-mix(in srgb, ${accent} 40%, transparent)`, cursor: props.onOpen ? 'pointer' : undefined }}>
      <div class="row gap-10" style={{ padding: '14px', alignItems: 'flex-start' }}>
        <Icon name="notifications" color={accent} />
        <div class="col grow">
          <span class="bold c-text">{n.title}</span>
          <span class="c-text2 wrap-any" style={{ whiteSpace: 'pre-line' }}>{n.message}</span>
          {details ? <span class="bold" style={{ color: accent }}>{details}</span> : null}
        </div>
      </div>
    </div>
  )
}

export function PaymentSummary(props: { purchase: M.PurchaseResult; walletTopUp: boolean; exactCardAmount: number | null }) {
  const p = props.purchase
  return (
    <div class="mcard" style={{ background: 'var(--card2)' }}>
      <div class="col gap-xs" style={{ padding: '15px', gap: '5px' }}>
        <span class="c-text xbold">{props.walletTopUp ? 'شارژ کیف پول' : 'پرداخت مبلغ کسری'}</span>
        <span class="c-text2">موجودی: {formatPrice(p.balance)} تومان</span>
        {!props.walletTopUp ? <span class="c-text2">قیمت سرویس: {formatPrice(p.price)} تومان</span> : null}
        <span class="c-highlight bold">قابل پرداخت: {formatPrice(props.exactCardAmount ?? p.amountDue)} تومان</span>
      </div>
    </div>
  )
}

export function PaymentMethodCard(props: { method: M.PaymentMethod; amount: number; enabled: boolean; onClick: () => void }) {
  const m = props.method
  const allowed = props.amount >= m.minimum && (m.maximum <= 0 || props.amount <= m.maximum)
  return (
    <button class="mcard" style={{ background: 'var(--card)' }} disabled={!(allowed && props.enabled)} onClick={props.onClick}>
      <div class="row gap-10" style={{ padding: '14px' }}>
        <Icon name="credit_card" color={allowed ? 'var(--primary)' : 'var(--on-disabled)'} />
        <div class="col grow">
          <span class="bold c-text">{m.label}</span>
          <span class="body-small c-text2">محدوده {formatPrice(m.minimum)} تا {m.maximum > 0 ? formatPrice(m.maximum) : 'نامحدود'} تومان</span>
        </div>
        <span style={{ color: allowed ? 'var(--primary)' : 'var(--error)' }}>{allowed ? 'انتخاب' : 'نامعتبر'}</span>
      </div>
    </button>
  )
}

/** CardToCardCard: the destination card, the exact amount, and the receipt upload. */
export function CardToCardCard(props: { payment: M.PaymentInit; receipt: File | null; busy: boolean; sent: boolean; onPick: (f: File | null) => void; onUpload: () => void }) {
  const p = props.payment
  const [preview, setPreview] = useState<string | null>(null)
  useEffect(() => {
    if (!props.receipt) { setPreview(null); return }
    const url = URL.createObjectURL(props.receipt)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [props.receipt])
  const card = (p.cardNumber ?? '').replace(/\s+/g, '')
  return (
    <div class="col gap-md">
      <div class="mcard" style={{ borderRadius: 'var(--r-lg)', boxShadow: '0 3px 8px rgba(0,0,0,.18)', overflow: 'hidden' }}>
        <div class="col gap-10" style={{ padding: '18px', background: 'linear-gradient(135deg, var(--card2), var(--card))' }}>
          <div class="row">
            <img src={wordmark} alt="" style={{ height: '38px', flex: '1 1 auto', objectFit: 'contain', objectPosition: 'right', minWidth: 0 }} />
            <img src={treasury} alt="خزانهٔ قاجار" style={{ width: '54px', height: '54px', objectFit: 'contain' }} />
          </div>
          <span class="label-medium c-text2">کارت مقصد • اطلاعات صادرشده از پنل</span>
          <div class="row">
            <span class="title-large bold c-text grow ltr" style={{ direction: 'ltr', textAlign: 'start', letterSpacing: '1px' }}>{(card.match(/.{1,4}/g) ?? []).join(' ')}</span>
            <button class="icon-btn" aria-label="کپی شماره کارت" disabled={!card} onClick={() => copyText(card, 'شماره کارت')}><Icon name="content_copy" color="var(--primary)" /></button>
          </div>
          <div class="row">
            <span class="c-text grow">{p.cardHolder ?? ''}</span>
            <button class="icon-btn" aria-label="کپی نام صاحب کارت" onClick={() => copyText(p.cardHolder ?? '', 'نام صاحب کارت')}><Icon name="content_copy" color="var(--primary)" /></button>
          </div>
          <div style={{ height: '1px', background: 'var(--border)' }} />
          <div class="row">
            <span class="c-highlight bold grow">مبلغ دقیق: {formatPrice(p.amount)} تومان</span>
            <button class="icon-btn" aria-label="کپی مبلغ تومان" onClick={() => copyText(String(p.amount), 'مبلغ تومان')}><Icon name="content_copy" color="var(--primary)" /></button>
          </div>
          <TextButton onClick={() => copyText(String(p.amountRial), 'مبلغ ریال')} color="var(--text2)">{formatPrice(p.amountRial)} ریال • کپی</TextButton>
        </div>
      </div>
      <span class="body-small">همین مبلغ دقیق را واریز کن؛ ممکن است برای تطبیق خودکار با قیمت پایه تفاوت داشته باشد.</span>
      {preview ? <img src={preview} alt="پیش‌نمایش رسید انتخاب‌شده" style={{ width: '100%', maxHeight: '180px', objectFit: 'contain' }} /> : null}
      <label class="m-out full" style={{ cursor: props.busy ? 'default' : 'pointer', opacity: props.busy ? 0.6 : 1 }}>
        <input type="file" accept="image/jpeg,image/png,image/webp" hidden disabled={props.busy}
          onChange={e => props.onPick((e.target as HTMLInputElement).files?.[0] ?? null)} />
        {props.receipt == null ? 'انتخاب عکس رسید از گوشی' : 'تغییر عکس رسید'}
      </label>
      <button class="m-btn full" disabled={props.receipt == null || props.busy || props.sent || !p.orderId} onClick={props.onUpload}>
        {props.sent ? 'رسید ارسال شد؛ منتظر تأیید' : 'ارسال رسید برای بررسی'}
      </button>
      <span class="body-small">JPEG، PNG یا WebP • حداکثر ۸ مگابایت. ارسال رسید به معنی تأیید پرداخت نیست.</span>
    </div>
  )
}

/** GhajarPendingPaymentCard: an unfinished payment, with a live countdown. */
export function PendingPaymentCard(props: { item: M.PendingPayment; busy: boolean; onResume: () => void; onCancel: () => void }) {
  const [now, setNow] = useState(Math.floor(Date.now() / 1000))
  const [confirm, setConfirm] = useState(false)
  useEffect(() => { const t = setInterval(() => setNow(Math.floor(Date.now() / 1000)), 1000); return () => clearInterval(t) }, [])
  const it = props.item
  const left = Math.max(0, it.expiresAt - now)
  const mm = String(Math.floor(left / 60)).padStart(2, '0'), ss = String(left % 60).padStart(2, '0')
  return (
    <>
      <div class="mcard" style={{ borderColor: 'var(--warning)' }}>
        <div class="col gap-md" style={{ padding: '18px' }}>
          <span class="bold c-text">پرداخت در انتظار تأیید — {it.label}</span>
          <span class="c-text">کد فاکتور: {ltr(it.orderId)}</span>
          <span class="c-text">مبلغ: {formatPrice(it.amount)} تومان</span>
          <span class="c-text">{it.expiresAt <= 0 ? 'در انتظار بررسی وضعیت سرور' : left === 0 ? 'زمان پرداخت تمام شده؛ وضعیت را پیگیری کن' : `باقی‌مانده: ${fa(`${mm}:${ss}`)}`}</span>
          <div class="row gap-sm">
            <button class="m-btn grow" disabled={props.busy} onClick={props.onResume}>ادامه پیگیری</button>
            <button class="m-out grow" disabled={props.busy} onClick={() => setConfirm(true)}>انصراف</button>
          </div>
        </div>
      </div>
      {confirm ? (
        <Dialog title="انصراف از فاکتور" onDismiss={() => setConfirm(false)}
          actions={<>
            <TextButton onClick={() => setConfirm(false)}>بازگشت</TextButton>
            <TextButton onClick={() => { setConfirm(false); props.onCancel() }}>انصراف از فاکتور</TextButton>
          </>}>
          اگر مبلغ را پرداخت کرده‌ای، پیگیری را ادامه بده. انصراف فقط پس از تأیید سرور انجام می‌شود.
        </Dialog>
      ) : null}
    </>
  )
}

/** PurchaseExtras: optional username, note and discount code, inside the order dialog. */
export function PurchaseExtras(props: {
  username: string; usernameRequired: boolean; note: string; showUsername: boolean; showNote: boolean; discount: string
  onUsername: (v: string) => void; onNote: (v: string) => void; onDiscount: (v: string) => void
}) {
  return (
    <Slab padding={14} spacing={8}>
      {props.showUsername ? <SkinField label={props.usernameRequired ? 'نام کاربری دلخواه (ضروری)' : 'نام کاربری دلخواه'} value={props.username} onInput={v => props.onUsername(v.slice(0, 40))} dir="ltr" /> : null}
      {props.showNote ? <SkinField label="یادداشت اختیاری" value={props.note} onInput={v => props.onNote(v.slice(0, 120))} /> : null}
      <SkinField label="کد تخفیف اختیاری" value={props.discount} onInput={v => props.onDiscount(v.slice(0, 60))} dir="ltr" />
    </Slab>
  )
}

export function RadioRow(props: { selected: boolean; onClick: () => void; children: ComponentChildren }) {
  return (
    <div class="row full" style={{ cursor: 'pointer' }} onClick={props.onClick}>
      <Radio selected={props.selected} />
      <div class="grow">{props.children}</div>
    </div>
  )
}

/** CurrentServiceSummary: what the service being renewed is right now. */
export function CurrentServiceSummary(props: { service: M.ServiceDetails }) {
  const s = props.service
  const gb = (v: number) => `${fa(v.toFixed(2))} گیگابایت`
  const rows: [string, string][] = []
  if (s.productName) rows.push(['پلن فعلی', s.productName])
  if (s.totalGb && s.totalGb > 0) rows.push(['حجم پلن', gb(s.totalGb)])
  if (s.usedGb != null) rows.push(['مصرف‌شده', gb(s.usedGb)])
  if (s.remainingGb != null) rows.push(['باقی‌مانده', gb(s.remainingGb)])
  if (s.expiresAt) rows.push(['انقضا', fa(s.expiresAt)])
  if (s.status) rows.push(['وضعیت', s.status])
  if (!rows.length) return null
  return (
    <div class="col gap-xs full" style={{ borderRadius: 'var(--r-md)', background: 'var(--card2)', padding: '12px' }}>
      <span class="label-medium bold c-text2">این سرویس الان چیست</span>
      {rows.map(([k, v]) => (
        <div class="row">
          <span class="body-small c-muted grow">{k}</span>
          <span class="body-small c-text mixed" style={{ fontWeight: 500 }}>{v}</span>
        </div>
      ))}
    </div>
  )
}

export { GhostPill, PillButton, SlabRow, LinearProgress }
