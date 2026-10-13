package io.github.diasdominik.sft.planner

import kotlin.math.abs

/**
 * A small dense two-phase simplex solver for
 *
 *     minimize c·x  subject to  A·x (<=, =, >=) b,  0 <= x <= upper
 *
 * The production planner produces problems with a few hundred rows and columns, which a dense
 * tableau handles comfortably on a phone.
 */
class LinearProgram(val variableCount: Int) {
    enum class Relation { LE, EQ, GE }

    class Constraint(val coefficients: Map<Int, Double>, val relation: Relation, val rhs: Double)

    val objective = DoubleArray(variableCount)
    val upperBounds = DoubleArray(variableCount) { Double.POSITIVE_INFINITY }
    val constraints = mutableListOf<Constraint>()

    fun addConstraint(coefficients: Map<Int, Double>, relation: Relation, rhs: Double) {
        constraints += Constraint(coefficients.filterValues { it != 0.0 }, relation, rhs)
    }
}

sealed interface LpResult {
    data class Optimal(val values: DoubleArray, val objective: Double) : LpResult
    data object Infeasible : LpResult
    data object Unbounded : LpResult
}

object Simplex {
    /** Pivot count of the last solve, for diagnostics. */
    var lastIterations = 0
        private set

    private const val EPS = 1e-9
    private const val PIVOT_EPS = 1e-7
    private const val FEASIBILITY_EPS = 1e-8
    private const val MAX_ITERATIONS = 50_000
    private const val REINVERT_EVERY = 500

    fun solve(lp: LinearProgram): LpResult {
        lastIterations = 0
        // Collect rows: user constraints plus finite upper bounds.
        data class Row(val coefficients: Map<Int, Double>, val relation: LinearProgram.Relation, val rhs: Double)

        val rows = ArrayList<Row>()
        for (c in lp.constraints) rows += Row(c.coefficients, c.relation, c.rhs)
        for (j in 0 until lp.variableCount) {
            val u = lp.upperBounds[j]
            if (u.isFinite()) rows += Row(mapOf(j to 1.0), LinearProgram.Relation.LE, u)
        }

        val m = rows.size
        val n = lp.variableCount
        // Normalise so every rhs is non-negative.
        val normalised = rows.map { r ->
            if (r.rhs < 0) {
                val flipped = when (r.relation) {
                    LinearProgram.Relation.LE -> LinearProgram.Relation.GE
                    LinearProgram.Relation.GE -> LinearProgram.Relation.LE
                    LinearProgram.Relation.EQ -> LinearProgram.Relation.EQ
                }
                Row(r.coefficients.mapValues { -it.value }, flipped, -r.rhs)
            } else r
        }

        val constraintScale = 1.0 + (normalised.take(lp.constraints.size).maxOfOrNull { it.rhs } ?: 0.0)

        val slackCount = normalised.count { it.relation != LinearProgram.Relation.EQ }
        val artificialCount = normalised.count { it.relation != LinearProgram.Relation.LE }
        val totalColumns = n + slackCount + artificialCount
        val rhsColumn = totalColumns

        val tableau = Array(m) { DoubleArray(totalColumns + 1) }
        val basis = IntArray(m)
        val isArtificial = BooleanArray(totalColumns)

        var slack = n
        var artificial = n + slackCount
        for ((i, r) in normalised.withIndex()) {
            val row = tableau[i]
            for ((j, v) in r.coefficients) row[j] = v
            row[rhsColumn] = r.rhs
            when (r.relation) {
                LinearProgram.Relation.LE -> {
                    row[slack] = 1.0
                    basis[i] = slack++
                }
                LinearProgram.Relation.GE -> {
                    row[slack++] = -1.0
                    row[artificial] = 1.0
                    isArtificial[artificial] = true
                    basis[i] = artificial++
                }
                LinearProgram.Relation.EQ -> {
                    row[artificial] = 1.0
                    isArtificial[artificial] = true
                    basis[i] = artificial++
                }
            }
        }

        val initialBasis = basis.copyOf()
        val original = Array(m) { tableau[it].copyOf() }

        // Phase 1: minimise the sum of artificial variables.
        if (artificialCount > 0) {
            val phase1 = DoubleArray(totalColumns)
            for (j in 0 until totalColumns) if (isArtificial[j]) phase1[j] = 1.0
            val status = runSimplex(tableau, original, basis, phase1, totalColumns, initialBasis, allowed = { true })
            if (status != Status.OPTIMAL) return LpResult.Infeasible
            val infeasibility = basis.indices.sumOf { i -> if (isArtificial[basis[i]]) tableau[i][rhsColumn] else 0.0 }
            // Scale by the constraint right-hand sides only: upper bounds can be huge (map limits) and
            // would hide real infeasibility such as producing an item from nothing.
            if (infeasibility > FEASIBILITY_EPS * constraintScale) return LpResult.Infeasible
            // Drive remaining (zero-valued) artificials out of the basis where possible.
            for (i in 0 until m) {
                if (!isArtificial[basis[i]]) continue
                val row = tableau[i]
                var pivotColumn = -1
                for (j in 0 until totalColumns) {
                    if (!isArtificial[j] && abs(row[j]) > EPS) {
                        pivotColumn = j
                        break
                    }
                }
                if (pivotColumn >= 0) pivot(tableau, basis, i, pivotColumn, totalColumns)
            }
        }

        // Phase 2: original objective, artificial columns may not enter.
        val cost = DoubleArray(totalColumns)
        for (j in 0 until n) cost[j] = lp.objective[j]
        val status = runSimplex(tableau, original, basis, cost, totalColumns, initialBasis, allowed = { !isArtificial[it] })
        if (status == Status.UNBOUNDED) return LpResult.Unbounded
        if (status == Status.ITERATION_LIMIT) return LpResult.Infeasible

        // Thousands of pivots leave rounding errors in the tableau, so take the final basis and solve
        // for its values again from the original constraints.
        val basic = solveBasis(original, basis, rhsColumn) ?: DoubleArray(m) { tableau[it][rhsColumn] }
        val values = DoubleArray(n)
        for (i in 0 until m) {
            val b = basis[i]
            if (b < n) values[b] = basic[i].let { if (abs(it) < EPS) 0.0 else it }
        }
        var obj = 0.0
        for (j in 0 until n) obj += lp.objective[j] * values[j]
        return LpResult.Optimal(values, obj)
    }

    private enum class Status { OPTIMAL, UNBOUNDED, ITERATION_LIMIT }

    /**
     * Rebuilds the tableau for the current basis from the original rows (Gauss-Jordan), dropping the
     * rounding errors that thousands of pivots pile up. Keeps the old tableau if the basis is singular.
     */
    private fun reinvert(tableau: Array<DoubleArray>, original: Array<DoubleArray>, basis: IntArray) {
        val m = basis.size
        val work = Array(m) { original[it].copyOf() }
        val used = BooleanArray(m)
        val order = IntArray(m)
        for (k in 0 until m) {
            val col = basis[k]
            var best = -1
            for (i in 0 until m) if (!used[i] && (best < 0 || abs(work[i][col]) > abs(work[best][col]))) best = i
            if (best < 0 || abs(work[best][col]) < 1e-10) return
            used[best] = true
            order[k] = best
            val row = work[best]
            val p = row[col]
            for (j in row.indices) row[j] /= p
            for (i in 0 until m) {
                if (i == best) continue
                val f = work[i][col]
                if (f == 0.0) continue
                val other = work[i]
                for (j in other.indices) if (row[j] != 0.0) other[j] -= f * row[j]
                other[col] = 0.0
            }
        }
        for (k in 0 until m) work[order[k]].copyInto(tableau[k])
    }

    /** Solves B·x = b for the basis columns of [original] by Gaussian elimination with partial pivoting. */
    private fun solveBasis(original: Array<DoubleArray>, basis: IntArray, rhsColumn: Int): DoubleArray? {
        val m = basis.size
        val a = Array(m) { i -> DoubleArray(m + 1).also { row -> for (k in 0 until m) row[k] = original[i][basis[k]]; row[m] = original[i][rhsColumn] } }
        for (col in 0 until m) {
            var pivotRow = col
            for (i in col + 1 until m) if (abs(a[i][col]) > abs(a[pivotRow][col])) pivotRow = i
            if (abs(a[pivotRow][col]) < 1e-12) return null
            if (pivotRow != col) a[pivotRow] = a[col].also { a[col] = a[pivotRow] }
            val pivot = a[col]
            for (i in col + 1 until m) {
                val f = a[i][col] / pivot[col]
                if (f == 0.0) continue
                val row = a[i]
                for (k in col..m) row[k] -= f * pivot[k]
            }
        }
        val x = DoubleArray(m)
        for (i in m - 1 downTo 0) {
            var v = a[i][m]
            for (k in i + 1 until m) v -= a[i][k] * x[k]
            x[i] = v / a[i][i]
        }
        return x
    }

    private fun runSimplex(
        tableau: Array<DoubleArray>,
        original: Array<DoubleArray>,
        basis: IntArray,
        cost: DoubleArray,
        totalColumns: Int,
        initialBasis: IntArray,
        allowed: (Int) -> Boolean,
    ): Status {
        val m = tableau.size
        val rhsColumn = totalColumns
        // Reduced costs: r_j = c_j - c_B · column_j
        val reduced = DoubleArray(totalColumns)
        fun recomputeReduced() {
            for (j in 0 until totalColumns) reduced[j] = cost[j]
            for (i in 0 until m) {
                val cb = cost[basis[i]]
                if (cb == 0.0) continue
                val row = tableau[i]
                for (j in 0 until totalColumns) reduced[j] -= cb * row[j]
            }
        }
        recomputeReduced()

        // Columns that formed the initial identity basis: their entries hold B^-1, used for the
        // lexicographic ratio test which prevents cycling on the (very) degenerate planner LPs.
        val identityColumns = initialBasis
        var iterations = 0
        while (iterations++ < MAX_ITERATIONS) {
            lastIterations++
            var entering = -1
            val best = -EPS
            for (j in 0 until totalColumns) {
                if (!allowed(j)) continue
                val r = reduced[j]
                if (r < best) {
                    // Bland's rule for the entering column; Dantzig's rule stalls on these LPs.
                    entering = j
                    break
                }
            }
            if (entering < 0) return Status.OPTIMAL

            var leaving = -1
            var bestRatio = Double.POSITIVE_INFINITY
            for (i in 0 until m) {
                val a = tableau[i][entering]
                if (a > PIVOT_EPS) {
                    val ratio = maxOf(0.0, tableau[i][rhsColumn]) / a
                    if (leaving < 0 || ratio < bestRatio - EPS) {
                        bestRatio = ratio
                        leaving = i
                    } else if (ratio <= bestRatio + EPS && lexSmaller(tableau, i, leaving, entering, identityColumns)) {
                        leaving = i
                        bestRatio = minOf(bestRatio, ratio)
                    }
                }
            }
            if (leaving < 0) return Status.UNBOUNDED

            pivot(tableau, basis, leaving, entering, totalColumns)

            // Update reduced costs with the pivot row.
            val factor = reduced[entering]
            if (factor != 0.0) {
                val row = tableau[leaving]
                for (j in 0 until totalColumns) reduced[j] -= factor * row[j]
            }
            if (iterations % REINVERT_EVERY == 0) {
                reinvert(tableau, original, basis)
                recomputeReduced()
            } else if (iterations % 100 == 0) {
                recomputeReduced()
            }
        }
        return Status.ITERATION_LIMIT
    }

    /** Lexicographic comparison of rows [i] and [l] (scaled by their pivot elements) over the B^-1 columns. */
    private fun lexSmaller(tableau: Array<DoubleArray>, i: Int, l: Int, entering: Int, identityColumns: IntArray): Boolean {
        val ai = tableau[i][entering]
        val al = tableau[l][entering]
        for (c in identityColumns) {
            val vi = tableau[i][c] / ai
            val vl = tableau[l][c] / al
            if (vi < vl - 1e-12) return true
            if (vi > vl + 1e-12) return false
        }
        return false
    }

    private fun pivot(tableau: Array<DoubleArray>, basis: IntArray, pivotRow: Int, pivotColumn: Int, totalColumns: Int) {
        val row = tableau[pivotRow]
        val p = row[pivotColumn]
        for (j in 0..totalColumns) row[j] /= p
        row[pivotColumn] = 1.0
        for (i in tableau.indices) {
            if (i == pivotRow) continue
            val other = tableau[i]
            val f = other[pivotColumn]
            if (f == 0.0) continue
            for (j in 0..totalColumns) {
                val v = row[j]
                if (v != 0.0) {
                    val updated = other[j] - f * v
                    other[j] = if (abs(updated) < 1e-11) 0.0 else updated
                }
            }
            other[pivotColumn] = 0.0
        }
        basis[pivotRow] = pivotColumn
    }
}
