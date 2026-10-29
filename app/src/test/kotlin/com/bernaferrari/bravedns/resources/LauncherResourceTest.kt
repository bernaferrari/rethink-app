package com.bernaferrari.bravedns.resources

import android.app.Application
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.bernaferrari.bravedns.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28, 33])
class LauncherResourceTest {
    @Test
    fun `in app icon alias resolves to a bitmap usable by Compose`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(ContextCompat.getDrawable(context, R.drawable.ic_launcher) is BitmapDrawable)
        assertTrue(ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground) is BitmapDrawable)
        assertNotNull(ContextCompat.getDrawable(context, R.mipmap.ic_launcher))
    }

    @Test
    @Config(sdk = [24])
    fun `legacy launcher resolves below adaptive icon support`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(ContextCompat.getDrawable(context, R.mipmap.ic_launcher) is BitmapDrawable)
    }
}
