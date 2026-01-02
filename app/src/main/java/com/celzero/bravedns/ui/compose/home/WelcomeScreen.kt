/*
 * Copyright 2020 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.compose.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celzero.bravedns.R
import com.celzero.bravedns.util.UIUtils.fetchColor
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(onFinish: () -> Unit) {
    val slides = remember {
        listOf(
            WelcomeSlide(
                image = R.drawable.ic_launcher,
                title = R.string.slide_2_title,
                desc = R.string.slide_2_desc,
                imageSize = 120.dp,
                topPadding = 80.dp,
                titleTopPadding = 50.dp
            ),
            WelcomeSlide(
                image = R.drawable.ic_wireguard_welcome,
                title = R.string.wireguard_title,
                desc = R.string.wireguard_desc,
                imageSize = 200.dp,
                topPadding = 60.dp,
                titleTopPadding = 30.dp
            ),
            WelcomeSlide(
                image = R.drawable.ic_firewall_welcome,
                title = R.string.firewall_title,
                desc = R.string.firewall_desc,
                imageSize = 200.dp,
                topPadding = 60.dp,
                titleTopPadding = 30.dp
            ),
            WelcomeSlide(
                image = R.drawable.ic_dns_welcome,
                title = R.string.dns_title,
                desc = R.string.dns_desc,
                imageSize = 200.dp,
                topPadding = 60.dp,
                titleTopPadding = 30.dp
            )
        )
    }

    val pagerState = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.welcome_gradient_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                WelcomeSlideContent(slides[page])
            }

            DotsIndicator(
                count = slides.size,
                currentIndex = pagerState.currentPage
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage < slides.lastIndex) {
                    TextButton(onClick = { onFinish() }) {
                        Text(text = stringResource(R.string.skip))
                    }
                } else {
                    Spacer(modifier = Modifier.size(1.dp))
                }

                Button(
                    onClick = {
                        if (pagerState.currentPage >= slides.lastIndex) {
                            onFinish()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text =
                        if (pagerState.currentPage >= slides.lastIndex) {
                            stringResource(R.string.finish)
                        } else {
                            stringResource(R.string.next)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeSlideContent(slide: WelcomeSlide) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(slide.topPadding))
        Image(
            painter = painterResource(id = slide.image),
            contentDescription = null,
            modifier = Modifier.size(slide.imageSize)
        )
        Spacer(modifier = Modifier.height(slide.titleTopPadding))
        Text(
            text = stringResource(slide.title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(fetchColor(context, R.attr.primaryTextColor))
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(slide.desc),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = Color(fetchColor(context, R.attr.primaryTextColor)),
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun DotsIndicator(count: Int, currentIndex: Int) {
    val context = LocalContext.current
    val activeColors = context.resources.getIntArray(R.array.array_dot_active)
    val inactiveColors = context.resources.getIntArray(R.array.array_dot_inactive)

    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        repeat(count) { index ->
            val color =
                if (index == currentIndex) activeColors[index] else inactiveColors[currentIndex]
            Box(
                modifier =
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(color))
            )
        }
    }
}

private data class WelcomeSlide(
    val image: Int,
    val title: Int,
    val desc: Int,
    val imageSize: Dp,
    val topPadding: Dp,
    val titleTopPadding: Dp
)
