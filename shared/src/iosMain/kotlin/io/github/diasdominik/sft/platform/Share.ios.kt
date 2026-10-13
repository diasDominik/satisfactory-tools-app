package io.github.diasdominik.sft.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberTextSharer(): (title: String, text: String) -> Unit = remember {
    { _, text ->
        val controller = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        // Scene-based apps have no keyWindow on UIApplication, so look through the connected scenes.
        val windows = UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
        val window = windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull()
        var presenter = window?.rootViewController
        while (presenter?.presentedViewController != null) presenter = presenter.presentedViewController
        presenter?.view?.let { view ->
            // iPad shows the share sheet as a popover, which needs an anchor or it crashes.
            controller.popoverPresentationController?.sourceView = view
            controller.popoverPresentationController?.sourceRect = view.bounds.useContents {
                CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0)
            }
        }
        presenter?.presentViewController(controller, animated = true, completion = null)
    }
}
