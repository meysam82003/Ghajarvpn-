// A private Tor network on 127.0.0.1 for the engine test: one directory
// authority and two more relays, all exits (TestingTorNetwork, the setup
// chutney's "basic-min" network uses). The real Tor network is not reachable
// from a test machine; this one lets the desktop engine's tor bootstrap to
// 100% and carry traffic for real.
//
//   const net = await startTorTestnet({ tor, torGencert, dir, freePort })
//   net.clientTorrc   lines the client needs (TestingTorNetwork + DirAuthority)
//   net.stop()
import { spawn, execFileSync } from 'node:child_process'
import { mkdirSync, writeFileSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

const TESTING = [
  'TestingTorNetwork 1',
  'PathsNeededToBuildCircuits 0.25',
  'TestingDirAuthVoteExit *',
  'TestingDirAuthVoteGuard *',
  'TestingDirAuthVoteHSDir *',
  'TestingMinExitFlagThreshold 0',
  'V3AuthNIntervalsValid 2',
  'TestingV3AuthInitialVotingInterval 10',
  'TestingV3AuthInitialVoteDelay 2',
  'TestingV3AuthInitialDistDelay 2',
  'V3AuthVotingInterval 20',
  'V3AuthVoteDelay 4',
  'V3AuthDistDelay 4'
]

export async function startTorTestnet({ tor = 'tor', torGencert = 'tor-gencert', dir, freePort, lyrebird = '' }) {
  const procs = []
  const nodes = []
  for (let i = 0; i < 3; i++) nodes.push({ name: `test${i}`, dir: join(dir, `relay${i}`), orPort: await freePort(), dirPort: await freePort() })
  // With lyrebird: a fourth relay that is an unpublished obfs4 bridge.
  if (lyrebird) nodes.push({ name: 'bridge0', dir: join(dir, 'bridge0'), orPort: await freePort(), dirPort: await freePort(), bridge: true, ptPort: await freePort() })
  for (const n of nodes) mkdirSync(join(n.dir, 'keys'), { recursive: true, mode: 0o700 })
  const auth = nodes[0]
  // The authority's v3 identity, then every relay's identity fingerprint.
  execFileSync(torGencert, ['--create-identity-key', '-m', '12', '-a', `127.0.0.1:${auth.dirPort}`,
    '-i', join(auth.dir, 'keys', 'authority_identity_key'), '-s', join(auth.dir, 'keys', 'authority_signing_key'),
    '-c', join(auth.dir, 'keys', 'authority_certificate'), '--passphrase-fd', '0'], { input: '\n', stdio: ['pipe', 'ignore', 'ignore'] })
  const v3 = /fingerprint (\w+)/.exec(readFileSync(join(auth.dir, 'keys', 'authority_certificate'), 'utf8'))[1]
  for (const n of nodes) {
    const out = execFileSync(tor, ['--list-fingerprint', '--DataDirectory', n.dir, '--ORPort', String(n.orPort), '--Nickname', n.name, '--Address', '127.0.0.1', '--quiet'], { encoding: 'utf8' })
    n.fp = out.trim().split(/\s+/).slice(1).join('')
  }
  const dirAuth = `DirAuthority ${auth.name} orport=${auth.orPort} no-v2 v3ident=${v3} 127.0.0.1:${auth.dirPort} ${auth.fp}`
  for (const n of nodes) {
    const torrc = [
      ...TESTING, dirAuth,
      `DataDirectory ${n.dir}`, `Nickname ${n.name}`, 'Address 127.0.0.1', `ORPort 127.0.0.1:${n.orPort}`, `DirPort 127.0.0.1:${n.dirPort}`,
      'SocksPort 0', 'AssumeReachable 1', 'ExitRelay 1', 'ExitPolicy accept *:*', 'ExitPolicyRejectPrivate 0', 'ExitPolicyRejectLocalInterfaces 0',
      'ContactInfo test@localhost', 'Log notice stdout', 'ShutdownWaitLength 0',
      ...(n === auth ? ['AuthoritativeDirectory 1', 'V3AuthoritativeDirectory 1'] : []),
      ...(n.bridge ? ['BridgeRelay 1', 'PublishServerDescriptor 0', `ServerTransportPlugin obfs4 exec ${lyrebird}`,
        `ServerTransportListenAddr obfs4 127.0.0.1:${n.ptPort}`, 'ExtORPort auto'] : [])
    ]
    writeFileSync(join(n.dir, 'torrc'), torrc.join('\n') + '\n')
    const p = spawn(tor, ['-f', join(n.dir, 'torrc')], { stdio: ['ignore', 'pipe', 'pipe'] })
    p.log = ''
    p.stdout.on('data', d => { p.log = (p.log + d).slice(-4000) })
    procs.push(p)
  }
  // The bridge line lyrebird writes once it has its keys.
  const bridgeLine = async () => {
    const b = nodes.find(n => n.bridge)
    if (!b) return ''
    for (let i = 0; i < 100; i++) {
      try {
        const cert = /cert=(\S+)/.exec(readFileSync(join(b.dir, 'pt_state', 'obfs4_bridgeline.txt'), 'utf8'))
        if (cert) return `obfs4 127.0.0.1:${b.ptPort} ${b.fp} cert=${cert[1]} iat-mode=0`
      } catch { /* not yet */ }
      await new Promise(r => setTimeout(r, 200))
    }
    return ''
  }
  return {
    bridgeLine,
    clientTorrc: [...TESTING.filter(l => !/^(TestingDirAuth|TestingMinExit|V3Auth|TestingV3Auth)/.test(l)), dirAuth].join('\n'),
    procs,
    stop() { for (const p of procs) { try { p.kill('SIGKILL') } catch { /* gone */ } } }
  }
}
