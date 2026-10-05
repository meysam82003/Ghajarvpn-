import qrcode from 'qrcode-generator'

/**
 * A QR code as an SVG path, in the fixed scannable pair (GhajarFixed): it is
 * read by a camera, so it never follows the theme. Returns null when the text
 * does not fit in a QR code at all.
 */
export function qrSvg(text: string, dark = '#0B0F14', light = '#F7FAFC'): string | null {
  try {
    const qr = qrcode(0, 'M')
    qr.addData(unescape(encodeURIComponent(text)), 'Byte')
    qr.make()
    const n = qr.getModuleCount()
    const margin = 4
    const size = n + margin * 2
    let d = ''
    for (let y = 0; y < n; y++) {
      for (let x = 0; x < n; x++) {
        if (qr.isDark(y, x)) d += `M${x + margin},${y + margin}h1v1h-1z`
      }
    }
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}" shape-rendering="crispEdges"><rect width="${size}" height="${size}" fill="${light}"/><path d="${d}" fill="${dark}"/></svg>`
  } catch {
    return null
  }
}
