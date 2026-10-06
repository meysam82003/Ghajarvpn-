// Ghajar VPN for Windows, macOS and Linux: the Ghajar web app in its own
// window, with native notifications and a tray icon so notices keep coming
// while the window is closed. It carries no VPN core; purchased services are
// added to a VPN client with the app's own "add to app" links.
const { app, BrowserWindow, Tray, Menu, shell, ipcMain, session, nativeImage } = require('electron')
const fs = require('fs')
const path = require('path')

const APP_URL = process.env.GHAJAR_URL || 'https://httpuser87890.ir/Faoxima/Ghajarvpn/pwa/'
const APP_ID = 'com.ghajarvpn.desktop'
const ICON = path.join(__dirname, 'icons', 'icon.png')
const TRAY_ICON = path.join(__dirname, 'icons', 'tray.png')
// Schemes handed to the system: the browser, Telegram, mail and VPN clients.
const BLOCKED_SCHEMES = new Set(['file:', 'javascript:', 'data:', 'blob:', 'about:', 'chrome:', 'devtools:'])

let win = null
let tray = null
let quitting = false
const startHidden = process.argv.includes('--hidden') || (app.getLoginItemSettings().wasOpenedAsHidden ?? false)

app.setAppUserModelId(APP_ID)
if (!app.requestSingleInstanceLock()) app.quit()

const inApp = url => typeof url === 'string' && url.startsWith(APP_URL)

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
    width: 440,
    height: 880,
    minWidth: 360,
    minHeight: 560,
    title: 'قاجار وی پی ان',
    backgroundColor: '#050807',
    icon: ICON,
    autoHideMenuBar: true,
    show: false,
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
  if (!hidden) win.once('ready-to-show', () => win.show())

  win.webContents.setWindowOpenHandler(({ url }) => {
    if (inApp(url)) win.loadURL(url)
    else openOutside(url)
    return { action: 'deny' }
  })
  win.webContents.on('will-navigate', (event, url) => {
    if (inApp(url) || url.startsWith('file:')) return
    event.preventDefault()
    openOutside(url)
  })
  win.webContents.on('did-fail-load', (_e, code, _desc, url, isMainFrame) => {
    // -3 is an aborted load (a redirect or a new navigation), not a failure.
    if (isMainFrame && code !== -3 && inApp(url)) win.loadFile(path.join(__dirname, 'offline.html'))
  })
  win.on('close', event => {
    if (quitting) return
    event.preventDefault()
    win.hide()
  })
  win.on('closed', () => { win = null })
  win.loadURL(APP_URL)
}

function createTray() {
  const image = nativeImage.createFromPath(TRAY_ICON)
  tray = new Tray(image)
  tray.setToolTip('قاجار وی پی ان')
  const menu = () => Menu.buildFromTemplate([
    { label: 'باز کردن قاجار', click: show },
    {
      label: 'اجرا با روشن شدن کامپیوتر',
      type: 'checkbox',
      checked: app.getLoginItemSettings().openAtLogin,
      click: item => { setLogin(item.checked); tray.setContextMenu(menu()) }
    },
    { type: 'separator' },
    { label: 'خروج کامل', click: () => { quitting = true; app.quit() } }
  ])
  tray.setContextMenu(menu())
  tray.on('click', show)
}

function setLogin(on) {
  if (process.platform === 'linux') return
  app.setLoginItemSettings({ openAtLogin: on, openAsHidden: true, args: ['--hidden'] })
}

app.on('second-instance', show)
app.on('activate', show)
app.on('before-quit', () => { quitting = true })
app.on('window-all-closed', () => { /* stays in the tray */ })

ipcMain.on('ghajar:focus', show)
ipcMain.on('ghajar:open-external', (_e, url) => openOutside(String(url)))
ipcMain.on('ghajar:retry', () => win && win.loadURL(APP_URL))

app.whenReady().then(() => {
  const ses = session.fromPartition('persist:ghajar')
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

  createTray()
  createWindow(startHidden)
})
