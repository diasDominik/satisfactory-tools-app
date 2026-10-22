package io.github.diasdominik.sft

import io.github.diasdominik.sft.data.DataLoader
import io.github.diasdominik.sft.data.GameVersion
import io.github.diasdominik.sft.planner.ByproductMode
import io.github.diasdominik.sft.planner.FactoryPower
import io.github.diasdominik.sft.planner.Goal
import io.github.diasdominik.sft.planner.ProductionGraph
import io.github.diasdominik.sft.planner.ProductionItem
import io.github.diasdominik.sft.planner.ProductionRequest
import io.github.diasdominik.sft.planner.ProductionResult
import io.github.diasdominik.sft.planner.ProductionSolver
import io.github.diasdominik.sft.planner.ProductionType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Leftover control, sink point goals and spare power goals. */
class ByproductGoalsTest {
    private val data = DataLoader.parse(GameVersion.V1_0, File("src/commonMain/composeResources/files/data1.0.json").readText())
    private val solver = ProductionSolver(data)

    private fun result(request: ProductionRequest) = ProductionResult(request, ProductionGraph.from(solver.solve(request)!!), data)

    // Plastic from crude oil leaves something behind (residue, or whatever it is turned into).
    private val plastic = ProductionRequest(production = listOf(ProductionItem("Desc_Plastic_C", amount = 60.0)))

    @Test
    fun plasticNormallyLeavesResidue() {
        assertTrue(result(plastic).byproducts.isNotEmpty())
    }

    @Test
    fun forbiddenLeftoversNeedSomewhereToGo() {
        // With basic recipes every way to use up the residue leaves something else behind.
        assertNull(solver.solve(plastic.copy(byproductMode = ByproductMode.NONE)))
        // Burning the residue as fuel in the line's own generators uses it all.
        val burn = plastic.copy(
            byproductMode = ByproductMode.NONE,
            power = FactoryPower(generators = mapOf("Desc_GeneratorFuel_C" to listOf("Desc_LiquidFuel_C"))),
        )
        val r = result(burn)
        assertTrue(r.byproducts.isEmpty(), "${r.byproducts}")
        assertEquals(60.0, r.output.getValue("Desc_Plastic_C"), 1e-6)
    }

    @Test
    fun onlyChosenLeftoversMayLeave() {
        val r = result(plastic.copy(byproductMode = ByproductMode.CHOSEN, allowedByproducts = listOf("Desc_PetroleumCoke_C")))
        assertEquals(setOf("Desc_PetroleumCoke_C"), r.byproducts.keys)
    }

    @Test
    fun fluidsDontCountAsSinkPoints() {
        assertEquals(0, data.sinkPointsOf("Desc_QuantumEnergy_C"))
        assertTrue(data.sinkPointsOf("Desc_IronPlate_C") > 0)
    }

    @Test
    fun sinkPointsGoal() {
        val request = ProductionRequest(production = emptyList(), sinkPointsGoal = Goal(amount = 10_000.0))
        val r = result(request)
        assertTrue(r.sinkPoints >= 10_000 - 1e-3, "${r.sinkPoints}")
        // Maximising points uses the map limits and makes far more.
        val max = result(ProductionRequest(production = emptyList(), sinkPointsGoal = Goal(type = ProductionType.MAXIMIZE)))
        assertTrue(max.sinkPoints > 1_000_000, "${max.sinkPoints}")
    }

    @Test
    fun sinkPointsNextToProducts() {
        val r = result(plastic.copy(sinkPointsGoal = Goal(amount = 5_000.0)))
        assertEquals(60.0, r.output.getValue("Desc_Plastic_C"), 1e-6)
        assertTrue(r.sinkPoints >= 5_000 - 1e-3)
    }

    @Test
    fun spareMegawattsForTheGrid() {
        val coal = FactoryPower(generators = mapOf("Desc_GeneratorCoal_C" to listOf("Desc_Coal_C")))
        val r = result(ProductionRequest(production = emptyList(), power = coal, powerGoal = Goal(amount = 750.0)))
        assertTrue(r.generatedPower - r.power.total.average >= 750 - 1e-3, "${r.generatedPower} - ${r.power.total.average}")
        // Without power plants there's no way to make MW.
        assertNull(solver.solve(ProductionRequest(production = emptyList(), powerGoal = Goal(amount = 750.0))))
    }
}
