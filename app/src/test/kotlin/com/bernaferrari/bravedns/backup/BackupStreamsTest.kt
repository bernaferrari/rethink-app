package com.bernaferrari.bravedns.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupStreamsTest {
    @Test fun `input closes when provider refuses or throws opening destination`() = runTest {
        val refused = TrackingInput()
        assertFalse(copyBackupStream(refused) { null })
        assertTrue(refused.closed)
        val failed = TrackingInput()
        try { copyBackupStream(failed) { throw IOException("provider failed") } } catch (_: IOException) { }
        assertTrue(failed.closed)
    }

    @Test fun `cancellation while copying closes both streams`() = runTest {
        lateinit var task: Job
        val input = TrackingInput { task.cancel() }
        var outputClosed = false
        val output = object : ByteArrayOutputStream() {
            override fun close() { outputClosed = true; super.close() }
        }
        task = launch(start = CoroutineStart.LAZY) { copyBackupStream(input) { output } }
        task.start()
        task.join()
        assertTrue(task.isCancelled)
        assertTrue(input.closed)
        assertTrue(outputClosed)
    }

    private class TrackingInput(private val onRead: () -> Unit = {}) : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
        var closed = false
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            onRead()
            return super.read(buffer, offset, length)
        }
        override fun close() { closed = true; super.close() }
    }
}
