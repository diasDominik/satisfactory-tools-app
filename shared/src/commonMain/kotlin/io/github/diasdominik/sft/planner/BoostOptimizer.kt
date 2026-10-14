package io.github.diasdominik.sft.planner

import io.github.diasdominik.sft.data.GameData
import kotlin.math.floor

/** Where to put a limited number of Somersloops and Power Shards in one production line. */
data class BoostPlan(
    /** Recipes to fill with Somersloops, in the order they were picked (best first). */
    val amplifiedRecipes: List<String>,
    /** Recipes to overclock with Power Shards, and the clock speed for each. */
    val recipeClockSpeed: Map<String, Double>,
    val somersloopsUsed: Int,
    val shardsUsed: Int,
    val before: PlanStats,
    val after: PlanStats,
)

object BoostOptimizer {
    /**
     * Greedy: Somersloops go, one recipe at a time, where they save the most weighted raw resources per
     * Somersloop; then Power Shards go where they save the most buildings per shard. Whatever the line
     * already has amplified or overclocked per recipe is replaced by the suggestion.
     */
    fun optimize(data: GameData, request: ProductionRequest, somersloops: Int, shards: Int): BoostPlan? {
        val base = request.copy(amplifiedRecipes = emptyList(), amplifiedMachines = emptyList(), recipeClockSpeed = emptyMap())
        val baseResult = solve(data, base) ?: return null
        val before = stats(baseResult.first, baseResult.second)
        // Lines that maximise gain output; the others save raw resources.
        val maximizes = request.production.any { it.type == ProductionType.MAXIMIZE }
        fun value(s: PlanStats) = if (maximizes) -s.output else s.resourceScore

        // Somersloops.
        var current = base
        var currentResult = baseResult
        val amplified = mutableListOf<String>()
        while (true) {
            val used = currentResult.second.somersloops
            val currentScore = value(stats(currentResult.first, currentResult.second))
            var best: Triple<String, Pair<ProductionRequest, ProductionResult>, Double>? = null
            val candidates = currentResult.second.graph.nodes.filterIsInstance<RecipeNode>()
                .filter { Somersloop.slots(it.usage.machine.className) > 0 && it.usage.recipe.className !in amplified }
                .map { it.usage.recipe.className }.distinct()
            for (recipe in candidates) {
                val trial = current.copy(amplifiedRecipes = amplified + recipe)
                val solved = solve(data, trial) ?: continue
                val extra = solved.second.somersloops - used
                if (extra <= 0 || solved.second.somersloops > somersloops) continue
                val gain = currentScore - value(stats(solved.first, solved.second))
                if (gain <= 1e-6) continue
                val ratio = gain / extra
                if (best == null || ratio > best.third) best = Triple(recipe, trial to solved.second, ratio)
            }
            val pick = best ?: break
            amplified += pick.first
            current = pick.second.first
            currentResult = pick.second.first to pick.second.second
        }

        // Power Shards: fewer buildings for the same output.
        val clocks = LinkedHashMap<String, Double>()
        var shardsLeft = shards
        val nodes = currentResult.second.graph.nodes.filterIsInstance<RecipeNode>()
        val chosen = HashSet<String>()
        while (shardsLeft > 0) {
            var best: Triple<String, Double, Pair<Int, Double>>? = null // recipe, clock, (shards, buildings saved per shard)
            for (node in nodes) {
                val recipe = node.usage.recipe.className
                if (recipe in chosen) continue
                val before = node.machines.count
                for (perMachine in 1..3) {
                    val clock = 100.0 + 50.0 * perMachine
                    val (count, needed) = overclocked(node.usage.amount, clock)
                    if (needed == 0 || needed > shardsLeft) continue
                    val saved = before - count
                    if (saved <= 0) continue
                    val ratio = saved.toDouble() / needed
                    if (best == null || ratio > best.third.second) best = Triple(recipe, clock, needed to ratio)
                }
            }
            val pick = best ?: break
            clocks[pick.first] = pick.second
            chosen += pick.first
            shardsLeft -= pick.third.first
        }
        val finalRequest = current.copy(recipeClockSpeed = clocks)
        val finalResult = solve(data, finalRequest) ?: return null
        return BoostPlan(
            amplifiedRecipes = amplified,
            recipeClockSpeed = clocks,
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
