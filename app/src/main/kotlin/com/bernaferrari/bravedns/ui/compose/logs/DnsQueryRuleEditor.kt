package com.bernaferrari.bravedns.ui.compose.logs

import com.bernaferrari.bravedns.rules.DomainRuleInput
import com.bernaferrari.bravedns.service.DomainRulesManager.Status
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class DnsRuleEditorState(
    val statuses: Map<Int, Status>,
    val saving: Boolean = false,
    val error: Exception? = null,
)

/** Explicit selections, not tap-to-cycle: serial writes cannot turn a stale read into a new rule. */
internal class DnsQueryRuleEditor(
    query: String,
    private val scopes: Set<Int>,
    private val read: (String, Int) -> Status,
    private val write: suspend (String, Int, Status) -> Unit,
    private val onError: (Exception) -> Unit,
) {
    val domain: String? = exactQueryDomain(query)
    private val mutableState = MutableStateFlow(DnsRuleEditorState(
        if (domain == null) emptyMap() else scopes.associateWith { read(domain, it) },
    ))
    val state = mutableState.asStateFlow()
    private val request = Mutex()

    // The injected write crosses Room and Go JNI; generated Go errors have no narrower Java type.
    @Suppress("TooGenericExceptionCaught")
    suspend fun select(uid: Int, status: Status): Boolean {
        val name = domain ?: return false
        if (uid !in scopes || !request.tryLock()) return false
        mutableState.value = mutableState.value.copy(saving = true, error = null)
        try {
            writes.withLock {
                currentCoroutineContext().ensureActive()
                // Finish the DB + native-cache update together if this sheet is dismissed mid-write.
                withContext(NonCancellable) {
                    if (read(name, uid) != status) write(name, uid, status)
                }
                mutableState.value = mutableState.value.copy(statuses = scopes.associateWith { read(name, it) })
            }
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // DomainRulesManager writes cross Room and the Go JNI trie boundary; log either failure.
            return failed(error)
        } finally {
            mutableState.value = mutableState.value.copy(saving = false)
            request.unlock()
        }
    }

    private fun failed(error: Exception): Boolean {
        onError(error)
        mutableState.value = mutableState.value.copy(error = error)
        return false
    }

    companion object {
        private val writes = Mutex()
        fun exactQueryDomain(query: String): String? {
            if (query != query.trim() || query.endsWith("..")) return null
            val value = query.removeSuffix(".").lowercase()
            if (value.startsWith("*.") || ':' in value || '/' in value) return null
            return DomainRuleInput.normalize(value)?.takeIf { it == value }
        }
    }
}
