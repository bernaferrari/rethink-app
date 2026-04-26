package com.celzero.bravedns.util

import Logger
import android.os.FileObserver
import com.celzero.bravedns.RethinkDnsApplication.Companion.DEBUG
import com.celzero.bravedns.util.Utilities.isAtleastQ
import java.io.File

class FileObserverManager(
    private val path: String,
    private val fileName: String,
    private val onDetected: () -> Unit,
) {
    private var observer: FileObserver? = null
    companion object {
        const val TAG = "GoCrashFd"
    }
    fun start() {
        val dir = File(path)

        observer = if (isAtleastQ()) {
                object : FileObserver(dir, ALL_EVENTS) {
                    override fun onEvent(event: Int, changed: String?) {
                        Logger.vv("FileObserver", "$TAG onEvent: $event, $changed")
                        handle(event, changed)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                object : FileObserver(path, ALL_EVENTS) {
                    override fun onEvent(event: Int, changed: String?) {
                        Logger.vv("FileObserver", "$TAG onEvent: $event, $changed")
                        handle(event, changed)
                    }
                }
            }

        observer?.startWatching()
        Logger.vv("FileObserver", "$TAG startWatching for $fileName in $path")
    }

    private fun handle(event: Int, changed: String?) {
        if (changed == null) {
            Logger.vv("FileObserver", "$TAG onEvent: $event, $changed")
            return
        }

        if(DEBUG) Logger.vv("FileObserver", "$TAG onEvent: $event, $changed")
        if (changed == fileName && (event and FileObserver.MODIFY != 0)) {
            Logger.i("FileObserver", "$TAG Write detected on $fileName")

            stop() // stop observing
            onDetected() // trigger action
        }
    }

    fun stop() {
        observer?.stopWatching()
        observer = null
    }
}
