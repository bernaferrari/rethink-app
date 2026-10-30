package com.bernaferrari.bravedns.backup

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/** Current preference archive format; contains data only, never serialized JVM objects. */
internal object PreferenceBackupCodec {
    internal const val MAGIC = 0x4b505246 // KPRF
    internal const val CURRENT_FORMAT = 1
    internal const val MAX_FILE_BYTES = 4 * 1024 * 1024
    internal const val MAX_ENTRIES = 4096
    internal const val MAX_KEY_BYTES = 1024
    internal const val MAX_STRING_BYTES = 1024 * 1024
    private const val BOOLEAN = 1
    private const val INTEGER = 2
    private const val LONG = 3
    private const val STRING = 4

    fun read(file: File): Map<String, Any> {
        require(file.length() <= MAX_FILE_BYTES) { "Preference backup exceeds size limit" }
        return file.inputStream().buffered().use(::read)
    }

    fun write(file: File, values: Map<String, Any?>) {
        file.outputStream().buffered().use { write(it, values) }
    }

    fun read(source: InputStream): Map<String, Any> {
        val reader = Reader(DataInputStream(source))
        require(reader.int() == MAGIC && reader.byte() == CURRENT_FORMAT) { "Invalid preference backup format" }
        val count = reader.int()
        require(count in 0..MAX_ENTRIES) { "Invalid preference count" }
        val values = linkedMapOf<String, Any>()
        repeat(count) {
            val key = reader.string(MAX_KEY_BYTES)
            require(!values.containsKey(key)) { "Duplicate preference name" }
            values[key] = reader.value()
        }
        require(source.read() == -1) { "Trailing preference backup data" }
        return values
    }

    fun write(destination: OutputStream, values: Map<String, Any?>) {
        require(values.size <= MAX_ENTRIES) { "Too many preferences" }
        val writer = Writer(DataOutputStream(destination))
        writer.int(MAGIC)
        writer.byte(CURRENT_FORMAT)
        writer.int(values.size)
        values.forEach { (key, value) ->
            writer.string(key, MAX_KEY_BYTES)
            writer.value(value)
        }
    }

    private class Budget {
        private var remaining = MAX_FILE_BYTES
        fun reserve(bytes: Int) {
            require(bytes in 0..remaining) { "Preference backup exceeds size limit" }
            remaining -= bytes
        }
    }

    private class Reader(private val input: DataInputStream) {
        private val budget = Budget()
        fun byte(): Int { budget.reserve(1); return input.readUnsignedByte() }
        fun int(): Int { budget.reserve(Int.SIZE_BYTES); return input.readInt() }
        private fun long(): Long { budget.reserve(Long.SIZE_BYTES); return input.readLong() }
        fun string(maxBytes: Int): String {
            val size = int()
            require(size in 0..maxBytes) { "Invalid preference string size" }
            budget.reserve(size) // Check aggregate size before allocating the declared string.
            val bytes = ByteArray(size)
            input.readFully(bytes)
            return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        }
        fun value(): Any = when (byte()) {
            BOOLEAN -> byte().also { require(it == 0 || it == 1) { "Invalid preference boolean" } } == 1
            INTEGER -> int()
            LONG -> long()
            STRING -> string(MAX_STRING_BYTES)
            else -> throw IllegalArgumentException("Invalid preference type")
        }
    }

    private class Writer(private val output: DataOutputStream) {
        private val budget = Budget()
        fun byte(value: Int) { budget.reserve(1); output.writeByte(value) }
        fun int(value: Int) { budget.reserve(Int.SIZE_BYTES); output.writeInt(value) }
        private fun long(value: Long) { budget.reserve(Long.SIZE_BYTES); output.writeLong(value) }
        fun string(value: String, maxBytes: Int) {
            require(value.length <= maxBytes) { "Preference string exceeds size limit" }
            val encoded = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value))
            val size = encoded.remaining()
            require(size <= maxBytes) { "Preference string exceeds size limit" }
            int(size)
            budget.reserve(size)
            val bytes = ByteArray(size)
            encoded.get(bytes)
            output.write(bytes)
        }
        fun value(value: Any?) {
            when (value) {
                is Boolean -> { byte(BOOLEAN); byte(if (value) 1 else 0) }
                is Int -> { byte(INTEGER); int(value) }
                is Long -> { byte(LONG); long(value) }
                is String -> { byte(STRING); string(value, MAX_STRING_BYTES) }
                else -> throw IllegalArgumentException("Unsupported preference type")
            }
        }
    }
}
