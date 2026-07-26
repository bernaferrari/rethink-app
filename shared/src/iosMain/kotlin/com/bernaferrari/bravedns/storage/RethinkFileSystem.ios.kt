package com.bernaferrari.bravedns.storage

import okio.FileSystem

actual fun platformFileSystem(): FileSystem = FileSystem.SYSTEM
