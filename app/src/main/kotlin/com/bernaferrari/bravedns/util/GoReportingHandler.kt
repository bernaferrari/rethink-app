package com.bernaferrari.bravedns.util

import Logger
import android.content.Context
import com.bernaferrari.bravedns.scheduler.EnhancedBugReport
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.VpnController
import com.celzero.firestack.backend.LogConsumer
import com.celzero.firestack.intra.Console
import com.celzero.firestack.intra.Intra
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException

/** Process-owned string-console fallback. JNI FD ownership stays native when declined. */
internal class GoReportingHandler(
    private val context: Context,
    private val preferences: PersistentState,
    private val scope: CoroutineScope,
) : Console {
    @Suppress("TooGenericExceptionCaught") // Native bindings declare arbitrary bridge exceptions.
    fun initialize(): Boolean {
        val crashFile = EnhancedBugReport.newGoCrashFile(context)
        val ready = try {
            crashFile != null && Intra.setCrashOutput(crashFile.path, crashFile.path)
        } catch (error: Exception) {
            Logger.w(Logger.LOG_TAG_BUG_REPORT, "Native crash output failed", error)
            false
        }
        if (!ready) {
            crashFile?.let { EnhancedBugReport.markFileAsClosed(it.name); it.delete() }
            Logger.w(Logger.LOG_TAG_BUG_REPORT, "Native crash output unavailable; preserving console fallback")
        }
        // Attach the console even if crash-output setup failed; declining FD readers
        // below ensures that Firestack retains its string callback fallback.
        return try {
            Intra.setupConsole(this)
            true
        } catch (error: Exception) {
            Logger.e(Logger.LOG_TAG_BUG_REPORT, "Native console setup failed", error)
            false
        }
    }

    override fun log(level: Int, message: String?) {
        val text = message?.takeIf { it.isNotBlank() } ?: return
        val severity = Logger.LoggerLevel.fromId(level) ?: return
        scope.launch {
            if (severity.stacktrace()) {
                persistCrash(text)
            } else if (severity.user()) {
                Logger.e(Logger.LOG_TAG_VPN, text)
                VpnController.stop("native-console", context, userInitiated = false)
            } else {
                Logger.goLog(text, severity)
            }
        }
    }

    private fun persistCrash(text: String) {
        val file = EnhancedBugReport.newGoCrashFile(context)
        try {
            file?.writeText(text)
            if (preferences.firebaseErrorReportingEnabled && CrashReporter.recordGoCrash(text)) file?.delete()
        } catch (error: IOException) {
            Logger.e(Logger.LOG_TAG_BUG_REPORT, "Cannot persist native crash", error)
        } finally {
            file?.let { EnhancedBugReport.markFileAsClosed(it.name) }
            EnhancedBugReport.enforceMaxFiles(context)
        }
    }

    // Returning false/null asks Firestack to keep its string callback fallback. Do not
    // claim an FD consumer unless a reader owns and drains it for its complete lifetime.
    override fun logFD(fd: Long): Boolean = false
    override fun logMemFD(fda: Long, fdb: Long, bufferSize: Long, size: Long): LogConsumer? = null
}
