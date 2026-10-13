package io.github.diasdominik.sft.planner

import io.github.diasdominik.sft.data.GameData
import io.github.diasdominik.sft.data.Recipe
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

// ---------------------------------------------------------------------------------------------
// Graph (port of upstream Tools/Production/Result)
// ---------------------------------------------------------------------------------------------

class ResourceAmount(val item: String, val maxAmount: Double, var amount: Double)

sealed class GraphNode {
    var id: Int = 0
    open val inputs: List<ResourceAmount> = emptyList()
    open val outputs: List<ResourceAmount> = emptyList()
    val connectedEdges = mutableListOf<GraphEdge>()
}

class RecipeNode(val usage: RecipeUsage) : GraphNode() {
    private val multiplier = usage.amount * usage.machine.metadata.manufacturingSpeed * (60.0 / usage.recipe.time)
    override val inputs = usage.recipe.ingredients.map { ResourceAmount(it.item, it.amount * multiplier, 0.0) }
    override val outputs = usage.recipe.products.map { ResourceAmount(it.item, it.amount * multiplier, it.amount * multiplier) }
    val machines = MachineGroup(usage)
}

class MinerNode(val item: String, val amount: Double) : GraphNode() {
    override val outputs = listOf(ResourceAmount(item, amount, amount))
}

class InputNode(val item: String, val amount: Double) : GraphNode() {
    override val outputs = listOf(ResourceAmount(item, amount, amount))
}

class ProductNode(val item: String, val amount: Double) : GraphNode() {
    override val inputs = listOf(ResourceAmount(item, amount, 0.0))
}

class ByproductNode(val item: String, val amount: Double) : GraphNode() {
    override val inputs = listOf(ResourceAmount(item, amount, 0.0))
}

class GraphEdge(val from: GraphNode, val to: GraphNode, val item: String, val amount: Double) {
    var id: Int = 0
}

class ProductionGraph {
    val nodes = mutableListOf<GraphNode>()
    val edges = mutableListOf<GraphEdge>()
    private var lastId = 1

    fun addNode(node: GraphNode) {
        node.id = lastId++
        nodes += node
    }

    fun generateEdges() {
        for (from in nodes) {
            outputLoop@ for (output in from.outputs) {
                for (to in nodes) {
                    for (input in to.inputs) {
                        if (input.item == output.item && input.amount < input.maxAmount) {
                            val diff = minOf(input.maxAmount - input.amount, output.amount)
                            output.amount -= diff
                            input.amount += diff
                            if (abs(input.maxAmount - input.amount) < DELTA) input.amount = input.maxAmount
                            if (abs(output.amount) < DELTA) output.amount = 0.0
                            if (diff > DELTA) {
                                val edge = GraphEdge(from, to, output.item, diff).also { it.id = lastId++ }
                                edges += edge
                                from.connectedEdges += edge
                                if (to !== from) to.connectedEdges += edge
                            }
                            if (output.amount == 0.0) continue@outputLoop
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val DELTA = 1e-8

        fun from(output: SolverOutput): ProductionGraph {
            val graph = ProductionGraph()
            output.recipes.forEach { graph.addNode(RecipeNode(it)) }
            output.inputs.forEach { (item, amount) -> graph.addNode(InputNode(item, amount)) }
            output.mined.forEach { (item, amount) -> graph.addNode(MinerNode(item, amount)) }
            output.products.forEach { (item, amount) -> graph.addNode(ProductNode(item, amount)) }
            output.byproducts.forEach { (item, amount) -> graph.addNode(ByproductNode(item, amount)) }
            graph.generateEdges()
            return graph
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Machine groups and power
// ---------------------------------------------------------------------------------------------

data class Power(val average: Double = 0.0, val max: Double = 0.0, val isVariable: Boolean = false)

data class MachineGroupItem(val amount: Int, val clockSpeed: Double, val power: Power)

class MachineGroup(private val usage: RecipeUsage) {
    val machines: List<MachineGroupItem>
    val power: Power

    init {
        val exact = usage.amount * 100 / usage.clockSpeed
        val list = mutableListOf<MachineGroupItem>()
        val full = floor(exact + 1e-9).toInt()
        if (full > 0) list += machine(full, usage.clockSpeed)
        val rest = exact - full
        if (rest > 1e-6) list += machine(1, Numbers.ceil(rest * usage.clockSpeed, 4))
        machines = list
        var average = 0.0
        var max = 0.0
        var variable = false
        for (m in list) {
            average += m.power.average
            max += m.power.max
            variable = variable || m.power.isVariable
        }
        if (abs(max - average) < 1e-8) variable = false
        power = Power(average, max, variable)
    }

    val count: Int get() = machines.sumOf { it.amount }

    private fun machine(amount: Int, clockSpeed: Double): MachineGroupItem {
        val metadata = usage.machine.metadata
        val clockFactor = (clockSpeed / 100).pow(metadata.powerConsumptionExponent)
        var average = amount * metadata.powerConsumption * clockFactor
        var max = 0.0
        var variable = false
        if (usage.recipe.isVariablePower) {
            max = amount * usage.recipe.maxPower * clockFactor
            val min = amount * usage.recipe.minPower * clockFactor
            average = (max + min) / 2
            variable = true
        }
        if (max < average) max = average
        if (abs(max - average) < 1e-8) variable = false
        return MachineGroupItem(amount, clockSpeed, Power(average, max, variable))
    }
}

// ---------------------------------------------------------------------------------------------
// Result details (port of upstream ProductionResult)
// ---------------------------------------------------------------------------------------------

data class RecipeBuildingDetails(val amount: Int, val resources: Map<String, Double>)
data class BuildingDetails(val amount: Int, val recipes: Map<String, RecipeBuildingDetails>, val resources: Map<String, Double>)
data class BuildingsDetails(val buildings: Map<String, BuildingDetails>, val resources: Map<String, Double>, val amount: Int)

enum class FlowSource { RECIPE, MINER, INPUT }
data class ItemFlow(val type: FlowSource, val amount: Double, val percentage: Double)
data class ItemDetails(
    val produced: Double,
    val consumed: Double,
    val diff: Double,
    val producers: Map<String, ItemFlow>,
    val consumers: Map<String, ItemFlow>,
)

data class InputDetails(val used: Double, val max: Double, val usedPercentage: Double, val producedExtra: Double)
data class RawResourceDetails(val enabled: Boolean, val max: Double, val used: Double, val usedPercentage: Double)

data class RecipePowerDetails(val clockSpeed: Double, val amount: Int, val machines: List<MachineGroupItem>, val power: Power)
data class MachinePowerDetails(val amount: Int, val power: Power, val recipes: Map<String, RecipePowerDetails>)
data class PowerDetails(val total: Power, val byBuilding: Map<String, MachinePowerDetails>)

class ProductionResult(request: ProductionRequest, val graph: ProductionGraph, private val data: GameData) {
    /** Extractors per mined resource. Declared first because buildings and power include them. */
    val extraction: List<ExtractionDetails> = graph.nodes.filterIsInstance<MinerNode>()
        .groupBy { it.item }
        .mapNotNull { (item, nodes) -> Extraction.details(data, request, item, nodes.sumOf { it.amount }) }
        .sortedBy { data.nameOf(it.resource) }
    val buildings: BuildingsDetails = calculateBuildings()
    val items: Map<String, ItemDetails> = calculateItems()
    val input: Map<String, InputDetails> = calculateInput(request)
    val rawResources: Map<String, RawResourceDetails> = calculateRawResources(request)
    val output: Map<String, Double> = sumNodes<ProductNode> { it.item to it.amount }
    val byproducts: Map<String, Double> = sumNodes<ByproductNode> { it.item to it.amount }
    val alternatesNeeded: List<Recipe> = graph.nodes.filterIsInstance<RecipeNode>()
        .map { it.usage.recipe }.filter { it.alternate }.distinct().sortedBy { it.name }
    val power: PowerDetails = calculatePower()

    private inline fun <reified T : GraphNode> sumNodes(pair: (T) -> Pair<String, Double>): Map<String, Double> {
        val result = LinkedHashMap<String, Double>()
        graph.nodes.filterIsInstance<T>().forEach { val (k, v) = pair(it); result.mergeWith(k, v) { a, b -> a + b } }
        return result
    }

    private fun calculateBuildings(): BuildingsDetails {
        val recipesByBuilding = LinkedHashMap<String, LinkedHashMap<String, RecipeBuildingDetails>>()
        val counts = LinkedHashMap<String, Int>()
        for (node in graph.nodes.filterIsInstance<RecipeNode>()) {
            val building = node.usage.machine.className
            val amount = node.machines.count
            counts.mergeWith(building, amount) { a, b -> a + b }
            recipesByBuilding.getOrPut(building) { LinkedHashMap() }[node.usage.recipe.className] =
                RecipeBuildingDetails(amount, buildingCost(building, amount))
        }
        for (e in extraction) {
            val building = e.extractor.className
            counts.mergeWith(building, e.count) { a, b -> a + b }
            recipesByBuilding.getOrPut(building) { LinkedHashMap() }[e.resource] = RecipeBuildingDetails(e.count, buildingCost(building, e.count))
        }
        val buildings = recipesByBuilding.mapValues { (building, recipes) ->
            BuildingDetails(counts.getValue(building), recipes, sumCosts(recipes.values.map { it.resources }))
        }.entries.sortedBy { data.nameOf(it.key) }.associate { it.key to it.value }
        return BuildingsDetails(buildings, sumCosts(buildings.values.map { it.resources }), counts.values.sum())
    }

    private fun buildingCost(building: String, amount: Int): Map<String, Double> {
        val recipe = data.building(building)?.let { data.buildingRecipe(it) } ?: return emptyMap()
        return recipe.ingredients.associate { it.item to it.amount * amount }
    }

    private fun sumCosts(costs: List<Map<String, Double>>): Map<String, Double> {
        val result = LinkedHashMap<String, Double>()
        costs.forEach { c -> c.forEach { (k, v) -> result.mergeWith(k, v) { a, b -> a + b } } }
        return result
    }

    private fun calculateItems(): Map<String, ItemDetails> {
        class Acc {
            var produced = 0.0
            var consumed = 0.0
            val producers = LinkedHashMap<String, Pair<FlowSource, Double>>()
            val consumers = LinkedHashMap<String, Pair<FlowSource, Double>>()
        }
        val items = LinkedHashMap<String, Acc>()
        for (node in graph.nodes) {
            for (edge in node.connectedEdges) {
                val acc = items.getOrPut(edge.item) { Acc() }
                val amount = Numbers.round(edge.amount)
                if (edge.from === edge.to) {
                    acc.produced += amount
                    acc.consumed += amount
                } else if (edge.from === node) {
                    val to = edge.to
                    if (to is RecipeNode) {
                        acc.consumers.mergeWith(to.usage.recipe.className, FlowSource.RECIPE to amount) { a, b -> a.first to a.second + b.second }
                        acc.consumed += amount
                    }
                } else {
                    when (val from = edge.from) {
                        is RecipeNode -> {
                            acc.producers.mergeWith(from.usage.recipe.className, FlowSource.RECIPE to amount) { a, b -> a.first to a.second + b.second }
                            acc.produced += amount
                        }
                        is MinerNode -> {
                            acc.producers.mergeWith(from.item, FlowSource.MINER to amount) { a, b -> a.first to a.second + b.second }
                            acc.produced += amount
                        }
                        is InputNode -> {
                            acc.producers.mergeWith(from.item, FlowSource.INPUT to amount) { a, b -> a.first to a.second + b.second }
                        }
                        else -> {}
                    }
                }
            }
        }
        return items.entries.sortedWith(compareBy({ !data.isResource(it.key) }, { data.nameOf(it.key) })).associate { (item, acc) ->
            item to ItemDetails(
                produced = acc.produced,
                consumed = acc.consumed,
                diff = Numbers.round(acc.produced - acc.consumed),
                producers = acc.producers.mapValues { (_, v) -> ItemFlow(v.first, v.second, if (acc.produced > 0) Numbers.round(v.second / acc.produced * 100) else 0.0) },
                consumers = acc.consumers.mapValues { (_, v) -> ItemFlow(v.first, v.second, if (acc.consumed > 0) Numbers.round(v.second / acc.consumed * 100) else 0.0) },
            )
        }
    }

    private fun calculateInput(request: ProductionRequest): Map<String, InputDetails> {
        val max = LinkedHashMap<String, Double>()
        for (input in request.input) {
            val item = input.item ?: continue
            if (input.amount > 0 && data.item(item) != null) max.mergeWith(item, input.amount) { a, b -> a + b }
        }
        val used = HashMap<String, Double>()
        val extra = HashMap<String, Double>()
        for (node in graph.nodes) {
            when (node) {
                is InputNode -> used.mergeWith(node.item, node.amount) { a, b -> a + b }
                is RecipeNode -> node.outputs.forEach { if (it.item in max) extra.mergeWith(it.item, it.maxAmount) { a, b -> a + b } }
                is MinerNode -> if (node.item in max) extra.mergeWith(node.item, node.amount) { a, b -> a + b }
                else -> {}
            }
        }
        return max.mapValues { (item, limit) ->
            val u = Numbers.round(used[item] ?: 0.0)
            InputDetails(u, Numbers.round(limit), Numbers.round(u / limit * 100), Numbers.round(extra[item] ?: 0.0))
        }
    }

    private fun calculateRawResources(request: ProductionRequest): Map<String, RawResourceDetails> {
        val used = HashMap<String, Double>()
        graph.nodes.filterIsInstance<MinerNode>().forEach { used.mergeWith(it.item, it.amount) { a, b -> a + b } }
        return data.resourceList.associate { resource ->
            val max = request.resourceMax[resource.item] ?: 0.0
            val u = Numbers.round(used[resource.item] ?: 0.0)
            resource.item to RawResourceDetails(
                enabled = resource.item !in request.blockedResources,
                max = max,
                used = u,
                usedPercentage = if (max > 0) Numbers.round(u / max * 100) else 0.0,
            )
        }
    }

    private fun calculatePower(): PowerDetails {
        val byBuilding = LinkedHashMap<String, MachinePowerDetails>()
        for (node in graph.nodes.filterIsInstance<RecipeNode>()) {
            val machine = node.usage.machine.className
            val group = node.machines
            val recipePower = RecipePowerDetails(node.usage.clockSpeed, group.count, group.machines, group.power.rounded())
            val current = byBuilding[machine]
            byBuilding[machine] = if (current == null) {
                MachinePowerDetails(group.count, group.power, mapOf(node.usage.recipe.className to recipePower))
            } else {
                MachinePowerDetails(
                    current.amount + group.count,
                    Power(current.power.average + group.power.average, current.power.max + group.power.max, current.power.isVariable || group.power.isVariable),
                    current.recipes + (node.usage.recipe.className to recipePower),
                )
            }
        }
        for (e in extraction) {
            val machine = e.extractor.className
            val power = Power(e.power, e.power)
            val groups = e.groups.map { MachineGroupItem(it.amount, it.clockSpeed, Power(it.power, it.power)) }
            val details = RecipePowerDetails(e.settings.clockSpeed, e.count, groups, power.rounded())
            val current = byBuilding[machine]
            byBuilding[machine] = if (current == null) {
                MachinePowerDetails(e.count, power, mapOf(e.resource to details))
            } else {
                MachinePowerDetails(
                    current.amount + e.count,
                    Power(current.power.average + power.average, current.power.max + power.max, current.power.isVariable),
                    current.recipes + (e.resource to details),
                )
            }
        }
        var average = 0.0
        var max = 0.0
        var variable = false
        for (b in byBuilding.values) {
            average += b.power.average
            max += b.power.max
            variable = variable || b.power.isVariable
        }
        if (abs(max - average) < 1e-8) variable = false
        return PowerDetails(
            Power(Numbers.round(average), Numbers.round(max), variable),
            byBuilding.mapValues { (_, v) -> v.copy(power = v.power.rounded()) }
                .entries.sortedBy { data.nameOf(it.key) }.associate { it.key to it.value },
        )
    }

    private fun Power.rounded() = Power(Numbers.round(average), Numbers.round(max), isVariable)
}
