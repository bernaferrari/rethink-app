/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.logs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion
import com.bernaferrari.bravedns.ui.compose.theme.RethinkKeyValueRow
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPill
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillDefaults
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillSize
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusColors
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedListShape
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.ui.compose.theme.rethinkLatencyTone
import com.bernaferrari.bravedns.ui.compose.theme.rethinkLatencyBand
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLatencyBand

data class RethinkLogDetail(
    val label: String,
    val value: String,
    val monospace: Boolean = false,
    val isError: Boolean = false,
)

/** Localised copy for the latency meter shown in expanded DNS log rows. */
data class RethinkLogLatencyStrings(
    val title: String,
    val fast: String,
    val ok: String,
    val slow: String,
)

/**
 * The two logs read differently on purpose. A network log is about an app: who connected, where
 * to, over what, and the verdict. A DNS log is about a name: which domain was looked up, what the
 * resolver answered (or which blocklist stopped it), and which app asked.
 */
enum class RethinkLogKind { Network, Dns }

data class RethinkLogRowModel(
    val id: String,
    val destination: String,
    val appLabel: String,
    val typeLabel: String,
    val timeLabel: String,
    val isBlocked: Boolean,
    val allowedLabel: String,
    val blockedLabel: String,
    val details: List<RethinkLogDetail>,
    /** Network: the app icon. DNS: the site's favicon, when one is cached (null otherwise). */
    val icon: (@Composable (statusContainer: Color) -> Unit)? = null,
    val latencyMs: Long? = null,
    val latencyStrings: RethinkLogLatencyStrings? = null,
    val blocklistsLabel: String? = null,
    val onBlocklistsClick: (() -> Unit)? = null,
    val kind: RethinkLogKind = RethinkLogKind.Network,
    /** Why: the firewall rule that decided a connection, or the blocklists that stopped a query. */
    val reason: String? = null,
    /** Network: the address and port. DNS: the first answer. */
    val endpoint: String? = null,
    /** Short readouts after the protocol (data moved, latency). */
    val facts: List<String> = emptyList(),
    /** DNS rows name the asking app on their last line, with this small icon. */
    val appIcon: (@Composable () -> Unit)? = null,
)

/**
 * Portable log card. Platform code supplies data, formatted copy, and real app/fav icons; the
 * row geometry for both kinds, the interaction, and the expansion stay shared.
 *
 * With [onSelect] the card selects instead of expanding (list–detail layouts show the details
 * in a pane beside the list); [selected] then tints it.
 */
@Composable
fun RethinkLogRow(
    model: RethinkLogRowModel,
    index: Int,
    itemCount: Int,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onSelect: (() -> Unit)? = null,
) {
    val motion = LocalRethinkMotion.current
    var expanded by remember(model.id) { mutableStateOf(false) }
    var keepDetails by remember(model.id) { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scheme = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme
    val inline = onSelect == null
    val scale by animateFloatAsState(
        targetValue = if (pressed && !motion.reducedMotion) 0.985f else 1f,
        animationSpec = if (motion.reducedMotion) snap() else spring(stiffness = 420f, dampingRatio = 0.82f),
        label = "log_row_scale",
    )
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (motion.reducedMotion) snap() else motionScheme.fastSpatialSpec(),
        label = "log_row_chevron",
    )
    val detailsProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = if (motion.reducedMotion) snap() else motionScheme.defaultEffectsSpec(),
        label = "log_row_details",
        finishedListener = { if (it == 0f) keepDetails = false },
    )
    val baseColor = when {
        selected -> scheme.secondaryContainer
        expanded -> scheme.surfaceContainer
        else -> scheme.surfaceContainerLow
    }
    val cardColor by animateColorAsState(
        targetValue = if (pressed) lerp(baseColor, scheme.primaryContainer, 0.18f) else baseColor,
        animationSpec = if (motion.reducedMotion) snap() else motionScheme.fastEffectsSpec(),
        label = "log_row_color",
    )
    val shape = if (selected) {
        RoundedCornerShape(SharedDimensions.groupCornerRadius)
    } else {
        rethinkGroupedListShape(cardPositionFor(index, itemCount - 1))
    }

    Surface(
        onClick = {
            if (onSelect != null) {
                onSelect()
            } else {
                expanded = !expanded
                if (expanded) keepDetails = true
            }
        },
        interactionSource = interactionSource,
        shape = shape,
        color = cardColor,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(shape)
            .semantics { this.selected = selected },
    ) {
        Column(Modifier.fillMaxWidth()) {
            when (model.kind) {
                RethinkLogKind.Network -> NetworkLogHeader(model, cardColor, if (inline) chevron else null)
                RethinkLogKind.Dns -> DnsLogHeader(model, cardColor, if (inline) chevron else null)
            }
            if (inline && keepDetails) {
                Box(Modifier.fillMaxWidth().logAccordionReveal(detailsProgress)) {
                    LogDetailsPanel(model)
                }
            }
        }
    }
}

private val RowPadding = Modifier.padding(
    start = SharedDimensions.spacingLg,
    end = SharedDimensions.spacingMd,
    top = SharedDimensions.spacingMd,
    bottom = SharedDimensions.spacingMd,
)
private val LeadingSize = 44.dp
private val AppIconSize = 40.dp
private val VerdictBadgeSize = 18.dp

/** Network: the app leads, with its verdict as a badge; then where it went and how. */
@Composable
private fun NetworkLogHeader(model: RethinkLogRowModel, cardColor: Color, chevron: Float?) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = RowPadding.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
    ) {
        AppVerdictMark(model.isBlocked, cardColor) {
            val container = if (model.isBlocked) RethinkStatusColors.blockedContainer else RethinkStatusColors.allowedContainer
            model.icon?.invoke(container) ?: Box(Modifier.size(AppIconSize).background(container, CircleShape))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile)) {
            TitleAndTime(model.appLabel, model.timeLabel, chevron) { title, mod ->
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = mod)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                Icon(
                    MaterialSymbols.Filled.ArrowOutward,
                    contentDescription = null,
                    tint = if (model.isBlocked) RethinkStatusColors.blocked else RethinkStatusColors.allowed,
                    modifier = Modifier.size(SharedDimensions.iconSizeXs),
                )
                Text(
                    text = model.destination,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(SharedDimensions.spacingXs))
            Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs), verticalAlignment = Alignment.CenterVertically) {
                VerdictPill(model)
                RethinkPill(model.typeLabel, size = RethinkPillSize.Small, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                model.facts.firstOrNull()?.let { fact ->
                    RethinkPill(fact, size = RethinkPillSize.Small, modifier = Modifier.weight(1f, fill = false))
                }
            }
        }
    }
}

/**
 * DNS: the name leads; then the answer or the blocklist; then who asked. The app that asked is
 * the mark, with its verdict as a badge like network rows; a query with no app gets the verdict shape.
 */
@Composable
private fun DnsLogHeader(model: RethinkLogRowModel, cardColor: Color, chevron: Float?) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = RowPadding.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
    ) {
        val appIcon = model.appIcon
        if (appIcon != null) {
            AppVerdictMark(model.isBlocked, cardColor) { Box(Modifier.size(AppIconSize)) { appIcon() } }
        } else {
            DnsQueryMark(model.isBlocked, model.icon, LeadingSize)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
            TitleAndTime(model.destination, model.timeLabel, chevron) { title, mod ->
                Text(rethinkDomainText(title, scheme.onSurface, scheme.onSurfaceVariant), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = mod)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs), verticalAlignment = Alignment.CenterVertically) {
                if (model.isBlocked) {
                    VerdictPill(model)
                } else {
                    RethinkPill(
                        text = model.endpoint ?: model.allowedLabel,
                        colors = RethinkPillDefaults.allowed(),
                        size = RethinkPillSize.Small,
                        leadingIcon = MaterialSymbols.AutoMirrored.Filled.ArrowForward,
                        monospace = model.endpoint != null,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                RethinkPill(model.typeLabel, size = RethinkPillSize.Small, fontWeight = FontWeight.Bold)
                model.facts.firstOrNull()?.let { RethinkPill(it, size = RethinkPillSize.Small) }
            }
            Text(model.appLabel, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TitleAndTime(
    title: String,
    time: String,
    chevron: Float?,
    titleContent: @Composable (String, Modifier) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm), verticalAlignment = Alignment.CenterVertically) {
        titleContent(title, Modifier.weight(1f))
        Text(time, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = scheme.onSurfaceVariant, maxLines = 1)
        if (chevron != null) {
            // Inline accordion, not navigation: a down chevron that flips.
            Icon(MaterialSymbols.Filled.KeyboardArrowDown, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(SharedDimensions.iconSizeXs).rotate(chevron))
        }
    }
}

@Composable
private fun VerdictPill(model: RethinkLogRowModel) {
    val label = if (model.isBlocked) model.reason?.takeIf { it.isNotBlank() } ?: model.blockedLabel else model.allowedLabel
    RethinkPill(
        text = label,
        colors = if (model.isBlocked) RethinkPillDefaults.blocked() else RethinkPillDefaults.allowed(),
        size = RethinkPillSize.Small,
        leadingIcon = if (model.isBlocked) MaterialSymbols.Filled.Block else MaterialSymbols.Filled.Check,
    )
}

/** An app icon with its verdict badged at the bottom end: the lead of network and DNS rows alike. */
@Composable
private fun AppVerdictMark(blocked: Boolean, ringColor: Color, icon: @Composable () -> Unit) {
    Box(Modifier.size(LeadingSize)) {
        Box(Modifier.size(AppIconSize).align(Alignment.Center), contentAlignment = Alignment.Center) { icon() }
        VerdictBadge(blocked, ringColor = ringColor, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

/** The verdict as a small badge on the app icon, ringed in the card color so it reads as a cut-out. */
@Composable
private fun VerdictBadge(blocked: Boolean, ringColor: Color, modifier: Modifier = Modifier) {
    val color = if (blocked) RethinkStatusColors.blocked else RethinkStatusColors.allowed
    Box(
        modifier = modifier
            .size(VerdictBadgeSize)
            .border(2.dp, ringColor, CircleShape)
            .padding(2.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (blocked) MaterialSymbols.Filled.Close else MaterialSymbols.Filled.Check,
            contentDescription = null,
            tint = if (blocked) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(10.dp),
        )
    }
}

/**
 * The DNS verdict as a shape: an allowed answer is a calm circle, a blocked one a sharp-lobed
 * cookie in the error container. A cached favicon sits inside when there is one.
 */
@Composable
fun DnsQueryMark(blocked: Boolean, favicon: (@Composable (Color) -> Unit)?, size: Dp, modifier: Modifier = Modifier) {
    val blockedShape = MaterialShapes.Cookie6Sided.toShape()
    val container = if (blocked) RethinkStatusColors.blockedContainer else RethinkStatusColors.allowedContainer
    val content = if (blocked) RethinkStatusColors.onBlockedContainer else RethinkStatusColors.onAllowedContainer
    Box(
        modifier = modifier.size(size).background(container, if (blocked) blockedShape else CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (favicon != null) {
            Box(Modifier.size(size * 0.5f).clip(RoundedCornerShape(SharedDimensions.cornerRadiusXs))) { favicon(container) }
        } else {
            Icon(
                if (blocked) MaterialSymbols.Filled.Block else MaterialSymbols.Filled.Language,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(size * 0.46f),
            )
        }
    }
}

/** A domain with its registrable part emphasized: `telemetry.` quiet, `glyphkeys.io` strong. */
fun rethinkDomainText(domain: String, strong: Color, quiet: Color): AnnotatedString {
    val trimmed = domain.trimEnd('.')
    val labels = trimmed.split('.')
    val keep = when {
        labels.size < 2 -> labels.size
        labels.size >= 3 && labels.last().length == 2 && labels[labels.size - 2] in SecondLevelSuffixes -> 3
        else -> 2
    }
    val split = trimmed.length - labels.takeLast(keep).joinToString(".").length
    return buildAnnotatedString {
        withStyle(SpanStyle(color = quiet)) { append(trimmed.take(split)) }
        withStyle(SpanStyle(color = strong, fontWeight = FontWeight.SemiBold)) { append(trimmed.drop(split)) }
    }
}

private val SecondLevelSuffixes = setOf("co", "com", "org", "net", "ac", "gov", "edu")

@Composable
internal fun LogDetailsPanel(model: RethinkLogRowModel, inPane: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        if (!inPane) HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.42f), thickness = SharedDimensions.dividerThickness)
        Column(
            Modifier.padding(
                start = if (inPane) SharedDimensions.spacingNone else SharedDimensions.spacingLg + LeadingSize + SharedDimensions.spacingMd,
                end = if (inPane) SharedDimensions.spacingNone else SharedDimensions.spacingLg,
                top = SharedDimensions.spacingSmMd,
                bottom = if (inPane) SharedDimensions.spacingNone else SharedDimensions.spacingLg,
            ),
        ) {
            model.latencyMs?.let { LogLatency(it, model.latencyStrings) }
            if (model.latencyMs != null && model.details.isNotEmpty()) Spacer(Modifier.height(SharedDimensions.spacingSm))
            model.details.forEach { detail ->
                RethinkKeyValueRow(
                    label = detail.label,
                    value = detail.value,
                    modifier = Modifier.padding(vertical = if (inPane) SharedDimensions.spacingSm else SharedDimensions.spacingXs),
                    valueColor = if (detail.isError) scheme.error else scheme.onSurface,
                    monospace = detail.monospace,
                    labelStyle = if (inPane) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelSmall,
                    valueStyle = if (inPane) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelSmall,
                    valueMaxLines = 2,
                )
            }
            model.blocklistsLabel?.takeIf { it.isNotBlank() }?.let { label ->
                Spacer(Modifier.height(SharedDimensions.spacingSm))
                val blocklistShape = RoundedCornerShape(SharedDimensions.cornerRadiusMd)
                Surface(
                    onClick = model.onBlocklistsClick ?: {},
                    enabled = model.onBlocklistsClick != null,
                    shape = blocklistShape,
                    color = RethinkStatusColors.blockedContainer,
                    contentColor = RethinkStatusColors.onBlockedContainer,
                    modifier = Modifier.fillMaxWidth().clip(blocklistShape),
                ) {
                    Row(
                        Modifier.padding(horizontal = SharedDimensions.spacingMd, vertical = SharedDimensions.spacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
                    ) {
                        Icon(MaterialSymbols.Filled.Block, null, modifier = Modifier.size(SharedDimensions.iconSizeXs))
                        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogLatency(latency: Long, strings: RethinkLogLatencyStrings?) {
    val scheme = MaterialTheme.colorScheme
    val color = rethinkLatencyTone(latency)
    val quality = when (rethinkLatencyBand(latency)) {
        RethinkLatencyBand.Fast -> strings?.fast
        RethinkLatencyBand.Ok -> strings?.ok
        RethinkLatencyBand.Slow -> strings?.slow
        RethinkLatencyBand.Unknown -> null
    }
    val label = if (quality.isNullOrBlank()) "${latency}ms" else "${latency}ms · $quality"
    val fraction = (latency.toFloat() / 100f).coerceIn(0.04f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(strings?.title.orEmpty(), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = color)
        }
        LinearWavyProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = scheme.outlineVariant.copy(alpha = 0.35f),
        )
    }
}

private fun Modifier.logAccordionReveal(progress: Float): Modifier {
    val normalized = progress.coerceIn(0f, 1f)
    return graphicsLayer { alpha = normalized }.clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val height = (placeable.height * normalized).toInt()
        layout(placeable.width, height) { if (height > 0) placeable.place(0, 0) }
    }
}
