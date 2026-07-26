package com.bernaferrari.bravedns.iab.stripe

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bernaferrari.bravedns.network.StripeApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SubscriptionViewModel : ViewModel() {

    private val _prices = MutableStateFlow<List<Price>>(emptyList())
    val prices: StateFlow<List<Price>> = _prices.asStateFlow()

    private val productKey = ""
    private val secretKey = ""

    fun fetchPrices() {
        viewModelScope.launch {
            try {
                val prices = StripeApi.getPrices(authorization = secretKey)
                if (prices != null) {
                    _prices.value = prices.data.filter { it.product == productKey }
                } else {
                    Log.e("StripeAPI", "Error: empty response")
                }
            } catch (t: Throwable) {
                Log.e("StripeAPI", "Failure: ${t.message}")
            }
        }
    }
}