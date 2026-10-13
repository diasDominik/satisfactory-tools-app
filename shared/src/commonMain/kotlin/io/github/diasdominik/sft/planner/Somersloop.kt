package io.github.diasdominik.sft.planner

/**
 * Production Amplifier: Somersloops in a building's slots raise its output by up to 100 % for the
 * same input, while power use grows with (1 + filled / slots)². The planner fills all slots or none.
 */
object Somersloop {
    private val SLOTS = mapOf(
        "Desc_SmelterMk1_C" to 1,
        "Desc_ConstructorMk1_C" to 1,
        "Desc_AssemblerMk1_C" to 2,
        "Desc_FoundryMk1_C" to 2,
        "Desc_OilRefinery_C" to 2,
        "Desc_Converter_C" to 2,
        "Desc_ManufacturerMk1_C" to 4,
        "Desc_Blender_C" to 4,
        "Desc_HadronCollider_C" to 4,
        "Desc_QuantumEncoder_C" to 4,
    )

    const val OUTPUT_FACTOR = 2.0
    const val POWER_FACTOR = 4.0

    fun slots(machine: String): Int = SLOTS[machine] ?: 0
}
