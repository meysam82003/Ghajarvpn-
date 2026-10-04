package net.gozar.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityRegistryTest {

    @Test fun everyEngineHasADescriptorAndAllAreProtected() {
        EngineId.entries.forEach { id -> assertTrue(id.name, CapabilityRegistry.core(id).protected) }
        assertEquals(EngineId.entries.size, CapabilityRegistry.CORES.size)
    }

    @Test fun onlyRealCapabilitiesAreClaimed() {
        assertTrue(CapabilityRegistry.has("vless", Capability.REALITY))
        assertTrue(CapabilityRegistry.has("vless", Capability.XHTTP))
        assertFalse(CapabilityRegistry.has("hysteria2", Capability.PORT_HOPPING))
        assertFalse(CapabilityRegistry.has("trojan", Capability.REALITY))
        assertTrue(CapabilityRegistry.has("openvpn", Capability.FULL_CONFIG))
    }

    @Test fun removedCoresClaimNothing() {
        RemovedCores.PROTOCOLS.forEach { assertTrue(it, CapabilityRegistry.forProtocol(it).isEmpty()) }
    }
}
