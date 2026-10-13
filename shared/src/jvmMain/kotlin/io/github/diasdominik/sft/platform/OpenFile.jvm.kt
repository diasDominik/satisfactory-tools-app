package io.github.diasdominik.sft.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
actual fun rememberSftFileReader(): (() -> String?)? = remember {
    {
        val dialog = FileDialog(null as Frame?, "Import production lines", FileDialog.LOAD).apply {
            setFilenameFilter { _, name -> name.endsWith(".sft") }
            isVisible = true
        }
        dialog.file?.let { File(dialog.directory, it).readText() }
    }
}
