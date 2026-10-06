import { useMemo, useState } from 'preact/hooks'
import { Sheet } from './Overlay'
import { Icon } from './Icon'
import { GhostPill, PillButton, TextButton, LinearProgress, Rail } from './Skin'
import { AppLinks, appsFor, launchUrl, openScheme, AppLink, Sub } from '../lib/applinks'
import { detectPlatform, PlatformNames, nativeBridge } from '../lib/platform'
import { qrSvg } from '../lib/qr'
import { copyText, toast } from '../state/ui'
import { fa, ltr } from '../lib/format'

/**
 * GhajarDeliveryDialog, for a device without the app's own engines: the
 * service's QR (fixed scannable colours), every VPN app on this device that
 * can take it in one tap, copy, share and file download.
 */
export function AddToAppSheet(props: {
  title?: string
  productName: string
  username: string
  subscriptionUrl: string | null
  configs: string[]
  busy?: boolean
  failed?: boolean
  synced?: boolean
  onRetry?: () => void
  onDismiss: () => void
}) {
  const platform = useMemo(detectPlatform, [])
  const payloads = useMemo(() => [...new Set([props.subscriptionUrl, ...props.configs].filter((s): s is string => !!s && !!s.trim()))], [props.subscriptionUrl, props.configs])
  const [index, setIndex] = useState(0)
  const [showAll, setShowAll] = useState(false)
  const [missing, setMissing] = useState<AppLink | null>(null)
  const payload = payloads[index] ?? null
  const qr = useMemo(() => (payload ? qrSvg(payload) : null), [payload])
  const sub: Sub = { url: props.subscriptionUrl, name: props.productName || 'Ghajar VPN', configs: props.configs.filter(c => /^[a-z0-9+.-]+:\/\//i.test(c.trim())) }
  const local = appsFor(platform)
  const others = AppLinks.filter(a => !local.includes(a))
  const files = props.configs.map(fileFor).filter((f): f is FileOut => f != null)

  async function open(app: AppLink) {
    const url = launchUrl(app, sub, platform)
    if (!url) return
    const took = await openScheme(url)
    if (!took && !url.startsWith('intent://')) setMissing(app)
  }

  async function share() {
    if (!payload) return
    try {
      const native = nativeBridge()
      if (native?.share) native.share(props.productName, payload)
      else if (navigator.share) await navigator.share({ title: props.productName, text: payload })
      else await copyText(payload, 'لینک')
    } catch { /* cancelled */ }
  }

  const appButton = (app: AppLink) => {
    const can = !!app.build(sub)
    return (
      <button class="glyph-action" disabled={!can} onClick={() => open(app)} style={{ opacity: can ? 1 : 0.45 }}>
        <span class="glyph-tile" style={{ background: 'color-mix(in srgb, var(--primary) 16%, transparent)' }}>
          <Icon name={app.id.startsWith('ghajar') ? 'verified' : 'add_to_home_screen'} size={19} color="var(--primary)" />
        </span>
        <span class="label-medium c-text clamp2" style={{ fontWeight: 500 }}>{app.name}</span>
      </button>
    )
  }

  return (
    <Sheet onDismiss={props.onDismiss}>
      <div class="col gap-sm" style={{ alignItems: 'center', textAlign: 'center' }}>
        <span class="title-large c-text">{props.title ?? (props.failed ? 'همگام‌سازی ناموفق بود' : 'افزودن سرویس به اپ')}</span>
        <span class="bold c-text">{props.productName}</span>
        {props.username ? <span class="body-small c-text2">{ltr(props.username)}</span> : null}
      </div>
      {props.busy ? <LinearProgress /> : null}
      {payload ? (
        qr
          ? <div class="qr-box" role="img" aria-label="QR اتصال همین سرویس" dangerouslySetInnerHTML={{ __html: qr }} />
          : <span class="body-small center">این خروجی در QR جا نمی‌شود؛ از کپی لینک استفاده کن.</span>
      ) : !props.busy ? <span class="body-small center">خروجی اتصال هنوز در دسترس نیست.</span> : null}
      {payloads.length > 1 ? (
        <div class="row" style={{ justifyContent: 'center' }}>
          <TextButton enabled={index > 0} onClick={() => setIndex(index - 1)}>قبلی</TextButton>
          <span>{fa(`${index + 1} / ${payloads.length}`)}</span>
          <TextButton enabled={index < payloads.length - 1} onClick={() => setIndex(index + 1)}>بعدی</TextButton>
        </div>
      ) : null}
      {payload ? (
        <div class="row gap-sm">
          <PillButton text="کپی لینک" icon="content_copy" onClick={() => copyText(payload, 'لینک')} />
          <GhostPill text="اشتراک‌گذاری" icon="share" onClick={share} />
        </div>
      ) : null}

      <Rail label={`افزودن با یک لمس · ${PlatformNames[platform]}`} />
      {local.length ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(104px, 1fr))', gap: '8px' }}>
          {local.map(appButton)}
        </div>
      ) : <span class="body-small c-text2">برای این دستگاه اپ مشخصی نداریم؛ از «همهٔ اپ‌ها» یا کپی لینک استفاده کن.</span>}
      <TextButton onClick={() => setShowAll(!showAll)}>{showAll ? 'بستن فهرست اپ‌های دیگر' : 'همهٔ اپ‌ها'}</TextButton>
      {showAll ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(104px, 1fr))', gap: '8px' }}>
          {others.map(appButton)}
        </div>
      ) : null}

      {files.length ? <>
        <Rail label="فایل کانفیگ" />
        {files.map(f => (
          <GhostPill text={`دریافت ${f.label}`} icon="file_download" onClick={() => download(f)} />
        ))}
      </> : null}

      <span class="body-small center">
        {props.synced ? 'لینک و کانفیگ‌ها آماده‌اند؛ اپ دلخواهت را بزن تا مستقیم اضافه شود.'
          : props.failed ? 'تلاش قبلی ناموفق بود؛ اتصال یا سرور را بررسی کن و «دریافت دوباره» را بزن. خرید دوباره لازم نیست.'
            : 'اگر خروجی کامل نشد، دریافت دوباره را بزن؛ خرید دوباره لازم نیست.'}
      </span>
      <span class="label-small center c-text2">QR و لینک خصوصی‌اند؛ فقط با فرد مورد اعتماد به اشتراک بگذار.</span>
      <div class="row gap-sm" style={{ justifyContent: 'flex-end', flexWrap: 'wrap' }}>
        {!props.synced && props.onRetry ? <TextButton enabled={!props.busy} onClick={props.onRetry}>دریافت دوباره</TextButton> : null}
        <TextButton onClick={props.onDismiss}>متوجه شدم</TextButton>
      </div>

      {missing ? (
        <div class="mcard" style={{ background: 'var(--card2)' }}>
          <div class="col gap-sm" style={{ padding: '14px' }}>
            <span class="bold c-text">{missing.name} روی این دستگاه باز نشد</span>
            <span class="body-small c-text2">اگر نصب نیست، اول نصبش کن و دوباره همین دکمه را بزن.</span>
            {missing.store[platform] ? <PillButton text={`نصب ${missing.name}`} icon="download" onClick={() => { location.href = missing.store[platform]! }} /> : null}
            <TextButton onClick={() => setMissing(null)}>بستن</TextButton>
          </div>
        </div>
      ) : null}
    </Sheet>
  )
}

interface FileOut { label: string; name: string; mime: string; text: string }

function fileFor(text: string): FileOut | null {
  const t = text.trim()
  if (/^\[Interface\]/im.test(t) && /\[Peer\]/i.test(t)) return { label: 'فایل WireGuard (.conf)', name: 'ghajar-wireguard.conf', mime: 'text/plain', text: t }
  if (/^\s*(client|remote)\b/im.test(t) && /<ca>|remote\s+\S+/i.test(t)) return { label: 'فایل OpenVPN (.ovpn)', name: 'ghajar.ovpn', mime: 'application/x-openvpn-profile', text: t }
  return null
}

function download(f: FileOut) {
  const native = nativeBridge()
  if (native?.saveFile) {
    try { native.saveFile(f.name, f.mime, f.text); toast(`${f.name} در پوشهٔ Download ذخیره شد`) } catch { toast('دریافت فایل انجام نشد؛ متن کانفیگ را کپی کن.') }
    return
  }
  try {
    const blob = new Blob([f.text], { type: f.mime })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = f.name; a.rel = 'noopener'
    document.body.appendChild(a); a.click(); a.remove()
    setTimeout(() => URL.revokeObjectURL(url), 30_000)
  } catch {
    toast('دریافت فایل انجام نشد؛ متن کانفیگ را کپی کن.')
  }
}
