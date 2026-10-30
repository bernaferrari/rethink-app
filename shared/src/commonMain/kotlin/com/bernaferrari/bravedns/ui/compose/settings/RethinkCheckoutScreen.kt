@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.settings

import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.common.RethinkErrorState
import com.bernaferrari.bravedns.ui.compose.common.RethinkLoadingState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListSelection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.RethinkContainedCard

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
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { RethinkLargeTopBar(strings.appName, onBackClick = onBackClick, scrollBehavior = scrollBehavior) },
    ) { paddingValues ->
        Column(
            Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()),
        ) {
            when (paymentStatus) {
                RethinkCheckoutPaymentStatus.NotPaid -> CheckoutPaymentContent(plans, selectedPlanId, strings, { selectedPlanId = it }, onStartPayment, onNavigateToProxy)
                RethinkCheckoutPaymentStatus.Initiated -> CheckoutAwaiting(strings)
                RethinkCheckoutPaymentStatus.Paid -> CheckoutPaid(strings, onNavigateToProxy)
                RethinkCheckoutPaymentStatus.Failed -> CheckoutFailed(strings, onNavigateToProxy)
            }
            if (paymentStatus == RethinkCheckoutPaymentStatus.Paid) {
                TextButton(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.screenPaddingHorizontal, vertical = SharedDimensions.spacingMd),
                    onClick = onManageAccount,
                ) { Text(strings.manageAccount) }
            }
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
    // The top bar already names the product and the section header asks to choose a plan, so
    // the plan list leads directly instead of a hero card repeating both.
    Column(
        Modifier.fillMaxWidth().padding(
            start = SharedDimensions.screenPaddingHorizontal,
            end = SharedDimensions.screenPaddingHorizontal,
            bottom = SharedDimensions.spacing2xl,
        ),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
    ) {
        Column {
            SectionHeader(strings.choosePlan)
            RethinkListGroup(Modifier.selectableGroup()) {
                plans.forEachIndexed { index, plan ->
                    val selected = selectedPlanId == plan.id
                    RethinkListItem(
                        headline = plan.title,
                        supporting = plan.subtitle,
                        position = cardPositionFor(index, plans.lastIndex),
                        highlighted = selected,
                        selection = RethinkListSelection(checked = selected, role = Role.RadioButton) { onPlanSelected(plan.id) },
                        trailing = { RadioButton(selected = selected, onClick = null) },
                    )
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
        ) {
            Button(
                modifier = Modifier.fillMaxWidth().height(SharedDimensions.buttonHeight),
                onClick = onStartPayment,
                shape = RoundedCornerShape(SharedDimensions.buttonCornerRadius),
            ) { Text(strings.purchase, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            TextButton(onClick = onNavigateToProxy, modifier = Modifier.heightIn(min = SharedDimensions.touchTargetMin)) { Text(strings.restore) }
        }
        RethinkContainedCard(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
            Text(strings.termsTitle, style = MaterialTheme.typography.titleSmall)
            Text(strings.termsBody, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CheckoutAwaiting(strings: RethinkCheckoutStrings) = CheckoutStateFrame {
    SectionHeader(strings.awaitingTitle)
    RethinkLoadingState(label = strings.awaitingMessage, contained = true)
}

@Composable
private fun CheckoutPaid(strings: RethinkCheckoutStrings, onClick: () -> Unit) = CheckoutStateFrame {
    RethinkEmptyState(
        title = strings.successTitle,
        message = strings.successMessage,
        icon = MaterialSymbols.Filled.CheckCircle,
        ctaLabel = strings.successButton,
        onCtaClick = onClick,
        contained = true,
    )
}

@Composable
private fun CheckoutFailed(strings: RethinkCheckoutStrings, onClick: () -> Unit) = CheckoutStateFrame {
    RethinkErrorState(
        title = strings.failedTitle,
        message = strings.failedMessage,
        retryLabel = strings.failedButton,
        onRetry = onClick,
        contained = true,
    )
}

@Composable
private fun CheckoutStateFrame(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.screenPaddingHorizontal, vertical = SharedDimensions.spacingXl),
        content = content,
    )
}
