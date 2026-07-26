/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bernaferrari.bravedns.ui.compose.theme

import android.animation.ValueAnimator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun rememberReducedMotion(): Boolean {
    return remember {
        runCatching { !ValueAnimator.areAnimatorsEnabled() }.getOrDefault(false)
    }
}

@Composable
fun RethinkAnimatedSection(
    index: Int,
    content: @Composable () -> Unit
) {
    val reduceMotion = rememberReducedMotion()
    var entered by remember(index) { mutableStateOf(reduceMotion) }

    LaunchedEffect(index, reduceMotion) {
        if (!reduceMotion) {
            // Keep staggered sections perceptible without making frequent settings screens feel slow.
            delay((index.coerceIn(0, 5) * 32L))
            entered = true
        }
    }

    val progress by
        animateFloatAsState(
            targetValue = if (entered) 1f else 0f,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            label = "rethinkSectionEntrance"
        )

    androidx.compose.foundation.layout.Box(
        modifier =
            Modifier
                .alpha(if (reduceMotion) 1f else progress)
                .graphicsLayer {
                    translationY = if (reduceMotion) 0f else (1f - progress) * 8.dp.toPx()
                }
    ) {
        content()
    }
}
