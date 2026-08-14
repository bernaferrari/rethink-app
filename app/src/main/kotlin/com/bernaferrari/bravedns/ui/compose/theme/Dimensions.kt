/*
 * Copyright 2024 RethinkDNS and its authors
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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/** Android re-export of the shared dimension scale. Prefer [SharedDimensions] in new code. */
typealias Dimensions = SharedDimensions

/** Standard padding values for common use cases. */
object Paddings {
    val none = PaddingValues(0.dp)
    val xs = PaddingValues(SharedDimensions.spacingXs)
    val sm = PaddingValues(SharedDimensions.spacingSm)
    val md = PaddingValues(SharedDimensions.spacingMd)
    val lg = PaddingValues(SharedDimensions.spacingLg)
    val xl = PaddingValues(SharedDimensions.spacingXl)

    val screen = PaddingValues(
        horizontal = SharedDimensions.screenPaddingHorizontal,
        vertical = SharedDimensions.screenPaddingVertical,
    )

    val card = PaddingValues(SharedDimensions.cardPadding)
    val cardSm = PaddingValues(SharedDimensions.cardPaddingSm)
}
