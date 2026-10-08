// Ghajar VPN for Windows, macOS and Linux: the Ghajar web app in its own
// window, with native notifications and a tray icon so notices keep coming
// while the window is closed. It also connects for real: the engine (Xray,
// sing-box, Psiphon, the protocol helpers) runs the VPN for the browser only,
// through the system proxy, or for the whole device with a TUN.
const { app, BrowserWindow, Tray, Menu, shell, ipcMain, session, nativeImage, net } = require('electron')
const { pathToFileURL } = require('url')
const fs = require('fs')
const path = require('path')
const { Engine } = require('./engine/manager.js')
const { serve } = require('./engine/api.js')

const APP_URL = process.env.GHAJAR_URL || 'https://httpuser87890.ir/Faoxima/Ghajarvpn/pwa/'
// The app's own screens ship inside it (resources/pwa): they open at once,
// offline too, and are always the version this build was made with. They are
// served under APP_URL itself, so the API next to it, the account and the
// saved settings work exactly as on the website.
const PWA_DIR = app.isPackaged ? path.join(process.resourcesPath, 'pwa') : path.join(__dirname, '..', 'backend', 'Faoxima-1.0.0', 'pwa')
const APP_ID = 'com.ghajarvpn.desktop'
const ICON = path.join(__dirname, 'icons', 'icon.png')
const TRAY_ICON = path.join(__dirname, 'icons', 'tray.png')
// Schemes handed to the system: the browser, Telegram, mail and VPN clients.
const BLOCKED_SCHEMES = new Set(['file:', 'javascript:', 'data:', 'blob:', 'about:', 'chrome:', 'devtools:'])

let win = null
let core = null
// The cores ship next to the app (resources/core); in development it can be pointed at with GHAJAR_CORE_DIR.
const CORE_DIR = process.env.GHAJAR_CORE_DIR || (app.isPackaged ? path.join(process.resourcesPath, 'core') : path.join(__dirname, 'core', `${process.platform}-${process.arch}`))
// Connection attempts: through the system proxy first (a VPN client often
// sets one), then directly, since Iranian hosts commonly refuse foreign exits.
const ROUTES = [{ mode: 'system' }, { mode: 'direct' }]
let route = 0
let tray = null
let quitting = false
const startHidden = process.argv.includes('--hidden') || (app.getLoginItemSettings().wasOpenedAsHidden ?? false)

app.setAppUserModelId(APP_ID)
// The server sees an ordinary Chrome, not "Electron/…": some hosts and
// firewalls drop requests from unknown clients.
app.userAgentFallback = app.userAgentFallback.replace(/ (?:Electron|ghajar-app|GhajarVPN)\/\S+/g, '')
if (!app.requestSingleInstanceLock()) app.quit()

const inApp = url => typeof url === 'string' && url.startsWith(APP_URL)
// Pages that belong to other apps or stores open there; every other web page
// (the checkout page, the bank gateway, its return page) opens in the
// in-app payment window, with no address bar, like the Android app.
const EXTERNAL_HOSTS = /(^|\.)(t\.me|telegram\.me|telegram\.org|github\.com|githubusercontent\.com|google\.com|apple\.com|happ\.su|microsoft\.com)$/i
function isPaymentPage(url) {
  try {
    const u = new URL(url)
    return (u.protocol === 'https:' || u.protocol === 'http:') && !inApp(url) && !EXTERNAL_HOSTS.test(u.hostname)
  } catch { return false }
}

let payWin = null
/** The payment window: closes itself and refreshes the order when the gateway sends the buyer back. */
function openPayment(url) {
  if (!win) return
  if (payWin && !payWin.isDestroyed()) { payWin.loadURL(url); payWin.focus(); return }
  payWin = new BrowserWindow({
    parent: win,
    modal: process.platform !== 'darwin',
    width: 560,
    height: 820,
    minWidth: 360,
    minHeight: 500,
    title: 'پرداخت امن قاجار',
    backgroundColor: '#ffffff',
    icon: ICON,
    autoHideMenuBar: true,
    webPreferences: { partition: 'persist:ghajar-pay', contextIsolation: true, sandbox: true, nodeIntegration: false }
  })
  payWin.removeMenu()
  payWin.on('page-title-updated', e => e.preventDefault())
  const finish = () => {
    if (payWin && !payWin.isDestroyed()) payWin.close()
  }
  const route = target => {
    if (inApp(target) || /^ghajarvpn:/i.test(target)) { finish(); return true }
    if (/^https?:\/\/(t\.me|telegram\.me)\//i.test(target) || /^tg:/i.test(target)) { finish(); return true }
    if (!/^https?:/i.test(target)) { openOutside(target); return true }
    return false
  }
  payWin.webContents.setWindowOpenHandler(({ url: next }) => {
    if (!route(next)) payWin.loadURL(next)
    return { action: 'deny' }
  })
  payWin.webContents.on('will-navigate', (event, next) => { if (route(next)) event.preventDefault() })
  payWin.webContents.on('will-redirect', (event, next) => { if (route(next)) event.preventDefault() })
  payWin.on('closed', () => {
    payWin = null
    if (win && !win.isDestroyed()) {
      win.focus()
      // The app checks the order as it does when the Android payment screen closes.
      win.webContents.executeJavaScript("location.hash = '#/pay'").catch(() => undefined)
    }
  })
  payWin.loadURL(url)
}

let lastExternal = { url: '', at: 0 }
function openOutside(url) {
  let parsed
  try { parsed = new URL(url) } catch { return }
  if (BLOCKED_SCHEMES.has(parsed.protocol)) return
  // window.open is answered here and the page may then also navigate to the
  // same address as its fallback: open it once.
  const now = Date.now()
  if (lastExternal.url === url && now - lastExternal.at < 2000) return
  lastExternal = { url, at: now }
  shell.openExternal(url).catch(() => undefined)
}

function show() {
  if (!win) return createWindow(false)
  if (win.isMinimized()) win.restore()
  win.show()
  win.focus()
}

function createWindow(hidden) {
  win = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 380,
    minHeight: 600,
    center: true,
    title: 'قاجار وی پی ان',
    backgroundColor: '#050807',
    icon: ICON,
    autoHideMenuBar: true,
    // Shown at once (the bundled screens paint in a moment); the dark
    // background avoids a white flash meanwhile.
    show: !hidden,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      partition: 'persist:ghajar',
      contextIsolation: true,
      sandbox: true,
      nodeIntegration: false,
      spellcheck: false,
      // The notice poll keeps running while the window is hidden in the tray.
      backgroundThrottling: false
    }
  })
  win.removeMenu()

  win.webContents.setWindowOpenHandler(({ url }) => {
    if (inApp(url)) win.loadURL(url)
    else if (isPaymentPage(url)) openPayment(url)
    else openOutside(url)
    return { action: 'deny' }
  })
  win.webContents.on('will-navigate', (event, url) => {
    if (inApp(url) || url.startsWith('file:')) return
    event.preventDefault()
    if (isPaymentPage(url)) openPayment(url)
    else openOutside(url)
  })
  win.webContents.on('did-fail-load', async (_e, code, desc, url, isMainFrame) => {
    // -3 is an aborted load (a redirect or a new navigation), not a failure.
    if (!isMainFrame || code === -3 || !inApp(url)) return
    if (route < ROUTES.length - 1) {
      route++
      await useRoute()
      win.loadURL(APP_URL)
      return
    }
    route = 0
    await useRoute()
    win.loadFile(path.join(__dirname, 'offline.html'), { query: { code: String(code), desc: String(desc || '') } })
  })
  win.on('close', event => {
    if (quitting) return
    event.preventDefault()
    win.hide()
  })
  win.on('closed', () => { win = null })
  win.loadURL(APP_URL)
}

const MODE_LABEL = { tun: 'کل دستگاه', apps: 'برنامه‌های انتخابی', system: 'پراکسی سیستم', proxy: 'پراکسی محلی' }

function trayMenu() {
  const st = core ? core.status() : { connected: false }
  return Menu.buildFromTemplate([
    { label: st.connected ? `🟢 ${MODE_LABEL[st.mode] || 'متصل'} · ${st.server ? st.server.name.slice(0, 40) : ''}` : '⚪️ قطع', enabled: false },
    st.connected
      ? { label: 'قطع اتصال', click: () => core.disconnect().catch(() => undefined) }
      : { label: 'اتصال', enabled: !!core && !!core.store.selected(), click: () => core.connect().catch(() => undefined) },
    ...(core ? [{
      label: 'نوع اتصال',
      submenu: Object.entries(MODE_LABEL).map(([mode, label]) => ({
        label, type: 'radio', checked: core.settings().mode === mode,
        click: () => { core.setSettings({ mode }); if (core.status().connected) core.connect().catch(() => undefined) }
      }))
    }] : []),
    { type: 'separator' },
    { label: 'باز کردن قاجار', click: show },
    {
      label: 'اجرا با روشن شدن کامپیوتر',
      type: 'checkbox',
      checked: app.getLoginItemSettings().openAtLogin,
      click: item => { setLogin(item.checked); tray.setContextMenu(trayMenu()) }
    },
    { type: 'separator' },
    { label: 'خروج کامل', click: () => { quitting = true; app.quit() } }
  ])
}

function createTray() {
  const image = nativeImage.createFromPath(TRAY_ICON)
  tray = new Tray(image)
  tray.setToolTip('قاجار وی پی ان')
  tray.setContextMenu(trayMenu())
  tray.on('click', show)
}

function setLogin(on) {
  if (process.platform === 'linux') return
  app.setLoginItemSettings({ openAtLogin: on, openAsHidden: true, args: ['--hidden'] })
}

app.on('second-instance', show)
app.on('activate', show)
app.on('before-quit', () => { quitting = true; if (core) core.disconnect(true).catch(() => undefined) })
app.on('window-all-closed', () => { /* stays in the tray */ })

ipcMain.on('ghajar:focus', show)
ipcMain.on('ghajar:app-version', e => { e.returnValue = app.getVersion() })
// The web app's connect control, in this app: the core, browser-only.
ipcMain.handle('ghajar:core', async (_e, op, arg) => {
  if (!core) throw new Error('core not ready')
  const id = typeof arg === 'string' ? arg : ''
  switch (op) {
    case 'status': return core.status()
    case 'capabilities': return core.capabilities()
    case 'servers': return core.servers()
    case 'groups': return core.groups()
    case 'settings': return core.settings()
    case 'setSettings': return core.setSettings(arg && typeof arg === 'object' ? arg : {})
    case 'setService': await core.store.setService(arg || {}); return core.servers()
    case 'add': return core.store.addFromText(String(arg || ''))
    case 'addSubscription': return core.store.addSubscription(String((arg && arg.url) || ''), String((arg && arg.name) || ''))
    case 'refreshSubscriptions': return core.store.refreshAll()
    case 'renameSubscription': core.store.renameSubscription(String(arg.id), String(arg.name)); return core.groups()
    case 'removeSubscription': core.store.removeSubscription(id); return core.groups()
    case 'removeConfig': core.store.removeConfig(id); return core.groups()
    case 'favorite': core.store.toggleFavorite(id); return core.groups()
    case 'select': core.store.select(id); return core.status()
    case 'shareLink': { const c = core.store.config(id); return c ? require('./engine/parser.js').toShareLink(c) : null }
    case 'connect': return core.connect(Number.isInteger(arg) || typeof arg === 'string' ? arg : undefined)
    case 'disconnect': return core.disconnect()
    case 'ping': return core.ping()
    case 'test': return core.testDelays(Array.isArray(arg) ? arg.map(String) : undefined)
    case 'fastest': { const c = await core.fastest(); if (c) core.store.select(c.id); return c ? c.id : '' }
    case 'refreshFree': return core.refreshFree({ fetchImpl: await viaVpnFetch() })
    case 'addWarp': return (await core.addWarp({ fetchImpl: await viaVpnFetch() })).id
    case 'addPsiphon': return core.addPsiphon(String(arg || '')).id
    case 'runningApps': return core.runningApps()
    case 'setCredentials': core.setCredentials(String(arg.id), String(arg.username || ''), String(arg.password || '')); return core.groups()
    case 'addTor': return core.addTor(arg && typeof arg === 'object' ? arg : {}).id
    case 'torCountries': return require('./engine/tor.js').COUNTRIES
    case 'addAether': return core.addAether(arg && typeof arg === 'object' ? arg : {}).id
    case 'submitAetherCode': return core.submitAetherCode(String(arg || ''))
    case 'directIran': core.setSettings({ iranDirect: !!arg }); return core.status()
    default: throw new Error('unknown')
  }
})
ipcMain.on('ghajar:open-external', (_e, url) => openOutside(String(url)))
ipcMain.on('ghajar:open-payment', (_e, url) => { if (isPaymentPage(String(url))) openPayment(String(url)) })
ipcMain.on('ghajar:retry', async () => {
  if (!win) return
  route = 0
  await useRoute()
  win.loadURL(APP_URL)
})

/**
 * fetch for the free sources: Telegram and Cloudflare are filtered in Iran, so
 * while connected it goes through the VPN's own local HTTP port, like the app.
 */
async function viaVpnFetch() {
  const ses = session.fromPartition('ghajar-feeds')
  const st = core.status()
  await ses.setProxy(st.connected && st.http ? { proxyRules: `http://127.0.0.1:${st.http.port}` } : { mode: 'direct' })
  return (url, init) => ses.fetch(url, init)
}

/** The bundled file for an app-screen address, or null (API, PHP and anything else go to the network). */
function bundledFile(url) {
  if (!url.startsWith(APP_URL) || !fs.existsSync(path.join(PWA_DIR, 'index.html'))) return null
  let rel
  try { rel = decodeURIComponent(new URL(url).pathname.slice(new URL(APP_URL).pathname.length)) } catch { return null }
  if (!rel || rel.endsWith('/')) rel += 'index.html'
  if (/\.php$/i.test(rel)) return null
  const file = path.normalize(path.join(PWA_DIR, rel))
  if (!file.startsWith(PWA_DIR + path.sep)) return null
  return fs.existsSync(file) && fs.statSync(file).isFile() ? file : null
}

async function serveBundledScreens(ses) {
  if (!APP_URL.startsWith('https:') || !fs.existsSync(path.join(PWA_DIR, 'index.html'))) return
  // An older copy of the website's service worker would keep serving its own
  // cached screens; the bundled ones replace it. Once per app version, and
  // never holding the window back for more than a moment.
  const marker = path.join(app.getPath('userData'), 'screens-version')
  let seen = ''
  try { seen = fs.readFileSync(marker, 'utf8') } catch { /* first start */ }
  if (seen !== app.getVersion()) {
    await Promise.race([
      ses.clearStorageData({ storages: ['serviceworkers', 'cachestorage'] }).catch(() => undefined),
      new Promise(resolve => setTimeout(resolve, 2500))
    ])
    try { fs.writeFileSync(marker, app.getVersion()) } catch { /* read-only profile */ }
  }
  ses.protocol.handle('https', request => {
    const file = request.method === 'GET' ? bundledFile(request.url) : null
    if (file) return net.fetch(pathToFileURL(file).toString())
    return ses.fetch(request, { bypassCustomProtocolHandlers: true })
  })
}

async function useRoute() {
  const ses = session.fromPartition('persist:ghajar')
  try {
    await ses.setProxy(ROUTES[route])
    await ses.closeAllConnections()
  } catch { /* older runtime: keep the current route */ }
}

app.whenReady().then(async () => {
  const ses = session.fromPartition('persist:ghajar')
  await serveBundledScreens(ses)
  ses.setPermissionRequestHandler((_wc, permission, done) => {
    done(['notifications', 'clipboard-sanitized-write', 'clipboard-read'].includes(permission))
  })
  ses.setPermissionCheckHandler((_wc, permission) => ['notifications', 'clipboard-sanitized-write'].includes(permission))

  // Notices arrive only while the app runs, so it starts with the computer
  // (quietly, in the tray) unless the user turned that off once.
  const marker = path.join(app.getPath('userData'), 'login-item-set')
  if (!fs.existsSync(marker)) {
    setLogin(true)
    try { fs.writeFileSync(marker, '1') } catch { /* read-only profile */ }
  }

  core = new Engine({
    dataDir: app.getPath('userData'),
    binDir: CORE_DIR,
    onChange: st => {
      if (win && !win.isDestroyed()) win.webContents.send('ghajar:core-status', st)
      if (tray) { tray.setContextMenu(trayMenu()); tray.setToolTip(st.connected ? `قاجار وی پی ان · ${MODE_LABEL[st.mode] || 'متصل'}` : 'قاجار وی پی ان') }
    }
  })
  // The control port the Ghajar browser extension talks to (127.0.0.1 only).
  serve(core)

  createTray()
  createWindow(startHidden)
})
