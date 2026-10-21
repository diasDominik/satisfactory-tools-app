package io.github.diasdominik.sft.planner

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/**
 * Integer variables on top of [Simplex]: depth-first branch and bound, pruning branches whose
 * relaxation can't beat the best whole-number answer found so far.
 *
 * The planner's integer variables are building counts that rounding down always keeps feasible
 * (the rest of the work moves to ordinary buildings), so rounding the first relaxation down gives
 * a good answer straight away and the search only has to improve on it. After [maxNodes]
 * relaxations the best answer so far is returned.
 */
object BranchAndBound {
    private const val INTEGRALITY = 1e-6

    /** Relaxations solved by the last call, for tests and diagnostics. */
    var lastNodes = 0
        private set

    fun solve(lp: LinearProgram, integers: List<Int>, maxNodes: Int = 300): LpResult {
        lastNodes = 1
        val root = Simplex.solve(lp)
        if (root !is LpResult.Optimal || integers.isEmpty()) return root
        if (fractional(root.values, integers) == null) return root

        var best: LpResult.Optimal? = null
        val roundedDown = Simplex.solve(bounded(lp, emptyMap(), integers.associateWith { floor(root.values[it] + INTEGRALITY) }))
        lastNodes++
        if (roundedDown is LpResult.Optimal && fractional(roundedDown.values, integers) == null) best = roundedDown

        class Node(val lower: Map<Int, Double>, val upper: Map<Int, Double>)
        val stack = ArrayDeque<Node>()
        fun branch(values: DoubleArray, variable: Int, node: Node) {
            val value = values[variable]
            val down = Node(node.lower, node.upper + (variable to floor(value)))
            val up = Node(node.lower + (variable to ceil(value)), node.upper)
            // Explore the side the relaxation leans towards first.
            if (value - floor(value) >= 0.5) { stack.addLast(down); stack.addLast(up) } else { stack.addLast(up); stack.addLast(down) }
        }
        branch(root.values, fractional(root.values, integers)!!, Node(emptyMap(), emptyMap()))

        while (stack.isNotEmpty() && lastNodes < maxNodes) {
            val node = stack.removeLast()
            val result = Simplex.solve(bounded(lp, node.lower, node.upper))
            lastNodes++
            if (result !is LpResult.Optimal) continue
            val incumbent = best
            if (incumbent != null && result.objective >= incumbent.objective - 1e-9 * maxOf(1.0, abs(incumbent.objective))) continue
            val variable = fractional(result.values, integers)
            if (variable == null) best = result else branch(result.values, variable, node)
        }
        return best ?: root
    }

    /** The integer variable furthest from a whole number, or null when they all are whole. */
    private fun fractional(values: DoubleArray, integers: List<Int>): Int? {
        var pick: Int? = null
        var distance = INTEGRALITY
        for (v in integers) {
            val d = abs(values[v] - round(values[v]))
            if (d > distance) { distance = d; pick = v }
        }
        return pick
    }

    private fun bounded(lp: LinearProgram, lower: Map<Int, Double>, upper: Map<Int, Double>): LinearProgram {
        val copy = LinearProgram(lp.variableCount)
        lp.objective.copyInto(copy.objective)
        lp.upperBounds.copyInto(copy.upperBounds)
        for ((v, u) in upper) copy.upperBounds[v] = minOf(copy.upperBounds[v], u)
        copy.constraints += lp.constraints
        for ((v, l) in lower) if (l > 0) copy.addConstraint(mapOf(v to 1.0), LinearProgram.Relation.GE, l)
        return copy
    }
}
