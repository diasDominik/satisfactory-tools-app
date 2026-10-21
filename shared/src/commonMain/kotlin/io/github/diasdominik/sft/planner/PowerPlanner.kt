package io.github.diasdominik.sft.planner

import io.github.diasdominik.sft.data.Constants
import io.github.diasdominik.sft.data.Formula
import io.github.diasdominik.sft.data.GameData
import kotlin.math.ceil

data class PowerPlan(
    val generator: String,
    val fuel: String,
    val generators: Int,
    val clockSpeed: Double,
    val powerPerGenerator: Double,
    val totalPower: Double,
    /** Per minute, in m³ for fluid fuels. */
    val fuelPerMinute: Double,
    val waterPerMinute: Double,
    val waterExtractors: Int,
    /** Radioactive waste item and amount per minute, for nuclear fuel rods that leave any. */
    val waste: Pair<String, Double>?,
)

/** Power plant sizing: how many generators of a kind, and what they burn, for a count or a target in MW. */
object PowerPlanner {
    /** Water Extractor output at 100 %, m³/min. */
    const val WATER_EXTRACTOR_RATE = 120.0

    /** Generators needed to deliver [targetMw] at [clockSpeed]. */
    fun generatorsFor(data: GameData, generator: String, targetMw: Double, clockSpeed: Double): Int {
        val g = data.generators[generator] ?: return 0
        val each = Formula.generatorPower(g, clockSpeed)
        return if (each <= 0 || targetMw <= 0) 0 else ceil(targetMw / each - 1e-9).toInt()
    }

    fun plan(data: GameData, generator: String, fuel: String, count: Int, clockSpeed: Double): PowerPlan? {
        val g = data.generators[generator] ?: return null
        val item = data.item(fuel) ?: return null
        if (fuel !in g.fuel) return null
        val each = Formula.generatorPower(g, clockSpeed)
        val fuelRate = Formula.fuelConsumption(g, item, clockSpeed) * count
        val water = Formula.generatorWaterConsumption(g, clockSpeed) * count
        return PowerPlan(
            generator = generator,
            fuel = fuel,
            generators = count,
            clockSpeed = clockSpeed,
            powerPerGenerator = each,
            totalPower = each * count,
            fuelPerMinute = fuelRate,
            waterPerMinute = water,
            waterExtractors = if (water > 0) ceil(water / WATER_EXTRACTOR_RATE - 1e-9).toInt() else 0,
            waste = Constants.WASTE_PER_ROD[fuel]?.let { (waste, perRod) -> waste to fuelRate * perRod },
        )
    }
}
