package io.github.diasdominik.sft.planner

/** Totals across the production lines of one folder (project). */
data class ProjectTotals(
    val lines: Int,
    val buildings: Map<String, Int>,
    val powerUsed: Double,
    val powerMade: Double,
    /** Raw resources mined, per minute. */
    val raw: Map<String, Double>,
    val products: Map<String, Double>,
    val leftovers: Map<String, Double>,
    val sinkPoints: Double,
    /** Raw resources the lines together mine more of than [limits] allow: used to limit. */
    val overLimit: Map<String, Pair<Double, Double>>,
) {
    val buildingCount: Int get() = buildings.values.sum()

    companion object {
        fun of(results: List<ProductionResult>, limits: Map<String, Double>): ProjectTotals {
            val buildings = LinkedHashMap<String, Int>()
            val raw = LinkedHashMap<String, Double>()
            val products = LinkedHashMap<String, Double>()
            val leftovers = LinkedHashMap<String, Double>()
            for (r in results) {
                r.buildings.buildings.forEach { (b, d) -> buildings[b] = (buildings[b] ?: 0) + d.amount }
                r.rawResources.forEach { (item, d) -> if (d.used > 0) raw[item] = (raw[item] ?: 0.0) + d.used }
                r.output.forEach { (item, a) -> products[item] = (products[item] ?: 0.0) + a }
                r.byproducts.forEach { (item, a) -> leftovers[item] = (leftovers[item] ?: 0.0) + a }
            }
            val over = raw.mapNotNull { (item, used) ->
                val limit = limits[item] ?: return@mapNotNull null
                if (used > limit + 1e-6) item to (Numbers.round(used) to limit) else null
            }.toMap()
            return ProjectTotals(
                lines = results.size,
                buildings = buildings,
                powerUsed = Numbers.round(results.sumOf { it.power.total.average }),
                powerMade = Numbers.round(results.sumOf { it.generatedPower }),
                raw = raw.mapValues { Numbers.round(it.value) },
                products = products.mapValues { Numbers.round(it.value) },
                leftovers = leftovers.mapValues { Numbers.round(it.value) },
                sinkPoints = Numbers.round(results.sumOf { it.sinkPoints }),
                overLimit = over,
            )
        }
    }
}
