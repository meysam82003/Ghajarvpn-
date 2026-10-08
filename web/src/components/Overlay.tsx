import { ComponentChildren } from 'preact'
import { createPortal } from 'preact/compat'
import { useEffect, useRef } from 'preact/hooks'
import { Icon } from './Icon'
import { useStore } from '../lib/store'
import { toastStore } from '../state/ui'

/**
 * Open dialogs, innermost last. One history entry per dialog, so the system
 * back gesture (Android, browser back) closes the top dialog instead of
 * leaving the app - PredictiveBackHandler's job in the Compose app.
 */
interface ModalEntry { id: number; dismiss: () => void; dismissible: boolean; popped: boolean }
const stack: ModalEntry[] = []
let suppressPops = 0
let nextId = 1

/** Ignores the next popstate (the shell rewinding its own history entry). */
export function suppressNextPop(): void { suppressPops++ }

window.addEventListener('popstate', () => {
  if (suppressPops > 0) { suppressPops--; return }
  const top = stack[stack.length - 1]
  if (!top) return // page moves are read from the hash (hashchange)
  if (top.dismissible) { top.popped = true; top.dismiss() }
  else history.pushState({ ...(history.state ?? {}), ghajarModal: top.id }, '')
})

/** Locks page scroll under a dialog and closes it on Escape / the system back gesture. */
function useModal(onDismiss: () => void, dismissible: boolean) {
  const dismissRef = useRef(onDismiss)
  dismissRef.current = onDismiss
  useEffect(() => {
    const entry: ModalEntry = { id: nextId++, dismiss: () => dismissRef.current(), dismissible, popped: false }
    stack.push(entry)
    document.documentElement.style.overflow = 'hidden'
    const key = (e: KeyboardEvent) => { if (e.key === 'Escape' && dismissible && stack[stack.length - 1] === entry) dismissRef.current() }
    window.addEventListener('keydown', key)
    history.pushState({ ...(history.state ?? {}), ghajarModal: entry.id }, '')
    return () => {
      window.removeEventListener('keydown', key)
      const i = stack.indexOf(entry)
      if (i >= 0) stack.splice(i, 1)
      // Step back only off this dialog's own entry: if a link inside it already
      // moved the page, going back would undo that move instead.
      if (!entry.popped && (history.state as any)?.ghajarModal === entry.id) { suppressPops++; history.back() }
      if (stack.length === 0) document.documentElement.style.overflow = ''
    }
  }, [])
}

/** AlertDialog: title, scrollable body, actions at the end. */
export function Dialog(props: {
  title?: ComponentChildren
  children?: ComponentChildren
  actions?: ComponentChildren
  onDismiss: () => void
  dismissible?: boolean
}) {
  const dismissible = props.dismissible ?? true
  useModal(props.onDismiss, dismissible)
  return createPortal(
    <div class="scrim" onClick={e => { if (e.target === e.currentTarget && dismissible) props.onDismiss() }}>
      <div class="dialog" role="dialog" aria-modal="true">
        {props.title ? <h2>{props.title}</h2> : null}
        {props.children ? <div class="body">{props.children}</div> : null}
        {props.actions ? <div class="actions">{props.actions}</div> : null}
      </div>
    </div>,
    document.body
  )
}

/** A bottom sheet (a centred card on wide screens). */
export function Sheet(props: { children: ComponentChildren; onDismiss: () => void; title?: ComponentChildren }) {
  useModal(props.onDismiss, true)
  return createPortal(
    <div class="scrim sheet-scrim" onClick={e => { if (e.target === e.currentTarget) props.onDismiss() }}>
      <div class="sheet" role="dialog" aria-modal="true">
        <span class="handle" />
        {props.title ? <div class="title-medium bold c-text" style={{ padding: '0 4px 8px' }}>{props.title}</div> : null}
        <div class="sheet-body">{props.children}</div>
      </div>
    </div>,
    document.body
  )
}

/**
 * A screen of its own over the whole window (a pushed destination in the
 * Compose app): a bar with the way back, the title and its own tools, and a
 * body that fills the rest. Back / Escape close it like any dialog.
 */
export function FullPage(props: {
  title: ComponentChildren
  subtitle?: ComponentChildren
  trailing?: ComponentChildren
  children: ComponentChildren
  onDismiss: () => void
  class?: string
}) {
  useModal(props.onDismiss, true)
  return createPortal(
    <div class="scrim page-scrim">
      <div class={'fullpage' + (props.class ? ' ' + props.class : '')} role="dialog" aria-modal="true">
        <header class="fullpage-bar">
          <button class="icon-btn bounce" aria-label="بازگشت" onClick={props.onDismiss}><Icon name="arrow_back" /></button>
          <div class="col grow">
            <h1 class="ellipsis">{props.title}</h1>
            {props.subtitle ? <span class="label-medium c-text2 ellipsis">{props.subtitle}</span> : null}
          </div>
          {props.trailing}
        </header>
        <div class="fullpage-body">{props.children}</div>
      </div>
    </div>,
    document.body
  )
}

/**
 * A DropdownMenu: a small list anchored under the control that opened it.
 * It is a layer of its own, so Back/Escape close it first and a TV remote
 * stays inside it.
 */
export function Popover(props: { anchor: HTMLElement | null; onDismiss: () => void; children: ComponentChildren; width?: number }) {
  useModal(props.onDismiss, true)
  const box = useRef<HTMLDivElement>(null)
  useEffect(() => { box.current?.querySelector<HTMLElement>('button:not([disabled])')?.focus({ preventScroll: true }) }, [])
  const r = props.anchor?.getBoundingClientRect()
  const w = props.width ?? 240
  const vw = window.innerWidth, vh = window.innerHeight
  const style: Record<string, string> = { width: `${w}px` }
  if (r) {
    // Reading start (the right edge in Persian) lines up with the control's.
    const rtl = document.documentElement.dir !== 'ltr'
    let left = rtl ? r.right - w : r.left
    left = Math.max(8, Math.min(vw - w - 8, left))
    style.left = `${left}px`
    if (r.bottom + 260 > vh && r.top > vh / 2) { style.bottom = `${vh - r.top + 6}px`; style.maxHeight = `${r.top - 14}px` }
    else { style.top = `${r.bottom + 6}px`; style.maxHeight = `${vh - r.bottom - 14}px` }
  }
  return createPortal(
    <div class="scrim menu-scrim" onClick={e => { if (e.target === e.currentTarget) props.onDismiss() }}>
      <div class="menu" role="menu" ref={box} style={style}>{props.children}</div>
    </div>,
    document.body
  )
}

export function MenuItem(props: { icon?: string; label: ComponentChildren; onClick: () => void; enabled?: boolean; danger?: boolean; checked?: boolean }) {
  return (
    <button class={'menu-item' + (props.danger ? ' danger' : '')} role="menuitem" disabled={props.enabled === false} onClick={props.onClick}>
      {props.icon ? <Icon name={props.icon} size={18} /> : null}
      <span class="grow">{props.label}</span>
      {props.checked ? <Icon name="check" size={18} color="var(--primary)" /> : null}
    </button>
  )
}

export function MenuDivider() { return <span class="menu-divider" /> }

export function ToastHost() {
  const t = useStore(toastStore)
  if (!t) return null
  return <div class="toast-host" aria-live="polite"><div class="toast" key={t.id}>{t.text}</div></div>
}
