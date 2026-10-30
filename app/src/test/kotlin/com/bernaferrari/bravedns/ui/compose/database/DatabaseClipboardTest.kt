package com.bernaferrari.bravedns.ui.compose.database

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class DatabaseClipboardTest {
    @Test
    fun copyPreservesDatabaseTextAndShowsLocalizedConfirmation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val value = "id\tname\n1\tCafé\n"
        context.copyToClipboard("Firewall", value, "Copié")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("Firewall", clipboard.primaryClip!!.description.label)
        assertEquals(value, clipboard.primaryClip!!.getItemAt(0).text)
        assertEquals("Copié", ShadowToast.getTextOfLatestToast())
    }
}
