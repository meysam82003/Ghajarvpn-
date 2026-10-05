import { useEffect, useRef, useState } from 'preact/hooks'
import { Icon, RoyalIcon } from './Icon'
import { useStore } from '../lib/store'
import { lookStore, setLook, paletteStore } from '../theme/look'
import { noticeStore, acknowledge } from '../state/notices'
import { Notice } from '../api/models'
import { Dialog } from './Overlay'
import { TextButton } from './Skin'
import { renewRequest, shopOpenRequest, goTab } from '../state/ui'
import wordmark from '../assets/images/wordmark.webp'

/** The CenterAlignedTopAppBar: wordmark on Home, an Anta title elsewhere, theme flip at the end. */
export function TopBar(props: { title: string | null; onBack?: (() => void) | null }) {
  const p = useStore(paletteStore)
  const [scrolled, setScrolled] = useState(false)
  useEffect(() => {
    const on = () => setScrolled(window.scrollY > 4)
    window.addEventListener('scroll', on, { passive: true })
    on()
    return () => window.removeEventListener('scroll', on)
  }, [])
  return (
    <header class={'topbar' + (scrolled ? ' scrolled' : '')}>
      <div class="topbar-inner">
        <span class="slot">
          {props.onBack
            ? <button class="icon-btn bounce" aria-label="بازگشت" onClick={props.onBack}><Icon name="arrow_back" /></button>
            : null}
        </span>
        <div class="title">
          {props.title == null
            ? <img class="wordmark" src={wordmark} alt="قاجار VPN" />
            : <h1 class="mixed">{props.title}</h1>}
        </div>
        <span class="slot">
          <button class="icon-btn bounce" aria-label="تغییر تم"
            onClick={() => setLook({ theme: p.dark ? 'PREMIUM_GREEN_LIGHT' : 'PREMIUM_GREEN_DARK' })}>
            <Icon name={p.dark ? 'light_mode' : 'dark_mode'} />
          </button>
        </span>
      </div>
    </header>
  )
}

export interface NavItem { icon: 'home' | 'shop' | 'settings'; label: string; badge?: boolean; onSelect: () => void }

/** SkinNavBar: a floating capsule with one filled indicator that slides between destinations. */
export function NavBar(props: { items: NavItem[]; selected: number }) {
  const look = useStore(lookStore)
  const n = props.items.length
  const cls = ['navbar', look.navStyle, 'ind-' + look.navIndicator, look.navLabels ? '' : 'no-labels'].join(' ')
  const inset = 0
  return (
    <nav class={cls} aria-label="ناوبری اصلی">
      <div class="capsule">
        <div class="track" style={{ ['--cell-w' as any]: `calc(100% / ${n})` }}>
          <span class="indicator" style={{
            width: `calc(100% / ${n} - ${inset * 2}px)`,
            insetInlineStart: `calc(100% / ${n} * ${props.selected} + ${inset}px)`
          }} />
          {props.items.map((item, i) => (
            <button class={'cell' + (i === props.selected ? ' active' : '')} aria-current={i === props.selected ? 'page' : undefined}
              onClick={item.onSelect} aria-label={item.label}>
              <RoyalIcon name={item.icon} size={look.navIconSize} />
              {look.navLabels ? <span class="lbl">{item.label}</span> : null}
              {item.badge ? <span class="dot" /> : null}
            </button>
          ))}
        </div>
      </div>
    </nav>
  )
}

/**
 * GhajarNoticeBanner: one glass strip across every tab, swiped away to the
 * right, "خوندم" to acknowledge, tapped open for the full text and its action.
 */
export function NoticeBanner() {
  const current = useStore(noticeStore)
  const [expanded, setExpanded] = useState<string | null>(null)
  const [drag, setDrag] = useState(0)
  const start = useRef<number | null>(null)
  if (!current) return null
  const threshold = 90
  const dismiss = () => { void acknowledge(current.id) }
  return (
    <>
      <div class="notice-banner" key={current.id} aria-live="polite"
        style={{ transform: `translateX(${drag}px)`, opacity: Math.max(0.15, Math.min(1, 1 - drag / (threshold * 3))) }}
        onPointerDown={e => { start.current = e.clientX }}
        onPointerMove={e => { if (start.current != null) setDrag(Math.max(0, e.clientX - start.current)) }}
        onPointerUp={() => { if (drag > threshold) dismiss(); setDrag(0); start.current = null }}
        onPointerCancel={() => { setDrag(0); start.current = null }}>
        <div class="inner">
          <button class="main" onClick={() => { if (drag < 6) setExpanded(current.id) }}>
            <Icon name="notifications" size={22} color={current.important ? 'var(--highlight)' : 'var(--primary)'} />
            <span class="col grow">
              <span class="semibold ellipsis">{current.title}</span>
              {current.message !== current.title ? <span class="body-small c-text2 clamp2" style={{ whiteSpace: 'pre-line' }}>{current.message}</span> : null}
            </span>
          </button>
          <TextButton onClick={dismiss}>خوندم</TextButton>
        </div>
      </div>
      {expanded === current.id ? <NoticeDialog notice={current} onClose={() => setExpanded(null)} onAck={dismiss} /> : null}
    </>
  )
}

export function NoticeDialog(props: { notice: Notice; onClose: () => void; onAck: () => void }) {
  const n = props.notice
  const target = n.action === 'market_shop' ? n.actionRef.split('|', 2) : null
  const shopId = target && /^\d+$/.test(target[0] ?? '') ? +target[0] : null
  const code = (target?.[1] ?? '').replace(/[^\w-]/g, '')
  return (
    <Dialog title={n.title} onDismiss={props.onClose}
      actions={<>
        <TextButton onClick={props.onClose}>بازگشت</TextButton>
        {shopId != null ? <>
          <TextButton onClick={() => { props.onAck(); props.onClose(); shopOpenRequest.set({ shopId, code: '' }); goTab('shop') }}>رفتن به فروشگاه</TextButton>
          {code ? <TextButton onClick={() => { props.onAck(); props.onClose(); shopOpenRequest.set({ shopId, code }); goTab('shop') }}>استفاده از کد تخفیف</TextButton> : null}
        </> : n.serviceUsername ? (
          <TextButton onClick={() => { props.onAck(); props.onClose(); renewRequest.set(n.serviceUsername); goTab('shop') }}>تمدید همین سرویس</TextButton>
        ) : (
          <TextButton onClick={() => { props.onAck(); props.onClose() }}>خواندم</TextButton>
        )}
      </>}>
      <div class="body-medium c-text wrap-any" style={{ whiteSpace: 'pre-line' }}>{n.message}</div>
    </Dialog>
  )
}
