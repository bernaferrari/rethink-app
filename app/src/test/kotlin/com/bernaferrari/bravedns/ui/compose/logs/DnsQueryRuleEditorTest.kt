package com.bernaferrari.bravedns.ui.compose.logs

import com.bernaferrari.bravedns.service.DomainRulesManager.Status
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DnsQueryRuleEditorTest {
    @Test fun `only an exact DNS hostname is eligible`() {
        assertEquals("example.com", DnsQueryRuleEditor.exactQueryDomain("EXAMPLE.COM."))
        for (value in listOf("*.example.com", "https://example.com", "example.com/path", "127.0.0.1:53", "bad label.com", " example.com", "example.com..", "")) {
            assertNull(value, DnsQueryRuleEditor.exactQueryDomain(value))
        }
    }

    @Test fun `app and global status writes stay separate and outside scopes never write`() = runTest {
        val rows = mutableMapOf(42 to Status.NONE, -1000 to Status.TRUST)
        val editor = editor(rows)
        assertFalse(editor.select(43, Status.BLOCK))
        assertTrue(editor.select(42, Status.BLOCK))
        assertEquals(mapOf(42 to Status.BLOCK, -1000 to Status.TRUST), editor.state.value.statuses)
        assertTrue(editor.select(-1000, Status.NONE))
        assertEquals(mapOf(42 to Status.BLOCK, -1000 to Status.NONE), rows)
    }

    @Test fun `second click is rejected until a write finishes and no optimistic status is shown`() = runTest {
        val release = CompletableDeferred<Unit>()
        val rows = mutableMapOf(42 to Status.NONE, -1000 to Status.NONE)
        var writes = 0
        val editor = editor(rows, write = { uid, status -> writes++; release.await(); rows[uid] = status })
        val first = async { editor.select(42, Status.BLOCK) }
        try {
            runCurrent()
            assertTrue(editor.state.value.saving)
            assertEquals(Status.NONE, editor.state.value.statuses[42])
            assertFalse(editor.select(-1000, Status.TRUST))
            assertEquals(1, writes)
        } finally { release.complete(Unit) }
        assertTrue(first.await())
        assertFalse(editor.state.value.saving)
        assertEquals(Status.BLOCK, editor.state.value.statuses[42])
    }

    @Test fun `failed save preserves the old selection and exposes an error then retry works`() = runTest {
        val rows = mutableMapOf(42 to Status.NONE)
        var fail = true
        val errors = mutableListOf<Throwable>()
        val editor = editor(rows, write = { uid, status ->
            if (fail) throw IllegalStateException("DB closed")
            rows[uid] = status
        }, errors = errors)
        assertFalse(editor.select(42, Status.TRUST))
        assertEquals(Status.NONE, editor.state.value.statuses[42])
        assertNotNull(editor.state.value.error)
        assertFalse(editor.state.value.saving)
        assertEquals(1, errors.size)
        fail = false
        assertTrue(editor.select(42, Status.TRUST))
        assertNull(editor.state.value.error)
    }

    @Test fun `dismissal cancellation finishes an already started database and cache mutation`() = runTest {
        val release = CompletableDeferred<Unit>()
        val rows = mutableMapOf(42 to Status.NONE)
        val editor = editor(rows, write = { uid, status -> release.await(); rows[uid] = status })
        val write = launch { editor.select(42, Status.BLOCK) }
        try {
            runCurrent()
            write.cancel()
            assertEquals(Status.NONE, rows[42])
        } finally { release.complete(Unit) }
        write.join()
        assertTrue(write.isCancelled)
        assertEquals(Status.BLOCK, rows[42])
        assertFalse(editor.state.value.saving)
        assertNull(editor.state.value.error)
    }

    private fun editor(
        rows: MutableMap<Int, Status>,
        write: suspend (Int, Status) -> Unit = { uid, status -> rows[uid] = status },
        errors: MutableList<Throwable> = mutableListOf(),
    ) = DnsQueryRuleEditor("example.com.", rows.keys.toSet(),
        read = { _, uid -> rows.getValue(uid) }, write = { _, uid, status -> write(uid, status) }, onError = { errors.add(it) })
}
