package io.github.diasdominik.sft

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import io.github.diasdominik.sft.data.DataLoader
import io.github.diasdominik.sft.data.GameVersion
import io.github.diasdominik.sft.ui.AboutScreen
import io.github.diasdominik.sft.ui.SatisfactoryTheme
import io.github.diasdominik.sft.ui.codex.BuildingDetailScreen
import io.github.diasdominik.sft.ui.codex.BuildingsScreen
import io.github.diasdominik.sft.ui.codex.CodexLinks
import io.github.diasdominik.sft.ui.codex.ItemDetailScreen
import io.github.diasdominik.sft.ui.codex.ItemsScreen
import io.github.diasdominik.sft.ui.codex.SchematicDetailScreen
import io.github.diasdominik.sft.ui.codex.SchematicsScreen
import java.io.File
import kotlin.test.Test

/**
 * Composes and draws each codex screen once, so runtime failures that only show up when a screen is
 * on screen (such as mismatched Compose library versions) fail the build instead of the app.
 */
class ScreenSmokeTest {
    private val data = DataLoader.parse(GameVersion.V1_0, File("src/commonMain/composeResources/files/data1.0.json").readText())
    private val links = CodexLinks(openItem = {}, openBuilding = {}, openSchematic = {})

    private fun render(content: @Composable () -> Unit) {
        ImageComposeScene(420, 860) { SatisfactoryTheme(content) }.use { scene ->
            scene.render()
            scene.render(nanoTime = 1_000_000_000L)
        }
    }

    @Test fun items() = render { ItemsScreen(data, onOpen = {}, actions = {}) }
    @Test fun buildings() = render { BuildingsScreen(data, onOpen = {}, actions = {}) }
    @Test fun schematics() = render { SchematicsScreen(data, onOpen = {}, actions = {}) }
    @Test fun item() = render { ItemDetailScreen(data, data.itemBySlug("iron-plate")!!, links, onBack = {}) }
    @Test fun building() = render { BuildingDetailScreen(data, data.buildingBySlug("constructor")!!, links, onBack = {}) }
    @Test fun schematic() = render { SchematicDetailScreen(data, data.schematics.values.first(), links, onBack = {}) }
    @Test fun about() = render { AboutScreen(onBack = {}) }
}
