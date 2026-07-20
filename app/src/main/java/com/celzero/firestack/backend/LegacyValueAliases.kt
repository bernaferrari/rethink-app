package com.celzero.firestack.backend

/**
 * Source-compatible aliases for app code that still names the value wrappers removed by
 * Firestack 310d7bc603. The upgraded bindings now accept native values directly.
 */
typealias Gostr = String
typealias Gobyte = ByteArray

fun String.asGostr(): String = this
fun PipMsg.asGostr(): String = s()
val PipToken.s: String get() = s()
