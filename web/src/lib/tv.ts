/**
 * TV mode: the app driven by a remote (arrows, OK, Back) on LG webOS, Samsung
 * Tizen, Android TV browsers and the like. The arrows move focus to the
 * nearest control in that direction, OK clicks it, Back goes back.
 */
import { back } from '../state/ui'

const TV_UA = /Web0S|webOS|NetCast|SmartTV|SMART-TV|Tizen|HbbTV|BRAVIA|AFT[A-Z]|Android TV|GoogleTV|CrKey/i
const FOCUSABLE = 'button:not([disabled]), a[href], input:not([disabled]):not([type=hidden]), textarea:not([disabled]), select:not([disabled]), [role="button"], [tabindex]:not([tabindex="-1"])'
// webOS and Tizen send their own codes for Back; browsers send Backspace/Escape.
const BACK_KEYS = new Set([461, 10009, 8, 27])

export function isTv(): boolean {
  try {
    if (new URLSearchParams(location.search).get('tv') === '1') localStorage.setItem('ghajar.tv', '1')
    if (localStorage.getItem('ghajar.tv') === '1') return true
  } catch { /* storage blocked */ }
  return TV_UA.test(navigator.userAgent)
}

function visible(el: HTMLElement): boolean {
  const r = el.getBoundingClientRect()
  if (r.width < 2 || r.height < 2) return false
  const st = getComputedStyle(el)
  return st.visibility !== 'hidden' && st.display !== 'none' && !el.closest('[hidden],[aria-hidden="true"]')
}

/** The controls the remote may reach: inside the top dialog/sheet when one is open. */
function candidates(): HTMLElement[] {
  const layers = document.querySelectorAll<HTMLElement>('.scrim')
  const root: ParentNode = layers.length ? layers[layers.length - 1] : document
  return Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(visible)
}

type Dir = 'up' | 'down' | 'left' | 'right'

function next(from: HTMLElement | null, dir: Dir): HTMLElement | null {
  const list = candidates()
  // Nothing focused yet: the connect control, as on the Android TV home screen.
  if (!from || !list.includes(from)) return list.find(el => el.classList.contains('orb')) ?? list[0] ?? null
  const a = from.getBoundingClientRect()
  const ax = a.left + a.width / 2, ay = a.top + a.height / 2
  let best: HTMLElement | null = null
  let bestScore = Infinity
  for (const el of list) {
    if (el === from || el.contains(from) || from.contains(el)) continue
    const b = el.getBoundingClientRect()
    const bx = b.left + b.width / 2, by = b.top + b.height / 2
    const dx = bx - ax, dy = by - ay
    let main: number, cross: number
    switch (dir) {
      case 'up': if (b.bottom > a.top + 1) continue; main = a.top - b.bottom; cross = Math.abs(dx); break
      case 'down': if (b.top < a.bottom - 1) continue; main = b.top - a.bottom; cross = Math.abs(dx); break
      case 'left': if (b.right > a.left + 1) continue; main = a.left - b.right; cross = Math.abs(dy); break
      default: if (b.left < a.right - 1) continue; main = b.left - a.right; cross = Math.abs(dy)
    }
    // Overlapping on the cross axis (same row/column) wins over a diagonal jump.
    const overlap = dir === 'up' || dir === 'down'
      ? Math.min(a.right, b.right) - Math.max(a.left, b.left)
      : Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top)
    const score = Math.max(0, main) + cross * (overlap > 0 ? 0.3 : 2)
    if (score < bestScore) { bestScore = score; best = el }
  }
  return best
}

function move(dir: Dir): boolean {
  const cur = document.activeElement instanceof HTMLElement && document.activeElement !== document.body ? document.activeElement : null
  const el = next(cur, dir)
  if (!el) return false
  el.focus({ preventScroll: true })
  el.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' })
  return true
}

function typing(el: Element | null): boolean {
  return !!el && (el.tagName === 'TEXTAREA' || (el.tagName === 'INPUT' && !['checkbox', 'radio', 'range', 'button', 'submit'].includes((el as HTMLInputElement).type)))
}

export function startTvMode(): void {
  if (!isTv()) return
  document.documentElement.classList.add('tv')
  window.addEventListener('keydown', e => {
    const k = e.keyCode
    const active = document.activeElement
    if (BACK_KEYS.has(k)) {
      if ((k === 8 || k === 27) && typing(active)) { if (k === 27) (active as HTMLElement).blur(); return }
      e.preventDefault()
      e.stopImmediatePropagation()
      // Dialogs and sheets close on Escape (Overlay listens for it).
      if (document.querySelector('.scrim')) { window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' })); return }
      if (!back() && (window as Window & { webOS?: { platformBack?: () => void } }).webOS?.platformBack) {
        (window as Window & { webOS?: { platformBack?: () => void } }).webOS!.platformBack!()
      }
      return
    }
    const dir: Dir | null = k === 37 ? 'left' : k === 38 ? 'up' : k === 39 ? 'right' : k === 40 ? 'down' : null
    if (dir) {
      // Inside a text field the left/right arrows move the caret.
      if (typing(active) && (dir === 'left' || dir === 'right')) return
      if (move(dir)) e.preventDefault()
      return
    }
    if (k === 13 && active instanceof HTMLElement && active.getAttribute('role') === 'button') { e.preventDefault(); active.click() }
  }, true)
  // Something is always focused, so the first press of a remote does something.
  const ensure = () => { if (!document.activeElement || document.activeElement === document.body) move('down') }
  new MutationObserver(() => requestAnimationFrame(ensure)).observe(document.body, { childList: true, subtree: true })
  setTimeout(ensure, 600)
  // Opening the app (or coming back home) lands on the connect control.
  const home = () => setTimeout(() => {
    if (document.querySelector('.scrim')) return
    const orb = candidates().find(el => el.classList.contains('orb'))
    if (orb) orb.focus({ preventScroll: true })
  }, 350)
  home()
  window.addEventListener('hashchange', () => { if (location.hash === '#/' || location.hash === '') home() })
}
