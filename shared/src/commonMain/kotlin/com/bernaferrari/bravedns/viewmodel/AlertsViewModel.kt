package com.bernaferrari.bravedns.viewmodel

import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Provided
import androidx.lifecycle.ViewModel
import com.bernaferrari.bravedns.database.ConnectionTrackerDAO
import com.bernaferrari.bravedns.database.DnsLogDAO
import com.bernaferrari.bravedns.platform.currentTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow

/** Shared alert query state. Host targets only supply the shared log data sources. */
@KoinViewModel
class AlertsViewModel(
    @Provided private val connectionTrackerDao: ConnectionTrackerDAO,
    @Provided private val dnsLogDao: DnsLogDAO,
) : ViewModel() {
    private val ipLogList = MutableStateFlow("")
    private val domainLogList = MutableStateFlow("")
    private val appLogList = MutableStateFlow("")
    private val fromTime = MutableStateFlow(currentTimeMillis() - ALERT_WINDOW_MILLIS)
    private val toTime = MutableStateFlow(currentTimeMillis())

    private companion object {
        const val ALERT_WINDOW_MILLIS = 60L * 60L * 1_000L
    }
}
