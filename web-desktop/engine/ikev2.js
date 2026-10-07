// IKEv2 for the desktop app, through the operating system's own VPN client.
//
// On Android an ikev2:// profile goes to the strongSwan module
// (IkeController.kt): IKEv2 with EAP (username / password, the server proved
// by its certificate against the system CAs), gateway = address, remote
// identity = sni (the address when empty), MTU 576..1500 else 1400, every app
// through the tunnel. Every desktop OS ships an IKEv2 client that does the
// same, so nothing is bundled:
//
//   Windows  a per-user VPN entry (Add-VpnConnection, EAP-MSCHAPv2), dialled
//            and hung up with rasdial
//   macOS    a .mobileconfig (com.apple.vpn.managed, IKEv2, EAP) the user
//            installs once in System Settings, then scutil --nc start / stop
//   Linux    NetworkManager with the strongSwan plugin (nmcli)
//
// This is an OS-level VPN: it carries the whole device and exposes no local
// SOCKS port, so the engine treats it as a mode of its own (manager.js).
//
// Every command goes through an injectable runner so the command lines can be
// tested without the OS client. No dependencies beyond Node's standard library.
'use strict'

const crypto = require('crypto')
const fs = require('fs')
const path = require('path')
const { execFile } = require('child_process')

const CONNECT_TIMEOUT_MS = 45000

/** run(cmd, args, { input, timeout }) → Promise<{ code, stdout, stderr }>. */
function defaultRun(cmd, args, { input, timeout = 60000 } = {}) {
  return new Promise(resolve => {
    const child = execFile(cmd, args, { windowsHide: true, timeout, maxBuffer: 4 << 20 }, (err, stdout, stderr) => {
      resolve({ code: err ? (typeof err.code === 'number' ? err.code : 1) : 0, stdout: String(stdout || ''), stderr: String(stderr || (err && !stdout ? err.message : '')) })
    })
    if (input != null) { child.stdin.end(input) }
  })
}

/** The IKEv2 settings of a profile, as IkeController.profileFor reads them. */
function ikeProfile(config) {
  const c = config || {}
  const address = String(c.address || '').trim()
  const identity = String(c.sni || '').trim() || address
  const mtu = Number(c.mtu) >= 576 && Number(c.mtu) <= 1500 ? Number(c.mtu) : 1400
  const username = String(c.uuid || '')
  const password = String(c.password || '')
  if (!address) throw new Error('آدرس سرور IKEv2 خالی است')
  if (!username || !password) throw new Error('این پروفایل IKEv2 نام کاربری و رمز می‌خواهد')
  // One OS entry per server + credentials: a changed password is a new entry,
  // never a stale one the OS would keep dialling with.
  const hash = crypto.createHash('sha256').update([address, identity, username, password].join('\n')).digest('hex').slice(0, 8)
  return { address, identity, mtu, username, password, hash, name: `Ghajar IKEv2 ${hash}`, label: String(c.name || address) }
}

const isIp = s => /^\d{1,3}(\.\d{1,3}){3}$/.test(s) || (s.includes(':') && /^[0-9a-fA-F:.]+$/.test(s))
const psq = s => "'" + String(s).replace(/'/g, "''") + "'"
const xml = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

// ------------------------------------------------------------------ Windows

/**
 * The PowerShell that creates the per-user entry. Windows checks the server
 * certificate against the name it dials, so a host-name identity with an IP
 * gateway is dialled by that name. The IPsec parameters are the strongSwan
 * defaults (AES-256-GCM, SHA-256, DH group 14) instead of Windows' legacy
 * 3DES / modp1024 proposal, which current servers refuse.
 */
function windowsScript(p) {
  const server = !isIp(p.identity) && isIp(p.address) ? p.identity : p.address
  return [
    "$ErrorActionPreference = 'Stop'",
    `$n = ${psq(p.name)}`,
    'Get-VpnConnection -Name $n -ErrorAction SilentlyContinue | ForEach-Object { Remove-VpnConnection -Name $n -Force }',
    `Add-VpnConnection -Name $n -ServerAddress ${psq(server)} -TunnelType Ikev2 -AuthenticationMethod Eap -EncryptionLevel Required -RememberCredential -Force`,
    'Set-VpnConnectionIPsecConfiguration -ConnectionName $n -AuthenticationTransformConstants GCMAES256 -CipherTransformConstants GCMAES256 -EncryptionMethod AES256 -IntegrityCheckMethod SHA256 -PfsGroup None -DHGroup Group14 -Force',
    "Write-Output 'ok'"
  ].join('; ')
}

/** rasdial's error codes the user can act on. */
function windowsError(out) {
  const m = /(?:error|خطا)\s*(\d{3,5})/i.exec(out) || /\b(691|703|720|789|809|812|13801|13806|13868|868)\b/.exec(out)
  const code = m ? m[1] : ''
  const known = {
    691: 'نام کاربری یا رمز IKEv2 رد شد',
    809: 'سرور IKEv2 در دسترس نیست (پورت‌های UDP 500/4500 بسته‌اند)',
    868: 'نام سرور IKEv2 پیدا نشد',
    13801: 'گواهی سرور IKEv2 پذیرفته نشد (گواهی معتبر نیست یا نامش با سرور یکی نیست)',
    13806: 'گواهی سرور IKEv2 پذیرفته نشد',
    13868: 'سرور IKEv2 تنظیمات امنیتی را نپذیرفت'
  }
  return known[code] || ('اتصال IKEv2 ویندوز ناموفق بود' + (code ? ` (خطای ${code})` : ''))
}

const windows = {
  async connect(p, { run }) {
    const ps = await run('powershell.exe', ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-Command', windowsScript(p)])
    if (ps.code !== 0) throw new Error('ساخت اتصال IKEv2 در ویندوز نشد: ' + (ps.stderr || ps.stdout).trim().split('\n').slice(-2).join(' '))
    const r = await run('rasdial.exe', [p.name, p.username, p.password], { timeout: CONNECT_TIMEOUT_MS })
    if (r.code !== 0) throw new Error(windowsError(r.stdout + '\n' + r.stderr))
  },
  async disconnect(p, { run }) { await run('rasdial.exe', [p.name, '/disconnect']) },
  async connected(p, { run }) {
    const r = await run('rasdial.exe', [])
    return r.stdout.split(/\r?\n/).map(s => s.trim()).includes(p.name)
  }
}

// ------------------------------------------------------------------ macOS

/** A configuration profile with one IKEv2 VPN (EAP username / password). */
function mobileconfig(p) {
  const uuid = seed => { const h = crypto.createHash('sha256').update(seed + p.hash).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-a${h.slice(17, 20)}-${h.slice(20, 32)}`.toUpperCase() }
  const sa = `<dict>
          <key>EncryptionAlgorithm</key><string>AES-256-GCM</string>
          <key>IntegrityAlgorithm</key><string>SHA2-256</string>
          <key>DiffieHellmanGroup</key><integer>14</integer>
          <key>LifeTimeInMinutes</key><integer>1440</integer>
        </dict>`
  return `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>PayloadContent</key>
  <array>
    <dict>
      <key>PayloadType</key><string>com.apple.vpn.managed</string>
      <key>PayloadVersion</key><integer>1</integer>
      <key>PayloadIdentifier</key><string>app.ghajar.ikev2.${p.hash}.vpn</string>
      <key>PayloadUUID</key><string>${uuid('vpn')}</string>
      <key>PayloadDisplayName</key><string>${xml(p.name)}</string>
      <key>UserDefinedName</key><string>${xml(p.name)}</string>
      <key>VPNType</key><string>IKEv2</string>
      <key>IKEv2</key>
      <dict>
        <key>RemoteAddress</key><string>${xml(p.address)}</string>
        <key>RemoteIdentifier</key><string>${xml(p.identity)}</string>
        <key>LocalIdentifier</key><string>${xml(p.username)}</string>
        <key>AuthenticationMethod</key><string>None</string>
        <key>ExtendedAuthEnabled</key><integer>1</integer>
        <key>AuthName</key><string>${xml(p.username)}</string>
        <key>AuthPassword</key><string>${xml(p.password)}</string>
        <key>DisableMOBIKE</key><integer>0</integer>
        <key>DisableRedirect</key><integer>0</integer>
        <key>EnablePFS</key><integer>1</integer>
        <key>UseConfigurationAttributeInternalIPSubnet</key><integer>0</integer>
        <key>MTU</key><integer>${p.mtu}</integer>
        <key>IKESecurityAssociationParameters</key>
        ${sa}
        <key>ChildSecurityAssociationParameters</key>
        ${sa}
        <key>OnDemandEnabled</key><integer>0</integer>
      </dict>
      <key>IPv4</key><dict><key>OverridePrimary</key><integer>1</integer></dict>
    </dict>
  </array>
  <key>PayloadDisplayName</key><string>${xml('Ghajar VPN — IKEv2 ' + p.label)}</string>
  <key>PayloadIdentifier</key><string>app.ghajar.ikev2.${p.hash}</string>
  <key>PayloadType</key><string>Configuration</string>
  <key>PayloadUUID</key><string>${uuid('profile')}</string>
  <key>PayloadVersion</key><integer>1</integer>
  <key>PayloadRemovalDisallowed</key><false/>
</dict>
</plist>
`
}

/** scutil --nc status first line: Connected | Connecting | Disconnected | Disconnecting | Invalid … */
async function macStatus(p, run) {
  const r = await run('/usr/sbin/scutil', ['--nc', 'status', p.name])
  return r.code === 0 ? r.stdout.split('\n')[0].trim() : ''
}

const darwin = {
  async connect(p, { run, dir }) {
    const list = await run('/usr/sbin/scutil', ['--nc', 'list'])
    if (!list.stdout.includes(`"${p.name}"`)) {
      // macOS installs a VPN profile only with the user's approval in System Settings.
      fs.mkdirSync(dir, { recursive: true })
      const file = path.join(dir, `ghajar-ikev2-${p.hash}.mobileconfig`)
      fs.writeFileSync(file, mobileconfig(p), { mode: 0o600 })
      await run('/usr/bin/open', [file])
      const e = new Error('پروفایل IKEv2 باز شد: در System Settings › General › Device Management (یا Profiles) آن را Install کن و دوباره وصل شو')
      e.needsUserAction = true
      throw e
    }
    const r = await run('/usr/sbin/scutil', ['--nc', 'start', p.name])
    if (r.code !== 0) throw new Error('شروع اتصال IKEv2 نشد: ' + (r.stderr || r.stdout).trim())
    const started = Date.now()
    let last = ''
    while (Date.now() - started < CONNECT_TIMEOUT_MS) {
      last = await macStatus(p, run)
      if (last === 'Connected') return
      // Back to Disconnected after a few seconds: the server said no.
      if (last === 'Disconnected' && Date.now() - started > 5000) break
      await new Promise(r2 => setTimeout(r2, 1000))
    }
    await run('/usr/sbin/scutil', ['--nc', 'stop', p.name])
    throw new Error('اتصال IKEv2 برقرار نشد' + (last ? ` (${last})` : ''))
  },
  async disconnect(p, { run }) { await run('/usr/sbin/scutil', ['--nc', 'stop', p.name]) },
  async connected(p, { run }) { return (await macStatus(p, run)) === 'Connected' }
}

// ------------------------------------------------------------------ Linux

const NM_VPN_DIRS = ['/usr/lib/NetworkManager/VPN', '/etc/NetworkManager/VPN', '/usr/lib64/NetworkManager/VPN', '/usr/local/lib/NetworkManager/VPN']

/** Whether NetworkManager's strongSwan plugin is installed. */
function hasNmStrongswan(exists = fs.existsSync) {
  return NM_VPN_DIRS.some(d => exists(path.join(d, 'nm-strongswan-service.name')))
}

/** nmcli arguments that create the connection (NetworkManager-strongswan keys). */
function nmcliAddArgs(p) {
  const data = [
    `address=${p.address}`, 'method=eap', `user=${p.username}`, 'virtual=yes', 'encap=no', 'ipcomp=no',
    'proposal=no', 'password-flags=0'
  ]
  if (p.identity && p.identity !== p.address) data.push(`remote-identity=${p.identity}`)
  return ['connection', 'add', 'type', 'vpn', 'con-name', p.name, 'ifname', '--', 'vpn-type', 'strongswan',
    'connection.autoconnect', 'no', 'vpn.data', data.join(', '), 'vpn.secrets', `password=${p.password}`]
}

const linux = {
  async connect(p, { run, exists }) {
    if (!hasNmStrongswan(exists)) throw new Error('برای IKEv2 در لینوکس، NetworkManager و افزونهٔ strongSwan لازم است (مثلاً: sudo apt install network-manager-strongswan)')
    const show = await run('nmcli', ['-t', '-f', 'NAME', 'connection', 'show'])
    if (show.code !== 0) throw new Error('NetworkManager در دسترس نیست: ' + (show.stderr || show.stdout).trim())
    if (!show.stdout.split('\n').map(s => s.trim()).includes(p.name)) {
      // A connection with the same address but older credentials is replaced.
      for (const old of show.stdout.split('\n').map(s => s.trim()).filter(n => n.startsWith('Ghajar IKEv2 ') && n !== p.name)) await run('nmcli', ['connection', 'delete', 'id', old])
      const add = await run('nmcli', nmcliAddArgs(p))
      if (add.code !== 0) throw new Error('ساخت اتصال IKEv2 نشد: ' + (add.stderr || add.stdout).trim())
    }
    const up = await run('nmcli', ['--wait', String(Math.round(CONNECT_TIMEOUT_MS / 1000)), 'connection', 'up', 'id', p.name], { timeout: CONNECT_TIMEOUT_MS + 5000 })
    if (up.code !== 0) throw new Error('اتصال IKEv2 برقرار نشد: ' + (up.stderr || up.stdout).trim().split('\n').slice(-1)[0])
  },
  async disconnect(p, { run }) { await run('nmcli', ['connection', 'down', 'id', p.name]) },
  async connected(p, { run }) {
    const r = await run('nmcli', ['-t', '-f', 'GENERAL.STATE', 'connection', 'show', 'id', p.name])
    return /activated/.test(r.stdout) && !/deactivat/.test(r.stdout)
  }
}

const PLATFORMS = { win32: windows, darwin, linux }

/**
 * The OS IKEv2 client for one profile:
 *   const vpn = osVpn(config, { platform, run, dir })
 *   await vpn.connect(); await vpn.connected(); await vpn.disconnect()
 * dir: where the macOS profile is written (the engine's run folder).
 */
function osVpn(config, { platform = process.platform, run = defaultRun, dir = '', exists = fs.existsSync } = {}) {
  const impl = PLATFORMS[platform]
  if (!impl) throw new Error('IKEv2 روی این سیستم‌عامل پشتیبانی نمی‌شود')
  const p = ikeProfile(config)
  const ctx = { run, dir, exists }
  return {
    profile: p,
    connect: () => impl.connect(p, ctx),
    disconnect: () => impl.disconnect(p, ctx).catch(() => undefined),
    connected: () => impl.connected(p, ctx).catch(() => false)
  }
}

/** Whether this OS has an IKEv2 client the engine can drive. */
function supported(platform = process.platform, exists = fs.existsSync) {
  if (platform === 'win32' || platform === 'darwin') return true
  return platform === 'linux' && hasNmStrongswan(exists)
}

module.exports = { osVpn, supported, ikeProfile, windowsScript, windowsError, mobileconfig, nmcliAddArgs, hasNmStrongswan, CONNECT_TIMEOUT_MS }
