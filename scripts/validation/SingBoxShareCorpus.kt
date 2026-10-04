import net.gozar.app.engine.SingBoxConfig
fun main() { print(SingBoxConfig.full("""{"outbound":{"type":"socks","tag":"proxy","server":"127.0.0.1","server_port":1082}}""",1080,sharingPort=1081)) }
