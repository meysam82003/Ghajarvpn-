import { ComponentChildren } from 'preact'
import { createPortal } from 'preact/compat'
import { useEffect, useRef } from 'preact/hooks'
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

export function ToastHost() {
  const t = useStore(toastStore)
  if (!t) return null
  return <div class="toast-host" aria-live="polite"><div class="toast" key={t.id}>{t.text}</div></div>
}
