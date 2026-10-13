package io.github.diasdominik.sft

import androidx.compose.ui.window.ComposeUIViewController
import io.github.diasdominik.sft.di.initKoin
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    initKoin()
    return ComposeUIViewController { App() }
}
