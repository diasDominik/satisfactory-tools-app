package io.github.diasdominik.sft.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
actual fun rememberTextSharer(): (title: String, text: String) -> Unit = remember {
    { _, text -> Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
}
