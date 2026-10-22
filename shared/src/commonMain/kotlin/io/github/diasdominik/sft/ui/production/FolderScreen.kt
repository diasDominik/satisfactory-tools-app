package io.github.diasdominik.sft.ui.production

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.diasdominik.sft.data.GameData
import io.github.diasdominik.sft.state.AppState
import io.github.diasdominik.sft.ui.DetailScaffold
import io.github.diasdominik.sft.ui.EntityRow
import io.github.diasdominik.sft.ui.KeyValueRow
import io.github.diasdominik.sft.ui.SectionCard
import io.github.diasdominik.sft.ui.StatusColors
import io.github.diasdominik.sft.ui.fmt

/** Totals across the lines of one folder: what the whole project builds, uses and makes. */
@Composable
fun FolderScreen(state: AppState, data: GameData, folderId: String?, onOpenLine: (Long) -> Unit, onBack: () -> Unit) {
    val folder = state.folders.firstOrNull { it.id == folderId }
    val lines = state.linesIn(folder)
    val totals = state.totals(folder)
    DetailScaffold(folder?.name ?: "Lines without a folder", onBack = onBack) {
        item {
            SectionCard("Project") {
                KeyValueRow("Production lines", "${lines.size}" + if (totals.lines < lines.size) " (${lines.size - totals.lines} without a plan)" else "")
                KeyValueRow("Buildings", "${totals.buildingCount}")
                KeyValueRow("Power used", "${fmt(totals.powerUsed)} MW")
                if (totals.powerMade > 0) KeyValueRow("Power made", "${fmt(totals.powerMade)} MW")
                if (totals.sinkPoints > 0) KeyValueRow("Sink points", "${fmt(totals.sinkPoints)} /min")
            }
        }
        if (totals.overLimit.isNotEmpty()) {
            item {
                SectionCard("Over the map's limits") {
                    Text(
                        "Together these lines mine more than the whole map has.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusColors.danger,
                    )
                    totals.overLimit.forEach { (item, usage) ->
                        EntityRow(data, item, trailing = { Text("${fmt(usage.first)} of ${fmt(usage.second)} /min", color = StatusColors.danger) })
                    }
                }
            }
        }
        if (totals.products.isNotEmpty()) {
            item {
                SectionCard("Products") {
                    totals.products.forEach { (item, amount) -> EntityRow(data, item, trailing = { Text("${fmt(amount)} /min") }) }
                }
            }
        }
        if (totals.leftovers.isNotEmpty()) {
            item {
                SectionCard("Leftovers") {
                    Text(
                        "What the lines leave over, before other lines in the folder take any of it.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    totals.leftovers.forEach { (item, amount) -> EntityRow(data, item, trailing = { Text("${fmt(amount)} /min") }) }
                }
            }
        }
        if (totals.raw.isNotEmpty()) {
            item {
                SectionCard("Raw resources") {
                    totals.raw.forEach { (item, amount) -> EntityRow(data, item, trailing = { Text("${fmt(amount)} /min") }) }
                }
            }
        }
        if (totals.buildings.isNotEmpty()) {
            item {
                SectionCard("Buildings") {
                    totals.buildings.entries.sortedByDescending { it.value }.forEach { (building, count) ->
                        EntityRow(data, building, trailing = { Text("$count×") })
                    }
                }
            }
        }
        item {
            SectionCard("Lines") {
                lines.forEach { tab ->
                    Column(Modifier.fillMaxWidth().clickable { onOpenLine(tab.id) }.padding(vertical = 6.dp)) {
                        Text(tab.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            tab.result?.let { "${it.buildings.amount} buildings · ${fmt(it.power.total.average)} MW" } ?: "No plan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
