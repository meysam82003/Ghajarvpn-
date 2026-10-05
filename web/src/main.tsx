import { render } from 'preact'
import './styles/base.css'
import './styles/skin.css'
import './styles/shell.css'
import { applyTheme } from './theme/look'
import { App } from './app'
import { applyHash, goTab, toast } from './state/ui'
import { startNoticeMonitor } from './state/notices'
import { registerServiceWorker } from './lib/pwa'
import { redeemTicket } from './api/client'
import { refreshOwned } from './state/shop'
import { tokenStore } from './api/account'

applyTheme()

async function boot() {
  // A one-time ticket from the Android app's "open the full panel": spend it
  // for this same account and take it out of the address bar at once.
  const params = new URLSearchParams(location.search)
  const ticket = params.get('ticket')
  if (ticket) {
    history.replaceState(null, '', location.pathname + location.hash)
    try { if (await redeemTicket(ticket)) toast('حساب از برنامه وارد شد') } catch { /* ticket expired: the sign-in card is there */ }
  }
  applyHash()
  if (!location.hash) goTab('home')
  render(<App />, document.getElementById('app')!)
  document.documentElement.classList.add('ready')
  void registerServiceWorker()
  startNoticeMonitor()
  void refreshOwned().catch(() => undefined)
  tokenStore.subscribe(() => { void refreshOwned().catch(() => undefined) })
}

void boot()
