package com.bernaferrari.bravedns.ui.compose.firewall

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.bernaferrari.bravedns.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class RulesImportStringsTest {
    private val resources get() = ApplicationProvider.getApplicationContext<Context>().let { context ->
        val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        context.createConfigurationContext(configuration).resources
    }

    @Test
    fun previewFormatsThreeIndependentQuantities() {
        val strings = resources
        assertEquals("1 valid rule · 2 duplicates · 0 invalid rules", strings.getString(
            R.string.port_import_preview,
            strings.getQuantityString(R.plurals.port_import_valid, 1, 1),
            strings.getQuantityString(R.plurals.port_import_duplicates, 2, 2),
            strings.getQuantityString(R.plurals.port_import_invalid, 0, 0),
        ))
    }

    @Test
    fun summarySelectsImportedAndSkippedGrammarIndependently() {
        val strings = resources
        assertEquals("Imported 1 rule; skipped 2 existing rules.", strings.getString(
            R.string.port_import_summary,
            strings.getQuantityString(R.plurals.port_import_imported, 1, 1),
            strings.getQuantityString(R.plurals.port_import_skipped, 2, 2),
        ))
        assertEquals("Imported 2 rules; skipped 1 existing rule.", strings.getString(
            R.string.port_import_summary,
            strings.getQuantityString(R.plurals.port_import_imported, 2, 2),
            strings.getQuantityString(R.plurals.port_import_skipped, 1, 1),
        ))
    }
}
