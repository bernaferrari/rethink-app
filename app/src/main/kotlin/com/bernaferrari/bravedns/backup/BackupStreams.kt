package com.bernaferrari.bravedns.backup

import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Owns the input even when opening the SAF destination fails; cancellation closes both. */
internal suspend fun copyBackupStream(input: InputStream, openOutput: () -> OutputStream?): Boolean =
    input.use { source ->
        val output = openOutput() ?: return@use false
        output.use { destination ->
            copyBackupBytes(source, destination)
            destination.flush()
        }
        true
    }

internal suspend fun copyBackupBytes(source: InputStream, destination: OutputStream) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var count = source.read(buffer)
    while (count >= 0) {
        currentCoroutineContext().ensureActive()
        destination.write(buffer, 0, count)
        count = source.read(buffer)
    }
}
