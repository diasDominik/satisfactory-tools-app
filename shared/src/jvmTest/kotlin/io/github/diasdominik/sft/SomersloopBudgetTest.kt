package io.github.diasdominik.sft

import io.github.diasdominik.sft.data.DataLoader
import io.github.diasdominik.sft.data.GameVersion
import io.github.diasdominik.sft.planner.BranchAndBound
import io.github.diasdominik.sft.planner.LinearProgram
import io.github.diasdominik.sft.planner.LpResult
import io.github.diasdominik.sft.planner.ProductionGraph
import io.github.diasdominik.sft.planner.ProductionItem
import io.github.diasdominik.sft.planner.ProductionRequest
import io.github.diasdominik.sft.planner.ProductionResult
import io.github.diasdominik.sft.planner.ProductionSolver
import io.github.diasdominik.sft.planner.ProductionType
import io.github.diasdominik.sft.planner.RecipeNode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The planner placing a limited number of Somersloops itself. */
class SomersloopBudgetTest {
    private val data = DataLoader.parse(GameVersion.V1_0, File("src/commonMain/composeResources/files/data1.0.json").readText())

    private fun result(request: ProductionRequest) =
        ProductionResult(request, ProductionGraph.from(ProductionSolver(data).solve(request)!!), data)

    @Test
    fun branchAndBoundFindsTheWholeNumberOptimum() {
        // max 5x + 4y with 6x + 4y <= 24 and x + 2y <= 6: the relaxation is (3, 1.5), the integer optimum (4, 0).
        val lp = LinearProgram(2)
        lp.objective[0] = -5.0
        lp.objective[1] = -4.0
        lp.addConstraint(mapOf(0 to 6.0, 1 to 4.0), LinearProgram.Relation.LE, 24.0)
        lp.addConstraint(mapOf(0 to 1.0, 1 to 2.0), LinearProgram.Relation.LE, 6.0)
        val result = BranchAndBound.solve(lp, listOf(0, 1)) as LpResult.Optimal
        assertEquals(4.0, result.values[0], 1e-6)
        assertEquals(0.0, result.values[1], 1e-6)
        assertEquals(-20.0, result.objective, 1e-6)
    }

    @Test
    fun threeSomersloopsGoToTheRodsAndTheSmelter() {
        // 60 rods: two amplified Constructors (1 slot each) need only 30 ingots, and one amplified
        // Smelter makes those from 15 ore. That beats any other use of three Somersloops.
        val r = result(ProductionRequest(production = listOf(ProductionItem("Desc_IronRod_C", amount = 60.0)), somersloopBudget = 3))
        assertEquals(15.0, r.graph.nodes.filterIsInstance<io.github.diasdominik.sft.planner.MinerNode>().sumOf { it.amount }, 1e-6)
        assertEquals(3, r.somersloops)
        val amplified = r.graph.nodes.filterIsInstance<RecipeNode>().filter { it.usage.amplified }
        assertEquals(setOf("Recipe_IronRod_C", "Recipe_IngotIron_C"), amplified.map { it.usage.recipe.className }.toSet())
    }

    @Test
    fun budgetIsNeverExceededAndUnusedSloopsStayHome() {
        val request = ProductionRequest(production = listOf(ProductionItem("Desc_IronPlateReinforced_C", amount = 10.0)))
        for (budget in listOf(1, 2, 5, 9, 40)) {
            val r = result(request.copy(somersloopBudget = budget))
            assertTrue(r.somersloops <= budget, "budget $budget used ${r.somersloops}")
        }
        // A tiny line can't use 40 Somersloops; the planner doesn't place ones that gain nothing.
        val big = result(request.copy(somersloopBudget = 40))
        val bigger = result(request.copy(somersloopBudget = 400))
        assertEquals(big.somersloops, bigger.somersloops)
    }

    @Test
    fun maximisedLinesMakeMoreWithSomersloops() {
        val request = ProductionRequest(
            production = listOf(ProductionItem("Desc_Computer_C", type = ProductionType.MAXIMIZE, amount = 0.0, ratio = 100.0)),
        )
        val plain = result(request)
        val start = System.nanoTime()
        val amplified = result(request.copy(somersloopBudget = 20))
        println("Computers with 20 Somersloops: ${(System.nanoTime() - start) / 1_000_000} ms, ${BranchAndBound.lastNodes} relaxations")
        val made = { r: ProductionResult -> r.graph.nodes.filterIsInstance<io.github.diasdominik.sft.planner.ProductNode>().sumOf { it.amount } }
        assertTrue(made(amplified) > made(plain) * 1.01, "${made(plain)} -> ${made(amplified)}")
        assertTrue(amplified.somersloops in 1..20)
    }
}
