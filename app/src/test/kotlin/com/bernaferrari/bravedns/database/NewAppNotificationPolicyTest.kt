package com.bernaferrari.bravedns.database

import android.content.pm.ApplicationInfo
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = android.app.Application::class, sdk = [28])
class NewAppNotificationPolicyTest {
    @Test fun onlyRealNewAppsPromptWhenExemptComponentsAreInserted() {
        fun app(uid: Int, system: Boolean = false) = ApplicationInfo().apply {
            this.uid = uid
            flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0
        }
        assertFalse(NewAppNotificationPolicy.eligible("keepout", "keepout", app(10001)))
        assertFalse(NewAppNotificationPolicy.eligible("unknown", "keepout", null))
        assertFalse(NewAppNotificationPolicy.eligible("system", "keepout", app(1000, true)))
        assertTrue(NewAppNotificationPolicy.eligible("preinstalled", "keepout", app(10002, true)))
        assertTrue(NewAppNotificationPolicy.eligible("installed", "keepout", app(10003)))
        assertTrue(NewAppNotificationPolicy.eligible("work-profile", "keepout", app(110002, true)))
    }
}
