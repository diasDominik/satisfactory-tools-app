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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
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
import io.github.diasdominik.sft.ui.production.ProductionListScreen
import io.github.diasdominik.sft.ui.production.ProductionTabScreen
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import kotlin.reflect.KClass

@Serializable object ProductionListRoute
@Serializable data class ProductionTabRoute(val id: Long)
@Serializable object ItemsRoute
@Serializable data class ItemRoute(val slug: String)
@Serializable object BuildingsRoute
@Serializable data class BuildingRoute(val slug: String)
@Serializable object SchematicsRoute
@Serializable data class SchematicRoute(val slug: String)
@Serializable object AboutRoute

private class TopLevel(val route: Any, val routeClass: KClass<*>, val label: String, val icon: ImageVector)

private val topLevels = listOf(
    TopLevel(ProductionListRoute, ProductionListRoute::class, "Production", AppIcons.Factory),
    TopLevel(ItemsRoute, ItemsRoute::class, "Items", AppIcons.Items),
    TopLevel(BuildingsRoute, BuildingsRoute::class, "Buildings", AppIcons.List),
    TopLevel(SchematicsRoute, SchematicsRoute::class, "Schematics", AppIcons.Flask),
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
                else -> MainContent(state, data)
            }
        }
    }
}

@Composable
private fun MainContent(state: AppState, data: GameData) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBottomBar = topLevels.any { top -> destination?.hierarchy?.any { it.hasRoute(top.routeClass) } == true }
    val links = remember(navController) {
        CodexLinks(
            openItem = { navController.navigate(ItemRoute(it)) },
            openBuilding = { navController.navigate(BuildingRoute(it)) },
            openSchematic = { navController.navigate(SchematicRoute(it)) },
        )
    }
    val menu: @Composable () -> Unit = { AppMenu(state, onAbout = { navController.navigate(AboutRoute) }) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevels.forEach { top ->
                        val selected = destination?.hierarchy?.any { it.hasRoute(top.routeClass) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateTopLevel(top.route) },
                            icon = { Icon(top.icon, contentDescription = null) },
                            label = { Text(top.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController,
            startDestination = ProductionListRoute,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable<ProductionListRoute> {
                ProductionListScreen(state, data, onOpen = { navController.navigate(ProductionTabRoute(it)) }, actions = menu)
            }
            composable<ProductionTabRoute> { entry ->
                val id = entry.toRoute<ProductionTabRoute>().id
                val tab = state.tab(id)
                if (tab == null) {
                    LaunchedEffect(id) { navController.popBackStack() }
                } else {
                    ProductionTabScreen(state, data, tab, links, onBack = { navController.popBackStack() })
                }
            }
            composable<ItemsRoute> { ItemsScreen(data, onOpen = links.openItem, actions = menu) }
            composable<ItemRoute> { entry ->
                val item = data.itemBySlug(entry.toRoute<ItemRoute>().slug)
                if (item == null) LaunchedEffect(Unit) { navController.popBackStack() }
                else ItemDetailScreen(data, item, links, onBack = { navController.popBackStack() })
            }
            composable<BuildingsRoute> { BuildingsScreen(data, onOpen = links.openBuilding, actions = menu) }
            composable<BuildingRoute> { entry ->
                val building = data.buildingBySlug(entry.toRoute<BuildingRoute>().slug)
                if (building == null) LaunchedEffect(Unit) { navController.popBackStack() }
                else BuildingDetailScreen(data, building, links, onBack = { navController.popBackStack() })
            }
            composable<SchematicsRoute> { SchematicsScreen(data, onOpen = links.openSchematic, actions = menu) }
            composable<SchematicRoute> { entry ->
                val schematic = data.schematicBySlug(entry.toRoute<SchematicRoute>().slug)
                if (schematic == null) LaunchedEffect(Unit) { navController.popBackStack() }
                else SchematicDetailScreen(data, schematic, links, onBack = { navController.popBackStack() })
            }
            composable<AboutRoute> { AboutScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
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
        DropdownMenuItem(text = { Text("About") }, leadingIcon = { Icon(AppIcons.Info, null) }, onClick = { open = false; onAbout() })
    }
}
