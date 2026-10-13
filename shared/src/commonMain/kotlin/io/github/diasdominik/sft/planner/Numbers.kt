package io.github.diasdominik.sft.planner

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

object Numbers {
    fun round(value: Double, decimals: Int = 3): Double {
        val f = 10.0.pow(decimals)
        return (value * f).roundToLong() / f
    }

    fun ceil(value: Double, decimals: Int = 3): Double {
        val f = 10.0.pow(decimals)
        return kotlin.math.ceil(value * f - 1e-9) / f
    }

    /** Formats like upstream's number filter: up to [decimals] decimals, grouped thousands, trailing zeros trimmed. */
    fun format(value: Double, decimals: Int = 3): String {
        if (value.isNaN()) return "–"
        if (value.isInfinite()) return "∞"
        val rounded = round(value, decimals)
        val negative = rounded < 0
        val absValue = abs(rounded)
        val whole = absValue.toLong()
        var fraction = ((absValue - whole) * 10.0.pow(decimals)).roundToLong()
        var wholePart = whole
        if (fraction >= 10.0.pow(decimals).toLong()) {
            wholePart += 1
            fraction = 0
        }
        val grouped = wholePart.toString().reversed().chunked(3).joinToString(",").reversed()
        val fractionText = if (fraction == 0L) "" else "." + fraction.toString().padStart(decimals, '0').trimEnd('0')
        return (if (negative && (wholePart != 0L || fraction != 0L)) "-" else "") + grouped + fractionText
    }
}
