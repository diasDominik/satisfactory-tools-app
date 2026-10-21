package io.github.diasdominik.sft.planner

import io.github.diasdominik.sft.data.GameData
import kotlin.math.ceil
import kotlin.math.max

/**
 * A suggested floor plan for a production line: one manifold per recipe (buildings side by side,
 * a belt per ingredient in front with a splitter at each building, a belt per product behind with a
 * merger at each building), ordered from raw resources to products. A bus on the left connects every
 * output to the rows that use it, splitting where one output feeds several rows and merging where
 * several rows feed one. Distances are in metres, x grows to the right and y downwards; foundations are 8 m.
 * Fluids use pipes, with pipeline junctions in place of splitters and mergers.
 */
data class FactoryLayout(
    val rows: List<LayoutRow>,
    val belts: List<LayoutBelt>,
    /** Splitters, mergers and junctions, on the manifolds and the bus. */
    val connectors: List<LayoutConnector>,
    /** Left edge of the bus, so the whole plan spans [left, width] × [0, length]. */
    val left: Double,
    val width: Double,
    val length: Double,
) {
    val foundationsWide: Int get() = ceil((width - left) / FOUNDATION - 1e-9).toInt()
    val foundationsLong: Int get() = ceil(length / FOUNDATION - 1e-9).toInt()
    val splitters: Int get() = connectors.count { it.kind == ConnectorKind.SPLITTER }
    val mergers: Int get() = connectors.count { it.kind == ConnectorKind.MERGER }
    val junctions: Int get() = connectors.count { it.kind == ConnectorKind.JUNCTION }

    /** Belt (or pipe) length in metres, along the rows, to each building and on the bus. */
    fun length(fluid: Boolean): Double {
        var total = 0.0
        for (row in rows) for (lane in row.lanes) {
            if (lane.fluid != fluid) continue
            total += row.width
            val end = if (lane.input) row.machineY else row.machineY + row.machineLength
            total += row.count * kotlin.math.abs(end - lane.y)
        }
        for (belt in belts) {
            if (belt.fluid != fluid) continue
            total += (belt.bottom - belt.top) + (belt.fromYs.size + belt.toYs.size) * -belt.laneX
        }
        return total
    }

    companion object {
        const val FOUNDATION = 8.0
    }
}

/** A belt (or pipe) running along a row: an ingredient in front of the buildings or a product behind them. */
data class RowLane(val item: String, val y: Double, val fluid: Boolean, val input: Boolean)

/** One row of identical buildings running the same recipe. */
data class LayoutRow(
    val step: Int,
    val nodeId: Int,
    val nodeKey: String,
    val recipe: String,
    val machine: String,
    val count: Int,
    val y: Double,
    val pitch: Double,
    val machineWidth: Double,
    val machineLength: Double,
    val machineY: Double,
    val lanes: List<RowLane>,
    val width: Double,
    val depth: Double,
) {
    fun lane(item: String, input: Boolean): RowLane? = lanes.firstOrNull { it.item == item && it.input == input }

    /** Centre of building [index] along x. */
    fun machineX(index: Int): Double = index * pitch + pitch / 2
}

/**
 * A belt or pipe on the bus at [laneX], from a row's output lane (or the factory's top edge for raw
 * resources and inputs) to a row's input lane (or the bottom edge for products and byproducts).
 * [fromYs] and [toYs] hold one branch per row of the producing and consuming recipe.
 */
data class LayoutBelt(
    val item: String,
    val amount: Double,
    val fluid: Boolean,
    val lines: Int,
    val tier: Int,
    val laneX: Double,
    val fromYs: List<Double>,
    val toYs: List<Double>,
    val top: Double,
    val bottom: Double,
    val fromLabel: String,
    val toLabel: String,
)

enum class ConnectorKind(val label: String) { SPLITTER("Splitter"), MERGER("Merger"), JUNCTION("Pipeline Junction") }

data class LayoutConnector(val kind: ConnectorKind, val x: Double, val y: Double)

object FactoryLayoutPlanner {
    /** Footprints (width × length in metres) from the game; anything else counts as 10 × 10. */
    private val FOOTPRINTS = mapOf(
        "Desc_SmelterMk1_C" to (6.0 to 9.0),
        "Desc_ConstructorMk1_C" to (8.0 to 10.0),
        "Desc_AssemblerMk1_C" to (10.0 to 15.0),
        "Desc_FoundryMk1_C" to (10.0 to 9.0),
        "Desc_ManufacturerMk1_C" to (18.0 to 20.0),
        "Desc_OilRefinery_C" to (10.0 to 20.0),
        "Desc_Blender_C" to (18.0 to 16.0),
        "Desc_Packager_C" to (8.0 to 8.0),
        "Desc_HadronCollider_C" to (24.0 to 38.0),
        "Desc_QuantumEncoder_C" to (22.0 to 48.0),
        "Desc_Converter_C" to (16.0 to 16.0),
        "Desc_GeneratorCoal_C" to (10.0 to 26.0),
        "Desc_GeneratorFuel_C" to (20.0 to 20.0),
        "Desc_GeneratorNuclear_C" to (36.0 to 43.0),
        "Desc_GeneratorBiomass_Automated_C" to (8.0 to 8.0),
    )
    private const val MAX_PER_ROW = 10
    /** Spacing of bus lanes and of the belts along a row. */
    private const val LANE = 3.0
    private const val GAP = 2.0

    fun footprint(machine: String): Pair<Double, Double> = FOOTPRINTS[machine] ?: (10.0 to 10.0)

    fun plan(data: GameData, result: ProductionResult, beltTier: Int, pipeTier: Int): FactoryLayout {
        val graph = result.graph
        val step = steps(graph)
        fun fluid(item: String) = data.item(item)?.liquid == true
        val connectors = mutableListOf<LayoutConnector>()

        // Rows, in production order, each a manifold.
        val rows = mutableListOf<LayoutRow>()
        val rowsOf = HashMap<Int, MutableList<LayoutRow>>()
        var y = 0.0
        val recipeNodes = graph.nodes.filterIsInstance<RecipeNode>()
            .sortedWith(compareBy({ step[it.id] ?: 0 }, { it.usage.recipe.name }))
        for (node in recipeNodes) {
            val machine = node.usage.machine.className
            val (w, l) = footprint(machine)
            val pitch = ceil((w + GAP) / 4) * 4
            val inputs = node.usage.recipe.ingredients.map { it.item }.distinct()
            val outputs = node.usage.recipe.products.map { it.item }.distinct()
            var left = node.machines.count
            while (left > 0) {
                val count = minOf(left, MAX_PER_ROW)
                left -= count
                val inputDepth = inputs.size * LANE + GAP
                val outputDepth = outputs.size * LANE + GAP
                val depth = ceil((inputDepth + l + outputDepth) / FactoryLayout.FOUNDATION) * FactoryLayout.FOUNDATION
                val lanes = inputs.mapIndexed { i, item -> RowLane(item, y + LANE / 2 + i * LANE, fluid(item), input = true) } +
                    outputs.mapIndexed { i, item -> RowLane(item, y + depth - LANE / 2 - i * LANE, fluid(item), input = false) }
                val row = LayoutRow(
                    step = step[node.id] ?: 0,
                    nodeId = node.id,
                    nodeKey = graphNodeKey(node),
                    recipe = node.usage.recipe.className,
                    machine = machine,
                    count = count,
                    y = y,
                    pitch = pitch,
                    machineWidth = w,
                    machineLength = l,
                    machineY = y + inputDepth + (depth - inputDepth - outputDepth - l) / 2,
                    lanes = lanes,
                    width = count * pitch,
                    depth = depth,
                )
                // A splitter in front of every building but the last (it takes the end of the belt), a merger behind.
                for (lane in lanes) {
                    for (i in 0 until count - 1) {
                        val kind = when {
                            lane.fluid -> ConnectorKind.JUNCTION
                            lane.input -> ConnectorKind.SPLITTER
                            else -> ConnectorKind.MERGER
                        }
                        connectors += LayoutConnector(kind, row.machineX(i), lane.y)
                    }
                }
                rows += row
                rowsOf.getOrPut(node.id) { mutableListOf() } += row
                y += depth
            }
        }
        val length = max(y, FactoryLayout.FOUNDATION)

        // Belts on the bus, merged per source, target and item.
        class Pending(val build: (Double) -> LayoutBelt, val top: Double, val bottom: Double)
        val pending = mutableListOf<Pending>()
        val links = graph.edges.filter { it.from !== it.to }.groupBy { Triple(it.from, it.to, it.item) }
        for ((key, edges) in links) {
            val (from, to, item) = key
            val amount = edges.sumOf { it.amount }
            val isFluid = fluid(item)
            val tier = if (isFluid) pipeTier else beltTier
            val capacity = if (isFluid) Logistics.pipeSpeed(pipeTier) else Logistics.beltSpeed(beltTier)
            val fromYs = rowsOf[from.id]?.mapNotNull { it.lane(item, input = false)?.y }.orEmpty()
            val toYs = rowsOf[to.id]?.mapNotNull { it.lane(item, input = true)?.y }.orEmpty()
            val top = fromYs.minOrNull() ?: -LANE
            val bottom = toYs.maxOrNull() ?: (length + LANE)
            val fromLabel = when (from) {
                is RecipeNode -> from.usage.recipe.name
                is MinerNode -> "Mined " + data.nameOf(from.item)
                is InputNode -> "Input " + data.nameOf(from.item)
                else -> data.nameOf(item)
            }
            val toLabel = when (to) {
                is RecipeNode -> to.usage.recipe.name
                is ProductNode -> "Product " + data.nameOf(item)
                is ByproductNode -> "Byproduct " + data.nameOf(item)
                else -> data.nameOf(item)
            }
            pending += Pending(
                { lane -> LayoutBelt(item, amount, isFluid, Logistics.linesNeeded(amount, capacity), tier, lane, fromYs, toYs, minOf(top, bottom), max(top, bottom), fromLabel, toLabel) },
                minOf(top, bottom), max(top, bottom),
            )
        }
        // Overlapping belts get separate bus lanes (interval colouring), nearest the rows first.
        val laneEnds = mutableListOf<Double>()
        val belts = pending.sortedBy { it.top }.map { p ->
            var lane = laneEnds.indexOfFirst { it < p.top - 0.1 }
            if (lane < 0) {
                laneEnds += p.bottom
                lane = laneEnds.lastIndex
            } else {
                laneEnds[lane] = p.bottom
            }
            p.build(-(lane + 1) * LANE)
        }
        // Branching on the bus: several rows of one recipe share a belt.
        for (belt in belts) {
            val kindIn = if (belt.fluid) ConnectorKind.JUNCTION else ConnectorKind.MERGER
            val kindOut = if (belt.fluid) ConnectorKind.JUNCTION else ConnectorKind.SPLITTER
            belt.fromYs.sorted().drop(1).forEach { connectors += LayoutConnector(kindIn, belt.laneX, it) }
            belt.toYs.sorted().dropLast(1).forEach { connectors += LayoutConnector(kindOut, belt.laneX, it) }
        }
        // One output feeding several recipes is split; several outputs into one input are merged.
        // These sit where the bus lanes meet the row lane, just left of the row.
        belts.groupBy { it.fromLabel to it.item }.values.filter { it.size > 1 && it.first().fromYs.isNotEmpty() }.forEach { group ->
            val fluidGroup = group.first().fluid
            group.drop(1).forEach { b -> connectors += LayoutConnector(if (fluidGroup) ConnectorKind.JUNCTION else ConnectorKind.SPLITTER, b.laneX, b.fromYs.minOf { it }) }
        }
        belts.groupBy { it.toLabel to it.item }.values.filter { it.size > 1 && it.first().toYs.isNotEmpty() }.forEach { group ->
            val fluidGroup = group.first().fluid
            group.drop(1).forEach { b -> connectors += LayoutConnector(if (fluidGroup) ConnectorKind.JUNCTION else ConnectorKind.MERGER, b.laneX, b.toYs.maxOf { it }) }
        }
        val left = -(laneEnds.size + 1) * LANE
        val width = max(rows.maxOfOrNull { it.width } ?: 0.0, FactoryLayout.FOUNDATION)
        return FactoryLayout(rows, belts, connectors, left, width, length)
    }

    /** Production depth of each node: the longest chain of recipes feeding it. */
    private fun steps(graph: ProductionGraph): Map<Int, Int> {
        val step = HashMap<Int, Int>()
        graph.nodes.forEach { step[it.id] = 0 }
        // Relaxation bounded by the node count, so loops (e.g. recycled water) still finish.
        repeat(graph.nodes.size) {
            var changed = false
            for (e in graph.edges) {
                if (e.from === e.to || e.to !is RecipeNode) continue
                val candidate = (step[e.from.id] ?: 0) + if (e.from is RecipeNode) 1 else 0
                if (candidate > (step[e.to.id] ?: 0) && candidate <= graph.nodes.size) {
                    step[e.to.id] = candidate
                    changed = true
                }
            }
            if (!changed) return step
        }
        return step
    }
}
