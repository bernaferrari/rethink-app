package com.bernaferrari.bravedns.ui.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.LocalRethinkMotion
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun <T> RethinkIndexedFastScroller(
    items: List<T>,
    listState: LazyListState,
    getIndexKey: (T) -> String,
    modifier: Modifier = Modifier,
    minItemCount: Int = 24,
    onInteractionStart: () -> Unit = {},
    onInteractionEnd: () -> Unit = {},
    scrollItemOffset: Int = 0,
) {
    if (items.size < minItemCount) return

    val motion = LocalRethinkMotion.current
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val labelToIndex =
        remember(items, items.hashCode()) {
            val map = mutableMapOf<String, Int>()
            items.forEachIndexed { index, item ->
                val label = indexLabel(getIndexKey(item))
                if (label !in map) map[label] = index
            }
            map
        }
    val labels =
        remember(labelToIndex) {
            labelToIndex.keys.sortedWith(
                compareBy<String> { if (it == "#") 0 else 1 }.thenBy { it },
            )
        }
    if (labels.isEmpty()) return

    val trackInset = 14.dp
    val labelSlotHeight = 22.dp
    val bubbleSize = 72.dp
    val bubbleOffsetX = (-60).dp
    val maxTrackHeight = 520.dp
    val preferredTrackHeight = maxOf(
        bubbleSize,
        labelSlotHeight * labels.size + trackInset * 2,
    )
    val trackInsetPx = with(density) { trackInset.toPx() }
    val bubbleSizePx = with(density) { bubbleSize.toPx() }

    var trackSize by remember { mutableStateOf(IntSize.Zero) }
    var currentDragY by remember { mutableFloatStateOf(0f) }
    var currentLabel by remember { mutableStateOf("") }
    var previousLabel by remember { mutableStateOf("") }
    var interacting by remember { mutableStateOf(false) }

    fun scrollToLabel(label: String) {
        val targetIndex = labelToIndex[label] ?: return
        coroutineScope.launch {
            listState.scrollToItem((targetIndex - scrollItemOffset).coerceAtLeast(0))
        }
    }

    fun selectFromPosition(y: Float) {
        if (trackSize.height <= 0) return

        val trackHeight = trackSize.height.toFloat()
        val clampedY = y.coerceIn(trackInsetPx, trackHeight - trackInsetPx)
        val usableHeight = (trackHeight - trackInsetPx * 2f).coerceAtLeast(1f)
        val progress = ((clampedY - trackInsetPx) / usableHeight).coerceIn(0f, 1f)
        val index = (progress * labels.lastIndex).roundToInt().coerceIn(0, labels.lastIndex)
        val selectedLabel = labels[index]

        if (selectedLabel != previousLabel) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            previousLabel = selectedLabel
        }

        currentLabel = selectedLabel
        currentDragY =
            if (labels.size > 1) {
                trackInsetPx + index / labels.lastIndex.toFloat() * usableHeight
            } else {
                trackHeight / 2f
            }
        scrollToLabel(selectedLabel)
    }

    Box(
        modifier =
            modifier
                .width(SharedDimensions.touchTargetSm)
                .heightIn(max = maxTrackHeight)
                .height(preferredTrackHeight),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(vertical = trackInset)
                    .onGloballyPositioned { trackSize = it.size }
                    .pointerInput(labels, items.hashCode()) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown()
                                interacting = true
                                onInteractionStart()
                                selectFromPosition(down.position.y)

                                val change =
                                    awaitTouchSlopOrCancellation(down.id) { pointerChange, _ ->
                                        pointerChange.consume()
                                    }
                                if (change != null) {
                                    drag(change.id) { dragChange ->
                                        selectFromPosition(dragChange.position.y)
                                    }
                                }

                                interacting = false
                                previousLabel = ""
                                onInteractionEnd()
                            }
                        }
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val usableHeight = (trackSize.height - trackInsetPx * 2f).coerceAtLeast(1f)
            val dragProgress = ((currentDragY - trackInsetPx) / usableHeight).coerceIn(0f, 1f)

            labels.forEachIndexed { index, label ->
                val labelPosition =
                    if (labels.size > 1) index / labels.lastIndex.toFloat() else 0.5f
                val distance = abs(labelPosition - dragProgress)
                val scale by animateFloatAsState(
                    targetValue =
                        if (interacting) {
                            when {
                                distance < 0.06f -> 1.45f
                                distance < 0.12f -> 1.2f
                                else -> 0.95f
                            }
                        } else {
                            1f
                        },
                    animationSpec = if (motion.reducedMotion) snap() else spring(dampingRatio = 0.82f),
                    label = "fastScrollerScale_$index",
                )
                val alpha by animateFloatAsState(
                    targetValue =
                        if (interacting) {
                            when {
                                distance < 0.06f -> 1f
                                distance < 0.12f -> 0.85f
                                else -> 0.55f
                            }
                        } else {
                            0.72f
                        },
                    animationSpec = if (motion.reducedMotion) snap() else spring(),
                    label = "fastScrollerAlpha_$index",
                )

                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                },
                    )
                }
            }
        }

        if (interacting && currentLabel.isNotEmpty()) {
            val bubbleYPx =
                (currentDragY - bubbleSizePx / 2f)
                    .coerceIn(0f, (trackSize.height - bubbleSizePx).coerceAtLeast(0f))
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .size(bubbleSize)
                        .offset {
                            IntOffset(
                                x = with(density) { bubbleOffsetX.roundToPx() },
                                y = bubbleYPx.roundToInt(),
                            )
                        },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 6.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = currentLabel,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun indexLabel(raw: String): String {
    val key = raw.trim()
    if (key.isEmpty()) return "#"
    if (key.all(Char::isDigit) && key.length <= 3) return key

    val first = key.first().uppercaseChar()
    return if (first.isLetter()) first.toString() else "#"
}
