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
package com.celzero.bravedns.ui.compose.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.service.TcpProxyHelper
import com.celzero.bravedns.util.UIUtils

enum class CheckoutPlan(val titleRes: Int, val subtitleRes: Int) {
    ONE_MONTH(R.string.checkout_plan_1m_title, R.string.checkout_plan_1m_subtitle),
    THREE_MONTH(R.string.checkout_plan_3m_title, R.string.checkout_plan_3m_subtitle),
    SIX_MONTH(R.string.checkout_plan_6m_title, R.string.checkout_plan_6m_subtitle)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    paymentStatus: TcpProxyHelper.PaymentStatus,
    onStartPayment: () -> Unit,
    onNavigateToProxy: () -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    var selectedPlan by remember { mutableStateOf(CheckoutPlan.SIX_MONTH) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.checkout_app_name)) },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_arrow_back_24),
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (paymentStatus) {
                TcpProxyHelper.PaymentStatus.NOT_PAID -> PaymentContent(
                    selectedPlan = selectedPlan,
                    onPlanSelected = { selectedPlan = it },
                    onStartPayment = onStartPayment,
                    onNavigateToProxy = onNavigateToProxy
                )
                TcpProxyHelper.PaymentStatus.INITIATED -> PaymentAwaiting()
                TcpProxyHelper.PaymentStatus.PAID -> PaymentSuccess(onNavigateToProxy)
                TcpProxyHelper.PaymentStatus.FAILED -> PaymentFailed(onNavigateToProxy)
                else -> PaymentContent(
                    selectedPlan = selectedPlan,
                    onPlanSelected = { selectedPlan = it },
                    onStartPayment = onStartPayment,
                    onNavigateToProxy = onNavigateToProxy
                )
            }
        }
    }
}

@Composable
private fun PaymentContent(
    selectedPlan: CheckoutPlan,
    onPlanSelected: (CheckoutPlan) -> Unit,
    onStartPayment: () -> Unit,
    onNavigateToProxy: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        TopBanner()
        Spacer(modifier = Modifier.height(12.dp))
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = stringResource(R.string.checkout_choose_plan),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        PlanSelector(selectedPlan = selectedPlan, onPlanSelected = onPlanSelected)
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onStartPayment
        ) {
            Text(text = stringResource(R.string.checkout_purchase))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onNavigateToProxy
        ) {
            Text(text = stringResource(R.string.checkout_restore))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.checkout_terms_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.checkout_terms_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun TopBanner() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = stringResource(R.string.checkout_banner_icon_desc),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.checkout_app_name),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.checkout_proxy_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun PlanSelector(
    selectedPlan: CheckoutPlan,
    onPlanSelected: (CheckoutPlan) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CheckoutPlan.entries.forEach { plan ->
            PlanRow(plan = plan, isSelected = selectedPlan == plan, onClick = { onPlanSelected(plan) })
        }
    }
}

@Composable
private fun PlanRow(plan: CheckoutPlan, isSelected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val accent = Color(UIUtils.fetchColor(context, R.attr.accentGood))
    val subtle = Color(UIUtils.fetchColor(context, R.attr.primaryLightColorText))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Medium)) {
                    append(stringResource(plan.titleRes))
                }
                append(" ")
                withStyle(SpanStyle(color = subtle)) {
                    append(stringResource(plan.subtitleRes))
                }
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun PaymentSuccess(onNavigateToProxy: () -> Unit) {
    StatusScreen(
        title = stringResource(R.string.checkout_payment_success_title),
        message = stringResource(R.string.checkout_payment_success_message),
        buttonLabel = stringResource(R.string.checkout_payment_success_button),
        onButtonClick = onNavigateToProxy
    )
}

@Composable
private fun PaymentFailed(onNavigateToProxy: () -> Unit) {
    StatusScreen(
        title = stringResource(R.string.checkout_payment_failed_title),
        message = stringResource(R.string.checkout_payment_failed_message),
        buttonLabel = stringResource(R.string.checkout_payment_failed_button),
        onButtonClick = onNavigateToProxy
    )
}

@Composable
private fun PaymentAwaiting() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.checkout_payment_awaiting_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.checkout_payment_awaiting_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun StatusScreen(
    title: String,
    message: String,
    buttonLabel: String,
    onButtonClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onButtonClick
        ) {
            Text(text = buttonLabel)
        }
    }
}
