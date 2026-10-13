package io.github.diasdominik.sft.platform

import androidx.compose.runtime.Composable

/** Exports arrive through the share sheet and are pasted in, so there is no file picker here. */
@Composable
actual fun rememberSftFileReader(): (() -> String?)? = null
