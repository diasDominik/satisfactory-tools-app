package io.github.diasdominik.sft.planner

import kotlin.math.ceil

/** Conveyor belt and pipeline throughput in 1.0, per minute (pipes in m³). */
object Logistics {
    val BELT_SPEEDS = listOf(60.0, 120.0, 270.0, 480.0, 780.0, 1200.0)
    val PIPE_SPEEDS = listOf(300.0, 600.0)

    fun beltSpeed(tier: Int): Double = BELT_SPEEDS[(tier - 1).coerceIn(BELT_SPEEDS.indices)]
    fun pipeSpeed(tier: Int): Double = PIPE_SPEEDS[(tier - 1).coerceIn(PIPE_SPEEDS.indices)]

    /** Belts or pipes running side by side to carry [amount] per minute. */
    fun linesNeeded(amount: Double, capacity: Double): Int = maxOf(1, ceil(amount / capacity - 1e-9).toInt())
}
