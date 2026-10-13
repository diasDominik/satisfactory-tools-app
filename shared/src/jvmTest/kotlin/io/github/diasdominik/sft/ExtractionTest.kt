package io.github.diasdominik.sft

import io.github.diasdominik.sft.data.DataLoader
import io.github.diasdominik.sft.data.GameVersion
import io.github.diasdominik.sft.data.Purity
import io.github.diasdominik.sft.planner.Extraction
import io.github.diasdominik.sft.planner.ProductionData
import io.github.diasdominik.sft.planner.ProductionDiagnosis
import io.github.diasdominik.sft.planner.ProductionGraph
import io.github.diasdominik.sft.planner.ProductionItem
import io.github.diasdominik.sft.planner.ProductionRequest
import io.github.diasdominik.sft.planner.ProductionResult
import io.github.diasdominik.sft.planner.ProductionSolver
import io.github.diasdominik.sft.planner.ResourceExtraction
import io.github.diasdominik.sft.planner.SftFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtractionTest {
    private val data = DataLoader.parse(GameVersion.V1_0, File("src/commonMain/composeResources/files/data1.0.json").readText())
    private val solver = ProductionSolver(data)

    private fun rate(extractor: String, resource: String, purity: Purity = Purity.NORMAL, clock: Double = 100.0) =
        Extraction.rate(data, assertNotNull(Extraction.settings(data, ProductionRequest(extraction = mapOf(resource to ResourceExtraction(extractor, purity, clock))), resource)))

    @Test
    fun ratesMatchTheGame() {
        assertEquals(60.0, rate(MK1, IRON_ORE), 1e-9)
        assertEquals(120.0, rate(MK2, IRON_ORE), 1e-9)
        assertEquals(240.0, rate(MK3, IRON_ORE), 1e-9)
        assertEquals(30.0, rate(MK1, IRON_ORE, Purity.IMPURE), 1e-9)
        assertEquals(240.0, rate(MK2, IRON_ORE, Purity.PURE), 1e-9)
        assertEquals(1200.0, rate(MK3, IRON_ORE, Purity.PURE, 250.0), 1e-9)
        assertEquals(60.0, rate(MK2, IRON_ORE, Purity.NORMAL, 50.0), 1e-9)
        assertEquals(120.0, rate("Desc_OilPump_C", "Desc_LiquidOil_C"), 1e-9)
        assertEquals(240.0, rate("Desc_OilPump_C", "Desc_LiquidOil_C", Purity.PURE), 1e-9)
        assertEquals(120.0, rate("Desc_WaterPump_C", "Desc_Water_C"), 1e-9)
        assertEquals(60.0, rate("Desc_FrackingExtractor_C", "Desc_NitrogenGas_C"), 1e-9)
    }

    @Test
    fun everyResourceHasAnExtractor() {
        for (resource in data.resources.keys) {
            val settings = assertNotNull(Extraction.settings(data, ProductionRequest(), resource), "settings for $resource")
            assertTrue(Extraction.rate(data, settings) > 0, "rate for $resource")
        }
    }

    @Test
    fun defaultsToTheFirstTierOnANormalNode() {
        val settings = assertNotNull(Extraction.settings(data, ProductionRequest(), IRON_ORE))
        assertEquals(MK1, settings.extractor)
        assertEquals(Purity.NORMAL, settings.purity)
        assertEquals("Desc_WaterPump_C", Extraction.settings(data, ProductionRequest(), "Desc_Water_C")?.extractor)
        assertEquals("Desc_OilPump_C", Extraction.settings(data, ProductionRequest(), "Desc_LiquidOil_C")?.extractor)
    }

    /** The example from the feature request: 1000 iron rods with Miner Mk.2 on pure nodes. */
    @Test
    fun ironRodsWithMk2OnPureNodes() {
        val request = ProductionRequest(
            production = listOf(ProductionItem("Desc_IronRod_C", amount = 1000.0)),
            extraction = mapOf(IRON_ORE to ResourceExtraction(MK2, Purity.PURE)),
        )
        val out = assertNotNull(solver.solve(request))
        assertEquals(1000.0, out.mined.getValue(IRON_ORE), 1e-6)
        val result = ProductionResult(request, ProductionGraph.from(out), data)
        val iron = result.extraction.single { it.resource == IRON_ORE }
        assertEquals(MK2, iron.extractor.className)
        assertEquals(240.0, iron.ratePerExtractor, 1e-9)
        assertEquals(5, iron.count)
        assertEquals(4, iron.groups[0].amount)
        assertEquals(100.0, iron.groups[0].clockSpeed, 1e-9)
        assertEquals(1, iron.groups[1].amount)
        assertEquals(16.6667, iron.groups[1].clockSpeed, 1e-3)
        // Miners count as buildings and use power.
        assertEquals(5, result.buildings.buildings.getValue(MK2).amount)
        assertTrue(result.power.byBuilding.getValue(MK2).power.average > 4 * 15.0)
    }

    @Test
    fun overclockingNeedsFewerMiners() {
        val request = ProductionRequest(
            production = listOf(ProductionItem("Desc_IronRod_C", amount = 1000.0)),
            extraction = mapOf(IRON_ORE to ResourceExtraction(MK3, Purity.PURE, 250.0)),
        )
        val result = ProductionResult(request, ProductionGraph.from(assertNotNull(solver.solve(request))), data)
        assertEquals(1, result.extraction.single { it.resource == IRON_ORE }.count)
    }

    @Test
    fun nodeCountCapsTheResource() {
        val settings = ResourceExtraction(MK2, Purity.PURE, nodes = 2)
        val request = ProductionRequest(
            production = listOf(ProductionItem("Desc_IronRod_C", amount = 400.0)),
            extraction = mapOf(IRON_ORE to settings),
        )
        assertEquals(480.0, Extraction.effectiveLimit(data, request, IRON_ORE), 1e-9)
        val out = assertNotNull(solver.solve(request))
        assertEquals(400.0, out.mined.getValue(IRON_ORE), 1e-6)
    }

    @Test
    fun tooFewNodesExplainsTheShortfall() {
        val request = ProductionRequest(
            production = listOf(ProductionItem("Desc_IronRod_C", amount = 1000.0)),
            extraction = mapOf(IRON_ORE to ResourceExtraction(MK2, Purity.PURE, nodes = 2)),
            // Without the Converter, iron ore can only come from the nodes.
            blockedMachines = listOf("Desc_Converter_C"),
        )
        assertNull(solver.solve(request))
        val problems = ProductionDiagnosis(data).explain(request)
        val problem = problems.single()
        assertTrue("Iron Ore" in problem && "1,000" in problem && "480" in problem && "2 pure nodes" in problem, problem)
    }

    @Test
    fun extractionSurvivesSftExport() {
        val tab = ProductionData(request = ProductionRequest(extraction = mapOf(IRON_ORE to ResourceExtraction(MK3, Purity.IMPURE, 150.0, 4))))
        val imported = SftFormat.import(SftFormat.export(listOf(tab), listOf("Iron")))
        assertEquals(tab.request.extraction, imported.single().request.extraction)
    }

    private companion object {
        const val IRON_ORE = "Desc_OreIron_C"
        const val MK1 = "Desc_MinerMk1_C"
        const val MK2 = "Desc_MinerMk2_C"
        const val MK3 = "Desc_MinerMk3_C"
    }
}
