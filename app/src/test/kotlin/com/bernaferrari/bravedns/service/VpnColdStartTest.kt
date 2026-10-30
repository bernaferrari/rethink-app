package com.bernaferrari.bravedns.service

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class VpnColdStartTest {
    @Test fun savedEnabledDoesNotBlockExplicitStartOrMakeStopBeforeOnCreateIneffective() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val preferences = PersistentState(context)
        preferences.setVpnEnabled(true)
        startKoin { modules(module { single { preferences } }) }
        val service = mockk<BraveVPNService>(relaxed = true)
        var created = false
        try {
            assertTrue(VpnController.state().activationRequested)
            assertFalse(VpnController.sessionActive.value)
            VpnController.start(context)
            assertTrue(VpnController.sessionActive.value)
            assertEquals(BraveVPNService::class.java.name, shadowOf(context).nextStartedService.component?.className)
            VpnController.start(context)
            assertNull(shadowOf(context).nextStartedService)
            VpnController.stop("cancel pending test start", context)
            assertFalse(VpnController.sessionActive.value)
            assertFalse(preferences.getVpnEnabled())
            VpnController.onVpnCreated(service)
            created = true
            assertFalse(VpnController.mayStart(service))
            assertFalse(VpnController.sessionActive.value)
        } finally {
            if (!created) VpnController.onVpnCreated(service)
            VpnController.onVpnDestroyed(service)
            VpnController.onVpnTeardownComplete(service)
            stopKoin()
            unmockkAll()
        }
    }
}
