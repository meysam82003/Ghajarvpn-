// Downloads the Xray core for a desktop build into core/<os>-<arch>/.
//   node scripts/fetch-core.mjs win-x64 | mac-x64 | mac-arm64 | linux-x64 ...
import { mkdirSync, rmSync, writeFileSync, chmodSync, existsSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const ASSETS = { 'win-x64': 'Xray-windows-64.zip', 'mac-x64': 'Xray-macos-64.zip', 'mac-arm64': 'Xray-macos-arm64-v8a.zip', 'linux-x64': 'Xray-linux-64.zip' }
const VERSION = process.env.XRAY_VERSION || 'latest'
for (const target of process.argv.slice(2)) {
  const asset = ASSETS[target]
  if (!asset) throw new Error('unknown target ' + target)
  const url = VERSION === 'latest'
    ? `https://github.com/XTLS/Xray-core/releases/latest/download/${asset}`
    : `https://github.com/XTLS/Xray-core/releases/download/${VERSION}/${asset}`
  const out = join(here, '..', 'core', target)
  rmSync(out, { recursive: true, force: true })
  mkdirSync(out, { recursive: true })
  const res = await fetch(url, { redirect: 'follow' })
  if (!res.ok) throw new Error(`${url}: ${res.status}`)
  const zip = join(out, asset)
  writeFileSync(zip, Buffer.from(await res.arrayBuffer()))
  execFileSync(process.platform === 'win32' ? 'tar' : 'unzip', process.platform === 'win32' ? ['-xf', zip, '-C', out] : ['-o', '-q', zip, '-d', out])
  rmSync(zip)
  const bin = join(out, target.startsWith('win') ? 'xray.exe' : 'xray')
  if (!existsSync(bin)) throw new Error('xray missing in ' + asset)
  if (!target.startsWith('win')) chmodSync(bin, 0o755)
  console.log('core', target, '→', out)
}
