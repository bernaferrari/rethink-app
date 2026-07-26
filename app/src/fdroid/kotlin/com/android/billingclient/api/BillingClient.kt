/*
 * Stub: F-Droid flavor does not include Google Play Billing.
 * This file provides just enough of the BillingClient surface to let
 * SubscriptionStateMachineV2 (main source set) compile.
 */
package com.android.billingclient.api

class BillingClient {
    object ProductType {
        const val SUBS  = "subs"
        const val INAPP = "inapp"
    }
}

