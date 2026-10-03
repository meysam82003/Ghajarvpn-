package net.gozar.app.validation

import net.gozar.app.ConfigParser
import net.gozar.app.ConfigBuilder
import net.gozar.app.ProxyConfig
import java.io.File

/** No test-specific schema adapter: use the product's exact import/save/generator path. */
fun main(args: Array<String>) {
    val config=ConfigParser.parseBundle(File(args[0]).readText()).single()
    check(config.protocol=="wireguard")
    val restored=ProxyConfig.fromJson(config.toJson())
    check(config==restored)
    File(args[1]).writeText(ConfigBuilder.buildForTest(restored))
}
