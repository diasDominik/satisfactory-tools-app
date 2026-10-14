package io.github.diasdominik.sft.ui.production

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.diasdominik.sft.data.GameData
import io.github.diasdominik.sft.planner.FactoryLayout
import io.github.diasdominik.sft.planner.FactoryLayoutPlanner
import io.github.diasdominik.sft.planner.ProductionResult
import io.github.diasdominik.sft.state.ProductionTab
import io.github.diasdominik.sft.ui.EntityRow
import io.github.diasdominik.sft.ui.KeyValueRow
import io.github.diasdominik.sft.ui.SectionCard
import io.github.diasdominik.sft.ui.fmt

/** Floor plan suggestion: where to put the buildings and how to belt them together. */
internal fun LazyListScope.layoutSection(data: GameData, tab: ProductionTab, result: ProductionResult) {
    val metadata = tab.data.metadata
    item(key = "layout-hint") {
        Text(
            "A suggested floor plan: one row of buildings per recipe from ore to product, ingredients on belts in front, " +
                "products behind, and a belt bus on the left connecting the rows. Miners stand on their nodes and aren't shown. " +
                "Belt and pipe tiers come from Visualization.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    item(key = "layout") {
        val plan = remember(result, metadata.beltTier, metadata.pipeTier) {
            FactoryLayoutPlanner.plan(data, result, metadata.beltTier, metadata.pipeTier)
        }
        LayoutLegend()
        FactoryLayoutView(data, plan, Modifier.padding(top = 6.dp))
        LayoutSummary(data, plan)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayoutLegend() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Swatch(Color(0xFFDF691A), "Building")
        Swatch(BeltColor, "Belt")
        Swatch(PipeColor, "Pipe")
        Swatch(SplitterColor, "Splitter")
        Swatch(MergerColor, "Merger")
        Swatch(OverloadColor, "Over one belt")
    }
}

@Composable
private fun Swatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawRect(color) }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun LayoutSummary(data: GameData, plan: FactoryLayout) {
    SectionCard("Summary", Modifier.padding(top = 10.dp)) {
        KeyValueRow("Area", "${plan.foundationsWide} × ${plan.foundationsLong} foundations (${fmt(plan.foundationsWide * 8.0)} × ${fmt(plan.foundationsLong * 8.0)} m)")
        KeyValueRow("Rows", "${plan.rows.size}")
        KeyValueRow("Splitters", "${plan.splitters}")
        KeyValueRow("Mergers", "${plan.mergers}")
        if (plan.junctions > 0) KeyValueRow("Pipeline Junctions", "${plan.junctions}")
    }
    SectionCard("Rows", Modifier.padding(top = 10.dp)) {
        plan.rows.forEachIndexed { index, row ->
            val recipe = data.recipe(row.recipe)
            EntityRow(
                data, row.machine,
                subtitle = buildString {
                    append("Row ${index + 1}: ${recipe?.name ?: row.recipe}")
                    val inputs = row.lanes.filter { it.input }.joinToString { data.nameOf(it.item) }
                    val outputs = row.lanes.filter { !it.input }.joinToString { data.nameOf(it.item) }
                    if (inputs.isNotEmpty()) append(" · in: $inputs")
                    if (outputs.isNotEmpty()) append(" · out: $outputs")
                    append(" · ${row.width.toInt()} m wide")
                },
                trailing = { Text("${row.count}×") },
            )
        }
    }
    SectionCard("Belts and pipes", Modifier.padding(top = 10.dp)) {
        plan.belts.forEach { belt ->
            val kind = if (belt.fluid) "pipe" else "belt"
            KeyValueRow(
                "${data.nameOf(belt.item)}: ${belt.fromLabel} → ${belt.toLabel}",
                "${fmt(belt.amount)}/min · ${belt.lines}× Mk.${belt.tier} $kind${if (belt.lines > 1) "s" else ""}",
            )
        }
    }
}
