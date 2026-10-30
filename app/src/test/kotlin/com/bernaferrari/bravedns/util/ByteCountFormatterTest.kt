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
package com.bernaferrari.bravedns.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteCountFormatterTest {

    @Test
    fun `bytes below one kilobyte are shown verbatim`() {
        assertEquals("0 B", formatByteCount(0))
        assertEquals("1 B", formatByteCount(1))
        assertEquals("999 B", formatByteCount(999))
    }

    @Test
    fun `negative counts clamp to zero`() {
        assertEquals("0 B", formatByteCount(-42))
    }

    @Test
    fun `uses SI 1000-based units with one decimal`() {
        assertEquals("1.0 KB", formatByteCount(1_000))
        assertEquals("1.0 KB", formatByteCount(1_024))
        assertEquals("1.5 KB", formatByteCount(1_500))
        assertEquals("12.3 MB", formatByteCount(12_345_678))
        assertEquals("1.0 GB", formatByteCount(1_000_000_000))
        assertEquals("2.5 TB", formatByteCount(2_500_000_000_000))
        assertEquals("9.2 EB", formatByteCount(Long.MAX_VALUE))
    }

    @Test
    fun `values that would round to 1000 roll over to the next unit`() {
        assertEquals("999.9 KB", formatByteCount(999_940))
        assertEquals("1.0 MB", formatByteCount(999_960))
        assertEquals("1.0 MB", formatByteCount(999_999))
        assertEquals("1.0 GB", formatByteCount(999_999_999))
    }
}
