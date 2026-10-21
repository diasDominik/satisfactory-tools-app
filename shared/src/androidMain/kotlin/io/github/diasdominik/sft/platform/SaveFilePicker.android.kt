package io.github.diasdominik.sft.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.util.zip.Inflater

@Composable
actual fun rememberSaveFilePicker(onPicked: (ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { context.contentResolver.openInputStream(it)?.use { stream -> callback(stream.readBytes()) } }
    }
    // .sav has no registered MIME type, so allow any file.
    return remember(launcher) { { launcher.launch(arrayOf("*/*")) } }
}

actual fun inflateZlib(data: ByteArray, size: Int): ByteArray {
    val inflater = Inflater()
    try {
        inflater.setInput(data)
        val out = ByteArray(size)
        var done = 0
        while (done < size && !inflater.finished()) {
            val read = inflater.inflate(out, done, size - done)
            if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            done += read
        }
        return if (done == size) out else out.copyOf(done)
    } finally {
        inflater.end()
    }
}
