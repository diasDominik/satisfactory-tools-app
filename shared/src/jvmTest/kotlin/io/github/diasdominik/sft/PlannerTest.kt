package io.github.diasdominik.sft

import io.github.diasdominik.sft.data.DataLoader
import io.github.diasdominik.sft.data.GameVersion
import io.github.diasdominik.sft.planner.ProductionGraph
import io.github.diasdominik.sft.planner.ProductionItem
import io.github.diasdominik.sft.planner.ProductionRequest
import io.github.diasdominik.sft.planner.ProductionResult
import io.github.diasdominik.sft.planner.ProductionSolver
import io.github.diasdominik.sft.planner.ProductionType
import io.github.diasdominik.sft.planner.InputItem
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlannerTest {
    private val data = DataLoader.parse(GameVersion.V1_0, File("src/commonMain/composeResources/files/data1.0.json").readText())
    private val solver = ProductionSolver(data)

    private fun request(vararg items: ProductionItem, alternates: List<String> = emptyList(), input: List<InputItem> = emptyList()) =
        ProductionRequest(production = items.toList(), allowedAlternateRecipes = alternates, input = input)

    @Test
    fun ironPlates() {
        val out = assertNotNull(solver.solve(request(ProductionItem("Desc_IronPlate_C", amount = 60.0))))
        val byRecipe = out.recipes.associate { it.recipe.className to it.amount }
        assertEquals(3.0, byRecipe.getValue("Recipe_IronPlate_C"), 1e-6)
        assertEquals(3.0, byRecipe.getValue("Recipe_IngotIron_C"), 1e-6)
        assertEquals(90.0, out.mined.getValue("Desc_OreIron_C"), 1e-6)
        val result = ProductionResult(request(), ProductionGraph.from(out), data)
        // 3 constructors, 3 smelters and 2 Miner Mk.1 on normal nodes (the default) for 90 ore.
        assertEquals(8, result.buildings.amount)
        assertEquals(2, result.extraction.single().count)
        assertEquals(60.0, result.output.getValue("Desc_IronPlate_C"), 1e-6)
        assertTrue(result.power.total.average > 0)
    }

    @Test
    fun alternateIsUsedWhenCheaper() {
        val out = assertNotNull(
            solver.solve(request(ProductionItem("Desc_IronIngot_C", amount = 65.0), alternates = listOf("Recipe_Alternate_PureIronIngot_C")))
        )
        assertTrue(out.recipes.any { it.recipe.className == "Recipe_Alternate_PureIronIngot_C" })
        assertEquals(35.0, out.mined.getValue("Desc_OreIron_C"), 1e-6)
    }

    @Test
    fun complexProductBalances() {
        val out = assertNotNull(solver.solve(request(ProductionItem("Desc_ModularFrameHeavy_C", amount = 2.0))))
        val result = ProductionResult(request(), ProductionGraph.from(out), data)
        for ((item, details) in result.items) {
            if (data.isResource(item)) continue
            val product = result.output[item] ?: 0.0
            val by = result.byproducts[item] ?: 0.0
            assertEquals(details.produced, details.consumed + product + by, 1e-2, "balance of $item")
        }
    }

    @Test
    fun fluidByproducts() {
        val out = assertNotNull(solver.solve(request(ProductionItem("Desc_Plastic_C", amount = 20.0))))
        assertTrue(out.mined.containsKey("Desc_LiquidOil_C"))
    }

    @Test
    fun maximizeRespectsLimits() {
        val req = ProductionRequest(
            production = listOf(ProductionItem("Desc_IronPlate_C", type = ProductionType.MAXIMIZE)),
            resourceMax = data.resources.keys.associateWith { if (it == "Desc_OreIron_C") 300.0 else 0.0 },
        )
        val out = assertNotNull(solver.solve(req))
        assertEquals(200.0, out.products.getValue("Desc_IronPlate_C"), 1e-4)
    }

    @Test
    fun inputsReplaceMining() {
        val out = assertNotNull(
            solver.solve(request(ProductionItem("Desc_IronPlate_C", amount = 60.0), input = listOf(InputItem("Desc_IronIngot_C", 30.0))))
        )
        assertEquals(30.0, out.inputs.getValue("Desc_IronIngot_C"), 1e-6)
        assertEquals(60.0, out.mined.getValue("Desc_OreIron_C"), 1e-6)
    }

    @Test
    fun impossibleRequestHasNoResult() {
        val req = ProductionRequest(
            production = listOf(ProductionItem("Desc_IronPlate_C", amount = 60.0)),
            blockedResources = data.resources.keys.toList(),
        )
        assertNull(solver.solve(req))
    }
}
