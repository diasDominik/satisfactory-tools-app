package io.github.diasdominik.sft.ui.codex

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.diasdominik.sft.data.Constants
import io.github.diasdominik.sft.data.Formula
import io.github.diasdominik.sft.data.GameData
import io.github.diasdominik.sft.data.Recipe
import io.github.diasdominik.sft.ui.AmountChips
import io.github.diasdominik.sft.ui.EntityIcon
import io.github.diasdominik.sft.ui.fmt

/** Callbacks for following links between codex pages. */
class CodexLinks(
    val openItem: (String) -> Unit,
    val openBuilding: (String) -> Unit,
    val openSchematic: (String) -> Unit,
) {
    /** Opens whichever codex page the class name belongs to. */
    fun open(data: GameData, className: String) {
        when {
            data.item(className) != null -> data.item(className)?.let { openItem(it.slug) }
            data.building(className) != null -> data.building(className)?.let { openBuilding(it.slug) }
            data.schematic(className) != null -> data.schematic(className)?.let { openSchematic(it.slug) }
        }
    }
}

@Composable
fun RecipeCard(data: GameData, recipe: Recipe, overclock: Double, links: CodexLinks, modifier: Modifier = Modifier) {
    val machine = data.machineFor(recipe)
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(recipe.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (recipe.alternate) {
                    Text("ALT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(8.dp))
            val perMinute: ((io.github.diasdominik.sft.data.ItemAmount) -> Double?)? =
                if (recipe.inMachine && machine != null) { a -> Formula.amountPerMinute(recipe, machine, a.amount, overclock) } else null
            AmountChips(data, recipe.ingredients, perMinute) { links.open(data, it) }
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            AmountChips(data, recipe.products, perMinute) { links.open(data, it) }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    recipe.inMachine && machine != null -> {
                        Row(
                            Modifier.clickable { links.openBuilding(machine.slug) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            EntityIcon(data, machine.className, 20.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(machine.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        val time = Formula.recipeTime(recipe, machine, overclock)
                        Text("${fmt(time)} s · ${fmt(60 / time)} cycles/min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    recipe.inWorkshop -> Text("Equipment Workshop", style = MaterialTheme.typography.labelMedium)
                    recipe.inHand -> Text("Craft Bench", style = MaterialTheme.typography.labelMedium)
                    recipe.forBuilding -> Text("Build Gun", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (recipe.inMachine && recipe.isVariablePower) {
                val (min, max) = Formula.recipePowerRange(recipe, machine, overclock)
                Text("Power: ${fmt(min)} – ${fmt(max)} MW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun OverclockSlider(value: Double, onChange: (Double) -> Unit, enabled: Boolean = true) {
    Column {
        Text("Overclock: ${fmt(value, 0)} %", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(kotlin.math.round(it).toDouble()) },
            valueRange = 1f..250f,
            enabled = enabled,
        )
    }
}

fun schematicTypeName(type: String) = Constants.SCHEMATIC_TYPES[type] ?: type.removePrefix("EST_")

@Composable
fun SmallChip(text: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(text) })
}
