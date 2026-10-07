// Packs app/ into an LG webOS TV installer with LG's own packager
// (ares-package from @webos-tools/cli).  npm ci && node build.mjs
//   → dist/GhajarVPN-webos.ipk
// Install it on the TV with the Developer Mode app + `ares-install`, or with
// the Homebrew Channel on rooted TVs.
import { execFileSync } from 'node:child_process'
import { mkdirSync, readdirSync, renameSync, rmSync, statSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const out = join(here, 'dist')
rmSync(out, { recursive: true, force: true })
mkdirSync(out, { recursive: true })
const ares = join(here, 'node_modules', '.bin', process.platform === 'win32' ? 'ares-package.cmd' : 'ares-package')
execFileSync(ares, [join(here, 'app'), '-o', out], { stdio: 'inherit' })
const built = readdirSync(out).find(f => f.endsWith('.ipk'))
const ipk = join(out, 'GhajarVPN-webos.ipk')
renameSync(join(out, built), ipk)
console.log('webOS package →', ipk, Math.round(statSync(ipk).size / 1024) + ' KB')
