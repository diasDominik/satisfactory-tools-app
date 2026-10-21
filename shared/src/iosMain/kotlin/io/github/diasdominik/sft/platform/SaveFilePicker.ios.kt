package io.github.diasdominik.sft.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject
import platform.posix.memcpy
import platform.zlib.Z_OK
import platform.zlib.uLongfVar
import platform.zlib.uncompress

@Composable
actual fun rememberSaveFilePicker(onPicked: (ByteArray) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    // The picker only holds its delegate weakly, so it lives here.
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
                val scoped = url.startAccessingSecurityScopedResource()
                val data = NSData.dataWithContentsOfURL(url)
                if (scoped) url.stopAccessingSecurityScopedResource()
                data?.toByteArray()?.let { callback(it) }
            }
        }
    }
    return remember {
        {
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData), asCopy = true)
            picker.delegate = delegate
            val windows = UIApplication.sharedApplication.connectedScenes
                .filterIsInstance<UIWindowScene>()
                .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
            var presenter = (windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull())?.rootViewController
            while (presenter?.presentedViewController != null) presenter = presenter.presentedViewController
            presenter?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val bytes = ByteArray(length.toInt())
    if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}

@OptIn(ExperimentalForeignApi::class)
actual fun inflateZlib(data: ByteArray, size: Int): ByteArray {
    val out = ByteArray(size)
    memScoped {
        val length = alloc<uLongfVar>()
        length.value = size.convert()
        val result = out.usePinned { target ->
            data.usePinned { source ->
                uncompress(target.addressOf(0).reinterpret(), length.ptr, source.addressOf(0).reinterpret(), data.size.convert())
            }
        }
        check(result == Z_OK) { "Couldn't decompress the save (zlib error $result)" }
        return if (length.value.toInt() == size) out else out.copyOf(length.value.toInt())
    }
}
