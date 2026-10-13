package io.github.diasdominik.sft

import io.github.diasdominik.sft.data.DataLoader
import io.github.diasdominik.sft.data.GameVersion
import io.github.diasdominik.sft.data.Item
import io.github.diasdominik.sft.data.Purity
import io.github.diasdominik.sft.planner.Extraction
import io.github.diasdominik.sft.planner.ProductionDiagnosis
import io.github.diasdominik.sft.planner.ProductionGraph
import io.github.diasdominik.sft.planner.ProductionItem
import io.github.diasdominik.sft.planner.ProductionRequest
import io.github.diasdominik.sft.planner.ProductionResult
import io.github.diasdominik.sft.planner.ProductionSolver
import io.github.diasdominik.sft.planner.ProductionType
import io.github.diasdominik.sft.planner.ResourceExtraction
import io.github.diasdominik.sft.planner.SolverOutput
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs the planner for every item that can be made in a machine, under several settings, and checks
 * that each plan balances and that every request without a plan gets a concrete reason.
 */
class PlannerCoverageTest {
    private val data = DataLoader.parse(GameVersion.V1_0, File("src/commonMain/composeResources/files/data1.0.json").readText())
    private val solver = ProductionSolver(data)
    private val diagnosis = ProductionDiagnosis(data)
    private val allAlternates = data.alternateRecipes.map { it.className }
    private val items = data.automatableItems

    private fun request(item: Item, amount: Double = 10.0, alternates: Boolean = false) = ProductionRequest(
        production = listOf(ProductionItem(item.className, amount = amount)),
        allowedAlternateRecipes = if (alternates) allAlternates else emptyList(),
    )

    /** Runs [check] for every item and reports all failing items at once. */
    private fun forEveryItem(check: (Item) -> String?) {
        val failures = items.mapNotNull { item -> check(item)?.let { "${item.name}: $it" } }
        if (failures.isNotEmpty()) fail("${failures.size} of ${items.size} items failed:\n" + failures.joinToString("\n"))
    }

    /** Net flow of every item in a plan; a valid plan has zero everywhere. */
    private fun imbalances(out: SolverOutput): Map<String, Double> {
        val net = HashMap<String, Double>()
        fun add(item: String, v: Double) { net[item] = (net[item] ?: 0.0) + v }
        for (u in out.recipes) {
            val perMachine = 60.0 / u.recipe.time * u.machine.metadata.manufacturingSpeed
            u.recipe.products.forEach { add(it.item, it.amount * perMachine * u.amount) }
            u.recipe.ingredients.forEach { add(it.item, -it.amount * perMachine * u.amount) }
        }
        out.mined.forEach { (k, v) -> add(k, v) }
        out.inputs.forEach { (k, v) -> add(k, v) }
        out.products.forEach { (k, v) -> add(k, -v) }
        out.byproducts.forEach { (k, v) -> add(k, -v) }
        return net.filterValues { abs(it) > 1e-4 }
    }

    private fun checkPlan(item: Item, request: ProductionRequest, amount: Double): String? {
        val out = solver.solve(request) ?: return "no plan"
        val produced = out.products[item.className] ?: return "product missing from the plan"
        if (abs(produced - amount) > 1e-6) return "produces $produced instead of $amount"
        val bad = imbalances(out)
        if (bad.isNotEmpty()) return "unbalanced: $bad"
        if (out.recipes.any { it.amount < 0 } || out.mined.values.any { it < 0 }) return "negative amounts"
        for ((resource, mined) in out.mined) {
            val limit = Extraction.effectiveLimit(data, request, resource)
            if (mined > limit + 1e-6) return "mines $mined of $resource over the limit $limit"
        }
        return null
    }

    private fun checkExplained(request: ProductionRequest, vararg mustMention: String): String? {
        val reasons = diagnosis.explain(request)
        if (reasons.isEmpty()) return "no reason given"
        if (reasons.any { it.startsWith("Every product can be made, but no plan") }) return "only the generic reason: $reasons"
        val text = reasons.joinToString(" ")
        val missing = mustMention.filter { it !in text }
        return if (missing.isEmpty()) null else "reason $reasons doesn't mention $missing"
    }

    @Test
    fun everyItemSolvesWithBaseRecipesOrNamesTheAlternate() = forEveryItem { item ->
        if (item.name in NEEDS_OUTSIDE_SUPPLY) return@forEveryItem null
        val request = request(item)
        if (solver.solve(request) == null) checkExplained(request, "alternate recipe that isn't enabled") else checkPlan(item, request, 10.0)
    }

    @Test
    fun everyItemSolvesWithAllAlternates() = forEveryItem { item ->
        if (item.name in NEEDS_OUTSIDE_SUPPLY) return@forEveryItem null
        checkPlan(item, request(item, alternates = true), 10.0)
    }

    @Test
    fun everyItemSolvesInSmallAndLargeAmounts() = forEveryItem { item ->
        if (item.name in NEEDS_OUTSIDE_SUPPLY) return@forEveryItem null
        checkPlan(item, request(item, amount = 0.1, alternates = true), 0.1)?.let { return@forEveryItem "0.1/min: $it" }
        val large = request(item, amount = 2000.0, alternates = true)
        // Large amounts may exceed the map's resources; then the reason must name the shortfall.
        if (solver.solve(large) == null) checkExplained(large, "but only")?.let { "2000/min: $it" }
        else checkPlan(item, large, 2000.0)?.let { "2000/min: $it" }
    }

    @Test
    fun everyItemCanBeMaximized() = forEveryItem { item ->
        if (item.name in NEEDS_OUTSIDE_SUPPLY) return@forEveryItem null
        val request = ProductionRequest(
            production = listOf(ProductionItem(item.className, type = ProductionType.MAXIMIZE)),
            allowedAlternateRecipes = allAlternates,
        )
        val out = solver.solve(request) ?: return@forEveryItem checkExplained(request, "no upper bound")
        val produced = out.products[item.className] ?: 0.0
        when {
            produced <= 0 -> "maximized to $produced"
            imbalances(out).isNotEmpty() -> "unbalanced: ${imbalances(out)}"
            else -> null
        }
    }

    @Test
    fun everyResultBuildsItsDetails() = forEveryItem { item ->
        if (item.name in NEEDS_OUTSIDE_SUPPLY) return@forEveryItem null
        val request = request(item, alternates = true).copy(
            extraction = data.resources.keys.associateWith { ResourceExtraction(purity = Purity.PURE) },
        )
        val out = solver.solve(request) ?: return@forEveryItem "no plan"
        val result = ProductionResult(request, ProductionGraph.from(out), data)
        val minedResources = out.mined.keys
        when {
            abs((result.output[item.className] ?: 0.0) - 10.0) > 1e-6 -> "output ${result.output[item.className]}"
            result.buildings.amount <= 0 && out.recipes.isNotEmpty() -> "no buildings"
            result.extraction.map { it.resource }.toSet() != minedResources -> "extractors ${result.extraction.map { it.resource }} for $minedResources"
            result.extraction.any { e -> e.count * e.ratePerExtractor + 1e-6 < e.amount } -> "too few extractors"
            result.power.total.average < 0 -> "negative power"
            else -> null
        }
    }

    @Test
    fun itemsThatNeedOutsideSupplyExplainWhy() {
        val failing = items.filter { solver.solve(request(it, alternates = true)) == null }.map { it.name }.toSet()
        assertEquals(NEEDS_OUTSIDE_SUPPLY, failing)
        forEveryItem { item ->
            if (item.name !in NEEDS_OUTSIDE_SUPPLY) return@forEveryItem null
            checkExplained(request(item, alternates = true), "Add it under Input")
        }
    }

    @Test
    fun blockingAllResourcesNamesAResource() = forEveryItem { item ->
        if (item.name in NEEDS_OUTSIDE_SUPPLY) return@forEveryItem null
        val request = request(item).copy(blockedResources = data.resources.keys.toList())
        // A few items (Excited Photonic Matter) are made from nothing, which is fine without resources.
        solver.solve(request)?.let { out -> return@forEveryItem if (out.mined.isEmpty()) null else "mines ${out.mined} while all resources are off" }
        // The cause is a turned off resource, or an item no machine makes on the way there.
        val reasons = diagnosis.explain(request).joinToString(" ")
        val expected = listOf("turned off in Raw resources", "Add it under Input", "alternate recipe that isn't enabled")
        if (expected.any { it in reasons }) null else "reason doesn't name the cause: $reasons"
    }

    @Test
    fun blockingTheMachineNamesTheMachine() {
        val request = ProductionRequest(
            production = listOf(ProductionItem("Desc_IronRod_C", amount = 10.0)),
            blockedMachines = listOf("Desc_ConstructorMk1_C"),
        )
        val reason = diagnosis.explain(request).single()
        assertTrue("Iron Rod" in reason && "Constructor" in reason && "turned off in Buildings" in reason, reason)
    }

    @Test
    fun blockingTheOnlyRecipeNamesTheRecipe() {
        val request = ProductionRequest(
            production = listOf(ProductionItem("Desc_IronPlate_C", amount = 10.0)),
            blockedRecipes = listOf("Recipe_IronPlate_C"),
        )
        val reason = diagnosis.explain(request).single()
        assertTrue("Iron Plate" in reason && "turned off in Recipes" in reason, reason)
    }

    @Test
    fun nuclearWasteNamesThePowerPlant() {
        val reason = diagnosis.explain(ProductionRequest(production = listOf(ProductionItem("Desc_PlutoniumPellet_C", amount = 1.0)))).single()
        assertTrue("Uranium Waste" in reason && "Nuclear Power Plants" in reason, reason)
    }

    @Test
    fun wasteAsInputMakesPlutoniumPossible() {
        val request = ProductionRequest(
            production = listOf(ProductionItem("Desc_PlutoniumPellet_C", amount = 1.0)),
            input = listOf(io.github.diasdominik.sft.planner.InputItem("Desc_NuclearWaste_C", 100.0)),
        )
        assertTrue(solver.solve(request) != null)
    }

    @Test
    fun turnedOffResourceIsNamed() {
        val request = ProductionRequest(
            production = listOf(ProductionItem("Desc_IronPlate_C", amount = 10.0)),
            blockedResources = listOf("Desc_OreIron_C"),
            blockedMachines = listOf("Desc_Converter_C"),
        )
        val reason = diagnosis.explain(request).single()
        assertTrue("Iron Ore is turned off in Raw resources" in reason, reason)
    }

    @Test
    fun tooLowLimitIsNamed() {
        val request = ProductionRequest(
            production = listOf(ProductionItem("Desc_IronPlate_C", amount = 60.0)),
            resourceMax = data.resources.keys.associateWith { if (it == "Desc_OreIron_C") 50.0 else 0.0 },
        )
        val reason = diagnosis.explain(request).single()
        assertTrue("Needs 90" in reason && "Iron Ore" in reason && "50" in reason && "limit in Raw resources" in reason, reason)
    }

    private companion object {
        /** Items that need creature parts, hand-gathered plants or nuclear waste, which no production building makes. */
        val NEEDS_OUTSIDE_SUPPLY = setOf(
            "Alien DNA Capsule", "Alien Protein", "Biomass", "Encased Plutonium Cell", "Ficsonium", "Ficsonium Fuel Rod",
            "Gas Nobelisk", "Liquid Biofuel", "Non-Fissile Uranium", "Packaged Liquid Biofuel", "Plutonium Fuel Rod",
            "Plutonium Pellet", "Solid Biofuel",
        )
    }
}
