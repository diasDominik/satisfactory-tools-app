package io.github.diasdominik.sft.ui.production

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.diasdominik.sft.data.GameData
import io.github.diasdominik.sft.planner.AlternateImpact
import io.github.diasdominik.sft.planner.HardDriveAdvisor
import io.github.diasdominik.sft.state.AppState
import io.github.diasdominik.sft.ui.DetailScaffold
import io.github.diasdominik.sft.ui.SearchField
import io.github.diasdominik.sft.ui.EntityRow
import io.github.diasdominik.sft.ui.KeyValueRow
import io.github.diasdominik.sft.ui.SectionCard
import io.github.diasdominik.sft.ui.StatusColors
import io.github.diasdominik.sft.ui.fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Which alternate to take from a hard drive: each choice tried in every saved production line. */
@Composable
fun HardDriveScreen(state: AppState, data: GameData, onBack: () -> Unit) {
    val picked: SnapshotStateList<String> = remember { mutableStateListOf() }
    var picking by remember { mutableStateOf(false) }
    var ranking by remember { mutableStateOf<List<AlternateImpact>?>(null) }
    val unlocked = state.unlockedAlternates.orEmpty().toSet()
    LaunchedEffect(picked.toList(), state.tabs.map { it.request }) {
        ranking = null
        if (picked.isEmpty()) return@LaunchedEffect
        val lines = state.tabs.map { it.name to it.request }
        val candidates = picked.toList()
        ranking = withContext(Dispatchers.Default) { HardDriveAdvisor.evaluate(data, lines, candidates) }
    }
    DetailScaffold("Hard drive choice", onBack = onBack) {
        item {
            Text(
                "Pick the alternates a hard drive offers you (up to three). Each one is tried in all your production lines, " +
                    "and the one that saves the most comes first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            SectionCard("Offered alternates") {
                picked.forEach { recipe ->
                    val product = data.recipe(recipe)?.products?.firstOrNull()?.item ?: recipe
                    EntityRow(
                        data, product,
                        subtitle = data.recipe(recipe)?.name?.removePrefix("Alternate: "),
                        trailing = { TextButton(onClick = { picked.remove(recipe) }) { Text("Remove") } },
                    )
                }
                if (picked.size < 3) OutlinedButton(onClick = { picking = true }) { Text("Add alternate") }
            }
        }
        if (state.tabs.isEmpty()) {
            item { Text("Save a production line first; the choice is judged by what your lines need.") }
        }
        val result = ranking
        if (picked.isNotEmpty() && result == null && state.tabs.isNotEmpty()) item { CircularProgressIndicator(Modifier.padding(16.dp)) }
        result?.forEachIndexed { index, impact ->
            item(key = "impact-" + impact.recipe) { ImpactCard(state, data, impact, best = index == 0 && result.size > 1) }
        }
    }
    if (picking) {
        val options = data.alternateRecipes.filter { it.className !in unlocked && it.className !in picked }
        AlternatePicker(data, options.map { it.className }, onDismiss = { picking = false }) { picked += it; picking = false }
    }
}

@Composable
private fun AlternatePicker(data: GameData, recipes: List<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, recipes) {
        recipes.mapNotNull { data.recipe(it) }.filter { r ->
            r.name.contains(query, ignoreCase = true) || r.products.any { data.nameOf(it.item).contains(query, ignoreCase = true) }
        }.sortedBy { it.name }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alternate recipe") },
        text = {
            Column {
                SearchField(query, { query = it }, "Search recipe or product")
                LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                    items(filtered, key = { it.className }) { r ->
                        EntityRow(
                            data, r.products.firstOrNull()?.item ?: r.className,
                            subtitle = r.name.removePrefix("Alternate: "),
                            onClick = { onPick(r.className) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ImpactCard(state: AppState, data: GameData, impact: AlternateImpact, best: Boolean) {
    val recipe = data.recipe(impact.recipe)
    SectionCard((if (best) "Best: " else "") + (recipe?.name?.removePrefix("Alternate: ") ?: impact.recipe)) {
        if (impact.lines.isEmpty() || impact.linesImproved == 0) {
            Text("Changes nothing in your current lines.", style = MaterialTheme.typography.bodyMedium)
        } else {
            if (impact.newlyPossible > 0) {
                Text("Makes ${impact.newlyPossible} line${if (impact.newlyPossible == 1) "" else "s"} possible that can't be planned now.", color = StatusColors.success)
            }
            KeyValueRow("Raw resources saved", "${fmt(impact.rawSaved)} /min")
            KeyValueRow("Buildings saved", "${impact.buildingsSaved}")
            KeyValueRow("Power saved", "${fmt(impact.powerSaved)} MW")
            if (impact.outputGained != 0.0) KeyValueRow("Extra output (maximised lines)", "${fmt(impact.outputGained)} /min")
            Text("By line", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            impact.lines.filter { it.changed }.forEach { line ->
                val b = line.before
                val a = line.after
                val detail = when {
                    line.newlyPossible -> "becomes possible"
                    b == null || a == null -> "no plan"
                    line.maximizes -> "output ${fmt(b.output)} → ${fmt(a.output)} /min"
                    else -> "raw ${fmt(b.rawPerMinute)} → ${fmt(a.rawPerMinute)} /min · buildings ${b.buildings} → ${a.buildings}"
                }
                KeyValueRow(line.line, detail)
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { state.unlockAlternate(impact.recipe) }) { Text("I picked this one") }
        }
    }
}
