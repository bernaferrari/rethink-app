/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.logs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPill
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillDefaults
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillSize
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusColors
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

/**
 * One bar in a pane's ranking: an app that asked for a domain, or a host an app reached.
 * [blocked] of [count] were blocked.
 */
data class RethinkLogGroup(
    val label: String,
    val count: Int,
    val blocked: Int,
    val monospace: Boolean = false,
    val icon: (@Composable () -> Unit)? = null,
)

data class RethinkLogPaneStrings(
    /** "Apps that asked for this domain" / "Where this app connects". */
    val groupsTitle: String,
    /** Shown when nothing is selected yet. */
    val emptySelection: String,
    val detailsTitle: String,
)

/**
 * The detail half of the wide Logs layout: the selected entry as a hero (its verdict shape or
 * app, the name, the verdict and reason), every detail in a readable column, and a ranking that
 * puts the entry in context. DNS ranks the apps that looked the domain up; network ranks the
 * hosts the app reached, each bar split into allowed and blocked.
 */
@Composable
fun RethinkLogDetailPane(
    model: RethinkLogRowModel?,
    groups: List<RethinkLogGroup>,
    strings: RethinkLogPaneStrings,
    modifier: Modifier = Modifier,
) {
    val motion = LocalRethinkMotion.current
    val fadeSpec = if (motion.reducedMotion) snap() else MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(SharedDimensions.cornerRadius3xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        AnimatedContent(
            targetState = model,
            contentKey = { it?.id },
            transitionSpec = { fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) },
            label = "logPane",
        ) { selected ->
            if (selected == null) {
                Box(Modifier.fillMaxSize().padding(SharedDimensions.spacingXl), contentAlignment = Alignment.Center) {
                    Text(strings.emptySelection, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                PaneContent(selected, groups, strings)
            }
        }
    }
}

@Composable
private fun PaneContent(model: RethinkLogRowModel, groups: List<RethinkLogGroup>, strings: RethinkLogPaneStrings) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SharedDimensions.spacingXl),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg)) {
            when (model.kind) {
                RethinkLogKind.Dns -> DnsQueryMark(model.isBlocked, model.icon, PaneMarkSize)
                RethinkLogKind.Network -> Box(Modifier.size(PaneMarkSize).clip(RoundedCornerShape(SharedDimensions.cornerRadiusXl)), contentAlignment = Alignment.Center) {
                    val container = if (model.isBlocked) RethinkStatusColors.blockedContainer else RethinkStatusColors.allowedContainer
                    model.icon?.invoke(container) ?: Box(Modifier.size(PaneMarkSize).background(container, CircleShape))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                val title = if (model.kind == RethinkLogKind.Dns) model.destination else model.appLabel
                if (model.kind == RethinkLogKind.Dns) {
                    Text(rethinkDomainText(title, scheme.onSurface, scheme.onSurfaceVariant), style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                } else {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                }
                Text(
                    text = if (model.kind == RethinkLogKind.Dns) model.appLabel else model.destination,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (model.kind == RethinkLogKind.Network) FontFamily.Monospace else null,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm), verticalAlignment = Alignment.CenterVertically) {
            val verdict = if (model.isBlocked) model.reason?.takeIf { it.isNotBlank() } ?: model.blockedLabel else model.allowedLabel
            RethinkPill(
                text = verdict,
                colors = if (model.isBlocked) RethinkPillDefaults.blocked() else RethinkPillDefaults.allowed(),
                size = RethinkPillSize.Large,
                leadingIcon = if (model.isBlocked) MaterialSymbols.Filled.Block else MaterialSymbols.Filled.CheckCircle,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f, fill = false),
            )
            RethinkPill(model.typeLabel, size = RethinkPillSize.Large, fontWeight = FontWeight.Bold)
            RethinkPill(model.timeLabel, size = RethinkPillSize.Large)
        }
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
        SectionTitle(strings.detailsTitle)
        LogDetailsPanel(model, inPane = true)
        if (groups.isNotEmpty()) {
            Spacer(Modifier.height(SharedDimensions.spacingXs))
            SectionTitle(strings.groupsTitle)
            RethinkLogGroupBars(groups)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

/** A ranked list where every bar splits into allowed (green) and blocked (error) traffic. */
@Composable
fun RethinkLogGroupBars(groups: List<RethinkLogGroup>, modifier: Modifier = Modifier) {
    val max = groups.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val scheme = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)) {
        groups.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                    group.icon?.let { icon -> Box(Modifier.size(SharedDimensions.iconSizeSm).clip(CircleShape)) { icon() } }
                    Text(
                        group.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = if (group.monospace) FontFamily.Monospace else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        group.count.toString(),
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                    )
                }
                val share = group.count.toFloat() / max
                val blockedShare = if (group.count == 0) 0f else group.blocked.toFloat() / group.count
                Row(Modifier.fillMaxWidth().height(BarHeight).clip(CircleShape).background(scheme.surfaceContainerHighest)) {
                    if (share * (1f - blockedShare) > 0f) {
                        Box(Modifier.weight(share * (1f - blockedShare)).fillMaxHeight().background(RethinkStatusColors.allowed))
                    }
                    if (share * blockedShare > 0f) {
                        Box(Modifier.weight(share * blockedShare).fillMaxHeight().background(RethinkStatusColors.blocked))
                    }
                    if (share < 1f) Spacer(Modifier.weight(1f - share))
                }
            }
        }
    }
}

private val PaneMarkSize = 64.dp
private val BarHeight = 8.dp
