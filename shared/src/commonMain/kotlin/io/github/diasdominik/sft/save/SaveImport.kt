package io.github.diasdominik.sft.save

import io.github.diasdominik.sft.data.GameData
import kotlinx.serialization.Serializable

/** The player's progress from a save, as the planner's recipe and building switches. */
@Serializable
data class UnlockProfile(
    val sessionName: String = "",
    val alternates: List<String> = emptyList(),
    val lockedRecipes: List<String> = emptyList(),
    val lockedMachines: List<String> = emptyList(),
)

object SaveImport {
    fun profile(data: GameData, unlocks: SaveUnlocks): UnlockProfile {
        // A recipe counts as unlocked when the recipe manager lists it or a purchased schematic gives it.
        val unlocked = unlocks.recipes + unlocks.schematics.flatMap { data.schematic(it)?.unlock?.recipes.orEmpty() }
        val buildRecipes = data.recipes.values.filter { it.forBuilding }
        val machines = data.baseRecipes.flatMap { it.producedIn }.distinct().filter { machine ->
            val builtBy = buildRecipes.filter { r -> r.products.any { it.item == machine } }
            builtBy.isNotEmpty() && builtBy.none { it.className in unlocked }
        }
        return UnlockProfile(
            sessionName = unlocks.sessionName,
            alternates = data.alternateRecipes.map { it.className }.filter { it in unlocked },
            lockedRecipes = data.baseRecipes.map { it.className }.filter { it !in unlocked },
            lockedMachines = machines,
        )
    }
}
