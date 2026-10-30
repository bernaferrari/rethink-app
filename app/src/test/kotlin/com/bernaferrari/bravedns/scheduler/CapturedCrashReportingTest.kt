package com.bernaferrari.bravedns.scheduler

import android.app.Application
import com.bernaferrari.bravedns.util.FirebaseErrorReporting
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class CapturedCrashReportingTest {
    @get:Rule val directory = TemporaryFolder()
    @After fun cleanup() = unmockkAll()

    @Test fun `SDK rejection is not treated as successfully reported crash`() {
        mockkObject(FirebaseErrorReporting)
        every { FirebaseErrorReporting.log(any()) } just runs
        every { FirebaseErrorReporting.tryRecordException(any()) } returns false
        assertFalse(sendCrash())
    }

    @Test fun `accepted report contains captured stack frames`() {
        mockkObject(FirebaseErrorReporting)
        val captured = slot<Throwable>()
        every { FirebaseErrorReporting.log(any()) } just runs
        every { FirebaseErrorReporting.tryRecordException(capture(captured)) } returns true
        assertTrue(sendCrash())
        assertEquals("example.Work", captured.captured.stackTrace.single().className)
        assertEquals(42, captured.captured.stackTrace.single().lineNumber)
    }

    private fun sendCrash(): Boolean {
        val file = directory.newFile("kotlin_123.txt")
        file.writeText("java.lang.IllegalStateException: failed\n\tat example.Work.run(Work.kt:42)")
        val method = EnhancedBugReport::class.java.getDeclaredMethod("sendFileToFirebase", java.io.File::class.java)
        method.isAccessible = true
        return method.invoke(EnhancedBugReport, file) as Boolean
    }
}
