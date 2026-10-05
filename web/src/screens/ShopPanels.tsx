import { useEffect, useState } from 'preact/hooks'
import * as api from '../api/client'
import * as M from '../api/models'
import { LinkSession } from '../api/account'
import { LinkState, linkMessage } from '../api/linkflow'
import { Icon } from '../components/Icon'
import { Dialog } from '../components/Overlay'
import { Slab, GhostPill, PillButton, TextButton, LinearProgress, SkinField } from '../components/Skin'
import { CurrentServiceSummary, RadioRow } from '../components/ShopParts'
import { asciiDigits, fa, formatPrice, jalali } from '../lib/format'

/** LinkAccountCard: "connect with Telegram", the code, the countdown and the checks. */
export function LinkAccountCard(props: {
  session: LinkSession | null; busy: boolean; state: LinkState; verification: boolean; checking: boolean; remaining: number
  onBegin: () => void; onOpenBot: () => void; onCheck: () => void; onCancel: () => void; onCopy: () => void
}) {
  const s = props.session
  return (
    <div class="mcard" style={{ borderRadius: 'var(--r-lg)' }}>
      <div class="col" style={{ padding: '24px', alignItems: 'center', textAlign: 'center', gap: '0' }}>
        <span style={{ borderRadius: 'var(--r-sm)', background: 'var(--primary-14)', padding: '12px', display: 'inline-flex' }}>
          <Icon name="security" size={26} color="var(--primary)" />
        </span>
        <span style={{ height: '12px' }} />
        <span class="bold c-text">اتصال امن حساب</span>
        <span class="c-text2">ورود با ربات؛ اطلاعات اتصال در همین دستگاه نگه داشته می‌شود.</span>
        <span style={{ height: '14px' }} />
        {s == null ? (
          <button class="m-btn" disabled={props.busy} onClick={props.onBegin}><Icon name="link" />اتصال با تلگرام</button>
        ) : (
          <div class="col full" style={{ alignItems: 'center', gap: '6px' }}>
            {!props.verification ? <>
              <button class="text-btn" disabled={props.remaining <= 0} onClick={props.onOpenBot}>
                <span class="headline-medium black c-highlight ltr" style={{ letterSpacing: '2px' }}>{s.code}</span>
              </button>
              <span class="c-text" style={{ whiteSpace: 'pre-line' }}>{'۱. «تأیید اتصال در تلگرام» را بزن.\n۲. پایین چت ربات، «شروع / Start» را بزن.\n۳. به قاجار برگرد؛ حساب خودکار متصل می‌شود.'}</span>
              <span class="c-text2">کد از قبل داخل لینک است؛ آن را تایپ یا اصلاح نکن.</span>
            </> : null}
            <span class="c-text2" style={{ padding: '8px 0' }}>زمان باقی‌مانده: {fa(`${Math.floor(props.remaining / 60)}:${String(props.remaining % 60).padStart(2, '0')}`)}</span>
            <span class="c-text">{linkMessage(props.state)}</span>
            {props.checking ? <div class="full" style={{ padding: '8px 0' }}><LinearProgress /></div> : null}
            <button class="m-btn full" style={{ marginTop: '8px' }} disabled={props.remaining <= 0} onClick={props.onOpenBot}>
              <Icon name="open_in_new" />{props.verification ? 'تکمیل تأیید در ربات' : 'تأیید اتصال در تلگرام'}
            </button>
            <button class="m-out full" disabled={props.checking || props.remaining <= 0} onClick={props.onCheck}>تأیید کردم؛ بررسی دوباره</button>
            {!props.verification ? <TextButton enabled={props.remaining > 0} onClick={props.onCopy}>کپی کد اتصال</TextButton> : null}
            <TextButton onClick={props.onCancel}>لغو درخواست ورود</TextButton>
          </div>
        )}
      </div>
    </div>
  )
}

/** RenewServiceDialog: what the service is now, the renewal plans and a custom size. */
export function RenewServiceDialog(props: { username: string; onDismiss: () => void; onRenewed: () => void; onTopUp: () => void }) {
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [options, setOptions] = useState<M.RenewOptions | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [selected, setSelected] = useState<string | null>(null)
  const [useCustom, setUseCustom] = useState(false)
  const [vol, setVol] = useState('')
  const [time, setTime] = useState('')
  const [current, setCurrent] = useState<M.ServiceDetails | null>(null)

  useEffect(() => {
    let alive = true
    setLoading(true)
    api.renewOptions(props.username).then(r => {
      if (!alive) return
      setOptions(r)
      setSelected(r.currentPlanCode ?? r.products[0]?.code ?? null)
      setUseCustom(r.custom.forced || (r.products.length === 0 && r.custom.enabled))
    }).catch(e => alive && setLoadError(api.publicMessage(e) || 'دریافت گزینه‌های تمدید ناموفق بود')).finally(() => alive && setLoading(false))
    api.service(props.username).then(s => alive && setCurrent(s)).catch(() => undefined)
    return () => { alive = false }
  }, [props.username])

  const o = options
  const v = parseInt(vol, 10)
  const customValid = !!o && Number.isFinite(v) && v >= o.custom.minVolumeGb && v <= o.custom.maxVolumeGb &&
    (o.custom.maxTimeDays <= o.custom.minTimeDays || ((parseInt(time, 10) || -1) >= o.custom.minTimeDays && (parseInt(time, 10) || -1) <= o.custom.maxTimeDays))
  const canConfirm = !busy && !loading && !!o && (useCustom ? customValid : selected != null)

  async function confirm() {
    if (!o) return
    setBusy(true); setActionError(null)
    try {
      const r = useCustom ? await api.confirmRenew(props.username, null, v, parseInt(time, 10) || null)
        : await api.confirmRenew(props.username, selected)
      if (r.requiresPayment) setActionError(`موجودی کیف پول کافی نیست؛ ${formatPrice(r.amountDue)} تومان کسری دارید. ابتدا از تب «کیف پول» شارژ کن، سپس دوباره تمدید کن.`)
      else if (r.completed) props.onRenewed()
      else setActionError('تمدید تأیید نشد؛ دوباره تلاش کن.')
    } catch (e) {
      setActionError(api.publicMessage(e) || 'تمدید ناموفق بود')
    } finally { setBusy(false) }
  }

  return (
    <Dialog title="تمدید سرویس" onDismiss={() => { if (!busy) props.onDismiss() }} dismissible={!busy}
      actions={<>
        <TextButton enabled={!busy} onClick={props.onDismiss}>بازگشت</TextButton>
        {actionError?.includes('کیف پول') ? <TextButton onClick={props.onTopUp}>شارژ کیف پول</TextButton> : null}
        <button class="m-btn" disabled={!canConfirm} onClick={confirm}>{busy ? 'در حال تمدید…' : 'تأیید تمدید'}</button>
      </>}>
      <span class="body-small c-text2 ellipsis ltr">{props.username}</span>
      {current ? <CurrentServiceSummary service={current} /> : null}
      {loading ? <div class="row" style={{ justifyContent: 'center', padding: '24px' }}><div class="spinner" /></div>
        : loadError ? <span class="c-error">{loadError}</span>
          : o ? <>
            {o.products.map(p => (
              <RadioRow selected={!useCustom && selected === p.code} onClick={() => { setUseCustom(false); setSelected(p.code) }}>
                <div class="row gap-sm">
                  <div class="col grow">
                    <span class="bold c-text">{p.name}</span>
                    <span class="body-small c-text2">{[p.volumeGb > 0 ? `${fa(p.volumeGb)} گیگ` : null, p.timeDays > 0 ? `${fa(p.timeDays)} روز` : null].filter(Boolean).join('  •  ')}</span>
                  </div>
                  <span class="bold c-primary" style={{ whiteSpace: 'nowrap' }}>{p.showPrice ? `${formatPrice(p.price)} تومان` : 'قیمت پس از تأیید'}</span>
                </div>
              </RadioRow>
            ))}
            {o.custom.enabled ? <>
              <RadioRow selected={useCustom} onClick={() => setUseCustom(true)}><span class="bold c-text">حجم/زمان دلخواه</span></RadioRow>
              {useCustom ? <>
                <SkinField label={`حجم (گیگابایت) بین ${fa(o.custom.minVolumeGb)} و ${fa(o.custom.maxVolumeGb)}`} value={vol} inputMode="numeric" dir="ltr"
                  onInput={x => setVol(asciiDigits(x).slice(0, 6))} />
                {o.custom.maxTimeDays > o.custom.minTimeDays ? (
                  <SkinField label={`زمان (روز) بین ${fa(o.custom.minTimeDays)} و ${fa(o.custom.maxTimeDays)}`} value={time} inputMode="numeric" dir="ltr"
                    onInput={x => setTime(asciiDigits(x).slice(0, 4))} />
                ) : null}
              </> : null}
            </> : null}
            {o.products.length === 0 && !o.custom.enabled ? <span>گزینه‌ای برای تمدید این سرویس در دسترس نیست.</span> : null}
            <span class="body-small">موجودی کیف پول: {formatPrice(o.balance)} تومان</span>
          </> : null}
      {actionError ? <span class="body-small c-error">{actionError}</span> : null}
    </Dialog>
  )
}

/** GhajarTickets: list, departments, create, thread, reply and close. */
export function Tickets() {
  const [tickets, setTickets] = useState<any[]>([])
  const [departments, setDepartments] = useState<any[]>([])
  const [dept, setDept] = useState(0)
  const [thread, setThread] = useState<any | null>(null)
  const [creating, setCreating] = useState(false)
  const [subject, setSubject] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [page, setPage] = useState(1)
  const [pages, setPages] = useState(1)
  const rows = (o: any, k: string): any[] => (Array.isArray(o?.[k]) ? o[k] : []).filter((x: any) => x && typeof x === 'object')

  async function reload(t = thread) {
    if (t) setThread(await api.support('ticket_thread', null, { t: String(t.tracking) }))
    else {
      const r = await api.support('tickets', null, { page: String(page) })
      setTickets(rows(r, 'items'))
      setPages(Math.max(1, Number(r.total_pages) || 1))
    }
  }
  async function run(f: () => Promise<void>) {
    if (busy) return
    setBusy(true); setError(null)
    try { await f() } catch (e) { setError(api.publicMessage(e) || 'دریافت پشتیبانی ناموفق بود') } finally { setBusy(false) }
  }
  useEffect(() => { void run(() => reload(null)) }, [page])

  return (
    <div class="col gap-10">
      <span class="title-large c-text">پشتیبانی و تیکت</span>
      {busy ? <LinearProgress /> : null}
      {error ? <span class="c-error">{error}</span> : null}
      <div class="row gap-sm wrap">
        <button class="m-out" disabled={busy} onClick={() => run(() => reload())}>بروزرسانی</button>
        {thread || creating
          ? <TextButton enabled={!busy} onClick={() => { setThread(null); setCreating(false); setMessage(''); void run(() => reload(null)) }}>بازگشت</TextButton>
          : <button class="m-btn" disabled={busy} onClick={() => run(async () => {
            const d = rows(await api.support('ticket_departments'), 'items')
            setDepartments(d); setDept(Number(d[0]?.id) || 0); setSubject(''); setMessage(''); setCreating(true)
          })}>تیکت جدید</button>}
      </div>
      {creating ? <>
        <div class="chips">
          {departments.map(d => <button class={'chip' + (dept === Number(d.id) ? ' active' : '')} disabled={busy} onClick={() => setDept(Number(d.id))}><span>{String(d.name ?? '')}</span></button>)}
        </div>
        <SkinField label="موضوع" value={subject} onInput={v => setSubject(v.slice(0, 150))} />
      </> : !thread ? <>
        {tickets.length === 0 && !busy ? <span>هنوز تیکتی ثبت نکرده‌ای.</span> : null}
        {tickets.map(t => (
          <button class="m-out full" style={{ height: 'auto', padding: '10px 18px', justifyContent: 'flex-start', textAlign: 'start' }} disabled={busy}
            onClick={() => run(async () => { setThread(await api.support('ticket_thread', null, { t: String(t.tracking) })); setMessage('') })}>
            <span class="col">
              <span>{String(t.subject || t.tracking)}</span>
              <span class="body-small c-text2">{String(t.department ?? '')} • {t.open ? 'باز' : 'بسته'}</span>
            </span>
          </button>
        ))}
        <div class="row gap-sm" style={{ justifyContent: 'center' }}>
          <TextButton enabled={!busy && page > 1} onClick={() => setPage(page - 1)}>قبلی</TextButton>
          <span>{fa(`${page} / ${pages}`)}</span>
          <TextButton enabled={!busy && page < pages} onClick={() => setPage(page + 1)}>بعدی</TextButton>
        </div>
      </> : null}
      {thread ? <>
        <span class="title-medium c-text">{String(thread.subject ?? '')}</span>
        {rows(thread, 'messages').map(m => {
          const fromSupport = m.sender === 'admin'
          return (
            <div class="mcard" style={{ borderColor: fromSupport ? 'var(--primary-55)' : 'var(--border)' }}>
              <div class="col gap-xs" style={{ padding: '12px' }}>
                <span class="label-large c-text">{fromSupport ? 'پشتیبانی' : 'شما'}</span>
                {m.reply_to ? <span class="body-small">↪ {String(m.reply_to.body ?? '')}</span> : null}
                <span class="c-text wrap-any" style={{ whiteSpace: 'pre-line' }}>{String(m.body ?? '')}</span>
                {Number(m.media_count) > 0 ? <span class="body-small c-text2">پیوست: در پنل کامل پشتیبانی مشاهده کن.</span> : null}
                <span class="label-small c-muted">{String(m.time ?? '')}</span>
              </div>
            </div>
          )
        })}
        {thread.open ? <button class="m-out" disabled={busy} onClick={() => run(async () => { await api.support('ticket_close', { t: thread.tracking }); await reload() })}>بستن تیکت</button> : null}
      </> : null}
      {creating || thread?.open ? <>
        <SkinField label="متن پیام" value={message} multiline minLines={3} onInput={v => setMessage(v.slice(0, 4000))} />
        <button class="m-btn" disabled={busy || !message.trim() || (creating && dept <= 0)} onClick={() => run(async () => {
          if (creating) {
            const created = await api.support('ticket_create', { department_id: dept, subject, text: message })
            setThread(await api.support('ticket_thread', null, { t: String(created.tracking) }))
            setCreating(false)
          } else {
            await api.support('ticket_reply', { t: thread.tracking, text: message })
            await reload()
          }
          setMessage('')
        })}>ارسال</button>
      </> : null}
    </div>
  )
}

/** GhajarTransactionHistory: thirty days of wallet movements, paged. */
export function TransactionHistory(props: { revision: number }) {
  const [page, setPage] = useState(1)
  const [refresh, setRefresh] = useState(0)
  const [pages, setPages] = useState(0)
  const [items, setItems] = useState<any[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    let alive = true
    setBusy(true); setError(null)
    api.transactions(page).then(r => {
      if (!alive) return
      setPages(Number(r.total_pages) || 0)
      setItems((Array.isArray(r.items) ? r.items : []).filter((x: any) => x && typeof x === 'object'))
    }).catch(e => alive && setError(api.publicMessage(e))).finally(() => alive && setBusy(false))
    return () => { alive = false }
  }, [page, refresh, props.revision])
  const has = (v: any) => v != null && String(v).trim() !== '' && String(v) !== 'null'
  return (
    <div class="col gap-md">
      <span class="title-large bold c-text">تاریخچه تراکنش‌ها</span>
      <span class="body-small">گردش کیف پول در ۳۰ روز گذشته</span>
      <button class="m-out" style={{ alignSelf: 'flex-start' }} disabled={busy} onClick={() => setRefresh(refresh + 1)}>بروزرسانی تاریخچه</button>
      {busy ? <LinearProgress /> : null}
      {error ? <span class="c-error">{error}</span> : null}
      {!busy && !error && items.length === 0 ? <span>تراکنشی ثبت نشده است.</span> : null}
      {items.map(it => {
        const credit = it.direction === 'credit'
        return (
          <div class="mcard"><div class="col gap-6" style={{ padding: '16px' }}>
            <span class="bold c-text">{String(it.category_label ?? '')}</span>
            <span style={{ color: credit ? 'var(--primary)' : 'var(--error)' }}>{credit ? '+' : '−'}{formatPrice(Number(it.amount) || 0)} تومان</span>
            {has(it.balance_after) ? <span class="c-text">موجودی پس از تراکنش: {formatPrice(Number(it.balance_after) || 0)} تومان</span> : null}
            {has(it.description) ? <span class="c-text">{String(it.description)}</span> : null}
            {has(it.order_id) ? <span class="c-text">کد فاکتور: <span class="ltr">{String(it.order_id)}</span></span> : null}
            <span class="body-small">{String(it.created_at ?? '')}</span>
          </div></div>
        )
      })}
      <div class="row gap-md" style={{ justifyContent: 'center' }}>
        <button class="m-out" disabled={page <= 1 || busy} onClick={() => setPage(page - 1)}>قبلی</button>
        <span>{fa(`${page} / ${Math.max(1, pages)}`)}</span>
        <button class="m-out" disabled={page >= pages || busy} onClick={() => setPage(page + 1)}>بعدی</button>
      </div>
    </div>
  )
}

/** MarketAnnouncements: a shop's announcements; a discount one offers its code. */
export function Announcements(props: { items: M.MarketAnnouncement[]; onUseCode: (code: string) => void }) {
  const [expanded, setExpanded] = useState(false)
  return (
    <Slab spacing={8} accent="var(--highlight)">
      <button class="row full" onClick={() => setExpanded(!expanded)}>
        <span class="bold c-text grow" style={{ textAlign: 'start' }}>📣 اعلان‌های این فروشگاه</span>
        <span class="c-muted">{fa(props.items.length)}{expanded ? ' ▲' : ' ▼'}</span>
      </button>
      {(expanded ? props.items : props.items.slice(0, 1)).map(a => (
        <div class="col gap-xs" style={{ borderRadius: 'var(--r-md)', background: 'var(--card)', padding: '12px' }}>
          <span class="bold c-text">{a.title}</span>
          <span class="body-small c-text2 wrap-any" style={{ whiteSpace: 'pre-line' }}>{a.body}</span>
          <span class="label-small c-muted">{jalali(a.publishedAt)}</span>
          {a.discountCode ? <PillButton text={`استفاده از کد ${a.discountCode}`} icon="card_giftcard" minHeight={40} onClick={() => props.onUseCode(a.discountCode)} /> : null}
        </div>
      ))}
    </Slab>
  )
}

export { GhostPill }
