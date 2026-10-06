import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import * as api from '../api/client'
import * as M from '../api/models'
import { useStore } from '../lib/store'
import { tokenStore, pendingLink, clearPendingLink, clearAccount, LinkSession, isLinked } from '../api/account'
import { LinkState, linkMessage, remainingSeconds, retryDelayMillis, verificationGate, botLoginUrls, botVerificationUrls, validPendingLink } from '../api/linkflow'
import { Icon } from '../components/Icon'
import { Dialog } from '../components/Overlay'
import { Slab, SlabRow, PillButton, GhostPill, TextButton, Rail, ScreenHeader, SkinField, SkinEmpty, SkinLoading, SlidingSegments, TabRail, LinearProgress } from '../components/Skin'
import {
  SectionTitle, StatusCard, CrestLogo, ServiceTypeGrid, ChipFlowRow, ProductCard, PlanComparisonDialog, CustomServiceCard,
  OwnedServiceCard, ServiceSortChips, NoticeCard, PaymentSummary, PaymentMethodCard, CardToCardCard, PendingPaymentCard,
  PurchaseExtras, panelIcon
} from '../components/ShopParts'
import { LinkAccountCard, RenewServiceDialog, Tickets, TransactionHistory, Announcements } from './ShopPanels'
import { AddToAppSheet } from '../components/AddToApp'
import { MarketScreen } from './Market'
import { asciiDigits, fa, formatPrice } from '../lib/format'
import { cardPayment } from '../api/rules'
import * as co from '../state/checkout'
import { feedStore, shopStatus, refreshNotices, unreadStore } from '../state/notices'
import { ownedStore, ownedLoaded, refreshOwned, resetOwned } from '../state/shop'
import { renewRequest, shopOpenRequest, paymentReturn, openNoticesRequest, copyText, toast } from '../state/ui'
import { lookStore } from '../theme/look'
import { rebindPush } from '../lib/pwa'

/** GhajarShopScreen: every shop (Ghajar first), and inside Ghajar its six sections. */
export function ShopScreen(props: { active: boolean }) {
  const token = useStore(tokenStore)
  const linked = token !== ''
  const look = useStore(lookStore)
  const ck = useStore(co.checkout)
  const owned = useStore(ownedStore)
  const loadedOwned = useStore(ownedLoaded)
  const feed = useStore(feedStore)
  const shop = useStore(shopStatus)
  const unread = useStore(unreadStore)

  const [linkSession, setLinkSession] = useState<LinkSession | null>(pendingLink())
  const [linkGate, setLinkGate] = useState<LinkState | null>(null)
  const [linkState, setLinkState] = useState<LinkState>('PENDING')
  const [linkChecking, setLinkChecking] = useState(false)
  const [linkCheckKey, setLinkCheckKey] = useState(0)
  const [linkRemaining, setLinkRemaining] = useState(0)
  const [busy, setBusy] = useState(false)
  const [refreshKey, setRefreshKey] = useState(0)
  const [section, setSection] = useState(0)
  const [ownedSort, setOwnedSort] = useState<M.ServiceSort>('NEWEST')
  const [inGhajar, setInGhajar] = useState(false)
  const [linkPrompt, setLinkPrompt] = useState(false)
  const [signOutConfirm, setSignOutConfirm] = useState(false)
  const [confirmation, setConfirmation] = useState<M.PurchaseRequest | null>(null)
  const [confirmationTitle, setConfirmationTitle] = useState('')
  const [confirmationPrice, setConfirmationPrice] = useState<number | null>(null)

  const [panels, setPanels] = useState<M.Panel[]>([])
  const [selectedPanel, setSelectedPanel] = useState<M.Panel | null>(null)
  const [categories, setCategories] = useState<M.Category[]>([])
  const [selectedCategory, setSelectedCategory] = useState<M.Category | null>(null)
  const [timeRanges, setTimeRanges] = useState<M.TimeRange[]>([])
  const [selectedTime, setSelectedTime] = useState<M.TimeRange | null>(null)
  const [products, setProducts] = useState<M.Product[]>([])
  const [unfiltered, setUnfiltered] = useState<M.Product[]>([])
  const [ghajarGbPrice, setGbPrice] = useState<number | null>(null)
  const [ghajarGbMax, setGbMax] = useState<number | null>(null)
  const [loadedPanelId, setLoadedPanelId] = useState<string | null>(null)
  const [customMode, setCustomMode] = useState(false)
  const [comparePlans, setComparePlans] = useState(false)
  const [customTraffic, setCustomTraffic] = useState('')
  const [customDays, setCustomDays] = useState('')
  const [customQuote, setCustomQuote] = useState<M.CustomQuote | null>(null)
  const [customUsername, setCustomUsername] = useState('')
  const [customNote, setCustomNote] = useState('')
  const [discountCode, setDiscountCode] = useState('')
  const [trialOptions, setTrialOptions] = useState<M.TrialOptions | null>(null)
  const [renewUsername, setRenewUsername] = useState<string | null>(null)
  const [announcements, setAnnouncements] = useState<M.MarketAnnouncement[]>([])
  const [walletAmount, setWalletAmount] = useState('')
  const [ghajarLogo, setGhajarLogo] = useState(0)
  const topRef = useRef<HTMLDivElement>(null)

  const error = ck.error, message = ck.message
  const setError = (v: string | null) => co.setCheckoutError(v)
  const setMessage = (v: string | null) => co.setCheckoutMessage(v)
  const notices = feed?.notices ?? []

  // Restore an unfinished invoice of this account, once.
  useEffect(() => { void co.restoreCheckout() }, [token])

  // Checkout.openUrl: a gateway issued by payment_init opens immediately.
  useEffect(() => {
    if (!props.active || !ck.openUrl) return
    const url = ck.openUrl
    co.clearOpenUrl()
    co.openCheckout(url)
  }, [ck.openUrl, props.active])

  // Coming back to the app (from the gateway, or after a while): check the
  // payment with the server - often at first, then backing off.
  useEffect(() => {
    if (!props.active || !ck.payment) return
    let step = 0
    let timer: ReturnType<typeof setTimeout>
    const ladder = [2000, 2000, 3000, 4000, 6000, 9000]
    const tick = () => {
      if (document.visibilityState === 'visible') void co.checkPayment()
      timer = setTimeout(tick, ladder[step] ?? 15000)
      step++
    }
    const onVisible = () => { if (document.visibilityState === 'visible') { step = 0; clearTimeout(timer); tick() } }
    tick()
    document.addEventListener('visibilitychange', onVisible)
    window.addEventListener('focus', onVisible)
    return () => { clearTimeout(timer); document.removeEventListener('visibilitychange', onVisible); window.removeEventListener('focus', onVisible) }
  }, [props.active, ck.payment?.orderId])

  useEffect(() => {
    if (!linked || !props.active) return
    void co.refreshPending()
    const t = setInterval(() => { if (document.visibilityState === 'visible') void co.refreshPending() }, 30_000)
    return () => clearInterval(t)
  }, [linked, props.active])

  useEffect(() => { if (ck.revision > 0) { void refreshOwned(); void refreshNotices() } }, [ck.revision])

  // The store's catalogue and the account's services and notices, together.
  useEffect(() => {
    if (!linked) return
    let alive = true
    setBusy(true); setError(null)
    Promise.all([api.countries(), refreshOwned(), refreshNotices()]).then(([p]) => {
      if (!alive) return
      setPanels(p)
      setSelectedPanel(cur => (cur && p.some(x => x.id === cur.id) ? cur : p[0] ?? null))
    }).catch(e => alive && setError(api.publicMessage(e) || 'خطا در دریافت فروشگاه')).finally(() => alive && setBusy(false))
    return () => { alive = false }
  }, [linked, refreshKey])

  useEffect(() => {
    const custom = panels.filter(p => p.custom)
    if (!custom.length) return
    Promise.all(custom.map(p => api.customQuote(p.id, 1, 0).then(q => (q.price && q.price > 0 ? q.price : null)).catch(() => null)))
      .then(prices => {
        const ok = prices.filter((x): x is number => x != null)
        setGbPrice(ok.length ? Math.min(...ok) : null)
        setGbMax(ok.length ? Math.max(...ok) : null)
      })
  }, [panels])

  useEffect(() => {
    const panel = selectedPanel
    if (!panel) return
    let alive = true
    setLoadedPanelId(null); setSelectedCategory(null); setSelectedTime(null); setCustomMode(false); setCustomQuote(null); setProducts([])
    setBusy(true)
    const load = () => Promise.all([api.categories(panel.id), api.timeRanges(panel.id), api.products(panel.id, null, null)])
    load().catch(() => new Promise(r => setTimeout(r, 900)).then(load)).then(([c, t, p]) => {
      if (!alive) return
      setCategories(c); setTimeRanges(t); setProducts(p); setUnfiltered(p); setLoadedPanelId(panel.id)
    }).catch(e => alive && setError(api.publicMessage(e))).finally(() => alive && setBusy(false))
    return () => { alive = false }
  }, [selectedPanel?.id])

  useEffect(() => {
    const panel = selectedPanel
    if (!panel || loadedPanelId !== panel.id) return
    if (!customMode && !selectedCategory && !selectedTime) { setProducts(unfiltered); return }
    let alive = true
    setBusy(true)
    ;(customMode ? Promise.resolve([] as M.Product[]) : api.products(panel.id, selectedCategory?.id ?? null, selectedTime?.days ?? null))
      .then(p => alive && setProducts(p)).catch(e => alive && setError(api.publicMessage(e))).finally(() => alive && setBusy(false))
    return () => { alive = false }
  }, [loadedPanelId, selectedCategory?.id, selectedTime?.days, customMode])

  useEffect(() => {
    if (inGhajar) api.marketAnnouncements(0).then(setAnnouncements).catch(() => setAnnouncements([]))
  }, [inGhajar])

  // Requests from notifications and the banner.
  const openReq = useStore(shopOpenRequest)
  useEffect(() => {
    if (!openReq || !props.active) return
    if (openReq.shopId === 0) {
      setInGhajar(true); setSection(0)
      if (openReq.code) { setDiscountCode(openReq.code); setMessage(`🎟 کد تخفیف ${openReq.code} در سفارش قرار گرفت؛ پلن را انتخاب کن.`) }
      shopOpenRequest.set(null)
    } else setInGhajar(false)
  }, [openReq, props.active])

  const renewReq = useStore(renewRequest)
  useEffect(() => {
    const requested = renewReq
    if (!requested || !props.active) return
    let username = requested
    if (!owned.some(s => s.username === requested)) {
      if (!loadedOwned) return
      username = owned.find(s => s.invoiceId === requested)?.username ?? requested
    }
    setInGhajar(true); setSection(1); setRenewUsername(username)
    renewRequest.set(null)
  }, [renewReq, props.active, owned, loadedOwned])

  const noticesReq = useStore(openNoticesRequest)
  useEffect(() => { if (noticesReq > 0) { setInGhajar(true); setSection(2) } }, [noticesReq])

  const payTick = useStore(paymentReturn)
  useEffect(() => { if (payTick > 0) void co.checkPayment() }, [payTick])

  useEffect(() => {
    if (section > 5) setSection(0)
    if (props.active) window.scrollTo({ top: 0 })
    if (section === 3 && linked) void co.refreshMethods()
  }, [section, ck.checkoutVisible, inGhajar, linkPrompt])

  // ---------------------------------------------------------------- link flow

  useEffect(() => {
    const s = linkSession
    if (!s || !props.active) return
    const tick = () => setLinkRemaining(remainingSeconds(s.expiresAtMillis, Date.now()))
    tick()
    const t = setInterval(tick, 1000)
    return () => clearInterval(t)
  }, [linkSession?.sessionToken, props.active])

  useEffect(() => {
    const s = linkSession
    if (!s || !props.active) return
    if (isLinked()) { setLinkSession(null); setMessage(linkMessage('LINKED')); return }
    let alive = true
    let timer: ReturnType<typeof setTimeout>
    let failures = 0
    let gate = linkGate
    const loop = async () => {
      if (!alive) return
      if (document.visibilityState !== 'visible') { timer = setTimeout(loop, 1500); return }
      let result: LinkState
      if (Date.now() >= s.expiresAtMillis) result = 'EXPIRED'
      else {
        setLinkChecking(true)
        try { result = await api.pollLink(s.sessionToken) } catch (e) { result = e instanceof api.ApiError && e.network ? 'NETWORK_ERROR' : 'SERVER_ERROR' } finally { setLinkChecking(false) }
      }
      if (!alive) return
      setLinkState(result)
      gate = verificationGate(gate, result)
      setLinkGate(gate)
      if (result === 'LINKED') {
        setLinkSession(null); setError(null); setMessage(linkMessage(result))
        void rebindPush()
        return
      }
      if (result === 'EXPIRED' || result === 'NOT_FOUND' || result === 'SUPERSEDED') {
        if (result !== 'SUPERSEDED') clearPendingLink()
        setLinkSession(null); setMessage(null); setError(linkMessage(result))
        return
      }
      failures = ['NETWORK_ERROR', 'SERVER_ERROR', 'STORAGE_ERROR'].includes(result) ? Math.min(4, failures + 1) : 0
      timer = setTimeout(loop, Math.min(retryDelayMillis(failures), Math.max(1, s.expiresAtMillis - Date.now())))
    }
    void loop()
    const onVisible = () => { if (document.visibilityState === 'visible') { clearTimeout(timer); void loop() } }
    document.addEventListener('visibilitychange', onVisible)
    return () => { alive = false; clearTimeout(timer); document.removeEventListener('visibilitychange', onVisible) }
  }, [linkSession?.sessionToken, linkCheckKey, props.active])

  function openBot(session: LinkSession | null = linkSession) {
    if (!session || !validPendingLink(session.code, session.sessionToken, session.expiresAtMillis, Date.now())) {
      clearPendingLink(); setLinkSession(null)
      setError('کد اتصال معتبر نیست یا منقضی شده؛ دوباره «اتصال با تلگرام» را بزن.')
      return
    }
    const verification = linkGate != null
    const urls = verification ? botVerificationUrls(session.botUsername) : botLoginUrls(session.botUsername, session.code)
    // tg:// first (opens the Telegram app directly); the https link is the
    // fallback the browser always understands.
    const https = urls.find(u => u.startsWith('https://')) ?? urls[0]
    const w = window.open(https, '_blank')
    if (!w) location.href = https
    setMessage(verification ? 'ربات باز شد؛ «Start / شروع» را بزن و مراحل تأیید حساب را کامل کن.'
      : 'لینک تلگرام با کد اتصال آماده باز شد؛ «Start / شروع» را بزن و برگرد. نیازی به تایپ کد نیست.')
  }

  async function beginLink() {
    setBusy(true); setError(null); setMessage(null)
    try {
      const s = await api.beginLink()
      setLinkSession(s); setLinkState('PENDING'); setLinkRemaining(remainingSeconds(s.expiresAtMillis, Date.now()))
      openBot(s)
    } catch (e) {
      setError(api.publicMessage(e) || 'ساخت کد اتصال انجام نشد؛ دوباره تلاش کن.')
    } finally { setBusy(false) }
  }

  function signOut() {
    setSignOutConfirm(false)
    clearAccount()
    setLinkSession(null); setLinkState('PENDING')
    setPanels([]); setSelectedPanel(null); setCategories([]); setSelectedCategory(null); setTimeRanges([]); setSelectedTime(null)
    setProducts([]); setUnfiltered([]); setTrialOptions(null); setLoadedPanelId(null)
    resetOwned()
    setSection(0); setInGhajar(false)
    co.resetCheckout()
    setMessage('از حساب فروشگاه خارج شدی. برای ورود با حساب دیگر، کد تازه بگیر.')
    setError(null)
  }

  // ---------------------------------------------------------------- derived

  const shownOwned = useMemo(() => M.sortedFor(owned, ownedSort), [owned, ownedSort])
  const bestValueId = useMemo(() => {
    if (products.length <= 1) return null
    let best: string | null = null, bestRatio = Infinity
    for (const p of products) {
      if (p.price == null || p.trafficGb == null || p.price <= 0 || p.trafficGb <= 0) continue
      const r = p.price / p.trafficGb
      if (r < bestRatio) { bestRatio = r; best = p.id }
    }
    return best
  }, [products])
  const pendingPurchase = ck.checkoutVisible ? ck.purchase : null
  const pendingEntries = useMemo(() => {
    const entries = [...ck.pendingPayments]
    const local = ck.payment
    if (local && !entries.some(e => e.orderId === local.orderId)) {
      entries.unshift({ orderId: local.orderId, method: local.method, label: local.methodLabel || 'پرداخت', amount: local.amount, expiresAt: local.expiresAt, status: 'pending' })
    }
    return entries
  }, [ck.pendingPayments, ck.payment])
  const prices = unfiltered.map(p => p.price).filter((p): p is number => p != null && p > 0)

  // ---------------------------------------------------------------- render

  return (
    <div class="page" ref={topRef}>
      <ShopHeader linked={linked} onRefresh={() => setRefreshKey(refreshKey + 1)} />

      {!shop.enabled && inGhajar ? (
        <Slab accent="var(--warning)">
          <div class="row">
            <Icon name="info" size={22} color="var(--warning)" />
            <span style={{ width: '8px' }} />
            <div class="col grow">
              <span class="title-small bold c-text">فروشگاه موقتاً غیرفعال است</span>
              {shop.message ? <span class="body-small c-text2">{shop.message}</span> : null}
            </div>
          </div>
        </Slab>
      ) : null}
      {busy || ck.busy ? <LinearProgress /> : null}
      {message ? <StatusCard text={message} error={false} onDismiss={() => setMessage(null)} /> : null}
      {error ? <StatusCard text={error} error onDismiss={() => setError(null)} onRetry={() => { setError(null); setRefreshKey(refreshKey + 1) }}
        footnote={unfiltered.length ? 'پلن‌های پایین آخرین فهرستی است که دریافت شده؛ ممکن است قیمت‌ها تازه نباشد.' : null} /> : null}

      {inGhajar ? <>
        <GhostPill text="همهٔ فروشگاه‌ها" icon="chevron_left" onClick={() => setInGhajar(false)} />
        <GhajarShopIntro logo={ghajarLogo} />
        {announcements.length ? <Announcements items={announcements} onUseCode={code => {
          setDiscountCode(code); setSection(0); setMessage(`🎟 کد تخفیف ${code} در سفارش قرار گرفت؛ پلن را انتخاب کن.`)
        }} /> : null}
      </> : null}

      {!linked && (inGhajar || linkPrompt || linkSession != null) ? (
        <LinkAccountCard session={linkSession} busy={busy} state={linkState} verification={linkGate != null} checking={linkChecking}
          remaining={linkRemaining} onBegin={beginLink} onOpenBot={() => openBot()} onCheck={() => { if (!linkChecking) setLinkCheckKey(linkCheckKey + 1) }}
          onCancel={() => { clearPendingLink(); setLinkSession(null); setMessage('درخواست ورود در این دستگاه لغو شد؛ برای ادامه کد تازه بگیر.'); setError(null) }}
          onCopy={() => { if (linkSession) { void copyText(linkSession.code, 'کد اتصال'); setMessage('کد اتصال کپی شد؛ آن را بدون هیچ حرف اضافه‌ای در ربات بفرست.') } }} />
      ) : null}

      {!inGhajar ? <>
        <MarketScreen active={props.active && !inGhajar} signedIn={linked}
          onSignIn={() => { setLinkPrompt(true); setMessage('برای خرید، اول حساب را با «اتصال با تلگرام» متصل کن.') }}
          onGhajarLogo={setGhajarLogo}
          ghajarEntry={<GhajarEntryCard logo={ghajarLogo} gbPrice={ghajarGbPrice} gbMax={ghajarGbMax}
            cheapest={prices.length ? Math.min(...prices) : null} dearest={prices.length ? Math.max(...prices) : null}
            planCount={unfiltered.length} serviceCount={owned.length} onOpen={() => { setInGhajar(true); setLinkPrompt(false) }} />} />
        {!linked && !linkPrompt && linkSession == null ? <GhostPill text="اتصال حساب با تلگرام" icon="link" onClick={() => setLinkPrompt(true)} /> : null}
      </> : linked ? <>
        <ScreenHeader title="فروشگاه" context="پلن‌ها، سرویس‌ها، کیف پول و پشتیبانی"
          trailing={<button class="icon-btn" aria-label="خروج از حساب فروشگاه" onClick={() => setSignOutConfirm(true)}><Icon name="link_off" color="var(--text2)" /></button>} />
        <TabRail style={look.storeTabStyle} selected={Math.min(section, 5)} onSelect={setSection} tabs={[
          { label: 'خرید', icon: 'shopping_cart' },
          { label: 'سرویس‌ها', icon: 'dns', badge: owned.length },
          { label: 'پیام‌ها', icon: 'notifications', badge: unread || notices.length },
          { label: 'کیف پول', icon: 'account_balance_wallet', badge: ck.pendingPayments.length },
          { label: 'پشتیبانی', icon: 'support_agent' },
          { label: 'تراکنش‌ها', icon: 'swap_horiz' }
        ]} />

        {section === 4 ? <Tickets /> : null}
        {section === 5 ? <TransactionHistory revision={refreshKey + ck.revision} /> : null}

        {pendingEntries.map(item => (
          <PendingPaymentCard item={item} busy={ck.busy}
            onResume={() => { void co.resumePayment(item); setInGhajar(true); setSection(0) }}
            onCancel={() => void co.cancelPayment(item.orderId)} />
        ))}

        {section === 3 ? (
          <Slab padding={18} spacing={12}>
            <Rail label="کیف پول قاجار" />
            <span class="display-small bold c-highlight">{ck.methods ? formatPrice(ck.methods.balance) : '…'}</span>
            <span class="label-medium c-text2">{ck.methods?.currency ?? 'در حال دریافت موجودی'}</span>
            <div class="row gap-sm">
              {[50_000, 100_000, 200_000, 500_000].map(a => {
                const on = walletAmount === String(a)
                return <button class="grow label-medium" onClick={() => setWalletAmount(String(a))} style={{
                  borderRadius: '999px', padding: '8px 0', background: on ? 'var(--primary)' : 'var(--card)',
                  color: on ? 'var(--on-primary)' : 'var(--text2)', fontWeight: on ? 700 : 400, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis'
                }}>{formatPrice(a)}</button>
              })}
            </div>
            <SkinField label="مبلغ شارژ به تومان" placeholder="مثلاً ۱۰۰۰۰۰" value={walletAmount} inputMode="numeric" dir="ltr"
              helper="شارژ پس از تأیید پنل به موجودی اضافه می‌شود؛ برای شارژ سرویس جدید ساخته نمی‌شود."
              onInput={v => setWalletAmount(asciiDigits(v).slice(0, 12))} />
            <PillButton text="شارژ کیف پول" icon="account_balance_wallet" enabled={!ck.busy && (+walletAmount || 0) > 0}
              onClick={() => { void co.topUp(+walletAmount || 0); setSection(0) }} />
            <GhostPill text="بروزرسانی موجودی" icon="refresh" enabled={!ck.busy} onClick={() => void co.refreshMethods()} />
          </Slab>
        ) : null}

        {section === 2 ? <>
          {notices.length === 0 && !busy ? <span style={{ padding: '16px' }}>پیام تازه‌ای ندارید</span> : null}
          {notices.length ? <div class="card-grid">{notices.map(n => <NoticeCard notice={n} />)}</div> : null}
        </> : null}

        {section === 1 ? <>
          <SectionTitle title="سرویس‌های من" subtitle="برای دریافت لینک و افزودن به اپ روی سرویس بزن" />
          {owned.length ? <ServiceSortChips services={owned} selected={ownedSort} onSelect={setOwnedSort} /> : null}
          {owned.length === 0 && !busy ? <span>هنوز سرویسی برای این حساب ثبت نشده است.</span> : null}
          {owned.length > 0 && shownOwned.length === 0 ? <span class="c-muted">سرویسی در این دسته نیست.</span> : null}
          {shownOwned.length ? <div class="card-grid">{shownOwned.map(s => <OwnedServiceCard service={s} onImport={() => void co.importOwned(s.username)} onRenew={() => setRenewUsername(s.username)} />)}</div> : null}
        </> : null}

        {section === 0 ? <>
          {!pendingPurchase ? <>
            <GhostPill text="دریافت سرویس تست رایگان" icon="card_giftcard" accent="var(--premium)" enabled={!busy && !ck.busy}
              onClick={async () => { setBusy(true); try { setTrialOptions(await api.trialOptions()) } catch (e) { setError(api.publicMessage(e)) } finally { setBusy(false) } }} />
            {trialOptions ? <>
              {!trialOptions.canRequest ? <span class="c-error">سهمیهٔ سرویس تست در دسترس نیست</span> : null}
              {trialOptions.panels.map(p => (
                <Slab spacing={0}>
                  <SlabRow title={p.name} subtitle={p.remaining != null ? `باقی‌مانده: ${fa(p.remaining)}` : 'سرویس آزمایشی'} icon="card_giftcard"
                    accent="var(--premium)" chevron enabled={trialOptions.canRequest && (p.remaining == null || p.remaining > 0) && !ck.busy}
                    onClick={() => { void co.trial(p.code, customUsername); setTrialOptions(null) }} />
                </Slab>
              ))}
            </> : null}
            <SectionTitle title="۱. انتخاب سرویس" subtitle="قیمت و موجودی مستقیماً از پنل دریافت می‌شود" />
            {panels.length === 0 && !busy ? <SkinEmpty title="هیچ سرویسی از پنل دریافت نشد" hint="چند لحظه بعد دوباره بررسی کن؛ اگر ادامه داشت، از پشتیبانی بپرس." icon="shopping_cart" actionText="تلاش دوباره" onAction={() => setRefreshKey(refreshKey + 1)} /> : null}
            {panels.length ? <ServiceTypeGrid items={panels} selected={selectedPanel} label={p => p.name} icon={p => panelIcon(p.name)} onSelect={setSelectedPanel} same={(a, b) => a.id === b.id} /> : null}
            {categories.length ? <ChipFlowRow allLabel="همه دسته‌ها" items={categories} selected={selectedCategory} label={c => c.name} onSelect={setSelectedCategory} same={(a, b) => a.id === b.id} /> : null}
            {timeRanges.length ? <ChipFlowRow allLabel="همه مدت‌ها" items={timeRanges} selected={selectedTime} label={t => t.name} onSelect={setSelectedTime} same={(a, b) => a.days === b.days} /> : null}
            {selectedPanel?.custom ? <SlidingSegments labels={['پلن‌های آماده', 'سرویس سفارشی']} selected={customMode ? 1 : 0} onSelect={i => setCustomMode(i === 1)} /> : null}
            {customMode ? (
              <CustomServiceCard traffic={customTraffic} days={customDays} quote={customQuote}
                onTraffic={v => { setCustomTraffic(asciiDigits(v).slice(0, 5)); setCustomQuote(null) }}
                onDays={v => { setCustomDays(asciiDigits(v).slice(0, 4)); setCustomQuote(null) }}
                onQuote={async () => {
                  const panel = selectedPanel
                  if (!panel || busy) return
                  const t = customTraffic, d = customDays
                  setBusy(true)
                  try { const q = await api.customQuote(panel.id, +t || 0, +d || 0); setCustomQuote(q) } catch (e) { setError(api.publicMessage(e)) } finally { setBusy(false) }
                }} />
            ) : <>
              {products.length > 1 ? <GhostPill text="مقایسهٔ پلن‌ها" onClick={() => setComparePlans(true)} /> : null}
              {products.length === 0 && !busy ? <SkinEmpty title="پلنی با این فیلترها پیدا نشد" hint="دستهٔ دیگری انتخاب کن یا فیلتر مدت را بردار." icon="shopping_cart"
                actionText={selectedCategory || selectedTime ? 'برداشتن فیلترها' : null}
                onAction={selectedCategory || selectedTime ? () => { setSelectedCategory(null); setSelectedTime(null) } : null} /> : null}
              {products.length === 0 && busy ? <SkinLoading label="در حال دریافت پلن‌ها از پنل…" /> : null}
              {products.length ? <div class="card-grid">{products.map(p => (
                <ProductCard product={p} enabled={!busy && !ck.busy} bestValue={p.id === bestValueId}
                  onBuy={() => { setConfirmationTitle(p.name); setConfirmationPrice(p.price); setConfirmation({ countryId: p.countryId, serviceId: p.id }) }} />
              ))}</div> : null}
            </>}
            {comparePlans ? <PlanComparisonDialog products={products} onDismiss={() => setComparePlans(false)} /> : null}
            {selectedPanel && customMode ? (
              <PillButton text="خرید سرویس سفارشی" icon="shopping_cart" enabled={customQuote?.price != null && !busy}
                onClick={() => {
                  setConfirmationTitle(`سرویس سفارشی · ${fa(customTraffic)} گیگ · ${fa(customDays)} روز`)
                  setConfirmationPrice(customQuote?.price ?? null)
                  setConfirmation({ countryId: selectedPanel.id, customTrafficGb: +customTraffic || null, customTimeDays: +customDays || null })
                }} />
            ) : null}
          </> : null}

          {pendingPurchase?.requiresPayment ? <>
            <SectionTitle title="۳. پرداخت" subtitle="فاکتور روی دستگاه حفظ می‌شود؛ وضعیت را از سرور بررسی کن" />
            <PaymentSummary purchase={pendingPurchase} walletTopUp={ck.walletTopUp}
              exactCardAmount={ck.payment && cardPayment(ck.payment.kind, ck.payment.cardNumber) ? ck.payment.amount : null} />
            {!ck.payment ? <>
              {!ck.methods ? (ck.busy ? <SkinLoading label="در حال دریافت روش‌های پرداخت…" /> : <GhostPill text="دریافت روش‌های پرداخت" icon="credit_card" onClick={() => void co.refreshMethods()} />) : null}
              {(ck.methods?.methods ?? []).map(m => <PaymentMethodCard method={m} amount={pendingPurchase.amountDue} enabled={!ck.busy} onClick={() => void co.beginPayment(m)} />)}
            </> : null}
          </> : null}

          {ck.payment && ck.checkoutVisible ? <>
            {cardPayment(ck.payment.kind, ck.payment.cardNumber) ? (
              <CardToCardCard payment={ck.payment} receipt={ck.receipt} busy={ck.busy} sent={ck.receiptSent}
                onPick={co.setReceipt} onUpload={() => void co.uploadReceipt()} />
            ) : null}
            <Slab spacing={12}>
              <SlabRow title="کد پیگیری" value={<span class="ltr">{ck.payment.orderId}</span>} icon="receipt_long" accent="var(--info)" />
              {ck.busy ? <>
                <span class="label-large c-highlight">در حال بررسی وضعیت پرداخت…</span>
                <LinearProgress />
              </> : <>
                {ck.payment.url ? <PillButton text="ادامهٔ همین پرداخت" icon="open_in_new" onClick={() => co.openCheckout(ck.payment!.url!)} /> : null}
                <GhostPill text={co.deliveryFailed() ? 'تلاش مجدد برای تحویل سرویس' : 'پرداخت کردم؛ بررسی و دریافت سرویس'} icon="autorenew" onClick={() => void co.checkPayment()} />
              </>}
              {co.deliveryFailed() ? <span class="body-small c-error">پرداخت تأیید شده؛ تحویل سرویس یک‌بار ناموفق بود. دوباره پرداخت نکن، فقط تلاش مجدد را بزن.</span> : null}
              <span class="label-small c-muted">وضعیت پرداخت به‌صورت خودکار بررسی می‌شود. بستن صفحه به معنی لغو تراکنش نیست؛ در صورت پرداخت، دوباره واریز نکن.</span>
            </Slab>
          </> : null}
          {pendingPurchase ? <TextButton enabled={!ck.busy} onClick={co.leaveInvoice}>بازگشت به محصولات</TextButton> : null}
        </> : null}

        {section === 4 ? <GhostPill text="باز کردن ربات پشتیبانی در تلگرام" icon="support_agent" onClick={() => { const w = window.open('https://t.me/Ghajar_vpnbot', '_blank'); if (!w) location.href = 'https://t.me/Ghajar_vpnbot' }} /> : null}
      </> : null}

      <div style={{ height: '28px' }} />

      {ck.delivery ? (
        <AddToAppSheet productName={ck.delivery.service.productName} username={ck.delivery.service.username}
          subscriptionUrl={ck.delivery.service.subscriptionUrl} configs={ck.delivery.service.outputs}
          busy={ck.busy} failed={co.deliveryFailed()} synced={ck.delivery.synced}
          title={ck.delivery.synced ? 'سرویس آماده است' : undefined}
          onRetry={() => {
            const d = ck.delivery!
            if (ck.payment && ck.purchase?.username === d.service.username) void co.checkPayment()
            else void co.importOwned(d.service.username)
          }}
          onDismiss={co.clearDelivery} />
      ) : null}

      {signOutConfirm ? (
        <Dialog title="خروج از حساب فروشگاه" onDismiss={() => setSignOutConfirm(false)}
          actions={<><TextButton onClick={() => setSignOutConfirm(false)}>انصراف</TextButton><TextButton onClick={signOut}>خروج</TextButton></>}>
          <span style={{ whiteSpace: 'pre-line' }}>{'این دستگاه از حساب فعلی جدا می‌شود و می‌توانی با حساب دیگری وارد شوی.\n\nسرویس‌هایی که در اپ‌های VPN اضافه کرده‌ای پاک نمی‌شوند؛ فقط خرید، تمدید، کیف پول و اعلان‌های فروشگاه تا ورود دوباره در دسترس نیستند.\n\nبرای ورود دوباره به یک کد تازه از ربات نیاز داری.'}</span>
        </Dialog>
      ) : null}

      {confirmation ? (
        <Dialog title="۲. تأیید سفارش" onDismiss={() => setConfirmation(null)}
          actions={<>
            <TextButton onClick={() => setConfirmation(null)}>بازگشت</TextButton>
            <button class="m-btn" disabled={busy || ck.busy || confirmationPrice == null || (selectedPanel?.usernameRequired === true && !customUsername.trim())}
              onClick={() => { const r = confirmation; setConfirmation(null); void co.buy({ ...r, customUsername, note: customNote, discountCode }) }}>تأیید و ادامه</button>
          </>}>
          <span class="bold c-text">{confirmationTitle}</span>
          <span class="c-text">{confirmationPrice != null ? `قیمت پایه: ${formatPrice(confirmationPrice)} تومان` : 'قیمت در دسترس نیست'}</span>
          <span class="body-small">با تأیید، خرید با موجودی کیف پول انجام می‌شود؛ اگر کافی نباشد مرحلهٔ پرداخت باز می‌شود.</span>
          <PurchaseExtras username={customUsername} usernameRequired={selectedPanel?.usernameRequired === true} note={customNote}
            showUsername={selectedPanel?.customUsername === true} showNote={selectedPanel?.noteEnabled === true} discount={discountCode}
            onUsername={setCustomUsername} onNote={setCustomNote} onDiscount={setDiscountCode} />
        </Dialog>
      ) : null}

      {renewUsername ? (
        <RenewServiceDialog username={renewUsername} onDismiss={() => setRenewUsername(null)}
          onTopUp={() => { setRenewUsername(null); setSection(3) }}
          onRenewed={() => { setRenewUsername(null); toast('سرویس تمدید شد'); void refreshOwned(); void refreshNotices() }} />
      ) : null}
    </div>
  )
}

function ShopHeader(props: { linked: boolean; onRefresh: () => void }) {
  return (
    <div class="row" style={{ paddingTop: '14px' }}>
      <div class="col gap-xs grow">
        <span class="title-large bold c-text">خزانهٔ قاجار</span>
        <div class="row gap-6">
          <span style={{ width: '7px', height: '7px', borderRadius: '50%', background: props.linked ? 'var(--good)' : 'var(--warning)', flex: 'none' }} />
          <span class="body-small c-text2">{props.linked ? 'حساب متصل و همگام است' : 'برای خرید، حساب ربات را یک‌بار متصل کن'}</span>
        </div>
      </div>
      {props.linked ? <button class="icon-btn" aria-label="بروزرسانی" onClick={props.onRefresh}><Icon name="refresh" /></button> : null}
    </div>
  )
}

function GhajarShopIntro(props: { logo: number }) {
  return (
    <Slab spacing={8}>
      <div class="row">
        <CrestLogo size={56} logoVersion={props.logo} />
        <span style={{ width: '12px' }} />
        <div class="col grow">
          <div class="row gap-xs">
            <span class="title-medium bold c-text">فروشگاه قاجار</span>
            <Icon name="verified" size={16} color="var(--primary)" title="رسمی" />
          </div>
          <span class="label-medium c-text2">فروشگاه رسمی برنامه</span>
        </div>
      </div>
      <span class="body-small c-text2">سرویس‌های رسمی قاجار با پشتیبانی مستقیم: پلن‌های آماده و دلخواه، سرویس تست رایگان، کیف پول، تمدید هر سرویس و تحویل خودکار لینک اتصال.</span>
    </Slab>
  )
}

function GhajarEntryCard(props: { logo: number; gbPrice: number | null; gbMax: number | null; cheapest: number | null; dearest: number | null; planCount: number; serviceCount: number; onOpen: () => void }) {
  return (
    <Slab onClick={props.onOpen} spacing={8} accent="var(--premium)">
      <div class="row">
        <CrestLogo logoVersion={props.logo} />
        <span style={{ width: '8px' }} />
        <div class="col grow">
          <div class="row gap-xs">
            <span class="bold c-text">فروشگاه قاجار</span>
            <Icon name="verified" size={14} color="var(--primary)" title="رسمی" />
          </div>
          <span class="label-small c-text2 ellipsis">فروشگاه رسمی برنامه · تست رایگان · کیف پول</span>
        </div>
        <Icon name="chevron_left" color="var(--muted)" mirror={false} />
      </div>
      {props.gbPrice != null ? <span class="label-small c-text2">💾 هر گیگ {formatPrice(props.gbPrice)}{props.gbMax != null && props.gbMax > props.gbPrice ? ` تا ${formatPrice(props.gbMax)}` : ''} تومان</span> : null}
      {props.cheapest != null ? <span class="label-small c-text2">🏷 ارزان‌ترین {formatPrice(props.cheapest)}{props.dearest != null && props.dearest > props.cheapest ? ` · گران‌ترین ${formatPrice(props.dearest)}` : ''} تومان{props.planCount > 0 ? ` · ${fa(props.planCount)} پلن` : ''}</span> : null}
      {props.serviceCount > 0 ? <span class="label-small c-primary">📦 {fa(props.serviceCount)} سرویس فعال شما در این فروشگاه</span> : null}
    </Slab>
  )
}
