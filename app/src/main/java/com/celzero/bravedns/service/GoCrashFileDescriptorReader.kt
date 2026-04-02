/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.celzero.bravedns.service

import Logger
import Logger.LOG_TAG_BUG_REPORT
import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import com.celzero.bravedns.scheduler.EnhancedBugReport
import com.celzero.bravedns.service.GoCrashFileDescriptorReader.Companion.MAX_LINE_BYTES
import com.celzero.bravedns.util.Daemons
import kotlinx.coroutines.Dispatchers
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.InterruptedIOException
import kotlin.system.exitProcess

/**
 * Reads Go runtime crash logs from a duplicated file descriptor and persists them
 * to a timestamped file under the tombstone directory.
 *
 * Responsibilities (and nothing else):
 *   1. Duplicate the Go-provided fd so we own our copy independently.
 *   2. Make the dup blocking so [Os.read] blocks until data arrives.
 *   3. Create the output file upfront (before reading starts).
 *   4. Read lines from the fd and append them to the file.
 *   5. Close everything when the Go writer closes its end (EOF).
 *   6. Rotate old files so storage does not grow unbounded.
 *
 * Single-coroutine design: all I/O runs on one [Dispatchers.IO] coroutine so no
 * locks or synchronization are needed.  The coroutine is fire-and-forget; there is
 * no need to track the [Job] — Go's write-end close triggers EOF which terminates
 * the read loop naturally.
 *
 */
class GoCrashFileDescriptorReader(private val context: Context?) {

    private val executor = Daemons.makeThread("goCrashFd")
    private var writer: BufferedWriter? = null

    /**
     * Duplicates [fd], creates the output crash file, and starts the background
     * read loop.  Returns [true] if setup succeeded, [false] on any early error.
     */
    fun start(fd: Long): Boolean {
        if (fd <= 0L) {
            Logger.w(LOG_TAG_BUG_REPORT, "go crash fd invalid: $fd")
            return false
        }

        val pfd = ParcelFileDescriptor.adoptFd(fd.toInt())
        // Create the file BEFORE entering the blocking read loop so that a file
        // is always available for writing even if the first line arrives instantly.
        val outFile: File? = createCrashFile()

        if (outFile == null) {
            Logger.e(LOG_TAG_BUG_REPORT, "go crash: failed to create output file, aborting")
            try {
                pfd.close()
            } catch (_: IOException) { }
            return false
        }

        executor.execute {
            Logger.d(LOG_TAG_BUG_REPORT, "go crash: writing to ${outFile.absolutePath}")
            // 1
            // readLoop(pfd, outFile)

            // 2
            // readLoop(pfd)

            // 3
            writeLine("Init GoCrash->")
            readLoop1(pfd)

            // 4
            /*drainCrashFd(pfd.fileDescriptor) { chunk ->
                writeChunk(chunk.toByteArray(), chunk.length)
            }*/
        }

        return true
    }

    fun start2(): File? {
        // create a file and send the file descriptor to the service
        return createCrashFile2()
    }

    private fun readLoop1(pfd: ParcelFileDescriptor) {
        Logger.d(LOG_TAG_BUG_REPORT, "$TAG readLoop: started")
        val reader = BufferedReader(InputStreamReader(FileInputStream(pfd.fileDescriptor)))
        try {
            var line = reader.readLine()
            while (line != null) {
                writeLine(line)
                Logger.d(LOG_TAG_BUG_REPORT, "$TAG readLoop: $line")
                line = reader.readLine()
            }
            // readLine() returning null means the write-end of the pipe was closed (EOF).
            Logger.d(LOG_TAG_BUG_REPORT, "$TAG readLoop: EOF reached")
        } catch (e: Exception) {
            Logger.e(LOG_TAG_BUG_REPORT, "$TAG readLoop: error: ${e.message}", e)
        } finally {
            closeWriter()
            try { reader.close() } catch (_: Exception) {}
            try { pfd.close() } catch (_: Exception) {}
            Logger.d(LOG_TAG_BUG_REPORT, "$TAG readLoop: cleaned up")
        }
    }

    private fun writeLine(line: String) {
        try {
            val w = writer ?: run {
                Logger.e(LOG_TAG_BUG_REPORT, "$TAG writeLine: writer is null, line dropped")
                return
            }
            w.write(line)
            w.newLine()
            w.flush() // flush per line so data survives a process crash mid-session
        } catch (e: Exception) {
            Logger.e(LOG_TAG_BUG_REPORT, "$TAG writeLine: write failed: ${e.message}", e)
        }
    }

    private fun readLoop(pfd: ParcelFileDescriptor) {
        Logger.d(LOG_TAG_BUG_REPORT, "$TAG readLoop: started")

        val fd = pfd.fileDescriptor
        val input = FileInputStream(fd)
        val buffer = ByteArray(READ_BUF_SIZE)

        val pollFd = StructPollfd().apply {
            this.fd = fd
            this.events = OsConstants.POLLIN.toShort()
        }

        var started = false
        var lastReadTime = 0L

        try {
            while (true) {
                val timeout = if (!started) {
                    -1 // wait until first write
                } else {
                    IDLE_TIMEOUT_MS
                }

                val pollResult = try {
                    Os.poll(arrayOf(pollFd), timeout)
                } catch (e: ErrnoException) {
                    Logger.e(LOG_TAG_BUG_REPORT, "$TAG readLoop: poll error ${e.message}", e)
                    break
                }

                if (pollResult == 0) {
                    val now = System.currentTimeMillis()
                    if (started && now - lastReadTime >= IDLE_TIMEOUT_MS) {
                        Logger.w(LOG_TAG_BUG_REPORT, "$TAG readLoop: idle timeout, stopping drain")
                        exitProcess(-1)
                        //break
                    }
                    continue
                }

                val revents = pollFd.revents.toInt()

                if ((revents and OsConstants.POLLHUP) != 0) {
                    Logger.d(LOG_TAG_BUG_REPORT, "$TAG readLoop: POLLHUP detected")
                    break
                }

                if ((revents and OsConstants.POLLIN) != 0) {
                    val read = input.read(buffer)

                    if (read == -1) {
                        Logger.d(LOG_TAG_BUG_REPORT, "$TAG readLoop: EOF reached")
                        break
                    }

                    if (read > 0) {
                        if (!started) {
                            Logger.d(LOG_TAG_BUG_REPORT, "$TAG readLoop: first data received")
                            started = true
                        }

                        lastReadTime = System.currentTimeMillis()
                        writeChunk(buffer, read)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(LOG_TAG_BUG_REPORT, "$TAG readLoop: error ${e.message}", e)
        } finally {
            closeWriter()
            try { input.close() } catch (_: Exception) {}
            try { pfd.close() } catch (_: Exception) {}
            Logger.d(LOG_TAG_BUG_REPORT, "$TAG readLoop: cleaned up")
        }
    }

    fun drainCrashFd(fd: FileDescriptor, onChunk: (String) -> Unit) {
        Thread {
            val buffer = ByteArray(4096)

            while (true) {
                try {
                    FileInputStream(fd).use { input ->
                        while (true) {
                            val read = input.read(buffer)

                            if (read == -1) {
                                Logger.i(LOG_TAG_BUG_REPORT,"GoCrashFd: EOF")
                                break
                            }

                            if (read > 0) {
                                onChunk(String(buffer, 0, read, Charsets.UTF_8))
                            }
                        }
                    }

                    break // normal exit

                } catch (e: InterruptedIOException) {
                    Logger.e(LOG_TAG_BUG_REPORT,"GoCrashFd: fd closed, retrying...")

                    // 🔑 Wait before retry — FD may be reused later
                    Thread.sleep(50)

                } catch (e: Exception) {
                    Logger.e(LOG_TAG_BUG_REPORT,"GoCrashFd: error", e)
                    break
                }
            }

            Logger.i(LOG_TAG_BUG_REPORT,"GoCrashFd: cleaned up")
        }.start()
    }

    private fun writeChunk(buffer: ByteArray, length: Int) {
        try {
            val w = writer ?: run {
                Logger.e(LOG_TAG_BUG_REPORT, "$TAG writeChunk: writer is null, dropped")
                return
            }

            w.write(String(buffer, 0, length))
            w.flush() // keep for crash safety
        } catch (e: Exception) {
            Logger.e(LOG_TAG_BUG_REPORT, "$TAG writeChunk: failed ${e.message}", e)
        }
    }

    private fun closeWriter() {
        try { writer?.flush() } catch (_: Exception) {}
        try { writer?.close() } catch (_: Exception) {}
        writer = null
    }


    /**
     * Reads lines from [fd] and appends each one to [outFile].
     *
     * The fd is blocking after [makeBlocking]; [Os.read] will block until data
     * arrives or the write-end is closed (EOF).  When Go finishes writing (crash
     * logged, process exits, or pipe closed), read returns 0 and we exit.
     *
     * Go guarantees the total crash output is ≤ 64 KB, so the line accumulator
     * buffer will never grow large.  We still cap individual lines at
     * [MAX_LINE_BYTES] as a safety net against unexpected output.
     */
    private fun readLoop(pfd: ParcelFileDescriptor, outFile: File) {
        val readBuf = ByteArray(READ_BUF_SIZE)

        try {
            FileOutputStream(outFile, /* append= */ false).use { fos ->
                var m = 0
                while (true) {
                    val n = try {
                        Os.read(pfd.fileDescriptor, readBuf, 0, readBuf.size)
                    } catch (e: ErrnoException) {
                        Logger.d(LOG_TAG_BUG_REPORT, "go crash read errno=${e.errno}: ${e.message}")
                        break
                    }

                    m += n
                    Logger.d(LOG_TAG_BUG_REPORT, "go crash read n=$n, total = $m")
                    if (n <= 0) {
                        // EOF: Go closed its write-end → we are done.
                        Logger.d(LOG_TAG_BUG_REPORT, "go crash fd EOF (n=$n)")
                        break
                    }
                    fos.write(readBuf, 0, n)
                }

                Logger.d(LOG_TAG_BUG_REPORT, "bytes written: $m")
                // fsync so the data survives a process crash immediately after Go exits.
                try {
                    fos.fd.sync()
                    Logger.d(LOG_TAG_BUG_REPORT, "go crash fsync")
                } catch (_: Exception) {
                    Logger.e(LOG_TAG_BUG_REPORT, "go crash fsync error")
                }
            }

            // Delete the file if nothing was written (Go did not crash this session).
            /*if (readBuf.isEmpty()) {
                outFile.delete()
                Logger.d(LOG_TAG_BUG_REPORT, "go crash: deleted empty file ${outFile.name}")
            } else {
                // Rotate old files only when we actually wrote something.
                performRotation(outFile)
            }*/
        } catch (e: Exception) {
            Logger.e(LOG_TAG_BUG_REPORT, "go crash read loop error: ${e.message}", e)
            // delete the file if it is empty
            if (outFile.exists() && outFile.length() == 0L) outFile.delete()
        } finally {
            try {
                pfd.close()
                Logger.d(LOG_TAG_BUG_REPORT, "go crash: pfd closed")
            } catch (_: Exception) {
                Logger.e(LOG_TAG_BUG_REPORT, "go crash: pfd close error")
            }
        }
    }

    private fun createCrashFile2(): File? {
        if (context == null) {
            Logger.e(LOG_TAG_BUG_REPORT, "$TAG createCrashFileFd: missing app context")
            return null
        }
        val file = EnhancedBugReport.newGoCrashFile(context)
        if (file == null) {
            Logger.e(LOG_TAG_BUG_REPORT, "$TAG createCrashFileFd: newGoCrashFile returned null")
            return null
        }

        return file
    }

    /**
     * Creates a uniquely-named crash file via [EnhancedBugReport.newGoCrashFile] so that all
     * three tombstone types (golog_, gocrash_, kotlin_) share the same folder, naming scheme,
     * and rotation logic.
     */
    private fun createCrashFile(): File? {
        if (context == null) {
            Logger.e(LOG_TAG_BUG_REPORT, "$TAG createCrashFile: missing app context")
            return null
        }
        val file = EnhancedBugReport.newGoCrashFile(context)
        if (file == null) {
            Logger.e(LOG_TAG_BUG_REPORT, "$TAG createCrashFile: newGoCrashFile returned null")
            return null
        }
        return try {
            writer = BufferedWriter(
                OutputStreamWriter(FileOutputStream(file, /* append= */ true), Charsets.UTF_8)
                )
            Logger.d(LOG_TAG_BUG_REPORT, "$TAG createCrashFile: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Logger.e(LOG_TAG_BUG_REPORT, "$TAG createCrashFile: failed to open writer: ${e.message}", e)
            null
        }
    }

    /**
     * Delegates rotation to [EnhancedBugReport.enforceMaxFiles] which counts ALL tombstone
     * file types together and enforces the shared 20-file cap.
     */
    private fun performRotation(current: File) {
        if (context == null) {
            Logger.e(LOG_TAG_BUG_REPORT, "go-crash, context is null, rotation failed")
            return
        }
        if (!current.exists()) {
            Logger.e(LOG_TAG_BUG_REPORT, "go-crash, current file does not exist, rotation failed")
            return
        }
        EnhancedBugReport.enforceMaxFiles(context, justWritten = current)
    }

    /**
     * Delegates to [FdHelper.duplicate] which owns all reflection on private Android internals
     * ([FileDescriptor.descriptor], libcore.io.Libcore.os / android.system.Os fcntlInt).
     * This class is free of reflection entirely.
     */
    private fun duplicateFd(fd: Long): ParcelFileDescriptor? =
        FdHelper.duplicate(fd, "GoCrashFd")

    companion object {
        private const val READ_BUF_SIZE = 64 * 1024
        private const val MAX_LINE_BYTES = 4 * 1024
        private const val IDLE_TIMEOUT_MS = 3000
        private const val TAG = "GoCrashFd"
        /**
         * EWOULDBLOCK == EAGAIN on Linux/Android (both == 11) but OsConstants may
         * not expose EWOULDBLOCK on all API levels.
         */
        private val EWOULDBLOCK: Int = try {
            OsConstants::class.java.getField("EWOULDBLOCK").getInt(null)
        } catch (_: Throwable) {
            OsConstants.EAGAIN
        }
    }
}
