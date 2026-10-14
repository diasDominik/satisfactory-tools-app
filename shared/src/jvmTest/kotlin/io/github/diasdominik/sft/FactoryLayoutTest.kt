package io.github.diasdominik.sft

import io.github.diasdominik.sft.data.DataLoader
import io.github.diasdominik.sft.data.GameVersion
import io.github.diasdominik.sft.planner.ConnectorKind
import io.github.diasdominik.sft.planner.FactoryLayoutPlanner
import io.github.diasdominik.sft.planner.ProductionGraph
import io.github.diasdominik.sft.planner.ProductionItem
import io.github.diasdominik.sft.planner.ProductionRequest
import io.github.diasdominik.sft.planner.ProductionResult
import io.github.diasdominik.sft.planner.ProductionSolver
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FactoryLayoutTest {
    private val data = DataLoader.parse(GameVersion.V1_0, File("src/commonMain/composeResources/files/data1.0.json").readText())

    private fun layout(item: String, amount: Double) = run {
        val request = ProductionRequest(production = listOf(ProductionItem(item, amount = amount)))
        val result = ProductionResult(request, ProductionGraph.from(ProductionSolver(data).solve(request)!!), data)
        result to FactoryLayoutPlanner.plan(data, result, beltTier = 3, pipeTier = 1)
    }

    @Test
    fun reinforcedPlatesAreLaidOutFromOreToProduct() {
        val (result, plan) = layout("Desc_IronPlateReinforced_C", 5.0)
        // Every building of the plan is placed exactly once.
        assertEquals(result.buildings.buildings.filterKeys { it != "Desc_MinerMk1_C" }.values.sumOf { it.amount }, plan.rows.sumOf { it.count })
        // Smelting comes first, the Assembler last.
        assertEquals("Desc_SmelterMk1_C", plan.rows.first().machine)
        assertEquals("Desc_AssemblerMk1_C", plan.rows.last().machine)
        // Each ingredient lane of every row is fed by a belt, and each product lane leads somewhere.
        for (row in plan.rows) for (lane in row.lanes) {
            val connected = plan.belts.any { b -> b.item == lane.item && lane.y in (if (lane.input) b.toYs else b.fromYs) }
            assertTrue(connected, "${row.recipe} ${lane.item} input=${lane.input} isn't connected")
        }
        // Raw ore comes in at the top, the product leaves at the bottom.
        assertTrue(plan.belts.any { it.item == "Desc_OreIron_C" && it.fromYs.isEmpty() })
        assertTrue(plan.belts.any { it.item == "Desc_IronPlateReinforced_C" && it.toYs.isEmpty() })
        // A splitter or merger at each building but the last of every manifold lane.
        val manifold = plan.rows.sumOf { (it.count - 1) * it.lanes.size }
        assertTrue(plan.splitters + plan.mergers >= manifold)
        // Iron Ingots go to both Iron Plates and Iron Rods: split once on the bus.
        assertTrue(plan.connectors.any { it.kind == ConnectorKind.SPLITTER && it.x < 0 })
        // Belts on the bus don't share a lane where they overlap.
        for (a in plan.belts) for (b in plan.belts) {
            if (a !== b && a.laneX == b.laneX) assertTrue(a.bottom < b.top || b.bottom < a.top, "${a.item} and ${b.item} overlap")
        }
        assertTrue(plan.foundationsWide > 0 && plan.foundationsLong > 0)
    }

    @Test
    fun fluidsUsePipeJunctions() {
        val (_, plan) = layout("Desc_Plastic_C", 120.0)
        assertTrue(plan.belts.any { it.fluid })
        assertTrue(plan.junctions > 0)
    }
}
