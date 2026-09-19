package net.gozar.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the bug: ConfigBuilder used to bind VPN Share's inbounds
 * (socks-in when authenticated, http-share-in always) to the wildcard
 * "0.0.0.0" whenever sharing was on, which also binds the cellular data
 * interface - a real, non-hypothetical exposure on carriers/networks that
 * hand a phone a routable address there. The fix binds to this specific
 * allow-listed hotspot interface name instead (see VpnShareAddress.kt);
 * these tests pin down which names are and are not treated as "the
 * hotspot," since that's the entire security boundary now.
 */
class VpnShareAddressTest {
    @Test fun recognizedHotspotInterfaceNamesAcrossCommonOems() {
        listOf("ap0", "ap1", "wlan1", "swlan0", "p2p-swlan0-0").forEach {
            assertTrue(it, isHotspotInterfaceName(it))
        }
    }

    @Test fun cellularDataInterfacesAreNeverTreatedAsTheHotspot() {
        listOf("rmnet0", "rmnet_data0", "rmnet_ipa0", "ccmni0", "pdp_ip0", "v4-rmnet0").forEach {
            assertFalse(it, isHotspotInterfaceName(it))
        }
    }

    @Test fun theNormalWifiStationInterfaceIsNotTreatedAsTheHotspot() {
        // wlan0 is the phone acting as a Wi-Fi *client*; sharing there would
        // be sharing onto someone else's network, not this phone's own
        // hotspot, so it must not match (only wlan1+ does, the AP radio).
        assertFalse(isHotspotInterfaceName("wlan0"))
    }

    @Test fun looseningTheseNamesMustBeDeliberate() {
        // Documents the exact contract other interface names must not
        // accidentally start matching if this list is ever edited.
        listOf("eth0", "lo", "tun0", "ppp0", "usb0").forEach {
            assertFalse(it, isHotspotInterfaceName(it))
        }
    }
}
