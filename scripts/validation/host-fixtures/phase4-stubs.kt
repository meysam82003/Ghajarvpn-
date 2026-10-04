package net.gozar.app.engine
object DnsTunnelPrefs { val current = Values(); class Values { val remoteDns = "1.1.1.1" } }
object Sidecars { val SSH_MODES = setOf("direct", "payload", "tls", "payload-tls", "ws", "wss", "http-proxy", "https-proxy") }
