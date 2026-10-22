package io.github.diasdominik.sft.planner

import io.github.diasdominik.sft.data.GameData
import io.github.diasdominik.sft.data.Recipe

/** Works out why a production request has no solution, in words a player can act on. */
class ProductionDiagnosis(private val data: GameData) {
    private val solver = ProductionSolver(data)

    fun explain(request: ProductionRequest): List<String> {
        val products = request.production.mapNotNull { p -> p.item?.takeIf { data.item(it) != null } }.distinct()
        if (request.powerGoal?.isActive == true && request.power?.generators.isNullOrEmpty()) {
            return listOf("Spare power needs power plants in this line. Turn them on under Power plants.")
        }
        if (products.isEmpty() && request.activeGoals.isEmpty()) return listOf("Pick a product to make.")
        // Leftover limits are a common cause; check whether the plan works without them.
        if (request.byproductMode != ByproductMode.ALLOW && solver.solve(request.copy(byproductMode = ByproductMode.ALLOW)) != null) {
            val leftovers = solver.solve(request.copy(byproductMode = ByproductMode.ALLOW))!!.byproducts.keys
                .filterNot { request.byproductAllowed(it) }.map { name(it) }
            return listOf(
                "This needs leftovers that aren't allowed to leave the line" +
                    (if (leftovers.isEmpty()) "." else ": ${leftovers.joinToString()}.") +
                    " Allow them under Products, or enable recipes or power plants that use them up.",
            )
        }
        if (products.isEmpty()) {
            return listOf("No plan reaches the sink or power goal. Try raising resource limits or enabling more recipes.")
        }

        val allowed = solver.allowedRecipes(request)
        val available = availableSources(request)
        val reachable = reachable(allowed, available)

        val unreachable = products.filter { it !in reachable }
        if (unreachable.isNotEmpty()) {
            return unreachable.map { product -> explainUnreachable(product, request, allowed, reachable) }
        }
        return explainLimits(request).ifEmpty {
            listOf("Every product can be made, but no plan fits all the settings together. Try raising resource limits or enabling more recipes.")
        }
    }

    private fun availableSources(request: ProductionRequest): Set<String> {
        val result = HashSet<String>()
        for (resource in data.resources.keys) {
            if (resource !in request.blockedResources && Extraction.effectiveLimit(data, request, resource) > 0) result += resource
        }
        for (input in request.input) if (input.item != null && input.amount > 0) result += input.item
        return result
    }

    private fun reachable(recipes: List<Recipe>, sources: Set<String>) = reachableItems(recipes, sources)

    private fun explainUnreachable(product: String, request: ProductionRequest, allowed: List<Recipe>, reachable: Set<String>): String {
        fun producers(item: String) = allowed.filter { r -> r.products.any { it.item == item } }
        fun isRoot(item: String) = item in data.resources || producers(item).isEmpty()

        // Breadth-first through the ingredients that can't be made, to the nearest item that is
        // blocked by a setting or by the game itself.
        val parent = HashMap<String, String?>()
        parent[product] = null
        val queue = ArrayDeque(listOf(product))
        var root: String? = null
        while (queue.isNotEmpty()) {
            val item = queue.removeFirst()
            if (isRoot(item)) {
                root = item
                break
            }
            for (recipe in producers(item)) {
                for (ingredient in recipe.ingredients) {
                    if (ingredient.item !in reachable && ingredient.item !in parent) {
                        parent[ingredient.item] = item
                        queue += ingredient.item
                    }
                }
            }
        }
        val alternates = helpfulAlternates(product, request, allowed, reachable)
        val alternateHint = if (alternates.isEmpty()) "" else
            "an alternate recipe that isn't enabled, such as ${alternates.joinToString(" or ") { it.name }}"
        // An item the game itself can't make (or a recipe loop) is best fixed with an alternate recipe.
        val settingIsTheCause = root != null && (root in data.resources || data.recipes.values.any { r -> r.inMachine && r.products.any { it.item == root } })
        if (alternates.isNotEmpty() && !settingIsTheCause) {
            return "${name(product)} can't be made with the enabled recipes. It needs $alternateHint."
        }
        if (root == null) {
            return "${name(product)} can only be made in a loop that needs some of its own output to start. Add one of the loop's items under Input or enable another recipe."
        }
        val path = generateSequence(root) { parent[it] }.toList().reversed()
        val prefix = if (path.size > 1) "${name(product)} needs ${path.drop(1).joinToString(" → ") { name(it) }}. " else ""
        val suffix = if (alternates.isEmpty()) "" else " Or use $alternateHint."
        return prefix + rootCause(root, request, allowed) + suffix
    }

    /**
     * Disabled alternate recipes that would make [product] possible: first ones that do it on their
     * own, otherwise ones that make a missing ingredient once all alternates are enabled.
     */
    private fun helpfulAlternates(product: String, request: ProductionRequest, allowed: List<Recipe>, reachable: Set<String>): List<Recipe> {
        val withAll = solver.allowedRecipes(request.copy(allowedAlternateRecipes = data.alternateRecipes.map { it.className }))
        val candidates = withAll.filter { it.alternate && it !in allowed }
        if (candidates.isEmpty()) return emptyList()
        val sources = availableSources(request)
        val reachableWithAll = reachable(withAll, sources)
        if (product !in reachableWithAll) return emptyList()

        // Recipes whose main product is the wanted item read best, so they come first.
        fun mainFirst(recipes: List<Recipe>) = recipes.sortedBy { r -> if (r.products.firstOrNull()?.item == product) 0 else 1 }
        val single = candidates.filter { product in reachable(allowed + it, sources) }
        if (single.isNotEmpty()) return mainFirst(single).take(MAX_LISTED)

        // Items still missing on the way to the product, then alternates that make one of them.
        val needed = HashSet<String>()
        val queue = ArrayDeque(listOf(product))
        while (queue.isNotEmpty()) {
            val item = queue.removeFirst()
            if (!needed.add(item)) continue
            for (recipe in withAll) {
                if (recipe.products.none { it.item == item }) continue
                for (ingredient in recipe.ingredients) if (ingredient.item !in reachable) queue += ingredient.item
            }
        }
        return candidates
            .filter { r -> r.products.any { it.item in needed } && r.ingredients.all { it.item in reachableWithAll } }
            .let(::mainFirst)
            .take(MAX_LISTED)
    }

    private fun rootCause(item: String, request: ProductionRequest, allowed: List<Recipe>): String {
        val itemName = name(item)
        if (item in data.resources) {
            return when {
                item in request.blockedResources -> "$itemName is turned off in Raw resources."
                (Extraction.settings(data, request, item)?.nodes ?: -1) == 0 -> "$itemName is set to 0 nodes in Raw resources."
                else -> "$itemName has a limit of 0 in Raw resources."
            }
        }
        val all = data.recipes.values.filter { r -> r.inMachine && r.products.any { it.item == item } }
        if (all.isEmpty()) {
            val source = WORLD_SOURCES[item] ?: "it isn't made in any production building"
            return "No recipe makes $itemName: $source. Add it under Input if you have a supply."
        }
        val blockedMachines = request.blockedMachines.toSet()
        val reasons = all.map { r ->
            val machine = data.machineFor(r)
            when {
                machine != null && (machine.className in blockedMachines || r.producedIn.any { it in blockedMachines }) ->
                    "${r.name} needs the ${machine.name}, which is turned off in Buildings"
                r.alternate -> "${r.name} is an alternate recipe that isn't enabled"
                else -> "${r.name} is turned off in Recipes"
            }
        }
        val shown = reasons.take(3).joinToString("; ")
        val more = if (reasons.size > 3) " (and ${reasons.size - 3} more)" else ""
        return "Every recipe for $itemName is unavailable: $shown$more."
    }

    /** All products can be made, so compare what an unlimited plan would need with the limits. */
    private fun explainLimits(request: ProductionRequest): List<String> {
        val maximized = request.production.filter { it.item != null && it.type == ProductionType.MAXIMIZE }
        if (maximized.isNotEmpty()) {
            // If a small fixed amount works, maximizing failed because nothing limits it.
            val fixed = request.copy(production = request.production.map { if (it.type == ProductionType.MAXIMIZE) it.copy(type = ProductionType.PER_MINUTE, amount = 0.01) else it })
            if (solver.solve(fixed) != null) {
                val names = maximized.mapNotNull { it.item }.distinct().joinToString { name(it) }
                return listOf("$names can be made without using up any limited resource, so maximizing has no upper bound. Set an amount per minute instead.")
            }
            val zero = data.resources.keys.filter { it !in request.blockedResources && Extraction.effectiveLimit(data, request, it) <= 0 }
            return if (zero.isEmpty()) emptyList() else listOf(
                "Nothing is left to maximize with: ${zero.joinToString { name(it) }} ${if (zero.size == 1) "is" else "are"} limited to 0.",
            )
        }
        val relaxed = request.copy(
            resourceMax = data.resources.keys.associateWith { UNLIMITED },
            extraction = request.extraction.mapValues { it.value.copy(nodes = null) },
        )
        val output = solver.solve(relaxed) ?: return emptyList()
        return output.mined.mapNotNull { (resource, needed) ->
            val limit = Extraction.effectiveLimit(data, request, resource)
            if (needed <= limit + 1e-6) return@mapNotNull null
            val settings = Extraction.settings(data, request, resource)
            val unit = if (data.item(resource)?.liquid == true) "m³/min" else "/min"
            val source = if (settings?.nodes != null) {
                val extractor = data.building(settings.extractor)?.name ?: "extractor"
                val purity = if (Extraction.hasPurity(settings.extractor!!)) " ${settings.purity.label.lowercase()}" else ""
                "${settings.nodes}$purity node${if (settings.nodes == 1) "" else "s"} with $extractor at ${Numbers.format(settings.clockSpeed, 2)} %"
            } else {
                "the limit in Raw resources"
            }
            "Needs ${Numbers.format(needed, 2)} $unit ${name(resource)}, but only ${Numbers.format(limit, 2)} $unit is available ($source)."
        }
    }

    private fun name(item: String) = data.nameOf(item)

    private companion object {
        const val UNLIMITED = 1e15
        const val MAX_LISTED = 3
        val WORLD_SOURCES = mapOf(
            "Desc_Leaves_C" to "it is gathered by hand",
            "Desc_Wood_C" to "it is gathered by hand",
            "Desc_Mycelia_C" to "it is gathered by hand",
            "Desc_NuclearWaste_C" to "it comes out of Nuclear Power Plants burning uranium fuel rods",
            "Desc_PlutoniumWaste_C" to "it comes out of Nuclear Power Plants burning plutonium fuel rods",
            "Desc_HogParts_C" to "it is dropped by creatures",
            "Desc_SpitterParts_C" to "it is dropped by creatures",
            "Desc_StingerParts_C" to "it is dropped by creatures",
            "Desc_HatcherParts_C" to "it is dropped by creatures",
            "Desc_Crystal_C" to "it is collected from Power Slugs found in the world",
            "Desc_Crystal_mk2_C" to "it is collected from Power Slugs found in the world",
            "Desc_Crystal_mk3_C" to "it is collected from Power Slugs found in the world",
        )
    }
}
