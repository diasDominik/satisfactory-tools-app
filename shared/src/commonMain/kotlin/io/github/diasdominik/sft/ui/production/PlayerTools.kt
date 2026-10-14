package io.github.diasdominik.sft.ui.production

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.diasdominik.sft.data.GameData
import io.github.diasdominik.sft.planner.BoostOptimizer
import io.github.diasdominik.sft.planner.BoostPlan
import io.github.diasdominik.sft.planner.BuildDiff
import io.github.diasdominik.sft.planner.FactoryLayout
import io.github.diasdominik.sft.planner.PlanStats
import io.github.diasdominik.sft.planner.ProductionResult
import io.github.diasdominik.sft.planner.ShoppingList
import io.github.diasdominik.sft.state.ProductionTab
import io.github.diasdominik.sft.ui.EntityRow
import io.github.diasdominik.sft.ui.KeyValueRow
import io.github.diasdominik.sft.ui.NumberField
import io.github.diasdominik.sft.ui.SectionCard
import io.github.diasdominik.sft.ui.fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Upgrade diff: what changed since the plan was marked as built. */
internal fun LazyListScope.upgradeSection(data: GameData, tab: ProductionTab, result: ProductionResult) {
    item(key = "ov-upgrade") {
        val built = tab.data.metadata.builtPlan
        val changes = remember(built, result) { if (built.isEmpty()) emptyList() else BuildDiff.changes(built, result) }
        SectionCard("Built factory") {
            when {
                built.isEmpty() -> Hint("Mark the plan as built once your factory stands. When you later unlock an alternate, " +
                    "a better belt or anything else changes the plan, this shows exactly which buildings to add, remove or reclock.")
                changes.isEmpty() -> Hint("The plan matches what you built.")
                else -> {
                    Text("To upgrade what you built:", style = MaterialTheme.typography.labelLarge)
                    changes.forEach { c ->
                        val what = buildList {
                            when {
                                c.isNew -> add("new: build ${c.plannedCount}")
                                c.isRemoved -> add("no longer needed: tear down ${c.builtCount}")
                                c.delta > 0 -> add("add ${c.delta} (${c.builtCount} → ${c.plannedCount})")
                                c.delta < 0 -> add("remove ${-c.delta} (${c.builtCount} → ${c.plannedCount})")
                            }
                            if (c.clockChanged) add("clock ${fmt(c.builtClock, 2)} % → ${fmt(c.plannedClock, 2)} %")
                            if (c.somersloopsChanged) add("Somersloops ${c.builtSomersloops} → ${c.plannedSomersloops}")
                        }.joinToString(" · ")
                        EntityRow(data, c.machine, subtitle = "${data.nameOf(c.name)}: $what")
                    }
                }
            }
            Row(Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { tab.markPlanBuilt(result) }, enabled = built.isEmpty() || changes.isNotEmpty()) {
                    Text(if (built.isEmpty()) "Mark plan as built" else "Mark this plan as built")
                }
            }
        }
    }
}

/** Optimizer: where the player's Somersloops and Power Shards do the most good in this line. */
internal fun LazyListScope.boostSection(data: GameData, tab: ProductionTab) {
    item(key = "ov-boost") {
        val metadata = tab.data.metadata
        var plan by remember(tab.request) { mutableStateOf<BoostPlan?>(null) }
        var working by remember { mutableStateOf(false) }
        var failed by remember(tab.request) { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        SectionCard("Somersloops and Power Shards") {
            Hint("Enter how many you have and get a suggestion: Somersloops go where they save the most raw resources " +
                "(or add the most output), Power Shards where they save the most buildings.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(metadata.somersloopsOwned.toDouble(), { if (it >= 0) tab.setOwned(somersloops = it.toInt()) }, Modifier.weight(1f), label = "Somersloops")
                NumberField(metadata.powerShardsOwned.toDouble(), { if (it >= 0) tab.setOwned(shards = it.toInt()) }, Modifier.weight(1f), label = "Power Shards")
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !working && (metadata.somersloopsOwned > 0 || metadata.powerShardsOwned > 0),
                    onClick = {
                        working = true
                        val request = tab.request
                        scope.launch {
                            val found = withContext(Dispatchers.Default) {
                                BoostOptimizer.optimize(data, request, metadata.somersloopsOwned, metadata.powerShardsOwned)
                            }
                            plan = found
                            failed = found == null
                            working = false
                        }
                    },
                ) { Text("Suggest") }
                if (working) CircularProgressIndicator(Modifier.padding(4.dp))
            }
            if (failed) Hint("No suggestion: the line has no plan to improve.")
            plan?.let { p ->
                if (p.amplifiedRecipes.isEmpty() && p.recipeClockSpeed.isEmpty()) {
                    Hint("Nothing to gain here with what you have.")
                } else {
                    if (p.amplifiedRecipes.isNotEmpty()) {
                        Text("Somersloops (${p.somersloopsUsed}): fill every slot in", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                        p.amplifiedRecipes.forEach { Text("• " + (data.recipe(it)?.name ?: it), style = MaterialTheme.typography.bodyMedium) }
                    }
                    if (p.recipeClockSpeed.isNotEmpty()) {
                        Text("Power Shards (${p.shardsUsed}): overclock", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                        p.recipeClockSpeed.forEach { (recipe, clock) -> Text("• ${data.recipe(recipe)?.name ?: recipe} to ${fmt(clock, 0)} %", style = MaterialTheme.typography.bodyMedium) }
                    }
                    Comparison(p.before, p.after)
                    Button(onClick = { tab.applyBoost(p) }, modifier = Modifier.padding(top = 8.dp)) { Text("Apply to this line") }
                }
            }
        }
    }
}

@Composable
private fun Comparison(before: PlanStats, after: PlanStats) {
    KeyValueRow("Raw resources", "${fmt(before.rawPerMinute)} → ${fmt(after.rawPerMinute)} /min")
    KeyValueRow("Buildings", "${before.buildings} → ${after.buildings}")
    KeyValueRow("Power", "${fmt(before.power)} → ${fmt(after.power)} MW")
    if (kotlin.math.abs(before.output - after.output) > 1e-6) KeyValueRow("Output", "${fmt(before.output)} → ${fmt(after.output)} /min")
}

/** Everything to craft before building the suggested layout. */
@Composable
internal fun ShoppingListCard(data: GameData, result: ProductionResult, layout: FactoryLayout, beltTier: Int, pipeTier: Int) {
    val list = remember(result, layout) { ShoppingList.build(data, result, layout, beltTier, pipeTier) }
    SectionCard("Shopping list", Modifier.padding(top = 10.dp)) {
        list.entries.forEach { e -> EntityRow(data, e.building, trailing = { Text("${e.count}×") }) }
        if (list.beltMetres > 0) EntityRow(data, ShoppingList.beltClass(beltTier), subtitle = "about ${fmt(list.beltMetres, 0)} m", trailing = { Text("Mk.$beltTier") })
        if (list.pipeMetres > 0) EntityRow(data, ShoppingList.pipeClass(pipeTier), subtitle = "about ${fmt(list.pipeMetres, 0)} m", trailing = { Text("Mk.$pipeTier") })
        Text("Materials (without belts and pipes)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        list.materials.forEach { (item, amount) -> EntityRow(data, item, trailing = { Text(fmt(amount, 0)) }) }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
}
