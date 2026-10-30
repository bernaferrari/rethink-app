package com.bernaferrari.bravedns.service

import com.bernaferrari.bravedns.database.WgConfigFilesImmutable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class WgConfigRegistrySnapshotTest {
    private fun mapping() = WgConfigFilesImmutable(1, "wg", "", "", isActive = true,
        isCatchAll = false, isLockdown = false, oneWireGuard = true, useOnlyOnMetered = false,
        isDeletable = true, ssidEnabled = false, ssids = "")

    @Test fun `updating split info never removes the active one-wg from concurrent readers`() {
        val registry = WgConfigRegistry()
        registry.putMapping(mapping())
        val previous = registry.mappings
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val update = executor.submit {
                registry.replaceMapping(1) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    it.copy(isSplitProxy = true)
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertTrue(registry.oneWireGuardEnabled())
            assertEquals(1, registry.oneWireGuardId())
            assertNotNull(registry.findMapping(1))
            release.countDown()
            update.get(5, TimeUnit.SECONDS)
            assertTrue(registry.findMapping(1)!!.isSplitProxy)
            assertFalse(previous.single().isSplitProxy)
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun `identical imported configs stay addressable under their distinct ids`() {
        val key = object : com.bernaferrari.bravedns.wireguard.WgKeyHandle {
            override fun base64() = "test-key"
            override fun hex() = "test-key"
            override fun mult() = this
        }
        val iface = com.bernaferrari.bravedns.wireguard.WgInterface.Builder()
            .setKeyPair(com.bernaferrari.bravedns.wireguard.KeyPair(key)).build()
        val first = com.bernaferrari.bravedns.wireguard.Config.Builder().setId(1).setInterface(iface).build()
        val second = com.bernaferrari.bravedns.wireguard.Config.Builder().setId(2).setInterface(iface).build()
        assertEquals(first, second) // content equality deliberately excludes ID
        val registry = WgConfigRegistry()
        registry.putConfig(first)
        registry.putConfig(second)
        assertEquals(2, registry.configCount())
        assertNotNull(registry.findConfig(1))
        assertNotNull(registry.findConfig(2))
    }

    @Test fun `replacement publishes a complete new set and preserves earlier snapshots`() {
        val registry = WgConfigRegistry()
        registry.putMapping(mapping())
        val previous = registry.mappings
        registry.replaceMappings(listOf(mapping().copy(isSplitProxy = true), mapping().copy(id = 2)))
        assertEquals(1, previous.size)
        assertFalse(previous.single().isSplitProxy)
        assertEquals(setOf(1, 2), registry.allMappings().map { it.id }.toSet())
    }
}
