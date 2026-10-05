import { ComponentChildren, JSX } from 'preact'
import { useEffect, useLayoutEffect, useRef, useState } from 'preact/hooks'
import { Icon } from './Icon'

/** Slab: the container. [accent] tints the top hairline for a state. */
export function Slab(props: {
  children?: ComponentChildren
  accent?: string
  padding?: number
  spacing?: number
  onClick?: () => void
  color?: string
  class?: string
  style?: JSX.CSSProperties
}) {
  const style: Record<string, string> = {
    '--slab-pad': `${props.padding ?? 16}px`,
    '--slab-gap': `${props.spacing ?? 12}px`
  }
  if (props.accent) style['--slab-edge'] = `color-mix(in srgb, ${props.accent} 55%, transparent)`
  if (props.color) style.background = props.color
  const cls = 'slab' + (props.onClick ? ' clickable' : '') + (props.class ? ' ' + props.class : '')
  const body = <div class="slab-body">{props.children}</div>
  if (props.onClick) {
    return (
      <div class={cls} style={{ ...style, ...(props.style as object) }} role="button" tabIndex={0}
        onClick={props.onClick}
        onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); props.onClick?.() } }}>
        {body}
      </div>
    )
  }
  return <div class={cls} style={{ ...style, ...(props.style as object) }}>{body}</div>
}

export function GlyphBox(props: { icon: string; tint?: string; enabled?: boolean; size?: number; iconSize?: number; radius?: number; alphaPct?: number }) {
  const tint = props.tint ?? 'var(--primary)'
  const enabled = props.enabled ?? true
  const a = enabled ? (props.alphaPct ?? 14) : 6
  return (
    <span class="glyph-tile" style={{
      width: `${props.size ?? 38}px`, height: `${props.size ?? 38}px`, borderRadius: `${props.radius ?? 13}px`,
      background: `color-mix(in srgb, ${tint} ${a}%, transparent)`
    }}>
      <Icon name={props.icon} size={props.iconSize ?? 19} color={enabled ? tint : 'var(--on-disabled)'} />
    </span>
  )
}

/** SlabRow: the app's only list row. */
export function SlabRow(props: {
  title: ComponentChildren
  subtitle?: ComponentChildren
  icon?: string
  iconNode?: ComponentChildren
  accent?: string
  value?: ComponentChildren
  chevron?: boolean
  enabled?: boolean
  onClick?: () => void
  titleColor?: string
  trailing?: ComponentChildren
  mixed?: boolean
}) {
  const enabled = props.enabled ?? true
  const tint = props.accent ?? 'var(--primary)'
  const inner = (
    <>
      {props.iconNode ?? (props.icon ? <GlyphBox icon={props.icon} tint={tint} enabled={enabled} /> : null)}
      <span class="titles">
        <span class={'title clamp2' + (props.mixed !== false ? ' mixed-fa' : '')} style={props.titleColor && enabled ? { color: props.titleColor } : undefined}>{props.title}</span>
        {props.subtitle ? <span class="subtitle clamp2">{props.subtitle}</span> : null}
      </span>
      {props.value != null ? <span class="value" style={{ color: tint }}>{props.value}</span> : null}
      {props.trailing}
      {props.chevron ? <Icon name="keyboard_arrow_left" size={20} color="var(--muted)" mirror={false} class="chev" /> : null}
    </>
  )
  const cls = 'slab-row' + (enabled ? '' : ' disabled')
  if (props.onClick) return <button class={cls} disabled={!enabled} onClick={props.onClick}>{inner}</button>
  return <div class={cls}>{inner}</div>
}

export function SlabDivider() { return <div class="slab-divider" /> }

export function Rail(props: { label: string }) {
  return (
    <div class="rail">
      <span class="bar" />
      <span class="label ellipsis">{props.label}</span>
      <span class="rule" />
    </div>
  )
}

export function ScreenHeader(props: { title: string; context?: string; trailing?: ComponentChildren }) {
  return (
    <div class="screen-header">
      <div class="col gap-xs grow">
        <span class="tick" />
        <h1 class="clamp2">{props.title}</h1>
        {props.context ? <span class="label-medium c-text2 clamp2">{props.context}</span> : null}
      </div>
      {props.trailing}
    </div>
  )
}

export interface StatCell { label: string; value: ComponentChildren; accent?: string; sub?: string; onClick?: () => void }

export function StatStrip(props: { cells: StatCell[]; padding?: number }) {
  if (!props.cells.length) return null
  return (
    <Slab padding={props.padding ?? 12} spacing={0}>
      <div class="stat-strip">
        {props.cells.map((cell, i) => {
          const content = (
            <>
              <span class="k ellipsis" style={{ maxWidth: '100%' }}>{cell.label}</span>
              <span class="v" style={{ color: cell.accent ?? 'var(--text)', maxWidth: '100%' }}>{cell.value}</span>
              {cell.sub ? <span class="s ellipsis" style={{ maxWidth: '100%' }}>{cell.sub}</span> : null}
            </>
          )
          return (
            <>
              {i > 0 ? <span class="sep" /> : null}
              {cell.onClick
                ? <button class="cell" onClick={cell.onClick}>{content}</button>
                : <span class="cell">{content}</span>}
            </>
          )
        })}
      </div>
    </Slab>
  )
}

export function PillButton(props: {
  text: string; onClick: () => void; icon?: string; enabled?: boolean; accent?: string; minHeight?: number; fillWidth?: boolean
}) {
  const enabled = props.enabled ?? true
  const style: Record<string, string> = {}
  if (props.accent) style['--pill-tint'] = props.accent
  if (props.minHeight) style.minHeight = `${props.minHeight}px`
  return (
    <button class={'pill' + (props.fillWidth === false ? ' auto' : '')} style={style} disabled={!enabled} onClick={props.onClick}>
      {props.icon ? <Icon name={props.icon} size={19} /> : null}
      <span class="txt">{props.text}</span>
    </button>
  )
}

export function GhostPill(props: {
  text: string; onClick: () => void; icon?: string; enabled?: boolean; accent?: string; minHeight?: number; fillWidth?: boolean
}) {
  const enabled = props.enabled ?? true
  const style: Record<string, string> = {}
  if (props.accent) {
    style['--ghost-tint'] = props.accent
    style['--ghost-border'] = `color-mix(in srgb, ${props.accent} 70%, transparent)`
  }
  if (props.minHeight) style.minHeight = `${props.minHeight}px`
  return (
    <button class={'ghost' + (props.fillWidth === false ? ' auto' : '')} style={style} disabled={!enabled} onClick={props.onClick}>
      {props.icon ? <Icon name={props.icon} size={18} /> : null}
      <span class="txt">{props.text}</span>
    </button>
  )
}

export function TextButton(props: { children: ComponentChildren; onClick: () => void; enabled?: boolean; color?: string; class?: string }) {
  return (
    <button class={'text-btn' + (props.class ? ' ' + props.class : '')} disabled={props.enabled === false}
      style={props.color ? { color: props.color } : undefined} onClick={props.onClick}>
      {props.children}
    </button>
  )
}

export function SkinSwitch(props: { checked: boolean; onChange?: (v: boolean) => void; enabled?: boolean; label?: string }) {
  return (
    <button role="switch" aria-checked={props.checked} aria-label={props.label}
      class={'switch' + (props.checked ? ' on' : '')} disabled={props.enabled === false || !props.onChange}
      onClick={() => props.onChange?.(!props.checked)}>
      <span class="thumb" />
    </button>
  )
}

export function SkinField(props: {
  value: string
  onInput: (v: string) => void
  label: string
  placeholder?: string
  helper?: string
  multiline?: boolean
  minLines?: number
  enabled?: boolean
  error?: boolean
  inputMode?: 'numeric' | 'text' | 'decimal'
  type?: string
  maxLength?: number
  trailing?: ComponentChildren
  autoFocus?: boolean
  dir?: 'ltr' | 'rtl' | 'auto'
}) {
  const ref = useRef<HTMLInputElement & HTMLTextAreaElement>(null)
  useEffect(() => { if (props.autoFocus) ref.current?.focus() }, [])
  return (
    <label class={'field' + (props.error ? ' error' : '')}>
      <span class="flabel ellipsis">{props.label}</span>
      <span class="fbox">
        {props.multiline
          ? <textarea ref={ref} value={props.value} rows={props.minLines ?? 3} placeholder={props.placeholder}
              disabled={props.enabled === false} maxLength={props.maxLength} dir={props.dir ?? 'auto'}
              onInput={e => props.onInput((e.target as HTMLTextAreaElement).value)} />
          : <input ref={ref} value={props.value} placeholder={props.placeholder} type={props.type ?? 'text'}
              inputMode={props.inputMode} disabled={props.enabled === false} maxLength={props.maxLength} dir={props.dir ?? 'auto'}
              autoComplete="off" autoCorrect="off" spellcheck={false}
              onInput={e => props.onInput((e.target as HTMLInputElement).value)} />}
        {props.trailing}
      </span>
      {props.helper ? <span class="fhelp">{props.helper}</span> : null}
    </label>
  )
}

export interface RailTab { label: string; icon?: string; badge?: number | null }

export function TabRail(props: { tabs: RailTab[]; selected: number; onSelect: (i: number) => void; style?: string }) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    // Scrolls the rail only - scrollIntoView would also scroll the page sideways.
    const rail = ref.current
    const el = rail?.children[props.selected] as HTMLElement | undefined
    if (!rail || !el) return
    const r = rail.getBoundingClientRect(), e = el.getBoundingClientRect()
    if (e.left < r.left) rail.scrollBy({ left: e.left - r.left - 8, behavior: 'smooth' })
    else if (e.right > r.right) rail.scrollBy({ left: e.right - r.right + 8, behavior: 'smooth' })
  }, [props.selected])
  return (
    <div class={'tab-rail tr-' + (props.style ?? 'pill')} ref={ref} role="tablist">
      {props.tabs.map((tab, i) => (
        <button role="tab" aria-selected={i === props.selected} class={'tab' + (i === props.selected ? ' active' : '')}
          onClick={() => props.onSelect(i)}>
          {tab.icon ? <Icon name={tab.icon} size={16} /> : null}
          <span>{tab.label}</span>
          {tab.badge != null && tab.badge > 0 ? <span class="badge">{fa(tab.badge)}</span> : null}
        </button>
      ))}
    </div>
  )
}

export function SlidingSegments(props: { labels: string[]; selected: number; onSelect: (i: number) => void }) {
  const n = Math.max(1, props.labels.length)
  return (
    <div class="segments">
      <span class="indicator" style={{
        width: `calc((100% - 8px) / ${n})`,
        insetInlineStart: `calc(4px + (100% - 8px) / ${n} * ${props.selected})`
      }} />
      {props.labels.map((l, i) => (
        <button class={'seg' + (i === props.selected ? ' active' : '')} onClick={() => props.onSelect(i)}><span>{l}</span></button>
      ))}
    </div>
  )
}

export function SkinLoading(props: { label: string }) {
  return (
    <Slab spacing={12}>
      <span class="label-large c-text2">{props.label}</span>
      <div class="loading-track" />
    </Slab>
  )
}

export function SkinEmpty(props: { title: string; hint?: string; icon?: string; actionText?: string | null; onAction?: (() => void) | null }) {
  return (
    <Slab padding={24} spacing={12}>
      <div class="col gap-sm" style={{ alignItems: 'center' }}>
        {props.icon ? <span class="empty-ico"><Icon name={props.icon} size={24} /></span> : null}
        <span class="title-small bold c-text center">{props.title}</span>
        {props.hint ? <span class="label-medium c-text2 center">{props.hint}</span> : null}
      </div>
      {props.actionText && props.onAction ? <PillButton text={props.actionText} onClick={props.onAction} /> : null}
    </Slab>
  )
}

export function SkinError(props: { message: string; retryText?: string; onRetry?: () => void }) {
  return (
    <Slab accent="var(--error)" spacing={12}>
      <span class="label-large c-error wrap-any">{props.message}</span>
      {props.retryText && props.onRetry ? <GhostPill text={props.retryText} onClick={props.onRetry} accent="var(--error)" /> : null}
    </Slab>
  )
}

export function LinearProgress() { return <div class="linear-progress" role="progressbar" /> }

export function Radio(props: { selected: boolean; onClick?: () => void }) {
  return <span role="radio" aria-checked={props.selected} class={'radio' + (props.selected ? ' on' : '')} onClick={props.onClick} />
}

/** Persian digits, as localizeDigits(…, Lang.FA). */
export function fa(value: string | number): string {
  return String(value).replace(/[0-9]/g, d => '۰۱۲۳۴۵۶۷۸۹'[+d])
}

/** Measures an element's width (for sliding indicators). */
export function useWidth<T extends HTMLElement>(): [preact.RefObject<T>, number] {
  const ref = useRef<T>(null)
  const [w, setW] = useState(0)
  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    const ro = new ResizeObserver(() => setW(el.clientWidth))
    ro.observe(el)
    setW(el.clientWidth)
    return () => ro.disconnect()
  }, [])
  return [ref, w]
}
