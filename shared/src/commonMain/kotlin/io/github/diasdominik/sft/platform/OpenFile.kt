package io.github.diasdominik.sft.platform

import androidx.compose.runtime.Composable

/** Returns a function that lets the user pick a .sft file and returns its text, or null where unsupported. */
@Composable
expect fun rememberSftFileReader(): (() -> String?)?
