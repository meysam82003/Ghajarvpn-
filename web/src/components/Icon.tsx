import { JSX } from 'preact'
import { MaterialIcons } from './icons.generated'

/**
 * Material "Filled" icons, the same set the Compose screens use, plus the
 * app's own royal vector drawables (ic_royal_home/shop/settings,
 * ic_stat_ghajar) converted path for path from res/drawable.
 */

/** Icons that are AutoMirrored in Compose: they flip under RTL. */
const MIRRORED = new Set(['arrow_back', 'arrow_forward', 'keyboard_arrow_left', 'keyboard_arrow_right', 'chevron_left', 'chevron_right', 'open_in_new', 'send', 'logout'])

export function Icon(props: { name: string; size?: number; color?: string; class?: string; title?: string; mirror?: boolean }): JSX.Element {
  const d = MaterialIcons[props.name] ?? ''
  const size = props.size ?? 24
  const mirror = props.mirror ?? MIRRORED.has(props.name)
  return (
    <svg
      class={'ic' + (mirror ? ' ic-mirror' : '') + (props.class ? ' ' + props.class : '')}
      width={size} height={size} viewBox="0 0 24 24"
      fill={props.color ?? 'currentColor'}
      aria-hidden={props.title ? undefined : 'true'}
      role={props.title ? 'img' : undefined}
    >
      {props.title ? <title>{props.title}</title> : null}
      <path d={d} />
    </svg>
  )
}

const ROYAL: Record<string, { stroke: string; fill?: string }> = {
  home: { stroke: 'M3,25H25M5,25V14H23V25M9,14V9Q9,6 14,3Q19,6 19,9V14M14,3V1M11,25V20Q14,16 17,20V25M5,14V10M23,14V10M8,18V20M20,18V20' },
  shop: { stroke: 'M9,10L7,4L11,6L14,2L17,6L21,4L19,10ZM4,14H24L21,25H7ZM9,14L11,10M19,14L17,10M11,18V21M17,18V21' },
  settings: {
    stroke: 'M4,7H24M4,14H24M4,21H24M9,4V10M19,11V17M11,18V24',
    fill: 'M9,3L12,7L9,11L6,7ZM19,10L22,14L19,18L16,14ZM11,17L14,21L11,25L8,21Z'
  }
}

/** The bottom-bar glyphs: line art at 1.7 stroke in a 28-unit box, tinted like Icon(painterResource). */
export function RoyalIcon(props: { name: 'home' | 'shop' | 'settings'; size?: number; color?: string }): JSX.Element {
  const r = ROYAL[props.name]
  const size = props.size ?? 23
  const color = props.color ?? 'currentColor'
  return (
    <svg width={size} height={size} viewBox="0 0 28 28" aria-hidden="true">
      <path d={r.stroke} fill="none" stroke={color} stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
      {r.fill ? <path d={r.fill} fill={color} /> : null}
    </svg>
  )
}

/** ic_stat_ghajar: the monochrome crown-and-shield mark. */
export function GhajarMark(props: { size?: number; color?: string }): JSX.Element {
  const size = props.size ?? 24
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill={props.color ?? 'currentColor'} aria-hidden="true">
      <path d="M6,2L9,4L12,1L15,4L18,2L17,7H7Z" />
      <path fill-rule="evenodd" d="M4,8H20V13C20,18 16,21 12,23C8,21 4,18 4,13ZM6,10V13C6,17 9,19.5 12,20.8C15,19.5 18,17 18,13V10Z" />
      <path fill-rule="evenodd" d="M9,14V12.8C9,9.2 15,9.2 15,12.8V14H16V18H8V14ZM10.5,14H13.5V12.8C13.5,11 10.5,11 10.5,12.8Z" />
    </svg>
  )
}
