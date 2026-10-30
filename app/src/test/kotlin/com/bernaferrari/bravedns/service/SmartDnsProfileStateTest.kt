package com.bernaferrari.bravedns.service

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.bernaferrari.bravedns.data.SmartDnsApplyStatus
import com.bernaferrari.bravedns.data.SmartDnsProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class SmartDnsProfileStateTest {
    @Test fun preferenceSnapshotRestoresTheProfileWithoutSerializingRuntimeStatus() = runTest {
        val state = PersistentState(ApplicationProvider.getApplicationContext<Application>())
        assertEquals(SmartDnsApplyStatus.Saved, state.smartDnsApplyStatus.value)
        state.smartDnsProfile = SmartDnsProfile.Family.value
        val owner = Any()
        state.claimSmartDnsStatus(owner)
        state.publishSmartDnsStatus(owner, SmartDnsApplyStatus.Active(SmartDnsProfile.Family))
        val snapshot = state.snapshotPreferences()
        assertEquals(SmartDnsProfile.Family.value, snapshot[PersistentState.SMART_DNS_PROFILE])
        assertFalse(snapshot.values.any { it is SmartDnsApplyStatus })
        state.smartDnsProfile = SmartDnsProfile.None.value
        state.restorePreferencesAndAwait(snapshot)
        assertEquals(SmartDnsProfile.Family.value, state.smartDnsProfile)
        state.releaseSmartDnsStatus(owner)
        assertEquals(SmartDnsApplyStatus.Saved, state.smartDnsApplyStatus.value)
    }

    @Test fun oldSessionCannotPublishCompletionOrClearReplacementStatus() {
        val state = PersistentState(ApplicationProvider.getApplicationContext<Application>())
        val previous = Any()
        val current = Any()
        state.claimSmartDnsStatus(previous)
        state.claimSmartDnsStatus(current)
        val active = SmartDnsApplyStatus.Active(SmartDnsProfile.Family)
        state.publishSmartDnsStatus(current, active)
        state.publishSmartDnsStatus(previous, SmartDnsApplyStatus.Active(SmartDnsProfile.None))
        state.releaseSmartDnsStatus(previous)
        assertEquals(active, state.smartDnsApplyStatus.value)
        state.releaseSmartDnsStatus(current)
        state.queueSmartDnsProfile(SmartDnsProfile.Family)
        assertEquals(SmartDnsApplyStatus.Saved, state.smartDnsApplyStatus.value)
    }
}
