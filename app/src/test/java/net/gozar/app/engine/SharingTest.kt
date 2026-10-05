package net.gozar.app.engine

import net.gozar.app.ConfigParser
import net.gozar.app.ProxyConfig
import net.gozar.app.sharing.PhoneShare
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharingTest {


    @Test fun phoneShareOnlyWhereXrayCarriesTheSession() {
        assertTrue(PhoneShare.supports(ConfigParser.parse("vless://11111111-2222-3333-4444-555555555555@a.example.com:443?security=tls#a")!!))
        assertTrue(PhoneShare.supports(ConfigParser.parse("tuic://u:p@t.example.com:443#t")!!))
        assertFalse(PhoneShare.supports(ProxyConfig(name = "i", protocol = "ikev2", address = "h", port = 500)))
        assertFalse(PhoneShare.supports(ProxyConfig(name = "j", protocol = "juicity", address = "h", port = 443)))
    }
}
