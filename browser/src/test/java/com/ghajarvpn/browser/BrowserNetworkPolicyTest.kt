package com.ghajarvpn.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserNetworkPolicyTest {
    @Test
    fun browserOnly_requires_reachable_proxy_and_webview_support() {
        assertFalse(BrowserNetworkPolicy.decide(BrowserNetworkMode.BROWSER_ONLY, true, false, true).ready)
        assertFalse(BrowserNetworkPolicy.decide(BrowserNetworkMode.BROWSER_ONLY, true, true, false).ready)
        val ready = BrowserNetworkPolicy.decide(BrowserNetworkMode.BROWSER_ONLY, true, true, true)
        assertTrue(ready.ready)
        assertTrue(ready.useLocalProxy)
    }

    @Test
    fun fullDevice_prefers_local_proxy_for_the_excluded_app_uid() {
        val route = BrowserNetworkPolicy.decide(BrowserNetworkMode.FULL_DEVICE, true, true, true)
        assertTrue(route.ready)
        assertTrue(route.useLocalProxy)
    }

    @Test
    fun fullDevice_fails_closed_when_no_vpn_route_exists() {
        val route = BrowserNetworkPolicy.decide(BrowserNetworkMode.FULL_DEVICE, false, false, true)
        assertFalse(route.ready)
        assertFalse(route.useLocalProxy)
    }

    @Test
    fun direct_never_requires_the_local_proxy() {
        val route = BrowserNetworkPolicy.decide(BrowserNetworkMode.DIRECT, false, false, false)
        assertTrue(route.ready)
        assertFalse(route.useLocalProxy)
    }
}
