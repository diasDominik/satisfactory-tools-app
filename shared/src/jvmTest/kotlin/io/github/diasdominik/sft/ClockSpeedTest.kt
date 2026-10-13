package io.github.diasdominik.sft

import io.github.diasdominik.sft.data.DataLoader
import io.github.diasdominik.sft.data.GameVersion
import io.github.diasdominik.sft.planner.ProductionData
import io.github.diasdominik.sft.planner.ProductionGraph
import io.github.diasdominik.sft.planner.ProductionItem
import io.github.diasdominik.sft.planner.ProductionRequest
import io.github.diasdominik.sft.planner.ProductionResult
import io.github.diasdominik.sft.planner.ProductionSolver
import io.github.diasdominik.sft.planner.SftFormat
import java.io.File
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals

/** Under- and overclocked production buildings: building counts and power on the game's curve. */
class ClockSpeedTest {
    private val data = DataLoader.parse(GameVersion.V1_0, File("src/commonMain/composeResources/files/data1.0.json").readText())
    private val solver = ProductionSolver(data)

    /** 60 Iron Rods/min: 4 Constructors at 100 %, each making 15/min from 15 Iron Ingots/min. */
    private fun ironRods(clock: Double, machineClock: Map<String, Double> = emptyMap()): ProductionResult {
        val request = ProductionRequest(
            production = listOf(ProductionItem("Desc_IronRod_C", amount = 60.0)),
            clockSpeed = clock,
            machineClockSpeed = machineClock,
        )
        return ProductionResult(request, ProductionGraph.from(solver.solve(request)!!), data)
    }

    private fun power(result: ProductionResult, building: String) = result.power.byBuilding.getValue(building)

    @Test
    fun defaultClockMatchesTheGame() {
        val constructors = power(ironRods(100.0), CONSTRUCTOR)
        assertEquals(4, constructors.amount)
        assertEquals(16.0, constructors.power.average, 1e-6)
    }

    @Test
    fun overclockingNeedsFewerBuildingsAndMorePowerEach() {
        val result = ironRods(200.0)
        val constructors = power(result, CONSTRUCTOR)
        assertEquals(2, constructors.amount)
        // 4 MW × 2^1.321929 = 10 MW per Constructor.
        assertEquals(20.0, constructors.power.average, 1e-3)
        assertEquals(60.0, result.output.getValue("Desc_IronRod_C"), 1e-9)
        assertEquals(2, result.buildings.buildings.getValue(CONSTRUCTOR).amount)
    }

    @Test
    fun underclockingNeedsMoreBuildingsAndLessPower() {
        val constructors = power(ironRods(50.0), CONSTRUCTOR)
        assertEquals(8, constructors.amount)
        // 4 MW × 0.5^1.321929 = 1.6 MW per Constructor.
        assertEquals(12.8, constructors.power.average, 1e-3)
    }

    @Test
    fun theLastBuildingRunsSlowerWhenTheAmountDoesNotDivideEvenly() {
        // 60/min needs 1.6 Constructors at 250 %: one at 250 % and one at 150 %.
        val recipe = power(ironRods(250.0), CONSTRUCTOR).recipes.getValue("Recipe_IronRod_C")
        assertEquals(listOf(1 to 250.0, 1 to 150.0), recipe.machines.map { it.amount to it.clockSpeed })
        val expected = 4 * 2.5.pow(1.321929) + 4 * 1.5.pow(1.321929)
        assertEquals(expected, recipe.power.average, 1e-2)
    }

    @Test
    fun perBuildingClockOnlyChangesThatBuilding() {
        val result = ironRods(100.0, mapOf(CONSTRUCTOR to 250.0))
        assertEquals(2, power(result, CONSTRUCTOR).amount)
        // The Smelters making 60 Iron Ingots/min stay at 100 %: 2 × 4 MW.
        assertEquals(2, power(result, SMELTER).amount)
        assertEquals(8.0, power(result, SMELTER).power.average, 1e-6)
    }

    @Test
    fun clockSpeedIsKeptWithinTheGameLimits() {
        val request = ProductionRequest(clockSpeed = 400.0, machineClockSpeed = mapOf(SMELTER to 0.0))
        assertEquals(250.0, request.clockSpeedFor(CONSTRUCTOR))
        assertEquals(1.0, request.clockSpeedFor(SMELTER))
    }

    @Test
    fun clockSpeedSurvivesSftExport() {
        val request = ProductionRequest(clockSpeed = 150.0, machineClockSpeed = mapOf(CONSTRUCTOR to 200.0))
        val back = SftFormat.import(SftFormat.export(listOf(ProductionData(request = request)), listOf("Rods"))).single().request
        assertEquals(150.0, back.clockSpeed)
        assertEquals(200.0, back.clockSpeedFor(CONSTRUCTOR))
    }

    private companion object {
        const val CONSTRUCTOR = "Desc_ConstructorMk1_C"
        const val SMELTER = "Desc_SmelterMk1_C"
    }
}
