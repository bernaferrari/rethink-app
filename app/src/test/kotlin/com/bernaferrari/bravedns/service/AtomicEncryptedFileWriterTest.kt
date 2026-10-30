package com.bernaferrari.bravedns.service

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AtomicEncryptedFileWriterTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `staging preserves logical filename and replaces only after stream closure and sync`() {
        val target = target()
        var closed = false
        var synced = false
        writer(open = { staged ->
            assertEquals(target.name, staged.name)
            assertNotEquals(target.parentFile, staged.parentFile)
            object : OutputStream() {
                private val output = staged.outputStream()
                override fun write(value: Int) = output.write(value)
                override fun close() { output.close(); closed = true }
            }
        }, sync = { assertTrue(closed); synced = true }, replace = { from, to ->
            assertTrue(synced)
            assertEquals("old", to.readText())
            move(from, to)
        }).write(target, "new".toByteArray())
        assertEquals("new", target.readText())
        assertStagingEmpty()
    }

    @Test fun `partial stream write failure closes it and preserves old bytes`() {
        val target = target()
        var closed = false
        val failing = writer(open = { staged -> object : OutputStream() {
            private val output = staged.outputStream()
            override fun write(value: Int) { output.write(value); throw IOException("write failure") }
            override fun close() { output.close(); closed = true }
        } })
        expectFailure { failing.write(target, "new".toByteArray()) }
        assertTrue(closed)
        assertEquals("old", target.readText())
        assertStagingEmpty()
    }

    @Test fun `close failure leaves old bytes and never commits`() {
        val target = target()
        val failing = writer(open = { staged -> object : OutputStream() {
            private val output = staged.outputStream()
            override fun write(value: Int) = output.write(value)
            override fun close() { output.close(); throw IOException("close failure") }
        } })
        expectFailure { failing.write(target, "new".toByteArray()) }
        assertEquals("old", target.readText())
        assertStagingEmpty()
    }

    @Test fun `sync and atomic replacement failures preserve old bytes`() {
        for (failSync in listOf(true, false)) {
            val target = target()
            val failing = if (failSync) writer(sync = { throw IOException("sync failure") })
                else writer(replace = { _, _ -> throw IOException("rename failure") })
            expectFailure { failing.write(target, "new".toByteArray()) }
            assertEquals("old", target.readText())
            assertStagingEmpty()
        }
    }

    @Test fun `failed first write leaves no empty final configuration`() {
        val target = File(temporary.root, "new.conf")
        expectFailure { writer(replace = { _, _ -> throw IOException("rename failure") }).write(target, byteArrayOf(1)) }
        assertFalse(target.exists())
        assertStagingEmpty()
    }

    @Test fun `different writer instances serialize the same target`() {
        val target = target()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit { writer(open = { staged ->
                entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); staged.outputStream()
            }).write(target, "first".toByteArray()) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val second = executor.submit {
                secondStarted.countDown()
                writer(open = { secondEntered.countDown(); it.outputStream() }).write(target, "second".toByteArray())
            }
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS))
            assertFalse(secondEntered.await(150, TimeUnit.MILLISECONDS))
            release.countDown()
            first.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS)
            assertEquals("second", target.readText())
            assertStagingEmpty()
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun `cleanup and diagnostic failures cannot turn a committed replacement into failure`() {
        val target = target()
        AtomicEncryptedFileWriter(File(temporary.root, "staging"), { it.outputStream() }, {}, ::move,
            cleanup = { throw IOException("Cleanup failure") },
            cleanupFailed = { throw IllegalStateException("Diagnostic failure") },
        ).write(target, "committed".toByteArray())
        assertEquals("committed", target.readText())
    }

    @Test fun `cleanup and diagnostic failures cannot mask the original write failure`() {
        val target = target()
        val original = IOException("Original failure")
        val writer = AtomicEncryptedFileWriter(File(temporary.root, "staging"), { throw original }, {}, ::move,
            cleanup = { throw SecurityException("Cleanup failure") },
            cleanupFailed = { throw IllegalStateException("Diagnostic failure") },
        )
        try { writer.write(target, "new".toByteArray()); fail("Expected original failure") }
        catch (actual: IOException) { assertSame(original, actual) }
        assertEquals("old", target.readText())
    }

    @Test fun `another writer creating the shared parent concurrently is accepted`() {
        val staging = object : File(temporary.root, "staging") {
            override fun mkdirs(): Boolean { super.mkdirs(); return false }
        }
        val target = target()
        AtomicEncryptedFileWriter(staging, { it.outputStream() }, {}, ::move).write(target, "new".toByteArray())
        assertEquals("new", target.readText())
        assertStagingEmpty()
    }

    private fun target(): File = File(temporary.root, "config.conf").apply { writeText("old") }
    private fun assertStagingEmpty() = assertTrue(requireNotNull(File(temporary.root, "staging").list()).isEmpty())
    private fun move(from: File, to: File) { Files.move(from.toPath(), to.toPath(), ATOMIC_MOVE, REPLACE_EXISTING) }
    private fun writer(
        open: (File) -> OutputStream = { it.outputStream() },
        sync: (File) -> Unit = {},
        replace: (File, File) -> Unit = ::move,
    ) = AtomicEncryptedFileWriter(File(temporary.root, "staging"), open, sync, replace)
    private fun expectFailure(block: () -> Unit) {
        try { block(); fail("Expected write failure") } catch (_: IOException) { }
    }
}
