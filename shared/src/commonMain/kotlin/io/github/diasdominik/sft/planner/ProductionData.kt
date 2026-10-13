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
)

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
) {
    /** Clock speed the planner runs [machine] at, kept within what the game allows. */
    fun clockSpeedFor(machine: String): Double =
        (machineClockSpeed[machine] ?: clockSpeed).coerceIn(MIN_CLOCK_SPEED, MAX_CLOCK_SPEED)

    companion object {
        const val MIN_CLOCK_SPEED = 1.0
        /** 100 % plus three Power Shards. */
        const val MAX_CLOCK_SPEED = 250.0
    }
}
