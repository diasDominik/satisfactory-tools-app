package io.github.diasdominik.sft.planner

/** A step of the plan compared with what was built: buildings to add (positive) or remove (negative). */
data class BuildChange(
    val key: String,
    val name: String,
    val machine: String,
    val builtCount: Int,
    val plannedCount: Int,
    val builtClock: Double,
    val plannedClock: Double,
    val builtSomersloops: Int,
    val plannedSomersloops: Int,
) {
    val delta: Int get() = plannedCount - builtCount
    val isNew: Boolean get() = builtCount == 0
    val isRemoved: Boolean get() = plannedCount == 0
    val clockChanged: Boolean get() = !isNew && !isRemoved && kotlin.math.abs(builtClock - plannedClock) > 1e-6
    val somersloopsChanged: Boolean get() = builtSomersloops != plannedSomersloops
}

/** Compares the current plan with the one marked as built, so an upgrade shows exactly what to change. */
object BuildDiff {
    /** What the current plan builds, by graph node key: recipes and extractors. */
    fun snapshot(result: ProductionResult): Map<String, BuiltStep> {
        val steps = LinkedHashMap<String, BuiltStep>()
        for (node in result.graph.nodes.filterIsInstance<RecipeNode>()) {
            steps[graphNodeKey(node)] = BuiltStep(node.usage.recipe.name, node.usage.machine.className, node.machines.count, node.usage.clockSpeed, node.machines.somersloops)
        }
        for (e in result.extraction) {
            steps["mine:" + e.resource] = BuiltStep(e.resource, e.extractor.className, e.count, e.settings.clockSpeed)
        }
        return steps
    }

    /** Every step whose buildings, clock speed or Somersloops differ; unchanged steps are left out. */
    fun changes(built: Map<String, BuiltStep>, result: ProductionResult): List<BuildChange> {
        val planned = snapshot(result)
        return (built.keys + planned.keys).distinct().mapNotNull { key ->
            val b = built[key]
            val p = planned[key]
            val change = BuildChange(
                key = key,
                name = (p ?: b)!!.name,
                machine = (p ?: b)!!.machine,
                builtCount = b?.count ?: 0,
                plannedCount = p?.count ?: 0,
                builtClock = b?.clockSpeed ?: 100.0,
                plannedClock = p?.clockSpeed ?: 100.0,
                builtSomersloops = b?.somersloops ?: 0,
                plannedSomersloops = p?.somersloops ?: 0,
            )
            change.takeIf { it.delta != 0 || it.clockChanged || it.somersloopsChanged || (b != null && p != null && b.machine != p.machine) }
        }.sortedWith(compareBy({ !it.isRemoved }, { !it.isNew }, { it.name }))
    }
}
