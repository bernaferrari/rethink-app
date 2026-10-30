package com.bernaferrari.bravedns.rules

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.Reader

/** Enforces byte/line bounds before allocating or retaining input from an external document. */
object RuleImportReader {
    fun preview(input: InputStream, normalize: (String) -> String?): RuleImportPreview =
        BoundedInput(input).bufferedReader(Charsets.UTF_8).use { reader ->
            RuleImportParser.preview(lines(reader), normalize)
        }

    private fun lines(reader: Reader): Sequence<String> = sequence {
        val line = StringBuilder()
        var overlong = false
        var character = reader.read()
        while (character >= 0) {
            when (character.toChar()) {
                '\n' -> {
                    yield(if (overlong) " ".repeat(RuleImportParser.MAX_LINE_LENGTH + 1) else line.toString().trimEnd('\r'))
                    line.clear(); overlong = false
                }
                else -> if (line.length < RuleImportParser.MAX_LINE_LENGTH) line.append(character.toChar()) else overlong = true
            }
            character = reader.read()
        }
        if (line.isNotEmpty() || overlong) {
            yield(if (overlong) " ".repeat(RuleImportParser.MAX_LINE_LENGTH + 1) else line.toString().trimEnd('\r'))
        }
    }

    private class BoundedInput(input: InputStream) : FilterInputStream(input) {
        private var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0) add(1) }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            `in`.read(buffer, offset, length).also { if (it > 0) add(it) }
        private fun add(amount: Int) {
            count += amount
            if (count > RuleImportParser.MAX_FILE_BYTES) throw IOException("Rule file exceeds size limit")
        }
    }
}
