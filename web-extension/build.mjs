// Builds the Chrome (MV3) and Firefox (MV3) packages from src/:
//   dist/GhajarVPN-chrome-extension.zip, dist/GhajarVPN-firefox-extension.xpi
import { cpSync, mkdirSync, rmSync, writeFileSync, readFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const { version } = JSON.parse(readFileSync(join(here, 'package.json'), 'utf8'))
const base = {
  manifest_version: 3,
  name: 'قاجار وی پی ان',
  short_name: 'Ghajar VPN',
  description: 'اتصال فقط مرورگر به VPN قاجار، با هستهٔ برنامهٔ دسکتاپ قاجار — بقیهٔ سیستم دست نمی‌خورد.',
  version,
  default_locale: undefined,
  icons: { 16: 'icons/icon-16.png', 32: 'icons/icon-32.png', 48: 'icons/icon-48.png', 128: 'icons/icon-128.png' },
  action: { default_popup: 'popup.html', default_title: 'قاجار وی پی ان', default_icon: { 16: 'icons/icon-off-16.png', 32: 'icons/icon-off-32.png' } },
  permissions: ['proxy', 'storage', 'alarms', 'privacy']
}
const chrome = { ...base, background: { service_worker: 'background.js' }, host_permissions: ['http://127.0.0.1/*'], minimum_chrome_version: '110' }
const firefox = {
  ...base,
  background: { scripts: ['background.js'] },
  host_permissions: ['<all_urls>'],
  browser_specific_settings: { gecko: { id: 'ghajar-vpn@ghajarvpn.app', strict_min_version: '128.0', data_collection_permissions: { required: ['none'] } } }
}
const dist = join(here, 'dist')
rmSync(dist, { recursive: true, force: true })
for (const [name, manifest, file] of [['chrome', chrome, 'GhajarVPN-chrome-extension.zip'], ['firefox', firefox, 'GhajarVPN-firefox-extension.xpi']]) {
  const out = join(dist, name)
  mkdirSync(out, { recursive: true })
  cpSync(join(here, 'src'), out, { recursive: true })
  writeFileSync(join(out, 'manifest.json'), JSON.stringify(manifest, (k, v) => (v === undefined ? undefined : v), 2))
  execFileSync('zip', ['-qr', join(dist, file), '.'], { cwd: out })
  console.log(join(dist, file))
}
