package io.github.diasdominik.sft.data

enum class GameVersion(val id: String, val label: String, val file: String) {
    V1_0("1.0", "1.0", "files/data1.0.json"),
    V1_0_FICSMAS("1.0-ficsmas", "1.0 + FICSMAS", "files/data1.0-ficsmas.json");

    companion object {
        fun fromId(id: String?): GameVersion = entries.firstOrNull { it.id == id } ?: V1_0
    }
}

object Constants {
    const val WORKBENCH = "Desc_WorkBench_C"
    const val WORKSHOP = "Desc_Workshop_C"
    const val WATER_EXTRACTOR = "Desc_WaterPump_C"
    const val WATER = "Desc_Water_C"
    const val RESOURCE_WELL_EXTRACTOR = "Desc_FrackingExtractor_C"
    val MINERS = listOf("Desc_MinerMk1_C", "Desc_MinerMk2_C", "Desc_MinerMk3_C")
    const val NUCLEAR_WASTE = "Desc_NuclearWaste_C"
    const val PLUTONIUM_WASTE = "Desc_PlutoniumWaste_C"
    const val NUCLEAR_FUEL_ROD = "Desc_NuclearFuelRod_C"
    const val GEOTHERMAL_GENERATOR = "Desc_GeneratorGeoThermal_C"
    /** Waste left per fuel rod burned. */
    val WASTE_PER_ROD: Map<String, Pair<String, Double>> = mapOf(
        NUCLEAR_FUEL_ROD to (NUCLEAR_WASTE to 50.0),
        "Desc_PlutoniumFuelRod_C" to (PLUTONIUM_WASTE to 10.0),
    )

    const val MULTIPLIER_IMPURE = 0.5
    const val MULTIPLIER_NORMAL = 1.0
    const val MULTIPLIER_PURE = 2.0

    /** Map limits of raw resources (items/min, or m3/min for fluids) for 1.0. */
    val RESOURCE_AMOUNTS: Map<String, Double> = linkedMapOf(
        "Desc_OreIron_C" to 92100.0,
        "Desc_OreCopper_C" to 36900.0,
        "Desc_Stone_C" to 69900.0,
        "Desc_Coal_C" to 42300.0,
        "Desc_OreGold_C" to 15000.0,
        "Desc_LiquidOil_C" to 12600.0,
        "Desc_RawQuartz_C" to 13500.0,
        "Desc_Sulfur_C" to 10800.0,
        "Desc_OreBauxite_C" to 12300.0,
        "Desc_OreUranium_C" to 2100.0,
        "Desc_NitrogenGas_C" to 12000.0,
        "Desc_SAM_C" to 10200.0,
        "Desc_Water_C" to 9007199254740991.0,
    )

    /** Relative scarcity of raw resources, used as the planner's cost function. */
    val RESOURCE_WEIGHTS: Map<String, Double> = linkedMapOf(
        "Desc_OreIron_C" to 1.0,
        "Desc_OreCopper_C" to 2.4959349593495936,
        "Desc_Stone_C" to 1.3175965665236051,
        "Desc_Coal_C" to 2.1773049645390072,
        "Desc_OreGold_C" to 6.140000000000001,
        "Desc_LiquidOil_C" to 7.30952380952381,
        "Desc_RawQuartz_C" to 6.822222222222222,
        "Desc_Sulfur_C" to 8.527777777777779,
        "Desc_OreBauxite_C" to 7.487804878048781,
        "Desc_OreUranium_C" to 43.85714285714286,
        "Desc_NitrogenGas_C" to 7.675000000000001,
        "Desc_SAM_C" to 9.029411764705882,
        "Desc_Water_C" to 0.0,
    )

    val SCHEMATIC_TYPES: Map<String, String> = linkedMapOf(
        "EST_Milestone" to "Milestone",
        "EST_MAM" to "MAM Research",
        "EST_Alternate" to "Alternate recipe",
        "EST_Tutorial" to "Tutorial",
        "EST_HardDrive" to "HDD Research",
        "EST_ResourceSink" to "AWESOME Sink",
        "EST_Customization" to "Customization",
    )
}

enum class BuildingKind { MANUFACTURER, EXTRACTOR, GENERATOR, OTHER }

/**
 * Indexed game data with the lookups the upstream Data/Model classes provide.
 */
class GameData(val version: GameVersion, val raw: GameDataSchema) {
    val items get() = raw.items
    val recipes get() = raw.recipes
    val buildings get() = raw.buildings
    val schematics get() = raw.schematics
    val generators get() = raw.generators
    val miners get() = raw.miners
    val resources get() = raw.resources

    private val itemsBySlug = raw.items.values.associateBy { it.slug }
    private val buildingsBySlug = raw.buildings.values.associateBy { it.slug }
    private val schematicsBySlug = raw.schematics.values.associateBy { it.slug }

    fun item(className: String?): Item? = className?.let { raw.items[it] }
    fun recipe(className: String?): Recipe? = className?.let { raw.recipes[it] ?: powerRecipes[it] }
    fun building(className: String?): Building? = className?.let { raw.buildings[it] }
    fun schematic(className: String?): Schematic? = className?.let { raw.schematics[it] }
    fun itemBySlug(slug: String) = itemsBySlug[slug]
    fun buildingBySlug(slug: String) = buildingsBySlug[slug]
    fun schematicBySlug(slug: String) = schematicsBySlug[slug]

    /** Display name of anything that can appear in a recipe (items and buildings share the namespace). */
    fun nameOf(className: String): String =
        raw.items[className]?.name ?: raw.buildings[className]?.name ?: raw.schematics[className]?.name ?: className

    fun isResource(className: String) = className in raw.resources

    fun isManualManufacturer(b: Building) = b.className == Constants.WORKBENCH || b.className == Constants.WORKSHOP
    fun isGenerator(b: Building) = b.className in raw.generators
    fun isExtractor(b: Building) = b.className in raw.miners
    fun isManufacturer(b: Building): Boolean {
        if (isGenerator(b) || isExtractor(b)) return false
        if (isManualManufacturer(b)) return true
        return b.metadata.manufacturingSpeed != 0.0
    }

    fun kindOf(b: Building): BuildingKind = when {
        isGenerator(b) -> BuildingKind.GENERATOR
        isExtractor(b) -> BuildingKind.EXTRACTOR
        isManufacturer(b) && !isManualManufacturer(b) && raw.recipes.values.any { b.className in it.producedIn && it.inMachine } -> BuildingKind.MANUFACTURER
        else -> BuildingKind.OTHER
    }

    /** Machines a recipe runs in (only automated manufacturers). */
    fun machineFor(recipe: Recipe): Building? =
        recipe.producedIn.firstNotNullOfOrNull { raw.buildings[it]?.takeIf { b -> isManufacturer(b) && !isManualManufacturer(b) } }

    /**
     * A generator burning one fuel, written as a recipe so a production line can include power plants:
     * per minute, what one generator at 100 % burns (fuel and water) and leaves (waste).
     */
    val powerRecipes: Map<String, Recipe> by lazy {
        raw.generators.values.flatMap { g ->
            val generator = raw.buildings[g.className] ?: return@flatMap emptyList()
            g.fuel.mapNotNull { fuel ->
                val item = raw.items[fuel] ?: return@mapNotNull null
                val burn = Formula.fuelConsumption(g, item, 100.0).takeIf { it > 0 } ?: return@mapNotNull null
                val water = Formula.generatorWaterConsumption(g, 100.0)
                Recipe(
                    slug = "power-" + generator.slug + "-" + item.slug,
                    name = generator.name + " (" + item.name + ")",
                    className = powerRecipeClass(g.className, fuel),
                    time = 60.0,
                    ingredients = listOf(ItemAmount(fuel, burn)) + if (water > 0) listOf(ItemAmount(Constants.WATER, water)) else emptyList(),
                    products = Constants.WASTE_PER_ROD[fuel]?.let { (waste, perRod) -> listOf(ItemAmount(waste, burn * perRod)) }.orEmpty(),
                    producedIn = listOf(g.className),
                )
            }
        }.associateBy { it.className }
    }

    /** The generator a power recipe runs in, set up so one "machine" is one generator at 100 %. */
    fun powerMachine(generator: String): Building? =
        raw.buildings[generator]?.let { it.copy(metadata = it.metadata.copy(powerConsumption = 0.0, manufacturingSpeed = 1.0)) }

    fun powerRecipeClass(generator: String, fuel: String) = "Power_" + generator + "_" + fuel

    val baseRecipes: List<Recipe> by lazy {
        raw.recipes.values.filter { !it.alternate && it.inMachine }.sortedBy { it.name }
    }
    val alternateRecipes: List<Recipe> by lazy {
        raw.recipes.values.filter { it.alternate }.sortedBy { it.name.removePrefix("Alternate: ") }
    }
    val sinkableItems: List<Item> by lazy { raw.items.values.filter { sinkPointsOf(it.className) > 0 }.sortedBy { it.name } }

    /** AWESOME Sink points for one [item]; fluids can't go into the sink, whatever the data says. */
    fun sinkPointsOf(item: String): Int = raw.items[item]?.takeIf { !it.liquid }?.sinkPoints ?: 0

    val resourceList: List<Resource> by lazy {
        raw.resources.values.sortedBy { raw.items[it.item]?.name ?: it.item }
    }

    val manufacturers: List<Building> by lazy {
        raw.buildings.values.filter { isManufacturer(it) && !isManualManufacturer(it) && kindOf(it) == BuildingKind.MANUFACTURER }
            .sortedBy { it.name }
    }

    /** Items that can be produced by an automated recipe (upstream Model.getAutomatableItems). */
    val automatableItems: List<Item> by lazy { automatable(includeWaste = false) }

    /** Items that can be provided as factory input. */
    val inputableItems: List<Item> by lazy {
        automatable(includeWaste = true).filter {
            it.className !in raw.resources || it.className == Constants.NUCLEAR_WASTE || it.className == Constants.PLUTONIUM_WASTE
        }
    }

    private fun automatable(includeWaste: Boolean): List<Item> {
        val produced = raw.recipes.values.filter { it.inMachine }.flatMap { r -> r.products.map { it.item } }.toSet()
        return raw.items.values.filter {
            it.className in produced ||
                (includeWaste && (it.className == Constants.NUCLEAR_WASTE || it.className == Constants.PLUTONIUM_WASTE))
        }.sortedBy { it.name }
    }

    fun recipesForItem(item: Item): List<Recipe> {
        val all = raw.recipes.values.filter { r -> r.products.any { it.item == item.className } }
        return all.filter { !it.alternate } + all.filter { it.alternate }
    }

    fun usagesAsIngredient(item: Item): List<Recipe> {
        val all = raw.recipes.values.filter { r -> !r.forBuilding && r.ingredients.any { it.item == item.className } }
        return all.filter { !it.alternate } + all.filter { it.alternate }
    }

    fun usagesForBuilding(item: Item): List<Recipe> =
        raw.recipes.values.filter { r -> r.forBuilding && r.ingredients.any { it.item == item.className } }

    fun usagesForSchematics(item: Item): List<Schematic> =
        raw.schematics.values.filter { s -> s.cost.any { it.item == item.className } }

    /** Recipe that builds the given building (its construction cost). */
    fun buildingRecipe(building: Building): Recipe? =
        raw.recipes.values.firstOrNull { it.products.firstOrNull()?.item == building.className }

    fun recipesInMachine(building: Building): List<Recipe> =
        raw.recipes.values.filter { building.className in it.producedIn }.let { all ->
            all.filter { !it.alternate }.sortedBy { it.name } + all.filter { it.alternate }.sortedBy { it.name }
        }

    /** Schematics that unlock the given recipe. */
    fun schematicsUnlocking(recipe: Recipe): List<Schematic> =
        raw.schematics.values.filter { recipe.className in it.unlock.recipes }

    /** The schematic, everything it depends on, and everything that depends on it. */
    fun relevantSchematics(schematic: Schematic): List<Schematic> {
        val result = linkedSetOf(schematic)
        fun parents(s: Schematic) {
            for (dep in s.requiredSchematics) {
                val d = raw.schematics[dep] ?: continue
                if (result.add(d)) parents(d)
            }
        }
        fun children(s: Schematic) {
            for (c in raw.schematics.values) {
                if (s.className in c.requiredSchematics && result.add(c)) children(c)
            }
        }
        parents(schematic)
        children(schematic)
        return result.toList()
    }

    fun extractorResources(miner: Miner): List<Item> =
        if (miner.allowedResources.isNotEmpty()) miner.allowedResources.mapNotNull { raw.items[it] }
        else raw.resources.keys.mapNotNull { raw.items[it] }.filter { (miner.allowLiquids && it.liquid) || (miner.allowSolids && !it.liquid) }
}
