/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.theme

/**
 * Whether a sheet dismissed from its own content should finish the hide animation before the
 * host removes it. On WebAssembly, waiting for hide() can leave the sheet's semantics/scrim
 * mounted after it has visually disappeared, so the browser removes the host immediately.
 */
internal expect val sheetHidesBeforeDismiss: Boolean
