package io.github.diasdominik.sft.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
actual fun rememberTextSharer(): (title: String, text: String) -> Unit = remember {
    { _, text ->
        val controller = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        var presenter = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (presenter?.presentedViewController != null) presenter = presenter.presentedViewController
        presenter?.presentViewController(controller, animated = true, completion = null)
    }
}
