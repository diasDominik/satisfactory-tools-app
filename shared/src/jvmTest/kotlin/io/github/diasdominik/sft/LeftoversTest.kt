package io.github.diasdominik.sft

import io.github.diasdominik.sft.data.Constants
import io.github.diasdominik.sft.data.DataLoader
import io.github.diasdominik.sft.data.GameVersion
import io.github.diasdominik.sft.planner.InputItem
import io.github.diasdominik.sft.planner.LeftoverLine
import io.github.diasdominik.sft.planner.Leftovers
import io.github.diasdominik.sft.planner.ProductionGraph
import io.github.diasdominik.sft.planner.ProductionItem
import io.github.diasdominik.sft.planner.ProductionRequest
import io.github.diasdominik.sft.planner.ProductionResult
import io.github.diasdominik.sft.planner.ProductionSolver
import io.github.diasdominik.sft.planner.ProjectTotals
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Handing leftovers between lines, and folder totals. */
class LeftoversTest {
    @Test
    fun linesBelowGetWhatsLeft() {
        val plastic = LeftoverLine("plastic", "p", takeLeftovers = false, byproducts = mapOf("Desc_HeavyOilResidue_C" to 30.0))
        // The first taker used 20 of the 30 it was offered.
        val first = LeftoverLine(
            "first", "p", takeLeftovers = true,
            inputs = listOf(InputItem("Desc_HeavyOilResidue_C", 30.0, "plastic", leftover = true)),
            used = mapOf("Desc_HeavyOilResidue_C" to 20.0),
        )
        val second = LeftoverLine("second", "p", takeLeftovers = true)
        val elsewhere = LeftoverLine("elsewhere", "other", takeLeftovers = true)
        val off = LeftoverLine("off", "p", takeLeftovers = false)
        val out = Leftovers.allocate(listOf(plastic, first, second, elsewhere, off))
        assertEquals(listOf(InputItem("Desc_HeavyOilResidue_C", 30.0, "plastic", leftover = true)), out["first"])
        assertEquals(listOf(InputItem("Desc_HeavyOilResidue_C", 10.0, "plastic", leftover = true)), out["second"])
        assertEquals(emptyList(), out["elsewhere"], "other folders don't share")
        assertEquals(emptyList(), out["off"])
        assertEquals(emptyList(), out["plastic"], "nothing from lines below")
    }

    @Test
    fun ownInputsCountBeforeLeftovers() {
        val source = LeftoverLine("s", null, takeLeftovers = false, byproducts = mapOf("Desc_Water_C" to 100.0))
        val taker = LeftoverLine(
            "t", null, takeLeftovers = true,
            inputs = listOf(InputItem("Desc_Water_C", 50.0), InputItem("Desc_Water_C", 100.0, "s", leftover = true)),
            used = mapOf("Desc_Water_C" to 70.0),
        )
        val next = LeftoverLine("n", null, takeLeftovers = true)
        // 50 came from its own input, so only 20 of the leftovers were taken.
        assertEquals(80.0, Leftovers.allocate(listOf(source, taker, next))["n"]!!.single().amount)
    }

    @Test
    fun excludedAndLinkedSourcesAreSkipped() {
        val a = LeftoverLine("a", null, takeLeftovers = false, byproducts = mapOf("Desc_Rubber_C" to 5.0))
        val b = LeftoverLine("b", null, takeLeftovers = false, byproducts = mapOf("Desc_Rubber_C" to 5.0))
        val t = LeftoverLine("t", null, takeLeftovers = true, excluded = setOf("a"), inputs = listOf(InputItem("Desc_Plastic_C", 1.0, "b")))
        assertEquals(emptyList(), Leftovers.allocate(listOf(a, b, t))["t"])
    }

    @Test
    fun aLineUsesLeftoversInsteadOfMining() {
        val data = DataLoader.parse(GameVersion.V1_0, File("src/commonMain/composeResources/files/data1.0.json").readText())
        val solver = ProductionSolver(data)
        val rods = ProductionRequest(production = listOf(ProductionItem("Desc_IronRod_C", amount = 15.0)))
        val withLeftover = rods.copy(input = listOf(InputItem("Desc_IronIngot_C", 10.0, "other", leftover = true)))
        val r = ProductionResult(withLeftover, ProductionGraph.from(solver.solve(withLeftover)!!), data)
        assertEquals(10.0, r.input.getValue("Desc_IronIngot_C").used, 1e-6)
        assertEquals(5.0, r.rawResources.getValue("Desc_OreIron_C").used, 1e-6)

        // Folder totals add the lines up and flag resources used beyond the map.
        val plain = ProductionResult(rods, ProductionGraph.from(solver.solve(rods)!!), data)
        val totals = ProjectTotals.of(listOf(plain, r), mapOf("Desc_OreIron_C" to 18.0))
        assertEquals(2, totals.lines)
        assertEquals(20.0, totals.raw.getValue("Desc_OreIron_C"), 1e-6)
        assertEquals(30.0, totals.products.getValue("Desc_IronRod_C"), 1e-6)
        assertTrue("Desc_OreIron_C" in totals.overLimit)
        assertTrue(totals.buildingCount > 0)
        assertTrue(Constants.RESOURCE_AMOUNTS.isNotEmpty())
    }
}
