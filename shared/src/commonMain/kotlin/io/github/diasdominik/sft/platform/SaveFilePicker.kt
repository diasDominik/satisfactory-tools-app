package io.github.diasdominik.sft.platform

import androidx.compose.runtime.Composable

/** Returns a function that lets the user pick a Satisfactory .sav file and hands its bytes to [onPicked]. */
@Composable
expect fun rememberSaveFilePicker(onPicked: (ByteArray) -> Unit): () -> Unit

/** Inflates one zlib-compressed save chunk of [size] bytes with the platform's own zlib, which is much faster. */
expect fun inflateZlib(data: ByteArray, size: Int): ByteArray
