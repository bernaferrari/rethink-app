package com.bernaferrari.bravedns.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreferenceBackupCodecTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `roundtrip preserves every supported value type and Unicode`() {
        val values = linkedMapOf<String, Any?>(
            "true" to true, "false" to false,
            "intMin" to Int.MIN_VALUE, "intMax" to Int.MAX_VALUE,
            "longMin" to Long.MIN_VALUE, "longMax" to Long.MAX_VALUE,
            "" to "", "unicode-🌍" to "é\u0000🌍\n", "numericString" to "123",
        )
        val file = temporary.newFile()
        PreferenceBackupCodec.write(file, values)
        assertEquals(values, PreferenceBackupCodec.read(file))
        assertEquals(emptyMap<String, Any>(), decode(encode(emptyMap())))
    }

    @Test fun `maximum individual key and string byte sizes roundtrip`() {
        val values = mapOf("k".repeat(PreferenceBackupCodec.MAX_KEY_BYTES) to
            "v".repeat(PreferenceBackupCodec.MAX_STRING_BYTES))
        assertEquals(values, decode(encode(values)))
    }

    @Test fun `invalid magic current marker and harmless Java stream header reject`() {
        rejects(byteArrayOf(0xac.toByte(), 0xed.toByte(), 0x00, 0x05))
        rejects(raw { writeInt(0); writeByte(1); writeInt(0) })
        rejects(raw { writeInt(PreferenceBackupCodec.MAGIC); writeByte(2); writeInt(0) })
    }

    @Test fun `entry count is bounded before records are read`() {
        listOf(-1, PreferenceBackupCodec.MAX_ENTRIES + 1, Int.MAX_VALUE).forEach { count ->
            rejects(raw { header(count) })
        }
        val maximum = (0 until PreferenceBackupCodec.MAX_ENTRIES).associate { "key$it" to 1 }
        assertEquals(maximum, decode(encode(maximum)))
        val values = (0..PreferenceBackupCodec.MAX_ENTRIES).associate { "key$it" to 1 }
        assertThrows(IllegalArgumentException::class.java) { encode(values) }
    }

    @Test fun `declared key and string lengths are checked before allocation`() {
        listOf(-1, PreferenceBackupCodec.MAX_KEY_BYTES + 1, Int.MAX_VALUE).forEach { size ->
            rejects(raw { header(1); writeInt(size) })
        }
        listOf(-1, PreferenceBackupCodec.MAX_STRING_BYTES + 1, Int.MAX_VALUE).forEach { size ->
            rejects(raw { header(1); key("key"); writeByte(4); writeInt(size) })
        }
    }

    @Test fun `duplicate names unknown types and noncanonical booleans reject`() {
        rejects(raw {
            header(2)
            repeat(2) { key("duplicate"); writeByte(2); writeInt(it) }
        })
        rejects(raw { header(1); key("key"); writeByte(0) })
        rejects(raw { header(1); key("key"); writeByte(255) })
        rejects(raw { header(1); key("key"); writeByte(1); writeByte(2) })
    }

    @Test fun `all truncated prefixes and trailing bytes reject`() {
        val valid = encode(mapOf("number" to 42L, "string" to "complete"))
        for (size in valid.indices) rejects(valid.copyOf(size))
        rejects(valid + byteArrayOf(0))
    }

    @Test fun `invalid UTF-8 is rejected rather than replacing names or values`() {
        rejects(raw { header(1); writeInt(2); write(byteArrayOf(0xc3.toByte(), 0x28)) })
        rejects(raw { header(1); key("key"); writeByte(4); writeInt(2); write(byteArrayOf(0xc3.toByte(), 0x28)) })
        assertThrows(Exception::class.java) { encode(mapOf("key" to "\uD800")) }
    }

    @Test fun `aggregate stream and file sizes are bounded`() {
        val bytes = raw {
            header(4)
            repeat(4) {
                key("key$it"); writeByte(4)
                writeInt(PreferenceBackupCodec.MAX_STRING_BYTES)
                write(ByteArray(PreferenceBackupCodec.MAX_STRING_BYTES))
            }
        }
        rejects(bytes)
        val file = temporary.newFile()
        RandomAccessFile(file, "rw").use { it.setLength(PreferenceBackupCodec.MAX_FILE_BYTES.toLong() + 1) }
        assertThrows(IllegalArgumentException::class.java) { PreferenceBackupCodec.read(file) }
        val oversized = (0..3).associate { "key$it" to "x".repeat(PreferenceBackupCodec.MAX_STRING_BYTES) }
        assertThrows(IllegalArgumentException::class.java) { encode(oversized) }
    }

    @Test fun `unsupported values and oversized UTF-8 names are never silently dropped`() {
        listOf(null, 1f, 1.0, setOf("value"), byteArrayOf(1)).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { encode(mapOf("key" to value)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            encode(mapOf("é".repeat(PreferenceBackupCodec.MAX_KEY_BYTES) to true))
        }
        assertThrows(IllegalArgumentException::class.java) {
            encode(mapOf("key" to "x".repeat(PreferenceBackupCodec.MAX_STRING_BYTES + 1)))
        }
    }

    private fun encode(values: Map<String, Any?>): ByteArray = ByteArrayOutputStream().also {
        PreferenceBackupCodec.write(it, values)
    }.toByteArray()
    private fun decode(bytes: ByteArray) = PreferenceBackupCodec.read(ByteArrayInputStream(bytes))
    private fun rejects(bytes: ByteArray) {
        assertThrows(Exception::class.java) { decode(bytes) }
    }
    private fun raw(write: DataOutputStream.() -> Unit) = ByteArrayOutputStream().also {
        DataOutputStream(it).use(write)
    }.toByteArray()
    private fun DataOutputStream.header(count: Int) {
        writeInt(PreferenceBackupCodec.MAGIC)
        writeByte(PreferenceBackupCodec.CURRENT_FORMAT)
        writeInt(count)
    }
    private fun DataOutputStream.key(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }
}
