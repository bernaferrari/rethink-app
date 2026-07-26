/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.settings

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor

enum class RethinkCheckoutPaymentStatus { NotPaid, Initiated, Paid, Failed }

data class RethinkCheckoutPlan(val id: String, val title: String, val subtitle: String)

data class RethinkCheckoutStrings(
    val appName: String,
    val choosePlan: String,
    val purchase: String,
    val restore: String,
    val termsTitle: String,
    val termsBody: String,
    val manageAccount: String,
    val awaitingTitle: String,
    val awaitingMessage: String,
    val successTitle: String,
    val successMessage: String,
    val successButton: String,
    val failedTitle: String,
    val failedMessage: String,
    val failedButton: String,
)

/** Shared checkout presentation. Billing and account navigation are owned by the host. */
@Composable
fun RethinkCheckoutScreen(
    paymentStatus: RethinkCheckoutPaymentStatus,
    plans: List<RethinkCheckoutPlan>,
    strings: RethinkCheckoutStrings,
    onStartPayment: () -> Unit,
    onNavigateToProxy: () -> Unit,
    onManageAccount: () -> Unit,
    onBackClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var selectedPlanId by remember(plans) { mutableStateOf(plans.lastOrNull()?.id.orEmpty()) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    androidx.compose.material3.Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { RethinkLargeTopBar(strings.appName, onBackClick = onBackClick, scrollBehavior = scrollBehavior) },
    ) { paddingValues ->
        Column(
            Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.background),
        ) {
            when (paymentStatus) {
                RethinkCheckoutPaymentStatus.NotPaid -> CheckoutPaymentContent(plans, selectedPlanId, strings, { selectedPlanId = it }, onStartPayment, onNavigateToProxy)
                RethinkCheckoutPaymentStatus.Initiated -> CheckoutAwaiting(strings)
                RethinkCheckoutPaymentStatus.Paid -> CheckoutStatus(strings.successTitle, strings.successMessage, strings.successButton, onNavigateToProxy)
                RethinkCheckoutPaymentStatus.Failed -> CheckoutStatus(strings.failedTitle, strings.failedMessage, strings.failedButton, onNavigateToProxy)
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.screenPaddingHorizontal, vertical = SharedDimensions.spacingMd).height(50.dp),
                onClick = onManageAccount,
                shape = RoundedCornerShape(SharedDimensions.buttonCornerRadius),
            ) { Text(strings.manageAccount) }
        }
    }
}

@Composable
private fun CheckoutPaymentContent(
    plans: List<RethinkCheckoutPlan>,
    selectedPlanId: String,
    strings: RethinkCheckoutStrings,
    onPlanSelected: (String) -> Unit,
    onStartPayment: () -> Unit,
    onNavigateToProxy: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = SharedDimensions.spacingLg),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.screenPaddingHorizontal),
            shape = RoundedCornerShape(SharedDimensions.cardCornerRadiusLarge),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
            tonalElevation = 1.dp,
        ) {
            Column(Modifier.padding(SharedDimensions.spacingLg), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                Text(strings.appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(strings.choosePlan, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.screenPaddingHorizontal),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
        ) {
            SectionHeader(strings.choosePlan, modifier = Modifier.padding(horizontal = SharedDimensions.spacingXs))
            RethinkListGroup {
                plans.forEachIndexed { index, plan ->
                    val selected = selectedPlanId == plan.id
                    RethinkListItem(
                        headline = plan.title,
                        supporting = plan.subtitle,
                        position = cardPositionFor(index, plans.lastIndex),
                        leadingIcon = if (selected) MaterialSymbols.Filled.RadioButtonChecked else MaterialSymbols.Filled.RadioButtonUnchecked,
                        leadingIconTint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { onPlanSelected(plan.id) },
                        trailing = { RadioButton(selected = selected, onClick = null) },
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)) {
                Button(modifier = Modifier.fillMaxWidth().height(50.dp), onClick = onStartPayment, shape = RoundedCornerShape(SharedDimensions.buttonCornerRadius)) { Text(strings.purchase, style = MaterialTheme.typography.labelLarge) }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    onClick = onNavigateToProxy,
                    shape = RoundedCornerShape(SharedDimensions.buttonCornerRadius),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                ) { Text(strings.restore) }
            }
            Spacer(Modifier.height(SharedDimensions.spacingMd))
            Surface(shape = RoundedCornerShape(SharedDimensions.cornerRadius2xl), color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 1.dp) {
                Column(Modifier.padding(horizontal = SharedDimensions.spacingLg, vertical = SharedDimensions.spacingMd), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                    Text(strings.termsTitle, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(strings.termsBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(SharedDimensions.spacingLg))
        }
    }
}

@Composable
private fun CheckoutAwaiting(strings: RethinkCheckoutStrings) = CheckoutStatusCard(strings.awaitingTitle, strings.awaitingMessage) {
    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun CheckoutStatus(title: String, message: String, button: String, onClick: () -> Unit) = CheckoutStatusCard(title, message) {
    Button(modifier = Modifier.fillMaxWidth(), onClick = onClick, shape = RoundedCornerShape(SharedDimensions.buttonCornerRadius)) { Text(button) }
}

@Composable
private fun CheckoutStatusCard(title: String, message: String, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.screenPaddingHorizontal, vertical = SharedDimensions.spacingXl)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(SharedDimensions.cornerRadius4xl),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f)),
            tonalElevation = 1.dp,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(SharedDimensions.spacingXs))
                content()
            }
        }
    }
}
