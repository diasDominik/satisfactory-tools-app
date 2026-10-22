package io.github.diasdominik.sft.planner

/** What [Leftovers.allocate] needs to know about one production line. */
data class LeftoverLine(
    val uid: String,
    val folder: String?,
    val takeLeftovers: Boolean,
    val excluded: Set<String> = emptySet(),
    /** Its current inputs, including leftover inputs from the last allocation. */
    val inputs: List<InputItem> = emptyList(),
    /** What it has left over itself, per minute. */
    val byproducts: Map<String, Double> = emptyMap(),
    /** How much of each input item its plan uses, per minute. */
    val used: Map<String, Double> = emptyMap(),
)

/**
 * Lines can take what other lines leave over. A line that opts in is offered the leftovers of the
 * lines above it in the same folder, less whatever lines in between already use, so nothing is
 * handed out twice. Only lines above count, which keeps the hand-overs free of loops.
 */
object Leftovers {
    /**
     * The leftover inputs each line should have, by uid. [dependsOn] (source, target) tells whether
     * a source already takes something from the target through explicit links.
     */
    fun allocate(lines: List<LeftoverLine>, dependsOn: (String, String) -> Boolean = { _, _ -> false }): Map<String, List<InputItem>> {
        val taken = HashMap<Pair<String, String>, Double>()
        val result = LinkedHashMap<String, List<InputItem>>()
        for ((index, target) in lines.withIndex()) {
            val explicitSources = target.inputs.filter { !it.leftover }.mapNotNull { it.source }.toSet()
            result[target.uid] = if (!target.takeLeftovers) emptyList() else lines.take(index)
                .filter { s -> s.folder == target.folder && s.uid !in target.excluded && s.uid !in explicitSources && !dependsOn(s.uid, target.uid) }
                .flatMap { source ->
                    source.byproducts.mapNotNull { (item, amount) ->
                        val left = Numbers.round(amount - (taken[source.uid to item] ?: 0.0))
                        if (left > 1e-6) InputItem(item, left, source.uid, leftover = true) else null
                    }
                }
            // Book what this line really uses from its leftover inputs: its own inputs first, then leftovers in order.
            for ((item, used) in target.used) {
                var rest = used - target.inputs.filter { !it.leftover && it.item == item }.sumOf { it.amount }
                for (input in target.inputs.filter { it.leftover && it.item == item && it.source != null }) {
                    val share = minOf(rest, input.amount).coerceAtLeast(0.0)
                    taken[input.source!! to item] = (taken[input.source to item] ?: 0.0) + share
                    rest -= share
                }
            }
        }
        return result
    }
}
