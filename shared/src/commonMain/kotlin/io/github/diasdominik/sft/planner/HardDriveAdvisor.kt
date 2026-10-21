package io.github.diasdominik.sft.planner

import io.github.diasdominik.sft.data.GameData

/** What a plan costs: raw resources, their weighted rarity, power, buildings, and what it makes. */
data class PlanStats(
    val rawPerMinute: Double,
    /** Raw resources weighted by rarity, the same measure the planner minimises. */
    val resourceScore: Double,
    val power: Double,
    val buildings: Int,
    val output: Double,
)

/** One production line before and after unlocking an alternate; null means no plan was possible. */
data class LineImpact(val line: String, val maximizes: Boolean, val before: PlanStats?, val after: PlanStats?) {
    val newlyPossible: Boolean get() = before == null && after != null
    val changed: Boolean get() = newlyPossible || (before != null && after != null && before != after)
}

data class AlternateImpact(val recipe: String, val lines: List<LineImpact>) {
    private val compared get() = lines.filter { it.before != null && it.after != null && !it.maximizes }
    val newlyPossible: Int get() = lines.count { it.newlyPossible }
    val linesImproved: Int get() = lines.count { it.changed }
    val rawSaved: Double get() = compared.sumOf { it.before!!.rawPerMinute - it.after!!.rawPerMinute }
    val scoreSaved: Double get() = compared.sumOf { it.before!!.resourceScore - it.after!!.resourceScore }
    val powerSaved: Double get() = compared.sumOf { it.before!!.power - it.after!!.power }
    val buildingsSaved: Int get() = compared.sumOf { it.before!!.buildings - it.after!!.buildings }
    /** Extra output of the lines that maximise, in items per minute. */
    val outputGained: Double get() = lines.filter { it.maximizes && it.before != null && it.after != null }
        .sumOf { it.after!!.output - it.before!!.output }
}

/**
 * Helps choose between the alternates a hard drive offers: each candidate is tried in every saved
 * production line (on top of what the line already allows) and ranked by what it saves there.
 */
object HardDriveAdvisor {
    fun stats(data: GameData, request: ProductionRequest): PlanStats? {
        val output = ProductionSolver(data).solve(request) ?: return null
        return stats(request, ProductionResult(request, ProductionGraph.from(output), data))
    }

    fun stats(request: ProductionRequest, result: ProductionResult): PlanStats {
        val raw = result.rawResources.values.sumOf { it.used }
        val score = result.rawResources.entries.sumOf { (item, r) -> r.used * request.weightOf(item) }
        return PlanStats(raw, score, result.power.total.average, result.buildings.amount, result.output.values.sum())
    }

    fun evaluate(data: GameData, lines: List<Pair<String, ProductionRequest>>, candidates: List<String>): List<AlternateImpact> {
        val before = lines.map { (_, request) -> stats(data, request) }
        return candidates.map { candidate ->
            val impacts = lines.mapIndexedNotNull { index, (name, request) ->
                if (candidate in request.allowedAlternateRecipes) return@mapIndexedNotNull null
                val after = stats(data, request.copy(allowedAlternateRecipes = request.allowedAlternateRecipes + candidate))
                LineImpact(name, request.production.any { it.type == ProductionType.MAXIMIZE }, before[index], after)
            }
            AlternateImpact(candidate, impacts)
        }.sortedWith(compareByDescending<AlternateImpact> { it.newlyPossible }.thenByDescending { it.scoreSaved }.thenByDescending { it.outputGained })
    }
}
