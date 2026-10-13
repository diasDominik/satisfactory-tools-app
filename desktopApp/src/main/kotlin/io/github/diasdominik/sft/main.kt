package io.github.diasdominik.sft

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.diasdominik.sft.di.initKoin

fun main() {
    initKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Satisfactory Tools",
            state = rememberWindowState(width = 420.dp, height = 860.dp),
        ) {
            App()
        }
    }
}
