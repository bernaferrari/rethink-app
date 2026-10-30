package com.bernaferrari.bravedns.util

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28, 29, 33])
class SsidPermissionManagerTest {
    @Test fun foregroundAccessDoesNotImplyBackgroundAccessOnAndroidTenOrLater() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE)
        assertTrue(SsidPermissionManager.hasRequiredPermissions(app))
        assertEquals(Build.VERSION.SDK_INT < 29, SsidPermissionManager.hasBackgroundAccess(app))
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        assertTrue(SsidPermissionManager.hasBackgroundAccess(app))
    }
}
