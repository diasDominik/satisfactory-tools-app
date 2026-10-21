package io.github.diasdominik.sft.planner

import io.github.diasdominik.sft.data.Building
import io.github.diasdominik.sft.data.GameData
import io.github.diasdominik.sft.data.Recipe

/** Raw solver output, equivalent to the upstream API response. */
data class SolverOutput(
    val recipes: List<RecipeUsage>,
    val mined: Map<String, Double>,
    val inputs: Map<String, Double>,
    val products: Map<String, Double>,
    val byproducts: Map<String, Double>,
)

/**
 * [amount] is the throughput in machines running at 100 % (may be fractional); [clockSpeed] is what
 * the machines actually run at, so the real machine count is amount × 100 / clockSpeed.
 */
data class RecipeUsage(
    val recipe: Recipe,
    val machine: Building,
    val amount: Double,
    val clockSpeed: Double = 100.0,
    /** Every Somersloop slot filled: twice the output for the same input, at four times the power. */
    val amplified: Boolean = false,
) {
    val outputFactor: Double get() = if (amplified) Somersloop.OUTPUT_FACTOR else 1.0
}

/**
 * Replaces the upstream server-side solver (api.satisfactorytools.com/v2/solver) with an
 * on-device linear program:
 *
 *  - every allowed recipe is a variable (machines at 100 %),
 *  - every enabled raw resource can be mined up to its limit,
 *  - inputs can be used up to their given amount,
 *  - surplus of any item leaves the factory as a byproduct,
 *  - "items per minute" products are fixed demands, "maximize" products share one scale factor.
 *
 * The objective minimises the weighted raw resource use (the same weights upstream uses), with a
 * tiny tie-breaker on machine count. When something is maximised, the scale factor is maximised
 * first and the resource use minimised second.
 *
 * With a Somersloop budget, every recipe that runs in a building with slots also gets an amplified
 * variant (twice the output for the same input) and a whole number of amplified buildings, each using
 * all its slots, within the budget. That makes it an integer program, solved by [BranchAndBound].
 */
class ProductionSolver(private val data: GameData) {

    fun solve(request: ProductionRequest): SolverOutput? {
        val products = request.production.filter { it.item != null && data.item(it.item) != null }
        val perMinute = products.filter { it.type != ProductionType.MAXIMIZE && it.amount > 0 }
        val maximize = products.filter { it.type == ProductionType.MAXIMIZE && it.ratio > 0 }
        if (perMinute.isEmpty() && maximize.isEmpty()) return null

        val resources = data.resources.keys.filter { resource ->
            resource !in request.blockedResources && Extraction.effectiveLimit(data, request, resource) > 0
        }
        val inputAmounts = LinkedHashMap<String, Double>()
        for (input in request.input) {
            val item = input.item ?: continue
            if (input.amount <= 0 || data.item(item) == null) continue
            inputAmounts[item] = (inputAmounts[item] ?: 0.0) + input.amount
        }
        // Recipes whose ingredients can't be made never run. Leaving them out keeps the program
        // small and avoids loops that would need some of their own output to start.
        val reachable = reachableItems(allowedRecipes(request), resources + inputAmounts.keys)
        val recipes = allowedRecipes(request).filter { r -> r.ingredients.all { it.item in reachable } }

        // Variable layout
        var next = 0
        val recipeVar = recipes.associateWith { next++ }
        val resourceVar = LinkedHashMap<String, Int>()
        for (resource in resources) resourceVar[resource] = next++
        val inputVar = inputAmounts.keys.associateWith { next++ }
        val budget = request.somersloopBudget?.takeIf { it > 0 }
        val sloopRecipes = if (budget == null) emptyList() else recipes.filter { Somersloop.slots(data.machineFor(it)!!.className) > 0 }
        val amplifiedVar = sloopRecipes.associateWith { next++ }
        val amplifiedCountVar = sloopRecipes.associateWith { next++ }

        val itemCoefficients = LinkedHashMap<String, MutableMap<Int, Double>>()
        fun coef(item: String) = itemCoefficients.getOrPut(item) { HashMap() }

        for ((recipe, variable) in recipeVar) {
            val machine = data.machineFor(recipe)!!
            val perMinuteFactor = 60.0 / recipe.time * machine.metadata.manufacturingSpeed
            val boost = if (request.isAmplified(recipe.className, machine.className)) Somersloop.OUTPUT_FACTOR else 1.0
            for (p in recipe.products) coef(p.item).mergeWith(variable, p.amount * perMinuteFactor * boost) { a, b -> a + b }
            for (i in recipe.ingredients) coef(i.item).mergeWith(variable, -i.amount * perMinuteFactor) { a, b -> a + b }
            amplifiedVar[recipe]?.let { amplified ->
                for (p in recipe.products) coef(p.item).mergeWith(amplified, p.amount * perMinuteFactor * Somersloop.OUTPUT_FACTOR) { a, b -> a + b }
                for (i in recipe.ingredients) coef(i.item).mergeWith(amplified, -i.amount * perMinuteFactor) { a, b -> a + b }
            }
        }
        for ((resource, variable) in resourceVar) coef(resource)[variable] = 1.0
        for ((item, variable) in inputVar) coef(item)[variable] = 1.0

        val demand = HashMap<String, Double>()
        for (p in perMinute) demand.mergeWith(p.item!!, p.amount) { a, b -> a + b }
        val maxRatio = HashMap<String, Double>()
        for (p in maximize) maxRatio.mergeWith(p.item!!, p.ratio / 100.0) { a, b -> a + b }
        for (item in demand.keys + maxRatio.keys) coef(item)

        val byproductVar = itemCoefficients.keys.associateWith { next++ }
        val scaleVar = if (maxRatio.isNotEmpty()) next++ else -1
        val variableCount = next

        fun buildProgram(): LinearProgram {
            val lp = LinearProgram(variableCount)
            for ((resource, variable) in resourceVar) {
                val max = Extraction.effectiveLimit(data, request, resource)
                if (max < 1e12) lp.upperBounds[variable] = max
            }
            for ((item, variable) in inputVar) lp.upperBounds[variable] = inputAmounts.getValue(item)
            for ((item, coefficients) in itemCoefficients) {
                val row = HashMap(coefficients)
                row[byproductVar.getValue(item)] = -1.0
                maxRatio[item]?.let { row[scaleVar] = -it }
                lp.addConstraint(row, LinearProgram.Relation.EQ, demand[item] ?: 0.0)
            }
            if (budget != null) {
                for ((recipe, amplified) in amplifiedVar) {
                    val machine = data.machineFor(recipe)!!.className
                    // Amplified throughput fits in the amplified buildings at their clock speed.
                    val perBuilding = request.clockSpeedFor(machine, recipe.className) / 100.0
                    lp.addConstraint(mapOf(amplified to 1.0, amplifiedCountVar.getValue(recipe) to -perBuilding), LinearProgram.Relation.LE, 0.0)
                }
                lp.addConstraint(
                    amplifiedCountVar.map { (recipe, v) -> v to Somersloop.slots(data.machineFor(recipe)!!.className).toDouble() }.toMap(),
                    LinearProgram.Relation.LE,
                    budget.toDouble(),
                )
            }
            return lp
        }

        fun applyResourceObjective(lp: LinearProgram) {
            for ((resource, variable) in resourceVar) {
                lp.objective[variable] = (request.resourceWeight[resource] ?: 1.0) + TIE_BREAK
            }
            for ((recipe, variable) in recipeVar) lp.objective[variable] = TIE_BREAK * (1 + recipe.ingredients.size)
            for ((recipe, variable) in amplifiedVar) lp.objective[variable] = TIE_BREAK * (1 + recipe.ingredients.size)
            // Somersloops that gain nothing stay in the inventory.
            for ((recipe, variable) in amplifiedCountVar) lp.objective[variable] = TIE_BREAK * Somersloop.slots(data.machineFor(recipe)!!.className)
            for ((_, variable) in byproductVar) lp.objective[variable] = TIE_BREAK
        }

        val integers = amplifiedCountVar.values.toList()
        var scale = 0.0
        if (scaleVar >= 0) {
            val lp = buildProgram()
            lp.objective[scaleVar] = -1.0
            when (val result = BranchAndBound.solve(lp, integers)) {
                is LpResult.Optimal -> scale = result.values[scaleVar]
                else -> return null
            }
            if (scale <= 1e-9) return null
        }

        val lp = buildProgram()
        if (scaleVar >= 0) {
            lp.upperBounds[scaleVar] = scale
            lp.addConstraint(mapOf(scaleVar to 1.0), LinearProgram.Relation.GE, scale * (1 - 1e-9))
        }
        applyResourceObjective(lp)
        val solution = when (val result = BranchAndBound.solve(lp, integers)) {
            is LpResult.Optimal -> result.values
            else -> return null
        }

        fun clean(v: Double) = if (v < 1e-7) 0.0 else v

        val usages = recipeVar.flatMap { (recipe, variable) ->
            val machine = data.machineFor(recipe)!!
            val clock = request.clockSpeedFor(machine.className, recipe.className)
            listOfNotNull(
                clean(solution[variable]).takeIf { it > 0 }?.let {
                    RecipeUsage(recipe, machine, it, clock, request.isAmplified(recipe.className, machine.className))
                },
                amplifiedVar[recipe]?.let { clean(solution[it]) }?.takeIf { it > 0 }?.let {
                    RecipeUsage(recipe, machine, it, clock, amplified = true)
                },
            )
        }
        val mined = resourceVar.mapValues { clean(solution[it.value]) }.filterValues { it > 0 }
        val inputs = inputVar.mapValues { clean(solution[it.value]) }.filterValues { it > 0 }
        val productAmounts = LinkedHashMap<String, Double>()
        for ((item, amount) in demand) productAmounts[item] = amount
        val finalScale = if (scaleVar >= 0) solution[scaleVar] else 0.0
        for ((item, ratio) in maxRatio) productAmounts.mergeWith(item, ratio * finalScale) { a, b -> a + b }
        val byproducts = byproductVar.mapValues { clean(solution[it.value]) }.filterValues { it > 1e-6 }

        return SolverOutput(usages, mined, inputs, productAmounts, byproducts)
    }

    /** Applies blocked machines and recipe toggles the same way upstream ProductionTab.calculate does. */
    fun allowedRecipes(request: ProductionRequest): List<Recipe> {
        val blockedMachines = request.blockedMachines.toSet()
        val blockedRecipes = request.blockedRecipes.toSet()
        val allowedAlternates = request.allowedAlternateRecipes.toSet()
        // A resource with a node count comes only from those nodes, not from Converter recipes that
        // turn other resources into it; otherwise the plan quietly works around the nodes you have.
        val fromNodesOnly = request.extraction.filterValues { it.nodes != null }.keys
        return data.recipes.values.filter { recipe ->
            if (recipe.products.any { it.item in fromNodesOnly }) return@filter false
            if (!recipe.inMachine) return@filter false
            val machine = data.machineFor(recipe) ?: return@filter false
            if (recipe.time <= 0) return@filter false
            if (recipe.producedIn.any { it in blockedMachines } || machine.className in blockedMachines) return@filter false
            if (recipe.alternate) recipe.className in allowedAlternates else recipe.className !in blockedRecipes
        }
    }

    private companion object {
        const val TIE_BREAK = 1e-5
    }
}

/** Items that can be made at all, ignoring amounts: a recipe works once all its ingredients can. */
internal fun reachableItems(recipes: List<Recipe>, sources: Collection<String>): Set<String> {
    val result = HashSet(sources)
    var changed = true
    while (changed) {
        changed = false
        for (recipe in recipes) {
            if (recipe.ingredients.all { it.item in result }) {
                for (p in recipe.products) if (result.add(p.item)) changed = true
            }
        }
    }
    return result
}
