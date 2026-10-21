package io.github.diasdominik.sft

import io.github.diasdominik.sft.data.DataLoader
import io.github.diasdominik.sft.data.GameVersion
import io.github.diasdominik.sft.planner.FactoryPower
import io.github.diasdominik.sft.planner.ProductionGraph
import io.github.diasdominik.sft.planner.ProductionItem
import io.github.diasdominik.sft.planner.ProductionRequest
import io.github.diasdominik.sft.planner.ProductionResult
import io.github.diasdominik.sft.planner.ProductionSolver
import io.github.diasdominik.sft.planner.RecipeNode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Power plants inside a production line. */
class FactoryPowerTest {
    private val data = DataLoader.parse(GameVersion.V1_0, File("src/commonMain/composeResources/files/data1.0.json").readText())
    private val plates = ProductionRequest(production = listOf(ProductionItem("Desc_IronPlate_C", amount = 60.0)))

    private fun result(request: ProductionRequest) =
        ProductionResult(request, ProductionGraph.from(ProductionSolver(data).solve(request)!!), data)

    private val coal = FactoryPower(generators = mapOf("Desc_GeneratorCoal_C" to listOf("Desc_Coal_C")))

    @Test
    fun coalGeneratorsCoverTheLine() {
        val r = result(plates.copy(power = coal))
        val generators = r.graph.nodes.filterIsInstance<RecipeNode>().filter { it.usage.isGenerator }
        assertEquals(1, generators.size)
        // Every building and extractor, including the coal miners and water pumps for the generators.
        // The plan counts each building at full power, so the last underclocked one leaves a little spare.
        assertCovers(r, 1.0)
        assertTrue(r.rawResources.containsKey("Desc_Coal_C"))
        assertTrue(r.rawResources.containsKey("Desc_Water_C"))
        assertTrue(r.buildings.buildings.containsKey("Desc_GeneratorCoal_C"))
        assertTrue(r.buildings.buildings.containsKey("Desc_WaterPump_C"))
    }

    @Test
    fun headroomAddsPower() {
        val r = result(plates.copy(power = coal.copy(extraPercent = 50.0)))
        assertCovers(r, 1.5)
    }

    private fun assertCovers(r: ProductionResult, headroom: Double) {
        val needed = r.power.total.average * headroom
        assertTrue(r.generatedPower >= needed - 1e-3 && r.generatedPower <= needed * 1.15, "made ${r.generatedPower} for $needed")
    }

    @Test
    fun cheapestFuelIsPicked() {
        // Compacted Coal needs extra sulfur and coal per MW, so plain coal wins.
        val r = result(plates.copy(power = FactoryPower(generators = mapOf("Desc_GeneratorCoal_C" to listOf("Desc_Coal_C", "Desc_CompactedCoal_C")))))
        val fuels = r.graph.nodes.filterIsInstance<RecipeNode>().filter { it.usage.isGenerator }.map { it.usage.recipe.ingredients.first().item }
        assertEquals(listOf("Desc_Coal_C"), fuels)
    }

    @Test
    fun nuclearLeavesWaste() {
        val request = ProductionRequest(
            production = listOf(ProductionItem("Desc_Computer_C", amount = 5.0)),
            power = FactoryPower(generators = mapOf("Desc_GeneratorNuclear_C" to listOf("Desc_NuclearFuelRod_C"))),
        )
        val r = result(request)
        assertTrue(r.generatedPower >= r.power.total.average - 1e-3)
        assertTrue((r.byproducts["Desc_NuclearWaste_C"] ?: 0.0) > 0)
        assertEquals("Nuclear Power Plant (Uranium Fuel Rod)", r.graph.nodes.filterIsInstance<RecipeNode>().first { it.usage.isGenerator }.usage.recipe.name)
    }

    @Test
    fun withoutPowerNothingChanges() {
        assertEquals(0.0, result(plates).generatedPower)
    }
}
