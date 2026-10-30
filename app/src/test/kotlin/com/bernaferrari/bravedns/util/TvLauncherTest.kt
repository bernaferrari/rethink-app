package com.bernaferrari.bravedns.util

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class TvLauncherTest {
    @Test fun televisionAndPhoneLaunchThroughTheSamePersistentAlias() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val manager = context.packageManager
        fun launcher(category: String) = manager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(context.packageName), 0)
            .map { it.activityInfo.name }
        val tv = launcher(Intent.CATEGORY_LEANBACK_LAUNCHER)
        assertEquals(launcher(Intent.CATEGORY_LAUNCHER), tv)
        assertTrue(tv.single().endsWith("LauncherAliasAppLock"))
        assertNotNull(context.applicationInfo.loadBanner(manager))
    }
}
