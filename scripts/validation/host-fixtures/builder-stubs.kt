package net.gozar.app
object TorController { const val CONTROL_PORT=9151;const val BRIDGE_PORT=10627;const val SOCKS_PORT=9150 }
object PsiphonController { const val SOCKS_PORT=1082 }
object Warp { const val WARP_ENDPOINT_HOST="engage.cloudflareclient.com";const val WARP_ENDPOINT_PORT=2408 }
object CertPin { fun isValid(v:String)=v.matches(Regex("[a-fA-F0-9]{64}")) }
object GhajarLog { fun i(tag:String,msg:String) {} }
