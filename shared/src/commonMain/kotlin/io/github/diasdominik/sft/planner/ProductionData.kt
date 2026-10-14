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
) {
    /** Whether [recipe] runs in [machine] with every Somersloop slot filled. */
    fun isAmplified(recipe: String, machine: String): Boolean =
        Somersloop.slots(machine) > 0 && (recipe in amplifiedRecipes || machine in amplifiedMachines)

    /** Clock speed the planner runs [machine] at, kept within what the game allows. */
    fun clockSpeedFor(machine: String, recipe: String? = null): Double =
        (recipe?.let { recipeClockSpeed[it] } ?: machineClockSpeed[machine] ?: clockSpeed).coerceIn(MIN_CLOCK_SPEED, MAX_CLOCK_SPEED)

    companion object {
        const val MIN_CLOCK_SPEED = 1.0
        /** 100 % plus three Power Shards. */
        const val MAX_CLOCK_SPEED = 250.0
    }
}
