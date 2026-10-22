package io.github.diasdominik.sft.ui.codex

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.diasdominik.sft.data.Building
import io.github.diasdominik.sft.data.BuildingKind
import io.github.diasdominik.sft.data.Constants
import io.github.diasdominik.sft.data.Formula
import io.github.diasdominik.sft.data.GameData
import io.github.diasdominik.sft.data.Item
import io.github.diasdominik.sft.data.Purity
import io.github.diasdominik.sft.data.Schematic
import io.github.diasdominik.sft.ui.AmountChips
import io.github.diasdominik.sft.ui.DetailScaffold
import io.github.diasdominik.sft.ui.EmptyState
import io.github.diasdominik.sft.ui.EntityIcon
import io.github.diasdominik.sft.ui.EntityRow
import io.github.diasdominik.sft.ui.GraphView
import io.github.diasdominik.sft.ui.GraphViewEdge
import io.github.diasdominik.sft.ui.GraphViewNode
import io.github.diasdominik.sft.ui.KeyValueRow
import io.github.diasdominik.sft.ui.SectionCard
import io.github.diasdominik.sft.ui.fmt

@Composable
private fun Header(data: GameData, className: String, name: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        EntityIcon(data, className, 72.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.headlineSmall)
            if (description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun LazyListScope.recipeSection(title: String, data: GameData, recipes: List<io.github.diasdominik.sft.data.Recipe>, overclock: Double, links: CodexLinks) {
    item(key = "title-$title") {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    }
    if (recipes.isEmpty()) {
        item(key = "empty-$title") { EmptyState("None") }
    } else {
        items(recipes, key = { "$title-${it.className}" }) { RecipeCard(data, it, overclock, links) }
    }
}

@Composable
fun ItemDetailScreen(data: GameData, item: Item, links: CodexLinks, onBack: () -> Unit) {
    val recipes = remember(item) { data.recipesForItem(item) }
    val usages = remember(item) { data.usagesAsIngredient(item) }
    val forBuilding = remember(item) { data.usagesForBuilding(item) }
    val forSchematics = remember(item) { data.usagesForSchematics(item).sortedBy { it.name } }
    DetailScaffold(item.name, onBack) {
        item { Header(data, item.className, item.name, item.description) }
        item {
            SectionCard("Details") {
                KeyValueRow("Sink points", data.sinkPointsOf(item.className).let { if (it > 0) fmt(it.toDouble()) else "Can't be put into the sink" })
                KeyValueRow("Form", if (item.liquid) "Liquid" else "Solid")
                if (!item.liquid) KeyValueRow("Stack size", item.stackSize.toString())
                if (item.energyValue > 0) {
                    KeyValueRow("Energy value", if (item.liquid) "${fmt(item.energyValue * 1000)} MJ/m³" else "${fmt(item.energyValue)} MJ")
                }
                KeyValueRow("Radioactive", if (item.radioactiveDecay > 0) "Yes" else "No")
                if (data.isResource(item.className)) KeyValueRow("Raw resource", "Yes")
            }
        }
        recipeSection("Recipes", data, recipes, 100.0, links)
        recipeSection("Usages as ingredient", data, usages, 100.0, links)
        item { Text("Usages for building", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        if (forBuilding.isEmpty()) item { EmptyState("None") }
        items(forBuilding, key = { "b-" + it.className }) { recipe ->
            Column(Modifier.fillMaxWidth()) {
                recipe.products.forEach { product ->
                    val building = data.building(product.item)
                    EntityRow(data, product.item, onClick = building?.let { b -> { links.openBuilding(b.slug) } })
                }
                AmountChips(data, recipe.ingredients) { links.open(data, it) }
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }
        }
        item { Text("Usages for schematics", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        if (forSchematics.isEmpty()) item { EmptyState("None") }
        items(forSchematics, key = { "s-" + it.className }) { schematic ->
            Column(Modifier.fillMaxWidth()) {
                EntityRow(data, schematic.className, subtitle = schematicTypeName(schematic.type), onClick = { links.openSchematic(schematic.slug) })
                AmountChips(data, schematic.cost) { links.open(data, it) }
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuildingDetailScreen(data: GameData, building: Building, links: CodexLinks, onBack: () -> Unit) {
    val kind = remember(building) { data.kindOf(building) }
    val buildingRecipe = remember(building) { data.buildingRecipe(building) }
    var overclock by rememberSaveable(building.className) { mutableStateOf(100.0) }
    var purity by remember(building.className) { mutableStateOf(Purity.NORMAL) }
    DetailScaffold(building.name, onBack) {
        item { Header(data, building.className, building.name, building.description) }
        if (buildingRecipe != null) {
            item {
                SectionCard("Cost") {
                    AmountChips(data, buildingRecipe.ingredients) { links.open(data, it) }
                }
            }
        }
        when (kind) {
            BuildingKind.MANUFACTURER -> {
                item {
                    SectionCard("Details") {
                        OverclockSlider(overclock, { overclock = it })
                        val isVariable = data.recipesInMachine(building).any { it.isVariablePower }
                        if (isVariable) {
                            val ranges = data.recipesInMachine(building).filter { it.isVariablePower }.map { Formula.recipePowerRange(it, building, overclock) }
                            KeyValueRow("Power consumption", "${fmt(ranges.minOf { it.first })} – ${fmt(ranges.maxOf { it.second })} MW (varies by recipe)")
                        } else {
                            KeyValueRow("Power consumption", "${fmt(Formula.powerConsumption(building, overclock))} MW")
                        }
                        KeyValueRow("Manufacturing speed multiplier", "×" + fmt(building.metadata.manufacturingSpeed * overclock / 100))
                    }
                }
                recipeSection("Recipes", data, data.recipesInMachine(building), overclock, links)
            }
            BuildingKind.EXTRACTOR -> {
                val miner = data.miners.getValue(building.className)
                item {
                    SectionCard("Details") {
                        OverclockSlider(overclock, { overclock = it })
                        KeyValueRow("Power consumption", "${fmt(Formula.powerConsumption(building, overclock))} MW")
                        KeyValueRow("Liquid resources allowed", if (miner.allowLiquids) "Yes" else "No")
                        KeyValueRow("Solid resources allowed", if (miner.allowSolids) "Yes" else "No")
                        if (building.className != Constants.WATER_EXTRACTOR) {
                            FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Purity.entries.forEach { p -> FilterChip(purity == p, { purity = p }, label = { Text(p.label) }) }
                            }
                        }
                        val unit = if (miner.allowLiquids) "m³/min" else "items/min"
                        KeyValueRow("Extraction rate", "${fmt(Formula.extractionRate(miner, building, if (building.className == Constants.WATER_EXTRACTOR) Purity.NORMAL else purity, overclock))} $unit")
                    }
                }
                item {
                    SectionCard("Extractable resources") {
                        data.extractorResources(miner).forEach { res ->
                            EntityRow(data, res.className, onClick = { links.openItem(res.slug) })
                        }
                    }
                }
            }
            BuildingKind.GENERATOR -> {
                val generator = data.generators.getValue(building.className)
                val canOverclock = building.className != Constants.GEOTHERMAL_GENERATOR
                item {
                    SectionCard("Details") {
                        if (canOverclock) OverclockSlider(overclock, { overclock = it })
                        KeyValueRow("Power production", "${fmt(Formula.generatorPower(generator, if (canOverclock) overclock else 100.0))} MW")
                        KeyValueRow("Requires water", if (generator.waterToPowerRatio > 0) "Yes" else "No")
                    }
                }
                if (generator.fuel.isNotEmpty()) {
                    item {
                        SectionCard("Fuel") {
                            generator.fuel.mapNotNull { data.item(it) }.forEach { fuel ->
                                val lines = buildList {
                                    val unit = if (fuel.liquid) "m³/min" else "items/min"
                                    add("${fmt(Formula.fuelConsumption(generator, fuel, overclock))} $unit")
                                    if (generator.waterToPowerRatio > 0) add("+ ${fmt(Formula.generatorWaterConsumption(generator, overclock))} m³/min water")
                                    if (fuel.className == Constants.NUCLEAR_FUEL_ROD) add("produces ${fmt(Formula.nuclearWasteProduction(overclock))} Uranium Waste/min")
                                }
                                EntityRow(data, fuel.className, subtitle = lines.joinToString("\n"), onClick = { links.openItem(fuel.slug) })
                            }
                        }
                    }
                }
            }
            BuildingKind.OTHER -> {
                if (building.metadata.powerConsumption > 0 || building.metadata.manufacturingSpeed > 0) {
                    item {
                        SectionCard("Details") {
                            if (building.metadata.powerConsumption > 0) KeyValueRow("Power consumption", "${fmt(Formula.powerConsumption(building, 100.0))} MW")
                            if (building.metadata.manufacturingSpeed > 0) KeyValueRow("Manufacturing speed multiplier", "×" + fmt(building.metadata.manufacturingSpeed))
                        }
                    }
                }
                val handRecipes = data.recipesInMachine(building)
                if (handRecipes.isNotEmpty()) recipeSection("Recipes", data, handRecipes, 100.0, links)
            }
        }
        val unlockedBy = buildingRecipe?.let { data.schematicsUnlocking(it) }.orEmpty()
        if (unlockedBy.isNotEmpty()) {
            item {
                SectionCard("Unlocked by") {
                    unlockedBy.forEach { s -> EntityRow(data, s.className, subtitle = schematicTypeName(s.type), onClick = { links.openSchematic(s.slug) }) }
                }
            }
        }
    }
}

@Composable
fun SchematicDetailScreen(data: GameData, schematic: Schematic, links: CodexLinks, onBack: () -> Unit) {
    val relevant = remember(schematic) { data.relevantSchematics(schematic) }
    val graph = remember(schematic) {
        val ids = relevant.withIndex().associate { (i, s) -> s.className to i + 1 }
        val nodes = relevant.map { s ->
            GraphViewNode(
                id = ids.getValue(s.className),
                title = s.name,
                subtitle = "Tier ${s.tier}",
                color = if (s.className == schematic.className) androidx.compose.ui.graphics.Color(0xFF5CB85C) else androidx.compose.ui.graphics.Color(0xFF2B3E50),
                details = listOf(schematicTypeName(s.type)),
                action = if (s.className != schematic.className) ("Open" to { links.openSchematic(s.slug) }) else null,
            )
        }
        val edges = relevant.flatMap { s ->
            s.requiredSchematics.mapNotNull { dep -> ids[dep]?.let { GraphViewEdge(it, ids.getValue(s.className)) } }
        }
        nodes to edges
    }
    DetailScaffold(schematic.name, onBack) {
        item { Header(data, schematic.className, schematic.name, "") }
        item {
            SectionCard("Overview") {
                KeyValueRow("Type", schematicTypeName(schematic.type))
                KeyValueRow("Tier", schematic.tier.toString())
                KeyValueRow("Time to research", "${fmt(schematic.time)} s")
            }
        }
        if (schematic.cost.isNotEmpty()) {
            item { SectionCard("Cost") { AmountChips(data, schematic.cost) { links.open(data, it) } } }
        }
        item {
            SectionCard("Schematic details") {
                if (schematic.requiredSchematics.isNotEmpty()) {
                    Text("Requires", style = MaterialTheme.typography.labelLarge)
                    schematic.requiredSchematics.mapNotNull { data.schematic(it) }.forEach { s ->
                        EntityRow(data, s.className, onClick = { links.openSchematic(s.slug) })
                    }
                }
                if (schematic.unlock.recipes.isNotEmpty()) {
                    Text("Unlocks recipes", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    schematic.unlock.recipes.mapNotNull { data.recipe(it) }.forEach { r ->
                        val product = r.products.firstOrNull()?.item
                        if (product != null) {
                            EntityRow(data, product, subtitle = r.name, onClick = { links.open(data, product) })
                        }
                    }
                }
                if (schematic.unlock.scannerResources.isNotEmpty()) {
                    Text("Unlocks scanning for", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    schematic.unlock.scannerResources.forEach { r -> EntityRow(data, r, onClick = { links.open(data, r) }) }
                }
                if (schematic.unlock.inventorySlots > 0) {
                    KeyValueRow("Inventory slots", "+" + schematic.unlock.inventorySlots)
                }
                if (schematic.unlock.giveItems.isNotEmpty()) {
                    Text("Gives items", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    AmountChips(data, schematic.unlock.giveItems) { links.open(data, it) }
                }
                if (schematic.requiredSchematics.isEmpty() && schematic.unlock.recipes.isEmpty() && schematic.unlock.scannerResources.isEmpty() &&
                    schematic.unlock.inventorySlots == 0 && schematic.unlock.giveItems.isEmpty()
                ) {
                    EmptyState("Nothing to show")
                }
            }
        }
        if (relevant.size > 1) {
            item {
                SectionCard("Related schematics") {
                    GraphView(graph.first, graph.second, height = 420.dp, highlight = 1 + relevant.indexOf(schematic))
                }
            }
        }
    }
}
