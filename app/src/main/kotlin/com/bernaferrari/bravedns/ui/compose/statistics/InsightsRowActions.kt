/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.statistics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConnection
import com.bernaferrari.bravedns.data.SummaryStatisticsType
import com.bernaferrari.bravedns.database.ConnectionTrackerRepository
import com.bernaferrari.bravedns.database.DnsLogRepository
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.ui.compose.logs.ConnectionSheetNavigation
import com.bernaferrari.bravedns.ui.compose.logs.LogConnectionSheet
import com.bernaferrari.bravedns.ui.compose.logs.LogSheetTarget
import com.bernaferrari.bravedns.ui.compose.logs.findLogSheetTarget
import com.bernaferrari.bravedns.viewmodel.SummaryStatisticsViewModel.TimeCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Where Insights rows lead (journey b): apps open App detail, domains and IPs open the Connection
 * sheet for their newest logged entry, ASNs open the connections behind them. Country rows keep
 * expanding in place. Null actions leave every row inert (previews).
 */
@Immutable
data class InsightsRowActions(
    val eventLogger: EventLogger,
    val connections: ConnectionTrackerRepository,
    val lookups: DnsLogRepository,
    val onOpenApp: (uid: Int) -> Unit,
    val onOpenAsn: (asn: String, blocked: Boolean, timeCategory: TimeCategory) -> Unit,
    val sheetNavigation: ConnectionSheetNavigation,
    val loadFavIcon: Boolean = false,
)

/** Row clicks for one Insights list, plus the sheet they open ([Sheet]). */
internal class InsightsRowHandler(
    private val actions: InsightsRowActions?,
    private val launch: (suspend () -> Unit) -> Unit,
    private val allAppsLabel: String,
) {
    var target by mutableStateOf<LogSheetTarget?>(null)
        private set

    fun onClick(type: SummaryStatisticsType, item: AppConnection, window: TimeCategory): (() -> Unit)? {
        val actions = actions ?: return null
        return when (type) {
            SummaryStatisticsType.MOST_CONNECTED_APPS, SummaryStatisticsType.MOST_BLOCKED_APPS,
            SummaryStatisticsType.TOP_ACTIVE_CONNS,
            -> item.uid.takeIf { it >= 0 }?.let { uid -> { actions.onOpenApp(uid) } }
            SummaryStatisticsType.MOST_CONTACTED_DOMAINS, SummaryStatisticsType.MOST_BLOCKED_DOMAINS -> {
                val domain = item.appOrDnsName?.takeIf { it.isNotBlank() } ?: return null
                return { open(actions, domain = domain, ip = null, blocked = type == SummaryStatisticsType.MOST_BLOCKED_DOMAINS, window) }
            }
            SummaryStatisticsType.MOST_CONTACTED_IPS, SummaryStatisticsType.MOST_BLOCKED_IPS -> {
                val ip = item.ipAddress.takeIf { it.isNotBlank() } ?: return null
                return { open(actions, domain = null, ip = ip, blocked = type == SummaryStatisticsType.MOST_BLOCKED_IPS, window) }
            }
            SummaryStatisticsType.MOST_CONNECTED_ASN, SummaryStatisticsType.MOST_BLOCKED_ASN -> {
                val asn = item.appOrDnsName?.takeIf { it.isNotBlank() } ?: return null
                return { actions.onOpenAsn(asn, type == SummaryStatisticsType.MOST_BLOCKED_ASN, window) }
            }
            SummaryStatisticsType.MOST_CONTACTED_COUNTRIES -> null
        }
    }

    private fun open(actions: InsightsRowActions, domain: String?, ip: String?, blocked: Boolean, window: TimeCategory) {
        val since = System.currentTimeMillis() - window.millis()
        launch {
            target = withContext(Dispatchers.IO) {
                findLogSheetTarget(actions.connections, actions.lookups, domain, ip, blocked, since, allAppsLabel)
            }
        }
    }

    @Composable
    fun Sheet() {
        val actions = actions ?: return
        val current = target ?: return
        LogConnectionSheet(
            target = current,
            eventLogger = actions.eventLogger,
            navigation = actions.sheetNavigation,
            loadFavIcon = actions.loadFavIcon,
            onDismiss = { target = null },
        )
    }
}

@Composable
internal fun rememberInsightsRowHandler(actions: InsightsRowActions?): InsightsRowHandler {
    val scope = rememberCoroutineScope()
    val allApps = stringResource(R.string.lbl_all_apps)
    return remember(actions, allApps) { InsightsRowHandler(actions, { block -> scope.launch { block() } }, allApps) }
}

private fun TimeCategory.millis(): Long = when (this) {
    TimeCategory.ONE_HOUR -> HOUR_MILLIS
    TimeCategory.TWENTY_FOUR_HOUR -> 24 * HOUR_MILLIS
    TimeCategory.SEVEN_DAYS -> 7 * 24 * HOUR_MILLIS
}

private const val HOUR_MILLIS = 60 * 60 * 1000L
