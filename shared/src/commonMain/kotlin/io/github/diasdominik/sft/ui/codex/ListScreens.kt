package io.github.diasdominik.sft.ui.codex

import io.github.diasdominik.sft.ui.AppIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.diasdominik.sft.data.GameData
import io.github.diasdominik.sft.data.Item
import io.github.diasdominik.sft.ui.EmptyState
import io.github.diasdominik.sft.ui.EntityRow
import io.github.diasdominik.sft.ui.SearchField
import io.github.diasdominik.sft.ui.fmt

/** Item filters, mirroring upstream ItemFiltersService. */
data class ItemFilter(
    val query: String = "",
    val onlyRadioactive: Boolean = false,
    val onlyWithEnergyValue: Boolean = false,
    val stackSize: Int? = null,
    val liquid: Boolean? = null,
) {
    fun apply(items: Collection<Item>): List<Item> = items.filter { item ->
        (!onlyRadioactive || item.radioactiveDecay > 0) &&
            (!onlyWithEnergyValue || item.energyValue > 0) &&
            (liquid == null || item.liquid == liquid) &&
            (stackSize == null || (if (item.liquid) item.stackSize / 1000 else item.stackSize) == stackSize) &&
            (query.isEmpty() || item.name.contains(query, ignoreCase = true))
    }.sortedBy { it.name }
}

private val stackSizes = listOf(1, 50, 100, 200, 500)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemsScreen(data: GameData, onOpen: (String) -> Unit, actions: @Composable () -> Unit) {
    var filter by remember { mutableStateOf(ItemFilter()) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val items = remember(filter, data) { filter.apply(data.items.values) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Items") }, actions = {
            IconButton(onClick = { showFilters = !showFilters }) { Icon(AppIcons.Filter, contentDescription = "Filters") }
            actions()
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item {
                SearchField(filter.query, { filter = filter.copy(query = it) }, "Search items")
            }
            if (showFilters) {
                item {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(filter.onlyRadioactive, { filter = filter.copy(onlyRadioactive = !filter.onlyRadioactive) }, label = { Text("Radioactive") })
                            FilterChip(filter.onlyWithEnergyValue, { filter = filter.copy(onlyWithEnergyValue = !filter.onlyWithEnergyValue) }, label = { Text("With energy value") })
                            FilterChip(filter.liquid == false, { filter = filter.copy(liquid = if (filter.liquid == false) null else false) }, label = { Text("Solid") })
                            FilterChip(filter.liquid == true, { filter = filter.copy(liquid = if (filter.liquid == true) null else true) }, label = { Text("Liquid") })
                        }
                        Text("Stack size", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            stackSizes.forEach { size ->
                                FilterChip(filter.stackSize == size, { filter = filter.copy(stackSize = if (filter.stackSize == size) null else size) }, label = { Text(size.toString()) })
                            }
                        }
                        TextButton(onClick = { filter = ItemFilter(query = filter.query) }) { Text("Reset filters") }
                    }
                }
            }
            if (items.isEmpty()) item { EmptyState("No items match your filters.") }
            items(items, key = { it.className }) { item ->
                EntityRow(
                    data, item.className,
                    subtitle = buildList {
                        add(if (item.liquid) "Liquid" else "Solid")
                        if (item.sinkPoints > 0) add("${fmt(item.sinkPoints.toDouble())} pts")
                        if (data.isResource(item.className)) add("Raw resource")
                    }.joinToString(" · "),
                    onClick = { onOpen(item.slug) },
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildingsScreen(data: GameData, onOpen: (String) -> Unit, actions: @Composable () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val buildings = remember(query, data) {
        data.buildings.values.filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }.sortedBy { it.name }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Buildings") }, actions = { actions() }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item { SearchField(query, { query = it }, "Search buildings") }
            if (buildings.isEmpty()) item { EmptyState("No buildings match your search.") }
            items(buildings, key = { it.className }) { building ->
                EntityRow(data, building.className, subtitle = kindLabel(data, building), onClick = { onOpen(building.slug) })
                HorizontalDivider()
            }
        }
    }
}

private fun kindLabel(data: GameData, building: io.github.diasdominik.sft.data.Building): String? = when (data.kindOf(building)) {
    io.github.diasdominik.sft.data.BuildingKind.MANUFACTURER -> "Manufacturer"
    io.github.diasdominik.sft.data.BuildingKind.EXTRACTOR -> "Extractor"
    io.github.diasdominik.sft.data.BuildingKind.GENERATOR -> "Power generator"
    io.github.diasdominik.sft.data.BuildingKind.OTHER -> null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SchematicsScreen(data: GameData, onOpen: (String) -> Unit, actions: @Composable () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    val types = remember(data) { data.schematics.values.map { it.type }.distinct().sortedBy { schematicTypeName(it) } }
    val schematics = remember(query, type, data) {
        data.schematics.values
            .filter { (query.isEmpty() || it.name.contains(query, ignoreCase = true)) && (type == null || it.type == type) }
            .sortedWith(compareBy({ it.tier }, { it.name }))
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Schematics") }, actions = { actions() }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item { SearchField(query, { query = it }, "Search schematics") }
            item {
                FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { t ->
                        FilterChip(type == t, { type = if (type == t) null else t }, label = { Text(schematicTypeName(t)) })
                    }
                }
            }
            if (schematics.isEmpty()) item { EmptyState("No schematics match your search.") }
            items(schematics, key = { it.className }) { schematic ->
                EntityRow(
                    data, schematic.className,
                    subtitle = "${schematicTypeName(schematic.type)} · Tier ${schematic.tier}",
                    onClick = { onOpen(schematic.slug) },
                )
                HorizontalDivider()
            }
        }
    }
}
