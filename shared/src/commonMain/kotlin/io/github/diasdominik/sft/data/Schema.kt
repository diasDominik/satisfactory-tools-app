package io.github.diasdominik.sft.data

import kotlinx.serialization.Serializable

/**
 * Mirrors the JSON exported by greeny/SatisfactoryTools (data/data1.0.json).
 */
@Serializable
data class GameDataSchema(
    val items: Map<String, Item>,
    val recipes: Map<String, Recipe>,
    val schematics: Map<String, Schematic>,
    val generators: Map<String, Generator>,
    val resources: Map<String, Resource>,
    val miners: Map<String, Miner>,
    val buildings: Map<String, Building>,
)

@Serializable
data class Color(val r: Int = 0, val g: Int = 0, val b: Int = 0, val a: Int = 0)

@Serializable
data class Item(
    val slug: String,
    val icon: String = "",
    val name: String,
    val description: String = "",
    val sinkPoints: Int = 0,
    val className: String,
    val stackSize: Int = 0,
    val energyValue: Double = 0.0,
    val radioactiveDecay: Double = 0.0,
    val liquid: Boolean = false,
    val fluidColor: Color = Color(),
)

@Serializable
data class ItemAmount(val item: String, val amount: Double)

@Serializable
data class Recipe(
    val slug: String,
    val name: String,
    val className: String,
    val alternate: Boolean = false,
    val time: Double = 0.0,
    val inHand: Boolean = false,
    val forBuilding: Boolean = false,
    val inWorkshop: Boolean = false,
    val inMachine: Boolean = false,
    val manualTimeMultiplier: Double = 1.0,
    val ingredients: List<ItemAmount> = emptyList(),
    val products: List<ItemAmount> = emptyList(),
    val producedIn: List<String> = emptyList(),
    val isVariablePower: Boolean = false,
    val minPower: Double = 0.0,
    val maxPower: Double = 0.0,
)

@Serializable
data class SchematicUnlock(
    val recipes: List<String> = emptyList(),
    val scannerResources: List<String> = emptyList(),
    val inventorySlots: Int = 0,
    val giveItems: List<ItemAmount> = emptyList(),
)

@Serializable
data class Schematic(
    val className: String,
    val type: String,
    val name: String,
    val slug: String,
    val icon: String = "",
    val cost: List<ItemAmount> = emptyList(),
    val unlock: SchematicUnlock = SchematicUnlock(),
    val requiredSchematics: List<String> = emptyList(),
    val tier: Int = 0,
    val time: Double = 0.0,
    val mam: Boolean = false,
    val alternate: Boolean = false,
)

@Serializable
data class Generator(
    val className: String,
    val fuel: List<String> = emptyList(),
    val powerProduction: Double = 0.0,
    val powerProductionExponent: Double = 1.6,
    val waterToPowerRatio: Double = 0.0,
)

@Serializable
data class Resource(
    val item: String,
    val pingColor: Color = Color(),
    val speed: Double = 1.0,
)

@Serializable
data class Miner(
    val className: String,
    val allowedResources: List<String> = emptyList(),
    val allowLiquids: Boolean = false,
    val allowSolids: Boolean = false,
    val itemsPerCycle: Double = 1.0,
    val extractCycleTime: Double = 1.0,
)

@Serializable
data class BuildingMetadata(
    val powerConsumption: Double = 0.0,
    val powerConsumptionExponent: Double = 0.0,
    val manufacturingSpeed: Double = 0.0,
)

@Serializable
data class BuildingSize(val width: Double = 0.0, val height: Double = 0.0, val length: Double = 0.0)

@Serializable
data class Building(
    val slug: String,
    val icon: String = "",
    val name: String,
    val description: String = "",
    val className: String,
    val categories: List<String> = emptyList(),
    val buildMenuPriority: Double = 0.0,
    val metadata: BuildingMetadata = BuildingMetadata(),
    val size: BuildingSize = BuildingSize(),
)
