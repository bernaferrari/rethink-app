/* Copyright 2026 RethinkDNS and its authors */

package com.celzero.bravedns.ui.compose.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.celzero.bravedns.R
import com.celzero.bravedns.service.TcpProxyHelper

/** Android billing-state bridge for the common checkout renderer. */
@Composable
fun CheckoutScreen(
    paymentStatus: TcpProxyHelper.PaymentStatus,
    onStartPayment: () -> Unit,
    onNavigateToProxy: () -> Unit,
    onManageAccount: () -> Unit,
    onBackClick: (() -> Unit)? = null,
) {
    RethinkCheckoutScreen(
        paymentStatus = paymentStatus.toShared(),
        plans = listOf(
            RethinkCheckoutPlan("one-month", stringResource(R.string.checkout_plan_1m_title), stringResource(R.string.checkout_plan_1m_subtitle)),
            RethinkCheckoutPlan("three-month", stringResource(R.string.checkout_plan_3m_title), stringResource(R.string.checkout_plan_3m_subtitle)),
            RethinkCheckoutPlan("six-month", stringResource(R.string.checkout_plan_6m_title), stringResource(R.string.checkout_plan_6m_subtitle)),
        ),
        strings = RethinkCheckoutStrings(
            appName = stringResource(R.string.checkout_app_name), choosePlan = stringResource(R.string.checkout_choose_plan), purchase = stringResource(R.string.checkout_purchase), restore = stringResource(R.string.checkout_restore),
            termsTitle = stringResource(R.string.checkout_terms_title), termsBody = stringResource(R.string.checkout_terms_body), manageAccount = stringResource(R.string.rpn_account_open),
            awaitingTitle = stringResource(R.string.checkout_payment_awaiting_title), awaitingMessage = stringResource(R.string.checkout_payment_awaiting_message),
            successTitle = stringResource(R.string.checkout_payment_success_title), successMessage = stringResource(R.string.checkout_payment_success_message), successButton = stringResource(R.string.checkout_payment_success_button),
            failedTitle = stringResource(R.string.checkout_payment_failed_title), failedMessage = stringResource(R.string.checkout_payment_failed_message), failedButton = stringResource(R.string.checkout_payment_failed_button),
        ),
        onStartPayment = onStartPayment,
        onNavigateToProxy = onNavigateToProxy,
        onManageAccount = onManageAccount,
        onBackClick = onBackClick,
    )
}

private fun TcpProxyHelper.PaymentStatus.toShared() = when (this) {
    TcpProxyHelper.PaymentStatus.INITIATED -> RethinkCheckoutPaymentStatus.Initiated
    TcpProxyHelper.PaymentStatus.PAID -> RethinkCheckoutPaymentStatus.Paid
    TcpProxyHelper.PaymentStatus.FAILED -> RethinkCheckoutPaymentStatus.Failed
    else -> RethinkCheckoutPaymentStatus.NotPaid
}
