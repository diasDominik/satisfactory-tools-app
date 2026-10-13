package io.github.diasdominik.sft.platform

import androidx.compose.runtime.Composable

/** Returns a function that hands text to the platform share sheet (or the clipboard where there is none). */
@Composable
expect fun rememberTextSharer(): (title: String, text: String) -> Unit
