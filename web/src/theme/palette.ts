/**
 * The app's colour system, ported value for value from GhajarDesign.kt and
 * GhajarLook.kt. Components never carry a literal colour: they read the CSS
 * variables this file publishes, exactly as the Compose screens read
 * ghajarColors.
 */

export type ThemeId = 'PREMIUM_GREEN_DARK' | 'PREMIUM_GREEN_LIGHT' | 'MIDNIGHT_BLUE' | 'GRAPHITE_GOLD' | 'SYSTEM'

export interface Palette {
  id: ThemeId
  dark: boolean
  background: string
  surface: string
  card: string
  secondaryCard: string
  primary: string
  premium: string
  highlight: string
  successGlow: string
  onPrimary: string
  textPrimary: string
  textSecondary: string
  textMuted: string
  border: string
  borderStrong: string
  error: string
  onError: string
  errorSurface: string
  warning: string
  disabled: string
  onDisabled: string
  scrim: string
  good: string
  info: string
  accentAlt: string
  neutralBar: string
}

const PremiumGreenDark: Palette = {
  id: 'PREMIUM_GREEN_DARK', dark: true,
  background: '#050807', surface: '#0B1512', card: '#101816', secondaryCard: '#14231F',
  primary: '#00A86B', premium: '#00B978', highlight: '#24D98B', successGlow: '#0CE6A0',
  onPrimary: '#02120B', textPrimary: '#E9F4EF', textSecondary: '#9DB3AA', textMuted: '#6B807A',
  border: '#1B2A26', borderStrong: '#263A34', error: '#FF6B6B', onError: '#2A0A0A',
  errorSurface: '#2B1416', warning: '#F2B23E', disabled: '#1E2C28', onDisabled: '#5B6E68',
  scrim: 'rgba(0,0,0,0.90)', good: '#2ED47A', info: '#35E0FF', accentAlt: '#B86BFF', neutralBar: '#8A94A6'
}

const PremiumGreenLight: Palette = {
  id: 'PREMIUM_GREEN_LIGHT', dark: false,
  background: '#F3F8F6', surface: '#FFFFFF', card: '#FFFFFF', secondaryCard: '#EAF3EF',
  primary: '#00794E', premium: '#00925E', highlight: '#00A86B', successGlow: '#00B978',
  onPrimary: '#FFFFFF', textPrimary: '#07130F', textSecondary: '#44564F', textMuted: '#6C7E77',
  border: '#DBE7E2', borderStrong: '#C3D5CE', error: '#B3261E', onError: '#FFFFFF',
  errorSurface: '#FCE9E7', warning: '#8A5A00', disabled: '#E2EAE7', onDisabled: '#93A29C',
  scrim: 'rgba(0,0,0,0.60)', good: '#1B7F4B', info: '#0A7C99', accentAlt: '#7A3BD6', neutralBar: '#6B7484'
}

const MidnightBlue: Palette = {
  id: 'MIDNIGHT_BLUE', dark: true,
  background: '#04070F', surface: '#0A111F', card: '#0F1929', secondaryCard: '#152238',
  primary: '#3D7BFF', premium: '#5A8CFF', highlight: '#82AAFF', successGlow: '#4DD6FF',
  onPrimary: '#03081A', textPrimary: '#E7ECF7', textSecondary: '#9BABC7', textMuted: '#6B7B94',
  border: '#1A2540', borderStrong: '#243352', error: '#FF7A85', onError: '#2A0A0E',
  errorSurface: '#2A1420', warning: '#E8B44A', disabled: '#1A2335', onDisabled: '#5D6C86',
  scrim: 'rgba(0,0,0,0.90)', good: '#3ED9A3', info: '#4DD6FF', accentAlt: '#9B8CFF', neutralBar: '#6B7B94'
}

const GraphiteGold: Palette = {
  id: 'GRAPHITE_GOLD', dark: true,
  background: '#07070A', surface: '#111114', card: '#17171B', secondaryCard: '#1F1E24',
  primary: '#D8B15C', premium: '#E4C173', highlight: '#F0D394', successGlow: '#7FD6A8',
  onPrimary: '#130F06', textPrimary: '#F1EFEA', textSecondary: '#AEA89C', textMuted: '#7B766C',
  border: '#26242B', borderStrong: '#34313A', error: '#FF7A7A', onError: '#2A0A0A',
  errorSurface: '#2A1614', warning: '#E0A94B', disabled: '#1D1C21', onDisabled: '#6E6961',
  scrim: 'rgba(0,0,0,0.90)', good: '#7FD6A8', info: '#6FD3E8', accentAlt: '#C99CF0', neutralBar: '#7B766C'
}

export const Palettes: Palette[] = [PremiumGreenDark, PremiumGreenLight, MidnightBlue, GraphiteGold]

export const ThemeNames: Record<ThemeId, string> = {
  PREMIUM_GREEN_DARK: 'سبز ممتاز تیره',
  PREMIUM_GREEN_LIGHT: 'سبز ممتاز روشن',
  MIDNIGHT_BLUE: 'آبی نیمه‌شب',
  GRAPHITE_GOLD: 'گرافیت طلایی',
  SYSTEM: 'هماهنگ با سیستم'
}

export function paletteFor(theme: ThemeId, systemDark: boolean): Palette {
  switch (theme) {
    case 'PREMIUM_GREEN_LIGHT': return PremiumGreenLight
    case 'MIDNIGHT_BLUE': return MidnightBlue
    case 'GRAPHITE_GOLD': return GraphiteGold
    case 'SYSTEM': return systemDark ? PremiumGreenDark : PremiumGreenLight
    default: return PremiumGreenDark
  }
}

/** A QR is read by a camera, so it keeps a fixed pair (GhajarFixed). */
export const QrBackground = '#0B0F14'
export const QrForeground = '#F7FAFC'

// ---------------------------------------------------------------- colour math

interface Rgb { r: number; g: number; b: number }

export function hexToRgb(hex: string): Rgb {
  const h = hex.replace('#', '')
  const full = h.length === 3 ? h.split('').map(c => c + c).join('') : h.slice(-6)
  const n = parseInt(full, 16)
  return { r: ((n >> 16) & 255) / 255, g: ((n >> 8) & 255) / 255, b: (n & 255) / 255 }
}

export function rgbToHex(c: Rgb): string {
  const to = (v: number) => Math.round(Math.max(0, Math.min(1, v)) * 255).toString(16).padStart(2, '0')
  return '#' + to(c.r) + to(c.g) + to(c.b)
}

export function mix(a: string, b: string, t: number): string {
  const x = hexToRgb(a), y = hexToRgb(b)
  return rgbToHex({ r: x.r + (y.r - x.r) * t, g: x.g + (y.g - x.g) * t, b: x.b + (y.b - x.b) * t })
}

/** Compose's Color.luminance(): relative luminance with the sRGB transfer. */
export function luminance(hex: string): number {
  const c = hexToRgb(hex)
  const lin = (v: number) => (v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4))
  return 0.2126 * lin(c.r) + 0.7152 * lin(c.g) + 0.0722 * lin(c.b)
}

export function contrastRatio(a: string, b: string): number {
  const la = luminance(a) + 0.05, lb = luminance(b) + 0.05
  return la > lb ? la / lb : lb / la
}

export function alpha(hex: string, a: number): string {
  const c = hexToRgb(hex)
  return `rgba(${Math.round(c.r * 255)},${Math.round(c.g * 255)},${Math.round(c.b * 255)},${a})`
}

/** The accent's three tones and a readable ink on top of it (withAccent). */
export function withAccent(p: Palette, a: string): Palette {
  const light = !p.dark
  return {
    ...p,
    primary: a,
    premium: light ? mix(a, '#000000', 0.12) : mix(a, '#FFFFFF', 0.10),
    highlight: light ? a : mix(a, '#FFFFFF', 0.25),
    successGlow: light ? a : mix(a, '#FFFFFF', 0.18),
    onPrimary: luminance(a) > 0.45 ? '#07100C' : '#FFFFFF'
  }
}

export function amoled(p: Palette): Palette {
  if (!p.dark) return p
  return {
    ...p,
    background: '#000000', surface: '#050505', card: '#0A0A0A', secondaryCard: '#111111',
    border: '#1C1C1C', borderStrong: '#262626', disabled: '#161616'
  }
}
