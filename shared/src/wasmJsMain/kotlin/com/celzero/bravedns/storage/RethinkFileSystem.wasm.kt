package com.celzero.bravedns.storage

import okio.FileSystem
import okio.fakefilesystem.FakeFileSystem

private val wasmFileSystem = FakeFileSystem()

/**
 * The browser demo keeps filesystem-backed state in memory. Its public contract is identical to
 * Android's Okio system filesystem, so common storage code and view models remain unchanged.
 */
actual fun platformFileSystem(): FileSystem = wasmFileSystem
