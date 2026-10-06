// macOS: the app is not signed with a developer certificate, so it gets an
// ad-hoc signature here. Without any signature Apple-silicon Macs report the
// app as "damaged" instead of offering to open it.
const { execFileSync } = require('child_process')
const path = require('path')

exports.default = async function afterPack(context) {
  if (context.electronPlatformName !== 'darwin') return
  const appPath = path.join(context.appOutDir, `${context.packager.appInfo.productFilename}.app`)
  // The bundled Xray core sits in Resources, outside --deep's reach: signed on its own first.
  const xray = path.join(appPath, 'Contents', 'Resources', 'core', 'xray')
  if (require('fs').existsSync(xray)) execFileSync('codesign', ['--force', '--sign', '-', xray], { stdio: 'inherit' })
  execFileSync('codesign', ['--force', '--deep', '--sign', '-', appPath], { stdio: 'inherit' })
}
