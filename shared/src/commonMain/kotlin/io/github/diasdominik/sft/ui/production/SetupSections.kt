package io.github.diasdominik.sft.ui.production

import io.github.diasdominik.sft.ui.AppIcons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.diasdominik.sft.data.Constants
import io.github.diasdominik.sft.data.GameData
import io.github.diasdominik.sft.data.Purity
import io.github.diasdominik.sft.data.Recipe
import io.github.diasdominik.sft.planner.Extraction
import io.github.diasdominik.sft.planner.InputItem
import io.github.diasdominik.sft.planner.ResourceExtraction
import io.github.diasdominik.sft.planner.ProductionItem
import io.github.diasdominik.sft.planner.ProductionRequest
import io.github.diasdominik.sft.planner.ProductionType
import io.github.diasdominik.sft.planner.FactoryPower
import io.github.diasdominik.sft.planner.Somersloop
import io.github.diasdominik.sft.planner.Goal
import io.github.diasdominik.sft.planner.ByproductMode
import io.github.diasdominik.sft.planner.RecipeNode
import io.github.diasdominik.sft.planner.ClockGrouping
import io.github.diasdominik.sft.planner.WeightMode
import io.github.diasdominik.sft.platform.rememberSaveFilePicker
import io.github.diasdominik.sft.state.AppState
import io.github.diasdominik.sft.state.ProductionTab
import io.github.diasdominik.sft.ui.EntityIcon
import io.github.diasdominik.sft.ui.EntityPickerDialog
import io.github.diasdominik.sft.ui.NumberField
import io.github.diasdominik.sft.ui.OptionalCountField
import io.github.diasdominik.sft.ui.SearchField
import io.github.diasdominik.sft.ui.fmt

/** Updates the current element at [index] (not a copy captured when the row was drawn). */
private fun <T> List<T>.updateAt(index: Int, transform: (T) -> T) =
    if (index !in indices) this else toMutableList().also { it[index] = transform(it[index]) }
private fun <T> List<T>.removeAt(index: Int) = toMutableList().also { it.removeAt(index) }
private fun <T> List<T>.move(from: Int, to: Int) =
    if (to !in indices) this else toMutableList().also { it.add(to, it.removeAt(from)) }
private fun List<String>.toggle(value: String) = if (value in this) this - value else this + value

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ItemButton(data: GameData, className: String?, options: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var picking by remember { mutableStateOf(false) }
    Row(
        modifier.clickable { picking = true }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntityIcon(data, className, 36.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            className?.let { data.nameOf(it) } ?: "Select item…",
            style = MaterialTheme.typography.bodyLarge,
            color = if (className == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (picking) {
        EntityPickerDialog(data, "Select item", options, onDismiss = { picking = false }) {
            onPick(it)
            picking = false
        }
    }
}

@Composable
private fun RowCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { content() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.productsSection(data: GameData, tab: ProductionTab) {
    item {
        Hint(
            "Select items to produce. \"Items/min\" produces the given amount from as few raw resources as possible; " +
                "\"Maximize\" produces as much as the raw resource limits allow (several maximized items share the output by their ratio).",
        )
    }
    val products = tab.request.production
    itemsIndexed(products, key = { index, _ -> "p$index" }) { index, product ->
        RowCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemButton(
                    data, product.item, data.automatableItems.map { it.className },
                    onPick = { picked -> tab.updateRequest { r -> r.copy(production = r.production.updateAt(index) { it.copy(item = picked) }) } },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { tab.updateRequest { r -> r.copy(production = r.production.move(index, index - 1)) } }, enabled = index > 0) {
                    Icon(AppIcons.ArrowUp, contentDescription = "Move up")
                }
                IconButton(onClick = { tab.updateRequest { r -> r.copy(production = r.production.move(index, index + 1)) } }, enabled = index < products.lastIndex) {
                    Icon(AppIcons.ArrowDown, contentDescription = "Move down")
                }
            }
            if (product.item != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        product.type != ProductionType.MAXIMIZE,
                        onClick = { tab.updateRequest { r -> r.copy(production = r.production.updateAt(index) { it.copy(type = ProductionType.PER_MINUTE) }) } },
                        label = { Text("Items/min") },
                    )
                    FilterChip(
                        product.type == ProductionType.MAXIMIZE,
                        onClick = { tab.updateRequest { r -> r.copy(production = r.production.updateAt(index) { it.copy(type = ProductionType.MAXIMIZE) }) } },
                        label = { Text("Maximize") },
                    )
                }
                if (product.type == ProductionType.MAXIMIZE) {
                    NumberField(
                        product.ratio,
                        { v -> tab.updateRequest { r -> r.copy(production = r.production.updateAt(index) { it.copy(ratio = v) }) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = "Ratio",
                    )
                } else {
                    NumberField(
                        product.amount,
                        { v -> tab.updateRequest { r -> r.copy(production = r.production.updateAt(index) { it.copy(amount = v) }) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = "Amount",
                        suffix = if (data.item(product.item)?.liquid == true) "m³/min" else "/min",
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { tab.updateRequest { r -> r.copy(production = r.production + product.copy()) } }) {
                    Icon(AppIcons.Copy, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Clone")
                }
                TextButton(onClick = { tab.updateRequest { r -> r.copy(production = r.production.removeAt(index)) } }) {
                    Icon(AppIcons.Close, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Remove")
                }
            }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { tab.updateRequest { r -> r.copy(production = r.production + ProductionItem()) } }) {
                Icon(AppIcons.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add product")
            }
            TextButton(onClick = { tab.updateRequest { r -> r.copy(production = listOf(ProductionItem())) } }) { Text("Clear") }
        }
    }
    item(key = "goals") {
        RowCard {
            Text("More goals", style = MaterialTheme.typography.titleSmall)
            Text(
                "Ask for AWESOME Sink points from the leftovers, or spare power for your grid (needs Power plants). " +
                    "Maximized goals share the scale with maximized products by their ratio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GoalEditor("Sink points", "points/min", tab.request.sinkPointsGoal) { g -> tab.updateRequest { r -> r.copy(sinkPointsGoal = g) } }
            GoalEditor("Spare power", "MW", tab.request.powerGoal) { g -> tab.updateRequest { r -> r.copy(powerGoal = g) } }
        }
    }
    item(key = "leftover-mode") {
        val request = tab.request
        RowCard {
            Text("Leftovers", style = MaterialTheme.typography.titleSmall)
            Text(
                when (request.byproductMode) {
                    ByproductMode.ALLOW -> "Anything the line makes too much of may leave it (sink, storage or another line)."
                    ByproductMode.CHOSEN -> "Only the items picked here may leave the line; everything else has to be used up."
                    ByproductMode.NONE -> "Nothing may be left over: the plan uses up everything it makes, or picks other recipes."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChipRow {
                ByproductMode.entries.forEach { m ->
                    FilterChip(
                        selected = m == request.byproductMode,
                        onClick = {
                            tab.updateRequest { r ->
                                // Start the pick list from what the line leaves over today.
                                if (m == ByproductMode.CHOSEN && r.allowedByproducts.isEmpty()) {
                                    r.copy(byproductMode = m, allowedByproducts = tab.result?.byproducts?.keys?.toList().orEmpty())
                                } else r.copy(byproductMode = m)
                            }
                        },
                        label = { Text(m.label) },
                    )
                }
            }
            if (request.byproductMode == ByproductMode.CHOSEN) {
                val candidates = (request.allowedByproducts + tab.result?.byproducts?.keys.orEmpty()).distinct()
                ChipRow {
                    candidates.forEach { item ->
                        FilterChip(
                            selected = item in request.allowedByproducts,
                            onClick = { tab.updateRequest { r -> r.copy(allowedByproducts = r.allowedByproducts.toggle(item)) } },
                            label = { Text(data.nameOf(item)) },
                            leadingIcon = { EntityIcon(data, item, 18.dp) },
                        )
                    }
                }
                var picking by remember { mutableStateOf(false) }
                TextButton(onClick = { picking = true }) { Text("Allow another item…") }
                if (picking) {
                    EntityPickerDialog(
                        data, "Allow as a leftover", data.inputableItems.map { it.className },
                        onDismiss = { picking = false },
                        onPick = { picked -> picking = false; tab.updateRequest { r -> r.copy(allowedByproducts = (r.allowedByproducts + picked).distinct()) } },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalEditor(label: String, unit: String, goal: Goal?, onChange: (Goal?) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        ChipRow {
            FilterChip(goal == null, onClick = { onChange(null) }, label = { Text("Off") })
            FilterChip(
                goal != null && goal.type != ProductionType.MAXIMIZE,
                onClick = { onChange((goal ?: Goal()).copy(type = ProductionType.PER_MINUTE)) },
                label = { Text("At least") },
            )
            FilterChip(
                goal?.type == ProductionType.MAXIMIZE,
                onClick = { onChange((goal ?: Goal()).copy(type = ProductionType.MAXIMIZE)) },
                label = { Text("Maximize") },
            )
        }
        if (goal != null) {
            if (goal.type == ProductionType.MAXIMIZE) {
                NumberField(goal.ratio, { v -> if (v >= 0) onChange(goal.copy(ratio = v)) }, modifier = Modifier.fillMaxWidth(), label = "Ratio")
            } else {
                NumberField(goal.amount, { v -> if (v >= 0) onChange(goal.copy(amount = v)) }, modifier = Modifier.fillMaxWidth(), label = "Amount", suffix = unit)
            }
        }
    }
}

private fun ProductionTab.updateExtraction(data: GameData, resource: String, transform: (ResourceExtraction) -> ResourceExtraction) =
    updateRequest { r ->
        val current = Extraction.settings(data, r, resource)
        if (current == null) r else r.copy(extraction = r.extraction + (resource to transform(current)))
    }

internal fun LazyListScope.resourcesSection(data: GameData, tab: ProductionTab) {
    item {
        Hint(
            "Select which raw resources may be used and their limits. Defaults are the map limits. " +
                "Pick the extractor, node purity and clock speed you have to see how many extractors you need, " +
                "and enter how many nodes you can use to limit a resource to what they deliver " +
                "(it then isn't made from other resources in a Converter either). With Maximize, every node gets an extractor.",
        )
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { tab.updateRequest { r -> r.copy(resourceMax = Constants.RESOURCE_AMOUNTS) } }) { Text("Set from map limits") }
            OutlinedButton(onClick = { tab.updateRequest { r -> r.copy(resourceMax = r.resourceMax.mapValues { 0.0 }) } }) { Text("Set to 0") }
        }
    }
    item(key = "weights") {
        val mode = tab.request.weightMode
        RowCard {
            Text("Resource cost", style = MaterialTheme.typography.titleSmall)
            Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChipRow {
                WeightMode.entries.forEach { m ->
                    FilterChip(
                        selected = m == mode,
                        onClick = {
                            tab.updateRequest { r ->
                                // Manual starts from what the previous mode used, so switching is never a jump.
                                if (m == WeightMode.MANUAL && r.weightMode != WeightMode.MANUAL) {
                                    r.copy(weightMode = m, resourceWeight = data.resourceList.associate { it.item to r.weightOf(it.item) })
                                } else r.copy(weightMode = m)
                            }
                        },
                        label = { Text(m.label) },
                    )
                }
            }
        }
    }
    item { AllOresCard(data, tab) }
    items(data.resourceList, key = { "r" + it.item }) { resource ->
        val enabled = resource.item !in tab.request.blockedResources
        val max = tab.request.resourceMax[resource.item] ?: 0.0
        RowCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(enabled, onCheckedChange = { tab.updateRequest { r -> r.copy(blockedResources = r.blockedResources.toggle(resource.item)) } })
                EntityIcon(data, resource.item, 28.dp)
                Spacer(Modifier.width(8.dp))
                Text(data.nameOf(resource.item), modifier = Modifier.weight(1f))
            }
            if (resource.item == Constants.WATER && max >= 1e12) {
                Text("Unlimited", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 48.dp, bottom = 4.dp))
            } else {
                NumberField(
                    max,
                    { v -> tab.updateRequest { r -> r.copy(resourceMax = r.resourceMax + (resource.item to v)) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Limit",
                    enabled = enabled,
                    suffix = "/min",
                )
            }
            if (enabled && tab.request.weightMode == WeightMode.MANUAL) {
                NumberField(
                    tab.request.weightOf(resource.item),
                    { v -> if (v >= 0) tab.updateRequest { r -> r.copy(resourceWeight = r.resourceWeight + (resource.item to v)) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    label = "Cost per unit",
                )
            }
            if (enabled) ExtractionSettings(data, tab, resource.item)
        }
    }
}

@Composable
private fun ExtractionSettings(data: GameData, tab: ProductionTab, resource: String) {
    val settings = Extraction.settings(data, tab.request, resource) ?: return
    val options = Extraction.extractors(data, resource)
    val extractor = settings.extractor!!
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ChipRow {
            options.forEach { b ->
                FilterChip(extractor == b.className, { tab.updateExtraction(data, resource) { it.copy(extractor = b.className) } }, label = { Text(b.name) })
            }
        }
        if (Extraction.hasPurity(extractor)) {
            ChipRow {
                Purity.entries.forEach { p ->
                    FilterChip(settings.purity == p, { tab.updateExtraction(data, resource) { it.copy(purity = p) } }, label = { Text(p.label) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
                settings.clockSpeed,
                { v -> if (v > 0) tab.updateExtraction(data, resource) { it.copy(clockSpeed = v.coerceIn(Extraction.MIN_CLOCK, Extraction.MAX_CLOCK)) } },
                modifier = Modifier.weight(1f),
                label = "Clock speed",
                suffix = "%",
            )
            OptionalCountField(
                settings.nodes,
                { n -> tab.updateExtraction(data, resource) { it.copy(nodes = n) } },
                modifier = Modifier.weight(1f),
                label = "Nodes",
                placeholder = "Any",
            )
        }
        val unit = if (data.item(resource)?.liquid == true) "m³/min" else "/min"
        val rate = Extraction.rate(data, settings)
        Text(
            buildString {
                append("${fmt(rate)} $unit per extractor")
                settings.nodes?.let { append(" · ${fmt(rate * it)} $unit from $it node${if (it == 1) "" else "s"}") }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Sets the miner tier and purity for every ore at once. */
@Composable
private fun AllOresCard(data: GameData, tab: ProductionTab) {
    val ores = data.resourceList.map { it.item }.filter { r -> Extraction.extractors(data, r).any { it.className in Constants.MINERS } }
    val miners = Constants.MINERS.mapNotNull { data.building(it) }
    RowCard {
        Text("All ores", style = MaterialTheme.typography.titleSmall)
        ChipRow {
            miners.forEach { m ->
                val selected = ores.all { Extraction.settings(data, tab.request, it)?.extractor == m.className }
                FilterChip(selected, { ores.forEach { r -> tab.updateExtraction(data, r) { it.copy(extractor = m.className) } } }, label = { Text(m.name) })
            }
        }
        ChipRow {
            Purity.entries.forEach { p ->
                val selected = ores.all { Extraction.settings(data, tab.request, it)?.purity == p }
                FilterChip(selected, { ores.forEach { r -> tab.updateExtraction(data, r) { it.copy(purity = p) } } }, label = { Text(p.label) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.inputsSection(state: AppState, data: GameData, tab: ProductionTab) {
    item {
        Hint(
            "Items you already have at your disposal, so this factory doesn't need to produce them. " +
                "Take them from another production line to feed this one with its output; the amounts then follow that line.",
        )
    }
    leftoversCard(state, data, tab)
    // Leftover inputs are managed by the card above; the rest keep their index in the full list.
    val inputs = tab.request.input.withIndex().filterNot { it.value.leftover }
    items(inputs, key = { "i" + it.index }) { (index, input) ->
        val source = input.source?.let { state.tabByUid(it) }
        RowCard {
            if (source != null && input.item != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EntityIcon(data, input.item, 32.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(data.nameOf(input.item), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${fmt(input.amount)} /min from ${source.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                ItemButton(
                    data, input.item, data.inputableItems.map { it.className },
                    onPick = { picked -> tab.updateRequest { r -> r.copy(input = r.input.updateAt(index) { it.copy(item = picked) }) } },
                )
                if (input.item != null) {
                    NumberField(
                        input.amount,
                        { v -> tab.updateRequest { r -> r.copy(input = r.input.updateAt(index) { it.copy(amount = v) }) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = "Amount",
                        suffix = "/min",
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (source != null) {
                    TextButton(onClick = { tab.updateRequest { r -> r.copy(input = r.input.updateAt(index) { it.copy(source = null) }) } }) { Text("Unlink") }
                } else {
                    TextButton(onClick = { tab.updateRequest { r -> r.copy(input = r.input + input.copy(source = null)) } }) { Text("Clone") }
                }
                TextButton(onClick = { tab.updateRequest { r -> r.copy(input = r.input.removeAt(index)) } }) { Text("Remove") }
            }
        }
    }
    item {
        var picking by remember { mutableStateOf(false) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { tab.updateRequest { r -> r.copy(input = r.input + InputItem()) } }) {
                Icon(AppIcons.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add input")
            }
            OutlinedButton(onClick = { picking = true }) { Text("Take from another line") }
            if (inputs.isNotEmpty()) TextButton(onClick = { tab.updateRequest { r -> r.copy(input = r.input.filter { it.leftover }) } }) { Text("Clear") }
        }
        if (picking) {
            val sources = state.linkableSources(tab)
            AlertDialog(
                onDismissRequest = { picking = false },
                title = { Text("Take from another line") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (sources.isEmpty()) {
                            Text("There's no other production line to take from yet. Lines that already use this one's output can't feed it back.")
                        }
                        sources.forEach { source ->
                            val outputs = source.outputs
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { state.linkLine(tab, source); picking = false }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                source.icon?.let { EntityIcon(data, it, 32.dp) }
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(source.name)
                                    Text(
                                        if (outputs.isEmpty()) "No result yet" else outputs.entries.joinToString { (item, amount) -> "${fmt(amount)} ${data.nameOf(item)}" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
            )
        }
    }
}

private fun recipeMachineDisabled(recipe: Recipe, blocked: List<String>) = recipe.producedIn.any { it in blocked }

/** The player's own unlocked alternates, kept across production lines, typed in or read from a save. */
@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.unlockedAlternatesSection(state: AppState, tab: ProductionTab) {
    item(key = "unlocked") {
        val unlocked = state.unlockedAlternates
        val current = tab.request.allowedAlternateRecipes
        var message by remember { mutableStateOf<String?>(null) }
        var reading by remember { mutableStateOf(false) }
        val pickSave = rememberSaveFilePicker { bytes ->
            reading = true
            message = null
            state.importSave(bytes, tab) { reading = false; message = it }
        }
        RowCard {
            Text("My unlocked alternates", style = MaterialTheme.typography.titleSmall)
            Text(
                when {
                    unlocked == null -> "Open your save file, or enable the alternates you've unlocked from hard drives and save them. New production lines then start with them."
                    unlocked.toSet() == current.toSet() -> "This line uses your ${unlocked.size} unlocked alternates. New production lines start with them."
                    else -> "${unlocked.size} saved. New production lines start with them."
                } + (state.unlockProfile?.takeIf { it.sessionName.isNotEmpty() }?.let { " Read from \"${it.sessionName}\"." } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = pickSave, enabled = !reading) { Text(if (reading) "Reading save…" else "Open save file…") }
                OutlinedButton(
                    onClick = { state.saveUnlockedAlternates(current) },
                    enabled = unlocked?.toSet() != current.toSet(),
                ) { Text("Save these as mine") }
                if (unlocked != null) {
                    OutlinedButton(
                        onClick = { state.applyProfile(tab) },
                        enabled = unlocked.toSet() != current.toSet() ||
                            state.unlockProfile?.let { it.lockedRecipes.toSet() != tab.request.blockedRecipes.toSet() } == true,
                    ) { Text("Use mine") }
                }
            }
        }
    }
}

internal fun LazyListScope.recipeToggleSection(data: GameData, tab: ProductionTab, alternates: Boolean) {
    val recipes = if (alternates) data.alternateRecipes else data.baseRecipes
    item(key = "hint-$alternates") {
        Hint(
            if (alternates) "Alternate recipes the planner may use (enabling one doesn't force it to be used)."
            else "Basic recipes the planner may use. Disable the ones you don't want.",
        )
    }
    item(key = "buttons-$alternates") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                tab.updateRequest { r ->
                    if (alternates) r.copy(allowedAlternateRecipes = data.alternateRecipes.map { it.className })
                    else r.copy(blockedRecipes = emptyList())
                }
            }) { Text("Enable all") }
            OutlinedButton(onClick = {
                tab.updateRequest { r ->
                    if (alternates) r.copy(allowedAlternateRecipes = emptyList())
                    else r.copy(blockedRecipes = data.baseRecipes.map { it.className })
                }
            }) { Text("Disable all") }
        }
    }
    item(key = "list-$alternates") {
        RecipeToggleList(data, tab, recipes, alternates)
    }
}

@Composable
private fun RecipeToggleList(data: GameData, tab: ProductionTab, recipes: List<Recipe>, alternates: Boolean) {
    var query by rememberSaveable(alternates) { mutableStateOf("") }
    val request = tab.request
    val filtered = remember(query, recipes) {
        recipes.filter { r ->
            query.isEmpty() || r.name.contains(query, ignoreCase = true) ||
                r.products.any { data.nameOf(it.item).contains(query, ignoreCase = true) }
        }
    }
    val enabledCount = if (alternates) request.allowedAlternateRecipes.size else recipes.size - request.blockedRecipes.count { b -> recipes.any { it.className == b } }
    Column {
        SearchField(query, { query = it }, "Search recipes")
        Text(
            "$enabledCount of ${recipes.size} enabled",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        filtered.forEach { recipe ->
            val machineDisabled = recipeMachineDisabled(recipe, request.blockedMachines)
            val checked = if (alternates) recipe.className in request.allowedAlternateRecipes else recipe.className !in request.blockedRecipes
            Row(
                Modifier.fillMaxWidth()
                    .alpha(if (machineDisabled) 0.5f else 1f)
                    // A recipe whose building is turned off can't be toggled until the building is back on.
                    .clickable(enabled = !machineDisabled) {
                        tab.updateRequest { r ->
                            if (alternates) r.copy(allowedAlternateRecipes = r.allowedAlternateRecipes.toggle(recipe.className))
                            else r.copy(blockedRecipes = r.blockedRecipes.toggle(recipe.className))
                        }
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked && !machineDisabled, onCheckedChange = null)
                EntityIcon(data, recipe.products.firstOrNull()?.item, 28.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(recipe.name.removePrefix("Alternate: "), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        recipe.ingredients.joinToString(" + ") { "${fmt(it.amount)} ${data.nameOf(it.item)}" } + " → " +
                            recipe.products.joinToString(" + ") { "${fmt(it.amount)} ${data.nameOf(it.item)}" } +
                            (if (machineDisabled) "\nDisabled because its building is disabled." else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

internal fun LazyListScope.machinesSection(data: GameData, tab: ProductionTab) {
    item {
        Hint(
            "Disable buildings you can't or don't want to use. Their recipes are then excluded too. " +
                "Set a clock speed (1–250 %) to plan under- or overclocked buildings: fewer buildings when overclocked, " +
                "and power follows the game's clock speed curve. Somersloops double a building's output for the same input " +
                "at four times the power; you can also add them to single recipes in the graph.",
        )
    }
    item {
        RowCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("All buildings", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                ClockField(tab.request.clockSpeed) { v ->
                    // Setting all buildings replaces the per-building values.
                    tab.updateRequest { r -> r.copy(clockSpeed = v, machineClockSpeed = emptyMap()) }
                }
            }
            Text("When the work doesn't fill whole buildings", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            ChipRow {
                ClockGrouping.entries.forEach { g ->
                    FilterChip(
                        selected = g == tab.request.clockGrouping,
                        onClick = { tab.updateRequest { r -> r.copy(clockGrouping = g) } },
                        label = { Text(g.label) },
                    )
                }
            }
        }
    }
    item(key = "cost-multipliers") {
        RowCard {
            Text("Advanced game settings", style = MaterialTheme.typography.titleSmall)
            Text(
                "If your world changes recipe or power costs (0.25 to 2 times), match it here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    tab.request.recipeCostMultiplier,
                    { v -> if (v > 0) tab.updateRequest { r -> r.copy(recipeCostMultiplier = v.coerceIn(ProductionRequest.MIN_MULTIPLIER, ProductionRequest.MAX_MULTIPLIER)) } },
                    modifier = Modifier.weight(1f),
                    label = "Recipe cost",
                    suffix = "×",
                )
                NumberField(
                    tab.request.powerCostMultiplier,
                    { v -> if (v > 0) tab.updateRequest { r -> r.copy(powerCostMultiplier = v.coerceIn(ProductionRequest.MIN_MULTIPLIER, ProductionRequest.MAX_MULTIPLIER)) } },
                    modifier = Modifier.weight(1f),
                    label = "Power cost",
                    suffix = "×",
                )
            }
        }
    }
    item(key = "recipe-clocks") {
        // Recipes in the current plan, plus any with their own clock that the plan no longer uses.
        val inPlan = tab.result?.graph?.nodes?.filterIsInstance<RecipeNode>()
            ?.filterNot { it.usage.isGenerator }?.map { it.usage.recipe.className }.orEmpty()
        val recipes = (inPlan + tab.request.recipeClockSpeed.keys).distinct().mapNotNull { data.recipe(it) }
        if (recipes.isNotEmpty()) {
            RowCard {
                Text("Clock speed per recipe", style = MaterialTheme.typography.titleSmall)
                Text("Wins over the building's clock speed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                recipes.forEach { recipe ->
                    val machine = data.machineFor(recipe)?.className ?: return@forEach
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(recipe.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        ClockField(tab.request.clockSpeedFor(machine, recipe.className)) { v ->
                            tab.updateRequest { r ->
                                val building = r.clockSpeedFor(machine)
                                r.copy(recipeClockSpeed = if (v == building) r.recipeClockSpeed - recipe.className else r.recipeClockSpeed + (recipe.className to v))
                            }
                        }
                    }
                }
            }
        }
    }
    item(key = "sloop-budget") {
        val budget = tab.request.somersloopBudget
        RowCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Place Somersloops for me", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (budget == null) "Or pick buildings and recipes by hand below and in the graph."
                        else "The planner picks the buildings where they help most, using at most this many.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(budget != null, onCheckedChange = { on ->
                    tab.updateRequest { r -> r.copy(somersloopBudget = if (on) tab.data.metadata.somersloopsOwned.takeIf { it > 0 } ?: 10 else null) }
                })
            }
            if (budget != null) {
                NumberField(
                    budget.toDouble(),
                    { v -> if (v >= 0) tab.updateRequest { r -> r.copy(somersloopBudget = v.toInt()) } },
                    modifier = Modifier.width(160.dp).padding(top = 8.dp),
                    label = "Somersloops",
                )
            }
        }
    }
    items(data.manufacturers, key = { "m" + it.className }) { machine ->
        val enabled = machine.className !in tab.request.blockedMachines
        val slots = Somersloop.slots(machine.className)
        Column {
            Row(
                Modifier.fillMaxWidth()
                    .clickable { tab.updateRequest { r -> r.copy(blockedMachines = r.blockedMachines.toggle(machine.className)) } }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(enabled, onCheckedChange = null)
                EntityIcon(data, machine.className, 32.dp)
                Spacer(Modifier.width(8.dp))
                Text(machine.name, modifier = Modifier.weight(1f))
                if (enabled) {
                    ClockField(tab.request.clockSpeedFor(machine.className)) { v ->
                        tab.updateRequest { r ->
                            r.copy(machineClockSpeed = if (v == r.clockSpeed) r.machineClockSpeed - machine.className else r.machineClockSpeed + (machine.className to v))
                        }
                    }
                }
            }
            if (enabled && slots > 0 && tab.request.somersloopBudget == null) {
                FilterChip(
                    selected = machine.className in tab.request.amplifiedMachines,
                    onClick = { tab.toggleAmplifiedMachine(machine.className) },
                    label = { Text("Somersloops in all ($slots ${if (slots == 1) "slot" else "slots"} each)") },
                    modifier = Modifier.padding(start = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun ClockField(value: Double, onValueChange: (Double) -> Unit) {
    NumberField(
        value,
        { v -> if (v > 0) onValueChange(v.coerceIn(ProductionRequest.MIN_CLOCK_SPEED, ProductionRequest.MAX_CLOCK_SPEED)) },
        modifier = Modifier.width(120.dp),
        label = "Clock",
        suffix = "%",
    )
}

/** Power plants inside the line: which generators and fuels it may use to cover its own power. */
@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.factoryPowerSection(data: GameData, tab: ProductionTab) {
    item(key = "power-hint") {
        Hint(
            "Let this line run its own power plants. The planner adds the generators, their fuel chain and water " +
                "so they cover every building and extractor in the line, choosing the cheapest of the fuels you allow.",
        )
    }
    item(key = "power-switch") {
        val power = tab.request.power
        RowCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Power plants in this line", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(power != null, onCheckedChange = { on ->
                    tab.updateRequest { r ->
                        r.copy(power = if (on) FactoryPower(generators = mapOf("Desc_GeneratorCoal_C" to listOf("Desc_Coal_C"))) else null)
                    }
                })
            }
            if (power != null) {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        power.extraPercent,
                        { v -> if (v >= 0) tab.updateRequest { r -> r.copy(power = r.power?.copy(extraPercent = v)) } },
                        modifier = Modifier.weight(1f),
                        label = "Extra power",
                        suffix = "%",
                    )
                    NumberField(
                        power.clockSpeed,
                        { v -> if (v > 0) tab.updateRequest { r -> r.copy(power = r.power?.copy(clockSpeed = v.coerceIn(ProductionRequest.MIN_CLOCK_SPEED, ProductionRequest.MAX_CLOCK_SPEED))) } },
                        modifier = Modifier.weight(1f),
                        label = "Generator clock",
                        suffix = "%",
                    )
                }
            }
        }
    }
    val power = tab.request.power ?: return
    items(data.generators.values.toList(), key = { "gen" + it.className }) { generator ->
        val allowed = power.generators[generator.className].orEmpty()
        RowCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EntityIcon(data, generator.className, 32.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(data.nameOf(generator.className), style = MaterialTheme.typography.titleSmall)
                    Text("${fmt(generator.powerProduction)} MW each at 100 %", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                generator.fuel.filter { data.recipe(data.powerRecipeClass(generator.className, it)) != null }.forEach { fuel ->
                    FilterChip(
                        selected = fuel in allowed,
                        onClick = {
                            tab.updateRequest { r ->
                                val current = r.power ?: return@updateRequest r
                                val fuels = current.generators[generator.className].orEmpty().toggle(fuel)
                                r.copy(power = current.copy(generators = if (fuels.isEmpty()) current.generators - generator.className else current.generators + (generator.className to fuels)))
                            }
                        },
                        label = { Text(data.nameOf(fuel)) },
                    )
                }
            }
        }
    }
}

/** Taking what lines above this one (in the same folder) leave over. */
@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.leftoversCard(state: AppState, data: GameData, tab: ProductionTab) {
    item(key = "leftovers-in") {
        val on = tab.data.metadata.takeLeftovers
        val sources = state.leftoverSources(tab)
        RowCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Use leftovers from other lines", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Takes what the lines above this one in its folder leave over, before mining anything. " +
                            "Lines further down get what's still left.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(on, onCheckedChange = { state.setTakeLeftovers(tab, it) })
            }
            if (on) {
                if (sources.isEmpty()) {
                    Text("No line above this one in its folder yet. Move this line down or add lines above it.", style = MaterialTheme.typography.bodySmall)
                }
                val taken = tab.request.input.filter { it.leftover }
                val used = tab.result?.input.orEmpty()
                sources.forEach { source ->
                    val included = source.uid !in tab.data.metadata.leftoverExcluded
                    Row(
                        Modifier.fillMaxWidth().clickable { state.toggleLeftoverSource(tab, source) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(included, onCheckedChange = null)
                        Column(Modifier.weight(1f)) {
                            Text(source.name, style = MaterialTheme.typography.bodyMedium)
                            val offers = taken.filter { it.source == source.uid }
                            Text(
                                when {
                                    !included -> "Not used"
                                    offers.isEmpty() -> "Nothing left over"
                                    else -> offers.joinToString { "${fmt(it.amount)} ${data.nameOf(it.item!!)}" } + " available"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                val usedLeftovers = taken.mapNotNull { it.item }.distinct().mapNotNull { item -> used[item]?.takeIf { it.used > 0 }?.let { item to it.used } }
                if (usedLeftovers.isNotEmpty()) {
                    Text(
                        "This plan uses " + usedLeftovers.joinToString { (item, amount) -> "${fmt(amount)} ${data.nameOf(item)}" } + " per minute from them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
