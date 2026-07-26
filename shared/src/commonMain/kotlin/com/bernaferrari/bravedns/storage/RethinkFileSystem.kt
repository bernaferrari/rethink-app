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
package com.bernaferrari.bravedns.storage

import okio.FileSystem
import okio.HashingSink
import okio.Path.Companion.toPath
import okio.blackholeSink
import okio.buffer

/**
 * Target-neutral file primitives used by app data flows.
 *
 * Keeping this surface on Okio means a platform can provide a real file system, a sandbox, or
 * Okio's fake file system without changing backup/download/domain code.
 */
class RethinkFileSystem(private val fileSystem: FileSystem) {
    fun copy(source: String, target: String): Boolean = runCatching {
        val sourcePath = source.toPath()
        val targetPath = target.toPath()
        if (!fileSystem.exists(sourcePath)) return false
        targetPath.parent?.let(fileSystem::createDirectories)
        val input = fileSystem.source(sourcePath).buffer()
        try {
            val output = fileSystem.sink(targetPath).buffer()
            try {
                output.writeAll(input)
            } finally {
                output.close()
            }
        } finally {
            input.close()
        }
        true
    }.getOrDefault(false)

    fun deleteRecursively(path: String): Boolean = runCatching {
        fileSystem.deleteRecursively(path.toPath(), mustExist = false)
        true
    }.getOrDefault(false)

    fun readUtf8(path: String): String =
        fileSystem.source(path.toPath()).buffer().let { source ->
            try {
                source.readUtf8()
            } finally {
                source.close()
            }
        }

    fun md5Hex(path: String): String =
        fileSystem.source(path.toPath()).buffer().let { source ->
            try {
                HashingSink.md5(blackholeSink()).let { sink ->
                    try {
                        source.readAll(sink)
                        sink.hash.hex()
                    } finally {
                        sink.close()
                    }
                }
            } finally {
                source.close()
            }
        }
}

/** Platform-selected store: real on device, fake and sandboxed for the WASM demo. */
expect fun platformFileSystem(): FileSystem

val appFileSystem: RethinkFileSystem by lazy { RethinkFileSystem(platformFileSystem()) }
