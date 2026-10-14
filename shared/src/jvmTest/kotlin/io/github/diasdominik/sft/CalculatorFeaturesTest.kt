package io.github.diasdominik.sft

import io.github.diasdominik.sft.data.DataLoader
import io.github.diasdominik.sft.data.GameVersion
import io.github.diasdominik.sft.planner.InputItem
import io.github.diasdominik.sft.planner.Logistics
import io.github.diasdominik.sft.planner.PowerPlanner
import io.github.diasdominik.sft.planner.ProductionData
import io.github.diasdominik.sft.planner.ProductionGraph
import io.github.diasdominik.sft.planner.ProductionItem
import io.github.diasdominik.sft.planner.ProductionRequest
import io.github.diasdominik.sft.planner.ProductionResult
import io.github.diasdominik.sft.planner.ProductionSolver
import io.github.diasdominik.sft.planner.SftFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Somersloops, belt and pipe capacity, the power plant planner and linked lines. */
class CalculatorFeaturesTest {
    private val data = DataLoader.parse(GameVersion.V1_0, File("src/commonMain/composeResources/files/data1.0.json").readText())
    private val solver = ProductionSolver(data)

    private fun ironRods(request: ProductionRequest): ProductionResult {
        val r = request.copy(production = listOf(ProductionItem("Desc_IronRod_C", amount = 60.0)))
        return ProductionResult(r, ProductionGraph.from(solver.solve(r)!!), data)
    }

    @Test
    fun somersloopsDoubleOutputAtFourTimesThePower() {
        val result = ironRods(ProductionRequest(amplifiedRecipes = listOf("Recipe_IronRod_C")))
        val constructors = result.power.byBuilding.getValue(CONSTRUCTOR)
        // 30 rods/min per Constructor from 15 ingots: half the Constructors and half the ore.
        assertEquals(2, constructors.amount)
        assertEquals(2 * 4.0 * 4, constructors.power.average, 1e-6)
        assertEquals(2, result.somersloops)
        assertEquals(30.0, result.rawResources.getValue("Desc_OreIron_C").used, 1e-6)
        assertEquals(60.0, result.output.getValue("Desc_IronRod_C"), 1e-9)
    }

    @Test
    fun somersloopsForAWholeBuildingType() {
        val result = ironRods(ProductionRequest(amplifiedMachines = listOf(CONSTRUCTOR)))
        assertEquals(2, result.power.byBuilding.getValue(CONSTRUCTOR).amount)
        // The Smelters aren't amplified: 30 ingots/min still need one at 100 %.
        assertEquals(1, result.power.byBuilding.getValue("Desc_SmelterMk1_C").amount)
        assertEquals(2, result.somersloops)
    }

    @Test
    fun buildingsWithoutSlotsIgnoreSomersloops() {
        val request = ProductionRequest(amplifiedMachines = listOf("Desc_Packager_C"))
        assertEquals(false, request.isAmplified("Recipe_PackagedWater_C", "Desc_Packager_C"))
    }

    @Test
    fun beltsNeededForALink() {
        assertEquals(1, Logistics.linesNeeded(60.0, Logistics.beltSpeed(1)))
        assertEquals(2, Logistics.linesNeeded(61.0, Logistics.beltSpeed(1)))
        assertEquals(1, Logistics.linesNeeded(1200.0, Logistics.beltSpeed(6)))
        assertEquals(2, Logistics.linesNeeded(700.0, Logistics.pipeSpeed(2)))
        assertEquals(780.0, Logistics.beltSpeed(5))
        assertEquals(1200.0, Logistics.beltSpeed(99))
    }

    @Test
    fun coalPowerPlant() {
        val plan = PowerPlanner.plan(data, "Desc_GeneratorCoal_C", "Desc_Coal_C", 8, 100.0)!!
        assertEquals(600.0, plan.totalPower, 1e-9)
        // 15 Coal and 45 m³ water per generator per minute.
        assertEquals(120.0, plan.fuelPerMinute, 1e-9)
        assertEquals(360.0, plan.waterPerMinute, 1e-9)
        assertEquals(3, plan.waterExtractors)
        assertNull(plan.waste)
    }

    @Test
    fun generatorsForATarget() {
        assertEquals(14, PowerPlanner.generatorsFor(data, "Desc_GeneratorCoal_C", 1000.0, 100.0))
        assertEquals(4, PowerPlanner.generatorsFor(data, "Desc_GeneratorFuel_C", 2500.0, 250.0))
    }

    @Test
    fun overclockedFuelGeneratorScalesLinearly() {
        val plan = PowerPlanner.plan(data, "Desc_GeneratorFuel_C", "Desc_LiquidFuel_C", 1, 250.0)!!
        assertEquals(625.0, plan.totalPower, 1e-9)
        assertEquals(50.0, plan.fuelPerMinute, 1e-9)
        assertEquals(0.0, plan.waterPerMinute, 1e-9)
    }

    @Test
    fun nuclearWaste() {
        val uranium = PowerPlanner.plan(data, "Desc_GeneratorNuclear_C", "Desc_NuclearFuelRod_C", 1, 100.0)!!
        assertEquals(0.2, uranium.fuelPerMinute, 1e-9)
        assertEquals(240.0, uranium.waterPerMinute, 1e-9)
        assertEquals("Desc_NuclearWaste_C" to 10.0, uranium.waste!!.first to uranium.waste!!.second.let { Math.round(it * 1e9) / 1e9 })
        val plutonium = PowerPlanner.plan(data, "Desc_GeneratorNuclear_C", "Desc_PlutoniumFuelRod_C", 1, 100.0)!!
        assertEquals(1.0, plutonium.waste!!.second, 1e-9)
        assertNull(PowerPlanner.plan(data, "Desc_GeneratorNuclear_C", "Desc_FicsoniumFuelRod_C", 1, 100.0)!!.waste)
    }

    @Test
    fun wrongFuelHasNoPlan() {
        assertNull(PowerPlanner.plan(data, "Desc_GeneratorCoal_C", "Desc_LiquidFuel_C", 1, 100.0))
    }

    @Test
    fun linkedInputsAndNewSettingsSurviveExport() {
        val production = ProductionData(
            request = ProductionRequest(
                input = listOf(InputItem("Desc_IronIngot_C", 30.0, source = "abc")),
                amplifiedRecipes = listOf("Recipe_IronRod_C"),
            ),
        ).let { it.copy(metadata = it.metadata.copy(uid = "def", beltTier = 3, pipeTier = 1)) }
        val back = SftFormat.import(SftFormat.export(listOf(production), listOf("Rods"))).single()
        assertEquals(production, back)
    }

    private companion object {
        const val CONSTRUCTOR = "Desc_ConstructorMk1_C"
    }
}

class HardDriveAdvisorTest {
    private val data = DataLoader.parse(GameVersion.V1_0, File("src/commonMain/composeResources/files/data1.0.json").readText())

    @Test
    fun ranksAlternatesByWhatTheySaveInYourLines() {
        val lines = listOf(
            "Wire" to ProductionRequest(production = listOf(ProductionItem("Desc_Wire_C", amount = 120.0))),
            "Screws" to ProductionRequest(production = listOf(ProductionItem("Desc_IronScrew_C", amount = 120.0))),
        )
        // Cast Screw skips the Iron Rods; Iron Wire swaps copper for iron. Whichever saves more weighted
        // resources ranks first.
        val ranked = io.github.diasdominik.sft.planner.HardDriveAdvisor.evaluate(
            data, lines, listOf("Recipe_Alternate_Screw_C", "Recipe_Alternate_Wire_1_C"),
        )
        assertEquals(2, ranked.size)
        assertTrue(ranked[0].scoreSaved >= ranked[1].scoreSaved)
        val screw = ranked.first { it.recipe == "Recipe_Alternate_Screw_C" }
        // Only the screw line changes.
        assertEquals(1, screw.linesImproved)
        assertTrue(screw.buildingsSaved > 0, "Cast Screw needs no rod Constructors")
    }

    @Test
    fun alreadyAllowedAlternatesAreSkipped() {
        val lines = listOf("Screws" to ProductionRequest(
            production = listOf(ProductionItem("Desc_IronScrew_C", amount = 60.0)),
            allowedAlternateRecipes = listOf("Recipe_Alternate_Screw_C"),
        ))
        val ranked = io.github.diasdominik.sft.planner.HardDriveAdvisor.evaluate(data, lines, listOf("Recipe_Alternate_Screw_C"))
        assertTrue(ranked.single().lines.isEmpty())
    }
}

class BoostAndBuildTest {
    private val data = DataLoader.parse(GameVersion.V1_0, File("src/commonMain/composeResources/files/data1.0.json").readText())
    private val rip = ProductionRequest(production = listOf(ProductionItem("Desc_IronPlateReinforced_C", amount = 10.0)))

    private fun result(request: ProductionRequest) =
        ProductionResult(request, ProductionGraph.from(ProductionSolver(data).solve(request)!!), data)

    @Test
    fun somersloopsGoWhereTheySaveMostWithinBudget() {
        val plan = io.github.diasdominik.sft.planner.BoostOptimizer.optimize(data, rip, somersloops = 4, shards = 0)!!
        assertTrue(plan.somersloopsUsed in 1..4, "used ${plan.somersloopsUsed}")
        assertTrue(plan.after.resourceScore < plan.before.resourceScore)
        assertTrue(plan.amplifiedRecipes.isNotEmpty())
    }

    @Test
    fun shardsSaveBuildingsWithinBudget() {
        val plan = io.github.diasdominik.sft.planner.BoostOptimizer.optimize(data, rip, somersloops = 0, shards = 6)!!
        assertTrue(plan.shardsUsed in 1..6)
        assertTrue(plan.after.buildings < plan.before.buildings)
        // The shards the plan needs match what the optimizer spent.
        val applied = rip.copy(recipeClockSpeed = plan.recipeClockSpeed)
        assertEquals(plan.shardsUsed, result(applied).powerShards)
    }

    @Test
    fun overclockedCounts() {
        // 2.4 machines at 250 %: one building at 240 %, which needs 3 shards.
        assertEquals(1 to 3, io.github.diasdominik.sft.planner.BoostOptimizer.overclocked(2.4, 250.0))
        assertEquals(2 to 2, io.github.diasdominik.sft.planner.BoostOptimizer.overclocked(3.0, 150.0))
    }

    @Test
    fun shoppingListCoversBuildingsAndLogistics() {
        val r = result(rip)
        val layout = io.github.diasdominik.sft.planner.FactoryLayoutPlanner.plan(data, r, 3, 1)
        val list = io.github.diasdominik.sft.planner.ShoppingList.build(data, r, layout, 3, 1)
        val counts = list.entries.associate { it.building to it.count }
        assertEquals(layout.splitters, counts[io.github.diasdominik.sft.planner.ShoppingList.SPLITTER] ?: 0)
        assertEquals(layout.mergers, counts[io.github.diasdominik.sft.planner.ShoppingList.MERGER] ?: 0)
        assertEquals(r.buildings.buildings.getValue("Desc_SmelterMk1_C").amount, counts["Desc_SmelterMk1_C"])
        assertTrue(list.beltMetres > 0)
        assertEquals(0.0, list.pipeMetres)
        // A Smelter costs 5 Iron Rods and 8 Wire, so the total includes them.
        assertTrue((list.materials["Desc_Wire_C"] ?: 0.0) >= 8.0)
    }

    @Test
    fun upgradeShowsWhatToChange() {
        val before = result(rip)
        val built = io.github.diasdominik.sft.planner.BuildDiff.snapshot(before)
        assertTrue(io.github.diasdominik.sft.planner.BuildDiff.changes(built, before).isEmpty())
        // Unlocking Cast Screw removes the rod and screw Constructors for screws and adds a new row.
        val after = result(rip.copy(allowedAlternateRecipes = listOf("Recipe_Alternate_Screw_C")))
        val changes = io.github.diasdominik.sft.planner.BuildDiff.changes(built, after)
        assertTrue(changes.any { it.isRemoved && it.key == "Recipe_Screw_C" })
        assertTrue(changes.any { it.isNew && it.key == "Recipe_Alternate_Screw_C" })
    }
}
