// The popup: the connect control, the servers of the service chosen in the
// desktop app, and the Iranian-sites switch. All work happens in the
// background script; this only asks it and draws.
const ext = typeof browser !== 'undefined' ? browser : chrome
const $ = id => document.getElementById(id)
const fa = n => String(n).replace(/\d/g, d => '۰۱۲۳۴۵۶۷۸۹'[d])

let status = null
let servers = []
let pings = {}
let busy = false

function send(msg) {
  return new Promise(resolve => {
    try {
      const p = ext.runtime.sendMessage(msg, r => resolve(r || { ok: false, companion: false }))
      if (p && typeof p.then === 'function') p.then(r => resolve(r || { ok: false, companion: false }), () => resolve({ ok: false, companion: false }))
    } catch { resolve({ ok: false, companion: false }) }
  })
}

function showError(text) {
  $('error').textContent = text || ''
  $('error').hidden = !text
}

function draw() {
  const ok = !!(status && status.companion === true)
  $('missing').hidden = !!ok
  $('main').hidden = !ok
  if (!ok) return
  const on = !!status.connected
  const orb = $('orb')
  orb.classList.toggle('on', on)
  orb.classList.toggle('busy', busy || !!status.connecting)
  $('orbLabel').textContent = busy || status.connecting ? 'در حال اتصال…' : on ? 'متصل' : 'اتصال'
  $('orbHint').textContent = on ? (status.server ? status.server.name : '') : status.available === false ? 'هسته در برنامه نیست' : ''
  $('line').textContent = on
    ? `مرورگر از VPN استفاده می‌کند (${status.socks ? '127.0.0.1:' + status.socks.port : ''}) · بقیهٔ سیستم دست نمی‌خورد`
    : 'فقط همین مرورگر از VPN استفاده می‌کند؛ بقیهٔ برنامه‌های کامپیوتر دست نمی‌خورند.'
  $('iran').checked = status.directIran !== false
  $('service').textContent = status.service ? `سرورهای ${status.service}` : 'سرورها'

  const list = $('servers')
  list.textContent = ''
  $('empty').hidden = servers.length > 0
  for (const s of servers) {
    const li = document.createElement('li')
    const b = document.createElement('button')
    if (s.index === status.selected) b.className = 'sel'
    const name = document.createElement('span'); name.className = 'name'; name.textContent = s.name
    const proto = document.createElement('span'); proto.className = 'proto'; proto.textContent = s.protocol.toUpperCase()
    const ms = document.createElement('span'); ms.className = 'ms'
    if (pings[s.index] != null) { ms.textContent = pings[s.index] < 0 ? '✕' : fa(pings[s.index]) + ' ms'; if (pings[s.index] < 0) ms.classList.add('bad') }
    b.append(name, proto, ms)
    b.addEventListener('click', () => connect(s.index))
    li.append(b)
    list.append(li)
  }
}

async function load() {
  status = await send({ type: 'status' })
  if (status && status.companion === true) {
    const r = await send({ type: 'servers' })
    servers = (r && r.servers) || []
  }
  draw()
}

async function connect(index) {
  busy = true; showError(''); draw()
  const r = await send({ type: 'connect', index })
  busy = false
  if (r && r.ok === false) showError(r.error || 'اتصال برقرار نشد')
  await load()
}

$('orb').addEventListener('click', async () => {
  if (busy) return
  if (status && status.connected) {
    busy = true; draw()
    await send({ type: 'disconnect' })
    busy = false
    await load()
  } else {
    await connect(status ? status.selected : undefined)
  }
})
$('ping').addEventListener('click', async () => {
  const r = await send({ type: 'ping' })
  pings = Object.fromEntries(((r && r.results) || []).map(x => [x.index, x.ms]))
  draw()
})
$('refresh').addEventListener('click', async () => {
  const r = await send({ type: 'refresh' })
  if (r && r.ok === false) showError(r.error || 'بروزرسانی نشد')
  await load()
})
$('iran').addEventListener('change', async e => {
  await send({ type: 'directIran', on: e.target.checked })
  if (status && status.connected) await connect(status.selected) // the new rule applies on the next connection
  else await load()
})
$('retry').addEventListener('click', load)

load()
