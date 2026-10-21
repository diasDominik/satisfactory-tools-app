package io.github.diasdominik.sft.planner

import io.github.diasdominik.sft.data.Constants
import kotlinx.serialization.Serializable

/** Same shape as upstream IProductionData, so .sft exports stay compatible with the website. */
@Serializable
data class ProductionData(
    val metadata: ProductionMetadata = ProductionMetadata(),
    val request: ProductionRequest = ProductionRequest(),
)

@Serializable
data class ProductionMetadata(
    val name: String? = null,
    val icon: String? = null,
    val schemaVersion: Int = 1,
    val gameVersion: String = "0",
    /** App-only: graph nodes marked as built, by node key (see [graphNodeKey]). */
    val built: List<String> = emptyList(),
    /** App-only: where graph nodes were dragged to, by node key, in dp. */
    val graphPositions: Map<String, GraphPoint> = emptyMap(),
    /** App-only: stable id, so other lines can take this line's output as their input. */
    val uid: String? = null,
    /** App-only: best belt (Mk.1–6) and pipe (Mk.1–2) the player has, for capacity warnings. */
    val beltTier: Int = Logistics.BELT_SPEEDS.size,
    val pipeTier: Int = Logistics.PIPE_SPEEDS.size,
    /** App-only: the Somersloops and Power Shards the player has, for the optimizer. */
    val somersloopsOwned: Int = 0,
    val powerShardsOwned: Int = 0,
    /** App-only: what was built when the player last marked the plan as built, by node key. */
    val builtPlan: Map<String, BuiltStep> = emptyMap(),
)

@Serializable
data class BuiltStep(val name: String, val machine: String, val count: Int, val clockSpeed: Double = 100.0, val somersloops: Int = 0)

@Serializable
data class GraphPoint(val x: Float, val y: Float)

object ProductionType {
    const val PER_MINUTE = "perMinute"
    const val MAXIMIZE = "max"
}

@Serializable
data class ProductionItem(
    val item: String? = null,
    val type: String = ProductionType.PER_MINUTE,
    val amount: Double = 10.0,
    val ratio: Double = 100.0,
)

@Serializable
data class InputItem(
    val item: String? = null,
    val amount: Double = 10.0,
    /** App-only: [ProductionMetadata.uid] of the line this input comes from; its amount follows that line's output. */
    val source: String? = null,
)

@Serializable
data class ProductionRequest(
    val resourceMax: Map<String, Double> = Constants.RESOURCE_AMOUNTS,
    val resourceWeight: Map<String, Double> = Constants.RESOURCE_WEIGHTS,
    val blockedResources: List<String> = emptyList(),
    val blockedRecipes: List<String> = emptyList(),
    val blockedMachines: List<String> = emptyList(),
    val allowedAlternateRecipes: List<String> = emptyList(),
    val sinkableResources: List<String> = emptyList(),
    val production: List<ProductionItem> = listOf(ProductionItem()),
    val input: List<InputItem> = emptyList(),
    /** App-only: extractor, purity, clock speed and node count per raw resource. */
    val extraction: Map<String, ResourceExtraction> = emptyMap(),
    /** App-only: clock speed in % for production buildings, and per building overrides. */
    val clockSpeed: Double = 100.0,
    val machineClockSpeed: Map<String, Double> = emptyMap(),
    /** App-only: buildings and recipes with every Somersloop slot filled (Production Amplifier). */
    val amplifiedMachines: List<String> = emptyList(),
    val amplifiedRecipes: List<String> = emptyList(),
    /** App-only: clock speed per recipe, e.g. from the Power Shard optimizer; wins over the building's. */
    val recipeClockSpeed: Map<String, Double> = emptyMap(),
    /**
     * App-only: when set, the planner decides itself which buildings get Somersloops, using at most
     * this many, instead of [amplifiedMachines] and [amplifiedRecipes].
     */
    val somersloopBudget: Int? = null,
    /** App-only: power plants inside this line that cover what it uses. */
    val power: FactoryPower? = null,
    /** App-only: how raw resources are weighed against each other. Older lines used their own weights. */
    val weightMode: WeightMode = WeightMode.MANUAL,
    /** App-only: how buildings split the work when the count isn't whole. */
    val clockGrouping: ClockGrouping = ClockGrouping.UNDERCLOCK_LAST,
    /** App-only: like the game's advanced settings, ingredients per craft and building power scaled. */
    val recipeCostMultiplier: Double = 1.0,
    val powerCostMultiplier: Double = 1.0,
) {
    val recipeCost: Double get() = recipeCostMultiplier.coerceIn(MIN_MULTIPLIER, MAX_MULTIPLIER)
    val powerCost: Double get() = powerCostMultiplier.coerceIn(MIN_MULTIPLIER, MAX_MULTIPLIER)

    /** What one unit of [resource] costs the planner, by [weightMode]. */
    fun weightOf(resource: String): Double = when (weightMode) {
        WeightMode.MAP -> Constants.RESOURCE_WEIGHTS[resource] ?: 1.0
        WeightMode.EQUAL -> if (resource == Constants.WATER) 0.0 else 1.0
        WeightMode.MANUAL -> resourceWeight[resource] ?: 1.0
        WeightMode.LIMITS -> {
            // Like the map weights, but from this line's own limits: scarcer means dearer.
            val limit = resourceMax[resource] ?: 0.0
            val largest = resourceMax.values.filter { it > 0 && it < 1e12 }.maxOrNull() ?: 0.0
            if (limit <= 0 || limit >= 1e12 || largest <= 0) 0.0 else largest / limit
        }
    }

    /** Whether [recipe] runs in [machine] with every Somersloop slot filled. */
    fun isAmplified(recipe: String, machine: String): Boolean =
        somersloopBudget == null && Somersloop.slots(machine) > 0 && (recipe in amplifiedRecipes || machine in amplifiedMachines)

    /** Clock speed the planner runs [machine] at, kept within what the game allows. */
    fun clockSpeedFor(machine: String, recipe: String? = null): Double =
        (recipe?.let { recipeClockSpeed[it] } ?: machineClockSpeed[machine] ?: clockSpeed).coerceIn(MIN_CLOCK_SPEED, MAX_CLOCK_SPEED)

    companion object {
        const val MIN_CLOCK_SPEED = 1.0
        /** 100 % plus three Power Shards. */
        const val MAX_CLOCK_SPEED = 250.0
        const val MIN_MULTIPLIER = 0.25
        const val MAX_MULTIPLIER = 2.0
    }
}

/** Generators a production line runs itself to cover its own power use. */
@Serializable
data class FactoryPower(
    /** Generator → fuels it may burn. The planner picks the cheapest mix. */
    val generators: Map<String, List<String>> = emptyMap(),
    /** Headroom on top of what the line uses, in percent. */
    val extraPercent: Double = 0.0,
    val clockSpeed: Double = 100.0,
)

@Serializable
enum class WeightMode(val label: String, val description: String) {
    MAP("Map", "Resources cost more the scarcer they are on the map."),
    LIMITS("My limits", "Resources cost more the lower the limits you set here."),
    EQUAL("Equal", "Every resource costs the same, so the plan uses the fewest raw resources in total."),
    MANUAL("Manual", "Set the cost of each resource yourself."),
}

@Serializable
enum class ClockGrouping(val label: String) {
    UNDERCLOCK_LAST("Underclock the last"),
    EQUAL("Clock all equally"),
    NONE("No clocking"),
}

/**
 * Splits [exact] buildings' worth of work at [clockSpeed] into groups of (count, clock speed):
 * full buildings and one slower one, all equally slower, or whole buildings at the set speed.
 */
fun splitBuildings(exact: Double, clockSpeed: Double, grouping: ClockGrouping): List<Pair<Int, Double>> {
    if (exact <= 1e-6) return emptyList()
    val whole = kotlin.math.ceil(exact - 1e-9).toInt()
    return when (grouping) {
        ClockGrouping.UNDERCLOCK_LAST -> {
            val full = kotlin.math.floor(exact + 1e-9).toInt()
            val rest = exact - full
            listOfNotNull(
                (full to clockSpeed).takeIf { full > 0 },
                (1 to Numbers.ceil(rest * clockSpeed, 4)).takeIf { rest > 1e-6 },
            )
        }
        ClockGrouping.EQUAL -> listOf(whole to Numbers.ceil(exact * clockSpeed / whole, 4))
        ClockGrouping.NONE -> listOf(whole to clockSpeed)
    }
}
