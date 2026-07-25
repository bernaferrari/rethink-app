package com.celzero.bravedns.ui.compose.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.celzero.bravedns.platform.currentTimeMillis
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Provided

/** Minimal preferences contract required by the shared About state. */
interface AboutPreferences {
    val firebaseUserToken: String
    val firebaseErrorReportingEnabled: Boolean

    fun updateFirebaseUserToken(token: String, timestampMillis: Long)
}

/** Platform scheduling bridge for the optional App Exit Info report. */
interface BugReportController {
    val isRunning: Flow<Boolean>

    /** Returns false when a report is already queued or cannot be scheduled. */
    fun trigger(): Boolean
}

fun interface AboutTokenGenerator {
    fun generate(): String
}

/**
 * Shared About state and business rules. Android supplies real package/work metadata; demo
 * targets can supply a deterministic mock without importing PackageManager or WorkManager.
 */
@KoinViewModel
class AboutViewModel(
    @Provided private val metadataProvider: AppMetadataProvider,
    @Provided private val preferences: AboutPreferences,
    @Provided private val bugReportController: BugReportController,
    @Provided private val tokenGenerator: AboutTokenGenerator,
) : ViewModel() {

    private val uiStateMutable = MutableStateFlow(AboutUiState())
    val uiState: StateFlow<AboutUiState> = uiStateMutable.asStateFlow()

    init {
        updateUiState()
        bugReportController.isRunning
            .onEach { running -> uiStateMutable.update { it.copy(isBugReportRunning = running) } }
            .launchIn(viewModelScope)
    }

    fun updateUiState() {
        val metadata = metadataProvider.metadata()
        val version = metadata.versionName
        val sponsorInfo = calculateSponsorInfo(metadata)

        uiStateMutable.update {
            it.copy(
                versionName = version,
                slicedVersion = version.take(7),
                installSource = metadata.installSource,
                buildNumber = metadata.buildNumber,
                lastUpdated = metadata.lastUpdated,
                daysSinceInstall = sponsorInfo.first,
                sponsoredAmount = sponsorInfo.second,
                firebaseToken = preferences.firebaseUserToken,
                isFirebaseEnabled = preferences.firebaseErrorReportingEnabled,
                isFdroid = metadata.isFdroid,
                isPlayStore = metadata.isPlayStore,
                isDebug = metadata.isDebug,
            )
        }
    }

    fun generateNewToken() {
        if (metadataProvider.metadata().isFdroid) return

        val token = tokenGenerator.generate()
        preferences.updateFirebaseUserToken(token, currentTimeMillis())
        uiStateMutable.update { it.copy(firebaseToken = token) }
    }

    fun triggerBugReport() {
        if (bugReportController.trigger()) {
            uiStateMutable.update { it.copy(isBugReportRunning = true) }
        }
    }

    fun setBugReportRunning(isRunning: Boolean) {
        uiStateMutable.update { it.copy(isBugReportRunning = isRunning) }
    }

    private fun calculateSponsorInfo(metadata: AppMetadata): Pair<String, String> {
        val now = currentTimeMillis()
        val installTime = metadata.firstInstalledAtMillis.takeIf { it > 0L } ?: now
        val days = ((now - installTime).coerceAtLeast(0L) / MILLIS_PER_DAY)
        val cents = ((days.toDouble() / DAYS_PER_MONTH) * MONTHLY_SPONSOR_CENTS).roundToLong()
        val amount = "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
        return days.toString() to amount
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
        const val DAYS_PER_MONTH = 30.0
        const val MONTHLY_SPONSOR_CENTS = 80.0
    }
}
