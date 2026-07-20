/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.celzero.bravedns.wireguard

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

/** JVM/Android stream entry points for common [Config.parse]. */
object ConfigIo {
    @Throws(IOException::class, BadConfigException::class)
    fun parse(stream: InputStream?): Config =
        parse(BufferedReader(InputStreamReader(stream)))

    @Throws(IOException::class, BadConfigException::class)
    fun parse(reader: BufferedReader): Config =
        Config.parse(reader.lineSequence().toList())
}
