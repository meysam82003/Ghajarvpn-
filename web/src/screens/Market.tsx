import { ComponentChildren } from 'preact'
import { useEffect, useMemo, useState } from 'preact/hooks'
import * as api from '../api/client'
import * as M from '../api/models'
import { useStore } from '../lib/store'
import { Icon } from '../components/Icon'
import { Dialog } from '../components/Overlay'
import { Slab, SlabRow, SlabDivider, PillButton, GhostPill, TextButton, Rail, SkinField, SkinEmpty, SkinLoading, SkinError, SlidingSegments, TabRail } from '../components/Skin'
import { MarketLogoImg, SectionTitle, ServiceTypeGrid, ChipFlowRow, ProductCard, CustomServiceCard, OwnedServiceCard, ServiceSortChips, panelIcon, RadioRow } from '../components/ShopParts'
import { Announcements } from './ShopPanels'
import { AddToAppSheet } from '../components/AddToApp'
import { asciiDigits, fa, formatPrice, jalali, ltr } from '../lib/format'
import { lookStore } from '../theme/look'
import { shopOpenRequest, paymentReturn, copyText } from '../state/ui'
import { isTrustedPaymentUrl, TELEGRAM_BOT_URL } from '../api/config'

type Route = { kind: 'list' } | { kind: 'shop'; id: number; code: string; renew: string } | { kind: 'order'; shopId: number; order: M.MarketOrder } | { kind: 'register' }

type MarketSort = 'BEST' | 'MOST_REVIEWED' | 'WORST' | 'DISCOUNTED' | 'CHEAPEST' | 'PRICIEST'
const SORTS: [MarketSort, string][] = [['BEST', 'بهترین‌ها'], ['MOST_REVIEWED', 'پرنظرترین'], ['WORST', 'منفورترین'], ['DISCOUNTED', 'در تخفیف'], ['CHEAPEST', 'ارزان‌ترین'], ['PRICIEST', 'گران‌ترین']]

function sortShops(list: M.MarketShop[], sort: MarketSort): M.MarketShop[] {
  const l = [...list]
  switch (sort) {
    case 'BEST': return l.sort((a, b) => (+(b.reviewCount > 0) - +(a.reviewCount > 0)) || (b.stars * Math.min(b.reviewCount, 20) / 20 - a.stars * Math.min(a.reviewCount, 20) / 20) || (b.reviewCount - a.reviewCount))
    case 'MOST_REVIEWED': return l.sort((a, b) => (b.reviewCount - a.reviewCount) || (b.stars - a.stars))
    case 'WORST': return l.filter(s => s.reviewCount > 0).sort((a, b) => (a.stars - b.stars) || (b.reviewCount - a.reviewCount))
    case 'DISCOUNTED': return l.filter(s => s.discountCount > 0).sort((a, b) => (b.discountCount - a.discountCount) || (b.stars - a.stars))
    case 'CHEAPEST': return l.sort((a, b) => (+(b.minPrice > 0) - +(a.minPrice > 0)) || (a.minPrice - b.minPrice))
    case 'PRICIEST': return l.sort((a, b) => b.maxPrice - a.maxPrice)
  }
}

const STATUS = { AWAITING: 'awaiting_receipt', REVIEW: 'review', PAID: 'paid', REJECTED: 'rejected', FAILED: 'failed' }

export function openTelegram(handle: string) {
  const clean = handle.replace(/^@/, '').trim()
  if (!clean) return
  openExternal(clean.startsWith('http') ? clean : `https://t.me/${clean}`)
}
export function openExternal(url: string) {
  const w = window.open(url, '_blank')
  if (w) { try { w.opener = null } catch { /* ignore */ } } else location.href = url
}

/** GhajarMarketScreen: Ghajar first, then every other shop, browsable without an account. */
export function MarketScreen(props: { active: boolean; signedIn: boolean; onSignIn: () => void; ghajarEntry: ComponentChildren; onGhajarLogo?: (v: number) => void }) {
  const [route, setRoute] = useState<Route>({ kind: 'list' })
  const [feed, setFeed] = useState<M.MarketFeed | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    if (!props.active) return
    let alive = true
    setBusy(true); setError(null)
    api.marketShops().then(f => { if (alive) { setFeed(f); props.onGhajarLogo?.(f.ghajarLogoVersion) } })
      .catch(e => alive && setError(api.publicMessage(e) || 'فهرست فروشگاه‌ها در دسترس نیست'))
      .finally(() => alive && setBusy(false))
    return () => { alive = false }
  }, [props.active, refreshKey])

  const openReq = useStore(shopOpenRequest)
  useEffect(() => {
    if (openReq && openReq.shopId > 0) { setRoute({ kind: 'shop', id: openReq.shopId, code: openReq.code, renew: openReq.renew ?? '' }); shopOpenRequest.set(null) }
  }, [openReq])

  const onRegister = () => { if (props.signedIn) setRoute({ kind: 'register' }); else props.onSignIn() }

  if (route.kind === 'list' && (!feed || !feed.enabled)) {
    return (
      <div class="col gap-md">
        {feed?.enabled !== false ? <AddShopCard onRegister={onRegister} /> : null}
        <Rail label="فروشگاه‌ها" />
        {props.ghajarEntry}
        {busy && !feed ? <SkinLoading label="در حال گرفتن فهرست فروشگاه‌ها" /> : error && !feed ? <SkinError message={error} retryText="تلاش دوباره" onRetry={() => setRefreshKey(refreshKey + 1)} /> : null}
      </div>
    )
  }
  if (!feed) return <SkinLoading label="فروشگاه‌ها" />
  switch (route.kind) {
    case 'list': return <MarketList feed={feed} signedIn={props.signedIn} onOpen={id => setRoute({ kind: 'shop', id, code: '', renew: '' })} onRegister={onRegister} onRefresh={() => setRefreshKey(refreshKey + 1)} ghajarEntry={props.ghajarEntry} />
    case 'shop': return <MarketShopHome shopId={route.id} initialCode={route.code} initialRenew={route.renew} signedIn={props.signedIn} onSignIn={props.onSignIn}
      onBack={() => { setRoute({ kind: 'list' }); setRefreshKey(refreshKey + 1) }} onOrdered={order => setRoute({ kind: 'order', shopId: route.id, order })} />
    case 'order': return <MarketOrderPage order={route.order} onBack={() => setRoute({ kind: 'shop', id: route.shopId, code: '', renew: '' })} />
    case 'register': return <MarketRegisterPage onBack={() => setRoute({ kind: 'list' })} />
  }
}

function AddShopCard(props: { onRegister: () => void }) {
  return (
    <Slab onClick={props.onRegister} accent="var(--primary)">
      <div class="row">
        <Icon name="storefront" color="var(--primary)" />
        <span style={{ width: '8px' }} />
        <div class="col grow">
          <span class="bold c-text">افزودن فروشگاه</span>
          <span class="label-medium c-text2">فروشگاه خودت را ثبت کن: ثبت‌نام، تأیید و مدیریت از ربات</span>
        </div>
        <Icon name="chevron_left" color="var(--muted)" mirror={false} />
      </div>
    </Slab>
  )
}

function MarketList(props: { feed: M.MarketFeed; signedIn: boolean; onOpen: (id: number) => void; onRegister: () => void; onRefresh: () => void; ghajarEntry: ComponentChildren }) {
  const look = useStore(lookStore)
  const [sort, setSort] = useState<MarketSort>('BEST')
  const mine = props.feed.shops.filter(s => s.myServices > 0).sort((a, b) => b.myServices - a.myServices)
  const shown = sortShops(props.feed.shops, sort)
  return (
    <div class="col gap-md">
      <AddShopCard onRegister={props.onRegister} />
      {mine.length ? <>
        <Rail label="فروشگاه‌هایی که از آن‌ها سرویس داری" />
        <Slab spacing={0}>
          {mine.map((s, i) => <>{i > 0 ? <SlabDivider /> : null}<SlabRow title={s.name} subtitle={`${fa(s.myServices)} سرویس`} icon="shopping_bag" chevron onClick={() => props.onOpen(s.id)} /></>)}
        </Slab>
      </> : null}
      <Rail label="فروشگاه‌ها" />
      <span class="label-medium c-text2">همهٔ فروشگاه‌ها، داخل همین برنامه. خرید، پرداخت و تحویل کانفیگ بدون رفتن به تلگرام.</span>
      {props.feed.shops.length ? <TabRail tabs={SORTS.map(([, l]) => ({ label: l }))} selected={SORTS.findIndex(([k]) => k === sort)} onSelect={i => setSort(SORTS[i][0])} style={look.storeTabStyle} /> : null}
      {props.ghajarEntry}
      {props.feed.shops.length ? <>
        {shown.length === 0 ? <SkinEmpty icon="shopping_bag" hint="یکی از فیلترهای بالا را عوض کنید."
          title={sort === 'DISCOUNTED' ? 'هیچ فروشگاهی کد تخفیف فعال ندارد' : sort === 'WORST' ? 'هنوز هیچ فروشگاهی نظر ثبت‌شده ندارد' : 'چیزی با این فیلتر پیدا نشد'} /> : null}
        {shown.length ? <div class="card-grid">{shown.map(s => <MarketShopCard shop={s} onOpen={() => props.onOpen(s.id)} />)}</div> : null}
      </> : null}
      {!props.signedIn ? (
        <Slab accent="var(--primary)" spacing={4}>
          <span class="bold c-text">برای خرید از فروشگاه‌ها، حساب را یک‌بار متصل کن</span>
          <span class="label-small c-text2">دیدن فروشگاه‌ها، قیمت‌ها و نظرها نیازی به اتصال ندارد.</span>
        </Slab>
      ) : null}
      <GhostPill text="بازخوانی فهرست" icon="refresh" onClick={props.onRefresh} />
    </div>
  )
}

export function StarRow(props: { stars: number; size?: number }) {
  const filled = Math.max(0, Math.min(5, Math.trunc(props.stars)))
  return (
    <span class="row" aria-label={`${fa(filled)} از ۵`}>
      {[0, 1, 2, 3, 4].map(i => <Icon name={i < filled ? 'star' : 'star_border'} size={props.size ?? 14} color={i < filled ? 'var(--warning)' : 'var(--muted)'} />)}
    </span>
  )
}

function MarketShopCard(props: { shop: M.MarketShop; onOpen: () => void }) {
  const s = props.shop
  const lines = [
    s.gbPrice > 0 ? `💾 هر گیگ ${formatPrice(s.gbPrice)} تومان` : null,
    s.minPrice > 0 ? `🏷 ارزان‌ترین ${formatPrice(s.minPrice)}${s.maxPrice > s.minPrice ? ` · گران‌ترین ${formatPrice(s.maxPrice)}` : ''} تومان${s.productCount > 0 ? ` · ${fa(s.productCount)} پلن` : ''}` : null,
    s.panelMinPrice > 0 && s.panelCount > 1 ? `🌐 ${fa(s.panelCount)} سرور · از ${formatPrice(s.panelMinPrice)}${s.panelMaxPrice > s.panelMinPrice ? ` تا ${formatPrice(s.panelMaxPrice)}` : ''} تومان` : null
  ].filter(Boolean) as string[]
  return (
    <Slab onClick={props.onOpen} spacing={8}>
      <div class="row">
        <MarketLogoImg url={api.marketLogoUrl(s.id, s.logoVersion)} size={48} />
        <span style={{ width: '8px' }} />
        <div class="col grow">
          <div class="row gap-xs">
            <span class="bold c-text ellipsis">{s.name}</span>
            {s.tick?.earned ? <Icon name="verified" size={15} color="var(--primary)" title="تیک اعتماد" /> : s.verified ? <Icon name="shield" size={14} color="var(--primary)" title="تأییدشده" /> : null}
          </div>
          {s.tick?.earned ? <span class="label-small c-primary ellipsis">{s.tick.label || 'قابل اعتماد از نظر خریداران'}</span> : null}
          {s.tagline ? <span class="label-small c-text2 ellipsis">{s.tagline}</span> : null}
        </div>
        <Icon name="chevron_left" color="var(--muted)" mirror={false} />
      </div>
      <div class="row gap-sm">
        <StarRow stars={s.stars} />
        <span class="label-small grow" style={{ color: s.reviewCount > 0 ? 'var(--text2)' : 'var(--muted)' }}>
          {s.reviewCount > 0 ? `${fa(s.stars.toFixed(1))} از ۵ · ${fa(s.satisfaction)}٪ رضایت · ${fa(s.reviewCount)} نظر` : 'هنوز نظری ثبت نشده'}
        </span>
      </div>
      {lines.map(l => <span class="label-small c-text2">{l}</span>)}
      {s.testAvailable || s.discountCount > 0 ? (
        <div class="row gap-sm">
          {s.testAvailable ? <span class="label-small c-premium">🎁 تست رایگان</span> : null}
          {s.discountCount > 0 ? <span class="label-small c-primary">{fa(s.discountCount)} کد تخفیف</span> : null}
        </div>
      ) : null}
      {s.myServices > 0 ? <span class="label-small c-primary">📦 {fa(s.myServices)} سرویس فعال شما در این فروشگاه</span> : null}
      {!s.canSell && s.closedReason ? <span class="label-small c-warning">{s.closedReason}</span> : null}
    </Slab>
  )
}

const MARKET_TABS = ['خرید', 'سرویس‌ها', 'پیام‌ها', 'کیف پول', 'پشتیبانی', 'تراکنش‌ها']

function MarketShopHome(props: { shopId: number; initialCode: string; initialRenew: string; signedIn: boolean; onSignIn: () => void; onBack: () => void; onOrdered: (o: M.MarketOrder) => void }) {
  const look = useStore(lookStore)
  const [code, setCode] = useState(props.initialCode)
  const [home, setHome] = useState<M.MarketHome | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(true)
  const [reload, setReload] = useState(0)
  const [tab, setTab] = useState(props.initialRenew ? 1 : 0)
  const [report, setReport] = useState(false)
  const [tick, setTick] = useState(false)
  useEffect(() => {
    let alive = true
    setBusy(true)
    api.marketHome(props.shopId, code).then(h => { if (alive) { setHome(h); setError(null) } })
      .catch(e => alive && setError(api.publicMessage(e) || 'این فروشگاه باز نشد')).finally(() => alive && setBusy(false))
    return () => { alive = false }
  }, [props.shopId, reload, code])

  return (
    <div class="col gap-md">
      <GhostPill text="همهٔ فروشگاه‌ها" icon="chevron_left" onClick={props.onBack} />
      {!home ? (busy ? <SkinLoading label="در حال باز کردن فروشگاه" /> : <SkinError message={error ?? 'این فروشگاه باز نشد'} retryText="تلاش دوباره" onRetry={() => setReload(reload + 1)} />) : (() => {
        const shop = home.shop
        const links = [shop.telegramBot ? ['ربات', shop.telegramBot] : null, shop.telegramChannel ? ['کانال', shop.telegramChannel] : null, shop.supportContact ? ['پشتیبانی', shop.supportContact] : null].filter(Boolean) as [string, string][]
        return <>
          <Slab spacing={8}>
            <div class="row">
              <MarketLogoImg url={api.marketLogoUrl(shop.id, shop.logoVersion)} size={56} />
              <span style={{ width: '12px' }} />
              <div class="col grow">
                <div class="row gap-xs">
                  <span class="title-medium bold c-text ellipsis">{shop.name}</span>
                  {shop.tick?.earned ? <Icon name="verified" size={17} color="var(--primary)" /> : shop.verified ? <Icon name="shield" size={16} color="var(--primary)" /> : null}
                </div>
                {shop.tick?.earned ? <button class="label-small c-primary" style={{ textAlign: 'start' }} onClick={() => setTick(true)}>{shop.tick.label || 'قابل اعتماد از نظر خریداران'}</button> : null}
                {shop.tagline ? <span class="label-medium c-text2 clamp2">{shop.tagline}</span> : null}
                <div class="row gap-xs"><StarRow stars={shop.stars} /><span class="label-small c-muted">{shop.reviewCount > 0 ? `${fa(shop.stars.toFixed(1))} · ${fa(shop.reviewCount)} نظر` : 'بدون نظر'}</span></div>
              </div>
            </div>
            {shop.description ? <span class="body-small c-text2 wrap-any" style={{ whiteSpace: 'pre-line' }}>{shop.description}</span> : null}
            {links.map(([label, handle]) => (
              <button class="row full gap-sm" onClick={() => openTelegram(handle)}>
                <Icon name="support_agent" size={16} color="var(--primary)" />
                <span class="label-small c-muted grow" style={{ textAlign: 'start' }}>{label}</span>
                <span class="label-medium c-text ellipsis mixed">{handle.startsWith('http') ? handle : '@' + handle.replace(/^@/, '')}</span>
                <Icon name="open_in_new" size={14} color="var(--muted)" />
              </button>
            ))}
          </Slab>
          <GhostPill text="گزارش این فروشگاه" icon="report" minHeight={36} onClick={() => (props.signedIn ? setReport(true) : props.onSignIn())} />
          {report ? <ReportDialog shopName={shop.name} onDismiss={() => setReport(false)} send={(r, b) => api.marketReport(shop.id, r, b).catch(e => api.publicMessage(e) || 'گزارش ثبت نشد')} /> : null}
          {tick && shop.tick ? <TickDialog tick={shop.tick} onDismiss={() => setTick(false)} /> : null}
          {home.appliedCode ? (
            <Slab accent={home.appliedOk ? 'var(--primary)' : 'var(--error)'} spacing={4}>
              <span class="bold" style={{ color: home.appliedOk ? 'var(--primary)' : 'var(--error)' }}>
                {home.appliedOk ? `🎟 کد ${home.appliedCode} روی همهٔ پلن‌ها و سرورها اعمال شد` : `کد ${home.appliedCode}: ${home.appliedMsg || 'معتبر نیست'}`}
              </span>
              <GhostPill text="برداشتن کد" minHeight={36} onClick={() => setCode('')} />
            </Slab>
          ) : null}
          {home.discountCodes.length && tab === 0 ? <DiscountCodes codes={home.discountCodes} applied={home.appliedOk ? home.appliedCode : ''} onApply={setCode} /> : null}
          <TabRail style={look.storeTabStyle} selected={tab} onSelect={setTab} tabs={MARKET_TABS.map((l, i) => ({
            label: i === 1 && shop.myServices > 0 ? `${l} (${fa(shop.myServices)})` : l,
            badge: i === 2 && home.unread + home.announcements.length > 0 ? home.unread + home.announcements.length : null
          }))} />
          {tab !== 0 && !props.signedIn ? (
            <Slab accent="var(--primary)" spacing={4} onClick={props.onSignIn}>
              <span class="bold c-text">برای این بخش، حساب را یک‌بار متصل کن</span>
              <span class="label-small c-text2">دیدن فروشگاه و پلن‌ها بدون اتصال هم ممکن است؛ خرید، سرویس‌ها، کیف پول و پشتیبانی به حساب نیاز دارند.</span>
            </Slab>
          ) : tab === 0 ? <BuyTab home={home} signedIn={props.signedIn} onSignIn={props.onSignIn} onOrdered={props.onOrdered} />
            : tab === 1 ? <ServicesTab home={home} onOrdered={props.onOrdered} initialRenew={props.initialRenew} />
              : tab === 2 ? <div class="col gap-md">
                {home.announcements.length ? <Announcements items={home.announcements} onUseCode={c => { setCode(c); setTab(0) }} /> : null}
                <MessagesTab shopId={props.shopId} onRead={() => setReload(reload + 1)} />
              </div>
                : tab === 3 ? <WalletTab home={home} onOrdered={props.onOrdered} />
                  : tab === 4 ? <SupportTab shopId={props.shopId} />
                    : <TransactionsTab shopId={props.shopId} />}
        </>
      })()}
    </div>
  )
}

function DiscountCodes(props: { codes: M.MarketPublicCode[]; applied: string; onApply: (c: string) => void }) {
  return (
    <div class="col gap-sm">
      <span class="title-small bold c-text">🎟 کدهای تخفیف این فروشگاه</span>
      {props.codes.map(c => {
        const isApplied = props.applied.toLowerCase() === c.code.toLowerCase()
        const days = c.expiresAt > 0 ? Math.max(0, Math.floor((c.expiresAt - Date.now() / 1000) / 86400)) : null
        const facts = [`${fa(c.percent % 1 === 0 ? c.percent : c.percent)}٪ تخفیف`, c.left >= 0 ? `${fa(c.left)} بار باقی‌مانده` : null, days != null ? (days > 0 ? `${fa(days)} روز مانده` : 'امروز تمام می‌شود') : null].filter(Boolean).join(' • ')
        return (
          <Slab spacing={6} padding={12} accent={isApplied ? 'var(--primary)' : 'var(--premium)'}>
            <span class="title-medium bold c-primary ellipsis" style={{ direction: 'ltr', textAlign: 'left', fontFamily: 'monospace' }}>{c.code}</span>
            <span class="body-small c-text2">{facts}</span>
            {isApplied ? <span class="label-large bold c-good">✓ اعمال شد</span> : <GhostPill text="اعمال این کد" minHeight={38} onClick={() => props.onApply(c.code)} />}
          </Slab>
        )
      })}
    </div>
  )
}

function TickDialog(props: { tick: M.MarketTick; onDismiss: () => void }) {
  return (
    <Dialog title={props.tick.label || 'قابل اعتماد از نظر خریداران'} onDismiss={props.onDismiss} actions={<TextButton onClick={props.onDismiss}>بستن</TextButton>}>
      <span class="label-small c-text2">این تیک خودکار و فقط از روی عملکرد فروشگاه داده می‌شود؛ هیچ اطلاعات شخصی لازم نیست.</span>
      {props.tick.criteria.map(c => (
        <div class="row gap-6"><span>{c.ok ? '✅' : '⬜️'}</span><span class="label-medium" style={{ color: c.ok ? 'var(--text)' : 'var(--text2)' }}>{c.label} — {fa(`${c.value} / ${c.target}`)}</span></div>
      ))}
    </Dialog>
  )
}

function ReportDialog(props: { shopName: string; onDismiss: () => void; send: (reason: string, body: string) => Promise<string> }) {
  const reasons: [string, string][] = [['no_delivery', 'سرویس تحویل نشد'], ['not_working', 'سرویس کار نمی‌کند'], ['scam', 'کلاهبرداری / پول گرفت و جواب نداد'], ['fake', 'نظر یا تبلیغ جعلی'], ['content', 'محتوای نامناسب'], ['other', 'دیگر']]
  const [reason, setReason] = useState(reasons[0][0])
  const [body, setBody] = useState('')
  const [result, setResult] = useState<string | null>(null)
  const [sending, setSending] = useState(false)
  return (
    <Dialog title={`گزارش «${props.shopName}»`} onDismiss={props.onDismiss}
      actions={<>
        <TextButton onClick={props.onDismiss}>بستن</TextButton>
        <TextButton enabled={!sending && result == null} onClick={async () => { setSending(true); setResult(await props.send(reason, body.trim())); setSending(false) }}>{sending ? 'در حال ارسال…' : 'ارسال گزارش'}</TextButton>
      </>}>
      {reasons.map(([k, l]) => <RadioRow selected={reason === k} onClick={() => setReason(k)}><span class="body-small c-text">{l}</span></RadioRow>)}
      <SkinField label="توضیح (اختیاری)" value={body} onInput={v => setBody(v.slice(0, 800))} />
      {result ? <span class="label-small c-primary">{result}</span> : null}
    </Dialog>
  )
}

function ChoiceRow(props: { title: string; subtitle?: string | null; value?: string | null; selected: boolean; onSelect: () => void }) {
  return (
    <button class="row full" onClick={props.onSelect} style={{ borderRadius: 'var(--r-md)', background: props.selected ? 'var(--card)' : 'var(--card2)', padding: '10px 12px', textAlign: 'start' }}>
      <span style={{ width: '16px', height: '16px', borderRadius: '999px', background: props.selected ? 'var(--primary)' : 'var(--border)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', flex: 'none' }}>
        {props.selected ? <Icon name="check" size={11} color="var(--on-primary)" /> : null}
      </span>
      <span style={{ width: '8px' }} />
      <span class="col grow">
        <span class="c-text ellipsis" style={{ fontWeight: 500 }}>{props.title}</span>
        {props.subtitle ? <span class="label-small c-text2 ellipsis">{props.subtitle}</span> : null}
      </span>
      {props.value ? <span class="label-medium bold c-primary" style={{ whiteSpace: 'nowrap' }}>{props.value}</span> : null}
    </button>
  )
}

function InfoLine(props: { label: string; value: string }) {
  return (
    <div class="row full">
      <span class="label-small c-muted grow">{props.label}</span>
      <span class="label-medium c-text ellipsis mixed" style={{ fontWeight: 500 }}>{props.value}</span>
    </div>
  )
}

interface Pending { title: string; price: number; productCode: string; custom?: boolean; volumeGb?: number; timeDays?: number }

function BuyTab(props: { home: M.MarketHome; signedIn: boolean; onSignIn: () => void; onOrdered: (o: M.MarketOrder) => void }) {
  const { home } = props
  const shop = home.shop, catalog = home.catalog
  const [panelCode, setPanelCode] = useState<string | null>(catalog.panels[0]?.code ?? null)
  const [category, setCategory] = useState<M.Category | null>(null)
  const [duration, setDuration] = useState<M.TimeRange | null>(null)
  const [customMode, setCustomMode] = useState(false)
  const [showTests, setShowTests] = useState(false)
  const [gb, setGb] = useState('')
  const [days, setDays] = useState('')
  const [quote, setQuote] = useState<M.CustomQuote | null>(null)
  const [pending, setPending] = useState<Pending | null>(null)
  const [method, setMethod] = useState<string | null>(null)
  const [discountCode, setDiscountCode] = useState(home.appliedOk ? home.appliedCode : '')
  const [discounted, setDiscounted] = useState<number | null>(null)
  const [discountNote, setDiscountNote] = useState<string | null>(null)
  const [starting, setStarting] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const panel = catalog.panels.find(p => p.code === panelCode) ?? null
  const testPanels = home.testEnabled ? catalog.panels.filter(p => p.test) : []
  const customOn = !!panel?.custom && panel.gbPrice > 0
  useEffect(() => { if (!customOn && customMode) setCustomMode(false) }, [customOn])

  async function start(order: () => Promise<M.MarketOrder>) {
    if (!props.signedIn) { props.onSignIn(); return }
    setStarting(true); setActionError(null)
    try { const o = await order(); setPending(null); props.onOrdered(o) } catch (e) { setActionError(api.publicMessage(e) || 'ثبت سفارش انجام نشد') } finally { setStarting(false) }
  }

  const onPanel = (() => {
    const l = catalog.products.filter(p => !p.location || p.location === '/all' || p.location === panel?.name)
    return l.length ? l : catalog.products
  })()
  const usedCategories = catalog.categories.filter(c => onPanel.some(p => p.category === c.name || p.category === c.id))
  const durations = [...new Set(onPanel.map(p => p.timeDays).filter(d => d > 0))].sort((a, b) => a - b).map(d => ({
    days: d, name: d === 30 ? '⏳ یک ماه' : d === 60 ? '⏳ دو ماه' : d === 90 ? '⏳ سه ماه' : d === 180 ? '⏳ شش ماه' : d === 365 ? '⏳ یک سال' : `⏳ ${fa(d)} روز`
  }))
  const shown = onPanel.filter(p => (!category || p.category === category.name || p.category === category.id) && (!duration || p.timeDays === duration.days))
  const bestValue = shown.length > 1 ? shown.filter(p => p.price > 0 && p.volumeGb > 0).sort((a, b) => a.price / a.volumeGb - b.price / b.volumeGb)[0]?.code : undefined

  return (
    <div class="col gap-md">
      {!shop.canSell ? <SkinError message={shop.closedReason || 'این فروشگاه فعلاً فروش جدید ندارد.'} /> : null}
      {!home.reachable ? <SkinError message="سرور این فروشگاه همین حالا جواب نداد؛ پلن‌ها ممکن است کامل نباشند. چند لحظه بعد دوباره باز کن." /> : null}
      {home.blocked ? <SkinError message="این فروشگاه امکان خرید را برای حساب شما بسته است." /> : null}
      {actionError ? <span class="label-medium c-error">{actionError}</span> : null}
      {testPanels.length ? <>
        <GhostPill text={home.testUsed ? 'سرویس تست این فروشگاه را گرفته‌اید' : 'دریافت سرویس تست رایگان'} icon="card_giftcard" accent="var(--premium)"
          enabled={!home.testUsed && !starting} onClick={() => (props.signedIn ? setShowTests(!showTests) : props.onSignIn())} />
        {showTests ? testPanels.map(p => (
          <Slab spacing={0}>
            <SlabRow title={[p.flag, p.name].filter(Boolean).join(' ')} icon="card_giftcard" accent="var(--premium)" chevron enabled={!starting}
              subtitle={[p.testMb > 0 ? (p.testMb >= 1024 ? `${fa((p.testMb / 1024).toFixed(p.testMb >= 10240 ? 0 : 1))} گیگ` : `${fa(p.testMb)} مگ`) : null, p.testHours > 0 ? `${fa(p.testHours)} ساعت` : null].filter(Boolean).join(' · ') || 'سرویس آزمایشی'}
              onClick={() => { setShowTests(false); void start(() => api.marketOrderStart({ shopId: shop.id, productCode: '', panelCode: p.code, method: 'free', kind: 'test' })) }} />
          </Slab>
        )) : null}
      </> : null}
      <SectionTitle title="۱. انتخاب سرویس" subtitle="قیمت و موجودی مستقیماً از پنل فروشنده دریافت می‌شود" />
      {catalog.panels.length === 0 ? <SkinEmpty title="هیچ سرویسی از پنل فروشنده دریافت نشد" hint="چند لحظه بعد دوباره بررسی کن؛ اگر ادامه داشت، از پشتیبانی فروشگاه بپرس." icon="shopping_bag" />
        : <ServiceTypeGrid items={catalog.panels} selected={panel} label={p => [p.name, p.flag].filter(Boolean).join(' ')} icon={p => panelIcon(p.name)} onSelect={p => { setPanelCode(p.code); setQuote(null) }} same={(a, b) => a.code === b.code} />}
      {usedCategories.length && !customMode ? <ChipFlowRow allLabel="همه دسته‌ها" items={usedCategories} selected={category} label={c => c.name} onSelect={setCategory} same={(a, b) => a.name === b.name} /> : null}
      {durations.length > 1 && !customMode ? <ChipFlowRow allLabel="همه مدت‌ها" items={durations} selected={duration} label={d => d.name} onSelect={setDuration} same={(a, b) => a.days === b.days} /> : null}
      {customOn ? <SlidingSegments labels={['پلن‌های آماده', 'سرویس سفارشی']} selected={customMode ? 1 : 0} onSelect={i => setCustomMode(i === 1)} /> : null}
      {customMode && panel ? <>
        <CustomServiceCard traffic={gb} days={days} quote={quote}
          onTraffic={v => { setGb(asciiDigits(v).slice(0, 5)); setQuote(null) }} onDays={v => { setDays(asciiDigits(v).slice(0, 4)); setQuote(null) }}
          onQuote={() => {
            const g = +gb || 0, d = +days || 0
            const inRange = g > 0 && d > 0 && (panel.minGb <= 0 || g >= panel.minGb) && (panel.maxGb <= 0 || g <= panel.maxGb) && (panel.minDays <= 0 || d >= panel.minDays) && (panel.maxDays <= 0 || d <= panel.maxDays)
            setQuote({ price: inRange ? panel.gbPrice * g + panel.dayPrice * d : null, trafficMin: panel.minGb, trafficMax: panel.maxGb, timeMin: panel.minDays, timeMax: panel.maxDays })
          }} />
        <span class="label-medium c-text2">هر گیگ {formatPrice(panel.gbPrice)} تومان{panel.dayPrice > 0 ? ` · هر روز ${formatPrice(panel.dayPrice)} تومان` : ''}</span>
        <PillButton text="خرید سرویس سفارشی" icon="shopping_cart" enabled={quote?.price != null && !starting && shop.canSell}
          onClick={() => { if (quote?.price == null) return; setPending({ title: `سرویس سفارشی · ${fa(gb)} گیگ · ${fa(days)} روز`, price: quote.price, productCode: 'customvolume', custom: true, volumeGb: +gb || 0, timeDays: +days || 0 }); setMethod(null); setDiscounted(null); setDiscountNote(null) }} />
      </> : <>
        {shown.length === 0 ? <SkinEmpty title="پلنی با این فیلترها پیدا نشد" hint="دستهٔ دیگری انتخاب کن یا فیلتر مدت را بردار." icon="shopping_cart"
          actionText={category || duration ? 'برداشتن فیلترها' : null} onAction={category || duration ? () => { setCategory(null); setDuration(null) } : null} /> : null}
        {shown.length ? <div class="card-grid">
        {shown.map(p => (
          <ProductCard bestValue={p.code === bestValue} enabled={!starting && shop.canSell && !home.blocked}
            product={{ id: p.code, name: p.name, price: p.price, trafficGb: p.volumeGb > 0 ? p.volumeGb : null, days: p.timeDays > 0 ? p.timeDays : null, countryId: panelCode ?? '',
              description: [p.priceBefore > p.price ? `🎟 با کد ${home.appliedCode} — قیمت قبل: ${formatPrice(p.priceBefore)} تومان` : null, p.note || null].filter(Boolean).join('\n') }}
            onBuy={() => { setPending({ title: p.name, price: p.price, productCode: p.code }); setMethod(null); setDiscounted(null); setDiscountNote(null) }} />
        ))}
        </div> : null}
      </>}
      <Reviews shop={shop} signedIn={props.signedIn} onSignIn={props.onSignIn} />
      {pending ? (
        <Dialog title="۲. تأیید سفارش" onDismiss={() => { if (!starting) setPending(null) }} dismissible={!starting}
          actions={<>
            <TextButton onClick={() => setPending(null)}>بازگشت</TextButton>
            <button class="m-btn" disabled={starting || !method} onClick={() => {
              const m = method!
              void start(() => pending.custom
                ? api.marketOrderStart({ shopId: shop.id, productCode: 'customvolume', panelCode: panelCode ?? '', method: m, plan: 'custom', volumeGb: pending.volumeGb, timeDays: pending.timeDays, discountCode: discountCode.trim() })
                : api.marketOrderStart({ shopId: shop.id, productCode: pending.productCode, panelCode: panelCode ?? '', method: m, discountCode: discountCode.trim() }))
            }}>{starting ? 'در حال ثبت…' : 'تأیید و ادامه'}</button>
          </>}>
          <span class="bold c-text">{pending.title}</span>
          {discounted != null && discounted < pending.price
            ? <span class="bold c-primary">قیمت: {formatPrice(pending.price)} ← {formatPrice(discounted)} تومان</span>
            : <span class="c-text">قیمت: {formatPrice(pending.price)} تومان</span>}
          <div class="row gap-sm" style={{ alignItems: 'flex-end' }}>
            <div class="grow"><SkinField label="کد تخفیف (اختیاری)" value={discountCode} dir="ltr" onInput={v => { setDiscountCode(v.slice(0, 40)); setDiscounted(null); setDiscountNote(null) }} /></div>
            <TextButton enabled={!!discountCode.trim()} onClick={async () => {
              try { const [p, msg] = await api.marketDiscountCheck(shop.id, discountCode, pending.price); setDiscounted(p); setDiscountNote(msg) } catch (e) { setDiscounted(null); setDiscountNote(api.publicMessage(e) || 'کد تخفیف معتبر نیست.') }
            }}>اعمال</TextButton>
          </div>
          {discountNote ? <span class="label-small" style={{ color: discounted != null ? 'var(--primary)' : 'var(--error)' }}>{discountNote}</span> : null}
          <span class="label-medium c-muted">روش پرداخت</span>
          {home.wallet != null ? <ChoiceRow title="کیف پول این فروشگاه" subtitle={`موجودی: ${formatPrice(home.wallet)} تومان`} selected={method === 'wallet'} onSelect={() => setMethod('wallet')} /> : null}
          {catalog.methods.map(m => <ChoiceRow title={m.label} subtitle={m.note || null} selected={method === m.id} onSelect={() => setMethod(m.id)} />)}
          {catalog.methods.length === 0 && home.wallet == null ? <span class="c-error">این فروشگاه هنوز روش پرداختی متصل نکرده است.</span> : null}
          <span class="label-small c-text2">پرداخت به همین فروشگاه انجام می‌شود، نه به قاجار؛ رسید برای خودِ فروشنده می‌رود.</span>
          {actionError ? <span class="label-medium c-error">{actionError}</span> : null}
        </Dialog>
      ) : null}
    </div>
  )
}

function Reviews(props: { shop: M.MarketShop; signedIn: boolean; onSignIn: () => void }) {
  const [stars, setStars] = useState(0)
  const [note, setNote] = useState('')
  const [result, setResult] = useState<string | null>(null)
  return <>
    <Rail label="نظرها" />
    {props.shop.reviews.length === 0 ? <span class="label-medium c-muted">هنوز نظری ثبت نشده است.</span> : null}
    {props.shop.reviews.map(r => (
      <Slab spacing={4}><StarRow stars={r.stars} />{r.body ? <span class="body-small c-text2 wrap-any">{r.body}</span> : null}</Slab>
    ))}
    {!props.signedIn ? (
      <Slab accent="var(--primary)" spacing={4} onClick={props.onSignIn}>
        <span class="bold c-text">برای امتیاز دادن، حساب را متصل کن</span>
        <span class="label-small c-text2">نظر فقط از کسی پذیرفته می‌شود که از همین فروشگاه خرید کرده باشد.</span>
      </Slab>
    ) : (
      <Slab spacing={8}>
        <span class="bold c-text">امتیاز شما</span>
        <div class="row">{[1, 2, 3, 4, 5].map(i => (
          <button aria-label={`امتیاز ${i}`} style={{ padding: '2px' }} onClick={() => setStars(i)}><Icon name={i <= stars ? 'star' : 'star_border'} size={28} color={i <= stars ? 'var(--warning)' : 'var(--muted)'} /></button>
        ))}</div>
        <SkinField label="توضیح (اختیاری)" value={note} onInput={v => setNote(v.slice(0, 500))} />
        {result ? <span class="label-medium c-text2">{result}</span> : null}
        <GhostPill text="ثبت نظر" icon="star" onClick={async () => {
          if (stars <= 0) { setResult('اول ستاره را انتخاب کنید.'); return }
          try { setResult((await api.marketReview(props.shop.id, stars, note)) || 'نظر شما ثبت شد.') } catch (e) { setResult(api.publicMessage(e) || 'ثبت نظر انجام نشد') }
        }} />
      </Slab>
    )}
  </>
}

function toOwned(s: M.MarketService): M.OwnedService {
  return {
    username: s.username, productName: s.productName || s.username, status: s.status || (s.reachable ? 'active' : ''), location: s.panelName,
    invoiceId: s.invoiceId, dataLimitBytes: s.dataLimit > 0 ? s.dataLimit : null, usedBytes: s.used, expireTimestamp: s.expire > 0 ? s.expire : null,
    planGb: s.volumeGb > 0 ? s.volumeGb : null, planDays: s.timeDays > 0 ? s.timeDays : null, soldAt: s.boughtAt > 0 ? s.boughtAt : null
  }
}

function ServicesTab(props: { home: M.MarketHome; onOrdered: (o: M.MarketOrder) => void; initialRenew: string }) {
  const shopId = props.home.shop.id
  const [services, setServices] = useState<M.MarketService[] | null>(null)
  const [renewHandled, setRenewHandled] = useState(!props.initialRenew)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [renewing, setRenewing] = useState<M.MarketService | null>(null)
  const [delivery, setDelivery] = useState<{ status: M.MarketOrderStatus; name: string } | null>(null)
  const [sort, setSort] = useState<M.ServiceSort>('NEWEST')
  const [importing, setImporting] = useState<string | null>(null)
  useEffect(() => {
    api.marketServices(shopId).then(list => {
      setServices(list); setError(null)
      if (!renewHandled) { setRenewHandled(true); const t = list.find(s => s.username.toLowerCase() === props.initialRenew.toLowerCase() && !s.isTest); if (t) setRenewing(t) }
    }).catch(e => setError(api.publicMessage(e) || 'سرویس‌ها خوانده نشد'))
  }, [shopId, reload])
  if (renewing) return <RenewPanel home={props.home} service={renewing} onCancel={() => setRenewing(null)} onOrdered={props.onOrdered} />
  if (services == null && error == null) return <SkinLoading label="در حال گرفتن سرویس‌ها" />
  if (services == null) return <SkinError message={error!} retryText="تلاش دوباره" onRetry={() => setReload(reload + 1)} />
  if (services.length === 0) return <SkinEmpty title="از این فروشگاه هنوز سرویسی نخریده‌اید" hint="از تب «خرید» یک پلن یا تست بگیرید." icon="shopping_bag" />
  const asOwned = services.map(toOwned)
  const shown = M.sortedFor(asOwned, sort)
  return (
    <div class="col gap-md">
      <ServiceSortChips services={asOwned} selected={sort} onSelect={setSort} />
      {shown.length === 0 ? <span class="c-muted">سرویسی در این دسته نیست.</span> : null}
      {shown.map(o => {
        const s = services.find(x => x.username === o.username && x.invoiceId === o.invoiceId)!
        return <>
          <OwnedServiceCard service={o} onRenew={() => { if (!s.isTest) setRenewing(s) }} onImport={async () => {
            setImporting(s.username)
            try {
              const d = await api.marketServiceDelivery(shopId, s.invoiceId, s.username)
              if (!d.subscription && d.configs.length === 0) throw new api.ApiError('فروشنده هنوز کانفیگی برای این سرویس برنگردانده است؛ چند لحظه بعد دوباره بزنید.')
              setDelivery({ status: d, name: s.productName || 'سرویس فروشگاه' }); setError(null)
            } catch (e) { setError(api.publicMessage(e) || 'کانفیگ‌ها دریافت نشد') } finally { setImporting(null) }
          }} />
          {!s.reachable ? <span class="label-small c-warning">پنل فروشنده برای این سرویس جواب نداد؛ مصرف نمایش داده نمی‌شود.</span> : null}
        </>
      })}
      {importing ? <SkinLoading label={`در حال دریافت کانفیگ‌های ${importing}`} /> : null}
      {error ? <span class="label-medium c-error">{error}</span> : null}
      <GhostPill text="بازخوانی" icon="refresh" onClick={() => setReload(reload + 1)} />
      {delivery ? <AddToAppSheet productName={delivery.name} username={delivery.status.username} subscriptionUrl={delivery.status.subscription || null}
        configs={delivery.status.configs} synced onDismiss={() => setDelivery(null)} /> : null}
    </div>
  )
}

function RenewPanel(props: { home: M.MarketHome; service: M.MarketService; onCancel: () => void; onOrdered: (o: M.MarketOrder) => void }) {
  const { home, service } = props
  const products = (() => { const l = home.catalog.products.filter(p => !p.location || p.location === '/all' || p.location === service.panelName); return l.length ? l : home.catalog.products })()
  const [code, setCode] = useState<string | null>(products.find(p => p.name === service.productName)?.code ?? products[0]?.code ?? null)
  const [method, setMethod] = useState<string | null>(home.catalog.methods[0]?.id ?? null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  return (
    <div class="col gap-md">
      <Slab accent="var(--primary)" spacing={8}>
        <span class="bold c-text">تمدید {service.productName || service.username}</span>
        <span class="label-small c-muted ltr">{service.username}</span>
      </Slab>
      <span class="label-medium c-muted">پلن تمدید</span>
      {products.map(p => <ChoiceRow title={p.name} selected={code === p.code} onSelect={() => setCode(p.code)} value={`${formatPrice(p.price)} تومان`}
        subtitle={[p.volumeGb > 0 ? `${fa(p.volumeGb)} گیگ` : null, p.timeDays > 0 ? `${fa(p.timeDays)} روز` : null].filter(Boolean).join('  •  ') || null} />)}
      <span class="label-medium c-muted">روش پرداخت</span>
      {home.wallet != null ? <ChoiceRow title="کیف پول این فروشگاه" subtitle={`موجودی: ${formatPrice(home.wallet)} تومان`} selected={method === 'wallet'} onSelect={() => setMethod('wallet')} /> : null}
      {home.catalog.methods.map(m => <ChoiceRow title={m.label} subtitle={m.note || null} selected={method === m.id} onSelect={() => setMethod(m.id)} />)}
      {error ? <span class="label-medium c-error">{error}</span> : null}
      <PillButton text={busy ? 'در حال ثبت تمدید…' : 'تمدید همین سرویس'} icon="autorenew" enabled={!busy && !!code && !!method && home.shop.canSell}
        onClick={async () => {
          setBusy(true); setError(null)
          try { props.onOrdered(await api.marketOrderStart({ shopId: home.shop.id, productCode: code ?? '', panelCode: '', method: method ?? '', kind: 'renew', invoiceId: service.invoiceId, username: service.username })) }
          catch (e) { setError(api.publicMessage(e) || 'تمدید ثبت نشد') } finally { setBusy(false) }
        }} />
      <GhostPill text="انصراف" onClick={props.onCancel} />
    </div>
  )
}

function MessagesTab(props: { shopId: number; onRead: () => void }) {
  const [list, setList] = useState<M.MarketMessage[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  useEffect(() => {
    api.marketMessages(props.shopId).then(l => {
      setList(l); setError(null)
      if (l.some(m => !m.read)) api.marketMessagesRead(props.shopId).then(props.onRead).catch(() => undefined)
    }).catch(e => setError(api.publicMessage(e) || 'پیام‌ها خوانده نشد'))
  }, [props.shopId, reload])
  if (list == null && error == null) return <SkinLoading label="در حال گرفتن پیام‌ها" />
  if (list == null) return <SkinError message={error!} retryText="تلاش دوباره" onRetry={() => setReload(reload + 1)} />
  if (!list.length) return <SkinEmpty title="پیامی از این فروشگاه ندارید" hint="تأیید پرداخت، ساخت سرویس، پاسخ تیکت و اطلاعیه‌های فروشگاه اینجا می‌آید." />
  return <div class="col gap-sm">{list.map(m => (
    <Slab accent={m.read ? undefined : 'var(--primary)'} spacing={4}>
      <div class="row"><span class="bold c-text grow">{m.title}</span><span class="label-small c-muted">{jalali(m.createdAt)}</span></div>
      {m.body ? <span class="body-small c-text2 wrap-any" style={{ whiteSpace: 'pre-line' }}>{m.body}</span> : null}
    </Slab>
  ))}</div>
}

function WalletTab(props: { home: M.MarketHome; onOrdered: (o: M.MarketOrder) => void }) {
  const shopId = props.home.shop.id
  const [wallet, setWallet] = useState<M.MarketWallet | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [amount, setAmount] = useState('')
  const [method, setMethod] = useState<string | null>(props.home.catalog.methods[0]?.id ?? null)
  const [busy, setBusy] = useState(false)
  const [reload, setReload] = useState(0)
  useEffect(() => { api.marketWallet(shopId).then(w => { setWallet(w); setError(null) }).catch(e => setError(api.publicMessage(e) || 'کیف پول خوانده نشد')) }, [shopId, reload])
  return (
    <div class="col gap-md">
      <Slab padding={18} spacing={8}>
        <Rail label={`کیف پول ${props.home.shop.name}`} />
        {!wallet && error ? <SkinError message={error} retryText="تلاش دوباره" onRetry={() => { setError(null); setReload(reload + 1) }} /> : null}
        <span class="display-small bold c-highlight">{wallet ? formatPrice(wallet.balance) : '…'}</span>
        <span class="label-medium c-text2">تومان · فقط برای خرید و تمدید از همین فروشگاه</span>
        <div class="row gap-sm">{[50_000, 100_000, 200_000, 500_000].map(a => {
          const on = amount === String(a)
          return <button class="grow label-medium" onClick={() => setAmount(String(a))} style={{ borderRadius: '999px', padding: '8px 0', background: on ? 'var(--primary)' : 'var(--card)', color: on ? 'var(--on-primary)' : 'var(--text2)', fontWeight: on ? 700 : 400, whiteSpace: 'nowrap', overflow: 'hidden' }}>{formatPrice(a)}</button>
        })}</div>
        <SkinField label="مبلغ شارژ (تومان)" value={amount} inputMode="numeric" dir="ltr" onInput={v => setAmount(asciiDigits(v).slice(0, 10))} />
        {props.home.catalog.methods.map(m => <ChoiceRow title={m.label} subtitle={m.note || null} selected={method === m.id} onSelect={() => setMethod(m.id)} />)}
        {error && wallet ? <span class="label-medium c-error">{error}</span> : null}
        <PillButton text={busy ? 'در حال ثبت…' : 'شارژ کیف پول'} icon="account_balance_wallet" enabled={!busy && (+amount || 0) >= 1000 && !!method}
          onClick={async () => { setBusy(true); setError(null); try { props.onOrdered(await api.marketOrderStart({ shopId, productCode: '', panelCode: '', method: method ?? '', kind: 'wallet', amount: +amount || 0 })) } catch (e) { setError(api.publicMessage(e) || 'شارژ ثبت نشد') } finally { setBusy(false) } }} />
      </Slab>
      <GiftRedeem shopId={shopId} onRedeemed={() => setReload(reload + 1)} />
      {props.home.mine ? <OwnerCodes shopId={shopId} /> : null}
      {wallet?.history.length ? <>
        <Rail label="گردش کیف پول" />
        {wallet.history.map(e => (
          <Slab spacing={4}>
            <div class="row"><span class="c-text grow">{e.title || e.kind}</span><span class="bold" style={{ color: e.amount >= 0 ? 'var(--primary)' : 'var(--error)' }}>{e.amount >= 0 ? '+' : ''}{formatPrice(e.amount)}</span></div>
            <span class="label-small c-muted">{jalali(e.createdAt)} · مانده {formatPrice(e.balanceAfter)}</span>
          </Slab>
        ))}
      </> : null}
    </div>
  )
}

function GiftRedeem(props: { shopId: number; onRedeemed: () => void }) {
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<[boolean, string] | null>(null)
  return (
    <Slab padding={18} spacing={8} accent="var(--highlight)">
      <Rail label="🎁 کد هدیه" />
      <SkinField label="کد هدیه این فروشگاه" placeholder="مثلاً NOROOZ" value={code} dir="ltr" onInput={v => setCode(v.replace(/[^\w-]/g, '').slice(0, 40))} />
      <PillButton text={busy ? 'در حال بررسی…' : 'افزودن به کیف پول'} icon="card_giftcard" enabled={!busy && code.trim().length >= 3}
        onClick={async () => { setBusy(true); setResult(null); try { const r = await api.marketGiftRedeem(props.shopId, code.trim()); setResult(r); if (r[0]) { setCode(''); props.onRedeemed() } } catch (e) { setResult([false, api.publicMessage(e) || 'کد ثبت نشد']) } finally { setBusy(false) } }} />
      {result ? <span class="label-medium" style={{ color: result[0] ? 'var(--primary)' : 'var(--error)' }}>{result[1]}</span> : null}
    </Slab>
  )
}

function OwnerCodes(props: { shopId: number }) {
  const [codes, setCodes] = useState<{ discounts: api.MarketCode[]; gifts: api.MarketCode[] } | null>(null)
  const [message, setMessage] = useState<[boolean, string] | null>(null)
  const [gift, setGift] = useState(false)
  const [busy, setBusy] = useState(false)
  const [f, setF] = useState({ code: '', value: '', days: '', hours: '', maxUses: '', perUser: '', firstOnly: false, product: '', panel: '' })
  async function run(act: 'shop_codes' | 'code_add' | 'code_toggle' | 'code_delete', fields: Record<string, string> = {}) {
    setBusy(true)
    try {
      const [list, msg] = await api.marketCodes(props.shopId, act, fields)
      setCodes(list); setMessage(msg ? [true, msg] : null)
      if (act === 'code_add') setF({ code: '', value: '', days: '', hours: '', maxUses: '', perUser: '', firstOnly: false, product: '', panel: '' })
    } catch (e) { setMessage([false, api.publicMessage(e) || 'انجام نشد']) } finally { setBusy(false) }
  }
  useEffect(() => { void run('shop_codes') }, [props.shopId])
  const d = (v: string) => asciiDigits(v).slice(0, 9)
  const list = (gift ? codes?.gifts : codes?.discounts) ?? []
  const kind = gift ? 'gift' : 'discount'
  return (
    <Slab padding={18} spacing={8} accent="var(--primary)">
      <Rail label="🛠 مدیریت کدهای فروشگاه شما" />
      <SlidingSegments labels={['🎟 کد تخفیف', '🎁 کد هدیه']} selected={gift ? 1 : 0} onSelect={i => setGift(i === 1)} />
      {!codes && busy ? <SkinLoading label="در حال خواندن کدها" /> : null}
      {codes && !list.length ? <span class="c-muted">هنوز کدی در این بخش نیست.</span> : null}
      {list.map(it => {
        const expired = it.expiresAt > 0 && it.expiresAt < Date.now() / 1000
        const facts = [`استفاده ${it.used}${it.maxUses > 0 ? '/' + it.maxUses : ''}`, it.expiresAt > 0 ? `تا ${jalali(it.expiresAt)}` : 'بدون انقضا',
          it.perUser > 0 ? `هر نفر ${it.perUser} بار` : null, it.firstOnly ? 'فقط خرید اول' : null, it.product ? `پلن ${it.product}` : null,
          it.panel ? `سرور ${it.panel}` : null, it.source === 'seller' ? '🤖 از ربات خودتان' : 'قاجار'].filter(Boolean).join(' · ')
        return (
          <div class="col gap-xs" style={{ borderRadius: 'var(--r-md)', background: 'var(--card)', padding: '10px 12px' }}>
            <div class="row"><span class="bold c-text grow ltr">{(it.active && !expired ? '🟢 ' : '🔴 ') + it.code}</span><span class="bold c-highlight">{gift ? `${formatPrice(it.value)} تومان` : `${fa(it.value)}٪`}</span></div>
            <span class="label-small c-text2">{fa(facts)}</span>
            {it.source !== 'seller' && it.id > 0 ? <div class="row gap-sm">
              <GhostPill text={it.active ? 'غیرفعال' : 'فعال'} minHeight={36} enabled={!busy} onClick={() => run('code_toggle', { kind, id: String(it.id) })} />
              <GhostPill text="حذف" minHeight={36} enabled={!busy} accent="var(--error)" onClick={() => run('code_delete', { kind, id: String(it.id) })} />
            </div> : null}
          </div>
        )
      })}
      <Rail label={gift ? 'کد هدیهٔ تازه' : 'کد تخفیف تازه'} />
      <SkinField label="کد" value={f.code} dir="ltr" onInput={v => setF({ ...f, code: v.replace(/[^\w-]/g, '').slice(0, 40).toUpperCase() })} />
      <SkinField label={gift ? 'مبلغ هدیه (تومان)' : 'درصد تخفیف'} value={f.value} inputMode="numeric" dir="ltr" onInput={v => setF({ ...f, value: d(v) })} />
      <div class="row gap-sm">
        <div class="grow"><SkinField label="روز" value={f.days} inputMode="numeric" dir="ltr" onInput={v => setF({ ...f, days: d(v) })} /></div>
        <div class="grow"><SkinField label="ساعت" value={f.hours} inputMode="numeric" dir="ltr" onInput={v => setF({ ...f, hours: d(v) })} /></div>
        <div class="grow"><SkinField label="سقف کل" value={f.maxUses} inputMode="numeric" dir="ltr" onInput={v => setF({ ...f, maxUses: d(v) })} /></div>
      </div>
      {!gift ? <>
        <SkinField label="سقف برای هر نفر (۰ = بی‌نهایت)" value={f.perUser} inputMode="numeric" dir="ltr" onInput={v => setF({ ...f, perUser: d(v) })} />
        <div class="row gap-sm">
          <div class="grow"><SkinField label="کد پلن (اختیاری)" value={f.product} dir="ltr" onInput={v => setF({ ...f, product: v.slice(0, 100) })} /></div>
          <div class="grow"><SkinField label="کد سرور (اختیاری)" value={f.panel} dir="ltr" onInput={v => setF({ ...f, panel: v.slice(0, 100) })} /></div>
        </div>
        <ChoiceRow title="فقط برای اولین خرید" selected={f.firstOnly} onSelect={() => setF({ ...f, firstOnly: !f.firstOnly })} />
      </> : null}
      <span class="label-small c-muted">روز و ساعت خالی یعنی بدون انقضا؛ سقف خالی یعنی بی‌نهایت.{gift ? ' هر خریدار یک بار.' : ''}</span>
      {message ? <span class="label-medium" style={{ color: message[0] ? 'var(--primary)' : 'var(--error)' }}>{message[1]}</span> : null}
      <PillButton text={busy ? 'در حال ثبت…' : 'ثبت کد'} icon="card_giftcard" enabled={!busy && f.code.length >= 3 && !!f.value}
        onClick={() => run('code_add', { kind, code: f.code, [gift ? 'amount' : 'percent']: f.value, days: f.days || '0', hours: f.hours || '0', max_uses: f.maxUses || '0', per_user: f.perUser || '0', first_only: f.firstOnly ? '1' : '0', product: f.product.trim(), panel: f.panel.trim() })} />
    </Slab>
  )
}

function SupportTab(props: { shopId: number }) {
  const [tickets, setTickets] = useState<M.MarketTicket[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [open, setOpen] = useState<M.MarketTicketThread | null>(null)
  const [subject, setSubject] = useState('')
  const [body, setBody] = useState('')
  const [reply, setReply] = useState('')
  const [busy, setBusy] = useState(false)
  useEffect(() => { api.marketTickets(props.shopId).then(t => { setTickets(t); setError(null) }).catch(e => setError(api.publicMessage(e) || 'تیکت‌ها خوانده نشد')) }, [props.shopId, reload])
  if (open) return (
    <div class="col gap-md">
      <GhostPill text="همهٔ تیکت‌ها" icon="chevron_left" onClick={() => { setOpen(null); setReload(reload + 1) }} />
      <Rail label={open.subject} />
      {open.messages.map(m => (
        <Slab accent={m.fromSeller ? 'var(--primary)' : 'var(--border)'} spacing={4}>
          <div class="row"><span class="bold grow" style={{ color: m.fromSeller ? 'var(--primary)' : 'var(--text)' }}>{m.fromSeller ? 'فروشنده' : 'شما'}</span><span class="label-small c-muted">{jalali(m.createdAt)}</span></div>
          <span class="body-small c-text2 wrap-any" style={{ whiteSpace: 'pre-line' }}>{m.body}</span>
        </Slab>
      ))}
      {open.status === 'closed' ? <span class="label-medium c-muted">این تیکت بسته شده است.</span> : <>
        <SkinField label="پاسخ شما" value={reply} multiline minLines={3} onInput={v => setReply(v.slice(0, 2000))} />
        {error ? <span class="label-medium c-error">{error}</span> : null}
        <PillButton text={busy ? 'در حال ارسال…' : 'ارسال'} icon="send" enabled={!busy && !!reply.trim()} onClick={async () => {
          setBusy(true); try { setOpen(await api.marketTicketReply(open.id, reply)); setReply(''); setError(null) } catch (e) { setError(api.publicMessage(e) || 'ارسال نشد') } finally { setBusy(false) }
        }} />
        <GhostPill text="بستن تیکت" icon="lock" onClick={() => api.marketTicketClose(open.id).then(setOpen).catch(() => undefined)} />
      </>}
    </div>
  )
  return (
    <div class="col gap-md">
      <Slab spacing={8}>
        <span class="bold c-text">تیکت تازه به پشتیبانی این فروشگاه</span>
        <SkinField label="موضوع" value={subject} onInput={v => setSubject(v.slice(0, 120))} />
        <SkinField label="متن پیام" value={body} multiline minLines={3} onInput={v => setBody(v.slice(0, 2000))} />
        {error ? <span class="label-medium c-error">{error}</span> : null}
        <PillButton text={busy ? 'در حال ارسال…' : 'ارسال تیکت'} icon="send" enabled={!busy && !!subject.trim() && !!body.trim()} onClick={async () => {
          setBusy(true)
          try { const id = await api.marketTicketCreate(props.shopId, subject, body); setSubject(''); setBody(''); setError(null); setReload(reload + 1); api.marketTicketThread(id).then(setOpen).catch(() => undefined) }
          catch (e) { setError(api.publicMessage(e) || 'تیکت ثبت نشد') } finally { setBusy(false) }
        }} />
        <span class="label-small c-muted">پیام مستقیم برای فروشنده فرستاده می‌شود و پاسخش در همین‌جا و در «پیام‌ها» می‌آید.</span>
      </Slab>
      {tickets == null && error == null ? <SkinLoading label="در حال گرفتن تیکت‌ها" /> : tickets?.length ? <>
        <Rail label="تیکت‌های شما" />
        {tickets.map(t => (
          <Slab spacing={4} onClick={() => api.marketTicketThread(t.id).then(setOpen).catch(() => undefined)}>
            <div class="row"><span class="bold c-text grow ellipsis">{t.subject}</span>
              <span class="label-small" style={{ color: t.status === 'answered' ? 'var(--primary)' : t.status === 'closed' ? 'var(--muted)' : 'var(--warning)' }}>{t.status === 'answered' ? 'پاسخ داده شد' : t.status === 'closed' ? 'بسته' : 'منتظر پاسخ'}</span></div>
            <span class="label-small c-muted">{jalali(t.updatedAt)}</span>
          </Slab>
        ))}
      </> : null}
    </div>
  )
}

function TransactionsTab(props: { shopId: number }) {
  const [rows, setRows] = useState<M.MarketTransaction[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  useEffect(() => { api.marketTransactions(props.shopId).then(r => { setRows(r); setError(null) }).catch(e => setError(api.publicMessage(e) || 'تراکنش‌ها خوانده نشد')) }, [props.shopId, reload])
  if (rows == null && error == null) return <SkinLoading label="در حال گرفتن تراکنش‌ها" />
  if (rows == null) return <SkinError message={error!} retryText="تلاش دوباره" onRetry={() => setReload(reload + 1)} />
  if (!rows.length) return <SkinEmpty title="هنوز تراکنشی در این فروشگاه ندارید" />
  const label = (s: string) => s === STATUS.PAID ? 'پرداخت‌شده' : s === STATUS.REVIEW ? 'منتظر تأیید فروشنده' : s === STATUS.AWAITING ? 'منتظر پرداخت' : s === STATUS.REJECTED ? 'رد شده' : s === STATUS.FAILED ? 'ناموفق' : s === 'expired' ? 'منقضی' : s
  const color = (s: string) => s === STATUS.PAID ? 'var(--primary)' : s === STATUS.REJECTED || s === STATUS.FAILED ? 'var(--error)' : 'var(--warning)'
  return <div class="col gap-sm">{rows.map(t => (
    <Slab spacing={4}>
      <div class="row"><span class="bold c-text grow ellipsis">{t.productName || (t.kind === 'wallet' ? 'شارژ کیف پول' : t.kind === 'renew' ? 'تمدید' : t.kind === 'test' ? 'سرویس تست' : 'خرید سرویس')}</span><span class="bold c-text">{formatPrice(t.amount)} تومان</span></div>
      <div class="row"><span class="label-small grow" style={{ color: color(t.status) }}>{label(t.status)}</span><span class="label-small c-muted">{fa(`#${t.id}`)} · {jalali(t.createdAt)}</span></div>
      {t.rejectReason ? <span class="label-small c-error">{t.rejectReason}</span> : null}
    </Slab>
  ))}</div>
}

/** MarketOrderPage: pay at the seller, send the receipt, watch the order until it is delivered. */
function MarketOrderPage(props: { order: M.MarketOrder; onBack: () => void }) {
  const o = props.order
  const [status, setStatus] = useState<M.MarketOrderStatus | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [sending, setSending] = useState(false)
  const [note, setNote] = useState('')
  const [pollKey, setPollKey] = useState(0)
  const [showAdd, setShowAdd] = useState(false)
  const tick = useStore(paymentReturn)
  useEffect(() => { if (tick > 0) setPollKey(k => k + 1) }, [tick])
  useEffect(() => {
    let alive = true
    let timer: ReturnType<typeof setTimeout>
    const loop = async () => {
      try { const s = await api.marketOrderStatus(o.id); if (!alive) return; setStatus(s); if ([STATUS.PAID, STATUS.REJECTED, STATUS.FAILED].includes(s.status)) return } catch { /* retry */ }
      if (alive) timer = setTimeout(loop, 12_000)
    }
    void loop()
    const onVisible = () => { if (document.visibilityState === 'visible') { clearTimeout(timer); void loop() } }
    document.addEventListener('visibilitychange', onVisible)
    return () => { alive = false; clearTimeout(timer); document.removeEventListener('visibilitychange', onVisible) }
  }, [o.id, pollKey])
  const live = status
  return (
    <div class="col gap-md">
      <GhostPill text="بازگشت به فروشگاه" icon="chevron_left" onClick={props.onBack} />
      <Rail label="پرداخت سفارش" />
      <Slab spacing={4}>
        <InfoLine label="شمارهٔ سفارش" value={fa(o.id)} />
        {o.productName ? <InfoLine label="پلن" value={o.productName} /> : null}
        <InfoLine label="مبلغ" value={`${formatPrice(o.amount)} تومان`} />
        <InfoLine label="روش" value={o.methodLabel || o.method} />
      </Slab>
      {live?.status === STATUS.PAID ? (
        <Slab accent="var(--primary)" spacing={8}>
          <div class="row gap-sm"><Icon name="check" size={20} color="var(--primary)" /><span class="bold c-text">سرویس شما آماده است</span></div>
          {live.username ? <InfoLine label="نام سرویس" value={live.username} /> : null}
          {!live.subscription && !live.configs.length ? <>
            <span class="label-medium c-warning">فروشنده پرداخت را تأیید کرده اما کانفیگی برنگشت. چند لحظه بعد دوباره بررسی کنید.</span>
            <GhostPill text="دریافت دوبارهٔ کانفیگ" icon="refresh" onClick={() => setPollKey(pollKey + 1)} />
          </> : <>
            <span class="label-medium c-text2">لینک و کانفیگ‌ها آماده‌اند. با «افزودن به اپ» مستقیم به برنامهٔ VPN خودت اضافه می‌شوند.</span>
            <PillButton text="افزودن به اپ" icon="add_to_home_screen" onClick={() => setShowAdd(true)} />
            <GhostPill text="کپی" icon="content_copy" onClick={() => copyText(live.subscription || live.configs.join('\n'), 'لینک')} />
          </>}
        </Slab>
      ) : live?.status === STATUS.REJECTED ? (
        <SkinError message={'فروشنده این پرداخت را رد کرد.' + (live.rejectReason ? '\n' + live.rejectReason : '')} retryText="بازگشت" onRetry={props.onBack} />
      ) : live?.status === STATUS.FAILED ? (
        <SkinError message={'پرداخت شما ثبت شد اما ساخت سرویس انجام نشد.' + (live.rejectReason ? '\n' + live.rejectReason : '') + `\nشمارهٔ سفارش ${fa(o.id)} را به پشتیبانی فروشگاه بدهید.`} />
      ) : live?.status === STATUS.REVIEW ? (
        <Slab accent="var(--warning)" spacing={8}>
          <span class="bold c-text">رسید برای فروشنده ارسال شد</span>
          <span class="label-medium c-text2">تأیید با فروشنده است. به‌محض تأیید، سرویس روی پنل خودش ساخته می‌شود و کانفیگ همین‌جا می‌آید. این صفحه خودش بررسی می‌کند.</span>
          <GhostPill text="بررسی دوباره" icon="refresh" onClick={() => setPollKey(pollKey + 1)} />
        </Slab>
      ) : live == null || live.status === STATUS.AWAITING ? (
        o.needs === 'secret' ? (
          <Slab accent="var(--warning)" spacing={8}>
            <span class="bold c-text">پرداخت در انتظار تأیید</span>
            <InfoLine label="کد پیگیری" value={fa(o.id)} />
            <InfoLine label="مبلغ" value={`${formatPrice(o.amount)} تومان`} />
            <span class="label-medium c-text2">پرداخت در صفحهٔ امن انجام می‌شود. بعد از پرداخت برگرد؛ نتیجه روی سرور فروشگاه بررسی و سرویس همین‌جا تحویل می‌شود.</span>
            {!o.gatewayUrl ? <span class="label-medium c-error">درگاه این فروشگاه در دسترس نیست؛ از روش پرداخت دیگری استفاده کن.</span>
              : <PillButton text="ادامهٔ همین پرداخت" icon="lock" onClick={() => {
                if (!/^https:\/\//i.test(o.gatewayUrl)) { setMessage('صفحهٔ پرداخت امن باز نشد.'); return }
                if (!isTrustedPaymentUrl(o.gatewayUrl)) { /* seller gateways vary: still https-only */ }
                openExternal(o.gatewayUrl)
              }} />}
            <GhostPill text="پرداخت کردم؛ بررسی و دریافت سرویس" icon="refresh" onClick={() => setPollKey(pollKey + 1)} />
            <GhostPill text="انصراف" icon="close" onClick={props.onBack} />
          </Slab>
        ) : o.needs === 'contact' ? (
          <Slab spacing={8}>
            <span class="bold c-text">پرداخت با هماهنگی پشتیبانی</span>
            <span class="label-medium c-text2">این فروشگاه کارت‌به‌کارت را از طریق حساب پشتیبانی خودش انجام می‌دهد.</span>
            {o.contact ? <PillButton text={'@' + o.contact.replace(/^@/, '')} icon="support_agent" onClick={() => openTelegram(o.contact)} /> : null}
          </Slab>
        ) : (
          <Slab spacing={8}>
            <span class="bold c-text">کارت به کارت</span>
            {!o.cardNumber ? <span class="label-medium c-error">شمارهٔ کارت این فروشگاه ثبت نشده است.</span> : <>
              <div class="row">
                <Icon name="credit_card" size={18} color="var(--primary)" />
                <span style={{ width: '8px' }} />
                <div class="col grow">
                  <span class="bold c-text" style={{ direction: 'ltr', textAlign: 'start' }}>{(o.cardNumber.replace(/\D/g, '').match(/.{1,4}/g) ?? []).join(' ')}</span>
                  {o.cardHolder ? <span class="label-small c-text2">{o.cardHolder}</span> : null}
                </div>
                <button class="icon-btn" aria-label="کپی شمارهٔ کارت" onClick={() => copyText(o.cardNumber.replace(/\D/g, ''), 'شماره کارت')}><Icon name="content_copy" size={20} color="var(--muted)" /></button>
              </div>
              <span class="label-medium c-text2">مبلغ را به همین کارت واریز کنید، بعد تصویر رسید را بفرستید. رسید مستقیم برای فروشنده می‌رود.</span>
              <SkinField label="توضیح برای فروشنده (اختیاری)" value={note} onInput={setNote} />
              <label class="pill" style={{ cursor: sending ? 'default' : 'pointer', opacity: sending ? 0.6 : 1 }}>
                <input type="file" accept="image/*" hidden disabled={sending} onChange={async e => {
                  const file = (e.target as HTMLInputElement).files?.[0]
                  if (!file) return
                  setSending(true); setMessage(null)
                  try { setMessage(await api.marketSubmitReceipt(o.id, file, note)); setPollKey(k => k + 1) } catch (err) { setMessage(api.publicMessage(err) || 'ارسال رسید انجام نشد') } finally { setSending(false) }
                }} />
                <Icon name="upload" size={19} />
                <span class="txt">{sending ? 'در حال ارسال رسید…' : 'انتخاب و ارسال تصویر رسید'}</span>
              </label>
            </>}
          </Slab>
        )
      ) : (
        <SkinError message={`وضعیت این سفارش برای این نسخهٔ برنامه ناشناخته است: ${live.status || '-'}\nبرنامه را به‌روز کنید یا از پشتیبانی فروشگاه بپرسید.`} retryText="بررسی دوباره" onRetry={() => setPollKey(pollKey + 1)} />
      )}
      {message ? <span class="label-medium c-text2">{message}</span> : null}
      {showAdd && live ? <AddToAppSheet productName={o.productName || 'سرویس فروشگاه'} username={live.username} subscriptionUrl={live.subscription || null} configs={live.configs} synced onDismiss={() => setShowAdd(false)} /> : null}
    </div>
  )
}

function MarketRegisterPage(props: { onBack: () => void }) {
  const [terms, setTerms] = useState<api.MarketTerms | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(true)
  useEffect(() => { api.marketTerms().then(setTerms).catch(e => setError(api.publicMessage(e) || 'قوانین خوانده نشد')).finally(() => setBusy(false)) }, [])
  const statusLabel = (s: string) => ({ active: 'فعال', pending: 'منتظر پرداخت هزینهٔ ثبت', review: 'منتظر بررسی', suspended: 'معلق (بدهی)', delisted: 'خارج از فهرست', rejected: 'رد شده' } as Record<string, string>)[s] ?? s
  return (
    <div class="col gap-md">
      <GhostPill text="بازگشت به فهرست" icon="chevron_left" onClick={props.onBack} />
      <Rail label="ثبت فروشگاه" />
      {busy && !terms ? <SkinLoading label="در حال گرفتن قوانین" /> : !terms ? <SkinError message={error ?? 'قوانین خوانده نشد'} retryText="بازگشت" onRetry={props.onBack} /> : <>
        <Slab spacing={4}>
          <InfoLine label="هزینهٔ ثبت" value={`${formatPrice(terms.fee)} تومان`} />
          <InfoLine label="کمیسیون فروش" value={`${fa(Math.round(terms.commissionPercent))}٪`} />
          <InfoLine label="طول دورهٔ مالی" value={`${fa(terms.cycleDays)} روز`} />
          <InfoLine label="جریمهٔ روزانهٔ تأخیر" value={`${formatPrice(terms.penaltyPerDay)} تومان`} />
          <InfoLine label="حذف از فهرست" value={`${fa(terms.graceDays)} روز پس از سررسید`} />
        </Slab>
        {terms.terms ? <Slab><span class="body-small c-text2 wrap-any" style={{ whiteSpace: 'pre-line' }}>{terms.terms}</span></Slab> : null}
        <Slab spacing={8}>
          <span class="bold c-text">چه چیزی لازم است</span>
          {['ربات فروشگاه خودتان، روی دامنهٔ خودتان', 'در ربات خودتان بزنید /token2 و توکن API را کپی کنید', 'شمارهٔ کارت یا درگاه خودتان، برای دریافت پول'].map((l, i) => (
            <div class="row"><span class="label-medium bold c-primary" style={{ width: '18px' }}>{fa(i + 1)}</span><span class="label-medium c-text2 grow">{l}</span></div>
          ))}
          <span class="label-small c-muted">سورس ربات شما تغییر نمی‌کند، فایلی آپلود نمی‌شود و هیچ مهاجرت دیتابیسی لازم نیست.</span>
        </Slab>
        {terms.myShops.length === 0 ? (
          <Slab accent="var(--primary)" spacing={8}>
            <span class="bold c-text">ثبت‌نام در ربات انجام می‌شود</span>
            <span class="label-medium c-text2">برای ثبت فروشگاه، در ربات قاجار «🏪 ثبت فروشگاه» را بزنید. آدرس و توکن آنجا گرفته می‌شود، چون تایپ کردنشان روی گوشی سخت است و توکن نباید در جایی بماند.</span>
            <PillButton text="رفتن به ربات" icon="open_in_new" onClick={() => openExternal(TELEGRAM_BOT_URL)} />
          </Slab>
        ) : <>
          <Rail label="فروشگاه‌های من" />
          {terms.myShops.map(s => (
            <Slab spacing={4}>
              <span class="bold c-text">{s.name}</span>
              <InfoLine label="وضعیت" value={statusLabel(s.status)} />
              {s.lastError ? <span class="label-small c-error">{s.lastError}</span> : null}
            </Slab>
          ))}
        </>}
      </>}
    </div>
  )
}

export { ltr, useMemo }
