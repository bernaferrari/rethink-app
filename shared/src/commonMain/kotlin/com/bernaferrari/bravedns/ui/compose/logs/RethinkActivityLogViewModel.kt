/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.logs

import androidx.compose.runtime.Composable
import com.bernaferrari.bravedns.viewmodel.ActivityLogViewModel
import org.koin.compose.viewmodel.koinViewModel

/** The log screen's rows, scoped to its navigation entry (each pushed log filters on its own). */
@Composable
fun rememberActivityLogViewModel(): ActivityLogViewModel = koinViewModel()
