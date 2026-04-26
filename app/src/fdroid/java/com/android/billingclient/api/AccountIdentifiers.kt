/*
 * Stub: F-Droid flavor does not include Google Play Billing.
 * This file provides just enough of the AccountIdentifiers surface to let
 * SubscriptionStateMachineV2 (main source set) compile.
 */
package com.android.billingclient.api

class AccountIdentifiers(
    val obfuscatedAccountId: String = "",
    val obfuscatedProfileId: String = ""
)

