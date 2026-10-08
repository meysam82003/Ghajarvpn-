// Runs the full-device tunnel (sing-box with a TUN inbound) with the
// administrator rights a TUN needs, without leaving a root process behind.
//
// The privileged side is a small watchdog script: it starts sing-box, then
// stops it as soon as the app writes a stop file or the app itself exits.
// So the user approves once per connection (UAC on Windows, the password
// sheet on macOS, polkit on Linux) and disconnecting needs no second prompt.
const { spawn } = require('child_process')
const fs = require('fs')
const path = require('path')

function write(file, text, mode) {
  fs.writeFileSync(file, text, { mode })
  return file
}

function shQuote(s) { return `'${String(s).replace(/'/g, `'\\''`)}'` }
function psQuote(s) { return `'${String(s).replace(/'/g, "''")}'` }

/**
 * Starts `binary args` elevated. Returns { stop(), exited: Promise<number|null>, logFile }.
 * `ready` resolves when the log shows `readyPattern` (or rejects with the log tail).
 */
function runElevated({ binary, args, workDir, logFile: ownLog, readyPattern = /started|tun.*(up|ready)|inbound\/tun/i, timeoutMs = 45000 }) {
  fs.mkdirSync(workDir, { recursive: true })
  const stopFile = path.join(workDir, 'tun.stop')
  // The program's own log when it writes one (sing-box log.output), else its redirected output.
  const logFile = ownLog || path.join(workDir, 'tun.log')
  const outFile = path.join(workDir, 'tun.out')
  const pidFile = path.join(workDir, 'tun.pid')
  for (const f of [stopFile, logFile, pidFile, outFile]) { try { fs.rmSync(f) } catch { /* fresh */ } }
  const parent = process.pid
  let launcher

  if (process.platform === 'win32') {
    const script = write(path.join(workDir, 'tun-watchdog.ps1'), [
      '$ErrorActionPreference = "SilentlyContinue"',
      `$p = Start-Process -FilePath ${psQuote(binary)} -ArgumentList ${args.map(a => psQuote(`"${a}"`)).join(',')} -WorkingDirectory ${psQuote(workDir)} -WindowStyle Hidden -PassThru -RedirectStandardError ${psQuote(outFile)} -RedirectStandardOutput ${psQuote(outFile + '2')}`,
      `Set-Content -Path ${psQuote(pidFile)} -Value $p.Id`,
      `while (-not (Test-Path ${psQuote(stopFile)}) -and (Get-Process -Id ${parent} -ErrorAction SilentlyContinue) -and -not $p.HasExited) { Start-Sleep -Milliseconds 400 }`,
      'if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force }',
      `Remove-Item ${psQuote(stopFile)} -ErrorAction SilentlyContinue`
    ].join('\r\n'))
    // One UAC prompt: an elevated, hidden PowerShell running the watchdog.
    launcher = spawn('powershell', ['-NoProfile', '-NonInteractive', '-WindowStyle', 'Hidden', '-Command',
      `Start-Process -FilePath powershell -Verb RunAs -WindowStyle Hidden -ArgumentList '-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "${script}"'`],
    { windowsHide: true })
  } else {
    const cmd = [shQuote(binary), ...args.map(shQuote)].join(' ')
    const script = write(path.join(workDir, 'tun-watchdog.sh'), [
      '#!/bin/sh',
      `${cmd} >${shQuote(outFile)} 2>&1 &`,
      'SB=$!',
      `echo $SB >${shQuote(pidFile)}`,
      `while [ ! -f ${shQuote(stopFile)} ] && kill -0 ${parent} 2>/dev/null && kill -0 $SB 2>/dev/null; do sleep 0.4; done`,
      'kill $SB 2>/dev/null; sleep 1; kill -9 $SB 2>/dev/null',
      `rm -f ${shQuote(stopFile)}`
    ].join('\n'), 0o755)
    if (process.platform === 'darwin') {
      // The macOS administrator sheet; the watchdog runs detached as root.
      launcher = spawn('/usr/bin/osascript', ['-e',
        `do shell script ${JSON.stringify(`/bin/sh ${shQuote(script)} >/dev/null 2>&1 &`)} with administrator privileges`])
    } else {
      // polkit (the desktop's own password dialog); sudo -n when already allowed.
      launcher = spawn('pkexec', ['/bin/sh', script], { detached: true, stdio: 'ignore' })
      launcher.unref()
    }
  }

  let launchError = null
  launcher.on('error', e => { launchError = e })

  const ready = new Promise((resolve, reject) => {
    const until = Date.now() + timeoutMs
    const tick = () => {
      let log = ''
      for (const f of [logFile, outFile]) { try { log += fs.readFileSync(f, 'utf8') + '\n' } catch { /* not yet */ } }
      if (readyPattern.test(log)) return resolve()
      if (/FATAL|panic:|permission denied|operation not permitted|access is denied|configure tun/i.test(log)) return reject(new Error(lastLines(log)))
      if (launchError) return reject(launchError)
      if (Date.now() > until) return reject(new Error(log ? lastLines(log) : 'اجازهٔ مدیر سیستم داده نشد یا تونل بالا نیامد'))
      setTimeout(tick, 300)
    }
    tick()
  })

  return {
    logFile,
    ready,
    stop() {
      try { fs.writeFileSync(stopFile, '1') } catch { /* already gone */ }
      return new Promise(resolve => {
        const until = Date.now() + 5000
        const wait = () => (!fs.existsSync(stopFile) || Date.now() > until ? resolve() : setTimeout(wait, 200))
        wait()
      })
    }
  }
}

function lastLines(text, n = 4) {
  return String(text).trim().split(/\r?\n/).slice(-n).join('\n').slice(0, 600)
}

module.exports = { runElevated }
