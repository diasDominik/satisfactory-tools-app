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
