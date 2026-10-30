package com.bernaferrari.bravedns.service

import com.bernaferrari.bravedns.util.ExpiringCache

/** FID identifies a socket/flow, not a DNS question. Include the exposed question fields. */
internal data class DnsQuestionKey(
    val flowId: String,
    val uid: String,
    val name: String,
    val type: Long,
    val startedAt: Long,
)

/** Native answer and summary callbacks can run on different threads and arrive out of order. */
internal class DnsAnswerReasons {
    private sealed interface Recorded {
        data class Known(val reason: String) : Recorded
        data object Ambiguous : Recorded
    }
    private val reasons = ExpiringCache<DnsQuestionKey, Recorded>(maxSize = 4096, expireAfterWriteMs = 60_000)

    @Synchronized
    fun record(question: DnsQuestionKey, reason: String) {
        val previous = reasons.getIfPresent(question)
        val recorded = when (previous) {
            null -> Recorded.Known(reason)
            is Recorded.Known -> if (previous.reason == reason) previous else Recorded.Ambiguous
            Recorded.Ambiguous -> previous
        }
        reasons.put(question, recorded)
    }

    // Keep the bounded entry until TTL, because native may report the same question twice.
    @Synchronized
    fun find(question: DnsQuestionKey): String? = (reasons.getIfPresent(question) as? Recorded.Known)?.reason

    @Synchronized
    fun clear() = reasons.invalidateAll()
}
