package com.bernaferrari.bravedns.receiver

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class VpnControlReceiverIdentityTest {
    @Test
    @Config(sdk = [34])
    fun `identity extras cannot replace missing platform identity on Android 14`() {
        assertUnverifiedIdentityRejected()
    }

    @Test
    fun `unverified identity extras cannot authenticate on older Android`() {
        assertUnverifiedIdentityRejected()
    }

    private fun assertUnverifiedIdentityRejected() {
        val receiver = VpnControlReceiver()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val method = VpnControlReceiver::class.java.getDeclaredMethod(
            "getCallerPkg", Context::class.java, Intent::class.java
        ).apply { isAccessible = true }
        val intent = Intent().apply {
            putExtra("sender", context.packageName)
            putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
            putExtra(Intent.EXTRA_INTENT, context.packageName)
        }
        assertNull(method.invoke(receiver, context, intent))
    }
}
