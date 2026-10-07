// The browser extension's control API, on 127.0.0.1 only. Web pages cannot
// use it: every call must carry the X-Ghajar-Client header (which forces a
// CORS preflight a page cannot pass) and come from an extension origin or
// none at all. Same endpoints as before the engine rewrite.
const http = require('http')

const API_PORT = 47823

function serve(engine) {
  const allowed = origin => !origin || /^(chrome|moz)-extension:\/\//.test(origin)
  const server = http.createServer(async (req, res) => {
    const origin = req.headers.origin || ''
    const headers = { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' }
    if (origin && allowed(origin)) {
      headers['Access-Control-Allow-Origin'] = origin
      headers['Access-Control-Allow-Headers'] = 'Content-Type, X-Ghajar-Client'
      headers['Access-Control-Allow-Methods'] = 'GET, POST'
      headers.Vary = 'Origin'
    }
    const reply = (code, body) => { res.writeHead(code, headers); res.end(JSON.stringify(body)) }
    if (!allowed(origin)) return reply(403, { ok: false, error: 'forbidden' })
    if (req.method === 'OPTIONS') return reply(204, {})
    if (req.headers['x-ghajar-client'] !== 'extension') return reply(403, { ok: false, error: 'forbidden' })
    let body = {}
    if (req.method === 'POST') {
      let raw = ''
      for await (const c of req) { raw += c; if (raw.length > 65536) break }
      try { body = JSON.parse(raw || '{}') } catch { body = {} }
    }
    const url = new URL(req.url, 'http://127.0.0.1')
    try {
      switch (`${req.method} ${url.pathname}`) {
        case 'GET /status': return reply(200, { ok: true, app: 'ghajar', version: 2, ...engine.status() })
        case 'GET /servers': return reply(200, { ok: true, servers: engine.servers(), selected: engine.activeIndex(), service: engine.serviceName() })
        case 'POST /connect': {
          // The extension wants the browser only; a running full-device or
          // system-proxy connection already covers the browser, so keep it.
          const st = engine.status()
          const target = Number.isInteger(body.index) ? body.index : undefined
          if (st.connected && target === undefined) return reply(200, { ok: true, ...st })
          return reply(200, { ok: true, ...(await engine.connect(target)) })
        }
        case 'POST /disconnect': return reply(200, { ok: true, ...(await engine.disconnect()) })
        case 'POST /refresh': await engine.store.refreshAll(); return reply(200, { ok: true, servers: engine.servers(), selected: engine.activeIndex() })
        case 'POST /ping': return reply(200, { ok: true, results: await engine.ping() })
        case 'POST /direct-iran': engine.setSettings({ iranDirect: !!body.on }); return reply(200, { ok: true, ...engine.status() })
        default: return reply(404, { ok: false, error: 'not found' })
      }
    } catch (e) {
      return reply(200, { ok: false, error: String((e && e.message) || e), ...engine.status() })
    }
  })
  server.on('error', () => { /* another copy already serves the port */ })
  server.listen(API_PORT, '127.0.0.1')
  return server
}

module.exports = { serve, API_PORT }
