package io.github.diasdominik.sft.planner

import io.github.diasdominik.sft.data.GameData
import kotlin.math.ceil

/** One thing to build, how many, and what it costs in total. */
data class ShoppingEntry(val building: String, val count: Int, val cost: Map<String, Double>)

/**
 * Everything to craft before building a line: its production buildings and extractors, plus the
 * splitters, mergers, junctions and foundations of the suggested layout, and the belt and pipe length.
 */
data class ShoppingList(
    val entries: List<ShoppingEntry>,
    val beltMetres: Double,
    val pipeMetres: Double,
    val beltTier: Int,
    val pipeTier: Int,
) {
    /** Total materials over all entries. */
    val materials: Map<String, Double>
        get() {
            val total = LinkedHashMap<String, Double>()
            entries.forEach { e -> e.cost.forEach { (item, amount) -> total[item] = (total[item] ?: 0.0) + amount } }
            return total.entries.sortedByDescending { it.value }.associate { it.key to it.value }
        }

    companion object {
        const val SPLITTER = "Desc_ConveyorAttachmentSplitter_C"
        const val MERGER = "Desc_ConveyorAttachmentMerger_C"
        const val JUNCTION = "Desc_PipelineJunction_Cross_C"
        const val FOUNDATION = "Desc_Foundation_8x1_01_C"

        fun beltClass(tier: Int) = "Desc_ConveyorBeltMk${tier}_C"
        fun pipeClass(tier: Int) = if (tier >= 2) "Desc_PipelineMK2_C" else "Desc_Pipeline_C"

        fun build(data: GameData, result: ProductionResult, layout: FactoryLayout, beltTier: Int, pipeTier: Int): ShoppingList {
            fun entry(building: String, count: Int): ShoppingEntry? {
                if (count <= 0) return null
                val b = data.building(building) ?: return null
                val recipe = data.buildingRecipe(b)
                return ShoppingEntry(building, count, recipe?.ingredients?.associate { it.item to it.amount * count }.orEmpty())
            }
            val entries = mutableListOf<ShoppingEntry>()
            result.buildings.buildings.forEach { (building, details) -> entry(building, details.amount)?.let { entries += it } }
            listOf(
                SPLITTER to layout.splitters,
                MERGER to layout.mergers,
                JUNCTION to layout.junctions,
                FOUNDATION to layout.foundationsWide * layout.foundationsLong,
            ).forEach { (building, count) -> entry(building, count)?.let { entries += it } }
            return ShoppingList(entries, ceil(layout.length(fluid = false)), ceil(layout.length(fluid = true)), beltTier, pipeTier)
        }
    }
}
