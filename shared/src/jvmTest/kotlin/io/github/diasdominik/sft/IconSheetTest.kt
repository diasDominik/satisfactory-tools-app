package io.github.diasdominik.sft

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.use
import io.github.diasdominik.sft.data.DataLoader
import io.github.diasdominik.sft.data.GameVersion
import io.github.diasdominik.sft.ui.GameIcon
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/** Renders every item icon into build/icon-sheet.png so the generated icons can be reviewed. */
class IconSheetTest {
    @Test
    fun renderIconSheet() {
        val data = DataLoader.parse(GameVersion.V1_0, File("src/commonMain/composeResources/files/data1.0.json").readText())
        val entries = data.items.values.sortedBy { it.name }.map { it.className } +
            listOf("Desc_ConstructorMk1_C", "Desc_GeneratorCoal_C", "Desc_MinerMk1_C", "Desc_ConveyorBeltMk1_C")
        val columns = 12
        val rows = entries.chunked(columns)
        val width = columns * 84
        val height = rows.size * 96 + 16
        ImageComposeScene(width, height, density = androidx.compose.ui.unit.Density(1f)) {
            Column(Modifier.background(Color(0xFF1E2023)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rows.forEach { row ->
                    Row {
                        row.forEach { className ->
                            Column(Modifier.width(84.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                GameIcon(data, className, 56.dp)
                                Text(data.nameOf(className), color = Color.White, fontSize = 8.sp, maxLines = 2, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }.use { scene ->
            val image = scene.render()
            val bytes = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            File("build").mkdirs()
            File("build/icon-sheet.png").writeBytes(bytes)
        }
    }
}
