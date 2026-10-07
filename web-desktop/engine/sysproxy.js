// System proxy mode: points the operating system's own proxy settings at the
// local core, so every app that honours them goes through the VPN without a
// TUN (and without administrator rights on Windows and Linux).
//
//   Windows  per-user WinINet settings (registry) + a refresh broadcast
//   macOS    networksetup on every enabled network service (asks for the
//            administrator password once per change, as macOS requires)
//   Linux    GNOME/Cinnamon/MATE gsettings, and KDE's kioslaverc when present
const { execFile } = require('child_process')
const fs = require('fs')
const os = require('os')
const path = require('path')

const BYPASS = ['localhost', '127.*', '10.*', '172.16.*', '172.17.*', '172.18.*', '172.19.*', '172.2*', '172.30.*', '172.31.*', '192.168.*', '*.local', '<local>']

function run(cmd, args, opts = {}) {
  return new Promise((resolve, reject) => {
    execFile(cmd, args, { windowsHide: true, timeout: 20000, ...opts }, (err, stdout, stderr) => {
      if (err) reject(Object.assign(err, { stdout, stderr }))
      else resolve(String(stdout))
    })
  })
}

// ------------------------------------------------------------------ Windows

const WININET_REFRESH = `
Add-Type -Namespace Ghajar -Name WinInet -MemberDefinition '[DllImport("wininet.dll")] public static extern bool InternetSetOption(System.IntPtr h, int o, System.IntPtr b, int l);'
[Ghajar.WinInet]::InternetSetOption([System.IntPtr]::Zero, 39, [System.IntPtr]::Zero, 0) | Out-Null
[Ghajar.WinInet]::InternetSetOption([System.IntPtr]::Zero, 37, [System.IntPtr]::Zero, 0) | Out-Null`

async function windows(on, host, httpPort, socksPort) {
  const key = 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings'
  if (on) {
    // HTTP for http/https, SOCKS for the rest: what WinINet-based apps understand.
    const server = `http=${host}:${httpPort};https=${host}:${httpPort};socks=${host}:${socksPort}`
    await run('reg', ['add', key, '/v', 'ProxyEnable', '/t', 'REG_DWORD', '/d', '1', '/f'])
    await run('reg', ['add', key, '/v', 'ProxyServer', '/t', 'REG_SZ', '/d', server, '/f'])
    await run('reg', ['add', key, '/v', 'ProxyOverride', '/t', 'REG_SZ', '/d', BYPASS.join(';'), '/f'])
  } else {
    await run('reg', ['add', key, '/v', 'ProxyEnable', '/t', 'REG_DWORD', '/d', '0', '/f'])
  }
  await run('powershell', ['-NoProfile', '-NonInteractive', '-Command', WININET_REFRESH]).catch(() => undefined)
}

// ------------------------------------------------------------------ macOS

async function macServices() {
  const out = await run('/usr/sbin/networksetup', ['-listallnetworkservices'])
  return out.split('\n').slice(1).map(s => s.trim()).filter(s => s && !s.startsWith('*'))
}

function shQuote(s) { return `'${String(s).replace(/'/g, `'\\''`)}'` }

async function mac(on, host, httpPort, socksPort) {
  const services = await macServices()
  const ns = '/usr/sbin/networksetup'
  const lines = []
  for (const s of services) {
    const q = shQuote(s)
    if (on) {
      lines.push(`${ns} -setwebproxy ${q} ${host} ${httpPort}`, `${ns} -setsecurewebproxy ${q} ${host} ${httpPort}`,
        `${ns} -setsocksfirewallproxy ${q} ${host} ${socksPort}`, `${ns} -setproxybypassdomains ${q} ${BYPASS.filter(b => b !== '<local>').join(' ')}`)
    } else {
      lines.push(`${ns} -setwebproxystate ${q} off`, `${ns} -setsecurewebproxystate ${q} off`, `${ns} -setsocksfirewallproxystate ${q} off`)
    }
  }
  const script = lines.join('; ')
  // One administrator prompt for the whole change.
  await run('/usr/bin/osascript', ['-e', `do shell script ${JSON.stringify(script)} with administrator privileges`], { timeout: 120000 })
}

// ------------------------------------------------------------------ Linux

async function linux(on, host, httpPort, socksPort) {
  const gs = (...a) => run('gsettings', a).catch(() => undefined)
  if (on) {
    await gs('set', 'org.gnome.system.proxy', 'mode', 'manual')
    for (const [k, p] of [['http', httpPort], ['https', httpPort], ['socks', socksPort]]) {
      await gs('set', `org.gnome.system.proxy.${k}`, 'host', host)
      await gs('set', `org.gnome.system.proxy.${k}`, 'port', String(p))
    }
    await gs('set', 'org.gnome.system.proxy', 'ignore-hosts', `[${BYPASS.filter(b => b !== '<local>').map(b => `'${b}'`).join(', ')}]`)
  } else {
    await gs('set', 'org.gnome.system.proxy', 'mode', 'none')
  }
  // KDE Plasma
  const kde = path.join(os.homedir(), '.config', 'kioslaverc')
  if (fs.existsSync(kde) || process.env.XDG_CURRENT_DESKTOP === 'KDE') {
    // Plasma 6 ships kwriteconfig6, Plasma 5 kwriteconfig5.
    const set = (k, v) => {
      const args = ['--file', 'kioslaverc', '--group', 'Proxy Settings', '--key', k, v]
      return run('kwriteconfig6', args).catch(() => run('kwriteconfig5', args)).catch(() => undefined)
    }
    await set('ProxyType', on ? '1' : '0')
    if (on) {
      await set('httpProxy', `http://${host} ${httpPort}`)
      await set('httpsProxy', `http://${host} ${httpPort}`)
      await set('socksProxy', `socks://${host} ${socksPort}`)
    }
  }
}

/** Turns the system proxy on (pointing at the local core) or off. */
async function setSystemProxy(on, { host = '127.0.0.1', httpPort = 10809, socksPort = 10808 } = {}) {
  if (process.platform === 'win32') return windows(on, host, httpPort, socksPort)
  if (process.platform === 'darwin') return mac(on, host, httpPort, socksPort)
  return linux(on, host, httpPort, socksPort)
}

module.exports = { setSystemProxy }
