package io.github.diasdominik.sft.planner

import io.github.diasdominik.sft.data.Building
import io.github.diasdominik.sft.data.Constants
import io.github.diasdominik.sft.data.Formula
import io.github.diasdominik.sft.data.GameData
import io.github.diasdominik.sft.data.Purity
import kotlinx.serialization.Serializable
import kotlin.math.floor
import kotlin.math.pow

/**
 * How one raw resource is extracted: which extractor, on which node purity, at which clock speed,
 * and optionally how many nodes are available (which then caps the resource).
 */
@Serializable
data class ResourceExtraction(
    val extractor: String? = null,
    val purity: Purity = Purity.NORMAL,
    val clockSpeed: Double = 100.0,
    val nodes: Int? = null,
)

data class ExtractorGroup(val amount: Int, val clockSpeed: Double, val power: Double)

data class ExtractionDetails(
    val resource: String,
    val extractor: Building,
    val settings: ResourceExtraction,
    /** Output of one extractor at the chosen clock speed. */
    val ratePerExtractor: Double,
    val amount: Double,
    val groups: List<ExtractorGroup>,
) {
    val count: Int get() = groups.sumOf { it.amount }
    val power: Double get() = groups.sumOf { it.power }
}

object Extraction {
    const val MIN_CLOCK = 1.0
    const val MAX_CLOCK = 250.0
    private const val WATER_EXTRACTOR_RATE = 120.0

    /** Extractors that can work this resource, slowest first, with resource wells last. */
    fun extractors(data: GameData, resource: String): List<Building> {
        val miners = data.miners.values
            .filter { resource in it.allowedResources }
            .mapNotNull { m -> data.building(m.className)?.let { it to m } }
            .sortedWith(compareBy({ it.second.className == Constants.RESOURCE_WELL_EXTRACTOR }, { 60.0 / it.second.extractCycleTime * it.second.itemsPerCycle }))
            .map { it.first }
        val water = if (resource == Constants.WATER) listOfNotNull(data.building(Constants.WATER_EXTRACTOR)) else emptyList()
        return water + miners
    }

    fun hasPurity(extractor: String) = extractor != Constants.WATER_EXTRACTOR

    /** The saved settings for a resource, falling back to the first extractor on a normal node. */
    fun settings(data: GameData, request: ProductionRequest, resource: String): ResourceExtraction? {
        val options = extractors(data, resource)
        if (options.isEmpty()) return null
        val saved = request.extraction[resource] ?: ResourceExtraction()
        val extractor = saved.extractor?.takeIf { e -> options.any { it.className == e } } ?: options.first().className
        return saved.copy(extractor = extractor, clockSpeed = saved.clockSpeed.coerceIn(MIN_CLOCK, MAX_CLOCK))
    }

    /** Units per minute one extractor delivers with these settings. */
    fun rate(data: GameData, settings: ResourceExtraction): Double {
        val extractor = settings.extractor ?: return 0.0
        if (extractor == Constants.WATER_EXTRACTOR) return WATER_EXTRACTOR_RATE * settings.clockSpeed / 100
        val miner = data.miners[extractor] ?: return 0.0
        val building = data.building(extractor) ?: return 0.0
        return Formula.extractionRate(miner, building, settings.purity, settings.clockSpeed)
    }

    /** The resource limit the solver should use: the map limit, capped by the available nodes when given. */
    fun effectiveLimit(data: GameData, request: ProductionRequest, resource: String): Double {
        val limit = request.resourceMax[resource] ?: 0.0
        val settings = settings(data, request, resource) ?: return limit
        val nodes = settings.nodes ?: return limit
        return minOf(limit, nodes * rate(data, settings))
    }

    fun details(data: GameData, request: ProductionRequest, resource: String, amount: Double): ExtractionDetails? {
        val settings = settings(data, request, resource) ?: return null
        val extractor = data.building(settings.extractor!!) ?: return null
        val rate = rate(data, settings)
        if (rate <= 0) return null
        // Run as many extractors as possible at the chosen clock speed and underclock the last one.
        val exact = amount / rate
        val full = floor(exact + 1e-9).toInt()
        val groups = mutableListOf<ExtractorGroup>()
        if (full > 0) groups += group(extractor, full, settings.clockSpeed)
        val rest = exact - full
        if (rest > 1e-6) groups += group(extractor, 1, Numbers.ceil(rest * settings.clockSpeed, 4))
        return ExtractionDetails(resource, extractor, settings, rate, amount, groups)
    }

    private fun group(extractor: Building, amount: Int, clockSpeed: Double): ExtractorGroup {
        val m = extractor.metadata
        val exponent = m.powerConsumptionExponent.takeIf { it != 0.0 } ?: 1.321929
        return ExtractorGroup(amount, clockSpeed, amount * m.powerConsumption * (clockSpeed / 100).pow(exponent))
    }
}
