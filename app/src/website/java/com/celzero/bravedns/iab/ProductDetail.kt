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
package com.celzero.bravedns.iab

import com.android.billingclient.api.BillingClient

data class ProductDetail(
    var productId: String,
    var planId: String,
    var productTitle: String,
    var productType: String,
    var pricingDetails: List<PricingPhase>

) {
    constructor() : this(
        productId = "",
        planId = "",
        productTitle = "",
        productType = BillingClient.ProductType.SUBS,
        pricingDetails = listOf(),
    )
}
