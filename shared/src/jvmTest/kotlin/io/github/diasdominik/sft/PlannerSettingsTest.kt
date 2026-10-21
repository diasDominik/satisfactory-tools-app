package io.github.diasdominik.sft

import io.github.diasdominik.sft.data.Constants
import io.github.diasdominik.sft.data.DataLoader
import io.github.diasdominik.sft.data.GameVersion
import io.github.diasdominik.sft.planner.ClockGrouping
import io.github.diasdominik.sft.planner.ProductionGraph
import io.github.diasdominik.sft.planner.ProductionItem
import io.github.diasdominik.sft.planner.ProductionRequest
import io.github.diasdominik.sft.planner.ProductionResult
import io.github.diasdominik.sft.planner.ProductionSolver
import io.github.diasdominik.sft.planner.WeightMode
import io.github.diasdominik.sft.planner.splitBuildings
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Weight modes, clock grouping, per-recipe clocks and cost multipliers. */
class PlannerSettingsTest {
    private val data = DataLoader.parse(GameVersion.V1_0, File("src/commonMain/composeResources/files/data1.0.json").readText())
    private val plates = ProductionRequest(production = listOf(ProductionItem("Desc_IronPlate_C", amount = 50.0)))

    private fun result(request: ProductionRequest) =
        ProductionResult(request, ProductionGraph.from(ProductionSolver(data).solve(request)!!), data)

    @Test
    fun weightModes() {
        val r = ProductionRequest(resourceMax = mapOf("Desc_OreIron_C" to 100.0, "Desc_OreCopper_C" to 50.0, Constants.WATER to 1e15))
        assertEquals(2.0, r.copy(weightMode = WeightMode.LIMITS).weightOf("Desc_OreCopper_C"))
        assertEquals(1.0, r.copy(weightMode = WeightMode.LIMITS).weightOf("Desc_OreIron_C"))
        assertEquals(0.0, r.copy(weightMode = WeightMode.LIMITS).weightOf(Constants.WATER))
        assertEquals(1.0, r.copy(weightMode = WeightMode.EQUAL).weightOf("Desc_OreUranium_C"))
        assertEquals(Constants.RESOURCE_WEIGHTS["Desc_OreCopper_C"], r.copy(weightMode = WeightMode.MAP, resourceWeight = emptyMap()).weightOf("Desc_OreCopper_C"))
        assertEquals(5.0, r.copy(weightMode = WeightMode.MANUAL, resourceWeight = mapOf("Desc_OreIron_C" to 5.0)).weightOf("Desc_OreIron_C"))
    }

    @Test
    fun equalWeightsChangeThePlan() {
        // Wire: with map weights caterium is dear, so copper wire wins; when every ore costs the same,
        // the fewest-ore recipe does.
        val wire = ProductionRequest(
            production = listOf(ProductionItem("Desc_Wire_C", amount = 60.0)),
            allowedAlternateRecipes = listOf("Recipe_Alternate_Wire_2_C"),
        )
        val map = result(wire.copy(weightMode = WeightMode.MAP)).rawResources.filterValues { it.used > 0 }.keys
        val equal = result(wire.copy(weightMode = WeightMode.EQUAL)).rawResources.filterValues { it.used > 0 }.keys
        assertTrue("Desc_OreGold_C" !in map, "$map")
        assertTrue("Desc_OreGold_C" in equal, "$equal")
    }

    @Test
    fun clockGrouping() {
        assertEquals(listOf(2 to 100.0, 1 to 50.0), splitBuildings(2.5, 100.0, ClockGrouping.UNDERCLOCK_LAST))
        assertEquals(listOf(3 to 83.3334), splitBuildings(2.5, 100.0, ClockGrouping.EQUAL))
        assertEquals(listOf(3 to 100.0), splitBuildings(2.5, 100.0, ClockGrouping.NONE))
        val equal = result(plates.copy(clockGrouping = ClockGrouping.EQUAL))
        assertTrue(equal.power.byBuilding.values.all { b -> b.recipes.values.all { it.machines.size == 1 } })
    }

    @Test
    fun recipeClockWinsOverBuildingClock() {
        val r = result(plates.copy(machineClockSpeed = mapOf("Desc_ConstructorMk1_C" to 50.0), recipeClockSpeed = mapOf("Recipe_IronPlate_C" to 200.0)))
        assertEquals(200.0, r.power.byBuilding.getValue("Desc_ConstructorMk1_C").recipes.getValue("Recipe_IronPlate_C").machines.first().clockSpeed)
    }

    @Test
    fun costMultipliers() {
        // 60 plates need exactly three smelters, so no underclocked building blurs the power.
        val sixty = ProductionRequest(production = listOf(ProductionItem("Desc_IronPlate_C", amount = 60.0)))
        val base = result(sixty)
        val dear = result(sixty.copy(recipeCostMultiplier = 2.0, powerCostMultiplier = 2.0))
        // Plates and ingots both take twice the ingredients: four times the ore.
        assertEquals(base.rawResources.getValue("Desc_OreIron_C").used * 4, dear.rawResources.getValue("Desc_OreIron_C").used, 1e-3)
        // Twice the ingots means twice the smelters, each at twice the power.
        val smelterPower = { r: ProductionResult -> r.power.byBuilding.getValue("Desc_SmelterMk1_C").power.average }
        assertEquals(smelterPower(base) * 4, smelterPower(dear), 0.1)
        // Out of range is clamped to what the game allows.
        assertEquals(2.0, plates.copy(recipeCostMultiplier = 9.0).recipeCost)
    }
}
