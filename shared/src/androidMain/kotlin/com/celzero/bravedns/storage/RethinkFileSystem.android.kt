package com.celzero.bravedns.storage

import okio.FileSystem

actual fun platformFileSystem(): FileSystem = FileSystem.SYSTEM
