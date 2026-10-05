import { createStore, safeStorage } from '../lib/store'
import { Palette, ThemeId, amoled, mix, paletteFor, withAccent, alpha } from './palette'

/**
 * Everything the Personalization page controls, ported from GhajarLook.kt.
 * Only the options that mean something without a VPN engine are kept; the
 * values, ranges and defaults are the app's own.
 */
export interface Look {
  theme: ThemeId
  preset: string
  accent: string | null
  amoled: boolean
  transition: string
  speed: string
  navStyle: string
  navIconSize: number
  navLabels: boolean
  navRadius: number
  navIndicator: string
  orbStyle: string
  density: string
  cardRadius: number
  elevation: number
  fontScale: number
  boldTitles: boolean
  storeTabStyle: string
  reduceMotion: boolean
}

export const DefaultLook: Look = {
  theme: 'PREMIUM_GREEN_DARK',
  preset: 'default',
  accent: null,
  amoled: false,
  transition: 'default',
  speed: 'normal',
  navStyle: 'floating',
  navIconSize: 23,
  navLabels: true,
  navRadius: 28,
  navIndicator: 'rounded',
  orbStyle: 'circle',
  density: 'comfortable',
  cardRadius: 24,
  elevation: 0,
  fontScale: 1,
  boldTitles: true,
  storeTabStyle: 'pill',
  reduceMotion: false
}

export interface LookPreset {
  key: string; fa: string; accent: string; bg: string; surface: string; card: string; secondaryCard: string; border: string
}

/** LookPreset, value for value. */
export const LookPresets: LookPreset[] = [
  { key: 'default', fa: 'پیش‌فرض', accent: '#00A86B', bg: '#050807', surface: '#0B1512', card: '#101816', secondaryCard: '#14231F', border: '#1B2A26' },
  { key: 'amoled', fa: 'AMOLED مشکی', accent: '#24D98B', bg: '#000000', surface: '#050505', card: '#0A0A0A', secondaryCard: '#111111', border: '#1C1C1C' },
  { key: 'emerald', fa: 'زمرد / فیروزه‌ای', accent: '#14B8A6', bg: '#03100F', surface: '#071A18', card: '#0B211F', secondaryCard: '#10302C', border: '#173A36' },
  { key: 'ocean', fa: 'آبی اقیانوسی', accent: '#3D7BFF', bg: '#04070F', surface: '#0A111F', card: '#0F1929', secondaryCard: '#152238', border: '#1A2540' },
  { key: 'purple', fa: 'شب بنفش', accent: '#A855F7', bg: '#08050F', surface: '#110A1D', card: '#170F27', secondaryCard: '#1F1533', border: '#2A1D42' },
  { key: 'rose', fa: 'صورتی رز', accent: '#F43F72', bg: '#0F0508', surface: '#1A0A10', card: '#220E16', secondaryCard: '#2C131D', border: '#3A1A27' },
  { key: 'sunset', fa: 'نارنجی غروب', accent: '#F97316', bg: '#0F0804', surface: '#1A0F07', card: '#22140A', secondaryCard: '#2D1B0E', border: '#3A2414' },
  { key: 'forest', fa: 'سبز جنگلی', accent: '#4CAF50', bg: '#050A05', surface: '#0B150B', card: '#101C10', secondaryCard: '#162616', border: '#1F331F' },
  { key: 'gold', fa: 'گرافیت طلایی', accent: '#D8B15C', bg: '#07070A', surface: '#111114', card: '#17171B', secondaryCard: '#1F1E24', border: '#26242B' },
  { key: 'crimson', fa: 'قرمز یاقوتی', accent: '#EF4444', bg: '#0D0505', surface: '#180909', card: '#200D0D', secondaryCard: '#2A1212', border: '#381919' },
  { key: 'cyber', fa: 'سایبر نئون', accent: '#22D3EE', bg: '#03060A', surface: '#070E15', card: '#0B141D', secondaryCard: '#101C28', border: '#172636' },
  { key: 'mono', fa: 'خاکستری مینیمال', accent: '#E5E7EB', bg: '#070707', surface: '#101010', card: '#161616', secondaryCard: '#1E1E1E', border: '#2A2A2A' },
  { key: 'lavender', fa: 'یاسی', accent: '#A5B4FC', bg: '#06060E', surface: '#0D0D1A', card: '#131324', secondaryCard: '#1A1A30', border: '#24243E' },
  { key: 'minimal', fa: 'مینیمال', accent: '#B8C4CC', bg: '#0B0C0D', surface: '#121315', card: '#17181A', secondaryCard: '#1C1E20', border: '#26282B' },
  { key: 'high_contrast', fa: 'کنتراست بالا', accent: '#FFE600', bg: '#000000', surface: '#000000', card: '#0A0A0A', secondaryCard: '#141414', border: '#FFFFFF' }
]

export const LookTransitions: [string, string][] = [
  ['default', 'پیش‌فرض'], ['none', 'بدون انیمیشن'], ['fade', 'محو شدن'], ['slide_h', 'لغزش افقی'],
  ['slide_v', 'لغزش عمودی'], ['scale', 'بزرگ‌نمایی'], ['axis_x', 'محور افقی'], ['axis_y', 'محور عمودی'],
  ['axis_z', 'محور عمق'], ['fade_through', 'محو عبوری']
]
export const LookNavStyles: [string, string][] = [
  ['floating', 'شناور'], ['standard', 'استاندارد'], ['minimal', 'مینیمال'], ['filled', 'پر'], ['outline', 'خطی']
]
export const LookOrbStyles: [string, string][] = [
  ['circle', 'دایره'], ['pill', 'قرصی'], ['capsule_glow', 'کپسول درخشان'], ['soft_square', 'مربع نرم'],
  ['double_ring', 'دو حلقه'], ['neon', 'نئون'], ['minimal', 'مینیمال'], ['segmented', 'بخش‌بندی'],
  ['shield', 'سپر'], ['power', 'دکمهٔ پاور']
]

function presetByKey(key: string): LookPreset {
  return LookPresets.find(p => p.key === key) ?? LookPresets[0]
}

/** GhajarLook.apply: the base theme with the look laid over it. */
export function resolvePalette(look: Look, systemDark: boolean): Palette {
  const base = paletteFor(look.theme, systemDark)
  const p = presetByKey(look.preset)
  let out: Palette
  if (p.key === 'default' || p.key === 'dynamic') out = base
  else if (!base.dark) out = withAccent(base, p.accent)
  else out = withAccent({
    ...base,
    background: p.bg, surface: p.surface, card: p.card, secondaryCard: p.secondaryCard,
    border: p.border, borderStrong: mix(p.border, '#FFFFFF', 0.06), disabled: p.secondaryCard, textMuted: '#7A807E'
  }, p.accent)
  if (p.key === 'high_contrast') out = { ...out, textPrimary: '#FFFFFF', textSecondary: '#E6E6E6', textMuted: '#BDBDBD', borderStrong: '#FFFFFF' }
  if (look.amoled || p.key === 'amoled') out = amoled(out)
  if (look.accent) out = withAccent(out, look.accent)
  return out
}

const KEY = 'ghajar.look.v1'

function load(): Look {
  const stored = safeStorage.json<Partial<Look>>(KEY, {})
  const look = { ...DefaultLook, ...stored }
  look.navIconSize = clamp(look.navIconSize, 18, 30)
  look.navRadius = clamp(look.navRadius, 0, 40)
  look.cardRadius = clamp(look.cardRadius, 4, 36)
  look.elevation = clamp(look.elevation, 0, 12)
  look.fontScale = clamp(look.fontScale, 0.85, 1.25)
  return look
}

function clamp(v: number, lo: number, hi: number): number {
  return Number.isFinite(v) ? Math.min(hi, Math.max(lo, v)) : lo
}

export const lookStore = createStore<Look>(load())

export function setLook(update: Partial<Look>): void {
  const next = { ...lookStore.get(), ...update }
  lookStore.set(next)
  safeStorage.set(KEY, JSON.stringify(next))
}

export function resetLook(): void {
  lookStore.set({ ...DefaultLook })
  safeStorage.remove(KEY)
}

export const paletteStore = createStore<Palette>(resolvePalette(lookStore.get(), systemDark()))

function systemDark(): boolean {
  try { return window.matchMedia('(prefers-color-scheme: dark)').matches } catch { return true }
}

/**
 * Publishes the palette as CSS variables on :root. Every component reads these
 * names; nothing else in the stylesheet carries a literal colour.
 */
export function applyTheme(): void {
  const look = lookStore.get()
  const p = resolvePalette(look, systemDark())
  paletteStore.set(p)
  const root = document.documentElement
  const vars: Record<string, string> = {
    '--bg': p.background, '--surface': p.surface, '--card': p.card, '--card2': p.secondaryCard,
    '--primary': p.primary, '--premium': p.premium, '--highlight': p.highlight, '--glow': p.successGlow,
    '--on-primary': p.onPrimary, '--text': p.textPrimary, '--text2': p.textSecondary, '--muted': p.textMuted,
    '--border': p.border, '--border-strong': p.borderStrong, '--error': p.error, '--on-error': p.onError,
    '--error-surface': p.errorSurface, '--warning': p.warning, '--disabled': p.disabled, '--on-disabled': p.onDisabled,
    '--scrim': p.scrim, '--good': p.good, '--info': p.info, '--accent-alt': p.accentAlt,
    '--primary-14': alpha(p.primary, 0.14), '--primary-12': alpha(p.primary, 0.12), '--primary-20': alpha(p.primary, 0.20),
    '--primary-55': alpha(p.primary, 0.55), '--primary-70': alpha(p.primary, 0.70),
    '--warning-18': alpha(p.warning, 0.18), '--error-40': alpha(p.error, 0.40), '--primary-40': alpha(p.primary, 0.40),
    '--on-primary-22': alpha(p.onPrimary, 0.22),
    '--wash-top': mix(p.background, p.primary, 0.12), '--wash-mid': mix(p.background, p.primary, 0.05),
    '--card-radius': `${look.cardRadius}px`,
    '--nav-radius': `${look.navRadius}px`,
    '--nav-icon': `${look.navIconSize}px`,
    '--font-scale': String(look.fontScale),
    '--title-weight': look.boldTitles ? '600' : '400',
    '--tile-title-weight': look.boldTitles ? '700' : '500',
    '--elev': look.elevation > 0 ? `0 ${look.elevation / 2}px ${look.elevation * 1.5}px rgba(0,0,0,${p.dark ? 0.45 : 0.18})` : 'none',
    '--gap': look.density === 'compact' ? '8px' : look.density === 'spacious' ? '24px' : '12px'
  }
  for (const [k, v] of Object.entries(vars)) root.style.setProperty(k, v)
  root.dataset.theme = p.dark ? 'dark' : 'light'
  root.dataset.reduceMotion = look.reduceMotion ? 'on' : 'off'
  root.style.colorScheme = p.dark ? 'dark' : 'light'
  // The status bar and the browser chrome follow the canvas tone.
  document.querySelectorAll('meta[name="theme-color"]').forEach(m => m.setAttribute('content', p.background))
}

lookStore.subscribe(applyTheme)
try {
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', applyTheme)
} catch { /* old browsers */ }
