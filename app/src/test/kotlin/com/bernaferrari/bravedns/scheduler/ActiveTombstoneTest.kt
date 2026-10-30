package com.bernaferrari.bravedns.scheduler

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class ActiveTombstoneTest {
    @Test fun `rotation never deletes empty active native crash output`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = requireNotNull(EnhancedBugReport.newGoCrashFile(context))
        try {
            EnhancedBugReport.enforceMaxFiles(context)
            assertTrue(file.isFile)
        } finally { EnhancedBugReport.markFileAsClosed(file.name); file.delete() }
    }
    @Test fun `closed native output can be pruned and filenames never collide`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val files = List(10) { requireNotNull(EnhancedBugReport.newGoCrashFile(context)) }
        try {
            org.junit.Assert.assertEquals(10, files.map { it.name }.toSet().size)
            files.forEach { EnhancedBugReport.markFileAsClosed(it.name) }
            EnhancedBugReport.enforceMaxFiles(context)
            files.forEach { org.junit.Assert.assertFalse(it.exists()) }
        } finally { files.forEach { EnhancedBugReport.markFileAsClosed(it.name); it.delete() } }
    }
}
