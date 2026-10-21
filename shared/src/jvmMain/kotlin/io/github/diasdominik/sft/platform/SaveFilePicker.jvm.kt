package io.github.diasdominik.sft.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.zip.Inflater

@Composable
actual fun rememberSaveFilePicker(onPicked: (ByteArray) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    return remember {
        {
            val dialog = FileDialog(null as Frame?, "Open a save file", FileDialog.LOAD).apply {
                // Windows keeps saves under %LOCALAPPDATA%\FactoryGame\Saved\SaveGames\<account id>.
                System.getenv("LOCALAPPDATA")?.let { File(it, "FactoryGame/Saved/SaveGames") }
                    ?.takeIf { it.isDirectory }?.let { directory = it.path }
                setFilenameFilter { _, name -> name.endsWith(".sav") }
                isVisible = true
            }
            dialog.file?.let { callback(File(dialog.directory, it).readBytes()) }
        }
    }
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
