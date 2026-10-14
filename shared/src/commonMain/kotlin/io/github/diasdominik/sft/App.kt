package io.github.diasdominik.sft

import io.github.diasdominik.sft.ui.AppIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import io.github.diasdominik.sft.data.GameData
import io.github.diasdominik.sft.data.GameVersion
import io.github.diasdominik.sft.state.AppState
import io.github.diasdominik.sft.ui.AboutScreen
import io.github.diasdominik.sft.ui.SatisfactoryTheme
import io.github.diasdominik.sft.ui.codex.BuildingDetailScreen
import io.github.diasdominik.sft.ui.codex.BuildingsScreen
import io.github.diasdominik.sft.ui.codex.CodexLinks
import io.github.diasdominik.sft.ui.codex.ItemDetailScreen
import io.github.diasdominik.sft.ui.codex.ItemsScreen
import io.github.diasdominik.sft.ui.codex.SchematicDetailScreen
import io.github.diasdominik.sft.ui.codex.SchematicsScreen
import io.github.diasdominik.sft.icons.LocalIconStore
import io.github.diasdominik.sft.ui.power.PowerPlannerScreen
import io.github.diasdominik.sft.ui.production.ProductionListScreen
import io.github.diasdominik.sft.ui.production.ProductionTabScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlin.random.Random
import org.koin.compose.viewmodel.koinViewModel

// Detail routes carry a random uid so the same page can sit in a back stack twice
// (item A -> item B -> item A) without clashing saved state.
@Serializable data object ProductionListRoute : NavKey
@Serializable data class ProductionTabRoute(val id: Long, val uid: Long = newUid()) : NavKey
@Serializable data object ItemsRoute : NavKey
@Serializable data class ItemRoute(val slug: String, val uid: Long = newUid()) : NavKey
@Serializable data object BuildingsRoute : NavKey
@Serializable data class BuildingRoute(val slug: String, val uid: Long = newUid()) : NavKey
@Serializable data object SchematicsRoute : NavKey
@Serializable data class SchematicRoute(val slug: String, val uid: Long = newUid()) : NavKey
@Serializable data object AboutRoute : NavKey
@Serializable data object PowerRoute : NavKey

private fun newUid() = Random.nextLong()

/** Non-JVM targets can't reflect on NavKey subclasses, so the back stack's serializers are listed here. */
private val navConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(ProductionListRoute::class, ProductionListRoute.serializer())
            subclass(ProductionTabRoute::class, ProductionTabRoute.serializer())
            subclass(ItemsRoute::class, ItemsRoute.serializer())
            subclass(ItemRoute::class, ItemRoute.serializer())
            subclass(BuildingsRoute::class, BuildingsRoute.serializer())
            subclass(BuildingRoute::class, BuildingRoute.serializer())
            subclass(SchematicsRoute::class, SchematicsRoute.serializer())
            subclass(SchematicRoute::class, SchematicRoute.serializer())
            subclass(AboutRoute::class, AboutRoute.serializer())
            subclass(PowerRoute::class, PowerRoute.serializer())
        }
    }
}

private class TopLevel(val route: NavKey, val label: String, val icon: ImageVector)

private val topLevels = listOf(
    TopLevel(ProductionListRoute, "Production", AppIcons.Factory),
    TopLevel(PowerRoute, "Power", AppIcons.Bolt),
    TopLevel(ItemsRoute, "Items", AppIcons.Items),
    TopLevel(BuildingsRoute, "Buildings", AppIcons.List),
    TopLevel(SchematicsRoute, "Schematics", AppIcons.Flask),
)

@Composable
fun App() {
    SatisfactoryTheme {
        val state = koinViewModel<AppState>()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val data = state.gameData
            when {
                state.loadError != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Couldn't load game data: ${state.loadError}")
                }
                data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Loading ${state.version.label} data…")
                    }
                }
                else -> CompositionLocalProvider(LocalIconStore provides state.icons) { MainContent(state, data) }
            }
        }
    }
}

/**
 * One back stack per bottom-bar destination, so each tab keeps its place. The displayed stack is the
 * Production stack followed by the selected tab's stack, which makes Back from a tab's root return
 * to Production.
 */
@Composable
private fun MainContent(state: AppState, data: GameData) {
    val stacks = topLevels.map { rememberNavBackStack(navConfig, it.route) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val current = stacks[selected]
    val backStack = if (selected == 0) stacks[0].toList() else stacks[0] + current

    fun push(route: NavKey) = current.add(route)
    fun pop() {
        if (current.size > 1) current.removeAt(current.lastIndex) else selected = 0
    }

    val links = CodexLinks(
        openItem = { push(ItemRoute(it)) },
        openBuilding = { push(BuildingRoute(it)) },
        openSchematic = { push(SchematicRoute(it)) },
    )
    val menu: @Composable () -> Unit = { AppMenu(state, onAbout = { push(AboutRoute) }) }
    val showBottomBar = current.size == 1

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevels.forEachIndexed { index, top ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = {
                                // Tapping the selected tab again returns to its root.
                                if (selected == index) stacks[index].retainAll(listOf(top.route)) else selected = index
                            },
                            icon = { Icon(top.icon, contentDescription = null) },
                            label = { Text(top.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            onBack = ::pop,
            entryProvider = entryProvider {
                entry<ProductionListRoute> {
                    ProductionListScreen(state, data, onOpen = { push(ProductionTabRoute(it)) }, actions = menu)
                }
                entry<ProductionTabRoute> { route ->
                    val tab = state.tab(route.id)
                    // A removed line closes itself, unless it is already on its way out.
                    if (tab == null) LaunchedEffect(route) { if (current.lastOrNull() == route) pop() }
                    else ProductionTabScreen(
                        state, data, tab, links,
                        onBack = ::pop,
                        // A clone replaces the line it was cloned from, so Back returns to the list.
                        onOpenTab = { id -> pop(); push(ProductionTabRoute(id)) },
                    )
                }
                entry<ItemsRoute> { ItemsScreen(data, onOpen = links.openItem, actions = menu) }
                entry<ItemRoute> { route ->
                    val item = data.itemBySlug(route.slug)
                    if (item == null) LaunchedEffect(route) { pop() }
                    else ItemDetailScreen(data, item, links, onBack = ::pop)
                }
                entry<BuildingsRoute> { BuildingsScreen(data, onOpen = links.openBuilding, actions = menu) }
                entry<BuildingRoute> { route ->
                    val building = data.buildingBySlug(route.slug)
                    if (building == null) LaunchedEffect(route) { pop() }
                    else BuildingDetailScreen(data, building, links, onBack = ::pop)
                }
                entry<SchematicsRoute> { SchematicsScreen(data, onOpen = links.openSchematic, actions = menu) }
                entry<SchematicRoute> { route ->
                    val schematic = data.schematicBySlug(route.slug)
                    if (schematic == null) LaunchedEffect(route) { pop() }
                    else SchematicDetailScreen(data, schematic, links, onBack = ::pop)
                }
                entry<AboutRoute> { AboutScreen(onBack = ::pop) }
                entry<PowerRoute> {
                    PowerPlannerScreen(
                        data,
                        onPlanFuel = { fuel, amount, name -> push(ProductionTabRoute(state.addFuelLine(fuel, amount, name).id)) },
                        actions = menu,
                    )
                }
            },
        )
    }
}

@Composable
private fun AppMenu(state: AppState, onAbout: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(AppIcons.MoreVert, contentDescription = "Menu") }
    DropdownMenu(open, onDismissRequest = { open = false }) {
        Text(
            "Game version",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        GameVersion.entries.forEach { version ->
            DropdownMenuItem(
                text = { Text(version.label) },
                leadingIcon = { RadioButton(selected = state.version == version, onClick = null) },
                onClick = {
                    open = false
                    state.switchVersion(version)
                },
            )
        }
        HorizontalDivider()
        val data = state.gameData
        val progress = state.icons.progress
        DropdownMenuItem(
            text = { Text(if (progress != null) "Downloading icons ${progress.done} of ${progress.total}" else "Download game icons again") },
            leadingIcon = { Icon(AppIcons.Import, null) },
            enabled = progress == null && data != null,
            onClick = {
                open = false
                state.redownloadIcons()
            },
        )
        DropdownMenuItem(text = { Text("About") }, leadingIcon = { Icon(AppIcons.Info, null) }, onClick = { open = false; onAbout() })
    }
}
