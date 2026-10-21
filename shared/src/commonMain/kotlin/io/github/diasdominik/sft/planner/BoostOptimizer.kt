package io.github.diasdominik.sft.planner

import io.github.diasdominik.sft.data.GameData
import kotlin.math.floor

/** Where to put a limited number of Somersloops and Power Shards in one production line. */
data class BoostPlan(
    /** How many buildings of each recipe get every Somersloop slot filled, most first. */
    val amplifiedBuildings: Map<String, Int>,
    /** Recipes to overclock with Power Shards, and the clock speed for each. */
    val recipeClockSpeed: Map<String, Double>,
    /** The budget the planner placed them with; applying the plan keeps it. */
    val somersloopBudget: Int?,
    val somersloopsUsed: Int,
    val shardsUsed: Int,
    val before: PlanStats,
    val after: PlanStats,
)

object BoostOptimizer {
    /**
     * Somersloops: the planner itself places them within the budget (an exact integer program),
     * saving the most weighted raw resources, or adding the most output on lines that maximise.
     * Power Shards then go, greedily, where they save the most buildings per shard. Whatever the
     * line already has amplified or overclocked per recipe is replaced by the suggestion.
     */
    fun optimize(data: GameData, request: ProductionRequest, somersloops: Int, shards: Int): BoostPlan? {
        val plain = request.copy(amplifiedRecipes = emptyList(), amplifiedMachines = emptyList(), recipeClockSpeed = emptyMap(), somersloopBudget = null)
        val baseResult = solve(data, plain) ?: return null
        val before = stats(baseResult.first, baseResult.second)
        val budget = somersloops.takeIf { it > 0 }
        val amplifiedRequest = plain.copy(somersloopBudget = budget)
        val amplifiedResult = if (budget == null) baseResult else solve(data, amplifiedRequest) ?: baseResult

        // Power Shards: fewer buildings for the same output. A recipe can have a plain and an amplified step.
        val steps = amplifiedResult.second.graph.nodes.filterIsInstance<RecipeNode>().groupBy { it.usage.recipe.className }
        val clocks = LinkedHashMap<String, Double>()
        var shardsLeft = shards
        while (shardsLeft > 0) {
            var best: Triple<String, Double, Pair<Int, Double>>? = null // recipe, clock, (shards, buildings saved per shard)
            for ((recipe, nodes) in steps) {
                if (recipe in clocks) continue
                val current = nodes.sumOf { it.machines.count }
                for (perMachine in 1..3) {
                    val clock = 100.0 + 50.0 * perMachine
                    val split = nodes.map { overclocked(it.usage.amount, clock) }
                    val needed = split.sumOf { it.second }
                    if (needed == 0 || needed > shardsLeft) continue
                    val saved = current - split.sumOf { it.first }
                    if (saved <= 0) continue
                    val ratio = saved.toDouble() / needed
                    if (best == null || ratio > best.third.second) best = Triple(recipe, clock, needed to ratio)
                }
            }
            val pick = best ?: break
            clocks[pick.first] = pick.second
            shardsLeft -= pick.third.first
        }
        val finalRequest = amplifiedRequest.copy(recipeClockSpeed = clocks)
        val finalResult = solve(data, finalRequest) ?: return null
        val amplified = finalResult.second.graph.nodes.filterIsInstance<RecipeNode>()
            .filter { it.usage.amplified }
            .associate { it.usage.recipe.className to it.machines.count }
            .entries.sortedByDescending { it.value }.associate { it.key to it.value }
        return BoostPlan(
            amplifiedBuildings = amplified,
            recipeClockSpeed = clocks,
            somersloopBudget = budget,
            somersloopsUsed = finalResult.second.somersloops,
            shardsUsed = shards - shardsLeft,
            before = before,
            after = stats(finalResult.first, finalResult.second),
        )
    }

    /** Buildings and shards for [amount] (in 100 % machines) at [clock], the last building running slower. */
    internal fun overclocked(amount: Double, clock: Double): Pair<Int, Int> {
        val exact = amount * 100 / clock
        val full = floor(exact + 1e-9).toInt()
        val rest = exact - full
        val shards = full * Somersloop.shardsFor(clock) + if (rest > 1e-6) Somersloop.shardsFor(rest * clock) else 0
        return (full + if (rest > 1e-6) 1 else 0) to shards
    }

    private fun solve(data: GameData, request: ProductionRequest): Pair<ProductionRequest, ProductionResult>? {
        val output = ProductionSolver(data).solve(request) ?: return null
        return request to ProductionResult(request, ProductionGraph.from(output), data)
    }

    private fun stats(request: ProductionRequest, result: ProductionResult): PlanStats = HardDriveAdvisor.stats(request, result)
}
