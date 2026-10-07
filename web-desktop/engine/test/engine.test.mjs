// The desktop engine end to end: real Xray and sing-box servers on this
// machine, the Engine connecting through them in proxy mode, delay tests,
// "fastest", subscriptions and the extension API. Also the engines ported
// from the Android app:
//   OpenVPN  .ovpn imported, carried by sing-box's openvpn-client against
//            sing-box's openvpn-server (UDP + tls-crypt, TCP + tls-auth)
//   Tor      the real tor on a private Tor network (tor-testnet.mjs): plain,
//            through another server (torThroughVpn), and over an obfs4 bridge
//            with lyrebird
//   Aether   process wiring with a stand-in aether (the real one needs
//            Cloudflare WARP; engines.test.mjs checks its CLI accepts the arguments)
//   IKEv2    refused cleanly where the OS client is missing
//   node web-desktop/engine/test/engine.test.mjs
// XRAY_BIN / SINGBOX_BIN / GEO_DIR / TOR_BIN / LYREBIRD_BIN choose the
// programs; the Tor part is skipped without tor + tor-gencert.
import { createRequire } from 'node:module'
import { spawn, execFileSync } from 'node:child_process'
import { mkdtempSync, writeFileSync, readFileSync, symlinkSync, existsSync, rmSync, mkdirSync, chmodSync } from 'node:fs'
import crypto from 'node:crypto'
import { tmpdir } from 'node:os'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import http from 'node:http'
import net from 'node:net'

const require = createRequire(import.meta.url)
const here = dirname(fileURLToPath(import.meta.url))
const { Engine } = require('../manager.js')
const { serve } = require('../api.js')
const P_family = c => require('../parser.js').protocolFamily(c)

const XRAY = process.env.XRAY_BIN || '/tmp/claude-0/cores/xray/xray'
const SINGBOX = process.env.SINGBOX_BIN || '/tmp/claude-0/cores/sing-box'
const GEO = process.env.GEO_DIR || join(here, '..', '..', '..', 'app', 'src', 'main', 'assets')
const which = n => { try { return execFileSync('sh', ['-c', `command -v ${n}`], { encoding: 'utf8' }).trim() } catch { return '' } }
const TOR = process.env.TOR_BIN || which('tor')
const TOR_GENCERT = which('tor-gencert')
const LYREBIRD = process.env.LYREBIRD_BIN || ['/tmp/claude-0/cores/lyrebird'].find(existsSync) || ''

let failures = 0
const ok = m => console.log('ok  ', m)
const fail = m => { failures++; console.log('FAIL', m) }
const children = []
const cleanup = () => { for (const c of children) { try { c.kill('SIGKILL') } catch { /* gone */ } } }
process.on('exit', cleanup)

const work = mkdtempSync(join(tmpdir(), 'ghajar-engine-'))
const binDir = join(work, 'bin')
const dataDir = join(work, 'data')
execFileSync('mkdir', ['-p', binDir, dataDir])
symlinkSync(XRAY, join(binDir, 'xray'))
if (existsSync(SINGBOX)) symlinkSync(SINGBOX, join(binDir, 'sing-box'))
for (const f of ['geoip.dat', 'geosite.dat']) symlinkSync(join(GEO, f), join(binDir, f))

function freePort() {
  return new Promise(r => { const s = net.createServer(); s.listen(0, '127.0.0.1', () => { const p = s.address().port; s.close(() => r(p)) }) })
}
function waitPort(port, ms = 8000) {
  const until = Date.now() + ms
  return new Promise(resolve => {
    const t = () => { const s = net.connect(port, '127.0.0.1'); s.once('connect', () => { s.destroy(); resolve(true) }); s.once('error', () => { s.destroy(); Date.now() < until ? setTimeout(t, 100) : resolve(false) }) }
    t()
  })
}
/** GET through a local SOCKS5 port (the way an app pointed at the engine would). */
function viaSocks(socksPort, host, port, pathName = '/') {
  return new Promise((resolve, reject) => {
    const s = net.connect(socksPort, '127.0.0.1')
    let stage = 0, buf = Buffer.alloc(0)
    s.setTimeout(8000, () => { s.destroy(); reject(new Error('timeout')) })
    s.on('error', reject)
    s.on('connect', () => s.write(Buffer.from([5, 1, 0])))
    s.on('data', d => {
      buf = Buffer.concat([buf, d])
      if (stage === 0 && buf.length >= 2) {
        stage = 1; buf = Buffer.alloc(0)
        const h = Buffer.from(host)
        s.write(Buffer.concat([Buffer.from([5, 1, 0, 3, h.length]), h, Buffer.from([port >> 8, port & 255])]))
      } else if (stage === 1 && buf.length >= 10) {
        if (buf[1] !== 0) { s.destroy(); return reject(new Error('socks refused ' + buf[1])) }
        stage = 2; buf = Buffer.alloc(0)
        s.write(`GET ${pathName} HTTP/1.1\r\nHost: ${host}\r\nConnection: close\r\n\r\n`)
      }
    })
    s.on('end', () => { if (stage === 2) resolve(buf.toString()) })
    s.on('close', () => { if (stage === 2) resolve(buf.toString()) })
  })
}

// ---- the "internet": an HTTP server every proxy server sends traffic to
const web = http.createServer((req, res) => {
  if (req.url === '/generate_204') { res.writeHead(204); return res.end() }
  if (req.url === '/sub') { res.writeHead(200, { 'subscription-userinfo': 'upload=1000; download=2000; total=10737418240; expire=1999999999', 'profile-title': 'base64:' + Buffer.from('اشتراک تست').toString('base64') }); return res.end(Buffer.from(subBody).toString('base64')) }
  res.writeHead(200, { 'Content-Type': 'text/plain' }); res.end('hello-through-vpn')
})
const webPort = await freePort()
await new Promise(r => web.listen(webPort, '127.0.0.1', r))

// ---- a private Tor network, started first: its first consensus takes about a minute
let torNet = null
if (TOR && TOR_GENCERT) {
  const { startTorTestnet } = await import('./tor-testnet.mjs')
  torNet = await startTorTestnet({ tor: TOR, torGencert: TOR_GENCERT, dir: join(work, 'tornet'), freePort, lyrebird: LYREBIRD })
  children.push(...torNet.procs)
}

// ---- an Xray VLESS server (freedom → the local web server whatever the destination)
const uuid = '6f1c2f0e-2b8e-4f0a-9d6d-3c6b0c8a1e22'
const vlessPort = await freePort()
// A second VLESS inbound goes out unchanged: the "base" server Tor dials its relays through.
const plainVlessPort = await freePort()
const xrayServerCfg = join(work, 'xray-server.json')
writeFileSync(xrayServerCfg, JSON.stringify({
  log: { loglevel: 'warning' },
  inbounds: [
    { listen: '127.0.0.1', port: vlessPort, protocol: 'vless', settings: { clients: [{ id: uuid }], decryption: 'none' }, streamSettings: { network: 'tcp' } },
    { tag: 'plain', listen: '127.0.0.1', port: plainVlessPort, protocol: 'vless', settings: { clients: [{ id: uuid, email: 'plain@t' }], decryption: 'none' }, streamSettings: { network: 'tcp' } }
  ],
  outbounds: [{ protocol: 'freedom', settings: { redirect: `127.0.0.1:${webPort}` } }, { tag: 'free', protocol: 'freedom' }],
  routing: { rules: [{ type: 'field', inboundTag: ['plain'], outboundTag: 'free' }] },
  stats: {}, policy: { levels: { 0: { statsUserUplink: true } } }
}))
const xs = spawn(XRAY, ['run', '-c', xrayServerCfg], { stdio: 'ignore' }); children.push(xs)
if (!(await waitPort(vlessPort))) { fail('xray server did not start'); process.exit(1) }

// ---- a sing-box AnyTLS server (self-signed), for the sing-box engine path
let anytlsLink = ''
if (existsSync(SINGBOX)) {
  const key = join(work, 'k.pem'), crt = join(work, 'c.pem')
  execFileSync('openssl', ['req', '-x509', '-newkey', 'ec', '-pkeyopt', 'ec_paramgen_curve:prime256v1', '-nodes', '-keyout', key, '-out', crt, '-days', '2', '-subj', '/CN=test.local'], { stdio: 'ignore' })
  const atPort = await freePort()
  const sbCfg = join(work, 'sb-server.json')
  writeFileSync(sbCfg, JSON.stringify({
    log: { level: 'warn' },
    inbounds: [{ type: 'anytls', listen: '127.0.0.1', listen_port: atPort, users: [{ name: 'u', password: 'pass-123' }], tls: { enabled: true, server_name: 'test.local', certificate_path: crt, key_path: key } }],
    outbounds: [{ type: 'direct', tag: 'direct' }],
    route: { rules: [{ action: 'route-options', override_address: '127.0.0.1', override_port: webPort }], final: 'direct' }
  }))
  const sb = spawn(SINGBOX, ['run', '-c', sbCfg], { stdio: 'ignore' }); children.push(sb)
  if (await waitPort(atPort)) anytlsLink = `anytls://pass-123@127.0.0.1:${atPort}?sni=test.local&insecure=1#AnyTLS%20test`
  else fail('sing-box anytls server did not start')
}

// ---- sing-box OpenVPN servers: UDP with tls-crypt, TCP with tls-auth, both with users
const ovpn = {}
if (existsSync(SINGBOX)) {
  const pki = (name, ext, ca) => {
    const key = join(work, `${name}.key`), crt = join(work, `${name}.crt`)
    if (!ca) {
      execFileSync('openssl', ['req', '-x509', '-newkey', 'ec', '-pkeyopt', 'ec_paramgen_curve:prime256v1', '-nodes', '-keyout', key, '-out', crt, '-days', '2', '-subj', `/CN=${name}`], { stdio: 'ignore' })
    } else {
      const csr = join(work, `${name}.csr`), extFile = join(work, `${name}.ext`)
      writeFileSync(extFile, `basicConstraints=CA:FALSE\nkeyUsage=digitalSignature,keyAgreement\nextendedKeyUsage=${ext}\n`)
      execFileSync('openssl', ['req', '-newkey', 'ec', '-pkeyopt', 'ec_paramgen_curve:prime256v1', '-nodes', '-keyout', key, '-out', csr, '-subj', `/CN=${name}`], { stdio: 'ignore' })
      execFileSync('openssl', ['x509', '-req', '-in', csr, '-CA', ca.crt, '-CAkey', ca.key, '-CAcreateserial', '-out', crt, '-days', '2', '-extfile', extFile], { stdio: 'ignore' })
    }
    return { key, crt, keyText: readFileSync(key, 'utf8').trim(), crtText: readFileSync(crt, 'utf8').trim() }
  }
  const ca = pki('ovpn-ca')
  const server = pki('ovpn-server', 'serverAuth', ca)
  const client = pki('ovpn-client', 'clientAuth', ca)
  // An OpenVPN static key (openvpn --genkey secret): 2048 bits as 16 lines of hex.
  const staticKey = () => '#\n# 2048 bit OpenVPN static key\n#\n-----BEGIN OpenVPN Static key V1-----\n' + crypto.randomBytes(256).toString('hex').match(/.{32}/g).join('\n') + '\n-----END OpenVPN Static key V1-----'
  const tc = staticKey(), ta = staticKey()
  const udpPort = await freePort(), tcpPort = await freePort()
  const users = [{ username: 'ovpn-user', password: 'ovpn-pass-1' }]
  const tls = wrap => ({ certificate_path: server.crt, key_path: server.key, client_certificate_path: ca.crt, control_wrap: wrap })
  const cfg = join(work, 'ovpn-server.json')
  writeFileSync(cfg, JSON.stringify({
    log: { level: 'warn' },
    endpoints: [
      { type: 'openvpn-server', tag: 'ovpn-udp', listen: '127.0.0.1', listen_port: udpPort, network: 'udp', address: ['10.8.0.1/24'], users, auth: 'SHA256', tls: tls({ type: 'tls_crypt', key: [tc] }) },
      { type: 'openvpn-server', tag: 'ovpn-tcp', listen: '127.0.0.1', listen_port: tcpPort, network: 'tcp', address: ['10.9.0.1/24'], users, auth: 'SHA256', tls: tls({ type: 'tls_auth', key: [ta], direction: 'server' }) }
    ],
    outbounds: [{ type: 'direct', tag: 'direct' }],
    route: { rules: [{ action: 'route-options', override_address: '127.0.0.1', override_port: webPort }], final: 'direct' }
  }))
  const os = spawn(SINGBOX, ['run', '-c', cfg], { stdio: 'ignore' }); children.push(os)
  const profile = (proto, port, extra) => `# exported by a VPN panel
client
dev tun
proto ${proto}
remote 127.0.0.1 ${port}
resolv-retry infinite
nobind
persist-key
persist-tun
remote-cert-tls server
verify-x509-name ovpn-server name
cipher AES-256-CBC
auth SHA256
auth-user-pass
verb 3
${extra}
<ca>
${ca.crtText}
</ca>
<cert>
${client.crtText}
</cert>
<key>
${client.keyText}
</key>
`
  if (await waitPort(tcpPort)) {
    ovpn.udp = profile('udp', udpPort, `<tls-crypt>\n${tc}\n</tls-crypt>`)
    ovpn.tcp = profile('tcp-client', tcpPort, `key-direction 1\n<tls-auth>\n${ta}\n</tls-auth>\n<auth-user-pass>\novpn-user\novpn-pass-1\n</auth-user-pass>`)
  } else fail('sing-box openvpn server did not start')
}

// ---- a stand-in aether: a SOCKS5 proxy on --bind that records how it was started
const fakeAether = `#!/usr/bin/env node
const net = require('net'), fs = require('fs')
const args = process.argv.slice(2)
const bind = args[args.indexOf('--bind') + 1]
const i = bind.lastIndexOf(':')
const env = Object.fromEntries(Object.entries(process.env).filter(([k]) => k.startsWith('AETHER_') || k === 'HOME'))
fs.writeFileSync(process.env.HOME + '/fake-aether.json', JSON.stringify({ args, env, cwd: process.cwd() }))
process.stdin.on('data', d => fs.appendFileSync(process.env.HOME + '/fake-aether-stdin.txt', d))
console.log('Aether v2.1.0 (stand-in)')
setTimeout(() => net.createServer(c => {
  c.once('data', () => {
    c.write(Buffer.from([5, 0]))
    c.once('data', req => {
      if (req[1] !== 1 || ![1, 3, 4].includes(req[3])) { c.destroy(); return }
      // Like the test servers: wherever it was asked to go, it goes to the local web server.
      const up = net.connect(${webPort}, '127.0.0.1', () => { c.write(Buffer.from([5, 0, 0, 1, 0, 0, 0, 0, 0, 0])); up.pipe(c); c.pipe(up) })
      up.on('error', () => c.destroy()); c.on('error', () => up.destroy())
    })
  })
}).listen(Number(bind.slice(i + 1)), bind.slice(0, i)), 300)
`
writeFileSync(join(binDir, 'aether'), fakeAether)
chmodSync(join(binDir, 'aether'), 0o755)

// ---- tor in the core folder, laid out like the Tor Expert Bundle (scripts/fetch-tor.sh)
if (torNet) {
  mkdirSync(join(binDir, 'tor', 'pluggable_transports'), { recursive: true })
  symlinkSync(TOR, join(binDir, 'tor', 'tor'))
  if (LYREBIRD) symlinkSync(LYREBIRD, join(binDir, 'tor', 'pluggable_transports', 'lyrebird'))
}

const vlessLink = `vless://${uuid}@127.0.0.1:${vlessPort}?type=tcp&security=none#سرور%20تست%20🇩🇪`
const deadLink = `vless://${uuid}@127.0.0.1:${await freePort()}?type=tcp&security=none#dead`
const subBody = [vlessLink, deadLink].join('\n')

// ---- the engine
const statuses = []
const engine = new Engine({ dataDir, binDir, onChange: s => statuses.push(s), probeUrl: 'http://www.gstatic.com/generate_204' })
try {
  if (engine.available()) ok('engine sees the xray core'); else fail('engine does not see xray')
  if (engine.settings().mode === 'tun') ok('default mode is the whole device'); else fail('default mode ' + engine.settings().mode)
  // No administrator rights here: the cores are exercised in local-proxy mode.
  engine.setSettings({ mode: 'proxy' })
  const caps = engine.capabilities()
  ok('capabilities ' + JSON.stringify({ xray: caps.xray, singbox: caps.singbox, tun: caps.tun }))

  // subscription with usage headers and a base64 title
  const sub = await engine.store.addSubscription(`http://127.0.0.1:${webPort}/sub`)
  const g = engine.groups().find(x => x.id === sub.id)
  if (g && g.configs.length === 2 && g.name === 'اشتراک تست' && g.total === 10737418240 && g.used === 3000) ok(`subscription: ${g.configs.length} servers, title "${g.name}", usage parsed`)
  else fail('subscription parse ' + JSON.stringify(g && { n: g.configs.length, name: g.name, total: g.total, used: g.used }))

  // manual add of a sing-box protocol link
  if (anytlsLink) {
    const r = await engine.store.addFromText(anytlsLink)
    if (r.added === 1) ok('manual AnyTLS link added'); else fail('anytls add ' + JSON.stringify(r))
  }

  // real-delay test across engines
  const delays = await engine.testDelays(engine.store.data.configs.map(c => c.id))
  const byName = Object.fromEntries(engine.store.data.configs.map(c => [c.name, delays[c.id]]))
  if (byName['سرور تست 🇩🇪'] > 0) ok(`real delay vless: ${byName['سرور تست 🇩🇪']} ms`); else fail('vless delay ' + byName['سرور تست 🇩🇪'])
  if (byName.dead === -1) ok('dead server reported -1'); else fail('dead server delay ' + byName.dead)
  if (anytlsLink) { if (byName['AnyTLS test'] > 0) ok(`real delay anytls through sing-box: ${byName['AnyTLS test']} ms`); else fail('anytls delay ' + byName['AnyTLS test']) }

  // fastest = the live vless
  const best = await engine.fastest()
  if (best && best.name === 'سرور تست 🇩🇪') ok('fastest picked the live server'); else fail('fastest picked ' + (best && best.name))

  // connect (proxy mode) and browse through it
  const st = await engine.connect(best.id)
  if (st.connected && st.engine === 'xray' && st.socks) ok(`connected via xray on socks ${st.socks.port}, http ${st.http.port}`)
  else fail('connect ' + JSON.stringify(st))
  const body = await viaSocks(st.socks.port, 'example.com', 80, '/')
  if (/hello-through-vpn/.test(body)) ok('traffic flows through the VPN (socks)'); else fail('no traffic: ' + body.slice(0, 120))

  // extension API: forbidden for web pages, works for the extension
  const api = serve(engine)
  await new Promise(r => setTimeout(r, 200))
  const call = (p, headers = {}, method = 'GET') => fetch('http://127.0.0.1:47823' + p, { method, headers }).then(async r => ({ code: r.status, json: await r.json().catch(() => null) }))
  const web1 = await call('/status', { Origin: 'https://evil.example', 'X-Ghajar-Client': 'extension' })
  const ext = await call('/status', { 'X-Ghajar-Client': 'extension' })
  if (web1.code === 403) ok('API refuses web origins'); else fail('API web origin ' + web1.code)
  if (ext.code === 200 && ext.json.connected) ok('API status for the extension'); else fail('API status ' + JSON.stringify(ext))
  api.close()

  // switch to the sing-box engine
  if (anytlsLink) {
    const at = engine.store.data.configs.find(c => c.protocol === 'anytls')
    const st2 = await engine.connect(at.id)
    if (st2.connected && st2.engine === 'singbox') ok(`connected via sing-box (AnyTLS) on socks ${st2.socks.port}`)
    else fail('anytls connect ' + JSON.stringify(st2))
    const body2 = await viaSocks(st2.socks.port, 'example.org', 80, '/')
    if (/hello-through-vpn/.test(body2)) ok('traffic flows through sing-box AnyTLS'); else fail('anytls traffic: ' + body2.slice(0, 160))
  }

  // free configs from Telegram: a channel page with the live server, a dead one and junk
  const nowIso = new Date(Date.now() - 3600e3).toISOString().replace(/\.\d+Z$/, '+00:00')
  const page = `<html><body><div class="tgme_widget_message_wrap"><div class="tgme_widget_message js-widget_message" data-post="Ghajarvpn/50">
<div class="tgme_widget_message_text js-message_text" dir="auto">کانفیگ رایگان<br/><code>${vlessLink.replace('#', '#free-')}</code><br/><code>${deadLink}</code></div>
<div class="tgme_widget_message_footer"><a class="tgme_widget_message_date" href="https://t.me/Ghajarvpn/50"><time datetime="${nowIso}" class="time">10:00</time></a></div></div></div></body></html>`
  const feedFetch = async url => String(url).includes('/s/Ghajarvpn') && !String(url).includes('before=')
    ? new Response(page, { status: 200, headers: { 'content-type': 'text/html' } })
    : new Response('<html><body></body></html>', { status: 200, headers: { 'content-type': 'text/html' } })
  const nFree = await engine.refreshFree({ fetchImpl: feedFetch })
  const freeGroup = engine.groups().find(x => x.id === 'free')
  if (nFree === 1 && freeGroup && freeGroup.configs.length === 1 && freeGroup.configs[0].delay > 0) ok(`free configs: 1 healthy kept (dead one dropped), ${freeGroup.configs[0].delay} ms`)
  else fail('free configs ' + nFree + ' ' + JSON.stringify(freeGroup && freeGroup.configs.map(c => [c.name, c.delay])))

  // WARP with a recorded registration reply
  const reg = JSON.parse(readFileSync(join(here, 'fixtures', 'warp-reg.json'), 'utf8'))
  const warpFetch = async (url, init) => { const body = JSON.parse(init.body); return new Response(JSON.stringify({ ...reg, key: body.key }), { status: 200, headers: { 'content-type': 'application/json' } }) }
  const w = await engine.addWarp({ fetchImpl: warpFetch })
  const wg = engine.groups().find(x => x.id === 'warp')
  if (w && w.protocol === 'wireguard' && wg && wg.configs.length === 8) ok('WARP registered: 8 WireGuard endpoints in their own group')
  else fail('warp ' + JSON.stringify(wg && wg.configs.length))
  const w2 = await engine.addWarp({ fetchImpl: async () => { throw new Error('must not register twice') } })
  if (w2 && w2.id === w.id) ok('WARP account reused, ids stable'); else fail('warp re-add changed ids')
  try { require('../xray.js').buildXrayConfig(w, engine.settings(), { socksPort: 1080, httpPort: 1081 }); ok('WARP config builds for Xray') } catch (e) { fail('warp xray build ' + e.message) }

  // ---- OpenVPN: an .ovpn as the panel exports it, carried by sing-box
  if (ovpn.udp) {
    const caps2 = engine.capabilities()
    if (caps2.openvpn) ok('capabilities: openvpn (sing-box present)'); else fail('openvpn capability ' + JSON.stringify(caps2))
    const r = await engine.store.addFromText(ovpn.udp)
    const oc = engine.store.data.configs.find(c => c.protocol === 'openvpn')
    const listed = engine.groups().flatMap(g => g.configs).find(c => c.id === (oc && oc.id))
    if (r.added === 1 && oc && oc.name === 'Ghajarvpn 🌐' && listed && listed.needsCredentials === true && listed.family === 'openvpn') ok('.ovpn imported as one openvpn server, asking for credentials')
    else fail('ovpn import ' + JSON.stringify({ r, oc: oc && oc.name, listed }))
    try { await engine.connect(oc.id); fail('ovpn connected without credentials?') } catch (e) { ok('ovpn without credentials refused: ' + e.message) }
    engine.setCredentials(oc.id, 'ovpn-user', 'ovpn-pass-1')
    const so = await engine.connect(oc.id)
    if (so.connected && so.engine === 'singbox' && so.socks) ok(`connected via OpenVPN (UDP, tls-crypt) on socks ${so.socks.port}`); else fail('ovpn connect ' + JSON.stringify(so))
    const ob = await viaSocks(so.socks.port, 'example.net', 80, '/')
    if (/hello-through-vpn/.test(ob)) ok('traffic flows through OpenVPN (UDP)'); else fail('ovpn traffic: ' + ob.slice(0, 160))
    const d = await engine.testDelays([oc.id])
    if (d[oc.id] > 0) ok(`real delay OpenVPN through sing-box: ${d[oc.id]} ms`); else fail('ovpn delay ' + d[oc.id])
    engine.setCredentials(oc.id, 'ovpn-user', 'wrong')
    try { await engine.connect(oc.id); fail('wrong OpenVPN password connected') } catch (e) {
      if (/AUTH_FAILED/.test(e.message) && !engine.status().connected && engine.children.length === 0) ok('wrong OpenVPN password refused: ' + e.message); else fail('wrong password: ' + e.message)
    }
    await engine.store.addFromText(ovpn.tcp)
    const ot = engine.store.data.configs.filter(c => c.protocol === 'openvpn').at(-1)
    const st3 = await engine.connect(ot.id)
    const tb = await viaSocks(st3.socks.port, 'example.org', 80, '/')
    if (st3.engine === 'singbox' && ot.uuid === 'ovpn-user' && /hello-through-vpn/.test(tb)) ok('OpenVPN over TCP with tls-auth + key-direction and inline credentials carries traffic')
    else fail('ovpn tcp ' + tb.slice(0, 120))
  }

  // ---- Aether (stand-in binary): started like AetherController, Xray in front of its SOCKS
  {
    const ac = engine.addAether({ mode: 'masque', scan: 'turbo', http2: true, fragment: true, exitLoc: 'de' })
    if (P_family(ac) === 'aether') ok('Aether entry added (family aether)'); else fail('aether family ' + P_family(ac))
    const sa = await engine.connect(ac.id)
    const ab = await viaSocks(sa.socks.port, 'example.com', 80, '/')
    if (sa.engine === 'aether' && /hello-through-vpn/.test(ab)) ok('connected via Aether, traffic flows through its SOCKS'); else fail('aether ' + JSON.stringify(sa) + ab.slice(0, 80))
    const rec = JSON.parse(readFileSync(join(dataDir, 'aether', 'fake-aether.json'), 'utf8'))
    const want = ['--masque', '--h2', '--fragment', '--exit-loc', 'DE', '--scan', 'turbo', '--quick-reconnect', '-4']
    if (want.every(a => rec.args.includes(a)) && rec.env.AETHER_MASQUE_HTTP2 === '1' && rec.env.AETHER_MASQUE_CONFIG === join(dataDir, 'aether', 'aether-masque.toml') && rec.cwd === join(dataDir, 'aether')) ok('aether started with the app\'s arguments and identity files: ' + rec.args.join(' '))
    else fail('aether args ' + JSON.stringify(rec))
    if (engine.submitAetherCode('123456')) {
      await new Promise(r => setTimeout(r, 300))
      const got = readFileSync(join(dataDir, 'aether', 'fake-aether-stdin.txt'), 'utf8')
      if (got === '123456\n') ok('Zero Trust email code reaches aether on stdin'); else fail('aether stdin ' + JSON.stringify(got))
    } else fail('submitAetherCode found no aether')
    await engine.disconnect()
  }

  // ---- Tor on the private network: plain, through another server, over an obfs4 bridge
  if (torNet) {
    engine.setSettings({ torrcExtra: torNet.clientTorrc, iranDirect: false })
    if (engine.capabilities().tor) ok('capabilities: tor (expert-bundle layout)'); else fail('tor capability')
    const tc = engine.addTor({})
    const t0 = Date.now()
    try {
      const s1 = await engine.connect(tc.id)
      const tb = await viaSocks(s1.socks.port, '127.0.0.1', webPort, '/')
      if (s1.engine === 'tor' && /hello-through-vpn/.test(tb)) ok(`connected via Tor (bootstrapped in ${((Date.now() - t0) / 1000).toFixed(0)} s), traffic flows through it`)
      else fail('tor traffic ' + tb.slice(0, 120))
      const torrc = readFileSync(join(dataDir, 'tor', 'torrc'), 'utf8')
      if (/^SocksPort 127\.0\.0\.1:\d+$/m.test(torrc) && torrc.includes(`DataDirectory ${join(dataDir, 'tor')}`) && !/UseBridges/.test(torrc)) ok('torrc: own SOCKS port and data folder, no bridges')
      else fail('torrc ' + torrc)
    } catch (e) { fail('tor connect: ' + e.message) }
    // torThroughVpn: Tor reaches its relays through the plain VLESS server.
    await engine.store.addFromText(`vless://${uuid}@127.0.0.1:${plainVlessPort}?type=tcp&security=none#base`)
    const baseCfg = engine.store.data.configs.find(c => c.name === 'base')
    const tv = engine.addTor({ throughVpn: true, baseId: baseCfg.id, country: 'de' })
    try {
      const s2 = await engine.connect(tv.id)
      const vb = await viaSocks(s2.socks.port, '127.0.0.1', webPort, '/')
      const torrc = readFileSync(join(dataDir, 'tor', 'torrc'), 'utf8')
      const via = /^Socks5Proxy 127\.0\.0\.1:(\d+)$/m.exec(torrc)
      if (tv.torThroughVpn && tv.torBaseId === baseCfg.id && via && engine.children.length === 3 && /hello-through-vpn/.test(vb)) ok(`Tor through another server: Socks5Proxy → base core on ${via[1]}, traffic flows`)
      else fail('tor via vpn ' + JSON.stringify({ via: !!via, n: engine.children.length }) + vb.slice(0, 80))
    } catch (e) { fail('tor via vpn connect: ' + e.message) }
    const line = LYREBIRD ? await torNet.bridgeLine() : ''
    if (line) {
      const before = engine.store.data.configs.length
      await engine.store.addFromText(line)
      const bc = engine.store.data.configs[before]
      try {
        const s3 = await engine.connect(bc.id)
        const bb = await viaSocks(s3.socks.port, '127.0.0.1', webPort, '/')
        const torrc = readFileSync(join(dataDir, 'tor', 'torrc'), 'utf8')
        if (bc.protocol === 'tor' && /ClientTransportPlugin obfs4,meek_lite,webtunnel,snowflake exec \.\/lyrebird/.test(torrc) && torrc.includes('Bridge obfs4 127.0.0.1:') && /hello-through-vpn/.test(bb)) ok('Tor over an obfs4 bridge (lyrebird) carries traffic')
        else fail('tor obfs4 ' + bb.slice(0, 80))
      } catch (e) { fail('tor obfs4 connect: ' + e.message) }
    } else if (LYREBIRD) fail('the obfs4 bridge never wrote its bridge line')
    else console.log('skip Tor obfs4 bridge (no lyrebird)')
    await engine.disconnect()
  } else console.log('skip Tor end to end (no tor / tor-gencert)')

  // a broken server fails cleanly and leaves nothing running
  const dead = engine.store.data.configs.find(c => c.name === 'dead')
  await engine.disconnect()
  const before = engine.children.length
  if (before === 0) ok('disconnect stopped every core'); else fail('children left: ' + before)

  // unsupported protocol message
  const r2 = await engine.store.addFromText('ikev2://user:pass@vpn.example.com:500#ike')
  const ike = engine.store.data.configs.find(c => c.protocol === 'ikev2')
  if (r2.added && ike) {
    if (engine.planFor(ike).engine === 'ikev2') ok('ikev2 goes to the OS VPN client'); else fail('ikev2 plan')
    // Here (Linux without NetworkManager-strongswan) the OS client is missing.
    try { await engine.connect(ike.id); fail('ikev2 connected?') } catch (e) { ok('ikev2 refused with: ' + e.message) }
  }
  if (!engine.status().connected && engine.children.length === 0) ok('a failed connect leaves nothing running')
  else fail('state after failed connect ' + JSON.stringify(engine.status()))
  if (dead) ok('dead server kept in the list (marked -1)')
} catch (e) {
  fail('exception: ' + (e.stack || e))
} finally {
  await engine.disconnect(true)
  if (torNet) torNet.stop()
  cleanup()
  web.close()
  rmSync(work, { recursive: true, force: true })
}
console.log(failures ? `\n${failures} failure(s)` : '\nengine: all checks passed')
process.exit(failures ? 1 : 0)
