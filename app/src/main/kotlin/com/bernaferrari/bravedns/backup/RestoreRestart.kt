package com.bernaferrari.bravedns.backup

import android.content.Context
import java.io.File
import com.bernaferrari.bravedns.backup.BackupHelper.Companion.INTENT_RESTART_APP

object RestoreRestart {
    const val MARKER = "restore-restart-required"
    fun required(context: Context): Boolean = File(context.filesDir, MARKER).exists()

    suspend fun refreshBlocklists(
        context: Context,
        state: com.bernaferrari.bravedns.service.PersistentState,
    ) {
        if (state.remoteBlocklistTimestamp > com.bernaferrari.bravedns.util.Constants.PACKAGED_REMOTE_FILETAG_TIMESTAMP) {
            val loaded = com.bernaferrari.bravedns.service.RethinkBlocklistManager.readJson(
                context, com.bernaferrari.bravedns.service.RethinkBlocklistManager.DownloadType.REMOTE,
                state.remoteBlocklistTimestamp,
            )
            if (loaded) return
        }
        com.bernaferrari.bravedns.util.RemoteFileTagUtil.moveFileToLocalDir(context.applicationContext, state)
    }

    fun restart(context: Context) {
        File(context.filesDir, MARKER).delete()
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val component = launch?.component ?: return
        val intent = android.content.Intent.makeRestartActivityTask(component)
            .putExtra(INTENT_RESTART_APP, true)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

}
