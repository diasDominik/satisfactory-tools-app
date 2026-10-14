package io.github.diasdominik.sft.ui.production

import io.github.diasdominik.sft.ui.AppIcons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.diasdominik.sft.data.GameData
import io.github.diasdominik.sft.data.ItemAmount
import io.github.diasdominik.sft.planner.ByproductNode
import io.github.diasdominik.sft.planner.FlowSource
import io.github.diasdominik.sft.planner.InputNode
import io.github.diasdominik.sft.planner.Logistics
import io.github.diasdominik.sft.planner.ProductionRequest
import io.github.diasdominik.sft.planner.Somersloop
import io.github.diasdominik.sft.planner.Extraction
import io.github.diasdominik.sft.planner.MinerNode
import io.github.diasdominik.sft.planner.Power
import io.github.diasdominik.sft.planner.ProductNode
import io.github.diasdominik.sft.planner.ProductionResult
import io.github.diasdominik.sft.planner.RecipeNode
import io.github.diasdominik.sft.planner.graphNodeKey
import io.github.diasdominik.sft.state.ProductionTab
import androidx.compose.ui.geometry.Offset
import io.github.diasdominik.sft.state.ResultStatus
import io.github.diasdominik.sft.ui.AmountChips
import io.github.diasdominik.sft.ui.EmptyState
import io.github.diasdominik.sft.ui.EntityIcon
import io.github.diasdominik.sft.ui.EntityRow
import io.github.diasdominik.sft.ui.GraphToggle
import io.github.diasdominik.sft.ui.GraphView
import io.github.diasdominik.sft.ui.GraphViewEdge
import io.github.diasdominik.sft.ui.GraphViewNode
import io.github.diasdominik.sft.ui.SectionCard
import io.github.diasdominik.sft.ui.StatusColors
import io.github.diasdominik.sft.ui.codex.CodexLinks
import io.github.diasdominik.sft.ui.fmt

private fun Power.text() = if (isVariable) "${fmt(average)} MW (up to ${fmt(max)} MW)" else "${fmt(average)} MW"

@Composable
internal fun StatusMessage(status: ResultStatus, problems: List<String> = emptyList()) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when (status) {
            ResultStatus.NO_INPUT -> Text(
                "Add a product in Setup to see the result.",
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ResultStatus.CALCULATING -> {
                CircularProgressIndicator()
                Spacer(Modifier.width(8.dp))
                Text("Calculating…", modifier = Modifier.padding(top = 12.dp))
            }
            ResultStatus.NO_RESULT -> {
                Text("No solution found", style = MaterialTheme.typography.titleMedium, color = StatusColors.danger)
                problems.forEach { problem ->
                    Text(
                        problem,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ResultStatus.RESULT -> {}
        }
    }
}

@Composable
private fun AmountRow(data: GameData, className: String, value: String, sub: String? = null, color: Color? = null, onClick: (() -> Unit)? = null) {
    EntityRow(
        data, className, subtitle = sub, onClick = onClick,
        trailing = { Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = color ?: MaterialTheme.colorScheme.onSurface) },
    )
}

private fun unit(data: GameData, item: String) = if (data.item(item)?.liquid == true) "m³/min" else "/min"

internal fun LazyListScope.overviewSection(data: GameData, tab: ProductionTab, result: ProductionResult, links: CodexLinks) {
    upgradeSection(data, tab, result)
    if (result.input.isNotEmpty()) {
        item(key = "ov-input") {
            SectionCard("Input") {
                result.input.forEach { (item, input) ->
                    AmountRow(
                        data, item, "${fmt(input.used)} / ${fmt(input.max)}",
                        sub = buildString {
                            append("${fmt(input.usedPercentage)} % used")
                            if (input.producedExtra > 0) append(" · ${fmt(input.producedExtra)} ${unit(data, item)} produced extra")
                        },
                        onClick = { links.open(data, item) },
                    )
                }
            }
        }
    }
    item(key = "ov-resources") {
        SectionCard("Raw resources") {
            val used = result.rawResources.filter { it.value.used > 0 }
            if (used.isEmpty()) Text("No raw resources needed.", style = MaterialTheme.typography.bodyMedium)
            used.forEach { (item, res) ->
                Column(Modifier.clickable { links.open(data, item) }) {
                    AmountRow(
                        data, item, "${fmt(res.used)} ${unit(data, item)}",
                        sub = if (res.max >= 1e12) "unlimited" else "${fmt(res.usedPercentage)} % of ${fmt(res.max)}",
                    )
                    if (res.max < 1e12) {
                        LinearProgressIndicator(
                            progress = { (res.usedPercentage / 100).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            color = if (res.usedPercentage > 90) StatusColors.warning else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            val disabled = result.rawResources.filter { !it.value.enabled }.keys
            if (disabled.isNotEmpty()) {
                Text(
                    "Disabled: " + disabled.joinToString { data.nameOf(it) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
    if (result.extraction.isNotEmpty()) {
        item(key = "ov-extraction") {
            SectionCard("Extractors", actions = { Text("Total: ${result.extraction.sumOf { it.count }}", modifier = Modifier.padding(end = 8.dp)) }) {
                result.extraction.forEach { e ->
                    val s = e.settings
                    AmountRow(
                        data, e.resource, "${e.count}× ${e.extractor.name}",
                        sub = buildList {
                            if (Extraction.hasPurity(e.extractor.className)) add("${s.purity.label} node${if (e.count == 1) "" else "s"}")
                            add("${fmt(e.ratePerExtractor)} ${unit(data, e.resource)} each at ${fmt(s.clockSpeed, 2)} %")
                            add(e.groups.joinToString(" + ") { "${it.amount} at ${fmt(it.clockSpeed, 2)} %" })
                            s.nodes?.let { add("uses ${e.count} of $it nodes") }
                            add("${fmt(e.power)} MW")
                        }.joinToString(" · "),
                        onClick = { links.open(data, e.extractor.className) },
                    )
                }
            }
        }
    }
    item(key = "ov-produced") {
        SectionCard("Produced") {
            result.output.forEach { (item, amount) ->
                AmountRow(data, item, "${fmt(amount)} ${unit(data, item)}", onClick = { links.open(data, item) })
            }
            if (result.byproducts.isNotEmpty()) {
                Text("Byproducts", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                result.byproducts.forEach { (item, amount) ->
                    AmountRow(data, item, "${fmt(amount)} ${unit(data, item)}", color = StatusColors.info, onClick = { links.open(data, item) })
                }
            }
        }
    }
    item(key = "ov-buildings") {
        SectionCard("Buildings", actions = { Text("Total: ${result.buildings.amount}", modifier = Modifier.padding(end = 8.dp)) }) {
            result.buildings.buildings.forEach { (building, details) ->
                AmountRow(data, building, "${details.amount}×", onClick = { links.open(data, building) })
            }
        }
    }
    item(key = "ov-power") {
        SectionCard("Power") {
            Text(result.power.total.text(), style = MaterialTheme.typography.headlineSmall)
        }
    }
    boostSection(data, tab)
    if (result.powerShards > 0) {
        item(key = "ov-shards") {
            SectionCard("Power Shards", actions = { Text("Total: ${result.powerShards}", modifier = Modifier.padding(end = 8.dp)) }) {
                Text("Overclocked buildings need one Power Shard per 50 % above 100 %.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (result.somersloops > 0) {
        item(key = "ov-somersloops") {
            SectionCard("Somersloops", actions = { Text("Total: ${result.somersloops}", modifier = Modifier.padding(end = 8.dp)) }) {
                result.graph.nodes.filterIsInstance<RecipeNode>().filter { it.machines.somersloops > 0 }.forEach { node ->
                    AmountRow(
                        data, node.usage.machine.className, "${node.machines.somersloops}",
                        sub = "${node.usage.recipe.name} · ${node.machines.count}× ${node.usage.machine.name}",
                    )
                }
            }
        }
    }
    item(key = "ov-alternates") {
        SectionCard("Alternate recipes needed", actions = { Text("Total: ${result.alternatesNeeded.size}", modifier = Modifier.padding(end = 8.dp)) }) {
            if (result.alternatesNeeded.isEmpty()) Text("None", style = MaterialTheme.typography.bodyMedium)
            result.alternatesNeeded.forEach { recipe ->
                val product = recipe.products.firstOrNull()?.item ?: return@forEach
                EntityRow(data, product, subtitle = recipe.name.removePrefix("Alternate: "), onClick = { links.open(data, product) })
            }
        }
    }
}

private val RecipeColor = Color(0xFFDF691A)
private val MinerColor = Color(0xFF4E5D6C)
private val InputColor = Color(0xFF8E44AD)
private val ProductColor = Color(0xFF5CB85C)
private val ByproductColor = Color(0xFF1B7A99)

internal fun LazyListScope.graphSection(data: GameData, tab: ProductionTab, result: ProductionResult, links: CodexLinks) {
    item(key = "graph-logistics") {
        val metadata = tab.data.metadata
        Column {
            Text("Best belt", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Logistics.BELT_SPEEDS.forEachIndexed { index, speed ->
                    FilterChip(
                        selected = metadata.beltTier == index + 1,
                        onClick = { tab.setBeltTier(index + 1) },
                        label = { Text("Mk.${index + 1} · ${fmt(speed)}") },
                    )
                }
            }
            Text("Best pipe", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Logistics.PIPE_SPEEDS.forEachIndexed { index, speed ->
                    FilterChip(
                        selected = metadata.pipeTier == index + 1,
                        onClick = { tab.setPipeTier(index + 1) },
                        label = { Text("Mk.${index + 1} · ${fmt(speed)} m³") },
                    )
                }
            }
        }
    }
    item(key = "graph") {
        val metadata = tab.data.metadata
        val built = metadata.built.toSet()
        val request = tab.request
        val (nodes, edges) = remember(result, built, metadata.beltTier, metadata.pipeTier, request) {
            graphModel(data, result, links, built, metadata.beltTier, metadata.pipeTier, request, tab)
        }
        val overloaded = edges.count { it.warning }
        if (overloaded > 0) {
            Text(
                "$overloaded ${if (overloaded == 1) "link carries" else "links carry"} more than one " +
                    "Mk.${metadata.beltTier} belt or Mk.${metadata.pipeTier} pipe can; they're marked in yellow with the number needed.",
                style = MaterialTheme.typography.bodySmall,
                color = StatusColors.warning,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        GraphView(
            nodes, edges, height = 560.dp,
            positions = metadata.graphPositions.mapValues { Offset(it.value.x, it.value.y) },
            onMoveNode = { key, at -> tab.moveGraphNode(key, at.x, at.y) },
            onResetLayout = tab::resetGraphLayout,
            onToggleDone = tab::toggleBuilt,
        )
    }
    item(key = "graph-legend") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Legend(RecipeColor, "Recipe")
            Legend(MinerColor, "Mining")
            Legend(InputColor, "Input")
            Legend(ProductColor, "Product")
            Legend(ByproductColor, "Byproduct")
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun graphModel(
    data: GameData,
    result: ProductionResult,
    links: CodexLinks,
    built: Set<String>,
    beltTier: Int,
    pipeTier: Int,
    request: ProductionRequest,
    tab: ProductionTab,
): Pair<List<GraphViewNode>, List<GraphViewEdge>> {
    val nodes = result.graph.nodes.map { node ->
        val key = graphNodeKey(node)
        // Machines and extractors are what gets built; products and inputs are just ends of the chain.
        val step = node is RecipeNode || node is MinerNode
        when (node) {
            is RecipeNode -> {
                val usage = node.usage
                val machine = usage.machine
                val slots = Somersloop.slots(machine.className)
                val sloops = node.machines.somersloops
                GraphViewNode(
                    node.id,
                    usage.recipe.name,
                    "${fmt(usage.amount * 100 / usage.clockSpeed)}× ${machine.name}" +
                        (if (usage.clockSpeed != 100.0) " at ${fmt(usage.clockSpeed, 2)} %" else "") +
                        (if (sloops > 0) " · $sloops Somersloops" else ""),
                    RecipeColor,
                    details = node.machines.machines.map { "${it.amount}× ${machine.name} at ${fmt(it.clockSpeed, 4)} % clock speed" } +
                        (if (sloops > 0) listOf("$sloops Somersloops ($slots per building): double output, four times the power") else emptyList()) +
                        listOf("Needed power: ${fmt(node.machines.power.average)} MW") +
                        node.inputs.map { "IN: ${fmt(it.maxAmount)} / min ${data.nameOf(it.item)}" } +
                        node.outputs.map { "OUT: ${fmt(it.maxAmount)} / min ${data.nameOf(it.item)}" },
                    action = "Building" to { links.open(data, machine.className) },
                    key = key, checkable = true, done = key in built,
                    toggles = if (slots == 0) emptyList() else {
                        val byMachine = machine.className in request.amplifiedMachines
                        listOf(
                            GraphToggle(
                                if (byMachine) "Somersloops (on for every ${machine.name})" else "Somersloops in every slot ($slots per building)",
                                checked = usage.amplified,
                                enabled = !byMachine,
                                onToggle = { tab.toggleAmplifiedRecipe(usage.recipe.className) },
                            ),
                        )
                    },
                )
            }
            is MinerNode -> GraphViewNode(node.id, data.nameOf(node.item), "Mine ${fmt(node.amount)} / min", MinerColor, action = "Open" to { links.open(data, node.item) }, key = key, checkable = step, done = step && key in built)
            is InputNode -> GraphViewNode(node.id, data.nameOf(node.item), "Input ${fmt(node.amount)} / min", InputColor, action = "Open" to { links.open(data, node.item) }, key = key, checkable = step, done = step && key in built)
            is ProductNode -> GraphViewNode(node.id, data.nameOf(node.item), "Product ${fmt(node.amount)} / min", ProductColor, action = "Open" to { links.open(data, node.item) }, key = key, checkable = step, done = step && key in built)
            is ByproductNode -> GraphViewNode(node.id, data.nameOf(node.item), "Byproduct ${fmt(node.amount)} / min", ByproductColor, action = "Open" to { links.open(data, node.item) }, key = key, checkable = step, done = step && key in built)
        }
    }
    // Merge parallel edges carrying the same item.
    val edges = result.graph.edges.groupBy { Triple(it.from.id, it.to.id, it.item) }.map { (key, list) ->
        val amount = list.sumOf { it.amount }
        val fluid = data.item(key.third)?.liquid == true
        val capacity = if (fluid) Logistics.pipeSpeed(pipeTier) else Logistics.beltSpeed(beltTier)
        val lines = Logistics.linesNeeded(amount, capacity)
        val label = "${data.nameOf(key.third)}\n${fmt(amount)} / min" +
            if (lines > 1) "\n$lines× Mk.${if (fluid) pipeTier else beltTier} ${if (fluid) "pipes" else "belts"}" else ""
        GraphViewEdge(key.first, key.second, label, warning = lines > 1)
    }
    return nodes to edges
}

@Composable
private fun ExpandableCard(header: @Composable () -> Unit, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) { header() }
            Icon(if (expanded) AppIcons.ExpandLess else AppIcons.ExpandMore, contentDescription = if (expanded) "Collapse" else "Expand")
        }
        if (expanded) {
            HorizontalDivider()
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { content() }
        }
    }
}

internal fun LazyListScope.itemsSection(data: GameData, result: ProductionResult, links: CodexLinks) {
    items(result.items.entries.toList(), key = { "it-" + it.key }) { (item, details) ->
        ExpandableCard(header = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EntityIcon(data, item, 32.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(data.nameOf(item), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Produced ${fmt(details.produced)} · consumed ${fmt(details.consumed)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val diff = details.diff
                Text(
                    (if (diff > 0) "+" else "") + fmt(diff),
                    color = when {
                        diff > 0 -> StatusColors.success
                        diff < 0 -> StatusColors.danger
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }) {
            Text("Production", style = MaterialTheme.typography.labelLarge)
            if (details.producers.isEmpty()) Text("—")
            details.producers.forEach { (key, flow) ->
                val label = when (flow.type) {
                    FlowSource.RECIPE -> data.recipe(key)?.name ?: key
                    FlowSource.MINER -> "Extracting / mining"
                    FlowSource.INPUT -> "Input"
                }
                FlowLine(label, "${fmt(flow.amount)} ${unit(data, item)}", "${fmt(flow.percentage)} %")
            }
            Text("Consumption", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            if (details.consumers.isEmpty()) Text("—")
            details.consumers.forEach { (key, flow) ->
                FlowLine(data.recipe(key)?.name ?: key, "${fmt(flow.amount)} ${unit(data, item)}", "${fmt(flow.percentage)} %")
            }
            androidx.compose.material3.TextButton(onClick = { links.open(data, item) }) { Text("Open in codex") }
        }
    }
}

@Composable
private fun FlowLine(label: String, amount: String, percentage: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(amount, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp))
        Text(percentage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun LazyListScope.buildingsSection(data: GameData, result: ProductionResult, links: CodexLinks) {
    items(result.buildings.buildings.entries.toList(), key = { "b-" + it.key }) { (building, details) ->
        ExpandableCard(header = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EntityIcon(data, building, 32.dp)
                Spacer(Modifier.width(10.dp))
                Text(data.nameOf(building), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("${details.amount}×", fontWeight = FontWeight.Medium)
            }
        }) {
            details.recipes.forEach { (recipe, r) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(data.recipe(recipe)?.name ?: data.nameOf(recipe), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${r.amount}×", style = MaterialTheme.typography.bodyMedium)
                }
                AmountChips(data, r.resources.map { ItemAmount(it.key, it.value) }) { links.open(data, it) }
            }
            Text("Total cost", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            AmountChips(data, details.resources.map { ItemAmount(it.key, it.value) }) { links.open(data, it) }
            androidx.compose.material3.TextButton(onClick = { links.open(data, building) }) { Text("Open in codex") }
        }
    }
    item(key = "b-total") {
        SectionCard("Total cost of all ${result.buildings.amount} buildings") {
            if (result.buildings.resources.isEmpty()) EmptyState("No buildings needed")
            AmountChips(data, result.buildings.resources.map { ItemAmount(it.key, it.value) }) { links.open(data, it) }
        }
    }
}

internal fun LazyListScope.powerSection(data: GameData, result: ProductionResult, links: CodexLinks) {
    items(result.power.byBuilding.entries.toList(), key = { "p-" + it.key }) { (building, details) ->
        ExpandableCard(header = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EntityIcon(data, building, 32.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${details.amount}× ${data.nameOf(building)}", style = MaterialTheme.typography.bodyLarge)
                    Text(details.power.text(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }) {
            details.recipes.forEach { (recipe, r) ->
                Text(data.recipe(recipe)?.name ?: data.nameOf(recipe), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                r.machines.forEach { m ->
                    FlowLine("${m.amount}× at ${fmt(m.clockSpeed, 4)} %", m.power.text(), "")
                }
                FlowLine("Subtotal", r.power.text(), "")
            }
            androidx.compose.material3.TextButton(onClick = { links.open(data, building) }) { Text("Open in codex") }
        }
    }
    item(key = "p-total") {
        SectionCard("Total") {
            Text(result.power.total.text(), style = MaterialTheme.typography.headlineSmall)
        }
    }
}
