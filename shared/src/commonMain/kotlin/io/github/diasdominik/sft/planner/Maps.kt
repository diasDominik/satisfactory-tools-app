package io.github.diasdominik.sft.planner

/** Multiplatform stand-in for java.util.Map.merge. */
internal inline fun <K, V> MutableMap<K, V>.mergeWith(key: K, value: V, remap: (V, V) -> V) {
    val old = this[key]
    this[key] = if (old == null) value else remap(old, value)
}
