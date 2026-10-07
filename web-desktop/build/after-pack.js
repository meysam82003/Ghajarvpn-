// Runs after electron-builder has laid out the app, before it is packed.
//
// macOS and Linux: every program in Resources/core (xray, sing-box, psiphon,
// ghajar-helper and the other sidecars that scripts/build-cores.sh builds) is
// made executable again, in case a copy step dropped the mode bit.
//
// macOS: the app is not signed with a developer certificate, so it gets an
// ad-hoc signature here. Without any signature Apple-silicon Macs report the
// app as "damaged" instead of offering to open it, and refuse to exec an
// unsigned arm64 binary at all. The cores sit in Resources, outside --deep's
// reach, so each Mach-O file there is signed on its own first.
const { execFileSync } = require('child_process')
const fs = require('fs')
const path = require('path')

// Mach-O magics: 64-bit (both byte orders), 32-bit, and universal ("fat").
const MACHO = new Set([0xfeedfacf, 0xcffaedfe, 0xfeedface, 0xcefaedfe, 0xcafebabe, 0xbebafeca])
const ELF = 0x7f454c46

function magic(file) {
  const fd = fs.openSync(file, 'r')
  try {
    const buf = Buffer.alloc(4)
    return fs.readSync(fd, buf, 0, 4, 0) === 4 ? buf.readUInt32BE(0) : 0
  } finally {
    fs.closeSync(fd)
  }
}

function filesUnder(dir) {
  if (!fs.existsSync(dir)) return []
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(e => {
    const p = path.join(dir, e.name)
    return e.isDirectory() ? filesUnder(p) : e.isFile() ? [p] : []
  })
}

exports.default = async function afterPack(context) {
  const platform = context.electronPlatformName
  if (platform !== 'darwin' && platform !== 'linux') return

  const appPath = platform === 'darwin'
    ? path.join(context.appOutDir, `${context.packager.appInfo.productFilename}.app`)
    : context.appOutDir
  const coreDir = platform === 'darwin'
    ? path.join(appPath, 'Contents', 'Resources', 'core')
    : path.join(appPath, 'resources', 'core')

  for (const file of filesUnder(coreDir)) {
    const m = magic(file)
    const isMachO = MACHO.has(m)
    if (!isMachO && m !== ELF) continue
    // Shared libraries (libcronet.so) need no exec bit, but it does no harm.
    fs.chmodSync(file, 0o755)
    if (platform === 'darwin' && isMachO) {
      execFileSync('codesign', ['--force', '--sign', '-', file], { stdio: 'inherit' })
    }
  }

  if (platform === 'darwin') {
    execFileSync('codesign', ['--force', '--deep', '--sign', '-', appPath], { stdio: 'inherit' })
  }
}
