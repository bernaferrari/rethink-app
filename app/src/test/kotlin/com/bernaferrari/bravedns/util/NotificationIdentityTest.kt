package com.bernaferrari.bravedns.util

import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [31])
class NotificationIdentityTest {
    @Test fun sensitiveActionsRequireUnlockAndTapIntentsStayDistinct() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, android.app.Activity::class.java)
        val first = Utilities.getActivityPendingIntent(context, intent, PendingIntent.FLAG_IMMUTABLE, false, 11)
        val second = Utilities.getActivityPendingIntent(context, intent, PendingIntent.FLAG_IMMUTABLE, false, 12)
        assertNotEquals(first, second)
        assertTrue(Utilities.notificationAction("Allow", first).isAuthenticationRequired)
    }
}
