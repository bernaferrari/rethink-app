package com.bernaferrari.bravedns.ui.compose.dns

import com.bernaferrari.bravedns.data.SmartDnsProfile

data class SmartDnsProfileLabels(val title: String, val choices: Map<SmartDnsProfile, Pair<String, String>>)
