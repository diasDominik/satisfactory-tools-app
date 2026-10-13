package io.github.diasdominik.sft.data

import kotlin.math.pow

/** Port of upstream Formula.ts plus the per-component calculations of the building pages. */
object Formula {
    fun recipeTime(recipe: Recipe, building: Building?, overclock: Double): Double =
        (100.0 / overclock) * recipe.time * (1.0 / (building?.metadata?.manufacturingSpeed?.takeIf { it != 0.0 } ?: 1.0))

    fun amountPerMinute(recipe: Recipe, building: Building?, amount: Double, overclock: Double): Double =
        60.0 / recipeTime(recipe, building, overclock) * amount

    fun powerConsumption(building: Building, overclock: Double): Double {
        val m = building.metadata
        return if (m.powerConsumptionExponent != 0.0) m.powerConsumption * (overclock / 100).pow(m.powerConsumptionExponent) else m.powerConsumption
    }

    fun recipePowerRange(recipe: Recipe, building: Building?, overclock: Double): Pair<Double, Double> {
        val exponent = building?.metadata?.powerConsumptionExponent?.takeIf { it != 0.0 } ?: 1.321929
        val factor = (overclock / 100).pow(exponent)
        return recipe.minPower * factor to recipe.maxPower * factor
    }

    fun extractionRate(miner: Miner, building: Building, purity: Purity, overclock: Double): Double {
        val base = (60.0 / miner.extractCycleTime) * (miner.itemsPerCycle / (if (miner.allowLiquids) 1000.0 else 1.0))
        val isWaterExtractor = building.className == Constants.WATER_EXTRACTOR
        val value = when (purity) {
            Purity.IMPURE -> base * Constants.MULTIPLIER_IMPURE
            Purity.NORMAL -> base * Constants.MULTIPLIER_NORMAL
            Purity.PURE -> base * Constants.MULTIPLIER_PURE
        }
        return if (isWaterExtractor && purity != Purity.NORMAL) 0.0 else value * (overclock / 100)
    }

    fun generatorPower(generator: Generator, overclock: Double): Double = generator.powerProduction * overclock / 100

    fun fuelConsumption(generator: Generator, fuel: Item, overclock: Double): Double =
        if (fuel.energyValue <= 0) 0.0 else ((generator.powerProduction / fuel.energyValue) * 60) * overclock / 100

    fun generatorWaterConsumption(generator: Generator, overclock: Double): Double =
        (60 * (generator.powerProduction * (overclock / 100).pow(1 / generator.powerProductionExponent) * generator.waterToPowerRatio)) / 1000

    fun nuclearWasteProduction(overclock: Double): Double = 10 * (overclock / 100)
}

enum class Purity(val label: String) { IMPURE("Impure"), NORMAL("Normal"), PURE("Pure") }
