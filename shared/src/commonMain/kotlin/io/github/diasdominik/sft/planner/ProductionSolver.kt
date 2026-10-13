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

/** [amount] is the number of machines running at 100 % clock speed (may be fractional). */
data class RecipeUsage(val recipe: Recipe, val machine: Building, val amount: Double, val clockSpeed: Int = 100)

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
 */
class ProductionSolver(private val data: GameData) {

    fun solve(request: ProductionRequest): SolverOutput? {
        val products = request.production.filter { it.item != null && data.item(it.item) != null }
        val perMinute = products.filter { it.type != ProductionType.MAXIMIZE && it.amount > 0 }
        val maximize = products.filter { it.type == ProductionType.MAXIMIZE && it.ratio > 0 }
        if (perMinute.isEmpty() && maximize.isEmpty()) return null

        val recipes = allowedRecipes(request)

        // Variable layout
        var next = 0
        val recipeVar = recipes.associateWith { next++ }
        val resourceVar = LinkedHashMap<String, Int>()
        for (resource in data.resources.keys) {
            if (resource in request.blockedResources) continue
            val max = request.resourceMax[resource] ?: 0.0
            if (max <= 0) continue
            resourceVar[resource] = next++
        }
        val inputAmounts = LinkedHashMap<String, Double>()
        for (input in request.input) {
            val item = input.item ?: continue
            if (input.amount <= 0 || data.item(item) == null) continue
            inputAmounts[item] = (inputAmounts[item] ?: 0.0) + input.amount
        }
        val inputVar = inputAmounts.keys.associateWith { next++ }

        val itemCoefficients = LinkedHashMap<String, MutableMap<Int, Double>>()
        fun coef(item: String) = itemCoefficients.getOrPut(item) { HashMap() }

        for ((recipe, variable) in recipeVar) {
            val machine = data.machineFor(recipe)!!
            val perMinuteFactor = 60.0 / recipe.time * machine.metadata.manufacturingSpeed
            for (p in recipe.products) coef(p.item).merge(variable, p.amount * perMinuteFactor, Double::plus)
            for (i in recipe.ingredients) coef(i.item).merge(variable, -i.amount * perMinuteFactor, Double::plus)
        }
        for ((resource, variable) in resourceVar) coef(resource)[variable] = 1.0
        for ((item, variable) in inputVar) coef(item)[variable] = 1.0

        val demand = HashMap<String, Double>()
        for (p in perMinute) demand.merge(p.item!!, p.amount, Double::plus)
        val maxRatio = HashMap<String, Double>()
        for (p in maximize) maxRatio.merge(p.item!!, p.ratio / 100.0, Double::plus)
        for (item in demand.keys + maxRatio.keys) coef(item)

        val byproductVar = itemCoefficients.keys.associateWith { next++ }
        val scaleVar = if (maxRatio.isNotEmpty()) next++ else -1
        val variableCount = next

        fun buildProgram(): LinearProgram {
            val lp = LinearProgram(variableCount)
            for ((resource, variable) in resourceVar) {
                val max = request.resourceMax[resource] ?: 0.0
                if (max < 1e12) lp.upperBounds[variable] = max
            }
            for ((item, variable) in inputVar) lp.upperBounds[variable] = inputAmounts.getValue(item)
            for ((item, coefficients) in itemCoefficients) {
                val row = HashMap(coefficients)
                row[byproductVar.getValue(item)] = -1.0
                maxRatio[item]?.let { row[scaleVar] = -it }
                lp.addConstraint(row, LinearProgram.Relation.EQ, demand[item] ?: 0.0)
            }
            return lp
        }

        fun applyResourceObjective(lp: LinearProgram) {
            for ((resource, variable) in resourceVar) {
                lp.objective[variable] = (request.resourceWeight[resource] ?: 1.0) + TIE_BREAK
            }
            for ((recipe, variable) in recipeVar) lp.objective[variable] = TIE_BREAK * (1 + recipe.ingredients.size)
            for ((_, variable) in byproductVar) lp.objective[variable] = TIE_BREAK
        }

        var scale = 0.0
        if (scaleVar >= 0) {
            val lp = buildProgram()
            lp.objective[scaleVar] = -1.0
            when (val result = Simplex.solve(lp)) {
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
        val solution = when (val result = Simplex.solve(lp)) {
            is LpResult.Optimal -> result.values
            else -> return null
        }

        fun clean(v: Double) = if (v < 1e-7) 0.0 else v

        val usages = recipeVar.mapNotNull { (recipe, variable) ->
            val amount = clean(solution[variable])
            if (amount > 0) RecipeUsage(recipe, data.machineFor(recipe)!!, amount) else null
        }
        val mined = resourceVar.mapValues { clean(solution[it.value]) }.filterValues { it > 0 }
        val inputs = inputVar.mapValues { clean(solution[it.value]) }.filterValues { it > 0 }
        val productAmounts = LinkedHashMap<String, Double>()
        for ((item, amount) in demand) productAmounts[item] = amount
        for ((item, ratio) in maxRatio) productAmounts.merge(item, ratio * scale, Double::plus)
        val byproducts = byproductVar.mapValues { clean(solution[it.value]) }.filterValues { it > 1e-6 }

        return SolverOutput(usages, mined, inputs, productAmounts, byproducts)
    }

    /** Applies blocked machines and recipe toggles the same way upstream ProductionTab.calculate does. */
    fun allowedRecipes(request: ProductionRequest): List<Recipe> {
        val blockedMachines = request.blockedMachines.toSet()
        val blockedRecipes = request.blockedRecipes.toSet()
        val allowedAlternates = request.allowedAlternateRecipes.toSet()
        return data.recipes.values.filter { recipe ->
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
