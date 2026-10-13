package io.github.diasdominik.sft.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Desktop has no share sheet, so exports are saved as a .sft file the website can import too. */
@Composable
actual fun rememberTextSharer(): (title: String, text: String) -> Unit = remember {
    { title, text ->
        val dialog = FileDialog(null as Frame?, "Export production lines", FileDialog.SAVE).apply {
            file = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "export" } + ".sft"
            isVisible = true
        }
        val name = dialog.file
        if (name != null) {
            val target = File(dialog.directory, if (name.endsWith(".sft")) name else "$name.sft")
            target.writeText(text)
        }
    }
}
