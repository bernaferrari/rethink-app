package com.celzero.bravedns.ui.compose.about

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.celzero.bravedns.RethinkDnsApplication.Companion.DEBUG
import com.celzero.bravedns.scheduler.WorkScheduler
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.util.Constants.Companion.TIME_FORMAT_4
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.getPackageMetadata
import com.celzero.bravedns.util.Utilities.getRandomString
import com.celzero.bravedns.util.Utilities.isFdroidFlavour
import com.celzero.bravedns.util.Utilities.isPlayStoreFlavour
import com.celzero.bravedns.util.workInfosByTagFlow
import com.celzero.firestack.intra.Intra
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Android-only PackageManager adapter for shared About state. */
class AndroidAppMetadataProvider(context: Context) : AppMetadataProvider {
    private val appContext = context.applicationContext

    override fun metadata(): AppMetadata {
        val packageInfo = getPackageMetadata(appContext.packageManager, appContext.packageName)
        val lastUpdated = packageInfo?.lastUpdateTime?.takeIf { it > 0L }?.let {
            Utilities.convertLongToTime(it, TIME_FORMAT_4)
        }.orEmpty()
        return AppMetadata(
            versionName = packageInfo?.versionName.orEmpty(),
            installSource = when {
                isFdroidFlavour() -> "F-Droid"
                isPlayStoreFlavour() -> "Google Play"
                else -> "Website"
            },
            buildNumber = Intra.build(false),
            lastUpdated = lastUpdated,
            firstInstalledAtMillis = packageInfo?.firstInstallTime ?: 0L,
            isFdroid = isFdroidFlavour(),
            isPlayStore = isPlayStoreFlavour(),
            isDebug = DEBUG,
        )
    }
}

class AndroidAboutPreferences(private val persistentState: PersistentState) : AboutPreferences {
    override val firebaseUserToken: String
        get() = persistentState.firebaseUserToken

    override val firebaseErrorReportingEnabled: Boolean
        get() = persistentState.firebaseErrorReportingEnabled

    override fun updateFirebaseUserToken(token: String, timestampMillis: Long) {
        persistentState.firebaseUserToken = token
        persistentState.firebaseUserTokenTimestamp = timestampMillis
    }
}

class AndroidBugReportController(
    context: Context,
    private val workScheduler: WorkScheduler,
) : BugReportController {
    private val appContext = context.applicationContext

    override val isRunning: Flow<Boolean> =
        WorkManager.getInstance(appContext)
            .workInfosByTagFlow(WorkScheduler.APP_EXIT_INFO_ONE_TIME_JOB_TAG)
            .map { workInfos ->
                workInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            }
            .distinctUntilChanged()

    override fun trigger(): Boolean {
        if (WorkScheduler.isWorkRunning(appContext, WorkScheduler.APP_EXIT_INFO_JOB_TAG)) return false
        workScheduler.scheduleOneTimeWorkForAppExitInfo()
        return true
    }
}

object AndroidAboutTokenGenerator : AboutTokenGenerator {
    override fun generate(): String = getRandomString(TOKEN_LENGTH)

    private const val TOKEN_LENGTH = 16
}
