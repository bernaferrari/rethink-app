package com.celzero.bravedns.iab.stripe

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SubscriptionViewModel : ViewModel() {

    private val _prices = MutableStateFlow<List<Price>>(emptyList())
    val prices: StateFlow<List<Price>> = _prices.asStateFlow()
}